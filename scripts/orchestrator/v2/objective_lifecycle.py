"""Durable exact-objective lifecycle controls for the pre-Bootstrap console.

These controls fence future automatic progression only. They never kill a running
provider process, delete remote authority, discard worktrees, or repeat effects.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from pathlib import Path
import re
import threading
from typing import Any, Mapping

from .identity import canonical_digest
from .objective_ingress import (
    ObjectiveProposalStore,
    ProgramObjectiveSource,
    ScopedObjectiveSource,
)
from .state_store import JsonStateStoreAdapter

OBJECTIVE_LIFECYCLE_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "objective-lifecycle.json"
)
OBJECTIVE_LIFECYCLE_BACKUP_RELATIVE_PATH = (
    Path(".skyforge-platform-v2") / "objective-lifecycle.json.bak"
)
_LIFECYCLE_LOCK = threading.RLock()
_REQUEST_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")


def _required(value: Any, label: str) -> str:
    text = str(value or "").strip()
    if not text:
        raise ValueError(f"{label} is required")
    return text


def _sha64(value: Any, label: str) -> str:
    text = _required(value, label).lower()
    if len(text) != 64 or any(ch not in "0123456789abcdef" for ch in text):
        raise ValueError(f"{label} must be lowercase SHA-256 hex")
    return text


def _request_id(value: Any) -> str:
    text = _required(value, "request_id")
    if not _REQUEST_RE.fullmatch(text):
        raise ValueError("request_id is malformed")
    return text


class ObjectiveLifecycleOperation(str, Enum):
    PAUSE = "PAUSE"
    RESUME = "RESUME"
    CANCEL = "CANCEL"
    RECONCILE = "RECONCILE"


class ObjectiveLifecycleState(str, Enum):
    ACTIVE = "ACTIVE"
    PAUSED = "PAUSED"
    CANCELLED = "CANCELLED"


@dataclass(frozen=True)
class ObjectiveLifecycleCommand:
    request_id: str
    proposal_id: str
    operation: ObjectiveLifecycleOperation
    reason: str
    actor: str
    client: str

    def __post_init__(self) -> None:
        object.__setattr__(self, "request_id", _request_id(self.request_id))
        object.__setattr__(self, "proposal_id", _sha64(self.proposal_id, "proposal_id"))
        if not isinstance(self.operation, ObjectiveLifecycleOperation):
            object.__setattr__(
                self,
                "operation",
                ObjectiveLifecycleOperation(str(self.operation)),
            )
        object.__setattr__(self, "reason", _required(self.reason, "reason"))
        object.__setattr__(self, "actor", _required(self.actor, "actor"))
        object.__setattr__(self, "client", _required(self.client, "client"))

    @property
    def command_id(self) -> str:
        return canonical_digest(
            {
                "request_id": self.request_id,
                "proposal_id": self.proposal_id,
                "operation": self.operation.value,
                "reason": self.reason,
                "actor": self.actor,
                "client": self.client,
            }
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "request_id": self.request_id,
            "proposal_id": self.proposal_id,
            "operation": self.operation.value,
            "reason": self.reason,
            "actor": self.actor,
            "client": self.client,
            "command_id": self.command_id,
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveLifecycleCommand":
        if not isinstance(raw, Mapping):
            raise ValueError("objective lifecycle command must be object")
        value = cls(
            request_id=raw.get("request_id"),
            proposal_id=raw.get("proposal_id"),
            operation=ObjectiveLifecycleOperation(str(raw.get("operation") or "")),
            reason=raw.get("reason"),
            actor=raw.get("actor"),
            client=raw.get("client"),
        )
        if str(raw.get("command_id") or "") != value.command_id:
            raise ValueError("objective lifecycle command identity mismatch")
        return value


@dataclass(frozen=True)
class ObjectiveLifecycleStatus:
    proposal_id: str
    state: ObjectiveLifecycleState
    last_command: ObjectiveLifecycleCommand | None = None

    @property
    def blocks_automatic_progression(self) -> bool:
        return self.state in {
            ObjectiveLifecycleState.PAUSED,
            ObjectiveLifecycleState.CANCELLED,
        }

    def as_dict(self) -> dict[str, Any]:
        return {
            "proposal_id": self.proposal_id,
            "state": self.state.value,
            "blocks_automatic_progression": self.blocks_automatic_progression,
            "last_operation": (
                self.last_command.operation.value if self.last_command else ""
            ),
            "last_request_id": (
                self.last_command.request_id if self.last_command else ""
            ),
            "reason": self.last_command.reason if self.last_command else "",
        }


@dataclass(frozen=True)
class ObjectiveLifecycleLedger:
    commands: tuple[ObjectiveLifecycleCommand, ...] = ()

    def __post_init__(self) -> None:
        by_request: dict[str, ObjectiveLifecycleCommand] = {}
        for command in self.commands:
            if not isinstance(command, ObjectiveLifecycleCommand):
                raise ValueError("invalid objective lifecycle command")
            prior = by_request.get(command.request_id)
            if prior is not None and prior != command:
                raise ValueError("conflicting objective lifecycle request_id")
            by_request[command.request_id] = command
        object.__setattr__(self, "commands", tuple(by_request.values()))

    def get_request(self, request_id: str) -> ObjectiveLifecycleCommand | None:
        key = _request_id(request_id)
        matches = [value for value in self.commands if value.request_id == key]
        if len(matches) > 1:
            raise ValueError("duplicate objective lifecycle request")
        return matches[0] if matches else None

    def status(self, proposal_id: str) -> ObjectiveLifecycleStatus:
        key = _sha64(proposal_id, "proposal_id")
        current = ObjectiveLifecycleState.ACTIVE
        last: ObjectiveLifecycleCommand | None = None
        for command in self.commands:
            if command.proposal_id != key:
                continue
            if command.operation is ObjectiveLifecycleOperation.PAUSE:
                if current is not ObjectiveLifecycleState.CANCELLED:
                    current = ObjectiveLifecycleState.PAUSED
            elif command.operation is ObjectiveLifecycleOperation.RESUME:
                if current is not ObjectiveLifecycleState.CANCELLED:
                    current = ObjectiveLifecycleState.ACTIVE
            elif command.operation is ObjectiveLifecycleOperation.CANCEL:
                current = ObjectiveLifecycleState.CANCELLED
            # RECONCILE is intentionally state-neutral.
            last = command
        return ObjectiveLifecycleStatus(key, current, last)

    def statuses(self) -> tuple[ObjectiveLifecycleStatus, ...]:
        proposal_ids = sorted({value.proposal_id for value in self.commands})
        return tuple(self.status(value) for value in proposal_ids)

    def append(self, command: ObjectiveLifecycleCommand) -> "ObjectiveLifecycleLedger":
        existing = self.get_request(command.request_id)
        if existing is not None:
            if existing != command:
                raise ValueError("objective lifecycle request replay changed payload")
            return self
        current = self.status(command.proposal_id)
        if (
            current.state is ObjectiveLifecycleState.CANCELLED
            and command.operation
            in {
                ObjectiveLifecycleOperation.PAUSE,
                ObjectiveLifecycleOperation.RESUME,
            }
        ):
            raise ValueError("cancelled objective cannot be paused or resumed")
        return ObjectiveLifecycleLedger(self.commands + (command,))

    def as_dict(self) -> dict[str, Any]:
        return {
            "schema_version": 1,
            "commands": [value.as_dict() for value in self.commands],
        }

    @classmethod
    def from_mapping(cls, raw: Any) -> "ObjectiveLifecycleLedger":
        if raw in (None, {}):
            return cls()
        if not isinstance(raw, Mapping) or raw.get("schema_version") != 1:
            raise ValueError("invalid objective lifecycle ledger")
        values = raw.get("commands")
        if not isinstance(values, list):
            raise ValueError("objective lifecycle commands must be list")
        return cls(tuple(ObjectiveLifecycleCommand.from_mapping(value) for value in values))

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class ObjectiveLifecycleStore:
    adapter: JsonStateStoreAdapter

    @classmethod
    def for_root(cls, root: Path) -> "ObjectiveLifecycleStore":
        root = Path(root).resolve()
        return cls(
            JsonStateStoreAdapter(
                path=root / OBJECTIVE_LIFECYCLE_RELATIVE_PATH,
                backup_path=root / OBJECTIVE_LIFECYCLE_BACKUP_RELATIVE_PATH,
            )
        )

    def load(self) -> ObjectiveLifecycleLedger:
        return ObjectiveLifecycleLedger.from_mapping(self.adapter.load().as_dict())

    def save(self, ledger: ObjectiveLifecycleLedger) -> ObjectiveLifecycleLedger:
        self.adapter.save(ledger.as_dict())
        return ledger

    def apply(
        self,
        *,
        root: Path,
        request_id: str,
        proposal_id: str,
        operation: ObjectiveLifecycleOperation,
        reason: str,
        actor: str,
        client: str,
    ) -> tuple[ObjectiveLifecycleCommand, ObjectiveLifecycleStatus, bool]:
        root = Path(root).resolve()
        proposal_id = _sha64(proposal_id, "proposal_id")
        proposals = [
            value
            for value in ObjectiveProposalStore.for_root(root).load().records
            if value.proposal_id == proposal_id
        ]
        if len(proposals) != 1:
            raise ValueError("objective proposal is unavailable or ambiguous")

        command = ObjectiveLifecycleCommand(
            request_id=request_id,
            proposal_id=proposal_id,
            operation=operation,
            reason=reason,
            actor=actor,
            client=client,
        )
        with _LIFECYCLE_LOCK:
            ledger = self.load()
            existing = ledger.get_request(command.request_id)
            if existing is not None:
                if existing != command:
                    raise ValueError("objective lifecycle request replay changed payload")
                return existing, ledger.status(proposal_id), False
            updated = ledger.append(command)
            self.save(updated)
            return command, updated.status(proposal_id), True


def objective_progression_allowed(root: Path, proposal_id: str) -> tuple[bool, ObjectiveLifecycleStatus]:
    status = ObjectiveLifecycleStore.for_root(root).load().status(proposal_id)
    return not status.blocks_automatic_progression, status


@dataclass(frozen=True)
class ObjectiveProgressionDecision:
    proposal_id: str
    allowed: bool
    state: ObjectiveLifecycleState
    controlled_by_proposal_id: str
    reason: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "proposal_id": self.proposal_id,
            "allowed": self.allowed,
            "state": self.state.value,
            "controlled_by_proposal_id": self.controlled_by_proposal_id,
            "reason": self.reason,
        }


def effective_objective_progression(
    root: Path,
    proposal_id: str,
) -> ObjectiveProgressionDecision:
    """Return exact lifecycle authority across persisted objective ancestry.

    PAUSED/CANCELLED controls fence the exact objective and every not-yet-executing
    durable descendant, including PROGRAM and standalone scoped children. Missing,
    duplicate, or cyclic ancestry fails closed rather than guessing which objective
    owns authority.
    """
    root = Path(root).resolve()
    proposal_id = _sha64(proposal_id, "proposal_id")
    records = ObjectiveProposalStore.for_root(root).load().records
    by_id: dict[str, list[Any]] = {}
    for record in records:
        by_id.setdefault(record.proposal_id, []).append(record)

    def exact(key: str):
        matches = by_id.get(key, [])
        if len(matches) != 1:
            raise ValueError("objective proposal ancestry is unavailable or ambiguous")
        return matches[0]

    ledger = ObjectiveLifecycleStore.for_root(root).load()
    current_id = proposal_id
    visited: set[str] = set()
    first = True
    while True:
        if current_id in visited:
            raise ValueError("objective proposal ancestry contains a cycle")
        visited.add(current_id)
        record = exact(current_id)
        status = ledger.status(current_id)
        if status.blocks_automatic_progression:
            subject = "objective" if first else "ancestor objective"
            return ObjectiveProgressionDecision(
                proposal_id,
                False,
                status.state,
                current_id,
                f"{subject} is {status.state.value.lower()}",
            )
        source = record.source
        if isinstance(source, (ProgramObjectiveSource, ScopedObjectiveSource)):
            current_id = source.parent_proposal_id
            first = False
            continue
        break

    return ObjectiveProgressionDecision(
        proposal_id,
        True,
        ObjectiveLifecycleState.ACTIVE,
        proposal_id,
        "objective automatic progression is active",
    )
