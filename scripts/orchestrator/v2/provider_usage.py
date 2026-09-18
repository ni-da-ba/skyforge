"""Durable daily provider-spend accounting for Platform v2 R5C24."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Any, Mapping

from .identity import canonical_digest
from .quota import LocalBudgetObservation
from .state_store import JsonStateStoreAdapter


PROVIDER_USAGE_RELATIVE_PATH = Path(".skyforge-platform-v2") / "provider-usage.json"
PROVIDER_USAGE_BACKUP_RELATIVE_PATH = Path(".skyforge-platform-v2") / "provider-usage.json.bak"


class ProviderSpendKind(str, Enum):
    CLASSIFIER = "CLASSIFIER"
    WORKER = "WORKER"


@dataclass(frozen=True)
class ProviderSpendReservation:
    day: str
    kind: ProviderSpendKind
    spend_id: str

    def __post_init__(self) -> None:
        if not isinstance(self.day, str) or len(self.day) != 10:
            raise ValueError("day must be YYYY-MM-DD")
        if not isinstance(self.kind, ProviderSpendKind):
            raise ValueError("kind must be ProviderSpendKind")
        if not isinstance(self.spend_id, str) or not self.spend_id.strip():
            raise ValueError("spend_id is required")

    @property
    def reservation_id(self) -> str:
        return canonical_digest({
            "day": self.day,
            "kind": self.kind.value,
            "spend_id": self.spend_id,
        })

    def as_dict(self) -> dict[str, str]:
        return {
            "day": self.day,
            "kind": self.kind.value,
            "spend_id": self.spend_id,
            "reservation_id": self.reservation_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ProviderSpendReservation":
        if not isinstance(raw, Mapping):
            raise ValueError("provider spend reservation must be object")
        value = cls(
            day=str(raw.get("day") or ""),
            kind=ProviderSpendKind(str(raw.get("kind") or "")),
            spend_id=str(raw.get("spend_id") or ""),
        )
        if str(raw.get("reservation_id") or "") != value.reservation_id:
            raise ValueError("provider spend reservation identity mismatch")
        return value


@dataclass(frozen=True)
class ProviderUsageLedger:
    reservations: tuple[ProviderSpendReservation, ...] = ()

    def __post_init__(self) -> None:
        ids=[r.reservation_id for r in self.reservations]
        if len(ids) != len(set(ids)):
            raise ValueError("duplicate provider spend reservation")
        semantic=[(r.day,r.kind.value,r.spend_id) for r in self.reservations]
        if len(semantic) != len(set(semantic)):
            raise ValueError("duplicate semantic provider spend")

    @classmethod
    def from_mapping(cls, raw: Any) -> "ProviderUsageLedger":
        if raw is None or raw == {}:
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid provider usage ledger")
        values=raw.get("reservations")
        if not isinstance(values,list):
            raise ValueError("provider usage reservations must be list")
        return cls(tuple(ProviderSpendReservation.from_mapping(v) for v in values))

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "reservations": [r.as_dict() for r in self.reservations],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())

    def count(self, *, day: str, kind: ProviderSpendKind) -> int:
        return sum(1 for r in self.reservations if r.day == day and r.kind is kind)

    def budget(self, *, day: str, kind: ProviderSpendKind, daily_limit: int) -> LocalBudgetObservation:
        if isinstance(daily_limit,bool) or not isinstance(daily_limit,int) or daily_limit < 1:
            raise ValueError("daily_limit must be positive")
        return LocalBudgetObservation(
            calls_used=self.count(day=day,kind=kind),
            daily_limit=daily_limit,
        )

    def budget_for_spend(
        self,
        *,
        day: str,
        kind: ProviderSpendKind,
        spend_id: str,
        daily_limit: int,
    ) -> LocalBudgetObservation:
        """Budget observation that does not charge the same durable reservation twice."""
        if isinstance(daily_limit,bool) or not isinstance(daily_limit,int) or daily_limit < 1:
            raise ValueError("daily_limit must be positive")
        used=sum(
            1 for r in self.reservations
            if r.day == day and r.kind is kind and r.spend_id != spend_id
        )
        return LocalBudgetObservation(calls_used=used,daily_limit=daily_limit)

    def reserve(
        self,
        *,
        day: str,
        kind: ProviderSpendKind,
        spend_id: str,
        daily_limit: int,
    ) -> tuple["ProviderUsageLedger", bool]:
        if isinstance(daily_limit,bool) or not isinstance(daily_limit,int) or daily_limit < 1:
            raise ValueError("daily_limit must be positive")
        candidate=ProviderSpendReservation(day=day,kind=kind,spend_id=spend_id)
        for existing in self.reservations:
            if existing.reservation_id == candidate.reservation_id:
                return self, False
        if self.count(day=day,kind=kind) >= daily_limit:
            return self, False
        return ProviderUsageLedger(self.reservations + (candidate,)), True


@dataclass(frozen=True)
class ProviderUsageStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ProviderUsageStore":
        root=Path(root)
        return cls(JsonStateStoreAdapter(
            path=root / PROVIDER_USAGE_RELATIVE_PATH,
            backup_path=root / PROVIDER_USAGE_BACKUP_RELATIVE_PATH,
        ))

    def load(self) -> ProviderUsageLedger:
        return ProviderUsageLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ProviderUsageLedger) -> ProviderUsageLedger:
        self.adapter.save(ledger.as_dict())
        return ledger

    def reserve(
        self,
        *,
        day: str,
        kind: ProviderSpendKind,
        spend_id: str,
        daily_limit: int,
    ) -> tuple[ProviderUsageLedger, bool]:
        current=self.load()
        updated,created=current.reserve(
            day=day,kind=kind,spend_id=spend_id,daily_limit=daily_limit
        )
        if updated != current:
            self.save(updated)
        return updated,created
