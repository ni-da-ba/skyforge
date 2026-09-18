"""Bounded worker-worktree commit adapter for Platform v2.

Local repository edits are already produced by a disposable worker.  This adapter only
validates that bounded delta and creates one deterministic handoff commit.  It never
pushes or calls GitHub.
"""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import subprocess
from typing import Callable, Sequence

from .identity import canonical_digest


ATTEMPT_TRAILER = "Skyforge-Attempt"


def _normalize(path: str) -> str:
    return str(path).replace("\\", "/").lstrip("./")


def _allowed(path: str, scopes: tuple[str, ...]) -> bool:
    value = _normalize(path)
    for raw in scopes:
        scope = _normalize(raw)
        if scope.endswith("/**"):
            prefix = scope[:-3].rstrip("/") + "/"
            if value.startswith(prefix):
                return True
        elif value == scope:
            return True
    return False


@dataclass(frozen=True)
class WorkspaceCommitScope:
    attempt_id: str
    branch: str
    start_head: str
    allowed_paths: tuple[str, ...]
    protected_paths: tuple[str, ...] = ()
    protected_prefixes: tuple[str, ...] = (
        ".git/",
        ".skyforge-orchestrator/",
        ".skyforge-platform-v2/",
    )

    def __post_init__(self) -> None:
        for name in ("attempt_id", "branch", "start_head"):
            if not str(getattr(self, name) or "").strip():
                raise ValueError(f"workspace scope {name} is required")
        if len(self.start_head) != 40:
            raise ValueError("workspace scope start_head must be a 40-character SHA")
        if not self.allowed_paths:
            raise ValueError("workspace scope allowed_paths must be non-empty")
        for values, label in (
            (self.allowed_paths, "allowed_paths"),
            (self.protected_paths, "protected_paths"),
            (self.protected_prefixes, "protected_prefixes"),
        ):
            if any(not isinstance(value, str) or not value.strip() for value in values):
                raise ValueError(f"workspace scope {label} must contain non-empty paths")

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "attempt_id": self.attempt_id,
                "branch": self.branch,
                "start_head": self.start_head,
                "allowed_paths": list(self.allowed_paths),
                "protected_paths": list(self.protected_paths),
                "protected_prefixes": list(self.protected_prefixes),
            }
        )


@dataclass(frozen=True)
class WorkspaceCommitResult:
    head_sha: str
    changed_paths: tuple[str, ...]
    commit_created: bool

    @property
    def digest(self) -> str:
        return canonical_digest(
            {
                "head_sha": self.head_sha,
                "changed_paths": list(self.changed_paths),
                "commit_created": self.commit_created,
            }
        )


class WorkspaceCommitAdapter:
    def __init__(
        self,
        *,
        worktree: Path,
        scope: WorkspaceCommitScope,
        runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
    ) -> None:
        self.worktree = Path(worktree)
        self.scope = scope
        self.runner = runner

    def _run(self, args: Sequence[str], *, timeout: int = 60) -> str:
        allowed_prefixes = {
            ("git", "rev-parse", "HEAD"),
            ("git", "branch", "--show-current"),
            ("git", "status", "--porcelain"),
            ("git", "diff", "--check", "HEAD"),
            ("git", "add", "--all"),
            ("git", "log", "-1", "--format=%B"),
        }
        command = tuple(str(value) for value in args)
        is_commit = (
            len(command) == 5
            and command[:3] == ("git", "commit", "-m")
            and command[3].startswith("CODEX ")
            and command[4] == ""
        )
        # commit is emitted through _commit below because multi-line -m cannot fit the
        # simple set shape above.
        if command not in allowed_prefixes and not is_commit:
            raise ValueError("workspace command is outside the bounded allowlist")
        try:
            result = self.runner(
                list(command[:-1] if is_commit else command),
                cwd=self.worktree,
                check=True,
                text=True,
                capture_output=True,
                timeout=timeout,
            )
        except subprocess.SubprocessError as exc:
            raise RuntimeError(f"workspace command failed: {command[:3]}") from exc
        return result.stdout.strip()

    def _raw(self, args: Sequence[str], *, timeout: int = 60) -> str:
        """Run a command already constructed internally, not caller-controlled."""
        try:
            result = self.runner(
                list(args),
                cwd=self.worktree,
                check=True,
                text=True,
                capture_output=True,
                timeout=timeout,
            )
        except subprocess.SubprocessError as exc:
            raise RuntimeError(f"workspace command failed: {tuple(args[:3])}") from exc
        return result.stdout.strip()

    def _current_head(self) -> str:
        head = self._raw(("git", "rev-parse", "HEAD"))
        if len(head) != 40:
            raise RuntimeError("workspace HEAD is malformed")
        return head

    def _current_branch(self) -> str:
        return self._raw(("git", "branch", "--show-current"))

    def _changed_paths(self) -> tuple[str, ...]:
        lines = self._raw(("git", "status", "--porcelain")).splitlines()
        paths: set[str] = set()
        for line in lines:
            if not line:
                continue
            raw = line[3:] if len(line) > 3 else line
            if " -> " in raw:
                raw = raw.split(" -> ", 1)[1]
            paths.add(_normalize(raw.strip()))
        return tuple(sorted(path for path in paths if path))

    def _validate_paths(self, paths: tuple[str, ...]) -> None:
        protected_exact = {_normalize(path) for path in self.scope.protected_paths}
        prefixes = tuple(_normalize(path) for path in self.scope.protected_prefixes)
        for path in paths:
            if path in protected_exact or any(path.startswith(prefix) for prefix in prefixes):
                raise RuntimeError(f"worker changed protected path: {path}")
            if not _allowed(path, self.scope.allowed_paths):
                raise RuntimeError(f"worker changed path outside frozen scope: {path}")

    def _existing_attempt_commit(self) -> WorkspaceCommitResult | None:
        head = self._current_head()
        body = self._raw(("git", "log", "-1", "--format=%B"))
        marker = f"{ATTEMPT_TRAILER}: {self.scope.attempt_id}"
        if marker not in body:
            return None
        if self._changed_paths():
            raise RuntimeError("attempt commit exists but worktree is dirty")
        return WorkspaceCommitResult(
            head_sha=head,
            changed_paths=(),
            commit_created=False,
        )

    def commit(self, *, lane: str, objective: str) -> WorkspaceCommitResult:
        branch = self._current_branch()
        if branch != self.scope.branch:
            raise RuntimeError(
                f"worker branch drifted: expected {self.scope.branch!r}, got {branch!r}"
            )

        existing = self._existing_attempt_commit()
        if existing is not None:
            return existing

        if self._current_head() != self.scope.start_head:
            raise RuntimeError("worker HEAD moved before bounded handoff commit")

        paths = self._changed_paths()
        self._validate_paths(paths)
        if not paths:
            return WorkspaceCommitResult(
                head_sha=self.scope.start_head,
                changed_paths=(),
                commit_created=False,
            )

        self._raw(("git", "diff", "--check", "HEAD"))
        self._raw(("git", "add", "--all"))
        short = " ".join(str(objective or "").split())[:72] or "bounded task"
        lane_text = " ".join(str(lane or "Program").split())[:32] or "Program"
        message = (
            f"CODEX {lane_text}: {short}\n\n"
            f"{ATTEMPT_TRAILER}: {self.scope.attempt_id}"
        )
        self._raw(("git", "commit", "-m", message), timeout=120)
        head = self._current_head()
        if head == self.scope.start_head:
            raise RuntimeError("bounded commit did not advance HEAD")
        return WorkspaceCommitResult(
            head_sha=head,
            changed_paths=paths,
            commit_created=True,
        )
