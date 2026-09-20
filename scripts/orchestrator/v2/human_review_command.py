"""Durable two-phase command journal for artifact-bound human reviews."""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
from typing import Any, Mapping

from .human_review import (
    DevelopmentApiHumanReviewSource,
    HumanReviewStore,
    HumanReviewSubmission,
)
from .identity import canonical_digest
from .roadmap_service import (
    HumanGateReviewDisposition,
    RoadmapAuthorityLedger,
    RoadmapAuthorityStore,
    apply_human_gate_review,
)
from .roadmap_shadow import ShadowRoadmapManifest
from .state_store import JsonStateStoreAdapter

COMMANDS_RELATIVE_PATH = Path(".skyforge-platform-v2/human-review-commands.json")
COMMANDS_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2/human-review-commands.json.bak")


class HumanReviewCommandPhase(str, Enum):
    PREPARED = "PREPARED"
    REVIEW_PERSISTED = "REVIEW_PERSISTED"
    RECONCILED = "RECONCILED"


@dataclass(frozen=True)
class HumanReviewCommandRecord:
    request_id: str
    request_digest: str
    review: HumanReviewSubmission
    roadmap_before_digest: str
    roadmap_after_digest: str
    roadmap_disposition: HumanGateReviewDisposition
    phase: HumanReviewCommandPhase

    def __post_init__(self) -> None:
        if not isinstance(self.review.source, DevelopmentApiHumanReviewSource):
            raise ValueError("human-review command requires DEVELOPMENT_API review source")
        if self.review.source.request_id != self.request_id:
            raise ValueError("command request_id must match review source")
        expected_request_digest = canonical_digest(self.review.as_dict())
        if self.request_digest != expected_request_digest:
            raise ValueError("human-review command request digest mismatch")
        if not self.roadmap_before_digest or not self.roadmap_after_digest:
            raise ValueError("human-review command requires roadmap before/after digests")
        if self.roadmap_disposition not in {
            HumanGateReviewDisposition.ACCEPTED,
            HumanGateReviewDisposition.CHANGES_REQUIRED,
        }:
            raise ValueError("command roadmap disposition must be an applied human judgment")
        if not isinstance(self.phase, HumanReviewCommandPhase):
            object.__setattr__(self, "phase", HumanReviewCommandPhase(str(self.phase)))

    @property
    def command_id(self) -> str:
        return canonical_digest(
            {
                "request_id": self.request_id,
                "request_digest": self.request_digest,
                "review_id": self.review.review_id,
                "roadmap_before_digest": self.roadmap_before_digest,
                "roadmap_after_digest": self.roadmap_after_digest,
                "roadmap_disposition": self.roadmap_disposition.value,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "command_id": self.command_id,
            "request_id": self.request_id,
            "request_digest": self.request_digest,
            "review": self.review.as_dict(),
            "roadmap_before_digest": self.roadmap_before_digest,
            "roadmap_after_digest": self.roadmap_after_digest,
            "roadmap_disposition": self.roadmap_disposition.value,
            "phase": self.phase.value,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "HumanReviewCommandRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("human-review command must be an object")
        record = cls(
            request_id=str(raw.get("request_id") or ""),
            request_digest=str(raw.get("request_digest") or ""),
            review=HumanReviewSubmission.from_mapping(raw.get("review")),
            roadmap_before_digest=str(raw.get("roadmap_before_digest") or ""),
            roadmap_after_digest=str(raw.get("roadmap_after_digest") or ""),
            roadmap_disposition=HumanGateReviewDisposition(
                str(raw.get("roadmap_disposition") or "")
            ),
            phase=HumanReviewCommandPhase(str(raw.get("phase") or "")),
        )
        if str(raw.get("command_id") or "") != record.command_id:
            raise ValueError("human-review command id mismatch")
        return record

    def with_phase(self, phase: HumanReviewCommandPhase) -> "HumanReviewCommandRecord":
        return replace(self, phase=phase)


@dataclass(frozen=True)
class HumanReviewCommandLedger:
    records: tuple[HumanReviewCommandRecord, ...] = ()

    def __post_init__(self) -> None:
        request_ids = [record.request_id for record in self.records]
        if len(request_ids) != len(set(request_ids)):
            raise ValueError("duplicate human-review command request_id")
        command_ids = [record.command_id for record in self.records]
        if len(command_ids) != len(set(command_ids)):
            raise ValueError("duplicate human-review command identity")

    def get(self, request_id: str) -> HumanReviewCommandRecord | None:
        matches = [record for record in self.records if record.request_id == request_id]
        if len(matches) > 1:
            raise ValueError("duplicate human-review command request identity")
        return matches[0] if matches else None

    @property
    def pending(self) -> tuple[HumanReviewCommandRecord, ...]:
        return tuple(
            record
            for record in self.records
            if record.phase is not HumanReviewCommandPhase.RECONCILED
        )

    def put(self, record: HumanReviewCommandRecord) -> "HumanReviewCommandLedger":
        existing = self.get(record.request_id)
        if existing is not None:
            if (
                existing.command_id != record.command_id
                or existing.request_digest != record.request_digest
            ):
                raise ValueError("conflicting human-review command request_id")
            values = tuple(
                record if item.request_id == record.request_id else item
                for item in self.records
            )
            return HumanReviewCommandLedger(values)
        return HumanReviewCommandLedger(self.records + (record,))

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "records": [record.as_dict() for record in self.records],
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "HumanReviewCommandLedger":
        if raw in (None, {}):
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid human-review command ledger")
        records = raw.get("records")
        if not isinstance(records, list):
            raise ValueError("human-review command records must be a list")
        return cls(tuple(HumanReviewCommandRecord.from_mapping(item) for item in records))


@dataclass(frozen=True)
class HumanReviewCommandStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "HumanReviewCommandStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / COMMANDS_RELATIVE_PATH,
                backup_path=root / COMMANDS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> HumanReviewCommandLedger:
        return HumanReviewCommandLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: HumanReviewCommandLedger) -> HumanReviewCommandLedger:
        self.adapter.save(ledger.as_dict())
        return ledger

    def put(self, record: HumanReviewCommandRecord) -> HumanReviewCommandRecord:
        ledger = self.load().put(record)
        self.save(ledger)
        stored = ledger.get(record.request_id)
        if stored is None:
            raise RuntimeError("human-review command disappeared after durable save")
        return stored


def prepare_human_review_command(
    *,
    store: HumanReviewCommandStore,
    review: HumanReviewSubmission,
    roadmap: RoadmapAuthorityLedger,
    manifest: ShadowRoadmapManifest,
) -> HumanReviewCommandRecord:
    if not isinstance(review.source, DevelopmentApiHumanReviewSource):
        raise ValueError("prepared human-review command requires API review source")

    request_digest = canonical_digest(review.as_dict())
    existing = store.load().get(review.source.request_id)
    if existing is not None:
        if existing.request_digest != request_digest:
            raise ValueError("conflicting payload for existing human-review request_id")
        return existing

    pending = store.load().pending
    if pending:
        raise ValueError(
            "another human-review command is pending durable reconciliation"
        )

    reduction = apply_human_gate_review(
        ledger=roadmap,
        manifest=manifest,
        gate_id=review.gate_id,
        verdict=review.verdict.value,
        review_id=review.review_id,
    )
    if reduction.disposition not in {
        HumanGateReviewDisposition.ACCEPTED,
        HumanGateReviewDisposition.CHANGES_REQUIRED,
    }:
        raise ValueError(f"human-review roadmap reconciliation blocked: {reduction.reason}")

    record = HumanReviewCommandRecord(
        request_id=review.source.request_id,
        request_digest=request_digest,
        review=review,
        roadmap_before_digest=roadmap.digest,
        roadmap_after_digest=reduction.ledger.digest,
        roadmap_disposition=reduction.disposition,
        phase=HumanReviewCommandPhase.PREPARED,
    )
    return store.put(record)


def advance_human_review_command(
    *,
    command_store: HumanReviewCommandStore,
    review_store: HumanReviewStore,
    roadmap_store: RoadmapAuthorityStore,
    manifest: ShadowRoadmapManifest,
    request_id: str,
) -> HumanReviewCommandRecord:
    ledger = command_store.load()
    record = ledger.get(request_id)
    if record is None:
        raise ValueError("human-review command does not exist")

    if record.phase is HumanReviewCommandPhase.RECONCILED:
        return record

    if record.phase is HumanReviewCommandPhase.PREPARED:
        captured = review_store.capture(record.review)
        if captured.record.review_id != record.review.review_id:
            raise ValueError("durable review identity differs from prepared command")
        record = command_store.put(
            record.with_phase(HumanReviewCommandPhase.REVIEW_PERSISTED)
        )

    roadmap = roadmap_store.load()
    if roadmap.digest == record.roadmap_after_digest:
        return command_store.put(
            record.with_phase(HumanReviewCommandPhase.RECONCILED)
        )
    if roadmap.digest != record.roadmap_before_digest:
        raise ValueError(
            "roadmap authority changed during human-review command; refusing stale reconciliation"
        )

    reduction = apply_human_gate_review(
        ledger=roadmap,
        manifest=manifest,
        gate_id=record.review.gate_id,
        verdict=record.review.verdict.value,
        review_id=record.review.review_id,
    )
    if reduction.disposition is not record.roadmap_disposition:
        raise ValueError("human-review roadmap reduction disposition drifted")
    if reduction.ledger.digest != record.roadmap_after_digest:
        raise ValueError("human-review roadmap reduction digest drifted")

    roadmap_store.save(reduction.ledger)
    return command_store.put(
        record.with_phase(HumanReviewCommandPhase.RECONCILED)
    )


def reconcile_pending_human_review_commands(
    *,
    command_store: HumanReviewCommandStore,
    review_store: HumanReviewStore,
    roadmap_store: RoadmapAuthorityStore,
    manifest: ShadowRoadmapManifest,
) -> tuple[HumanReviewCommandRecord, ...]:
    reconciled: list[HumanReviewCommandRecord] = []
    for record in command_store.load().pending:
        reconciled.append(
            advance_human_review_command(
                command_store=command_store,
                review_store=review_store,
                roadmap_store=roadmap_store,
                manifest=manifest,
                request_id=record.request_id,
            )
        )
    return tuple(reconciled)
