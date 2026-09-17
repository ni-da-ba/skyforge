"""Inert JSON state-store adapter for Platform v2.

This mirrors the current controller's primary/backup JSON persistence boundary without
being imported by the production runtime.  Network and repository mutation do not
belong in this adapter.
"""

from __future__ import annotations

from dataclasses import dataclass
import json
import os
from pathlib import Path
from typing import Any, Mapping

from .identity import canonical_digest


class StateStoreError(RuntimeError):
    """Raised when durable controller state cannot be read safely."""


@dataclass(frozen=True)
class JsonStateSnapshot:
    """Immutable canonical representation of one loaded JSON state mapping."""

    canonical_json: str
    source: str
    recovered_from_backup: bool = False

    @classmethod
    def from_mapping(
        cls,
        mapping: Mapping[str, Any],
        *,
        source: str,
        recovered_from_backup: bool = False,
    ) -> "JsonStateSnapshot":
        if not isinstance(mapping, Mapping):
            raise ValueError("controller state must be a mapping")
        try:
            canonical = json.dumps(
                dict(mapping),
                sort_keys=True,
                separators=(",", ":"),
                ensure_ascii=False,
            )
        except (TypeError, ValueError) as exc:
            raise ValueError("controller state must be JSON serializable") from exc
        decoded = json.loads(canonical)
        if not isinstance(decoded, dict):
            raise ValueError("controller state must canonicalize to a JSON object")
        return cls(
            canonical_json=canonical,
            source=str(source),
            recovered_from_backup=bool(recovered_from_backup),
        )

    def as_dict(self) -> dict[str, Any]:
        value = json.loads(self.canonical_json)
        if not isinstance(value, dict):
            raise StateStoreError("canonical state snapshot is not a JSON object")
        return value

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass(frozen=True)
class JsonStateStoreAdapter:
    """Current JSON state-store behavior behind an explicit v2 adapter boundary."""

    path: Path
    backup_path: Path

    @classmethod
    def for_legacy_root(cls, root: Path) -> "JsonStateStoreAdapter":
        state_dir = Path(root) / ".skyforge-orchestrator"
        path = state_dir / "state.json"
        return cls(path=path, backup_path=state_dir / "state.json.bak")

    @staticmethod
    def _read_mapping(path: Path) -> dict[str, Any]:
        value = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            raise ValueError(f"State file {path} must contain a JSON object")
        return value

    @staticmethod
    def _atomic_write(path: Path, payload: str) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        tmp = path.with_name(path.name + ".tmp")
        with tmp.open("w", encoding="utf-8") as handle:
            handle.write(payload)
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(tmp, path)

    def load(self) -> JsonStateSnapshot:
        primary_error: Exception | None = None
        loaded: dict[str, Any] | None = None
        source = "defaults"

        if self.path.exists():
            try:
                loaded = self._read_mapping(self.path)
                source = self.path.name
            except Exception as exc:
                primary_error = exc
        elif self.backup_path.exists():
            primary_error = FileNotFoundError(str(self.path))

        if loaded is None and self.backup_path.exists():
            try:
                loaded = self._read_mapping(self.backup_path)
                source = self.backup_path.name
                return JsonStateSnapshot.from_mapping(
                    loaded,
                    source=source,
                    recovered_from_backup=True,
                )
            except Exception as backup_error:
                raise StateStoreError(
                    "Skyforge orchestrator state is unreadable in both primary and backup files"
                ) from backup_error

        if loaded is None and primary_error is not None:
            raise StateStoreError(
                "Skyforge orchestrator state file is unreadable and no valid backup is available"
            ) from primary_error

        return JsonStateSnapshot.from_mapping(
            loaded or {},
            source=source,
            recovered_from_backup=False,
        )

    def save(self, state: Mapping[str, Any] | JsonStateSnapshot) -> JsonStateSnapshot:
        mapping = state.as_dict() if isinstance(state, JsonStateSnapshot) else dict(state)
        snapshot = JsonStateSnapshot.from_mapping(mapping, source=self.path.name)
        payload = json.dumps(snapshot.as_dict(), indent=2, sort_keys=True) + "\n"
        self._atomic_write(self.path, payload)
        self._atomic_write(self.backup_path, payload)
        return snapshot
