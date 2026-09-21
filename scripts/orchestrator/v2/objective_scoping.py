from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
import json
from pathlib import Path
import re
import subprocess
from typing import Any, Callable, Mapping, Sequence

from .classifier_provider import (
    ClassifierAdvanceDisposition,
    ClassifierProvider,
    ClassifierProviderConfig,
    ClassifierRequest,
    ClassifierRunStore,
    advance_classifier,
    classifier_provider_config,
)
from .context_package import ContextPackageStore, package_proposal, repository_identity
from .context_retrieval import ContextRetrievalStore, retrieve_context
from .decision import DecisionKind
from .identity import canonical_digest
from .objective_ingress import (
    DevelopmentApiObjectiveSource,
    ObjectiveProposalRecord,
    ObjectiveProposalStore,
    ScopedObjectiveSource,
)
from .objective_intake import (
    ObjectiveCandidateTask,
    ObjectiveCompileDisposition,
    ObjectiveCompileResult,
    ObjectiveIntent,
)
from .objective_lifecycle import effective_objective_progression
from .objective_promotion_effect import (
    ObjectivePromotionPostDisposition,
    execute_frozen_promotion_post,
)
from .quota import (
    LocalBudgetObservation,
    ProviderQuotaDecision,
    QuotaAdmissionDisposition,
    classify_quota_admission,
)
from .scope_promotion import PromotionDisposition, PromotionStore, validate_promotion
from .state_store import JsonStateStoreAdapter


OBJECTIVE_SCOPING_RELATIVE_PATH = Path(".skyforge-platform-v2/objective-scoping.json")
OBJECTIVE_SCOPING_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2/objective-scoping.json.bak")

_ALLOWED_LANES = {
    "implementation": "Implementation",
    "authorship": "Authorship",
    "content": "Content",
    "music": "Music",
    "presentation": "Presentation",
    "audit": "Audit",
}
_PROTECTED_PREFIXES = (".github/", "deploy/orchestrator/", "scripts/orchestrator/")
_PROTECTED_EXACT = {
    "docs/agent-state/ORCHESTRATOR_ROADMAP.json",
    "docs/agent-state/PROGRAM_PROGRESSION.json",
    "docs/architecture/SKYFORGE_DEVELOPMENT_PLATFORM_OPTIMIZATION_ROADMAP.md",
}
_MAX_SCOPE_PATHS = 12
_ISSUE_MARKER_PREFIX = "<!-- skyforge:objective-scope:"
_ISSUE_MARKER_SUFFIX = " -->"


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _sha40(value: Any, label: str) -> str:
    text = _required(value, label).lower()
    if len(text) != 40 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase 40-character Git SHA")
    return text


def _sha64(value: Any, label: str) -> str:
    text = _required(value, label).lower()
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


def _exact_path(value: Any) -> str:
    text = _required(value, "scope path").replace("\\", "/")
    if text.startswith("/") or text.startswith("./") or re.match(r"^[A-Za-z]:/", text):
        raise ValueError("scope path must be repository-relative")
    if any(part in {"", ".", ".."} for part in text.split("/")):
        raise ValueError("scope path must be normalized")
    if any(token in text for token in ("*", "?", "[", "]", "{", "}")):
        raise ValueError("scope path must be exact")
    return text


def _protected(path: str) -> bool:
    return path in _PROTECTED_EXACT or any(path.startswith(prefix) for prefix in _PROTECTED_PREFIXES)


def _tracked_paths(root: Path) -> set[str]:
    completed = subprocess.run(
        ["git", "-C", str(root), "-c", f"safe.directory={root}", "ls-files", "-z"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return {part.decode("utf-8") for part in completed.stdout.split(b"\0") if part}


class ObjectiveScopeStatus(str, Enum):
    PREPARED = "PREPARED"
    SCOPED = "SCOPED"
    ISSUE_READY = "ISSUE_READY"
    CHILD_READY = "CHILD_READY"
    PACKAGE_READY = "PACKAGE_READY"
    RETRIEVAL_READY = "RETRIEVAL_READY"
    PROMOTION_READY = "PROMOTION_READY"
    AUTHORITY_POSTED = "AUTHORITY_POSTED"
    HUMAN_GATE = "HUMAN_GATE"
    BLOCKED = "BLOCKED"
    RECOVERY_REQUIRED = "RECOVERY_REQUIRED"


_TERMINAL = {
    ObjectiveScopeStatus.AUTHORITY_POSTED,
    ObjectiveScopeStatus.HUMAN_GATE,
    ObjectiveScopeStatus.BLOCKED,
    ObjectiveScopeStatus.RECOVERY_REQUIRED,
}


@dataclass(frozen=True)
class ObjectiveScopeRecord:
    parent_proposal_id: str
    accepted_main_sha: str
    classifier_request_id: str
    status: ObjectiveScopeStatus
    reason: str
    classifier_run_id: str = ""
    lane: str = ""
    stop_boundary: str = ""
    proposed_paths: tuple[str, ...] = ()
    issue_number: int | None = None
    child_proposal_id: str = ""
    package_id: str = ""
    retrieval_id: str = ""
    promotion_id: str = ""
    remote_identity: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "parent_proposal_id", _sha64(self.parent_proposal_id, "parent_proposal_id"))
        object.__setattr__(self, "accepted_main_sha", _sha40(self.accepted_main_sha, "accepted_main_sha"))
        object.__setattr__(self, "classifier_request_id", _sha64(self.classifier_request_id, "classifier_request_id"))
        if not isinstance(self.status, ObjectiveScopeStatus):
            object.__setattr__(self, "status", ObjectiveScopeStatus(str(self.status)))
        object.__setattr__(self, "reason", _required(self.reason, "scope reason"))
        if self.classifier_run_id:
            object.__setattr__(self, "classifier_run_id", _sha64(self.classifier_run_id, "classifier_run_id"))
        if self.issue_number is not None and (
            isinstance(self.issue_number, bool) or not isinstance(self.issue_number, int) or self.issue_number <= 0
        ):
            raise ValueError("issue_number must be a positive integer or null")
        for name in ("child_proposal_id", "package_id", "retrieval_id", "promotion_id"):
            value = getattr(self, name)
            if value:
                object.__setattr__(self, name, _sha64(value, name))
        paths = tuple(_exact_path(path) for path in self.proposed_paths)
        if len(paths) != len(set(paths)):
            raise ValueError("scope proposed_paths contain duplicates")
        if len(paths) > _MAX_SCOPE_PATHS:
            raise ValueError("scope proposed_paths exceed bounded maximum")
        object.__setattr__(self, "proposed_paths", paths)

    @property
    def terminal(self) -> bool:
        return self.status in _TERMINAL

    @property
    def scope_digest(self) -> str:
        if not self.lane or not self.stop_boundary or not self.proposed_paths:
            return ""
        return canonical_digest(
            {
                "parent_proposal_id": self.parent_proposal_id,
                "accepted_main_sha": self.accepted_main_sha,
                "classifier_request_id": self.classifier_request_id,
                "classifier_run_id": self.classifier_run_id,
                "lane": self.lane,
                "stop_boundary": self.stop_boundary,
                "proposed_paths": list(self.proposed_paths),
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "parent_proposal_id": self.parent_proposal_id,
            "accepted_main_sha": self.accepted_main_sha,
            "classifier_request_id": self.classifier_request_id,
            "status": self.status.value,
            "reason": self.reason,
            "classifier_run_id": self.classifier_run_id,
            "lane": self.lane,
            "stop_boundary": self.stop_boundary,
            "proposed_paths": list(self.proposed_paths),
            "issue_number": self.issue_number,
            "child_proposal_id": self.child_proposal_id,
            "package_id": self.package_id,
            "retrieval_id": self.retrieval_id,
            "promotion_id": self.promotion_id,
            "remote_identity": self.remote_identity,
            "scope_digest": self.scope_digest,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveScopeRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("objective scope record must be an object")
        paths = raw.get("proposed_paths") or []
        if not isinstance(paths, list):
            raise ValueError("objective scope proposed_paths must be a list")
        record = cls(
            parent_proposal_id=raw.get("parent_proposal_id"),
            accepted_main_sha=raw.get("accepted_main_sha"),
            classifier_request_id=raw.get("classifier_request_id"),
            status=ObjectiveScopeStatus(str(raw.get("status") or "")),
            reason=raw.get("reason"),
            classifier_run_id=str(raw.get("classifier_run_id") or ""),
            lane=str(raw.get("lane") or ""),
            stop_boundary=str(raw.get("stop_boundary") or ""),
            proposed_paths=tuple(str(v) for v in paths),
            issue_number=raw.get("issue_number"),
            child_proposal_id=str(raw.get("child_proposal_id") or ""),
            package_id=str(raw.get("package_id") or ""),
            retrieval_id=str(raw.get("retrieval_id") or ""),
            promotion_id=str(raw.get("promotion_id") or ""),
            remote_identity=str(raw.get("remote_identity") or ""),
        )
        recorded = str(raw.get("scope_digest") or "")
        if recorded and recorded != record.scope_digest:
            raise ValueError("objective scope digest mismatch")
        return record


@dataclass(frozen=True)
class ObjectiveScopeLedger:
    records: tuple[ObjectiveScopeRecord, ...] = ()

    def get(self, parent_proposal_id: str) -> ObjectiveScopeRecord | None:
        key = _sha64(parent_proposal_id, "parent_proposal_id")
        matches = [record for record in self.records if record.parent_proposal_id == key]
        if len(matches) > 1:
            raise ValueError("duplicate objective scope parent identity")
        return matches[0] if matches else None

    def put(self, record: ObjectiveScopeRecord) -> "ObjectiveScopeLedger":
        current = self.get(record.parent_proposal_id)
        if current is not None and (
            current.accepted_main_sha != record.accepted_main_sha
            or current.classifier_request_id != record.classifier_request_id
        ):
            raise ValueError("objective scope immutable identity drifted")
        if current is None:
            return ObjectiveScopeLedger(self.records + (record,))
        return ObjectiveScopeLedger(
            tuple(record if value.parent_proposal_id == record.parent_proposal_id else value for value in self.records)
        )

    def as_dict(self) -> dict[str, Any]:
        return {"schema_version": 1, "records": [record.as_dict() for record in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveScopeLedger":
        if raw in (None, {}):
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid objective scope ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("objective scope records must be a list")
        ledger = cls()
        for value in values:
            ledger = ledger.put(ObjectiveScopeRecord.from_mapping(value))
        return ledger


@dataclass(frozen=True)
class ObjectiveScopeStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ObjectiveScopeStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / OBJECTIVE_SCOPING_RELATIVE_PATH,
                backup_path=root / OBJECTIVE_SCOPING_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> ObjectiveScopeLedger:
        return ObjectiveScopeLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ObjectiveScopeLedger) -> ObjectiveScopeLedger:
        self.adapter.save(ledger.as_dict())
        return ledger

    def put(self, record: ObjectiveScopeRecord) -> ObjectiveScopeRecord:
        self.save(self.load().put(record))
        return record


class ObjectiveScopingAdvanceDisposition(str, Enum):
    NO_WORK = "NO_WORK"
    PREPARED = "PREPARED"
    CLASSIFIER_ADVANCED = "CLASSIFIER_ADVANCED"
    ISSUE_ADVANCED = "ISSUE_ADVANCED"
    CHILD_CREATED = "CHILD_CREATED"
    PACKAGE_CREATED = "PACKAGE_CREATED"
    RETRIEVAL_CREATED = "RETRIEVAL_CREATED"
    PROMOTION_CREATED = "PROMOTION_CREATED"
    AUTHORITY_POSTED = "AUTHORITY_POSTED"
    HUMAN_GATE = "HUMAN_GATE"
    BLOCKED = "BLOCKED"
    RECOVERY_REQUIRED = "RECOVERY_REQUIRED"
    WAIT_CONTROL = "WAIT_CONTROL"
    QUOTA_WAIT = "QUOTA_WAIT"


@dataclass(frozen=True)
class ObjectiveScopingAdvanceResult:
    disposition: ObjectiveScopingAdvanceDisposition
    reason: str
    record: ObjectiveScopeRecord | None = None
    changed: bool = False

    @property
    def durable_identity(self) -> str:
        return self.record.parent_proposal_id if self.record else ""


def _scope_request(parent: ObjectiveProposalRecord, accepted_main_sha: str) -> ClassifierRequest:
    request = parent.compiled.request
    if request is None or request.intent not in {ObjectiveIntent.FIX, ObjectiveIntent.DEVELOP}:
        raise ValueError("standalone scoping requires FIX or DEVELOP parent objective")
    semantic_input = {
        "kind": "OBJECTIVE_SCOPE",
        "objective": parent.source.objective_text,
        "intent": request.intent.value,
        "target": request.target,
        "allowed_lanes": list(_ALLOWED_LANES.values()),
        "rules": [
            "Return DISPATCH only when one bounded repository lane is clear.",
            "DISPATCH must include a non-empty exact tracked-file allowed_paths list of at most 12 paths.",
            "Never use wildcards, directories, generated state, or whole-repository scope.",
            "Return HUMAN_GATE when product semantics, lane ownership, or safe bounded scope is genuinely ambiguous.",
            "Do not propose MERGE.",
            "Control-plane paths under .github/, deploy/orchestrator/, or scripts/orchestrator/ are protected and must not be treated as ordinary autonomous scope.",
        ],
    }
    return ClassifierRequest(current_main=accepted_main_sha, semantic_input=semantic_input, instructions_version=2)


def _canonical_lane(value: str | None) -> str | None:
    if value is None:
        return None
    return _ALLOWED_LANES.get(str(value).strip().lower())


def _validated_paths(root: Path, raw_paths: Sequence[str] | None) -> tuple[tuple[str, ...], tuple[str, ...]]:
    if not raw_paths:
        raise ValueError("scoping DISPATCH requires non-empty exact allowed_paths")
    if len(raw_paths) > _MAX_SCOPE_PATHS:
        raise ValueError("scoping allowed_paths exceed bounded maximum")
    tracked = _tracked_paths(root)
    paths: list[str] = []
    protected: list[str] = []
    for raw in raw_paths:
        path = _exact_path(raw)
        if path not in tracked:
            raise ValueError(f"scoping path is not a tracked file: {path}")
        if path in paths:
            continue
        paths.append(path)
        if _protected(path):
            protected.append(path)
    if not paths:
        raise ValueError("scoping exact path set is empty")
    return tuple(paths), tuple(protected)


def _issue_title(parent: ObjectiveProposalRecord) -> str:
    target = parent.compiled.request.target if parent.compiled.request else parent.source.objective_text
    compact = " ".join(str(target).split())
    if len(compact) > 180:
        compact = compact[:177] + "..."
    return f"OBJECTIVE: {compact}"


def _issue_marker(scope_digest: str) -> str:
    return f"{_ISSUE_MARKER_PREFIX}{_sha64(scope_digest, 'scope_digest')}{_ISSUE_MARKER_SUFFIX}"


def _issue_body(parent: ObjectiveProposalRecord, record: ObjectiveScopeRecord) -> str:
    if not record.scope_digest:
        raise ValueError("scope digest is unavailable before issue creation")
    paths = "\n".join(f"- `{path}`" for path in record.proposed_paths)
    return (
        f"{_issue_marker(record.scope_digest)}\n\n"
        "## Development-client objective\n\n"
        f"{parent.source.objective_text}\n\n"
        "## Bounded scope proposal\n\n"
        f"- Parent proposal: {parent.proposal_id}\n"
        f"- Accepted main: {record.accepted_main_sha}\n"
        f"- Lane: {record.lane}\n"
        f"- Stop boundary: {record.stop_boundary}\n\n"
        "Exact candidate paths:\n"
        f"{paths}\n\n"
        "This issue is controller-created scoping evidence, not executable task authority. "
        "The existing context retrieval, promotion validation, and typed task-authority boundary "
        "must still validate exact current repository/issue/path evidence before worker admission."
    )


def _issue_list_command(repo: str) -> tuple[str, ...]:
    return (
        "gh",
        "api",
        f"repos/{repo}/issues?state=all&per_page=100",
        "--paginate",
        "--jq",
        ".[] | {number: .number, title: .title, body: .body}",
    )


def _issue_create_command(repo: str, title: str, body: str) -> tuple[str, ...]:
    return ("gh", "api", "--method", "POST", f"repos/{repo}/issues", "-f", f"title={title}", "-f", f"body={body}", "--jq", ".number")


def _run_exact(
    *,
    root: Path,
    args: Sequence[str],
    expected: Sequence[str],
    runner: Callable[..., subprocess.CompletedProcess[str]],
    mutation: bool = False,
) -> str:
    command = tuple(str(v) for v in args)
    if command != tuple(str(v) for v in expected):
        raise ValueError("objective scoping command is outside the exact allowlist")
    completed = runner(
        list(command),
        cwd=root,
        check=True,
        text=True,
        capture_output=True,
        timeout=120 if mutation else 60,
    )
    return str(completed.stdout or "").strip()


def observe_scoped_issue(
    *,
    root: Path,
    repo: str,
    title: str,
    body: str,
    scope_digest: str,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> int | None:
    command = _issue_list_command(repo)
    output = _run_exact(root=root, args=command, expected=command, runner=runner)
    items: list[Mapping[str, Any]] = []
    for line in output.splitlines():
        text = line.strip()
        if not text:
            continue
        try:
            item = json.loads(text)
        except json.JSONDecodeError as exc:
            raise ValueError("objective issue observation returned malformed JSON line") from exc
        if not isinstance(item, Mapping):
            raise ValueError("objective issue observation returned malformed shape")
        items.append(item)
    marker = _issue_marker(scope_digest)
    marked = [item for item in items if marker in str(item.get("body") or "")]
    if not marked:
        return None
    exact = [item for item in marked if str(item.get("title") or "") == title and str(item.get("body") or "") == body]
    if len(marked) != 1 or len(exact) != 1:
        raise ValueError("objective scope issue marker is duplicated or conflicts remotely")
    number = exact[0].get("number")
    if isinstance(number, bool) or not isinstance(number, int) or number <= 0:
        raise ValueError("objective scope issue number is malformed")
    return number


def ensure_scoped_issue(
    *,
    root: Path,
    repo: str,
    parent: ObjectiveProposalRecord,
    record: ObjectiveScopeRecord,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> int:
    title = _issue_title(parent)
    body = _issue_body(parent, record)
    observed = observe_scoped_issue(root=root, repo=repo, title=title, body=body, scope_digest=record.scope_digest, runner=runner)
    if observed is not None:
        return observed
    command = _issue_create_command(repo, title, body)
    output = _run_exact(root=root, args=command, expected=command, runner=runner, mutation=True)
    try:
        created = int(output)
    except ValueError as exc:
        raise ValueError("objective issue creation returned malformed issue number") from exc
    if created <= 0:
        raise ValueError("objective issue creation returned invalid issue number")
    observed = observe_scoped_issue(root=root, repo=repo, title=title, body=body, scope_digest=record.scope_digest, runner=runner)
    if observed != created:
        raise ValueError("objective issue post-mutation observation differs from create result")
    return created


def _child_record(parent: ObjectiveProposalRecord, record: ObjectiveScopeRecord) -> ObjectiveProposalRecord:
    if record.issue_number is None or not record.scope_digest:
        raise ValueError("scoped child requires exact issue and scope identity")
    source = ScopedObjectiveSource(
        repo=parent.source.repo,
        parent_proposal_id=parent.proposal_id,
        issue_number=record.issue_number,
        accepted_main_sha=record.accepted_main_sha,
        lane=record.lane,
        stop_boundary=record.stop_boundary,
        scope_digest=record.scope_digest,
        objective_text=parent.source.objective_text,
    )
    candidate = ObjectiveCandidateTask(
        roadmap_id="standalone-objective",
        node_id=f"objective-{parent.proposal_id[:16]}",
        lane=record.lane,
        issue_number=record.issue_number,
        objective=parent.source.objective_text,
        stop_boundary=record.stop_boundary,
    )
    compiled = ObjectiveCompileResult(
        ObjectiveCompileDisposition.CANDIDATE_TASK,
        parent.compiled.request,
        "validated standalone scoping produced a bounded candidate task; executable authority still requires promotion",
        candidate_task=candidate,
    )
    return ObjectiveProposalRecord(source=source, delivery_id=f"scoped:{parent.proposal_id}", compiled=compiled)


def _select_parent(root: Path, ledger: ObjectiveScopeLedger) -> tuple[ObjectiveProposalRecord | None, ObjectiveScopeRecord | None]:
    proposals = ObjectiveProposalStore.for_root(root).load().records
    for parent in proposals:
        if not isinstance(parent.source, DevelopmentApiObjectiveSource):
            continue
        if parent.compiled.disposition is not ObjectiveCompileDisposition.NEEDS_SCOPING:
            continue
        request = parent.compiled.request
        if request is None or request.intent not in {ObjectiveIntent.FIX, ObjectiveIntent.DEVELOP}:
            continue
        record = ledger.get(parent.proposal_id)
        if record is None or not record.terminal:
            return parent, record
    return None, None


def _blocked(store: ObjectiveScopeStore, record: ObjectiveScopeRecord, reason: str) -> ObjectiveScopingAdvanceResult:
    updated = store.put(replace(record, status=ObjectiveScopeStatus.BLOCKED, reason=reason))
    return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.BLOCKED, reason, updated, True)


def advance_objective_scoping(
    *,
    root: Path,
    repo: str,
    classifier_provider: ClassifierProvider,
    classifier_local_budget: LocalBudgetObservation,
    classifier_provider_quota: ProviderQuotaDecision | None = None,
    classifier_config: ClassifierProviderConfig | None = None,
    runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
) -> ObjectiveScopingAdvanceResult:
    root = Path(root).resolve()
    store = ObjectiveScopeStore.for_root(root)
    ledger = store.load()
    parent, record = _select_parent(root, ledger)
    if parent is None:
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.NO_WORK, "no unscoped standalone FIX/DEVELOP objective requires progression")

    control = effective_objective_progression(root, parent.proposal_id)
    if not control.allowed:
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.WAIT_CONTROL, control.reason, record, False)

    if record is None:
        head, clean = repository_identity(root)
        if not clean:
            return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.BLOCKED, "tracked checkout is dirty; standalone objective scoping cannot freeze accepted main", None, False)
        request = _scope_request(parent, head)
        created = ObjectiveScopeRecord(
            parent_proposal_id=parent.proposal_id,
            accepted_main_sha=head,
            classifier_request_id=request.request_id,
            status=ObjectiveScopeStatus.PREPARED,
            reason="standalone objective is durably prepared for bounded read-only scope classification",
        )
        store.put(created)
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.PREPARED, created.reason, created, True)

    head, clean = repository_identity(root)
    if not clean or head != record.accepted_main_sha:
        return _blocked(store, record, "repository identity moved from frozen standalone objective scope; resubmit against current accepted main")

    if record.status is ObjectiveScopeStatus.PREPARED:
        quota = classify_quota_admission(classifier_provider_quota, classifier_local_budget)
        if quota.disposition in {QuotaAdmissionDisposition.BLOCK_PROVIDER, QuotaAdmissionDisposition.BLOCK_LOCAL}:
            return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.QUOTA_WAIT, quota.reason, record, False)
        request = _scope_request(parent, record.accepted_main_sha)
        if request.request_id != record.classifier_request_id:
            return _blocked(store, record, "standalone scope classifier request identity drifted")
        result = advance_classifier(
            request=request,
            root=root,
            store=ClassifierRunStore.for_root(root),
            provider=classifier_provider,
            config=classifier_config or classifier_provider_config(),
        )
        if result.disposition is ClassifierAdvanceDisposition.RECOVERY_REQUIRED:
            updated = store.put(replace(record, status=ObjectiveScopeStatus.RECOVERY_REQUIRED, reason=result.reason, classifier_run_id=result.record.run_id))
            return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.RECOVERY_REQUIRED, result.reason, updated, True)
        if result.disposition is ClassifierAdvanceDisposition.FAILED:
            return _blocked(store, record, "standalone scope classifier failed: " + result.reason)
        decision = result.record.decision
        if decision is None:
            return _blocked(store, record, "standalone scope classifier completed without a decision")
        if decision.kind is DecisionKind.HUMAN_GATE:
            reason = decision.human_message or decision.reason or "standalone objective scope requires human judgment"
            updated = store.put(replace(record, status=ObjectiveScopeStatus.HUMAN_GATE, reason=reason, classifier_run_id=result.record.run_id))
            return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.HUMAN_GATE, reason, updated, True)
        if decision.kind is not DecisionKind.DISPATCH:
            return _blocked(store, record, f"standalone scope classifier returned unsupported decision {decision.kind.value}")
        lane = _canonical_lane(decision.lane)
        if lane is None:
            return _blocked(store, record, "standalone scope classifier selected an unknown or missing lane")
        try:
            paths, protected = _validated_paths(root, decision.allowed_paths)
        except ValueError as exc:
            return _blocked(store, record, str(exc))
        if protected:
            reason = "standalone objective scope intersects protected control-plane paths and requires explicit manual/platform work: " + ", ".join(protected)
            updated = store.put(
                replace(
                    record,
                    status=ObjectiveScopeStatus.HUMAN_GATE,
                    reason=reason,
                    classifier_run_id=result.record.run_id,
                    lane=lane,
                    stop_boundary=decision.stop_boundary or "Stop before any protected control-plane mutation.",
                    proposed_paths=paths,
                )
            )
            return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.HUMAN_GATE, reason, updated, True)
        stop = _required(decision.stop_boundary, "classifier stop_boundary")
        updated = store.put(
            replace(
                record,
                status=ObjectiveScopeStatus.SCOPED,
                reason="read-only classifier proposed one bounded lane and exact tracked-file scope",
                classifier_run_id=result.record.run_id,
                lane=lane,
                stop_boundary=stop,
                proposed_paths=paths,
            )
        )
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.CLASSIFIER_ADVANCED, updated.reason, updated, True)

    if record.status is ObjectiveScopeStatus.SCOPED:
        try:
            issue_number = ensure_scoped_issue(root=root, repo=repo, parent=parent, record=record, runner=runner)
        except (ValueError, subprocess.SubprocessError) as exc:
            return _blocked(store, record, f"standalone objective issue materialization failed closed: {exc}")
        updated = store.put(replace(record, status=ObjectiveScopeStatus.ISSUE_READY, reason="exact controller-created scope issue is present", issue_number=issue_number))
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.ISSUE_ADVANCED, updated.reason, updated, True)

    if record.status is ObjectiveScopeStatus.ISSUE_READY:
        child = _child_record(parent, record)
        captured = ObjectiveProposalStore.for_root(root).capture_record(child)
        updated = store.put(replace(record, status=ObjectiveScopeStatus.CHILD_READY, reason="bounded scoped child candidate task is durable and remains non-authoritative", child_proposal_id=captured.record.proposal_id))
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.CHILD_CREATED, updated.reason, updated, True)

    if record.status is ObjectiveScopeStatus.CHILD_READY:
        package, _created = package_proposal(root=root, proposal_id=record.child_proposal_id)
        updated = store.put(replace(record, status=ObjectiveScopeStatus.PACKAGE_READY, reason="scoped child context package is durable", package_id=package.package_id))
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.PACKAGE_CREATED, updated.reason, updated, True)

    if record.status is ObjectiveScopeStatus.PACKAGE_READY:
        retrieval, _created = retrieve_context(root=root, repo=repo, package_id=record.package_id, gh_runner=runner)
        if retrieval.disposition.value != "SCOPE_PROPOSED":
            return _blocked(store, record, "scoped child retrieval did not produce bounded exact-path scope: " + retrieval.reason)
        updated = store.put(replace(record, status=ObjectiveScopeStatus.RETRIEVAL_READY, reason="scoped child exact-path retrieval proposal is durable", retrieval_id=retrieval.retrieval_id))
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.RETRIEVAL_CREATED, updated.reason, updated, True)

    if record.status is ObjectiveScopeStatus.RETRIEVAL_READY:
        packages = ContextPackageStore.for_root(root).load()
        retrievals = ContextRetrievalStore.for_root(root).load()
        package_matches = [value for value in packages.records if value.package_id == record.package_id]
        retrieval_matches = [value for value in retrievals.records if value.retrieval_id == record.retrieval_id]
        if len(package_matches) != 1 or len(retrieval_matches) != 1:
            return _blocked(store, record, "scoped child package/retrieval identity is unavailable or ambiguous")
        promotion = validate_promotion(root=root, repo=repo, package=package_matches[0], retrieval=retrieval_matches[0], gh_runner=runner)
        if promotion.disposition is not PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST:
            transient = {
                "another protected task authority is pending",
                "an active hosted task plan already exists",
                "an active hosted admission record already exists",
                "candidate issue already has an active external ownership claim",
            }
            if promotion.blockers and set(promotion.blockers).issubset(transient):
                return ObjectiveScopingAdvanceResult(
                    ObjectiveScopingAdvanceDisposition.NO_WORK,
                    "scoped child waits for existing repository/task authority to clear: "
                    + "; ".join(promotion.blockers),
                    record,
                    False,
                )
            return _blocked(store, record, "scoped child promotion failed closed: " + ("; ".join(promotion.blockers) or promotion.reason))
        PromotionStore.for_root(root).capture(promotion)
        updated = store.put(replace(record, status=ObjectiveScopeStatus.PROMOTION_READY, reason="scoped child promotion is frozen and ready for existing task-authority post", promotion_id=promotion.digest))
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.PROMOTION_CREATED, updated.reason, updated, True)

    if record.status is ObjectiveScopeStatus.PROMOTION_READY:
        post = execute_frozen_promotion_post(root=root, repo=repo, runner=runner, promotion_id=record.promotion_id)
        if post.disposition not in {
            ObjectivePromotionPostDisposition.EXECUTED,
            ObjectivePromotionPostDisposition.RECONCILED,
            ObjectivePromotionPostDisposition.ALREADY_COMPLETE,
        }:
            return _blocked(store, record, "scoped child task-authority post failed closed: " + post.reason)
        updated = store.put(replace(record, status=ObjectiveScopeStatus.AUTHORITY_POSTED, reason="scoped child task authority was posted through the existing exactly-once boundary", remote_identity=post.remote_identity))
        return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.AUTHORITY_POSTED, updated.reason, updated, True)

    return ObjectiveScopingAdvanceResult(ObjectiveScopingAdvanceDisposition.NO_WORK, f"standalone objective scoping is terminal at {record.status.value}", record, False)
