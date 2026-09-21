"""Durable local fallback budget accounting for the Platform-v2 hosted driver.

Provider quota remains authoritative when a fresh provider decision is available. This
ledger preserves accepted local Luna/Terra fallback ceilings across migration/restart.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path
import threading
from typing import Any, Mapping

from .identity import canonical_digest
from .quota import LocalBudgetObservation
from .state_store import JsonStateStoreAdapter


HOSTED_BUDGET_RELATIVE_PATH = Path(".skyforge-platform-v2") / "budget.json"
HOSTED_BUDGET_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "budget.json.bak"
)
_BUDGET_LOCK = threading.RLock()


def utc_day() -> str:
    return datetime.now(timezone.utc).date().isoformat()


def _count(value: Any, label: str) -> int:
    if isinstance(value, bool):
        raise ValueError(f"{label} must be a non-negative integer")
    try:
        result = int(value or 0)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"{label} must be a non-negative integer") from exc
    if result < 0:
        raise ValueError(f"{label} must be a non-negative integer")
    return result


@dataclass(frozen=True)
class HostedBudgetLedger:
    day: str = ""
    classifier_calls: int = 0
    luna_worker_calls: int = 0
    terra_worker_calls: int = 0
    worker_attempts: tuple[tuple[str, str], ...] = ()
    seeded_from_legacy: bool = False
    legacy_seed_digest: str = ""

    def __post_init__(self) -> None:
        if not isinstance(self.day, str):
            raise ValueError("day must be string")
        for name in ("classifier_calls", "luna_worker_calls", "terra_worker_calls"):
            _count(getattr(self, name), name)
        normalized_attempts: dict[str, str] = {}
        for entry in self.worker_attempts:
            if not isinstance(entry, tuple) or len(entry) != 2:
                raise ValueError("worker_attempts entries must be (attempt_id, kind)")
            attempt_id, kind = entry
            attempt_id = str(attempt_id or "").strip()
            kind = str(kind or "").strip()
            if len(attempt_id) != 64 or any(ch not in "0123456789abcdef" for ch in attempt_id):
                raise ValueError("worker attempt_id must be lowercase SHA-256 hex")
            if kind not in {"luna_worker", "terra_worker"}:
                raise ValueError("worker attempt kind is invalid")
            prior = normalized_attempts.get(attempt_id)
            if prior is not None and prior != kind:
                raise ValueError("worker attempt changed budget kind")
            normalized_attempts[attempt_id] = kind
        object.__setattr__(
            self,
            "worker_attempts",
            tuple(sorted(normalized_attempts.items())),
        )
        if not isinstance(self.seeded_from_legacy, bool):
            raise ValueError("seeded_from_legacy must be boolean")
        if not isinstance(self.legacy_seed_digest, str):
            raise ValueError("legacy_seed_digest must be string")

    @property
    def luna_calls(self) -> int:
        return self.classifier_calls + self.luna_worker_calls

    def observation(
        self,
        kind: str,
        *,
        luna_limit: int,
        terra_limit: int,
    ) -> LocalBudgetObservation:
        if kind in {"classifier", "luna_worker"}:
            return LocalBudgetObservation(
                self.luna_calls,
                _count(luna_limit, "luna_limit"),
            )
        if kind == "terra_worker":
            return LocalBudgetObservation(
                self.terra_worker_calls,
                _count(terra_limit, "terra_limit"),
            )
        raise ValueError(f"unknown budget kind: {kind}")

    def record_attempt(
        self,
        kind: str,
        *,
        attempt_id: str | None = None,
    ) -> "HostedBudgetLedger":
        if kind == "classifier":
            return replace(self, classifier_calls=self.classifier_calls + 1)
        if kind not in {"luna_worker", "terra_worker"}:
            raise ValueError(f"unknown budget kind: {kind}")

        attempts = dict(self.worker_attempts)
        if attempt_id is not None:
            key = str(attempt_id or "").strip()
            if len(key) != 64 or any(ch not in "0123456789abcdef" for ch in key):
                raise ValueError("worker attempt_id must be lowercase SHA-256 hex")
            prior = attempts.get(key)
            if prior is not None:
                if prior != kind:
                    raise ValueError("worker attempt changed budget kind")
                return self
            attempts[key] = kind

        if kind == "luna_worker":
            return replace(
                self,
                luna_worker_calls=self.luna_worker_calls + 1,
                worker_attempts=tuple(sorted(attempts.items())),
            )
        return replace(
            self,
            terra_worker_calls=self.terra_worker_calls + 1,
            worker_attempts=tuple(sorted(attempts.items())),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "day": self.day,
            "classifier_calls": self.classifier_calls,
            "luna_worker_calls": self.luna_worker_calls,
            "terra_worker_calls": self.terra_worker_calls,
            "worker_attempts": [
                {"attempt_id": attempt_id, "kind": kind}
                for attempt_id, kind in self.worker_attempts
            ],
            "seeded_from_legacy": self.seeded_from_legacy,
            "legacy_seed_digest": self.legacy_seed_digest,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "HostedBudgetLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid hosted budget ledger")
        attempts_raw = raw.get("worker_attempts") or []
        if not isinstance(attempts_raw, list):
            raise ValueError("worker_attempts must be a list")
        attempts = []
        for value in attempts_raw:
            if not isinstance(value, Mapping):
                raise ValueError("worker_attempt entry must be object")
            attempts.append(
                (str(value.get("attempt_id") or ""), str(value.get("kind") or ""))
            )
        return cls(
            day=str(raw.get("day") or ""),
            classifier_calls=_count(raw.get("classifier_calls"), "classifier_calls"),
            luna_worker_calls=_count(raw.get("luna_worker_calls"), "luna_worker_calls"),
            terra_worker_calls=_count(raw.get("terra_worker_calls"), "terra_worker_calls"),
            worker_attempts=tuple(attempts),
            seeded_from_legacy=bool(raw.get("seeded_from_legacy", False)),
            legacy_seed_digest=str(raw.get("legacy_seed_digest") or ""),
        )

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class HostedBudgetStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "HostedBudgetStore":
        root = Path(root).resolve()
        return cls(
            JsonStateStoreAdapter(
                path=root / HOSTED_BUDGET_RELATIVE_PATH,
                backup_path=root / HOSTED_BUDGET_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> HostedBudgetLedger:
        return HostedBudgetLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: HostedBudgetLedger) -> None:
        self.adapter.save(ledger.as_dict())

    def ensure_day(
        self,
        *,
        day: str,
        legacy_state: Mapping[str, Any] | None = None,
    ) -> HostedBudgetLedger:
        with _BUDGET_LOCK:
            if not isinstance(day, str) or not day.strip():
                raise ValueError("day is required")
            current = self.load()
            if current.day == day:
                return current

            seed = (
                legacy_state
                if not current.day and isinstance(legacy_state, Mapping)
                else None
            )
            if seed is not None and str(seed.get("budget_day") or "") == day:
                values = {
                    "budget_day": day,
                    "classifier_calls_today": _count(
                        seed.get("classifier_calls_today"), "classifier_calls_today"
                    ),
                    "luna_worker_calls_today": _count(
                        seed.get("luna_worker_calls_today"), "luna_worker_calls_today"
                    ),
                    "worker_calls_today": _count(
                        seed.get("worker_calls_today"), "worker_calls_today"
                    ),
                }
                next_ledger = HostedBudgetLedger(
                    day=day,
                    classifier_calls=values["classifier_calls_today"],
                    luna_worker_calls=values["luna_worker_calls_today"],
                    terra_worker_calls=values["worker_calls_today"],
                    seeded_from_legacy=True,
                    legacy_seed_digest=canonical_digest(values),
                )
            else:
                next_ledger = HostedBudgetLedger(day=day)

            self.save(next_ledger)
            return next_ledger

    def record_attempt(
        self,
        kind: str,
        *,
        day: str,
        legacy_state: Mapping[str, Any] | None = None,
        attempt_id: str | None = None,
    ) -> HostedBudgetLedger:
        with _BUDGET_LOCK:
            current = self.ensure_day(day=day, legacy_state=legacy_state)
            updated = current.record_attempt(kind, attempt_id=attempt_id)
            self.save(updated)
            return updated

    def reserve_worker_attempt(
        self,
        *,
        attempt_id: str,
        kind: str,
        day: str,
        daily_limit: int,
        legacy_state: Mapping[str, Any] | None = None,
    ) -> tuple[HostedBudgetLedger, bool]:
        """Atomically reserve one exact worker attempt against the local fallback budget."""
        if kind not in {"luna_worker", "terra_worker"}:
            raise ValueError("worker budget kind is invalid")
        with _BUDGET_LOCK:
            current = self.ensure_day(day=day, legacy_state=legacy_state)
            prior = dict(current.worker_attempts).get(attempt_id)
            if prior is not None:
                if prior != kind:
                    raise ValueError("worker attempt changed budget kind")
                return current, True
            observation = current.observation(
                kind,
                luna_limit=daily_limit if kind == "luna_worker" else 1,
                terra_limit=daily_limit if kind == "terra_worker" else 1,
            )
            if observation.calls_used >= observation.daily_limit:
                return current, False
            updated = current.record_attempt(kind, attempt_id=attempt_id)
            self.save(updated)
            return updated, True
