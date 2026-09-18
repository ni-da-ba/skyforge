"""Durable task-event authority composition for Platform v2 R5C8.

This module preserves the accepted DurableEvent identity schema. Trusted actor/revision
metadata from the signed GitHub webhook is captured in a separate durable record keyed by
the unchanged event_id, then freshly hydrated through R5C7 before an R5C6 request can be
constructed.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
from typing import Any, Iterable, Mapping

from .classifier_provider import ClassifierRequest
from .decision import DecisionFreshnessObservation
from .events import DurableEvent
from .external import ExternalProducerClaim
from .identity import canonical_digest
from .ordinary_pipeline import OrdinaryPipelineRequest
from .quota import LocalBudgetObservation, ProviderQuotaDecision
from .state_store import JsonStateStoreAdapter
from .task_authority import (
    TaskAuthorityDisposition,
    TaskAuthorityHydrationResult,
    TaskAuthorityWakeReference,
)


TASK_AUTHORITY_EVENTS_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "task-authority-events.json"
)
TASK_AUTHORITY_EVENTS_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "task-authority-events.json.bak"
)
_EVENT_ID_RE = re.compile(r"^sha256:[0-9a-f]{64}$")
_REPO_RE = re.compile(r"^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")


def _required_text(value: Any, label: str) -> str:
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{label} must be non-empty text")
    return value.strip()


def _positive_int(value: Any, label: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise ValueError(f"{label} must be a positive integer")
    return value


def _repo(value: Any) -> str:
    text = _required_text(value, "repo")
    if not _REPO_RE.fullmatch(text):
        raise ValueError("repo must be owner/name using GitHub-safe characters")
    return text


def _event_id(value: Any) -> str:
    text = _required_text(value, "event_id")
    if not _EVENT_ID_RE.fullmatch(text):
        raise ValueError("event_id must be canonical sha256 durable-event identity")
    return text


@dataclass(frozen=True)
class TaskAuthorityEventRecord:
    event_id: str
    reference: TaskAuthorityWakeReference
    delivery_id: str = ""

    def __post_init__(self) -> None:
        object.__setattr__(self, "event_id", _event_id(self.event_id))
        if not isinstance(self.reference, TaskAuthorityWakeReference):
            raise ValueError("reference must be TaskAuthorityWakeReference")
        if not isinstance(self.delivery_id, str):
            raise ValueError("delivery_id must be a string")

    def as_dict(self) -> dict[str, Any]:
        ref = self.reference
        return {
            "event_id": self.event_id,
            "reference": {
                "repo": ref.repo,
                "issue_number": ref.issue_number,
                "comment_id": ref.comment_id,
                "actor": ref.actor,
                "body": ref.body,
                "created_at": ref.created_at,
                "updated_at": ref.updated_at,
                "digest": ref.digest,
            },
            "delivery_id": self.delivery_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "TaskAuthorityEventRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("task authority event record must be an object")
        ref_raw = raw.get("reference")
        if not isinstance(ref_raw, Mapping):
            raise ValueError("task authority event reference must be an object")
        ref = TaskAuthorityWakeReference(
            repo=ref_raw.get("repo"),
            issue_number=ref_raw.get("issue_number"),
            comment_id=ref_raw.get("comment_id"),
            actor=ref_raw.get("actor"),
            body=ref_raw.get("body"),
            created_at=ref_raw.get("created_at"),
            updated_at=ref_raw.get("updated_at"),
        )
        recorded_digest = _required_text(
            ref_raw.get("digest"), "task authority reference digest"
        )
        if recorded_digest != ref.digest:
            raise ValueError("task authority reference digest mismatch")
        return cls(
            event_id=raw.get("event_id"),
            reference=ref,
            delivery_id=str(raw.get("delivery_id") or ""),
        )

    @property
    def authority_digest(self) -> str:
        return canonical_digest(
            {
                "event_id": self.event_id,
                "reference_digest": self.reference.digest,
            }
        )

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


def capture_task_authority_event(
    *,
    event: DurableEvent,
    payload: Mapping[str, Any],
    repo: str,
    trusted_actors: Iterable[str],
    delivery_id: str | None = None,
) -> TaskAuthorityEventRecord | None:
    """Bind one already-classified task event to exact signed-webhook source metadata.

    Non-task events return None. Task events fail closed on any disagreement between the
    legacy-compatible DurableEvent and the raw webhook payload.
    """

    if not isinstance(event, DurableEvent):
        raise ValueError("event must be DurableEvent")
    if event.signal_kind != "task":
        return None
    if not isinstance(payload, Mapping):
        raise ValueError("webhook payload must be an object")

    repo = _repo(repo)
    if not event.actionable:
        raise ValueError("task authority event must be actionable")
    if event.event != "issue_comment" or event.action != "audit_signal":
        raise ValueError("task authority event must be issue_comment/audit_signal")

    repository = payload.get("repository") or {}
    issue = payload.get("issue") or {}
    comment = payload.get("comment") or {}
    if not all(isinstance(v, Mapping) for v in (repository, issue, comment)):
        raise ValueError("task webhook repository/issue/comment must be objects")
    full_name = str(repository.get("full_name") or "").strip()
    if full_name and full_name.lower() != repo.lower():
        raise ValueError("task webhook repository identity mismatch")
    if str(payload.get("action") or "").lower() != "created":
        raise ValueError("task authority capture requires newly-created comment")

    issue_number = _positive_int(issue.get("number"), "webhook issue number")
    comment_id = _positive_int(comment.get("id"), "webhook comment id")
    body = _required_text(comment.get("body"), "webhook comment body")
    created_at = _required_text(comment.get("created_at"), "webhook comment created_at")
    updated_at = _required_text(comment.get("updated_at"), "webhook comment updated_at")
    user = comment.get("user") or {}
    if not isinstance(user, Mapping):
        raise ValueError("webhook comment user must be an object")
    actor = _required_text(user.get("login"), "webhook comment actor")

    trusted = {
        str(value).strip().lower()
        for value in trusted_actors
        if str(value).strip()
    }
    if actor.lower() not in trusted:
        raise ValueError("webhook task actor is not trusted")

    if event.task_issue_number != issue_number:
        raise ValueError("DurableEvent task issue differs from webhook issue")
    if str(event.source_id or "") != str(comment_id):
        raise ValueError("DurableEvent source_id differs from webhook comment")
    if str(event.signal_text or "") != body:
        raise ValueError("DurableEvent signal_text differs from webhook comment body")
    if str(event.observed_at or "") != created_at:
        raise ValueError("DurableEvent observed_at differs from webhook comment created_at")

    reference = TaskAuthorityWakeReference(
        repo=repo,
        issue_number=issue_number,
        comment_id=comment_id,
        actor=actor,
        body=body,
        created_at=created_at,
        updated_at=updated_at,
    )
    return TaskAuthorityEventRecord(
        event_id=event.event_id,
        reference=reference,
        delivery_id=str(delivery_id or "").strip(),
    )


@dataclass(frozen=True)
class TaskAuthorityEventLedger:
    records: tuple[TaskAuthorityEventRecord, ...] = ()

    def get(self, event_id: str) -> TaskAuthorityEventRecord | None:
        key = _event_id(event_id)
        matches = [record for record in self.records if record.event_id == key]
        if len(matches) > 1:
            raise ValueError("duplicate task authority event records in durable ledger")
        return matches[0] if matches else None

    def put(self, record: TaskAuthorityEventRecord) -> "TaskAuthorityEventLedger":
        if not isinstance(record, TaskAuthorityEventRecord):
            raise ValueError("record must be TaskAuthorityEventRecord")
        existing = self.get(record.event_id)
        if existing is not None:
            if existing.authority_digest != record.authority_digest:
                raise ValueError(
                    "conflicting task authority capture for existing durable event_id"
                )
            # Delivery identity is transport diagnostics, not repository authority.
            # Keep the first durable capture rather than rewriting it on redelivery.
            return self
        return TaskAuthorityEventLedger(self.records + (record,))

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "records": [record.as_dict() for record in self.records],
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "TaskAuthorityEventLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid task authority event ledger")
        values = raw.get("records")
        if not isinstance(values, list):
            raise ValueError("task authority event records must be a list")
        ledger = cls()
        for value in values:
            ledger = ledger.put(TaskAuthorityEventRecord.from_mapping(value))
        return ledger

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class TaskAuthorityEventStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "TaskAuthorityEventStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / TASK_AUTHORITY_EVENTS_RELATIVE_PATH,
                backup_path=root / TASK_AUTHORITY_EVENTS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> TaskAuthorityEventLedger:
        return TaskAuthorityEventLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: TaskAuthorityEventLedger) -> None:
        self.adapter.save(ledger.as_dict())

    def capture(self, record: TaskAuthorityEventRecord) -> TaskAuthorityEventLedger:
        ledger = self.load().put(record)
        self.save(ledger)
        return ledger


@dataclass(frozen=True)
class TaskPipelineSeed:
    event_id: str
    authority_identity_digest: str
    authority_digest: str
    classifier_request: ClassifierRequest
    issue_number: int

    def __post_init__(self) -> None:
        object.__setattr__(self, "event_id", _event_id(self.event_id))
        _required_text(self.authority_identity_digest, "authority_identity_digest")
        _required_text(self.authority_digest, "authority_digest")
        if not isinstance(self.classifier_request, ClassifierRequest):
            raise ValueError("classifier_request must be ClassifierRequest")
        _positive_int(self.issue_number, "issue_number")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "event_id": self.event_id,
                "authority_identity_digest": self.authority_identity_digest,
                "authority_digest": self.authority_digest,
                "classifier_request_id": self.classifier_request.request_id,
                "issue_number": self.issue_number,
            }
        )


def build_task_pipeline_seed(
    *,
    record: TaskAuthorityEventRecord,
    hydration: TaskAuthorityHydrationResult,
    current_main: str,
) -> TaskPipelineSeed:
    if hydration.disposition is not TaskAuthorityDisposition.EXECUTABLE_V2:
        raise ValueError("task authority hydration is not executable by Platform v2")
    if hydration.identity is None or hydration.authority is None:
        raise ValueError("executable hydration lacks identity/authority")
    ref = record.reference
    identity = hydration.identity
    authority = hydration.authority
    if identity.digest not in authority.authority_key:
        raise ValueError("hydrated authority key does not bind authority identity")
    if identity.issue_number != ref.issue_number or identity.comment_id != ref.comment_id:
        raise ValueError("hydrated authority identity differs from durable task event")
    if identity.actor.lower() != ref.actor.lower():
        raise ValueError("hydrated authority actor differs from durable task event")
    if tuple(authority.issue_numbers) != (ref.issue_number,):
        raise ValueError("hydrated authority issue scope differs from durable task event")

    semantic_input = {
        "event_keys": [record.event_id],
        "authority_event_keys": [record.event_id],
        "ordinary_event_keys": [],
        "task_issue_numbers": [ref.issue_number],
        "repository_snapshot": {"main": current_main},
        "task_authority": {
            "identity_digest": identity.digest,
            "authority_digest": authority.digest,
            "lane": authority.lane,
            "objective": authority.objective,
            "stop_boundary": authority.stop_boundary,
            "allowed_paths": list(authority.allowed_paths),
            "protected_paths": list(authority.protected_paths),
            "auto_merge_eligible": authority.auto_merge_eligible,
        },
        "task_context": authority.context_text,
    }
    request = ClassifierRequest(
        current_main=current_main,
        semantic_input=semantic_input,
    )
    return TaskPipelineSeed(
        event_id=record.event_id,
        authority_identity_digest=identity.digest,
        authority_digest=authority.digest,
        classifier_request=request,
        issue_number=ref.issue_number,
    )


def compose_ordinary_task_request(
    *,
    seed: TaskPipelineSeed,
    hydration: TaskAuthorityHydrationResult,
    freshness: DecisionFreshnessObservation,
    external_claims: Iterable[ExternalProducerClaim],
    provider_quota: ProviderQuotaDecision | None,
    local_budget: LocalBudgetObservation,
    attempt_number: int,
    repo: str,
    pr_title: str,
    pr_body: str,
    source_pr_head_at_classification: str = "",
    controller_comment_body: str = "",
) -> OrdinaryPipelineRequest:
    """Build the accepted R5C6 request without invoking classifier/worker/effects."""

    if hydration.disposition is not TaskAuthorityDisposition.EXECUTABLE_V2:
        raise ValueError("task authority hydration is not executable by Platform v2")
    if hydration.authority is None or hydration.identity is None:
        raise ValueError("executable hydration lacks identity/authority")
    if hydration.authority.digest != seed.authority_digest:
        raise ValueError("pipeline seed authority digest differs from hydration")
    if hydration.identity.digest != seed.authority_identity_digest:
        raise ValueError("pipeline seed authority identity differs from hydration")
    if seed.classifier_request.current_main != freshness.current_main:
        raise ValueError("pipeline seed main differs from freshness observation")

    comment_body = str(controller_comment_body or "").strip()
    return OrdinaryPipelineRequest(
        classifier_request=seed.classifier_request,
        authority=hydration.authority,
        freshness=freshness,
        current_main=seed.classifier_request.current_main,
        external_claims=tuple(external_claims),
        provider_quota=provider_quota,
        local_budget=local_budget,
        attempt_number=attempt_number,
        repo=_repo(repo),
        pr_title=_required_text(pr_title, "pr_title"),
        pr_body=_required_text(pr_body, "pr_body"),
        event_keys=(seed.event_id,),
        authority_event_keys=(seed.event_id,),
        ordinary_event_keys=(),
        source_pr_head_at_classification=str(source_pr_head_at_classification or ""),
        controller_comment_issue=seed.issue_number if comment_body else None,
        controller_comment_body=comment_body,
    )
