"""Durable exactly-once command journal for development-API objectives."""

from __future__ import annotations

from dataclasses import dataclass, replace
from enum import Enum
from pathlib import Path
from typing import Any, Mapping

from .identity import canonical_digest
from .objective_ingress import (
    DevelopmentApiObjectiveSource,
    ObjectiveProposalRecord,
    ObjectiveProposalStore,
)
from .objective_intake import compile_objective
from .state_store import JsonStateStoreAdapter

COMMANDS_RELATIVE_PATH = Path(".skyforge-platform-v2/objective-commands.json")
COMMANDS_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2/objective-commands.json.bak")


class ObjectiveCommandPhase(str, Enum):
    PREPARED = "PREPARED"
    PROPOSAL_PERSISTED = "PROPOSAL_PERSISTED"
    RECONCILED = "RECONCILED"


_PHASE_ORDER = {
    ObjectiveCommandPhase.PREPARED: 0,
    ObjectiveCommandPhase.PROPOSAL_PERSISTED: 1,
    ObjectiveCommandPhase.RECONCILED: 2,
}


@dataclass(frozen=True)
class ObjectiveCommandRecord:
    request_id: str
    request_digest: str
    proposal: ObjectiveProposalRecord
    phase: ObjectiveCommandPhase

    def __post_init__(self) -> None:
        if not isinstance(self.proposal.source, DevelopmentApiObjectiveSource):
            raise ValueError("objective command requires DEVELOPMENT_API objective source")
        if self.proposal.source.request_id != self.request_id:
            raise ValueError("command request_id must match objective source")
        expected = canonical_digest(self.proposal.source.as_dict())
        if self.request_digest != expected:
            raise ValueError("objective command request digest mismatch")
        if self.proposal.compiled.as_dict().get("executable_task_authority") is not False:
            raise ValueError("objective command may not contain executable task authority")
        if not isinstance(self.phase, ObjectiveCommandPhase):
            object.__setattr__(self, "phase", ObjectiveCommandPhase(str(self.phase)))

    @property
    def command_id(self) -> str:
        return canonical_digest(
            {
                "request_id": self.request_id,
                "request_digest": self.request_digest,
                "proposal_id": self.proposal.proposal_id,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "command_id": self.command_id,
            "request_id": self.request_id,
            "request_digest": self.request_digest,
            "proposal": self.proposal.as_dict(),
            "phase": self.phase.value,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveCommandRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("objective command must be an object")
        record = cls(
            request_id=str(raw.get("request_id") or ""),
            request_digest=str(raw.get("request_digest") or ""),
            proposal=ObjectiveProposalRecord.from_mapping(raw.get("proposal")),
            phase=ObjectiveCommandPhase(str(raw.get("phase") or "")),
        )
        if str(raw.get("command_id") or "") != record.command_id:
            raise ValueError("objective command id mismatch")
        return record

    def with_phase(self, phase: ObjectiveCommandPhase) -> "ObjectiveCommandRecord":
        return replace(self, phase=phase)


@dataclass(frozen=True)
class ObjectiveCommandLedger:
    records: tuple[ObjectiveCommandRecord, ...] = ()

    def __post_init__(self) -> None:
        request_ids = [record.request_id for record in self.records]
        if len(request_ids) != len(set(request_ids)):
            raise ValueError("duplicate objective command request_id")
        command_ids = [record.command_id for record in self.records]
        if len(command_ids) != len(set(command_ids)):
            raise ValueError("duplicate objective command identity")

    def get(self, request_id: str) -> ObjectiveCommandRecord | None:
        matches = [record for record in self.records if record.request_id == request_id]
        if len(matches) > 1:
            raise ValueError("duplicate objective command request identity")
        return matches[0] if matches else None

    @property
    def pending(self) -> tuple[ObjectiveCommandRecord, ...]:
        return tuple(
            record
            for record in self.records
            if record.phase is not ObjectiveCommandPhase.RECONCILED
        )

    def put(self, record: ObjectiveCommandRecord) -> "ObjectiveCommandLedger":
        existing = self.get(record.request_id)
        if existing is not None:
            if (
                existing.command_id != record.command_id
                or existing.request_digest != record.request_digest
            ):
                raise ValueError("conflicting objective command request_id")
            if _PHASE_ORDER[record.phase] < _PHASE_ORDER[existing.phase]:
                raise ValueError("objective command phase regression is forbidden")
            if record.phase is existing.phase:
                return self
            values = tuple(
                record if item.request_id == record.request_id else item
                for item in self.records
            )
            return ObjectiveCommandLedger(values)
        return ObjectiveCommandLedger(self.records + (record,))

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "records": [record.as_dict() for record in self.records],
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveCommandLedger":
        if raw in (None, {}):
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid objective command ledger")
        records = raw.get("records")
        if not isinstance(records, list):
            raise ValueError("objective command records must be a list")
        return cls(tuple(ObjectiveCommandRecord.from_mapping(item) for item in records))


@dataclass(frozen=True)
class ObjectiveCommandStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ObjectiveCommandStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / COMMANDS_RELATIVE_PATH,
                backup_path=root / COMMANDS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> ObjectiveCommandLedger:
        return ObjectiveCommandLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ObjectiveCommandLedger) -> ObjectiveCommandLedger:
        self.adapter.save(ledger.as_dict())
        return ledger

    def put(self, record: ObjectiveCommandRecord) -> ObjectiveCommandRecord:
        ledger = self.load().put(record)
        self.save(ledger)
        stored = ledger.get(record.request_id)
        if stored is None:
            raise RuntimeError("objective command disappeared after durable save")
        return stored


def prepare_objective_command(
    *,
    store: ObjectiveCommandStore,
    source: DevelopmentApiObjectiveSource,
    root: Path,
) -> ObjectiveCommandRecord:
    request_digest = canonical_digest(source.as_dict())
    existing = store.load().get(source.request_id)
    if existing is not None:
        if existing.request_digest != request_digest:
            raise ValueError("conflicting payload for existing objective request_id")
        return existing

    compiled = compile_objective(source.objective_text, root=root)
    proposal = ObjectiveProposalRecord(
        source=source,
        delivery_id="",
        compiled=compiled,
    )
    record = ObjectiveCommandRecord(
        request_id=source.request_id,
        request_digest=request_digest,
        proposal=proposal,
        phase=ObjectiveCommandPhase.PREPARED,
    )
    return store.put(record)


def advance_objective_command(
    *,
    command_store: ObjectiveCommandStore,
    proposal_store: ObjectiveProposalStore,
    request_id: str,
) -> ObjectiveCommandRecord:
    record = command_store.load().get(request_id)
    if record is None:
        raise ValueError("objective command does not exist")
    if record.phase is ObjectiveCommandPhase.RECONCILED:
        return record

    if record.phase is ObjectiveCommandPhase.PREPARED:
        captured = proposal_store.capture_record(record.proposal)
        if captured.record.proposal_id != record.proposal.proposal_id:
            raise ValueError("durable objective proposal differs from prepared command")
        record = command_store.put(
            record.with_phase(ObjectiveCommandPhase.PROPOSAL_PERSISTED)
        )

    proposal_matches = [
        item
        for item in proposal_store.load().records
        if item.proposal_id == record.proposal.proposal_id
    ]
    if len(proposal_matches) != 1 or proposal_matches[0] != record.proposal:
        raise ValueError("objective proposal is unavailable or differs from command")
    return command_store.put(record.with_phase(ObjectiveCommandPhase.RECONCILED))


def reconcile_pending_objective_commands(
    *,
    command_store: ObjectiveCommandStore,
    proposal_store: ObjectiveProposalStore,
) -> tuple[ObjectiveCommandRecord, ...]:
    reconciled: list[ObjectiveCommandRecord] = []
    for record in command_store.load().pending:
        reconciled.append(
            advance_objective_command(
                command_store=command_store,
                proposal_store=proposal_store,
                request_id=record.request_id,
            )
        )
    return tuple(reconciled)
