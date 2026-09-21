"""Durable conflict-aware mutation-scope claims for bounded Platform-v2 concurrency."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any, Mapping

from .identity import canonical_digest
from .path_scope import normalize_mutation_scopes, overlapping_scope_pairs
from .state_store import JsonStateStoreAdapter
from .worker_provider import FrozenWorkerSpec

CONCURRENCY_CLAIMS_RELATIVE_PATH = Path(".skyforge-platform-v2") / "concurrency-claims.json"
CONCURRENCY_CLAIMS_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2") / "concurrency-claims.json.bak"


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _sha(value: Any, length: int, label: str) -> str:
    text = _required(value, label).lower()
    if len(text) != length or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase {length}-character hex")
    return text


@dataclass(frozen=True)
class ConcurrencyClaimRecord:
    task_id: str
    authority_key: str
    task_spec_hash: str
    attempt_id: str
    base_sha: str
    lane: str
    allowed_paths: tuple[str, ...]

    def __post_init__(self) -> None:
        object.__setattr__(self, "task_id", _required(self.task_id, "task_id"))
        object.__setattr__(self, "authority_key", _required(self.authority_key, "authority_key"))
        object.__setattr__(self, "task_spec_hash", _sha(self.task_spec_hash, 64, "task_spec_hash"))
        object.__setattr__(self, "attempt_id", _sha(self.attempt_id, 64, "attempt_id"))
        object.__setattr__(self, "base_sha", _sha(self.base_sha, 40, "base_sha"))
        object.__setattr__(self, "lane", _required(self.lane, "lane"))
        object.__setattr__(
            self,
            "allowed_paths",
            normalize_mutation_scopes(self.allowed_paths, label="allowed_paths"),
        )

    @classmethod
    def from_worker(cls, worker: FrozenWorkerSpec) -> "ConcurrencyClaimRecord":
        if not isinstance(worker, FrozenWorkerSpec):
            raise ValueError("worker must be FrozenWorkerSpec")
        return cls(
            task_id=worker.task_id,
            authority_key=worker.authority_key,
            task_spec_hash=worker.task_spec_hash,
            attempt_id=worker.attempt_id,
            base_sha=worker.base_sha,
            lane=worker.lane,
            allowed_paths=worker.allowed_paths,
        )

    def identity_payload(self) -> dict[str, Any]:
        return {
            "task_id": self.task_id,
            "authority_key": self.authority_key,
            "task_spec_hash": self.task_spec_hash,
            "attempt_id": self.attempt_id,
            "base_sha": self.base_sha,
            "lane": self.lane,
            "allowed_paths": list(self.allowed_paths),
        }

    @property
    def claim_id(self) -> str:
        return canonical_digest(self.identity_payload())

    def as_dict(self) -> dict[str, Any]:
        return {**self.identity_payload(), "claim_id": self.claim_id}

    @classmethod
    def from_mapping(cls, raw: Any) -> "ConcurrencyClaimRecord":
        if not isinstance(raw, Mapping):
            raise ValueError("concurrency claim must be an object")
        paths = raw.get("allowed_paths")
        if not isinstance(paths, list):
            raise ValueError("concurrency claim allowed_paths must be a list")
        record = cls(
            task_id=raw.get("task_id"),
            authority_key=raw.get("authority_key"),
            task_spec_hash=raw.get("task_spec_hash"),
            attempt_id=raw.get("attempt_id"),
            base_sha=raw.get("base_sha"),
            lane=raw.get("lane"),
            allowed_paths=tuple(paths),
        )
        if _sha(raw.get("claim_id"), 64, "claim_id") != record.claim_id:
            raise ValueError("concurrency claim identity mismatch")
        return record


@dataclass(frozen=True)
class RetiredConcurrencyClaim:
    attempt_id: str
    claim_id: str
    task_id: str
    authority_key: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "attempt_id", _sha(self.attempt_id, 64, "attempt_id"))
        object.__setattr__(self, "claim_id", _sha(self.claim_id, 64, "claim_id"))
        object.__setattr__(self, "task_id", _required(self.task_id, "task_id"))
        object.__setattr__(self, "authority_key", _required(self.authority_key, "authority_key"))

    @classmethod
    def from_claim(cls, claim: ConcurrencyClaimRecord) -> "RetiredConcurrencyClaim":
        return cls(claim.attempt_id, claim.claim_id, claim.task_id, claim.authority_key)

    @property
    def retirement_id(self) -> str:
        return canonical_digest(
            {
                "attempt_id": self.attempt_id,
                "claim_id": self.claim_id,
                "task_id": self.task_id,
                "authority_key": self.authority_key,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "attempt_id": self.attempt_id,
            "claim_id": self.claim_id,
            "task_id": self.task_id,
            "authority_key": self.authority_key,
            "retirement_id": self.retirement_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "RetiredConcurrencyClaim":
        if not isinstance(raw, Mapping):
            raise ValueError("retired concurrency claim must be an object")
        value = cls(
            attempt_id=raw.get("attempt_id"),
            claim_id=raw.get("claim_id"),
            task_id=raw.get("task_id"),
            authority_key=raw.get("authority_key"),
        )
        if _sha(raw.get("retirement_id"), 64, "retirement_id") != value.retirement_id:
            raise ValueError("concurrency retirement identity mismatch")
        return value


@dataclass(frozen=True)
class ConcurrencyClaimLedger:
    active: tuple[ConcurrencyClaimRecord, ...] = ()
    retired: tuple[RetiredConcurrencyClaim, ...] = ()

    def __post_init__(self) -> None:
        active = tuple(sorted(self.active, key=lambda value: value.claim_id))
        retired = tuple(sorted(self.retired, key=lambda value: value.attempt_id))
        active_attempts = [value.attempt_id for value in active]
        retired_attempts = [value.attempt_id for value in retired]
        if len(active_attempts) != len(set(active_attempts)):
            raise ValueError("duplicate active concurrency attempt")
        if len(retired_attempts) != len(set(retired_attempts)):
            raise ValueError("duplicate retired concurrency attempt")
        if set(active_attempts) & set(retired_attempts):
            raise ValueError("concurrency attempt cannot be active and retired")
        active_tasks = [value.task_id for value in active]
        active_authorities = [value.authority_key for value in active]
        if len(active_tasks) != len(set(active_tasks)):
            raise ValueError("multiple active concurrency claims share one task")
        if len(active_authorities) != len(set(active_authorities)):
            raise ValueError("multiple active concurrency claims share one authority")
        for index, left in enumerate(active):
            for right in active[index + 1:]:
                if overlapping_scope_pairs(left.allowed_paths, right.allowed_paths):
                    raise ValueError("active concurrency claims have overlapping mutation scopes")
        object.__setattr__(self, "active", active)
        object.__setattr__(self, "retired", retired)

    def active_for_attempt(self, attempt_id: str) -> ConcurrencyClaimRecord | None:
        key = _sha(attempt_id, 64, "attempt_id")
        return next((value for value in self.active if value.attempt_id == key), None)

    def retired_for_attempt(self, attempt_id: str) -> RetiredConcurrencyClaim | None:
        key = _sha(attempt_id, 64, "attempt_id")
        return next((value for value in self.retired if value.attempt_id == key), None)

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "active": [value.as_dict() for value in self.active],
            "retired": [value.as_dict() for value in self.retired],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    @classmethod
    def from_mapping(cls, raw: Any) -> "ConcurrencyClaimLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid concurrency claim ledger")
        active = raw.get("active")
        retired = raw.get("retired")
        if not isinstance(active, list) or not isinstance(retired, list):
            raise ValueError("concurrency claim active/retired values must be lists")
        return cls(
            active=tuple(ConcurrencyClaimRecord.from_mapping(value) for value in active),
            retired=tuple(RetiredConcurrencyClaim.from_mapping(value) for value in retired),
        )


@dataclass(frozen=True)
class ConcurrencyClaimStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ConcurrencyClaimStore":
        root = Path(root)
        return cls(
            JsonStateStoreAdapter(
                path=root / CONCURRENCY_CLAIMS_RELATIVE_PATH,
                backup_path=root / CONCURRENCY_CLAIMS_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> ConcurrencyClaimLedger:
        return ConcurrencyClaimLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ConcurrencyClaimLedger) -> ConcurrencyClaimLedger:
        self.adapter.save(ledger.as_dict())
        return ledger

    def acquire(self, worker: FrozenWorkerSpec) -> "ConcurrencyAcquireResult":
        current = self.load()
        result = acquire_concurrency_claim(current, worker)
        if result.ledger != current:
            self.save(result.ledger)
        return result

    def retire(self, attempt_id: str) -> "ConcurrencyRetireResult":
        current = self.load()
        result = retire_concurrency_claim(current, attempt_id)
        if result.ledger != current:
            self.save(result.ledger)
        return result


@dataclass(frozen=True)
class ConcurrencyConflict:
    claim_id: str
    attempt_id: str
    task_id: str
    authority_key: str
    reason: str
    overlapping_scopes: tuple[tuple[str, str], ...] = ()

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def as_dict(self) -> dict[str, Any]:
        return {
            "claim_id": self.claim_id,
            "attempt_id": self.attempt_id,
            "task_id": self.task_id,
            "authority_key": self.authority_key,
            "reason": self.reason,
            "overlapping_scopes": [list(pair) for pair in self.overlapping_scopes],
        }


class ConcurrencyClaimDisposition(str, Enum):
    ADMIT = "ADMIT"
    ALREADY_ACTIVE = "ALREADY_ACTIVE"
    RETIRED_REPLAY = "RETIRED_REPLAY"
    CONFLICT = "CONFLICT"
    BLOCKED_UNSUPPORTED_SCOPE = "BLOCKED_UNSUPPORTED_SCOPE"


@dataclass(frozen=True)
class ConcurrencyClaimDecision:
    disposition: ConcurrencyClaimDisposition
    reason: str
    ledger_digest: str
    proposed_claim: ConcurrencyClaimRecord | None = None
    conflicts: tuple[ConcurrencyConflict, ...] = ()

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "ledger_digest": self.ledger_digest,
                "proposed_claim_id": self.proposed_claim.claim_id if self.proposed_claim else "",
                "conflict_digests": [value.digest for value in self.conflicts],
            }
        )


def _conflict(
    existing: ConcurrencyClaimRecord,
    *,
    reason: str,
    overlaps: tuple[tuple[str, str], ...] = (),
) -> ConcurrencyConflict:
    return ConcurrencyConflict(
        claim_id=existing.claim_id,
        attempt_id=existing.attempt_id,
        task_id=existing.task_id,
        authority_key=existing.authority_key,
        reason=reason,
        overlapping_scopes=overlaps,
    )


def classify_concurrency_claim(
    ledger: ConcurrencyClaimLedger,
    worker: FrozenWorkerSpec,
) -> ConcurrencyClaimDecision:
    if not isinstance(ledger, ConcurrencyClaimLedger):
        raise ValueError("ledger must be ConcurrencyClaimLedger")
    try:
        proposed = ConcurrencyClaimRecord.from_worker(worker)
    except ValueError as exc:
        return ConcurrencyClaimDecision(
            ConcurrencyClaimDisposition.BLOCKED_UNSUPPORTED_SCOPE,
            f"worker mutation scope is not concurrency-safe: {exc}",
            ledger.digest,
        )

    same_attempt = ledger.active_for_attempt(proposed.attempt_id)
    if same_attempt is not None:
        if same_attempt == proposed:
            return ConcurrencyClaimDecision(
                ConcurrencyClaimDisposition.ALREADY_ACTIVE,
                "exact concurrency claim is already active",
                ledger.digest,
                proposed,
            )
        return ConcurrencyClaimDecision(
            ConcurrencyClaimDisposition.CONFLICT,
            "attempt identity already owns a different active concurrency claim",
            ledger.digest,
            proposed,
            (_conflict(same_attempt, reason="immutable attempt identity conflict"),),
        )

    retired = ledger.retired_for_attempt(proposed.attempt_id)
    if retired is not None:
        if retired.claim_id == proposed.claim_id:
            return ConcurrencyClaimDecision(
                ConcurrencyClaimDisposition.RETIRED_REPLAY,
                "stale acquire replay references an already-retired concurrency claim",
                ledger.digest,
                proposed,
            )
        return ConcurrencyClaimDecision(
            ConcurrencyClaimDisposition.CONFLICT,
            "retired attempt identity cannot be rebound to different concurrency authority",
            ledger.digest,
            proposed,
        )

    conflicts: list[ConcurrencyConflict] = []
    for existing in ledger.active:
        identity_reason = ""
        if existing.task_id == proposed.task_id:
            identity_reason = "task already has another active attempt"
        elif existing.authority_key == proposed.authority_key:
            identity_reason = "authority already has another active attempt"
        overlaps = overlapping_scope_pairs(existing.allowed_paths, proposed.allowed_paths)
        if identity_reason or overlaps:
            reason = identity_reason or "allowed mutation scopes overlap"
            conflicts.append(_conflict(existing, reason=reason, overlaps=overlaps))

    if conflicts:
        ordered = tuple(sorted(conflicts, key=lambda value: value.claim_id))
        return ConcurrencyClaimDecision(
            ConcurrencyClaimDisposition.CONFLICT,
            "proposed worker conflicts with active concurrency authority",
            ledger.digest,
            proposed,
            ordered,
        )

    return ConcurrencyClaimDecision(
        ConcurrencyClaimDisposition.ADMIT,
        "proposed worker mutation scope is independent of active concurrency claims",
        ledger.digest,
        proposed,
    )


@dataclass(frozen=True)
class ConcurrencyAcquireResult:
    decision: ConcurrencyClaimDecision
    ledger: ConcurrencyClaimLedger

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "decision_digest": self.decision.digest,
                "ledger_digest": self.ledger.digest,
            }
        )


def acquire_concurrency_claim(
    ledger: ConcurrencyClaimLedger,
    worker: FrozenWorkerSpec,
) -> ConcurrencyAcquireResult:
    decision = classify_concurrency_claim(ledger, worker)
    if decision.disposition is not ConcurrencyClaimDisposition.ADMIT:
        return ConcurrencyAcquireResult(decision, ledger)
    assert decision.proposed_claim is not None
    updated = ConcurrencyClaimLedger(
        active=ledger.active + (decision.proposed_claim,),
        retired=ledger.retired,
    )
    return ConcurrencyAcquireResult(decision, updated)


class ConcurrencyRetireDisposition(str, Enum):
    RETIRED = "RETIRED"
    ALREADY_RETIRED = "ALREADY_RETIRED"
    NOT_ACTIVE = "NOT_ACTIVE"


@dataclass(frozen=True)
class ConcurrencyRetireResult:
    disposition: ConcurrencyRetireDisposition
    reason: str
    attempt_id: str
    ledger: ConcurrencyClaimLedger

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "disposition": self.disposition.value,
                "reason": self.reason,
                "attempt_id": self.attempt_id,
                "ledger_digest": self.ledger.digest,
            }
        )


def retire_concurrency_claim(
    ledger: ConcurrencyClaimLedger,
    attempt_id: str,
) -> ConcurrencyRetireResult:
    attempt = _sha(attempt_id, 64, "attempt_id")
    active = ledger.active_for_attempt(attempt)
    if active is not None:
        tombstone = RetiredConcurrencyClaim.from_claim(active)
        updated = ConcurrencyClaimLedger(
            active=tuple(value for value in ledger.active if value.attempt_id != attempt),
            retired=ledger.retired + (tombstone,),
        )
        return ConcurrencyRetireResult(
            ConcurrencyRetireDisposition.RETIRED,
            "exact active concurrency claim retired with durable tombstone",
            attempt,
            updated,
        )
    if ledger.retired_for_attempt(attempt) is not None:
        return ConcurrencyRetireResult(
            ConcurrencyRetireDisposition.ALREADY_RETIRED,
            "concurrency claim retirement is an idempotent replay",
            attempt,
            ledger,
        )
    return ConcurrencyRetireResult(
        ConcurrencyRetireDisposition.NOT_ACTIVE,
        "no active or retired concurrency claim exists for attempt",
        attempt,
        ledger,
    )
