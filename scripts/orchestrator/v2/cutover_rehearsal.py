"""Nonproduction process-level cutover/rollback rehearsal for Platform v2 R5C23.

This module may spawn only its own disposable fixture child processes. It has no service
manager, network listener, Git/GitHub mutation, production hosted-runtime, writer-fence,
or repository-effect capability.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from enum import Enum
import json
from pathlib import Path
import signal
import subprocess
import sys
import time
from typing import Any

from .cutover import (
    WriterAuthority,
    WriterAuthorityAction,
    WriterAuthorityTransition,
    advance_writer_authority,
)
from .identity import canonical_digest


_FIXTURE_CODE = r"""
import json
import os
from pathlib import Path
import signal
import sys
import time

role = sys.argv[1]
ready = Path(sys.argv[2]).resolve()
ready.parent.mkdir(parents=True, exist_ok=True)
ready.write_text(
    json.dumps({"role": role, "pid": os.getpid()}, sort_keys=True) + "\n",
    encoding="utf-8",
)
stopping = False

def handle(_signum, _frame):
    global stopping
    stopping = True

signal.signal(signal.SIGTERM, handle)
signal.signal(signal.SIGINT, handle)
while not stopping:
    time.sleep(0.05)
"""


class RehearsalRole(str, Enum):
    LEGACY = "LEGACY"
    V2 = "V2"


class RehearsalDisposition(str, Enum):
    ACCEPTED = "ACCEPTED"
    BLOCKED = "BLOCKED"


@dataclass(frozen=True)
class RehearsalEvent:
    sequence: int
    kind: str
    authority: WriterAuthority
    role: RehearsalRole | None = None
    pid: int | None = None
    transition_digest: str = ""
    detail: str = ""

    def __post_init__(self) -> None:
        if isinstance(self.sequence, bool) or not isinstance(self.sequence, int) or self.sequence <= 0:
            raise ValueError("sequence must be a positive integer")
        if not isinstance(self.kind, str) or not self.kind.strip():
            raise ValueError("kind is required")
        if not isinstance(self.authority, WriterAuthority):
            raise ValueError("authority must be WriterAuthority")
        if self.role is not None and not isinstance(self.role, RehearsalRole):
            raise ValueError("role must be RehearsalRole or null")
        if self.pid is not None and (
            isinstance(self.pid, bool) or not isinstance(self.pid, int) or self.pid <= 0
        ):
            raise ValueError("pid must be positive")
        if not isinstance(self.transition_digest, str):
            raise ValueError("transition_digest must be string")
        if not isinstance(self.detail, str):
            raise ValueError("detail must be string")

    def as_dict(self) -> dict[str, Any]:
        return {
            "sequence": self.sequence,
            "kind": self.kind,
            "authority": self.authority.value,
            "role": self.role.value if self.role is not None else None,
            "pid": self.pid,
            "transition_digest": self.transition_digest,
            "detail": self.detail,
        }


@dataclass(frozen=True)
class CutoverRehearsalEvidence:
    disposition: RehearsalDisposition
    reason: str
    final_authority: WriterAuthority
    events: tuple[RehearsalEvent, ...]
    forward_v2_observed: bool
    rollback_legacy_observed: bool
    no_overlap_proven: bool
    recovery_from_none_proven: bool

    def __post_init__(self) -> None:
        if not isinstance(self.disposition, RehearsalDisposition):
            raise ValueError("disposition must be RehearsalDisposition")
        if not isinstance(self.reason, str) or not self.reason.strip():
            raise ValueError("reason is required")
        if not isinstance(self.final_authority, WriterAuthority):
            raise ValueError("final_authority must be WriterAuthority")
        if not isinstance(self.events, tuple):
            raise ValueError("events must be tuple")
        expected = tuple(range(1, len(self.events) + 1))
        if tuple(event.sequence for event in self.events) != expected:
            raise ValueError("events must have contiguous ordered sequence")
        for name in (
            "forward_v2_observed",
            "rollback_legacy_observed",
            "no_overlap_proven",
            "recovery_from_none_proven",
        ):
            if not isinstance(getattr(self, name), bool):
                raise ValueError(f"{name} must be boolean")

    def as_dict(self) -> dict[str, Any]:
        return {
            "disposition": self.disposition.value,
            "reason": self.reason,
            "final_authority": self.final_authority.value,
            "forward_v2_observed": self.forward_v2_observed,
            "rollback_legacy_observed": self.rollback_legacy_observed,
            "no_overlap_proven": self.no_overlap_proven,
            "recovery_from_none_proven": self.recovery_from_none_proven,
            "events": [event.as_dict() for event in self.events],
        }

    @property
    def digest(self) -> str:
        return canonical_digest(self.as_dict())


@dataclass
class _FixtureProcess:
    role: RehearsalRole
    process: subprocess.Popen[str]
    ready_file: Path


class DisposableProcessSupervisor:
    """Starts/stops only this module's disposable fixture child process."""

    def __init__(self, root: Path, *, startup_timeout: float = 5.0) -> None:
        self.root = Path(root).resolve()
        self.root.mkdir(parents=True, exist_ok=True)
        self.startup_timeout = float(startup_timeout)
        if self.startup_timeout <= 0:
            raise ValueError("startup_timeout must be positive")
        self.active: _FixtureProcess | None = None

    def _ready_path(self, role: RehearsalRole) -> Path:
        return self.root / f"{role.value.lower()}-ready.json"

    def start(self, role: RehearsalRole, *, fail_start: bool = False) -> _FixtureProcess:
        if not isinstance(role, RehearsalRole):
            raise ValueError("role must be RehearsalRole")
        if self.active is not None and self.is_alive(self.active):
            raise RuntimeError("refusing dual fixture writers")
        if fail_start:
            raise RuntimeError(f"injected {role.value} fixture activation failure")

        ready = self._ready_path(role)
        ready.unlink(missing_ok=True)
        command = [
            sys.executable,
            "-c",
            _FIXTURE_CODE,
            role.value,
            str(ready),
        ]
        process = subprocess.Popen(
            command,
            cwd=self.root,
            text=True,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        fixture = _FixtureProcess(role, process, ready)
        deadline = time.monotonic() + self.startup_timeout
        while time.monotonic() < deadline:
            if process.poll() is not None:
                raise RuntimeError(
                    f"{role.value} fixture exited before readiness"
                )
            if ready.is_file():
                try:
                    value = json.loads(ready.read_text(encoding="utf-8"))
                except (OSError, json.JSONDecodeError):
                    time.sleep(0.02)
                    continue
                if (
                    isinstance(value, dict)
                    and value.get("role") == role.value
                    and value.get("pid") == process.pid
                ):
                    self.active = fixture
                    return fixture
            time.sleep(0.02)

        process.terminate()
        try:
            process.wait(timeout=1)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=1)
        raise RuntimeError(f"{role.value} fixture did not become ready")

    def is_alive(self, fixture: _FixtureProcess | None = None) -> bool:
        value = fixture if fixture is not None else self.active
        return value is not None and value.process.poll() is None

    def stop(
        self,
        role: RehearsalRole,
        *,
        fail_stop: bool = False,
    ) -> _FixtureProcess:
        fixture = self.active
        if fixture is None or fixture.role is not role:
            raise RuntimeError(f"{role.value} fixture is not the active writer")
        if not self.is_alive(fixture):
            raise RuntimeError(f"{role.value} fixture was not alive before revocation")
        if fail_stop:
            raise RuntimeError(f"injected {role.value} fixture revocation failure")

        fixture.process.terminate()
        try:
            fixture.process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            fixture.process.kill()
            fixture.process.wait(timeout=2)
        if self.is_alive(fixture):
            raise RuntimeError(f"{role.value} fixture remained alive after revocation")
        self.active = None
        return fixture

    def cleanup(self) -> None:
        fixture = self.active
        if fixture is None:
            return
        if self.is_alive(fixture):
            fixture.process.terminate()
            try:
                fixture.process.wait(timeout=2)
            except subprocess.TimeoutExpired:
                fixture.process.kill()
                fixture.process.wait(timeout=2)
        self.active = None


def rehearse_cutover_and_rollback(
    root: Path,
    *,
    readiness_accepted: bool = True,
    fail_legacy_revocation: bool = False,
    abort_after_legacy_revocation: bool = False,
    fail_v2_activation: bool = False,
) -> CutoverRehearsalEvidence:
    """Exercise real fixture processes and the accepted writer transition contract."""

    if not isinstance(readiness_accepted, bool):
        raise ValueError("readiness_accepted must be boolean")
    if not isinstance(fail_legacy_revocation, bool):
        raise ValueError("fail_legacy_revocation must be boolean")
    if not isinstance(abort_after_legacy_revocation, bool):
        raise ValueError("abort_after_legacy_revocation must be boolean")
    if not isinstance(fail_v2_activation, bool):
        raise ValueError("fail_v2_activation must be boolean")

    supervisor = DisposableProcessSupervisor(root)
    authority = WriterAuthority.LEGACY
    events: list[RehearsalEvent] = []
    forward_v2 = False
    rollback_legacy = False
    no_overlap = True
    recovery_from_none = False

    def record(
        kind: str,
        *,
        role: RehearsalRole | None = None,
        pid: int | None = None,
        transition: WriterAuthorityTransition | None = None,
        detail: str = "",
    ) -> None:
        events.append(
            RehearsalEvent(
                len(events) + 1,
                kind,
                authority,
                role,
                pid,
                transition.digest if transition is not None else "",
                detail,
            )
        )

    try:
        legacy = supervisor.start(RehearsalRole.LEGACY)
        record(
            "PROCESS_READY",
            role=RehearsalRole.LEGACY,
            pid=legacy.process.pid,
            detail="disposable legacy fixture is observably alive",
        )

        if fail_legacy_revocation:
            try:
                supervisor.stop(
                    RehearsalRole.LEGACY,
                    fail_stop=True,
                )
            except RuntimeError as exc:
                record(
                    "REVOCATION_BLOCKED",
                    role=RehearsalRole.LEGACY,
                    pid=legacy.process.pid,
                    detail=str(exc),
                )
                return CutoverRehearsalEvidence(
                    RehearsalDisposition.BLOCKED,
                    "legacy revocation failed; v2 was never started",
                    authority,
                    tuple(events),
                    False,
                    False,
                    True,
                    False,
                )

        stopped_legacy = supervisor.stop(RehearsalRole.LEGACY)
        if supervisor.is_alive(stopped_legacy):
            raise RuntimeError("legacy fixture still alive after revocation")
        transition = advance_writer_authority(
            authority,
            WriterAuthorityAction.REVOKE_LEGACY,
            readiness_accepted=readiness_accepted,
        )
        authority = transition.after
        record(
            "WRITER_NONE",
            role=RehearsalRole.LEGACY,
            pid=stopped_legacy.process.pid,
            transition=transition,
            detail="legacy process is dead before v2 activation attempt",
        )

        if abort_after_legacy_revocation:
            record(
                "ABORT_AT_WRITER_NONE",
                detail="injected abort after legacy revocation and before v2 activation",
            )
            recovery_from_none = True
            rollback_transition = advance_writer_authority(
                authority,
                WriterAuthorityAction.ACTIVATE_LEGACY,
                readiness_accepted=readiness_accepted,
            )
            restored = supervisor.start(RehearsalRole.LEGACY)
            authority = rollback_transition.after
            rollback_legacy = True
            record(
                "ROLLBACK_LEGACY_READY",
                role=RehearsalRole.LEGACY,
                pid=restored.process.pid,
                transition=rollback_transition,
                detail="legacy restored from explicit NONE after pre-v2 abort",
            )
            return CutoverRehearsalEvidence(
                RehearsalDisposition.ACCEPTED,
                "abort at explicit NONE rolled back to legacy without v2 start",
                authority,
                tuple(events),
                False,
                True,
                no_overlap,
                True,
            )

        # Purely validate the NONE -> V2 authority transition before starting the
        # v2 fixture. A rejected readiness predicate therefore cannot start even a
        # disposable would-be v2 writer.
        v2_transition = advance_writer_authority(
            authority,
            WriterAuthorityAction.ACTIVATE_V2,
            readiness_accepted=readiness_accepted,
        )

        if fail_v2_activation:
            try:
                supervisor.start(RehearsalRole.V2, fail_start=True)
            except RuntimeError as exc:
                record(
                    "V2_ACTIVATION_BLOCKED",
                    role=RehearsalRole.V2,
                    detail=str(exc),
                )
                recovery_from_none = True
                rollback_transition = advance_writer_authority(
                    authority,
                    WriterAuthorityAction.ACTIVATE_LEGACY,
                    readiness_accepted=readiness_accepted,
                )
                restored = supervisor.start(RehearsalRole.LEGACY)
                authority = rollback_transition.after
                rollback_legacy = True
                record(
                    "ROLLBACK_LEGACY_READY",
                    role=RehearsalRole.LEGACY,
                    pid=restored.process.pid,
                    transition=rollback_transition,
                    detail="legacy restored from explicit NONE after failed v2 activation",
                )
                return CutoverRehearsalEvidence(
                    RehearsalDisposition.ACCEPTED,
                    "failed v2 activation preserved NONE and rollback restored legacy",
                    authority,
                    tuple(events),
                    False,
                    True,
                    no_overlap,
                    True,
                )

        if supervisor.active is not None:
            no_overlap = False
            raise RuntimeError("unexpected active process before v2 start")
        v2 = supervisor.start(RehearsalRole.V2)
        transition = v2_transition
        authority = transition.after
        forward_v2 = True
        record(
            "V2_READY",
            role=RehearsalRole.V2,
            pid=v2.process.pid,
            transition=transition,
            detail="v2 fixture started only after observable NONE interval",
        )

        stopped_v2 = supervisor.stop(RehearsalRole.V2)
        if supervisor.is_alive(stopped_v2):
            raise RuntimeError("v2 fixture still alive after rollback revocation")
        transition = advance_writer_authority(
            authority,
            WriterAuthorityAction.REVOKE_V2,
            readiness_accepted=readiness_accepted,
        )
        authority = transition.after
        record(
            "ROLLBACK_WRITER_NONE",
            role=RehearsalRole.V2,
            pid=stopped_v2.process.pid,
            transition=transition,
            detail="v2 process is dead before legacy rollback activation",
        )

        if supervisor.active is not None:
            no_overlap = False
            raise RuntimeError("unexpected active process before legacy rollback")
        transition = advance_writer_authority(
            authority,
            WriterAuthorityAction.ACTIVATE_LEGACY,
            readiness_accepted=readiness_accepted,
        )
        restored = supervisor.start(RehearsalRole.LEGACY)
        authority = transition.after
        rollback_legacy = True
        recovery_from_none = True
        record(
            "ROLLBACK_LEGACY_READY",
            role=RehearsalRole.LEGACY,
            pid=restored.process.pid,
            transition=transition,
            detail="legacy fixture restored only after observable rollback NONE interval",
        )

        return CutoverRehearsalEvidence(
            RehearsalDisposition.ACCEPTED,
            "forward cutover and rollback completed with observable no-writer intervals",
            authority,
            tuple(events),
            forward_v2,
            rollback_legacy,
            no_overlap,
            recovery_from_none,
        )
    finally:
        supervisor.cleanup()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        description="Run disposable Platform-v2 cutover/rollback fixture rehearsal."
    )
    parser.add_argument("--root", type=Path)
    args = parser.parse_args(argv)

    if args.root is None:
        parser.error("--root is required for rehearsal mode")

    evidence = rehearse_cutover_and_rollback(args.root)
    print(json.dumps(evidence.as_dict(), sort_keys=True))
    return 0 if evidence.disposition is RehearsalDisposition.ACCEPTED else 2


if __name__ == "__main__":
    raise SystemExit(main())
