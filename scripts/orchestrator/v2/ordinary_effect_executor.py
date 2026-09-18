"""Exactly-once logical execution for bounded ordinary Platform-v2 effects."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Protocol

from .effects import (
    EffectReconcileDisposition,
    RemoteEffectIdentity,
    RemoteEffectObservation,
    RemoteEffectRecord,
    reconcile_remote_effect,
)
from .identity import canonical_digest
from .ordinary_effects import OrdinaryEffectLedger, OrdinaryEffectStore


class OrdinaryRemoteUnavailable(RuntimeError):
    """Exact remote truth or mutation outcome could not be established."""


class OrdinaryEffectExecutionDisposition(str, Enum):
    EXECUTED = "EXECUTED"
    RECONCILED = "RECONCILED"
    ALREADY_COMPLETE = "ALREADY_COMPLETE"
    BLOCKED = "BLOCKED"


class OrdinaryEffectAdapter(Protocol):
    def observe(self, identity: RemoteEffectIdentity) -> RemoteEffectObservation: ...
    def execute(self, identity: RemoteEffectIdentity) -> str: ...


@dataclass(frozen=True)
class OrdinaryEffectExecutionResult:
    disposition: OrdinaryEffectExecutionDisposition
    reason: str
    record: RemoteEffectRecord
    ledger_digest: str

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "record_digest": self.record.digest,
                "ledger_digest": self.ledger_digest,
            }
        )


def advance_remote_effect(
    *,
    store: OrdinaryEffectStore,
    identity: RemoteEffectIdentity,
    adapter: OrdinaryEffectAdapter,
    crash_after_execute: bool = False,
) -> OrdinaryEffectExecutionResult:
    """Advance one durable effect, never executing without a preceding PENDING save."""

    ledger = store.load()
    record = ledger.get(identity)
    if record is None:
        ledger = ledger.begin(identity)
        store.save(ledger)
        record = ledger.get(identity)
        assert record is not None

    try:
        observation = adapter.observe(identity)
    except OrdinaryRemoteUnavailable:
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.BLOCKED,
            "remote effect truth is unavailable; durable state preserved",
            record,
            ledger.digest,
        )

    decision = reconcile_remote_effect(record, observation)
    if decision.disposition is EffectReconcileDisposition.BLOCK:
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.BLOCKED,
            decision.reason,
            record,
            ledger.digest,
        )

    if decision.disposition is EffectReconcileDisposition.NOOP_COMPLETE:
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.ALREADY_COMPLETE,
            "effect is already durably complete",
            record,
            ledger.digest,
        )

    if decision.disposition is EffectReconcileDisposition.MARK_COMPLETE:
        ledger = ledger.complete(identity, decision.remote_identity)
        store.save(ledger)
        completed = ledger.get(identity)
        assert completed is not None
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.RECONCILED,
            "exact remote effect reconciled without re-execution",
            completed,
            ledger.digest,
        )

    # EXECUTE. Re-observe immediately before mutation to close the stale-read window.
    try:
        latest = adapter.observe(identity)
    except OrdinaryRemoteUnavailable:
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.BLOCKED,
            "pre-execution remote truth is unavailable",
            record,
            ledger.digest,
        )
    latest_decision = reconcile_remote_effect(record, latest)
    if latest_decision.disposition is EffectReconcileDisposition.MARK_COMPLETE:
        ledger = ledger.complete(identity, latest_decision.remote_identity)
        store.save(ledger)
        completed = ledger.get(identity)
        assert completed is not None
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.RECONCILED,
            "effect appeared before execution and was reconciled",
            completed,
            ledger.digest,
        )
    if latest_decision.disposition is not EffectReconcileDisposition.EXECUTE:
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.BLOCKED,
            latest_decision.reason,
            record,
            ledger.digest,
        )

    try:
        remote_identity = adapter.execute(identity)
    except OrdinaryRemoteUnavailable:
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.BLOCKED,
            "remote mutation outcome is unknown; PENDING effect preserved",
            record,
            ledger.digest,
        )

    if crash_after_execute:
        raise RuntimeError("injected crash after ordinary remote effect execution")

    try:
        confirmed = adapter.observe(identity)
    except OrdinaryRemoteUnavailable:
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.BLOCKED,
            "mutation returned but confirmation is unavailable; PENDING effect preserved",
            record,
            ledger.digest,
        )
    confirmed_decision = reconcile_remote_effect(record, confirmed)
    if (
        confirmed_decision.disposition
        is not EffectReconcileDisposition.MARK_COMPLETE
    ):
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.BLOCKED,
            "mutation returned but exact remote identity is not provable",
            record,
            ledger.digest,
        )

    expected_remote = confirmed_decision.remote_identity
    if remote_identity and remote_identity != expected_remote:
        return OrdinaryEffectExecutionResult(
            OrdinaryEffectExecutionDisposition.BLOCKED,
            "mutation return identity conflicts with observed exact remote identity",
            record,
            ledger.digest,
        )

    ledger = ledger.complete(identity, expected_remote)
    store.save(ledger)
    completed = ledger.get(identity)
    assert completed is not None
    return OrdinaryEffectExecutionResult(
        OrdinaryEffectExecutionDisposition.EXECUTED,
        "remote effect executed exactly once and durably completed",
        completed,
        ledger.digest,
    )
