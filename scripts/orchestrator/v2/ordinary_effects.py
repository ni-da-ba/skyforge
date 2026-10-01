"""Durable ordinary-task effect ledger and exact mutation scopes for Platform v2."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping

from .effects import (
    EffectKind,
    EffectStatus,
    RemoteEffectIdentity,
    RemoteEffectRecord,
)
from .identity import canonical_digest
from .state_store import JsonStateStoreAdapter


ORDINARY_EFFECTS_RELATIVE_PATH = Path(".skyforge-platform-v2") / "ordinary-effects.json"
ORDINARY_EFFECTS_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2") / "ordinary-effects.json.bak"


def _required_text(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


@dataclass(frozen=True)
class OrdinaryMutationScope:
    attempt_id: str
    repo: str
    base_sha: str
    branch: str
    expected_head_sha: str
    pr_title: str
    pr_body: str
    issue_number: int | None = None
    base_ref: str = "main"

    def __post_init__(self) -> None:
        for name in (
            "attempt_id",
            "repo",
            "base_sha",
            "branch",
            "expected_head_sha",
            "pr_title",
            "pr_body",
            "base_ref",
        ):
            object.__setattr__(
                self,
                name,
                _required_text(getattr(self, name), f"mutation scope {name}"),
            )
        for name in ("base_sha", "expected_head_sha"):
            value = getattr(self, name)
            if len(value) != 40 or any(ch not in "0123456789abcdef" for ch in value):
                raise ValueError("mutation scope SHAs must be lowercase 40-character hex")
        if "/" not in self.repo or self.repo.count("/") != 1:
            raise ValueError("mutation scope repo must be owner/name")
        ref = self.base_ref
        if (
            ref.startswith((".", "/"))
            or ref.endswith((".", "/"))
            or ".." in ref
            or "//" in ref
            or "@{" in ref
            or any(ch in ref for ch in " ~^:?*[\\")
        ):
            raise ValueError("mutation scope base_ref is not a safe Git ref name")
        if self.issue_number is not None:
            _positive_int(self.issue_number, "mutation scope issue_number")

    def as_dict(self) -> dict[str, Any]:
        value = {
            "attempt_id": self.attempt_id,
            "repo": self.repo,
            "base_sha": self.base_sha,
            "branch": self.branch,
            "expected_head_sha": self.expected_head_sha,
            "pr_title": self.pr_title,
            "pr_body": self.pr_body,
            "issue_number": self.issue_number,
        }
        # Preserve the exact legacy serialization/digest for ordinary main-scoped
        # effects and durable handoffs. Non-main rehearsal scopes bind the target ref
        # explicitly so it cannot be substituted without changing identity.
        if self.base_ref != "main":
            value["base_ref"] = self.base_ref
        return value

    @classmethod
    def from_mapping(cls, raw: Any) -> "OrdinaryMutationScope":
        if not isinstance(raw, Mapping):
            raise ValueError("ordinary mutation scope must be an object")
        issue_number = raw.get("issue_number")
        if issue_number is not None:
            issue_number = _positive_int(issue_number, "mutation scope issue_number")
        return cls(
            attempt_id=raw.get("attempt_id"),
            repo=raw.get("repo"),
            base_sha=raw.get("base_sha"),
            branch=raw.get("branch"),
            expected_head_sha=raw.get("expected_head_sha"),
            pr_title=raw.get("pr_title"),
            pr_body=raw.get("pr_body"),
            base_ref=raw.get("base_ref") or "main",
            issue_number=issue_number,
        )

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def push_identity(self) -> RemoteEffectIdentity:
        return RemoteEffectIdentity.create(
            attempt_id=self.attempt_id,
            kind=EffectKind.PUSH_BRANCH,
            subject=f"{self.branch}@{self.expected_head_sha}",
        )

    def create_pr_identity(self) -> RemoteEffectIdentity:
        subject = (
            f"{self.branch}->{self.base_sha}"
            if self.base_ref == "main"
            else f"{self.branch}->{self.base_ref}@{self.base_sha}"
        )
        return RemoteEffectIdentity.create(
            attempt_id=self.attempt_id,
            kind=EffectKind.CREATE_PR,
            subject=subject,
        )

    def ready_identity(self, pr_number: int) -> RemoteEffectIdentity:
        number = _positive_int(pr_number, "ready pr_number")
        return RemoteEffectIdentity.create(
            attempt_id=self.attempt_id,
            kind=EffectKind.UPDATE_PR,
            subject=f"pr:{number}:ready@{self.expected_head_sha}",
        )

    def merge_identity(self, pr_number: int) -> RemoteEffectIdentity:
        number = _positive_int(pr_number, "merge pr_number")
        subject = (
            f"pr:{number}@{self.expected_head_sha}"
            if self.base_ref == "main"
            else f"pr:{number}:{self.base_ref}@{self.expected_head_sha}"
        )
        return RemoteEffectIdentity.create(
            attempt_id=self.attempt_id,
            kind=EffectKind.MERGE_PR,
            subject=subject,
        )

    def comment_identity(self, body: str) -> RemoteEffectIdentity:
        if self.issue_number is None:
            raise ValueError("comment effect requires frozen issue_number")
        body_digest = canonical_digest({"body": _required_text(body, "comment body")})
        return RemoteEffectIdentity.create(
            attempt_id=self.attempt_id,
            kind=EffectKind.POST_COMMENT,
            subject=f"issue:{self.issue_number}:body:{body_digest}",
        )


def effect_as_dict(record: RemoteEffectRecord) -> dict[str, Any]:
    return {
        "identity": {
            "attempt_id": record.identity.attempt_id,
            "kind": record.identity.kind.value,
            "subject": record.identity.subject,
            "effect_id": record.identity.effect_id,
        },
        "status": record.status.value,
        "remote_identity": record.remote_identity,
    }


def effect_from_mapping(raw: Any) -> RemoteEffectRecord:
    if not isinstance(raw, Mapping):
        raise ValueError("ordinary effect record must be an object")
    identity_raw = raw.get("identity")
    if not isinstance(identity_raw, Mapping):
        raise ValueError("ordinary effect identity must be an object")
    try:
        kind = EffectKind(str(identity_raw.get("kind") or ""))
        status = EffectStatus(str(raw.get("status") or ""))
    except ValueError as exc:
        raise ValueError("ordinary effect record contains unknown enum") from exc
    identity = RemoteEffectIdentity.create(
        attempt_id=_required_text(identity_raw.get("attempt_id"), "effect attempt_id"),
        kind=kind,
        subject=_required_text(identity_raw.get("subject"), "effect subject"),
    )
    if str(identity_raw.get("effect_id") or "") != identity.effect_id:
        raise ValueError("ordinary effect_id does not match canonical identity")
    return RemoteEffectRecord(
        identity=identity,
        status=status,
        remote_identity=str(raw.get("remote_identity") or ""),
    )


@dataclass(frozen=True)
class OrdinaryEffectLedger:
    records: tuple[RemoteEffectRecord, ...] = ()

    @classmethod
    def from_mapping(cls, raw: Any) -> "OrdinaryEffectLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping):
            raise ValueError("ordinary effect ledger must be an object")
        if raw.get("schema_version") != 1:
            raise ValueError("unsupported ordinary effect ledger schema_version")
        values = raw.get("records", [])
        if not isinstance(values, list):
            raise ValueError("ordinary effect ledger records must be a list")
        records = tuple(effect_from_mapping(value) for value in values)
        ids = [record.identity.effect_id for record in records]
        if len(set(ids)) != len(ids):
            raise ValueError("ordinary effect ledger contains duplicate effect_id")
        return cls(records=records)

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "records": [effect_as_dict(record) for record in self.records],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def get(self, identity: RemoteEffectIdentity) -> RemoteEffectRecord | None:
        for record in self.records:
            if record.identity.effect_id == identity.effect_id:
                if record.identity != identity:
                    raise ValueError("effect_id collision with different canonical identity")
                return record
        return None

    def begin(self, identity: RemoteEffectIdentity) -> "OrdinaryEffectLedger":
        existing = self.get(identity)
        if existing is not None:
            return self
        return OrdinaryEffectLedger(
            records=self.records + (RemoteEffectRecord.begin(identity),)
        )

    def abandon(self, identity: RemoteEffectIdentity) -> "OrdinaryEffectLedger":
        existing = self.get(identity)
        if existing is None:
            raise ValueError("cannot abandon effect before durable begin")
        abandoned = existing.abandon()
        return OrdinaryEffectLedger(
            records=tuple(
                abandoned if record.identity.effect_id == identity.effect_id else record
                for record in self.records
            )
        )

    def complete(
        self,
        identity: RemoteEffectIdentity,
        remote_identity: str,
    ) -> "OrdinaryEffectLedger":
        existing = self.get(identity)
        if existing is None:
            raise ValueError("cannot complete effect before durable begin")
        completed = existing.complete(remote_identity)
        return OrdinaryEffectLedger(
            records=tuple(
                completed if record.identity.effect_id == identity.effect_id else record
                for record in self.records
            )
        )


@dataclass(frozen=True)
class OrdinaryEffectStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "OrdinaryEffectStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / ORDINARY_EFFECTS_RELATIVE_PATH,
                backup_path=root / ORDINARY_EFFECTS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> OrdinaryEffectLedger:
        return OrdinaryEffectLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: OrdinaryEffectLedger) -> OrdinaryEffectLedger:
        self.adapter.save(ledger.as_dict())
        return ledger
