"""POSIX single-writer fence for future Platform v2 controller ownership."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import fcntl
import json
import os
from pathlib import Path


class FenceBusyError(RuntimeError):
    """Raised when another process already owns the writer fence."""


@dataclass
class WriterFence:
    """Exclusive advisory lock with human-readable owner metadata."""

    path: Path
    controller_id: str
    _handle: object | None = None

    def acquire(self) -> None:
        if self._handle is not None:
            raise RuntimeError("writer fence is already acquired by this instance")
        self.path.parent.mkdir(parents=True, exist_ok=True)
        handle = self.path.open("a+", encoding="utf-8")
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as exc:
            handle.close()
            raise FenceBusyError(f"writer fence is already owned: {self.path}") from exc

        metadata = {
            "acquired_at": datetime.now(timezone.utc).isoformat(),
            "controller_id": self.controller_id,
            "pid": os.getpid(),
        }
        handle.seek(0)
        handle.truncate()
        json.dump(metadata, handle, sort_keys=True)
        handle.write("\n")
        handle.flush()
        os.fsync(handle.fileno())
        self._handle = handle

    def release(self) -> None:
        handle = self._handle
        if handle is None:
            return
        try:
            fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
        finally:
            handle.close()
            self._handle = None

    def __enter__(self) -> "WriterFence":
        self.acquire()
        return self

    def __exit__(self, exc_type, exc, traceback) -> None:
        self.release()
