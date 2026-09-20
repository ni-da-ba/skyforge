"""Durable typed capture for project-significant human review events."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import re
from typing import Any, Mapping

from .identity import canonical_digest
from .state_store import JsonStateStoreAdapter

HUMAN_REVIEW_MARKER = "SKYFORGE HUMAN REVIEW"
HUMAN_REVIEWS_RELATIVE_PATH = Path(".skyforge-platform-v2/human-reviews.json")
HUMAN_REVIEWS_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2/human-reviews.json.bak")
_SHA_RE = re.compile(r"^[0-9a-f]{40}$")


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _string_list(value: Any, label: str, *, required: bool) -> tuple[str, ...]:
    if value is None and not required:
        return ()
    if not isinstance(value, list):
        raise ValueError(f"{label} must be a list")
    items = tuple(_required(item, label) for item in value)
    if required and not items:
        raise ValueError(f"{label} must not be empty")
    return items


class HumanReviewVerdict(str, Enum):
    ACCEPTED = "ACCEPTED"
    CHANGES_REQUIRED = "CHANGES_REQUIRED"


@dataclass(frozen=True)
class HumanReviewSource:
    repo: str
    issue_number: int
    comment_id: int
    actor: str
    created_at: str
    updated_at: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "repo", _required(self.repo, "review repo"))
        if isinstance(self.issue_number, bool) or self.issue_number <= 0:
            raise ValueError("review issue_number must be positive")
        if isinstance(self.comment_id, bool) or self.comment_id <= 0:
            raise ValueError("review comment_id must be positive")
        object.__setattr__(self, "actor", _required(self.actor, "review actor").lower())
        object.__setattr__(self, "created_at", _required(self.created_at, "review created_at"))
        object.__setattr__(self, "updated_at", _required(self.updated_at, "review updated_at"))

    def as_dict(self) -> dict[str, object]:
        return {
            "repo": self.repo,
            "issue_number": self.issue_number,
            "comment_id": self.comment_id,
            "actor": self.actor,
            "created_at": self.created_at,
            "updated_at": self.updated_at,
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class HumanReviewSubmission:
    source: HumanReviewSource
    gate_id: str
    artifact_id: str
    source_sha: str
    verdict: HumanReviewVerdict
    findings: tuple[str, ...]
    positive_findings: tuple[str, ...]
    material_delta: str
    next_boundary: str
    deferred_product_work: bool
    prior_review_id: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "gate_id", _required(self.gate_id, "gate_id"))
        object.__setattr__(self, "artifact_id", _required(self.artifact_id, "artifact_id"))
        sha = _required(self.source_sha, "source_sha").lower()
        if not _SHA_RE.fullmatch(sha):
            raise ValueError("source_sha must be an exact 40-character lowercase hex commit")
        object.__setattr__(self, "source_sha", sha)
        if not isinstance(self.verdict, HumanReviewVerdict):
            object.__setattr__(self, "verdict", HumanReviewVerdict(str(self.verdict)))
        if not self.findings:
            raise ValueError("findings must not be empty")
        object.__setattr__(self, "material_delta", _required(self.material_delta, "material_delta"))
        object.__setattr__(self, "next_boundary", _required(self.next_boundary, "next_boundary"))
        if not isinstance(self.deferred_product_work, bool):
            raise ValueError("deferred_product_work must be a boolean")
        if self.prior_review_id is not None:
            object.__setattr__(
                self, "prior_review_id", _required(self.prior_review_id, "prior_review_id")
            )

    def identity_payload(self) -> dict[str, object]:
        return {
            "source": self.source.as_dict(),
            "source_digest": self.source.digest,
            "gate_id": self.gate_id,
            "artifact_id": self.artifact_id,
            "source_sha": self.source_sha,
            "verdict": self.verdict.value,
            "findings": list(self.findings),
            "positive_findings": list(self.positive_findings),
            "material_delta": self.material_delta,
            "next_boundary": self.next_boundary,
            "deferred_product_work": self.deferred_product_work,
            "prior_review_id": self.prior_review_id,
        }

    @property
    def review_id(self) -> str:
        return canonical_digest(self.identity_payload())

    def as_dict(self) -> dict[str, object]:
        return {"review_id": self.review_id, **self.identity_payload()}

    @classmethod
    def from_mapping(cls, raw: Any) -> "HumanReviewSubmission":
        if not isinstance(raw, Mapping):
            raise ValueError("human review record must be an object")
        src = raw.get("source")
        if not isinstance(src, Mapping):
            raise ValueError("human review source must be an object")
        source = HumanReviewSource(
            repo=src.get("repo"),
            issue_number=src.get("issue_number"),
            comment_id=src.get("comment_id"),
            actor=src.get("actor"),
            created_at=src.get("created_at"),
            updated_at=src.get("updated_at"),
        )
        record = cls(
            source=source,
            gate_id=raw.get("gate_id"),
            artifact_id=raw.get("artifact_id"),
            source_sha=raw.get("source_sha"),
            verdict=HumanReviewVerdict(str(raw.get("verdict") or "")),
            findings=_string_list(raw.get("findings"), "findings", required=True),
            positive_findings=_string_list(
                raw.get("positive_findings"), "positive_findings", required=False
            ),
            material_delta=raw.get("material_delta"),
            next_boundary=raw.get("next_boundary"),
            deferred_product_work=raw.get("deferred_product_work"),
            prior_review_id=raw.get("prior_review_id"),
        )
        if str(raw.get("source_digest") or "") != source.digest:
            raise ValueError("human review source digest mismatch")
        if str(raw.get("review_id") or "") != record.review_id:
            raise ValueError("human review id mismatch")
        return record


@dataclass(frozen=True)
class HumanReviewLedger:
    records: tuple[HumanReviewSubmission, ...] = ()

    def by_source_digest(self, digest: str) -> HumanReviewSubmission | None:
        matches = [record for record in self.records if record.source.digest == digest]
        if len(matches) > 1:
            raise ValueError("duplicate human review source identity")
        return matches[0] if matches else None

    def put(self, record: HumanReviewSubmission) -> "HumanReviewLedger":
        existing = self.by_source_digest(record.source.digest)
        if existing is not None:
            if existing != record:
                raise ValueError("conflicting human review for immutable source revision")
            return self
        if any(item.review_id == record.review_id for item in self.records):
            return self
        return HumanReviewLedger(self.records + (record,))

    def latest_for_gate(self, gate_id: str) -> HumanReviewSubmission | None:
        key = _required(gate_id, "gate_id")
        values = [record for record in self.records if record.gate_id == key]
        return values[-1] if values else None

    def as_dict(self) -> dict[str, object]:
        return {"schema_version": 1, "records": [record.as_dict() for record in self.records]}

    @classmethod
    def from_mapping(cls, raw: Any) -> "HumanReviewLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid human review ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("human review records must be a list")
        ledger = cls()
        for value in values:
            ledger = ledger.put(HumanReviewSubmission.from_mapping(value))
        return ledger


@dataclass(frozen=True)
class HumanReviewCaptureResult:
    record: HumanReviewSubmission
    created: bool


class HumanReviewStore:
    def __init__(self, adapter: JsonStateStoreAdapter) -> None:
        self.adapter = adapter

    @classmethod
    def for_root(cls, root: Path) -> "HumanReviewStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / HUMAN_REVIEWS_RELATIVE_PATH,
                backup_path=root / HUMAN_REVIEWS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> HumanReviewLedger:
        return HumanReviewLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: HumanReviewLedger) -> None:
        self.adapter.save(ledger.as_dict())

    def capture(self, submission: HumanReviewSubmission) -> HumanReviewCaptureResult:
        ledger = self.load()
        existing = ledger.by_source_digest(submission.source.digest)
        if existing is not None:
            if existing != submission:
                raise ValueError("conflicting human review for immutable source revision")
            return HumanReviewCaptureResult(existing, False)
        self.save(ledger.put(submission))
        return HumanReviewCaptureResult(submission, True)


def parse_human_review_comment(
    *,
    event_name: str,
    payload: Mapping[str, Any],
    repo: str,
    trusted_actors: tuple[str, ...],
) -> HumanReviewSubmission | None:
    if event_name != "issue_comment":
        return None
    comment = payload.get("comment")
    if not isinstance(comment, Mapping):
        return None
    body = str(comment.get("body") or "")
    lines = body.splitlines()
    if not lines or lines[0].strip() != HUMAN_REVIEW_MARKER:
        return None
    if payload.get("action") != "created":
        raise ValueError("human review ingress requires a newly-created GitHub comment")
    encoded = "\n".join(lines[1:]).strip()
    if not encoded:
        raise ValueError("human review marker must be followed by a JSON object")
    try:
        review = json.loads(encoded)
    except json.JSONDecodeError as exc:
        raise ValueError("human review payload must be valid JSON") from exc
    if not isinstance(review, Mapping):
        raise ValueError("human review payload must be a JSON object")

    user = comment.get("user")
    issue = payload.get("issue")
    repository = payload.get("repository")
    if not isinstance(user, Mapping) or not isinstance(issue, Mapping) or not isinstance(repository, Mapping):
        raise ValueError("human review comment lacks GitHub source identity")
    actor = _required(user.get("login"), "review actor").lower()
    trusted = {str(value).strip().lower() for value in trusted_actors if str(value).strip()}
    if actor not in trusted:
        raise ValueError("human review actor is not trusted")
    full_name = _required(repository.get("full_name"), "review repo")
    if full_name != repo:
        raise ValueError("human review repository mismatch")

    source = HumanReviewSource(
        repo=full_name,
        issue_number=issue.get("number"),
        comment_id=comment.get("id"),
        actor=actor,
        created_at=comment.get("created_at"),
        updated_at=comment.get("updated_at"),
    )
    return HumanReviewSubmission(
        source=source,
        gate_id=review.get("gate_id"),
        artifact_id=review.get("artifact_id"),
        source_sha=review.get("source_sha"),
        verdict=HumanReviewVerdict(str(review.get("verdict") or "")),
        findings=_string_list(review.get("findings"), "findings", required=True),
        positive_findings=_string_list(
            review.get("positive_findings"), "positive_findings", required=False
        ),
        material_delta=review.get("material_delta"),
        next_boundary=review.get("next_boundary"),
        deferred_product_work=review.get("deferred_product_work"),
        prior_review_id=review.get("prior_review_id"),
    )
