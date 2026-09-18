from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import json
from pathlib import Path
import re
import subprocess
from typing import Any, Callable, Mapping

from .context_package import ContextPackage, ContextPackageDisposition, ContextPackageStore, repository_identity
from .context_retrieval import ContextRetrievalDisposition, ContextRetrievalRecord, ContextRetrievalStore, observe_issue
from .hosted_admission import HostedAdmissionStore
from .hosted_state import HostedStateStore
from .hosted_task_plan import HostedTaskPlanStore
from .identity import canonical_digest
from .objective_ingress import ObjectiveProposalRecord, ObjectiveProposalStore
from .objective_intake import ObjectiveCompileDisposition, compile_objective
from .state_store import JsonStateStoreAdapter
from .task_authority import TASK_AUTHORITY_MARKER, TaskAuthorityDisposition, TypedTaskDirective, parse_typed_task_directive

PROMOTIONS_RELATIVE_PATH = Path('.skyforge-platform-v2/objective-promotions.json')
PROMOTIONS_BACKUP_RELATIVE_PATH = Path('.skyforge-platform-v2/objective-promotions.json.bak')
PROTECTED_PATHS = (
    '.github/**','deploy/orchestrator/**','scripts/orchestrator/**',
    'docs/agent-state/ORCHESTRATOR_ROADMAP.json',
    'docs/architecture/SKYFORGE_DEVELOPMENT_PLATFORM_OPTIMIZATION_ROADMAP.md',
)
_PROTECTED_PREFIXES = ('.github/','deploy/orchestrator/','scripts/orchestrator/')
_PROTECTED_EXACT = {
    'docs/agent-state/ORCHESTRATOR_ROADMAP.json',
    'docs/architecture/SKYFORGE_DEVELOPMENT_PLATFORM_OPTIMIZATION_ROADMAP.md',
}


def _required(value: Any, label: str) -> str:
    text = str(value or '').strip()
    if not text:
        raise ValueError(f'{label} is required')
    return text


def _exact_path(path: str) -> str:
    text = _required(path, 'allowed path').replace('\\', '/')
    if text.startswith('/') or text.startswith('./') or re.match(r'^[A-Za-z]:/', text):
        raise ValueError('allowed path must be repository-relative')
    if any(part in {'', '.', '..'} for part in text.split('/')):
        raise ValueError('allowed path must be normalized')
    if any(token in text for token in ('*', '?', '[', ']', '{', '}')):
        raise ValueError('objective promotion accepts exact paths only')
    return text


def _protected(path: str) -> bool:
    return path in _PROTECTED_EXACT or any(path.startswith(prefix) for prefix in _PROTECTED_PREFIXES)


@dataclass(frozen=True)
class TaskAuthorityDraft:
    issue_number: int
    package_id: str
    retrieval_id: str
    lane: str
    objective: str
    stop_boundary: str
    allowed_paths: tuple[str, ...]
    protected_paths: tuple[str, ...] = PROTECTED_PATHS
    auto_merge_eligible: bool = False

    def __post_init__(self) -> None:
        if isinstance(self.issue_number, bool) or not isinstance(self.issue_number, int) or self.issue_number <= 0:
            raise ValueError('draft issue_number must be positive')
        object.__setattr__(self, 'package_id', _required(self.package_id, 'package_id'))
        object.__setattr__(self, 'retrieval_id', _required(self.retrieval_id, 'retrieval_id'))
        object.__setattr__(self, 'lane', _required(self.lane, 'lane'))
        object.__setattr__(self, 'objective', _required(self.objective, 'objective'))
        object.__setattr__(self, 'stop_boundary', _required(self.stop_boundary, 'stop_boundary'))
        allowed = tuple(sorted({_exact_path(path) for path in self.allowed_paths}))
        if not allowed:
            raise ValueError('draft allowed_paths must be non-empty')
        object.__setattr__(self, 'allowed_paths', allowed)
        if self.auto_merge_eligible:
            raise ValueError('objective-promoted authority is not auto-merge eligible by default')

    def directive(self) -> TypedTaskDirective:
        return TypedTaskDirective(
            lane=self.lane,
            objective=self.objective,
            stop_boundary=self.stop_boundary,
            allowed_paths=self.allowed_paths,
            protected_paths=self.protected_paths,
            auto_merge_eligible=False,
        )

    @property
    def body(self) -> str:
        payload = json.dumps(self.directive().as_dict(), sort_keys=True, separators=(',', ':'))
        return (
            'AUDIT NEW TASK — OBJECTIVE PROMOTION\n'
            f'Objective package: {self.package_id}\n'
            f'Context retrieval: {self.retrieval_id}\n'
            f'{TASK_AUTHORITY_MARKER}\n{payload}'
        )

    @property
    def digest(self) -> str:
        return canonical_digest({
            'issue_number': self.issue_number,
            'package_id': self.package_id,
            'retrieval_id': self.retrieval_id,
            'body': self.body,
        })

    def as_dict(self) -> dict[str, object]:
        return {
            'issue_number': self.issue_number,
            'package_id': self.package_id,
            'retrieval_id': self.retrieval_id,
            'lane': self.lane,
            'objective': self.objective,
            'stop_boundary': self.stop_boundary,
            'allowed_paths': list(self.allowed_paths),
            'protected_paths': list(self.protected_paths),
            'auto_merge_eligible': False,
            'body': self.body,
            'digest': self.digest,
            'authoritative': False,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> 'TaskAuthorityDraft':
        if not isinstance(raw, Mapping) or raw.get('authoritative') is not False:
            raise ValueError('invalid non-authoritative task authority draft')
        draft = cls(
            issue_number=raw.get('issue_number'), package_id=raw.get('package_id'), retrieval_id=raw.get('retrieval_id'),
            lane=raw.get('lane'), objective=raw.get('objective'), stop_boundary=raw.get('stop_boundary'),
            allowed_paths=tuple(raw.get('allowed_paths') or ()), protected_paths=tuple(raw.get('protected_paths') or ()),
            auto_merge_eligible=bool(raw.get('auto_merge_eligible', False)),
        )
        if str(raw.get('body') or '') != draft.body or str(raw.get('digest') or '') != draft.digest:
            raise ValueError('task authority draft body/digest mismatch')
        return draft


class PromotionDisposition(str, Enum):
    READY_FOR_TASK_AUTHORITY_POST = 'READY_FOR_TASK_AUTHORITY_POST'
    BLOCKED = 'BLOCKED'


@dataclass(frozen=True)
class PromotionResult:
    disposition: PromotionDisposition
    reason: str
    package_id: str
    retrieval_id: str
    live_issue_digest: str = ''
    draft: TaskAuthorityDraft | None = None
    blockers: tuple[str, ...] = ()

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def as_dict(self) -> dict[str, object]:
        return {
            'schema_version': 1,
            'disposition': self.disposition.value,
            'reason': self.reason,
            'package_id': self.package_id,
            'retrieval_id': self.retrieval_id,
            'live_issue_digest': self.live_issue_digest,
            'draft': self.draft.as_dict() if self.draft else None,
            'blockers': list(self.blockers),
            'executable_task_authority': False,
        }


@dataclass(frozen=True)
class PromotionRecord:
    result: PromotionResult

    @property
    def promotion_id(self) -> str:
        return self.result.digest

    def as_dict(self) -> dict[str, object]:
        return {'schema_version': 1, 'promotion_id': self.promotion_id, 'result': self.result.as_dict()}


@dataclass(frozen=True)
class PromotionLedger:
    records: tuple[PromotionRecord, ...] = ()

    def get(self, package_id: str) -> PromotionRecord | None:
        found = [r for r in self.records if r.result.package_id == package_id]
        if len(found) > 1:
            raise ValueError('duplicate objective promotion package identity')
        return found[0] if found else None

    def put(self, record: PromotionRecord) -> 'PromotionLedger':
        existing = self.get(record.result.package_id)
        if existing is not None:
            if existing != record:
                raise ValueError('promotion changed for frozen package')
            return self
        return PromotionLedger(self.records + (record,))

    def as_dict(self) -> dict[str, object]:
        return {'schema_version': 1, 'records': [r.as_dict() for r in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> 'PromotionLedger':
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get('schema_version') != 1:
            raise ValueError('invalid promotion ledger')
        values = raw.get('records')
        if not isinstance(values, list):
            raise ValueError('promotion records must be list')
        records: list[PromotionRecord] = []
        for value in values:
            rr = value.get('result') if isinstance(value, Mapping) else None
            if not isinstance(rr, Mapping) or rr.get('schema_version') != 1:
                raise ValueError('invalid promotion result')
            draft_raw = rr.get('draft')
            draft = None if draft_raw is None else TaskAuthorityDraft.from_mapping(draft_raw)
            result = PromotionResult(
                disposition=PromotionDisposition(str(rr.get('disposition') or '')),
                reason=_required(rr.get('reason'), 'promotion reason'),
                package_id=_required(rr.get('package_id'), 'package_id'),
                retrieval_id=_required(rr.get('retrieval_id'), 'retrieval_id'),
                live_issue_digest=str(rr.get('live_issue_digest') or ''),
                draft=draft,
                blockers=tuple(str(v) for v in (rr.get('blockers') or [])),
            )
            record = PromotionRecord(result)
            if str(value.get('promotion_id') or '') != record.promotion_id:
                raise ValueError('promotion id mismatch')
            records.append(record)
        return cls(tuple(records))


class PromotionStore:
    def __init__(self, adapter: JsonStateStoreAdapter) -> None:
        self.adapter = adapter

    @classmethod
    def for_root(cls, root: Path) -> 'PromotionStore':
        root = Path(root)
        return cls(JsonStateStoreAdapter(path=root / PROMOTIONS_RELATIVE_PATH, backup_path=root / PROMOTIONS_BACKUP_RELATIVE_PATH))

    def load(self) -> PromotionLedger:
        return PromotionLedger.from_mapping(self.adapter.load().as_dict())

    def capture(self, result: PromotionResult) -> bool:
        if result.disposition is not PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST:
            raise ValueError('only ready promotions may be frozen')
        ledger = self.load()
        record = PromotionRecord(result)
        existing = ledger.get(result.package_id)
        if existing is not None:
            if existing != record:
                raise ValueError('promotion conflict for frozen package')
            return False
        self.adapter.save(ledger.put(record).as_dict())
        return True


def _find_proposal(root: Path, proposal_id: str) -> ObjectiveProposalRecord:
    matches = [r for r in ObjectiveProposalStore.for_root(root).load().records if r.proposal_id == proposal_id]
    if len(matches) != 1:
        raise ValueError('objective proposal id not found exactly once')
    return matches[0]


def _current_file_digest(root: Path, path: str) -> str:
    return hashlib.sha256((root / path).read_bytes()).hexdigest()


def _active_external_conflict(root: Path, issue_number: int) -> bool:
    projection = HostedStateStore.for_root(root).load().legacy_projection or {}
    claims = projection.get('external_claims') or []
    if not isinstance(claims, list):
        return True
    for claim in claims:
        if not isinstance(claim, Mapping):
            return True
        if str(claim.get('state') or '').lower() == 'active' and claim.get('issue_number') == issue_number:
            return True
    return False


def _control_blockers(root: Path) -> tuple[str, ...]:
    blockers: list[str] = []
    state = HostedStateStore.for_root(root).load()
    if any(event.protected_authority for event in state.inbox.pending_events):
        blockers.append('another protected task authority is pending')
    if HostedTaskPlanStore.for_root(root).load().active is not None:
        blockers.append('an active hosted task plan already exists')
    if HostedAdmissionStore.for_root(root).load().record is not None:
        blockers.append('an active hosted admission record already exists')
    return tuple(blockers)


def validate_promotion(*, root: Path, repo: str, package: ContextPackage, retrieval: ContextRetrievalRecord,
                       gh_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run) -> PromotionResult:
    root = Path(root).resolve()
    blockers: list[str] = []
    if package.package_id != retrieval.package_id:
        blockers.append('retrieval does not belong to context package')
    if package.proposal_id != retrieval.proposal_id:
        blockers.append('retrieval proposal identity differs from package')
    if package.disposition is not ContextPackageDisposition.SCOPE_UNRESOLVED:
        blockers.append('context package is not a promotable candidate task')
    if retrieval.disposition is not ContextRetrievalDisposition.SCOPE_PROPOSED:
        blockers.append('context retrieval has no exact-path scope proposal')

    if blockers:
        return PromotionResult(
            PromotionDisposition.BLOCKED,
            'objective candidate promotion failed closed before remote validation',
            package.package_id,
            retrieval.retrieval_id,
            '',
            None,
            tuple(sorted(set(blockers))),
        )

    head, clean = repository_identity(root)
    if not clean:
        blockers.append('tracked checkout is dirty')
    if head != package.accepted_main_sha or head != retrieval.accepted_main_sha:
        blockers.append('repository HEAD moved from frozen context package')

    proposal = None
    try:
        proposal = _find_proposal(root, package.proposal_id)
    except ValueError as exc:
        blockers.append(str(exc))
    if proposal is not None:
        current = compile_objective(proposal.source.objective_text, root=root)
        if current.disposition is not ObjectiveCompileDisposition.CANDIDATE_TASK:
            blockers.append('current objective compilation no longer selects a candidate task')
        elif proposal.compiled.candidate_task is None or current.candidate_task is None:
            blockers.append('candidate task metadata is missing')
        elif current.candidate_task.as_dict() != proposal.compiled.candidate_task.as_dict():
            blockers.append('current roadmap candidate differs from frozen objective proposal')

    issue_number = package.issue_number
    live_issue = None
    if issue_number is None:
        blockers.append('candidate package lacks issue number')
    else:
        try:
            live_issue = observe_issue(root=root, repo=repo, issue_number=issue_number, runner=gh_runner)
        except Exception as exc:
            blockers.append(f'candidate issue truth unavailable: {exc}')
        if live_issue is not None:
            if live_issue.state != 'OPEN':
                blockers.append('candidate issue is not open')
            frozen = retrieval.issue_observation
            if frozen is None or live_issue.digest != frozen.digest:
                blockers.append('candidate issue revision changed since retrieval')
        if _active_external_conflict(root, issue_number):
            blockers.append('candidate issue already has an active external ownership claim')

    blockers.extend(_control_blockers(root))

    slice_by_path = {item.path: item for item in retrieval.slices}
    allowed: list[str] = []
    for raw in retrieval.scope_proposal.paths:
        try:
            path = _exact_path(raw)
        except ValueError as exc:
            blockers.append(str(exc))
            continue
        if _protected(path):
            blockers.append(f'proposed path is protected control-plane state: {path}')
            continue
        evidence = slice_by_path.get(path)
        if evidence is None:
            blockers.append(f'proposed path lacks retrieval-slice evidence: {path}')
            continue
        full = root / path
        if not full.is_file():
            blockers.append(f'proposed path is not a current tracked file: {path}')
            continue
        if _current_file_digest(root, path) != evidence.sha256:
            blockers.append(f'proposed path digest changed since retrieval: {path}')
            continue
        allowed.append(path)
    if not allowed:
        blockers.append('validated allowed path set is empty')

    live_digest = live_issue.digest if live_issue is not None else ''
    if blockers:
        return PromotionResult(
            PromotionDisposition.BLOCKED,
            'objective candidate promotion failed closed',
            package.package_id,
            retrieval.retrieval_id,
            live_digest,
            None,
            tuple(sorted(set(blockers))),
        )

    assert proposal is not None and proposal.compiled.candidate_task is not None and issue_number is not None
    candidate = proposal.compiled.candidate_task
    draft = TaskAuthorityDraft(
        issue_number=issue_number,
        package_id=package.package_id,
        retrieval_id=retrieval.retrieval_id,
        lane=candidate.lane,
        objective=candidate.objective,
        stop_boundary=candidate.stop_boundary,
        allowed_paths=tuple(allowed),
    )
    parsed = parse_typed_task_directive(draft.body)
    if parsed.disposition is not TaskAuthorityDisposition.EXECUTABLE_V2 or parsed.directive != draft.directive():
        return PromotionResult(
            PromotionDisposition.BLOCKED,
            'frozen task-authority draft failed existing parser round-trip',
            package.package_id,
            retrieval.retrieval_id,
            live_digest,
            None,
            ('typed task authority parser rejected frozen draft',),
        )
    return PromotionResult(
        PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST,
        'current repository, roadmap, issue revision, ownership, and exact path evidence validate promotion',
        package.package_id,
        retrieval.retrieval_id,
        live_digest,
        draft,
        (),
    )


def validate_latest_promotion(*, root: Path, repo: str,
                              gh_runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run) -> tuple[PromotionResult, bool]:
    packages = ContextPackageStore.for_root(root).load()
    retrievals = ContextRetrievalStore.for_root(root).load()
    if not packages.records or not retrievals.records:
        raise ValueError('context package and retrieval records are required')
    retrieval = retrievals.records[-1]
    matches = [p for p in packages.records if p.package_id == retrieval.package_id]
    if len(matches) != 1:
        raise ValueError('retrieval package not found exactly once')
    result = validate_promotion(root=root, repo=repo, package=matches[0], retrieval=retrieval, gh_runner=gh_runner)
    created = False
    if result.disposition is PromotionDisposition.READY_FOR_TASK_AUTHORITY_POST:
        created = PromotionStore.for_root(root).capture(result)
    return result, created
