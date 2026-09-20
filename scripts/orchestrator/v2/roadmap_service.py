"""Durable roadmap and human-gate authority service for Platform v2 R5C14.

This module is inert with respect to GitHub, workers, classifiers, and hosted production
authority. It turns accepted pure roadmap decisions into restart-safe local semantic state.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import re
from pathlib import Path
from typing import Any, Mapping

from .core import HumanGateRecord
from .cutover import LegacyOperationalProjection
from .events import DurableEvent
from .identity import canonical_digest
from .roadmap_recovery import (
    ClosedActiveDisposition,
    ClosedActiveRoadmapObservation,
    RoadmapIssueState,
    classify_closed_active_roadmap,
)
from .roadmap_shadow import (
    RoadmapNodeKind,
    RoadmapShadowDecision,
    RoadmapShadowDisposition,
    ShadowRoadmapManifest,
    ShadowRoadmapNode,
    ShadowRoadmapState,
)
from .state_store import JsonStateStoreAdapter


ROADMAP_AUTHORITY_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "roadmap-authority.json"
)
ROADMAP_AUTHORITY_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "roadmap-authority.json.bak"
)
PROGRAM_ROADMAP_PATH = "docs/agent-state/PROGRAM_ROADMAP.md"
MANIFEST_PATH = "docs/agent-state/ORCHESTRATOR_ROADMAP.json"
DEFAULT_PROGRAM_GATE_TARGET = "349"


def _required(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} is required")
    return value.strip()


def _positive(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def _nonnegative(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError(f"{label} must be a nonnegative integer")
    return value


@dataclass(frozen=True)
class RoadmapBlockRecord:
    node_id: str
    reason: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "node_id", _required(self.node_id, "block node_id"))
        object.__setattr__(self, "reason", _required(self.reason, "block reason"))

    @classmethod
    def from_mapping(cls, raw: Any) -> "RoadmapBlockRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("roadmap block must be an object")
        return cls(
            node_id=raw.get("node_id"),
            reason=raw.get("reason"),
        )

    def as_dict(self) -> dict[str, Any]:
        return {"node_id": self.node_id, "reason": self.reason}


@dataclass(frozen=True)
class RoadmapActiveAuthority:
    node_id: str
    issue_number: int
    lane: str
    run_number: int
    manifest_fingerprint: str
    event: DurableEvent
    pr_number: int | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "node_id", _required(self.node_id, "active node_id"))
        object.__setattr__(self, "issue_number", _positive(self.issue_number, "active issue_number"))
        object.__setattr__(self, "lane", _required(self.lane, "active lane"))
        object.__setattr__(self, "run_number", _positive(self.run_number, "active run_number"))
        object.__setattr__(
            self,
            "manifest_fingerprint",
            _required(self.manifest_fingerprint, "active manifest_fingerprint"),
        )
        if not isinstance(self.event, DurableEvent):
            raise ValueError("active event must be DurableEvent")
        if self.event.event != "roadmap" or self.event.signal_kind != "task":
            raise ValueError("active event must be protected roadmap task authority")
        if self.event.task_issue_number != self.issue_number:
            raise ValueError("active event issue identity mismatch")
        expected_source = (
            f"roadmap:{self.event_source_roadmap_id}:"
            f"{self.node_id}:run:{self.run_number}"
        )
        if self.event.source_id != expected_source:
            raise ValueError("active event source identity mismatch")
        if self.pr_number is not None:
            _positive(self.pr_number, "active pr_number")

    @property
    def event_source_roadmap_id(self) -> str:
        source = str(self.event.source_id or "")
        parts = source.split(":")
        if len(parts) < 5 or parts[0] != "roadmap":
            raise ValueError("active roadmap source_id is malformed")
        return parts[1]

    @classmethod
    def from_mapping(cls, raw: Any) -> "RoadmapActiveAuthority":
        if not isinstance(raw, Mapping):
            raise ValueError("roadmap active authority must be an object")
        pr = raw.get("pr_number")
        return cls(
            node_id=raw.get("node_id"),
            issue_number=raw.get("issue_number"),
            lane=raw.get("lane"),
            run_number=raw.get("run_number"),
            manifest_fingerprint=raw.get("manifest_fingerprint"),
            event=DurableEvent.from_legacy_mapping(raw.get("event")),
            pr_number=None if pr is None else _positive(pr, "active pr_number"),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "node_id": self.node_id,
            "issue_number": self.issue_number,
            "lane": self.lane,
            "run_number": self.run_number,
            "manifest_fingerprint": self.manifest_fingerprint,
            "event": self.event.as_dict(),
            "pr_number": self.pr_number,
        }


@dataclass(frozen=True)
class RoadmapAuthorityLedger:
    roadmap_id: str
    manifest_fingerprint: str
    completed_runs: tuple[tuple[str, int], ...] = ()
    blocked_nodes: tuple[RoadmapBlockRecord, ...] = ()
    active: RoadmapActiveAuthority | None = None
    claims_day: str | None = None
    claims_today: int = 0
    human_gate_records: tuple[HumanGateRecord, ...] = ()

    def __post_init__(self) -> None:
        object.__setattr__(self, "roadmap_id", _required(self.roadmap_id, "roadmap_id"))
        object.__setattr__(
            self,
            "manifest_fingerprint",
            _required(self.manifest_fingerprint, "manifest_fingerprint"),
        )
        seen_completed: set[str] = set()
        normalized_completed: list[tuple[str, int]] = []
        for node_id, count in self.completed_runs:
            node = _required(node_id, "completed node_id")
            if node in seen_completed:
                raise ValueError("duplicate completed node")
            seen_completed.add(node)
            normalized_completed.append((node, _nonnegative(count, "completed count")))
        object.__setattr__(self, "completed_runs", tuple(sorted(normalized_completed)))

        block_ids = [record.node_id for record in self.blocked_nodes]
        if len(block_ids) != len(set(block_ids)):
            raise ValueError("duplicate blocked roadmap node")
        object.__setattr__(
            self,
            "blocked_nodes",
            tuple(sorted(self.blocked_nodes, key=lambda record: record.node_id)),
        )

        if self.active is not None and not isinstance(self.active, RoadmapActiveAuthority):
            raise ValueError("active must be RoadmapActiveAuthority or null")
        if self.active is not None and self.active.event_source_roadmap_id != self.roadmap_id:
            raise ValueError("active authority roadmap_id mismatch")

        if self.claims_day is not None:
            object.__setattr__(self, "claims_day", _required(self.claims_day, "claims_day"))
        object.__setattr__(self, "claims_today", _nonnegative(self.claims_today, "claims_today"))

        gate_keys = [record.key for record in self.human_gate_records]
        if len(gate_keys) != len(set(gate_keys)):
            raise ValueError("duplicate human gate key")
        object.__setattr__(
            self,
            "human_gate_records",
            tuple(sorted(self.human_gate_records, key=lambda record: record.key)),
        )

    @classmethod
    def empty(cls, manifest: ShadowRoadmapManifest) -> "RoadmapAuthorityLedger":
        return cls(
            roadmap_id=manifest.roadmap_id,
            manifest_fingerprint=manifest.fingerprint,
        )

    @classmethod
    def from_mapping(cls, raw: Any) -> "RoadmapAuthorityLedger":
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid roadmap authority ledger")
        completed_raw = raw.get("completed_runs")
        blocked_raw = raw.get("blocked_nodes")
        gates_raw = raw.get("human_gate_records")
        if not isinstance(completed_raw, Mapping):
            raise ValueError("completed_runs must be an object")
        if not isinstance(blocked_raw, list):
            raise ValueError("blocked_nodes must be a list")
        if not isinstance(gates_raw, list):
            raise ValueError("human_gate_records must be a list")
        active_raw = raw.get("active")
        return cls(
            roadmap_id=raw.get("roadmap_id"),
            manifest_fingerprint=raw.get("manifest_fingerprint"),
            completed_runs=tuple(
                (str(key), _nonnegative(value, "completed count"))
                for key, value in completed_raw.items()
            ),
            blocked_nodes=tuple(RoadmapBlockRecord.from_mapping(value) for value in blocked_raw),
            active=(
                None
                if active_raw is None
                else RoadmapActiveAuthority.from_mapping(active_raw)
            ),
            claims_day=raw.get("claims_day"),
            claims_today=raw.get("claims_today", 0),
            human_gate_records=tuple(
                HumanGateRecord.from_legacy(
                    str(value.get("key") if isinstance(value, Mapping) else ""),
                    value,
                )
                for value in gates_raw
            ),
        )

    @classmethod
    def from_legacy_projection(
        cls,
        projection: LegacyOperationalProjection,
        manifest: ShadowRoadmapManifest,
    ) -> "RoadmapAuthorityLedger":
        if not isinstance(projection, LegacyOperationalProjection):
            raise ValueError("projection must be LegacyOperationalProjection")
        road = projection.roadmap
        if road.roadmap_id != manifest.roadmap_id:
            raise ValueError("projected roadmap_id does not match accepted manifest")
        if road.manifest_fingerprint != manifest.fingerprint:
            raise ValueError("projected manifest fingerprint does not match accepted manifest")

        by_id = {node.node_id: node for node in manifest.nodes}
        completed: list[tuple[str, int]] = []
        for node_id, count in road.completed_runs.items():
            if node_id not in by_id:
                raise ValueError("projected completed_runs contains unknown node")
            completed.append((node_id, _nonnegative(count, "projected completed count")))

        blocked: list[RoadmapBlockRecord] = []
        for node_id, value in road.blocked_nodes.items():
            if node_id not in by_id:
                raise ValueError("projected blocked_nodes contains unknown node")
            if not isinstance(value, Mapping):
                raise ValueError("projected blocked node must be an object")
            reason = str(value.get("reason") or "").strip()
            if not reason:
                raise ValueError("projected blocked node reason is required")
            blocked.append(RoadmapBlockRecord(node_id=node_id, reason=reason))

        active = None
        if road.active is not None:
            if not isinstance(road.active, Mapping):
                raise ValueError("projected active roadmap authority must be an object")
            raw_active = dict(road.active)
            node_id = str(raw_active.get("node_id") or "")
            node = by_id.get(node_id)
            if node is None or node.kind is not RoadmapNodeKind.TASK:
                raise ValueError("projected active roadmap node is not an accepted task")
            event_raw = raw_active.get("event")
            if not isinstance(event_raw, Mapping):
                raise ValueError("projected active roadmap authority lacks frozen event")
            active = RoadmapActiveAuthority(
                node_id=node_id,
                issue_number=_positive(raw_active.get("issue_number"), "projected active issue"),
                lane=_required(raw_active.get("lane"), "projected active lane"),
                run_number=_positive(raw_active.get("run_number"), "projected active run"),
                manifest_fingerprint=manifest.fingerprint,
                event=DurableEvent.from_legacy_mapping(event_raw),
                pr_number=(
                    None
                    if raw_active.get("pr_number") is None
                    else _positive(raw_active.get("pr_number"), "projected active pr")
                ),
            )

        return cls(
            roadmap_id=manifest.roadmap_id,
            manifest_fingerprint=manifest.fingerprint,
            completed_runs=tuple(completed),
            blocked_nodes=tuple(blocked),
            active=active,
            claims_day=road.claims_day,
            claims_today=road.claims_today,
            human_gate_records=projection.human_gate_records,
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "roadmap_id": self.roadmap_id,
            "manifest_fingerprint": self.manifest_fingerprint,
            "completed_runs": dict(self.completed_runs),
            "blocked_nodes": [record.as_dict() for record in self.blocked_nodes],
            "active": self.active.as_dict() if self.active is not None else None,
            "claims_day": self.claims_day,
            "claims_today": self.claims_today,
            "human_gate_records": [record.as_dict() for record in self.human_gate_records],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def validate_manifest(self, manifest: ShadowRoadmapManifest) -> None:
        if manifest.roadmap_id != self.roadmap_id:
            raise ValueError("roadmap ledger roadmap_id mismatch")
        if manifest.fingerprint != self.manifest_fingerprint:
            raise ValueError("roadmap ledger manifest fingerprint mismatch")

    def shadow_state(self, manifest: ShadowRoadmapManifest) -> ShadowRoadmapState:
        self.validate_manifest(manifest)
        return ShadowRoadmapState.from_legacy(
            {
                "roadmap_id": self.roadmap_id,
                "manifest_fingerprint": self.manifest_fingerprint,
                "completed_runs": dict(self.completed_runs),
                "blocked_nodes": {
                    record.node_id: {"reason": record.reason}
                    for record in self.blocked_nodes
                },
                "active": self.active.as_dict() if self.active is not None else None,
            },
            manifest,
        )

    def block_for(self, node_id: str) -> RoadmapBlockRecord | None:
        node = _required(node_id, "node_id")
        return next((record for record in self.blocked_nodes if record.node_id == node), None)

    def gate_for(self, key: str) -> HumanGateRecord | None:
        value = _required(key, "gate key")
        return next((record for record in self.human_gate_records if record.key == value), None)


@dataclass(frozen=True)
class RoadmapAuthorityStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "RoadmapAuthorityStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / ROADMAP_AUTHORITY_RELATIVE_PATH,
                backup_path=root / ROADMAP_AUTHORITY_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> RoadmapAuthorityLedger:
        return RoadmapAuthorityLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: RoadmapAuthorityLedger) -> RoadmapAuthorityLedger:
        self.adapter.save(ledger.as_dict())
        return ledger


def build_roadmap_event(
    manifest: ShadowRoadmapManifest,
    node: ShadowRoadmapNode,
    *,
    run_number: int,
) -> DurableEvent:
    if node.kind is not RoadmapNodeKind.TASK:
        raise ValueError("only task nodes create roadmap task authority")
    if node.issue_number is None or node.lane is None:
        raise ValueError("roadmap task node lacks issue/lane authority")
    _positive(run_number, "run_number")
    signal = (
        f"AUDIT — ROADMAP TASK AUTHORITY: {node.node_id}\n"
        f"Canonical roadmap: {PROGRAM_ROADMAP_PATH}\n"
        f"Machine manifest: {MANIFEST_PATH}\n"
        f"Authorized issue: #{node.issue_number}\n"
        f"Lane hint: {node.lane}\n"
        f"Bounded objective hint: {node.objective_hint}\n"
        f"Stop boundary: {node.stop_boundary}\n"
        "This authority exists only for the smallest still-unaccepted tranche consistent with current "
        "main, lane ownership, validation policy, and the issue body. Do not recreate accepted work. "
        "If the remaining uncertainty is human/product/manual/external-evidence-only, return HUMAN_GATE "
        "or NOOP rather than inventing another implementation tranche."
    )
    return DurableEvent(
        actionable=True,
        reason="explicit bounded roadmap successor authority",
        event="roadmap",
        action="advance",
        pr_number=node.issue_number,
        source_id=f"roadmap:{manifest.roadmap_id}:{node.node_id}:run:{run_number}",
        signal_kind="task",
        signal_text=signal,
    )


def roadmap_gate_record(
    node: ShadowRoadmapNode,
    *,
    target: str = DEFAULT_PROGRAM_GATE_TARGET,
    seeded_from_github: bool = False,
) -> HumanGateRecord:
    if node.kind is not RoadmapNodeKind.GATE:
        raise ValueError("roadmap gate record requires gate node")
    lane = str(node.lane or "program").strip().lower() or "program"
    message = _required(node.human_message, "roadmap gate human_message")
    normalized = re.sub(r"[^a-z0-9]+", " ", message.lower()).strip()
    token = hashlib.sha256(normalized.encode("utf-8")).hexdigest()
    target = _required(target, "gate target")
    return HumanGateRecord(
        key=f"issue:{target}:{lane}",
        token=token,
        target=target,
        seeded_from_github=seeded_from_github,
    )


class RoadmapApplyDisposition(str, Enum):
    TASK_SEEDED = "TASK_SEEDED"
    GATE_BLOCKED = "GATE_BLOCKED"
    BLOCKED = "BLOCKED"
    EXHAUSTED = "EXHAUSTED"
    DAILY_BOUND = "DAILY_BOUND"


@dataclass(frozen=True)
class RoadmapApplyResult:
    disposition: RoadmapApplyDisposition
    reason: str
    ledger: RoadmapAuthorityLedger
    event: DurableEvent | None = None
    gate_record: HumanGateRecord | None = None
    gate_visibility_required: bool = False

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "ledger_digest": self.ledger.digest,
                "event_id": self.event.event_id if self.event is not None else "",
                "gate_record": self.gate_record.as_dict() if self.gate_record else None,
                "gate_visibility_required": self.gate_visibility_required,
            }
        )


def _with_projected_state(
    ledger: RoadmapAuthorityLedger,
    decision: RoadmapShadowDecision,
) -> RoadmapAuthorityLedger:
    existing_reasons = {record.node_id: record.reason for record in ledger.blocked_nodes}
    blocks = tuple(
        RoadmapBlockRecord(
            node_id=node_id,
            reason=existing_reasons.get(node_id, "roadmap node remains blocked"),
        )
        for node_id in decision.projected_blocked_nodes
    )
    return RoadmapAuthorityLedger(
        roadmap_id=ledger.roadmap_id,
        manifest_fingerprint=ledger.manifest_fingerprint,
        completed_runs=decision.projected_completed_runs,
        blocked_nodes=blocks,
        active=ledger.active,
        claims_day=ledger.claims_day,
        claims_today=ledger.claims_today,
        human_gate_records=ledger.human_gate_records,
    )


def _replace(
    ledger: RoadmapAuthorityLedger,
    *,
    completed_runs=None,
    blocked_nodes=None,
    active: RoadmapActiveAuthority | None | object = ...,
    claims_day=None,
    claims_today=None,
    human_gate_records=None,
) -> RoadmapAuthorityLedger:
    return RoadmapAuthorityLedger(
        roadmap_id=ledger.roadmap_id,
        manifest_fingerprint=ledger.manifest_fingerprint,
        completed_runs=(
            ledger.completed_runs if completed_runs is None else completed_runs
        ),
        blocked_nodes=ledger.blocked_nodes if blocked_nodes is None else blocked_nodes,
        active=ledger.active if active is ... else active,
        claims_day=ledger.claims_day if claims_day is None else claims_day,
        claims_today=ledger.claims_today if claims_today is None else claims_today,
        human_gate_records=(
            ledger.human_gate_records
            if human_gate_records is None
            else human_gate_records
        ),
    )


def apply_roadmap_decision(
    *,
    ledger: RoadmapAuthorityLedger,
    manifest: ShadowRoadmapManifest,
    decision: RoadmapShadowDecision,
    utc_day: str,
) -> RoadmapApplyResult:
    ledger.validate_manifest(manifest)
    if decision.state_digest != ledger.shadow_state(manifest).digest:
        return RoadmapApplyResult(
            RoadmapApplyDisposition.BLOCKED,
            "roadmap decision was computed from stale durable state",
            ledger,
        )

    projected = _with_projected_state(ledger, decision)
    if decision.disposition is RoadmapShadowDisposition.BLOCK:
        return RoadmapApplyResult(
            RoadmapApplyDisposition.BLOCKED,
            decision.reason,
            projected,
        )
    if decision.disposition is RoadmapShadowDisposition.EXHAUSTED:
        return RoadmapApplyResult(
            RoadmapApplyDisposition.EXHAUSTED,
            decision.reason,
            projected,
        )

    by_id = {node.node_id: node for node in manifest.nodes}
    node = by_id.get(str(decision.selected_node_id or ""))
    if node is None:
        return RoadmapApplyResult(
            RoadmapApplyDisposition.BLOCKED,
            "roadmap decision selected unknown node",
            projected,
        )

    if decision.disposition is RoadmapShadowDisposition.HUMAN_GATE:
        if node.kind is not RoadmapNodeKind.GATE:
            return RoadmapApplyResult(
                RoadmapApplyDisposition.BLOCKED,
                "human-gate decision selected non-gate node",
                projected,
            )
        reason = _required(node.human_message, "roadmap gate message")
        blocks = [
            record for record in projected.blocked_nodes if record.node_id != node.node_id
        ]
        blocks.append(RoadmapBlockRecord(node_id=node.node_id, reason=reason))
        gate = roadmap_gate_record(node)
        prior = projected.gate_for(gate.key)
        gated = _replace(projected, blocked_nodes=tuple(blocks))
        return RoadmapApplyResult(
            RoadmapApplyDisposition.GATE_BLOCKED,
            "eligible roadmap human gate durably blocked; machines may not pass it",
            gated,
            gate_record=gate,
            gate_visibility_required=(
                prior is None or prior.token != gate.token or prior.target != gate.target
            ),
        )

    if node.kind is not RoadmapNodeKind.TASK or node.issue_number is None or node.lane is None:
        return RoadmapApplyResult(
            RoadmapApplyDisposition.BLOCKED,
            "task-eligible decision selected malformed task node",
            projected,
        )
    if projected.active is not None:
        return RoadmapApplyResult(
            RoadmapApplyDisposition.BLOCKED,
            "roadmap already has active durable task authority",
            projected,
        )

    day = _required(utc_day, "utc_day")
    claims_today = projected.claims_today if projected.claims_day == day else 0
    if claims_today >= manifest.max_auto_claims_per_utc_day:
        bounded = _replace(projected, claims_day=day, claims_today=claims_today)
        return RoadmapApplyResult(
            RoadmapApplyDisposition.DAILY_BOUND,
            "bounded roadmap daily claim ceiling reached",
            bounded,
        )

    completed = dict(projected.completed_runs)
    run_number = int(completed.get(node.node_id) or 0) + 1
    if run_number > node.max_runs:
        return RoadmapApplyResult(
            RoadmapApplyDisposition.BLOCKED,
            "selected task run would exceed accepted max_runs",
            projected,
        )
    event = build_roadmap_event(manifest, node, run_number=run_number)
    active = RoadmapActiveAuthority(
        node_id=node.node_id,
        issue_number=node.issue_number,
        lane=node.lane,
        run_number=run_number,
        manifest_fingerprint=manifest.fingerprint,
        event=event,
    )
    seeded = _replace(
        projected,
        active=active,
        claims_day=day,
        claims_today=claims_today + 1,
    )
    return RoadmapApplyResult(
        RoadmapApplyDisposition.TASK_SEEDED,
        "exact roadmap task authority frozen and ready for durable enqueue",
        seeded,
        event=event,
    )


def record_gate_visibility(
    ledger: RoadmapAuthorityLedger,
    record: HumanGateRecord,
) -> RoadmapAuthorityLedger:
    if not isinstance(record, HumanGateRecord):
        raise ValueError("record must be HumanGateRecord")
    records = [item for item in ledger.human_gate_records if item.key != record.key]
    records.append(record)
    return _replace(ledger, human_gate_records=tuple(records))


def bind_active_pr(
    ledger: RoadmapAuthorityLedger,
    *,
    issue_number: int,
    pr_number: int,
) -> RoadmapAuthorityLedger:
    issue = _positive(issue_number, "issue_number")
    pr = _positive(pr_number, "pr_number")
    active = ledger.active
    if active is None:
        raise ValueError("cannot bind PR without active roadmap authority")
    if active.issue_number != issue:
        raise ValueError("managed PR issue does not match active roadmap authority")
    if active.pr_number is not None and active.pr_number != pr:
        raise ValueError("active roadmap authority is already bound to a different PR")
    rebound = RoadmapActiveAuthority(
        node_id=active.node_id,
        issue_number=active.issue_number,
        lane=active.lane,
        run_number=active.run_number,
        manifest_fingerprint=active.manifest_fingerprint,
        event=active.event,
        pr_number=pr,
    )
    return _replace(ledger, active=rebound)


@dataclass(frozen=True)
class ActiveRecoveryResult:
    disposition: ClosedActiveDisposition
    reason: str
    ledger: RoadmapAuthorityLedger

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "ledger_digest": self.ledger.digest,
            }
        )


def reconcile_active_issue(
    *,
    ledger: RoadmapAuthorityLedger,
    manifest: ShadowRoadmapManifest,
    issue_state: RoadmapIssueState,
) -> ActiveRecoveryResult:
    ledger.validate_manifest(manifest)
    if not isinstance(issue_state, RoadmapIssueState):
        raise ValueError("issue_state must be RoadmapIssueState")
    active = ledger.active
    if active is None:
        raise ValueError("roadmap has no active authority to reconcile")
    node = next((node for node in manifest.nodes if node.node_id == active.node_id), None)
    if node is None or node.kind is not RoadmapNodeKind.TASK:
        raise ValueError("active roadmap node is absent from accepted manifest")

    observation = ClosedActiveRoadmapObservation(
        node_id=active.node_id,
        issue_number=active.issue_number,
        max_runs=node.max_runs,
        issue_state=issue_state,
        pr_number=active.pr_number,
    )
    decision = classify_closed_active_roadmap(observation)
    updated = ledger
    if decision.disposition is ClosedActiveDisposition.COMPLETE_NODE:
        completed = dict(ledger.completed_runs)
        completed[active.node_id] = max(
            int(completed.get(active.node_id) or 0),
            int(decision.completed_runs_target or node.max_runs),
        )
        updated = _replace(
            ledger,
            completed_runs=tuple(completed.items()),
            active=None,
        )
    return ActiveRecoveryResult(
        disposition=decision.disposition,
        reason=decision.reason,
        ledger=updated,
    )


class HumanGateReviewDisposition(str, Enum):
    ACCEPTED = "ACCEPTED"
    CHANGES_REQUIRED = "CHANGES_REQUIRED"
    ALREADY_ACCEPTED = "ALREADY_ACCEPTED"
    BLOCKED = "BLOCKED"


@dataclass(frozen=True)
class HumanGateReviewResult:
    disposition: HumanGateReviewDisposition
    reason: str
    ledger: RoadmapAuthorityLedger
    gate_id: str
    review_id: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "ledger_digest": self.ledger.digest,
                "gate_id": self.gate_id,
                "review_id": self.review_id,
            }
        )


def apply_human_gate_review(
    *,
    ledger: RoadmapAuthorityLedger,
    manifest: ShadowRoadmapManifest,
    gate_id: str,
    verdict: str,
    review_id: str,
) -> HumanGateReviewResult:
    """Reconcile one durable human judgment into roadmap authority.

    This reducer never creates human judgment. It consumes an already-durable review
    identity and changes only the exact accepted gate node named by that review.
    """

    ledger.validate_manifest(manifest)
    gate = _required(gate_id, "gate_id")
    review = _required(review_id, "review_id")
    normalized_verdict = _required(verdict, "verdict").upper()
    if normalized_verdict not in {"ACCEPTED", "CHANGES_REQUIRED"}:
        raise ValueError("verdict must be ACCEPTED or CHANGES_REQUIRED")

    node = next((item for item in manifest.nodes if item.node_id == gate), None)
    if node is None:
        return HumanGateReviewResult(
            HumanGateReviewDisposition.BLOCKED,
            "human review references an unknown roadmap node",
            ledger,
            gate,
            review,
        )
    if node.kind is not RoadmapNodeKind.GATE:
        return HumanGateReviewResult(
            HumanGateReviewDisposition.BLOCKED,
            "human review may reconcile only a roadmap gate node",
            ledger,
            gate,
            review,
        )
    if ledger.active is not None:
        return HumanGateReviewResult(
            HumanGateReviewDisposition.BLOCKED,
            "roadmap has active task authority; human gate review cannot rewrite concurrent authority",
            ledger,
            gate,
            review,
        )

    completed = dict(ledger.completed_runs)
    completed_count = int(completed.get(gate) or 0)
    if completed_count >= node.max_runs:
        if normalized_verdict == "ACCEPTED":
            return HumanGateReviewResult(
                HumanGateReviewDisposition.ALREADY_ACCEPTED,
                "roadmap gate is already complete",
                ledger,
                gate,
                review,
            )
        return HumanGateReviewResult(
            HumanGateReviewDisposition.BLOCKED,
            "completed roadmap gate cannot be changed back to CHANGES_REQUIRED",
            ledger,
            gate,
            review,
        )

    current_block = ledger.block_for(gate)
    if current_block is None:
        return HumanGateReviewResult(
            HumanGateReviewDisposition.BLOCKED,
            "roadmap gate is not currently blocked for human review",
            ledger,
            gate,
            review,
        )

    if normalized_verdict == "CHANGES_REQUIRED":
        blocks = [
            record for record in ledger.blocked_nodes if record.node_id != gate
        ]
        blocks.append(
            RoadmapBlockRecord(
                node_id=gate,
                reason=f"human review CHANGES_REQUIRED; durable review {review}",
            )
        )
        updated = _replace(ledger, blocked_nodes=tuple(blocks))
        return HumanGateReviewResult(
            HumanGateReviewDisposition.CHANGES_REQUIRED,
            "durable human review requires changes; gate remains blocked",
            updated,
            gate,
            review,
        )

    completed[gate] = max(completed_count, node.max_runs)
    blocks = tuple(
        record for record in ledger.blocked_nodes if record.node_id != gate
    )

    # Human-gate visibility is a current-state projection, not history. Once this
    # exact gate is accepted, retire only the matching visibility record. The
    # authoritative human judgment remains in the append-only review ledger.
    expected_gate = roadmap_gate_record(node)
    gate_records = tuple(
        record
        for record in ledger.human_gate_records
        if not (
            record.key == expected_gate.key
            and record.token == expected_gate.token
            and record.target == expected_gate.target
        )
    )
    updated = _replace(
        ledger,
        completed_runs=tuple(completed.items()),
        blocked_nodes=blocks,
        human_gate_records=gate_records,
    )
    return HumanGateReviewResult(
        HumanGateReviewDisposition.ACCEPTED,
        "durable human review accepted the exact blocked gate",
        updated,
        gate,
        review,
    )

