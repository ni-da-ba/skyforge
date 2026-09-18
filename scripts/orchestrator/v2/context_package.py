from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import hashlib
import json
from pathlib import Path
import subprocess
from typing import Any, Mapping, Sequence

from .identity import canonical_digest
from .objective_ingress import ObjectiveProposalRecord, ObjectiveProposalStore
from .objective_intake import (
    ObjectiveCompileDisposition,
    load_authoritative_roadmap_state,
    load_manifest,
)
from .state_store import JsonStateStoreAdapter


CONTEXT_PACKAGES_RELATIVE_PATH = Path('.skyforge-platform-v2/context-packages.json')
CONTEXT_PACKAGES_BACKUP_RELATIVE_PATH = Path('.skyforge-platform-v2/context-packages.json.bak')

PROGRAM_CONTEXT_PATHS = (
    'docs/agent-state/CURRENT_PROJECT_STATE.md',
    'docs/agent-state/PROGRAM_CHARTER.md',
    'docs/agent-state/CROSS_LANE_CONTRACTS.md',
    'docs/agent-state/EXECUTION_BOUNDARIES.md',
    'docs/agent-state/VALIDATION_POLICY.md',
)
LANE_STATE_PATHS = {
    'Implementation': 'docs/agent-state/IMPLEMENTATION_STATE.md',
    'Authorship': 'docs/agent-state/AUTHORSHIP_STATE.md',
    'Content': 'docs/agent-state/CONTENT_STATE.md',
    'Music': 'docs/agent-state/MUSIC_STATE.md',
    'Presentation': 'docs/agent-state/PRESENTATION_STATE.md',
    'Audit': 'docs/agent-state/AUDIT_STATE.md',
}


class ContextPackageDisposition(str, Enum):
    HUMAN_GATE = 'HUMAN_GATE'
    READY_READ_ONLY = 'READY_READ_ONLY'
    SCOPE_UNRESOLVED = 'SCOPE_UNRESOLVED'
    READY_CANDIDATE_TASK = 'READY_CANDIDATE_TASK'
    BLOCKED = 'BLOCKED'


@dataclass(frozen=True)
class ContextReference:
    path: str
    sha256: str
    byte_count: int

    def as_dict(self) -> dict[str, object]:
        return {'path': self.path, 'sha256': self.sha256, 'byte_count': self.byte_count}

    @classmethod
    def from_mapping(cls, raw: Any) -> 'ContextReference':
        if not isinstance(raw, Mapping):
            raise ValueError('context reference must be an object')
        path = str(raw.get('path') or '').strip()
        sha = str(raw.get('sha256') or '').strip()
        count = raw.get('byte_count')
        if not path or len(sha) != 64 or isinstance(count, bool) or not isinstance(count, int) or count < 0:
            raise ValueError('invalid context reference')
        return cls(path, sha, count)


@dataclass(frozen=True)
class PathScopeProposal:
    disposition: str
    allowed_paths: tuple[str, ...] = ()
    protected_paths: tuple[str, ...] = ()
    reason: str = ''

    def __post_init__(self) -> None:
        if self.disposition not in {'NOT_REQUIRED', 'RESOLVED', 'UNRESOLVED'}:
            raise ValueError('invalid path-scope disposition')
        if self.disposition == 'RESOLVED' and not self.allowed_paths:
            raise ValueError('resolved path scope requires allowed_paths')
        if self.disposition != 'RESOLVED' and (self.allowed_paths or self.protected_paths):
            raise ValueError('non-resolved path scope may not carry paths')
        if not str(self.reason or '').strip():
            raise ValueError('path-scope reason is required')

    def as_dict(self) -> dict[str, object]:
        return {
            'disposition': self.disposition,
            'allowed_paths': list(self.allowed_paths),
            'protected_paths': list(self.protected_paths),
            'reason': self.reason,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> 'PathScopeProposal':
        if not isinstance(raw, Mapping):
            raise ValueError('path scope must be an object')
        allowed = raw.get('allowed_paths') or []
        protected = raw.get('protected_paths') or []
        if not isinstance(allowed, list) or not isinstance(protected, list):
            raise ValueError('path scope paths must be lists')
        return cls(
            disposition=str(raw.get('disposition') or ''),
            allowed_paths=tuple(str(v) for v in allowed),
            protected_paths=tuple(str(v) for v in protected),
            reason=str(raw.get('reason') or ''),
        )


@dataclass(frozen=True)
class ContextPackage:
    proposal_id: str
    accepted_main_sha: str
    tracked_clean: bool
    compiled_disposition: str
    roadmap_id: str
    roadmap_state_digest: str
    manifest_fingerprint: str
    lane: str | None
    issue_number: int | None
    node_id: str | None
    objective: str | None
    stop_boundary: str | None
    human_gate_message: str | None
    context_references: tuple[ContextReference, ...]
    path_scope: PathScopeProposal
    disposition: ContextPackageDisposition
    reason: str

    @property
    def package_id(self) -> str:
        return canonical_digest(self.as_dict(include_id=False))

    def as_dict(self, *, include_id: bool = True) -> dict[str, object]:
        value = {
            'schema_version': 1,
            'proposal_id': self.proposal_id,
            'accepted_main_sha': self.accepted_main_sha,
            'tracked_clean': self.tracked_clean,
            'compiled_disposition': self.compiled_disposition,
            'roadmap_id': self.roadmap_id,
            'roadmap_state_digest': self.roadmap_state_digest,
            'manifest_fingerprint': self.manifest_fingerprint,
            'lane': self.lane,
            'issue_number': self.issue_number,
            'node_id': self.node_id,
            'objective': self.objective,
            'stop_boundary': self.stop_boundary,
            'human_gate_message': self.human_gate_message,
            'context_references': [ref.as_dict() for ref in self.context_references],
            'path_scope': self.path_scope.as_dict(),
            'disposition': self.disposition.value,
            'reason': self.reason,
            'executable_task_authority': False,
        }
        if include_id:
            value['package_id'] = self.package_id
        return value

    @classmethod
    def from_mapping(cls, raw: Any) -> 'ContextPackage':
        if not isinstance(raw, Mapping) or raw.get('schema_version') != 1:
            raise ValueError('invalid context package')
        if raw.get('executable_task_authority') is not False:
            raise ValueError('context package must remain non-authoritative')
        refs = raw.get('context_references')
        if not isinstance(refs, list):
            raise ValueError('context references must be a list')
        issue = raw.get('issue_number')
        if issue is not None and (isinstance(issue, bool) or not isinstance(issue, int) or issue <= 0):
            raise ValueError('context package issue_number must be positive integer or null')
        package = cls(
            proposal_id=str(raw.get('proposal_id') or ''),
            accepted_main_sha=str(raw.get('accepted_main_sha') or ''),
            tracked_clean=bool(raw.get('tracked_clean')),
            compiled_disposition=str(raw.get('compiled_disposition') or ''),
            roadmap_id=str(raw.get('roadmap_id') or ''),
            roadmap_state_digest=str(raw.get('roadmap_state_digest') or ''),
            manifest_fingerprint=str(raw.get('manifest_fingerprint') or ''),
            lane=None if raw.get('lane') is None else str(raw.get('lane')),
            issue_number=issue,
            node_id=None if raw.get('node_id') is None else str(raw.get('node_id')),
            objective=None if raw.get('objective') is None else str(raw.get('objective')),
            stop_boundary=None if raw.get('stop_boundary') is None else str(raw.get('stop_boundary')),
            human_gate_message=None if raw.get('human_gate_message') is None else str(raw.get('human_gate_message')),
            context_references=tuple(ContextReference.from_mapping(v) for v in refs),
            path_scope=PathScopeProposal.from_mapping(raw.get('path_scope')),
            disposition=ContextPackageDisposition(str(raw.get('disposition') or '')),
            reason=str(raw.get('reason') or ''),
        )
        if str(raw.get('package_id') or '') != package.package_id:
            raise ValueError('context package id mismatch')
        return package


@dataclass(frozen=True)
class ContextPackageLedger:
    records: tuple[ContextPackage, ...] = ()

    def get(self, proposal_id: str) -> ContextPackage | None:
        found = [record for record in self.records if record.proposal_id == proposal_id]
        if len(found) > 1:
            raise ValueError('duplicate context package proposal identity')
        return found[0] if found else None

    def put(self, package: ContextPackage) -> 'ContextPackageLedger':
        existing = self.get(package.proposal_id)
        if existing is not None:
            if existing != package:
                raise ValueError('context package changed for already-frozen proposal')
            return self
        return ContextPackageLedger(self.records + (package,))

    def as_dict(self) -> dict[str, object]:
        return {'schema_version': 1, 'records': [record.as_dict() for record in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> 'ContextPackageLedger':
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get('schema_version') != 1:
            raise ValueError('invalid context package ledger')
        values = raw.get('records')
        if not isinstance(values, list):
            raise ValueError('context package records must be a list')
        ledger = cls()
        for value in values:
            ledger = ledger.put(ContextPackage.from_mapping(value))
        return ledger


class ContextPackageStore:
    def __init__(self, adapter: JsonStateStoreAdapter) -> None:
        self.adapter = adapter

    @classmethod
    def for_root(cls, root: Path) -> 'ContextPackageStore':
        root = Path(root)
        return cls(JsonStateStoreAdapter(
            path=root / CONTEXT_PACKAGES_RELATIVE_PATH,
            backup_path=root / CONTEXT_PACKAGES_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> ContextPackageLedger:
        return ContextPackageLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ContextPackageLedger) -> None:
        self.adapter.save(ledger.as_dict())

    def capture(self, package: ContextPackage) -> bool:
        ledger = self.load()
        existing = ledger.get(package.proposal_id)
        if existing is not None:
            if existing != package:
                raise ValueError('context package conflict for proposal')
            return False
        self.save(ledger.put(package))
        return True


def _git(root: Path, *args: str) -> str:
    completed = subprocess.run(
        ['git', '-C', str(root), '-c', f'safe.directory={root}', *args],
        check=True,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    return completed.stdout.strip()


def repository_identity(root: Path) -> tuple[str, bool]:
    root = Path(root).resolve()
    sha = _git(root, 'rev-parse', 'HEAD')
    if len(sha) != 40:
        raise ValueError('repository HEAD is not a full SHA')
    status = _git(root, 'status', '--porcelain', '--untracked-files=no')
    return sha, not bool(status)


def _reference(root: Path, relative: str) -> ContextReference:
    path = root / relative
    data = path.read_bytes()
    return ContextReference(relative, hashlib.sha256(data).hexdigest(), len(data))


def _context_paths(lane: str | None) -> tuple[str, ...]:
    paths = list(PROGRAM_CONTEXT_PATHS)
    if lane:
        lane_path = LANE_STATE_PATHS.get(lane)
        if lane_path:
            paths.append(lane_path)
    return tuple(paths)


def build_context_package(*, root: Path, proposal: ObjectiveProposalRecord) -> ContextPackage:
    root = Path(root).resolve()
    accepted_main_sha, tracked_clean = repository_identity(root)
    manifest = load_manifest(root)
    roadmap_state = load_authoritative_roadmap_state(root, manifest)
    compiled = proposal.compiled
    candidate = compiled.candidate_task
    gate = compiled.human_gate
    lane = candidate.lane if candidate else (gate.lane if gate else None)
    refs = tuple(_reference(root, path) for path in _context_paths(lane))

    common = dict(
        proposal_id=proposal.proposal_id,
        accepted_main_sha=accepted_main_sha,
        tracked_clean=tracked_clean,
        compiled_disposition=compiled.disposition.value,
        roadmap_id=manifest.roadmap_id,
        roadmap_state_digest=roadmap_state.digest,
        manifest_fingerprint=manifest.fingerprint,
        lane=lane,
        issue_number=candidate.issue_number if candidate else proposal.source.issue_number,
        node_id=candidate.node_id if candidate else (gate.node_id if gate else None),
        objective=candidate.objective if candidate else None,
        stop_boundary=candidate.stop_boundary if candidate else None,
        human_gate_message=gate.message if gate else None,
        context_references=refs,
    )

    if not tracked_clean:
        return ContextPackage(
            **common,
            path_scope=PathScopeProposal('UNRESOLVED', reason='tracked checkout is dirty; package cannot justify mutation scope'),
            disposition=ContextPackageDisposition.BLOCKED,
            reason='tracked checkout must be clean before packaging authoritative work',
        )

    if compiled.disposition is ObjectiveCompileDisposition.HUMAN_GATE:
        return ContextPackage(
            **common,
            path_scope=PathScopeProposal('NOT_REQUIRED', reason='human-gate objective does not authorize repository mutation'),
            disposition=ContextPackageDisposition.HUMAN_GATE,
            reason='objective is already at an explicit human gate; package is inspection-only',
        )

    if compiled.disposition is ObjectiveCompileDisposition.READ_ONLY:
        return ContextPackage(
            **common,
            path_scope=PathScopeProposal('NOT_REQUIRED', reason='read-only objective does not require mutation scope'),
            disposition=ContextPackageDisposition.READY_READ_ONLY,
            reason='read-only objective has a reproducible compact context package',
        )

    if compiled.disposition is ObjectiveCompileDisposition.CANDIDATE_TASK:
        # The current roadmap manifest defines semantic/lane authority but not exact
        # file ownership. Whole-repository or lane-wide wildcard inference would turn
        # context retrieval into mutation authority, so fail closed until a narrower
        # repository-owned scope source exists.
        return ContextPackage(
            **common,
            path_scope=PathScopeProposal(
                'UNRESOLVED',
                reason='accepted roadmap task lacks repository-owned exact allowed/protected path scope; broad lane/whole-repo inference is forbidden',
            ),
            disposition=ContextPackageDisposition.SCOPE_UNRESOLVED,
            reason='candidate task is semantically bounded but file mutation scope is not yet repository-authorized',
        )

    return ContextPackage(
        **common,
        path_scope=PathScopeProposal('UNRESOLVED', reason='objective has not produced a repository-bounded task candidate'),
        disposition=ContextPackageDisposition.SCOPE_UNRESOLVED,
        reason='objective requires additional context/scoping before task-authority promotion',
    )


def package_proposal(*, root: Path, proposal_id: str | None = None) -> tuple[ContextPackage, bool]:
    proposals = ObjectiveProposalStore.for_root(root).load()
    if not proposals.records:
        raise ValueError('no durable objective proposals exist')
    if proposal_id:
        matches = [record for record in proposals.records if record.proposal_id == proposal_id]
        if len(matches) != 1:
            raise ValueError('objective proposal id was not found exactly once')
        proposal = matches[0]
    else:
        proposal = proposals.records[-1]
    package = build_context_package(root=root, proposal=proposal)
    created = ContextPackageStore.for_root(root).capture(package)
    return package, created
