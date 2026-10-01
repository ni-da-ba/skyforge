"""Durable minimum-viable Continue Skyforge program progression service (OPT-6).

The service owns only program-continuation session state. It composes accepted lower
layers for child objectives and stops at human, authority, ambiguity, or strategic
boundaries. Executable task authority still enters only through the signed webhook path.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
import subprocess
import threading
from typing import Any, Callable, Mapping

from .context_package import ContextPackageDisposition, package_proposal
from .context_retrieval import ContextRetrievalDisposition, retrieve_context
from .external_service import ExternalClaimStore
from .human_review import HumanReviewStore, HumanReviewVerdict
from .hosted_admission import HostedAdmissionOutcome, HostedAdmissionStore
from .hosted_completion import HostedCompletionStore
from .hosted_state import HostedStateStore
from .hosted_task_plan import HostedTaskPlanStore
from .identity import canonical_digest
from .objective_ingress import (
    ObjectiveProposalRecord,
    ObjectiveProposalStore,
    ProgramObjectiveSource,
)
from .objective_lifecycle import effective_objective_progression
from .objective_intake import (
    ObjectiveCompileDisposition,
    ObjectiveCompileResult,
    ObjectiveIntent,
    ObjectiveRequest,
)
from .objective_promotion_effect import (
    ObjectivePromotionPostDisposition,
    execute_frozen_promotion_post,
)
from .objective_trace import build_objective_trace
from .program_projection import (
    ProgramNode,
    ProgramNodeKind,
    ProgramProjection,
    load_program_projection,
)
from .scope_promotion import (
    PromotionDisposition,
    PromotionStore,
    validate_promotion,
)
from .state_store import JsonStateStoreAdapter
from .task_event_composition import TaskAuthorityEventStore

PROGRAM_SESSIONS_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "program-continuations.json"
)
PROGRAM_SESSIONS_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "program-continuations.json.bak"
)
_PROGRAM_LOCK = threading.RLock()


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _sha64(value: Any, label: str) -> str:
    text = _required(value, label).lower()
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


class ProgramSessionDisposition(str, Enum):
    ADVANCING = "ADVANCING"
    WAIT_HUMAN = "WAIT_HUMAN"
    WAIT_AUTHORITY = "WAIT_AUTHORITY"
    WAIT_CHILD = "WAIT_CHILD"
    WAIT_STRATEGIC = "WAIT_STRATEGIC"
    WAIT_CONTROL = "WAIT_CONTROL"
    BLOCKED = "BLOCKED"
    SUPERSEDED = "SUPERSEDED"
    COMPLETE = "COMPLETE"


@dataclass(frozen=True)
class ProgramNodeCompletion:
    node_id: str
    evidence_id: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "node_id", _required(self.node_id, "completion node_id"))
        object.__setattr__(
            self, "evidence_id", _required(self.evidence_id, "completion evidence_id")
        )

    def as_dict(self) -> dict[str, str]:
        return {"node_id": self.node_id, "evidence_id": self.evidence_id}


@dataclass(frozen=True)
class ProgramContinuationSession:
    parent_proposal_id: str
    invocation_proposal_ids: tuple[str, ...]
    program_id: str
    projection_digest: str
    current_node_id: str
    disposition: ProgramSessionDisposition
    reason: str
    child_proposal_id: str = ""
    gate_id: str = ""
    completed_nodes: tuple[ProgramNodeCompletion, ...] = ()

    def __post_init__(self) -> None:
        object.__setattr__(
            self,
            "parent_proposal_id",
            _sha64(self.parent_proposal_id, "parent_proposal_id"),
        )
        aliases = tuple(
            sorted(
                {
                    _sha64(value, "invocation proposal id")
                    for value in self.invocation_proposal_ids
                }
            )
        )
        if self.parent_proposal_id not in aliases:
            aliases = tuple(sorted((*aliases, self.parent_proposal_id)))
        object.__setattr__(self, "invocation_proposal_ids", aliases)
        object.__setattr__(self, "program_id", _required(self.program_id, "program_id"))
        object.__setattr__(
            self,
            "projection_digest",
            _sha64(self.projection_digest, "projection_digest"),
        )
        object.__setattr__(
            self, "current_node_id", _required(self.current_node_id, "current_node_id")
        )
        if not isinstance(self.disposition, ProgramSessionDisposition):
            object.__setattr__(
                self,
                "disposition",
                ProgramSessionDisposition(str(self.disposition)),
            )
        object.__setattr__(self, "reason", _required(self.reason, "session reason"))
        if self.child_proposal_id:
            object.__setattr__(
                self,
                "child_proposal_id",
                _sha64(self.child_proposal_id, "child_proposal_id"),
            )
        ids = [value.node_id for value in self.completed_nodes]
        if len(ids) != len(set(ids)):
            raise ValueError("duplicate completed program node")

    @property
    def session_id(self) -> str:
        return canonical_digest(
            {
                "parent_proposal_id": self.parent_proposal_id,
                "program_id": self.program_id,
                "projection_digest": self.projection_digest,
            }
        )

    @property
    def active(self) -> bool:
        return self.disposition not in {
            ProgramSessionDisposition.COMPLETE,
            ProgramSessionDisposition.SUPERSEDED,
        }

    def has_completed(self, node_id: str) -> bool:
        return any(value.node_id == node_id for value in self.completed_nodes)

    def attach_invocation(self, proposal_id: str) -> "ProgramContinuationSession":
        key = _sha64(proposal_id, "invocation proposal id")
        if key in self.invocation_proposal_ids:
            return self
        return replace(
            self,
            invocation_proposal_ids=tuple(
                sorted((*self.invocation_proposal_ids, key))
            ),
        )

    def complete_node(
        self,
        *,
        node_id: str,
        evidence_id: str,
        next_node_id: str | None,
        reason: str,
    ) -> "ProgramContinuationSession":
        completion = ProgramNodeCompletion(node_id, evidence_id)
        values = tuple(
            value for value in self.completed_nodes if value.node_id != node_id
        ) + (completion,)
        if next_node_id is None:
            return replace(
                self,
                completed_nodes=values,
                disposition=ProgramSessionDisposition.COMPLETE,
                reason=reason,
                child_proposal_id="",
                gate_id="",
            )
        return replace(
            self,
            completed_nodes=values,
            current_node_id=next_node_id,
            disposition=ProgramSessionDisposition.ADVANCING,
            reason=reason,
            child_proposal_id="",
            gate_id="",
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "session_id": self.session_id,
            "parent_proposal_id": self.parent_proposal_id,
            "invocation_proposal_ids": list(self.invocation_proposal_ids),
            "program_id": self.program_id,
            "projection_digest": self.projection_digest,
            "current_node_id": self.current_node_id,
            "disposition": self.disposition.value,
            "reason": self.reason,
            "child_proposal_id": self.child_proposal_id,
            "gate_id": self.gate_id,
            "completed_nodes": [value.as_dict() for value in self.completed_nodes],
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ProgramContinuationSession":
        if not isinstance(raw, Mapping):
            raise ValueError("program continuation session must be object")
        values = raw.get("completed_nodes")
        aliases = raw.get("invocation_proposal_ids")
        if not isinstance(values, list) or not isinstance(aliases, list):
            raise ValueError("program session completion/invocation lists are required")
        record = cls(
            parent_proposal_id=raw.get("parent_proposal_id"),
            invocation_proposal_ids=tuple(str(value) for value in aliases),
            program_id=raw.get("program_id"),
            projection_digest=raw.get("projection_digest"),
            current_node_id=raw.get("current_node_id"),
            disposition=ProgramSessionDisposition(str(raw.get("disposition") or "")),
            reason=raw.get("reason"),
            child_proposal_id=str(raw.get("child_proposal_id") or ""),
            gate_id=str(raw.get("gate_id") or ""),
            completed_nodes=tuple(
                ProgramNodeCompletion(
                    node_id=value.get("node_id") if isinstance(value, Mapping) else None,
                    evidence_id=(
                        value.get("evidence_id") if isinstance(value, Mapping) else None
                    ),
                )
                for value in values
            ),
        )
        if str(raw.get("session_id") or "") != record.session_id:
            raise ValueError("program continuation session identity mismatch")
        return record


@dataclass(frozen=True)
class ProgramContinuationLedger:
    records: tuple[ProgramContinuationSession, ...] = ()

    def __post_init__(self) -> None:
        ids = [value.session_id for value in self.records]
        if len(ids) != len(set(ids)):
            raise ValueError("duplicate program continuation session")
        active = [value for value in self.records if value.active]
        if len(active) > 1:
            raise ValueError("multiple active program continuation sessions are forbidden")

    @property
    def active(self) -> ProgramContinuationSession | None:
        values = [value for value in self.records if value.active]
        return values[0] if values else None

    def for_invocation(self, proposal_id: str) -> ProgramContinuationSession | None:
        key = _sha64(proposal_id, "proposal_id")
        matches = [
            value for value in self.records if key in value.invocation_proposal_ids
        ]
        active = [value for value in matches if value.active]
        if len(active) > 1:
            raise ValueError("proposal belongs to multiple active program sessions")
        if active:
            return active[0]
        current = [
            value
            for value in matches
            if value.disposition is not ProgramSessionDisposition.SUPERSEDED
        ]
        if len(current) > 1:
            raise ValueError("proposal belongs to multiple current program sessions")
        return current[0] if current else (matches[-1] if matches else None)

    def put(self, record: ProgramContinuationSession) -> "ProgramContinuationLedger":
        current = next(
            (value for value in self.records if value.session_id == record.session_id),
            None,
        )
        if current is not None:
            values = tuple(
                record if value.session_id == record.session_id else value
                for value in self.records
            )
        else:
            values = self.records + (record,)
        return ProgramContinuationLedger(values)

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "records": [value.as_dict() for value in self.records],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    @classmethod
    def from_mapping(cls, raw: Any) -> "ProgramContinuationLedger":
        if raw in (None, {}):
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid program continuation ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("program continuation records must be list")
        return cls(
            tuple(ProgramContinuationSession.from_mapping(value) for value in values)
        )


@dataclass(frozen=True)
class ProgramContinuationStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ProgramContinuationStore":
        root = Path(root).resolve()
        return cls(
            JsonStateStoreAdapter(
                path=root / PROGRAM_SESSIONS_RELATIVE_PATH,
                backup_path=root / PROGRAM_SESSIONS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> ProgramContinuationLedger:
        return ProgramContinuationLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ProgramContinuationLedger) -> ProgramContinuationLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


class ProgramAdvanceDisposition(str, Enum):
    NO_SESSION = "NO_SESSION"
    ADVANCED = "ADVANCED"
    WAIT_HUMAN = "WAIT_HUMAN"
    WAIT_AUTHORITY = "WAIT_AUTHORITY"
    WAIT_CHILD = "WAIT_CHILD"
    WAIT_STRATEGIC = "WAIT_STRATEGIC"
    WAIT_CONTROL = "WAIT_CONTROL"
    BLOCKED = "BLOCKED"
    COMPLETE = "COMPLETE"


@dataclass(frozen=True)
class ProgramAdvanceResult:
    disposition: ProgramAdvanceDisposition
    reason: str
    session: ProgramContinuationSession | None = None

    @property
    def durable_identity(self) -> str:
        return self.session.session_id if self.session is not None else ""

    def as_dict(self) -> dict[str, Any]:
        return {
            "disposition": self.disposition.value,
            "reason": self.reason,
            "session": self.session.as_dict() if self.session is not None else None,
        }


def _program_parent_proposals(root: Path) -> tuple[ObjectiveProposalRecord, ...]:
    return tuple(
        value
        for value in ObjectiveProposalStore.for_root(root).load().records
        if value.compiled.disposition is ObjectiveCompileDisposition.PROGRAM_CONTINUE
    )


def ensure_program_session(
    *,
    root: Path,
    parent_proposal_id: str,
) -> ProgramContinuationSession:
    root = Path(root).resolve()
    projection = load_program_projection(root)
    parent_id = _sha64(parent_proposal_id, "parent_proposal_id")
    parents = [
        value
        for value in _program_parent_proposals(root)
        if value.proposal_id == parent_id
    ]
    if len(parents) != 1:
        raise ValueError("program continuation parent proposal is unavailable or ambiguous")

    with _PROGRAM_LOCK:
        store = ProgramContinuationStore.for_root(root)
        ledger = store.load()
        existing = ledger.for_invocation(parent_id)
        if existing is not None:
            return existing
        active = ledger.active
        if active is not None:
            if (
                active.program_id != projection.program_id
                or active.projection_digest != projection.digest
            ):
                raise ValueError("active program continuation projection identity drifted")
            attached = active.attach_invocation(parent_id)
            store.save(ledger.put(attached))
            return attached

        first = projection.nodes[0]
        created = ProgramContinuationSession(
            parent_proposal_id=parent_id,
            invocation_proposal_ids=(parent_id,),
            program_id=projection.program_id,
            projection_digest=projection.digest,
            current_node_id=first.node_id,
            disposition=ProgramSessionDisposition.ADVANCING,
            reason="durable Continue Skyforge session created from exact parent objective",
        )
        store.save(ledger.put(created))
        return created


def _child_record(
    *,
    root: Path,
    repo: str,
    projection: ProgramProjection,
    session: ProgramContinuationSession,
    node: ProgramNode,
) -> ObjectiveProposalRecord:
    if node.kind is not ProgramNodeKind.TASK:
        raise ValueError("program child objective requires task node")
    source = ProgramObjectiveSource(
        repo=repo,
        parent_proposal_id=session.parent_proposal_id,
        program_id=projection.program_id,
        node_id=node.node_id,
        projection_digest=projection.digest,
        objective_text=f"Continue Skyforge program node {node.node_id}",
    )
    request = ObjectiveRequest(
        raw_text=source.objective_text,
        intent=ObjectiveIntent.CONTINUE,
        target=f"{projection.program_id}:{node.node_id}",
    )
    compiled = ObjectiveCompileResult(
        ObjectiveCompileDisposition.CANDIDATE_TASK,
        request,
        "validated OPT-6 program projection selected an already-authorized bounded task",
        candidate_task=node.candidate_task(projection.program_id),
    )
    return ObjectiveProposalRecord(
        source=source,
        delivery_id=f"program:{session.session_id}",
        compiled=compiled,
    )


def _save_session(
    root: Path,
    session: ProgramContinuationSession,
) -> ProgramContinuationSession:
    store = ProgramContinuationStore.for_root(root)
    store.save(store.load().put(session))
    return session


def _trace_stage(trace: Mapping[str, Any], name: str) -> Mapping[str, Any] | None:
    values = trace.get("stages")
    if not isinstance(values, list):
        return None
    matches = [
        value
        for value in values
        if isinstance(value, Mapping) and value.get("stage") == name
    ]
    if len(matches) > 1:
        raise ValueError(f"program child trace has multiple {name} stages")
    return matches[0] if matches else None


def _supersede_projection_if_safe(
    *,
    root: Path,
    ledger: ProgramContinuationLedger,
    active: ProgramContinuationSession,
    projection: ProgramProjection,
) -> ProgramContinuationSession | None:
    """Rebind one non-executed child to an explicitly superseding program projection."""

    if projection.program_id != active.program_id:
        return None
    if projection.digest == active.projection_digest:
        return active
    if projection.supersedes_projection_digest != active.projection_digest:
        return None

    current = projection.get(active.current_node_id)
    if current is None:
        raise ValueError("superseding projection removed the active program node")
    for completed in active.completed_nodes:
        prior = projection.get(completed.node_id)
        if prior is None:
            raise ValueError(
                "superseding projection removed a durably completed program node"
            )

    event_id = ""
    plan = None
    admission = None
    if active.child_proposal_id:
        trace = build_objective_trace(
            root=root,
            correlation_id=active.child_proposal_id,
        )
        if trace is None:
            raise ValueError("program child objective trace disappeared during supersession")
        completion = _trace_stage(trace, "COMPLETION")
        if completion is not None and completion.get("status") in {"RECORDED", "CLEANED"}:
            raise ValueError(
                "superseding projection cannot replace a durably completed child"
            )
        task = _trace_stage(trace, "TASK_AUTHORITY")
        if task is not None:
            identities = task.get("identities")
            if not isinstance(identities, Mapping):
                raise ValueError("program child task authority lacks exact identities")
            event_id = _required(identities.get("event_id"), "task event_id")
            plan_store = HostedTaskPlanStore.for_root(root)
            plan = plan_store.load().get_event(event_id)
            if plan is not None:
                admission_store = HostedAdmissionStore.for_root(root)
                admission = admission_store.load().for_plan(plan.plan_id)
                if admission is not None:
                    if (
                        admission.outcome is HostedAdmissionOutcome.ADMITTED
                        or admission.consume_attempt
                        or admission.frozen_task is not None
                        or admission.attempt is not None
                        or admission.worker_spec is not None
                    ):
                        raise ValueError(
                            "superseding projection cannot replace child with executable authority"
                        )
                    if admission.outcome not in {
                        HostedAdmissionOutcome.BLOCKED,
                        HostedAdmissionOutcome.NOT_DISPATCH,
                        HostedAdmissionOutcome.RECLASSIFY,
                    }:
                        raise ValueError(
                            "superseding projection found unsupported non-executed admission"
                        )
            completion_records = [
                value
                for value in HostedCompletionStore.for_root(root).load().records
                if value.event_id == event_id
            ]
            if completion_records:
                raise ValueError(
                    "superseding projection cannot replace child with completion evidence"
                )

    # Fence the stale task event first. A crash after this point cannot recreate worker
    # authority; a later call can safely repeat the remaining idempotent cleanup.
    if event_id:
        state_store = HostedStateStore.for_root(root)
        state = state_store.load()
        retired = list(state.inbox.retired_event_keys)
        if event_id not in retired:
            retired.append(event_id)
        next_inbox = replace(
            state.inbox,
            pending_events=tuple(
                value
                for value in state.inbox.pending_events
                if value.event_id != event_id
            ),
            retired_event_keys=tuple(retired[-1024:]),
            owned_event_keys=tuple(
                value for value in state.inbox.owned_event_keys if value != event_id
            ),
        )
        state_store.save(replace(state, inbox=next_inbox))

    if plan is not None:
        admission_store = HostedAdmissionStore.for_root(root)
        admission_store.save(admission_store.load().remove_plan(plan.plan_id))
        plan_store = HostedTaskPlanStore.for_root(root)
        plan_store.save(plan_store.load().remove(plan.plan_id))

    superseded = replace(
        active,
        disposition=ProgramSessionDisposition.SUPERSEDED,
        reason=(
            "explicit accepted program projection superseded a non-executed child "
            f"at node {active.current_node_id}"
        ),
        gate_id="",
    )
    rebound = ProgramContinuationSession(
        parent_proposal_id=active.parent_proposal_id,
        invocation_proposal_ids=active.invocation_proposal_ids,
        program_id=projection.program_id,
        projection_digest=projection.digest,
        current_node_id=active.current_node_id,
        disposition=ProgramSessionDisposition.ADVANCING,
        reason=(
            "explicit accepted program projection supersession preserved completed "
            "program evidence and retired only non-executed stale child authority"
        ),
        completed_nodes=active.completed_nodes,
    )
    records = tuple(
        value for value in ledger.records if value.session_id != active.session_id
    ) + (superseded, rebound)
    ProgramContinuationStore.for_root(root).save(
        ProgramContinuationLedger(records)
    )
    return rebound


def _complete_from_child_trace(
    *,
    root: Path,
    projection: ProgramProjection,
    session: ProgramContinuationSession,
    node: ProgramNode,
) -> ProgramContinuationSession | None:
    if not session.child_proposal_id:
        return None
    trace = build_objective_trace(
        root=root,
        correlation_id=session.child_proposal_id,
    )
    if trace is None:
        raise ValueError("program child objective trace disappeared")
    completion_stage = next(
        (
            value
            for value in trace.get("stages", ())
            if value.get("stage") == "COMPLETION"
        ),
        None,
    )
    if not isinstance(completion_stage, Mapping):
        return None
    if completion_stage.get("status") not in {"RECORDED", "CLEANED"}:
        return None
    identities = completion_stage.get("identities")
    if not isinstance(identities, Mapping):
        raise ValueError("program child completion trace lacks identities")
    completion_id = _required(identities.get("completion_id"), "completion_id")
    successor = projection.successor(node.node_id)
    return session.complete_node(
        node_id=node.node_id,
        evidence_id=completion_id,
        next_node_id=successor.node_id if successor is not None else None,
        reason="exact X-2 child completion advanced program node",
    )


def _session_result(
    disposition: ProgramAdvanceDisposition,
    session: ProgramContinuationSession,
) -> ProgramAdvanceResult:
    return ProgramAdvanceResult(disposition, session.reason, session)


def advance_program_continuation(
    *,
    root: Path,
    repo: str,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> ProgramAdvanceResult:
    """Advance one active program session through bounded model-free composition."""

    root = Path(root).resolve()
    with _PROGRAM_LOCK:
        parents = _program_parent_proposals(root)
        store = ProgramContinuationStore.for_root(root)
        ledger = store.load()
        active = ledger.active

        if active is None:
            represented = {
                proposal_id
                for record in ledger.records
                for proposal_id in record.invocation_proposal_ids
            }
            pending = [value for value in parents if value.proposal_id not in represented]
            if not pending:
                return ProgramAdvanceResult(
                    ProgramAdvanceDisposition.NO_SESSION,
                    "no unbound Continue Skyforge parent objective exists",
                )
            active = ensure_program_session(
                root=root,
                parent_proposal_id=pending[-1].proposal_id,
            )
        else:
            for parent in parents:
                if parent.proposal_id not in active.invocation_proposal_ids:
                    active = active.attach_invocation(parent.proposal_id)
            active = _save_session(root, active)

        parent_control = effective_objective_progression(
            root,
            active.parent_proposal_id,
        )
        if not parent_control.allowed:
            active = replace(
                active,
                disposition=ProgramSessionDisposition.WAIT_CONTROL,
                reason=parent_control.reason,
            )
            active = _save_session(root, active)
            return _session_result(ProgramAdvanceDisposition.WAIT_CONTROL, active)

        try:
            projection = load_program_projection(root)
        except (OSError, ValueError) as exc:
            active = replace(
                active,
                disposition=ProgramSessionDisposition.BLOCKED,
                reason=f"program projection/source validation failed closed: {exc}",
            )
            active = _save_session(root, active)
            return _session_result(ProgramAdvanceDisposition.BLOCKED, active)

        if (
            projection.program_id != active.program_id
            or projection.digest != active.projection_digest
        ):
            try:
                rebound = _supersede_projection_if_safe(
                    root=root,
                    ledger=ledger,
                    active=active,
                    projection=projection,
                )
            except ValueError as exc:
                rebound = None
                migration_error = str(exc)
            else:
                migration_error = ""
            if rebound is None:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.BLOCKED,
                    reason=(
                        "program projection identity changed after session creation"
                        + (f": {migration_error}" if migration_error else "")
                    ),
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.BLOCKED, active)
            active = rebound
            ledger = store.load()

        # One call may cross several model-free program boundaries, but is bounded by
        # the tiny current projection and stops before any worker/provider execution.
        for _ in range(len(projection.nodes) + 2):
            node = projection.get(active.current_node_id)
            if node is None:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.BLOCKED,
                    reason="current program node is absent from accepted projection",
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.BLOCKED, active)

            missing = [
                prereq
                for prereq in node.prerequisites
                if not active.has_completed(prereq)
            ]
            if missing:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.BLOCKED,
                    reason=(
                        "program node prerequisites are not durably complete: "
                        + ", ".join(missing)
                    ),
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.BLOCKED, active)

            if node.kind is ProgramNodeKind.HUMAN_GATE:
                review = HumanReviewStore.for_root(root).load().latest_for_gate(
                    node.review_gate_id
                )
                if review is None or review.verdict is not HumanReviewVerdict.ACCEPTED:
                    reason = (
                        "program progression waits for exact durable ACCEPTED human review"
                        if review is None
                        else (
                            "latest durable human review is CHANGES_REQUIRED; "
                            "program successor remains locked"
                        )
                    )
                    active = replace(
                        active,
                        disposition=ProgramSessionDisposition.WAIT_HUMAN,
                        reason=reason,
                        gate_id=node.review_gate_id,
                        child_proposal_id="",
                    )
                    active = _save_session(root, active)
                    return _session_result(ProgramAdvanceDisposition.WAIT_HUMAN, active)

                successor = projection.successor(node.node_id)
                active = active.complete_node(
                    node_id=node.node_id,
                    evidence_id=review.review_id,
                    next_node_id=(successor.node_id if successor is not None else None),
                    reason="exact durable ACCEPTED human review unlocked program successor",
                )
                active = _save_session(root, active)
                if active.disposition is ProgramSessionDisposition.COMPLETE:
                    return _session_result(ProgramAdvanceDisposition.COMPLETE, active)
                continue

            if node.kind is ProgramNodeKind.STRATEGIC_GATE:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.WAIT_STRATEGIC,
                    reason=node.message,
                    gate_id=node.node_id,
                    child_proposal_id="",
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.WAIT_STRATEGIC, active)

            assert node.kind is ProgramNodeKind.TASK and node.issue_number is not None

            completed = _complete_from_child_trace(
                root=root,
                projection=projection,
                session=active,
                node=node,
            )
            if completed is not None:
                active = _save_session(root, completed)
                if active.disposition is ProgramSessionDisposition.COMPLETE:
                    return _session_result(ProgramAdvanceDisposition.COMPLETE, active)
                continue

            if (
                active.child_proposal_id
                and active.disposition is ProgramSessionDisposition.WAIT_CHILD
            ):
                return _session_result(
                    ProgramAdvanceDisposition.WAIT_CHILD,
                    active,
                )

            external = ExternalClaimStore.for_root(root).load().get(node.issue_number)
            if external is not None:
                suffix = f" / PR #{external.pr_number}" if external.pr_number else ""
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.WAIT_AUTHORITY,
                    reason=(
                        f"existing external producer authority owns issue "
                        f"#{node.issue_number}{suffix}; OPT-6 will not duplicate it"
                    ),
                    gate_id="",
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.WAIT_AUTHORITY, active)

            managed_plan = HostedTaskPlanStore.for_root(root).load().get_issue(
                node.issue_number
            )
            managed_admission = HostedAdmissionStore.for_root(root).load().for_issue(
                node.issue_number
            )
            if managed_plan is not None or managed_admission is not None:
                identities = []
                if managed_plan is not None:
                    identities.append(f"plan {managed_plan.plan_id}")
                if managed_admission is not None:
                    identities.append(f"admission {managed_admission.record_id}")
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.WAIT_AUTHORITY,
                    reason=(
                        f"existing managed Platform-v2 authority owns issue "
                        f"#{node.issue_number} ({', '.join(identities)}); "
                        "OPT-6 will not create duplicate child authority"
                    ),
                    gate_id="",
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.WAIT_AUTHORITY, active)

            hosted_state = HostedStateStore.for_root(root).load()
            signed_authorities = TaskAuthorityEventStore.for_root(root).load()
            pending_signed = tuple(
                event
                for event in hosted_state.inbox.pending_events
                if (
                    event.signal_kind == "task"
                    and event.protected_authority
                    and event.task_issue_number == node.issue_number
                    and signed_authorities.get(event.event_id) is not None
                )
            )
            if pending_signed:
                identities = ", ".join(event.event_id for event in pending_signed)
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.WAIT_AUTHORITY,
                    reason=(
                        f"pending signed Platform-v2 task authority owns issue "
                        f"#{node.issue_number} ({identities}); "
                        "OPT-6 will not rebuild child context or create duplicate authority"
                    ),
                    gate_id="",
                )
                active = _save_session(root, active)
                return _session_result(
                    ProgramAdvanceDisposition.WAIT_AUTHORITY,
                    active,
                )

            if not active.child_proposal_id:
                child = _child_record(
                    root=root,
                    repo=repo,
                    projection=projection,
                    session=active,
                    node=node,
                )
                captured = ObjectiveProposalStore.for_root(root).capture_record(child)
                active = replace(
                    active,
                    child_proposal_id=captured.record.proposal_id,
                    disposition=ProgramSessionDisposition.ADVANCING,
                    reason="deterministic program child objective persisted",
                    gate_id="",
                )
                active = _save_session(root, active)

            child_id = active.child_proposal_id
            child_control = effective_objective_progression(root, child_id)
            if not child_control.allowed:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.WAIT_CONTROL,
                    reason=child_control.reason,
                )
                active = _save_session(root, active)
                return _session_result(
                    ProgramAdvanceDisposition.WAIT_CONTROL,
                    active,
                )

            package, _ = package_proposal(root=root, proposal_id=child_id)
            if package.disposition is not ContextPackageDisposition.SCOPE_UNRESOLVED:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.BLOCKED,
                    reason=f"program child context package blocked: {package.reason}",
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.BLOCKED, active)

            retrieval, _ = retrieve_context(
                root=root,
                repo=repo,
                package_id=package.package_id,
                gh_runner=runner,
            )
            if retrieval.disposition is not ContextRetrievalDisposition.SCOPE_PROPOSED:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.BLOCKED,
                    reason=f"program child context retrieval blocked: {retrieval.reason}",
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.BLOCKED, active)

            promotion = validate_promotion(
                root=root,
                repo=repo,
                package=package,
                retrieval=retrieval,
                gh_runner=runner,
            )
            if promotion.disposition is not PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.BLOCKED,
                    reason=(
                        "program child promotion blocked: "
                        + ("; ".join(promotion.blockers) or promotion.reason)
                    ),
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.BLOCKED, active)
            PromotionStore.for_root(root).capture(promotion)

            posted = execute_frozen_promotion_post(
                root=root,
                repo=repo,
                runner=runner,
                promotion_id=promotion.digest,
            )
            if posted.disposition is ObjectivePromotionPostDisposition.BLOCKED:
                active = replace(
                    active,
                    disposition=ProgramSessionDisposition.BLOCKED,
                    reason=f"program child task-authority post blocked: {posted.reason}",
                )
                active = _save_session(root, active)
                return _session_result(ProgramAdvanceDisposition.BLOCKED, active)

            active = replace(
                active,
                disposition=ProgramSessionDisposition.WAIT_CHILD,
                reason=(
                    "program child task-authority comment is durable; wait for signed "
                    "webhook/execution/X-2 completion before advancing program node"
                ),
                gate_id="",
            )
            active = _save_session(root, active)
            return _session_result(ProgramAdvanceDisposition.WAIT_CHILD, active)

        active = replace(
            active,
            disposition=ProgramSessionDisposition.BLOCKED,
            reason="program continuation exceeded bounded projection advancement",
        )
        active = _save_session(root, active)
        return _session_result(ProgramAdvanceDisposition.BLOCKED, active)


def program_progression_snapshot(root: Path) -> dict[str, Any]:
    """Canonical read projection shared by development API / Console / MCP."""

    root = Path(root).resolve()
    ledger = ProgramContinuationStore.for_root(root).load()
    try:
        projection = load_program_projection(root)
        projection_state: dict[str, Any] = {
            "program_id": projection.program_id,
            "projection_digest": projection.digest,
            "source_valid": True,
            "source_error": "",
            "nodes": [
                {
                    "node_id": node.node_id,
                    "kind": node.kind.value,
                    "issue_number": node.issue_number,
                    "lane": node.lane or None,
                    "review_gate_id": node.review_gate_id or None,
                    "message": node.message or None,
                }
                for node in projection.nodes
            ],
        }
    except (OSError, ValueError) as exc:
        projection_state = {
            "program_id": "",
            "projection_digest": "",
            "source_valid": False,
            "source_error": f"{type(exc).__name__}: {exc}",
            "nodes": [],
        }
    return {
        "schema_version": 1,
        "projection": projection_state,
        "active_session": ledger.active.as_dict() if ledger.active is not None else None,
        "session_count": len(ledger.records),
        "sessions": [value.as_dict() for value in ledger.records],
    }
