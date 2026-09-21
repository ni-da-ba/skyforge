from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re
from typing import Any, Mapping

from .identity import canonical_digest
from .objective_intake import ObjectiveCompileResult, compile_objective
from .state_store import JsonStateStoreAdapter


OBJECTIVE_MARKER = "SKYFORGE OBJECTIVE"
OBJECTIVE_PROPOSALS_RELATIVE_PATH = Path(".skyforge-platform-v2/objective-proposals.json")
OBJECTIVE_PROPOSALS_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2/objective-proposals.json.bak")
_API_REQUEST_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$")
_CLIENT_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{1,63}$")


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _positive(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


@dataclass(frozen=True)
class ObjectiveSourceReference:
    repo: str
    issue_number: int
    comment_id: int
    actor: str
    created_at: str
    updated_at: str
    objective_text: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "repo", _required(self.repo, "repo"))
        object.__setattr__(self, "issue_number", _positive(self.issue_number, "issue_number"))
        object.__setattr__(self, "comment_id", _positive(self.comment_id, "comment_id"))
        object.__setattr__(self, "actor", _required(self.actor, "actor").lower())
        object.__setattr__(self, "created_at", _required(self.created_at, "created_at"))
        object.__setattr__(self, "updated_at", _required(self.updated_at, "updated_at"))
        object.__setattr__(self, "objective_text", _required(self.objective_text, "objective_text"))

    def as_dict(self) -> dict[str, object]:
        return {
            "repo": self.repo,
            "issue_number": self.issue_number,
            "comment_id": self.comment_id,
            "actor": self.actor,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
            "objective_text": self.objective_text,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class DevelopmentApiObjectiveSource:
    repo: str
    request_id: str
    actor: str
    client: str
    submitted_at: str
    objective_text: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "repo", _required(self.repo, "repo"))
        request_id = _required(self.request_id, "objective request_id")
        if not _API_REQUEST_RE.fullmatch(request_id):
            raise ValueError("objective request_id has invalid characters or length")
        object.__setattr__(self, "request_id", request_id)
        object.__setattr__(self, "actor", _required(self.actor, "actor").lower())
        client = _required(self.client, "objective client").lower()
        if not _CLIENT_RE.fullmatch(client):
            raise ValueError("objective client has invalid characters or length")
        object.__setattr__(self, "client", client)
        object.__setattr__(self, "submitted_at", _required(self.submitted_at, "submitted_at"))
        object.__setattr__(
            self,
            "objective_text",
            _required(self.objective_text, "objective_text"),
        )

    def as_dict(self) -> dict[str, object]:
        return {
            "kind": "DEVELOPMENT_API",
            "repo": self.repo,
            "request_id": self.request_id,
            "actor": self.actor,
            "client": self.client,
            "submitted_at": self.submitted_at,
            "objective_text": self.objective_text,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ProgramObjectiveSource:
    repo: str
    parent_proposal_id: str
    program_id: str
    node_id: str
    projection_digest: str
    objective_text: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "repo", _required(self.repo, "repo"))
        for name in ("parent_proposal_id", "projection_digest"):
            value = _required(getattr(self, name), name).lower()
            if len(value) != 64 or any(ch not in "0123456789abcdef" for ch in value):
                raise ValueError(f"{name} must be lowercase SHA-256 hex")
            object.__setattr__(self, name, value)
        object.__setattr__(self, "program_id", _required(self.program_id, "program_id"))
        object.__setattr__(self, "node_id", _required(self.node_id, "node_id"))
        object.__setattr__(
            self,
            "objective_text",
            _required(self.objective_text, "objective_text"),
        )

    def as_dict(self) -> dict[str, object]:
        return {
            "kind": "PROGRAM",
            "repo": self.repo,
            "parent_proposal_id": self.parent_proposal_id,
            "program_id": self.program_id,
            "node_id": self.node_id,
            "projection_digest": self.projection_digest,
            "objective_text": self.objective_text,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ScopedObjectiveSource:
    repo: str
    parent_proposal_id: str
    issue_number: int
    accepted_main_sha: str
    lane: str
    stop_boundary: str
    scope_digest: str
    objective_text: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "repo", _required(self.repo, "repo"))
        parent = _required(self.parent_proposal_id, "parent_proposal_id").lower()
        if len(parent) != 64 or any(ch not in "0123456789abcdef" for ch in parent):
            raise ValueError("parent_proposal_id must be lowercase SHA-256 hex")
        object.__setattr__(self, "parent_proposal_id", parent)
        object.__setattr__(self, "issue_number", _positive(self.issue_number, "issue_number"))
        sha = _required(self.accepted_main_sha, "accepted_main_sha").lower()
        if len(sha) != 40 or any(ch not in "0123456789abcdef" for ch in sha):
            raise ValueError("accepted_main_sha must be lowercase 40-character Git SHA")
        object.__setattr__(self, "accepted_main_sha", sha)
        object.__setattr__(self, "lane", _required(self.lane, "lane"))
        object.__setattr__(self, "stop_boundary", _required(self.stop_boundary, "stop_boundary"))
        scope = _required(self.scope_digest, "scope_digest").lower()
        if len(scope) != 64 or any(ch not in "0123456789abcdef" for ch in scope):
            raise ValueError("scope_digest must be lowercase SHA-256 hex")
        object.__setattr__(self, "scope_digest", scope)
        object.__setattr__(self, "objective_text", _required(self.objective_text, "objective_text"))

    def as_dict(self) -> dict[str, object]:
        return {
            "kind": "SCOPED_OBJECTIVE",
            "repo": self.repo,
            "parent_proposal_id": self.parent_proposal_id,
            "issue_number": self.issue_number,
            "accepted_main_sha": self.accepted_main_sha,
            "lane": self.lane,
            "stop_boundary": self.stop_boundary,
            "scope_digest": self.scope_digest,
            "objective_text": self.objective_text,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ObjectiveProposalRecord:
    source: ObjectiveSourceReference | DevelopmentApiObjectiveSource | ProgramObjectiveSource | ScopedObjectiveSource
    delivery_id: str
    compiled: ObjectiveCompileResult

    @property
    def proposal_id(self) -> str:
        return canonical_digest({
            "source_digest": self.source.digest,
            "compiled_digest": self.compiled.digest,
        })

    def as_dict(self) -> dict[str, object]:
        return {
            "proposal_id": self.proposal_id,
            "source": self.source.as_dict(),
            "source_digest": self.source.digest,
            "delivery_id": self.delivery_id,
            "compiled": self.compiled.as_dict(),
            "compiled_digest": self.compiled.digest,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveProposalRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("objective proposal record must be an object")
        src = raw.get("source")
        comp = raw.get("compiled")
        if not isinstance(src, Mapping) or not isinstance(comp, Mapping):
            raise ValueError("objective proposal record is malformed")
        if src.get("kind") == "SCOPED_OBJECTIVE":
            source = ScopedObjectiveSource(
                repo=src.get("repo"),
                parent_proposal_id=src.get("parent_proposal_id"),
                issue_number=src.get("issue_number"),
                accepted_main_sha=src.get("accepted_main_sha"),
                lane=src.get("lane"),
                stop_boundary=src.get("stop_boundary"),
                scope_digest=src.get("scope_digest"),
                objective_text=src.get("objective_text"),
            )
        elif src.get("kind") == "DEVELOPMENT_API":
            source = DevelopmentApiObjectiveSource(
                repo=src.get("repo"),
                request_id=src.get("request_id"),
                actor=src.get("actor"),
                client=src.get("client"),
                submitted_at=src.get("submitted_at"),
                objective_text=src.get("objective_text"),
            )
        elif src.get("kind") == "PROGRAM":
            source = ProgramObjectiveSource(
                repo=src.get("repo"),
                parent_proposal_id=src.get("parent_proposal_id"),
                program_id=src.get("program_id"),
                node_id=src.get("node_id"),
                projection_digest=src.get("projection_digest"),
                objective_text=src.get("objective_text"),
            )
        else:
            # Preserve the historical GitHub source serialization exactly. Existing
            # source digests/proposal ids depend on this field set.
            source = ObjectiveSourceReference(
                repo=src.get("repo"),
                issue_number=src.get("issue_number"),
                comment_id=src.get("comment_id"),
                actor=src.get("actor"),
                created_at=src.get("created_at"),
                updated_at=src.get("updated_at"),
                objective_text=src.get("objective_text"),
            )
        compiled = ObjectiveCompileResult.from_mapping(comp)
        record = cls(source=source, delivery_id=str(raw.get("delivery_id") or ""), compiled=compiled)
        if str(raw.get("source_digest") or "") != source.digest:
            raise ValueError("objective proposal source digest mismatch")
        if str(raw.get("compiled_digest") or "") != compiled.digest:
            raise ValueError("objective proposal compiled digest mismatch")
        if str(raw.get("proposal_id") or "") != record.proposal_id:
            raise ValueError("objective proposal id mismatch")
        return record


@dataclass(frozen=True)
class ObjectiveProposalLedger:
    records: tuple[ObjectiveProposalRecord, ...] = ()

    def by_source_digest(self, digest: str) -> ObjectiveProposalRecord | None:
        found = [record for record in self.records if record.source.digest == digest]
        if len(found) > 1:
            raise ValueError("duplicate objective proposal source identity")
        return found[0] if found else None

    def put(self, record: ObjectiveProposalRecord) -> "ObjectiveProposalLedger":
        existing = self.by_source_digest(record.source.digest)
        if existing is not None:
            if existing != record:
                raise ValueError("conflicting objective proposal for immutable source revision")
            return self
        return ObjectiveProposalLedger(self.records + (record,))

    def as_dict(self) -> dict[str, object]:
        return {"schema_version": 1, "records": [record.as_dict() for record in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveProposalLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid objective proposal ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("objective proposal records must be a list")
        ledger = cls()
        for value in values:
            ledger = ledger.put(ObjectiveProposalRecord.from_mapping(value))
        return ledger


@dataclass(frozen=True)
class ObjectiveCaptureResult:
    record: ObjectiveProposalRecord
    created: bool


class ObjectiveProposalStore:
    def __init__(self, adapter: JsonStateStoreAdapter) -> None:
        self.adapter = adapter

    @classmethod
    def for_root(cls, root: Path) -> "ObjectiveProposalStore":
        root = Path(root)
        return cls(JsonStateStoreAdapter(
            path=root / OBJECTIVE_PROPOSALS_RELATIVE_PATH,
            backup_path=root / OBJECTIVE_PROPOSALS_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> ObjectiveProposalLedger:
        return ObjectiveProposalLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ObjectiveProposalLedger) -> None:
        self.adapter.save(ledger.as_dict())

    def capture_record(self, record: ObjectiveProposalRecord) -> ObjectiveCaptureResult:
        ledger = self.load()
        existing = ledger.by_source_digest(record.source.digest)
        if existing is not None:
            if existing != record:
                raise ValueError(
                    "conflicting objective proposal for immutable source revision"
                )
            return ObjectiveCaptureResult(existing, False)
        self.save(ledger.put(record))
        return ObjectiveCaptureResult(record, True)

    def capture(
        self,
        *,
        source: ObjectiveSourceReference | DevelopmentApiObjectiveSource | ProgramObjectiveSource | ScopedObjectiveSource,
        delivery_id: str,
        root: Path,
    ) -> ObjectiveCaptureResult:
        # Preserve historical webhook redelivery semantics: immutable source identity,
        # not transport delivery id, is the idempotence boundary.
        ledger = self.load()
        existing = ledger.by_source_digest(source.digest)
        if existing is not None:
            return ObjectiveCaptureResult(existing, False)
        compiled = compile_objective(source.objective_text, root=root)
        record = ObjectiveProposalRecord(source, str(delivery_id or ""), compiled)
        self.save(ledger.put(record))
        return ObjectiveCaptureResult(record, True)


def parse_objective_comment(
    *,
    event_name: str,
    payload: Mapping[str, Any],
    repo: str,
    trusted_actors: tuple[str, ...],
) -> ObjectiveSourceReference | None:
    if event_name != "issue_comment":
        return None
    comment = payload.get("comment")
    if not isinstance(comment, Mapping):
        return None
    body = str(comment.get("body") or "")
    lines = body.splitlines()
    if not lines or lines[0].strip() != OBJECTIVE_MARKER:
        return None
    if payload.get("action") != "created":
        raise ValueError("objective ingress requires a newly-created GitHub comment")
    objective = "\n".join(lines[1:]).strip()
    if not objective:
        raise ValueError("objective marker must be followed by objective text")
    user = comment.get("user")
    issue = payload.get("issue")
    repository = payload.get("repository")
    if not isinstance(user, Mapping) or not isinstance(issue, Mapping) or not isinstance(repository, Mapping):
        raise ValueError("objective comment lacks GitHub source identity")
    actor = _required(user.get("login"), "objective actor").lower()
    trusted = {str(value).strip().lower() for value in trusted_actors if str(value).strip()}
    if actor not in trusted:
        raise ValueError("objective actor is not trusted")
    full_name = _required(repository.get("full_name"), "objective repo")
    if full_name != repo:
        raise ValueError("objective repository mismatch")
    return ObjectiveSourceReference(
        repo=full_name,
        issue_number=_positive(issue.get("number"), "objective issue number"),
        comment_id=_positive(comment.get("id"), "objective comment id"),
        actor=actor,
        created_at=comment.get("created_at"),
        updated_at=comment.get("updated_at"),
        objective_text=objective,
    )
