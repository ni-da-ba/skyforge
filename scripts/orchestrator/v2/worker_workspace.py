"""Deterministic isolated worker-worktree preparation for Platform v2 R5C3."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
import re
import subprocess
from typing import Callable, Sequence

from .identity import canonical_digest
from .worker_provider import FrozenWorkerSpec


def _slug(branch: str) -> str:
    value = re.sub(r"[^A-Za-z0-9._-]+", "-", branch).strip("-") or "worker"
    return value[-120:]


@dataclass(frozen=True)
class WorkerWorkspace:
    controller_root: Path
    worktree: Path
    branch: str
    base_sha: str

    @property
    def digest(self) -> str:
        return canonical_digest({
            "controller_root": str(self.controller_root),
            "worktree": str(self.worktree),
            "branch": self.branch,
            "base_sha": self.base_sha,
        })


class WorkerWorkspaceManager:
    def __init__(
        self,
        *,
        root: Path,
        runner: Callable[..., subprocess.CompletedProcess[str]] = subprocess.run,
    ) -> None:
        self.root = Path(root).resolve()
        self.runner = runner

    def expected_path(self, spec: FrozenWorkerSpec) -> Path:
        return (
            self.root
            / ".skyforge-platform-v2"
            / "worktrees"
            / _slug(spec.branch)
        ).resolve()

    def _allowed(self, spec: FrozenWorkerSpec) -> set[tuple[str, ...]]:
        worktree = self.expected_path(spec)
        return {
            ("git", "cat-file", "-e", f"{spec.base_sha}^{{commit}}"),
            ("git", "show-ref", "--verify", f"refs/heads/{spec.branch}"),
            ("git", "worktree", "prune"),
            ("git", "branch", "-f", spec.branch, spec.base_sha),
            ("git", "worktree", "add", str(worktree), spec.branch),
            (
                "git",
                "worktree",
                "add",
                "-b",
                spec.branch,
                str(worktree),
                spec.base_sha,
            ),
            ("git", "rev-parse", "--is-inside-work-tree"),
            ("git", "branch", "--show-current"),
            ("git", "rev-parse", "HEAD"),
            ("git", "status", "--porcelain=v1", "--untracked-files=all"),
        }

    def _run(
        self,
        args: Sequence[str],
        *,
        spec: FrozenWorkerSpec,
        cwd: Path | None = None,
        check: bool = True,
        timeout: int = 120,
    ) -> subprocess.CompletedProcess[str]:
        command = tuple(str(x) for x in args)
        if command not in self._allowed(spec):
            raise ValueError("worker workspace command is outside exact allowlist")
        try:
            return self.runner(
                list(command),
                cwd=(cwd or self.root),
                check=check,
                text=True,
                capture_output=True,
                timeout=timeout,
            )
        except subprocess.SubprocessError as exc:
            raise RuntimeError(f"worker workspace command failed: {command[:3]}") from exc

    def _verify_existing(self, spec: FrozenWorkerSpec, worktree: Path) -> WorkerWorkspace:
        if worktree == self.root:
            raise RuntimeError("controller checkout cannot be worker worktree")
        probe = self._run(
            ["git", "rev-parse", "--is-inside-work-tree"],
            spec=spec,
            cwd=worktree,
            check=False,
        )
        if probe.returncode != 0 or probe.stdout.strip().lower() != "true":
            raise RuntimeError("existing worker path is not a Git worktree")
        branch = self._run(
            ["git", "branch", "--show-current"],
            spec=spec,
            cwd=worktree,
        ).stdout.strip()
        if branch != spec.branch:
            raise RuntimeError("existing worker worktree branch identity drifted")
        head = self._run(
            ["git", "rev-parse", "HEAD"],
            spec=spec,
            cwd=worktree,
        ).stdout.strip()
        if head != spec.base_sha:
            raise RuntimeError("existing worker worktree HEAD drifted from frozen base")
        dirty = self._run(
            ["git", "status", "--porcelain=v1", "--untracked-files=all"],
            spec=spec,
            cwd=worktree,
        ).stdout.strip()
        if dirty:
            # A dirty existing path may be interrupted provider work. Preparation must
            # never reset or discard it; durable worker-run recovery owns that state.
            raise RuntimeError(
                "existing worker worktree is dirty; explicit interrupted-run recovery required"
            )
        return WorkerWorkspace(self.root, worktree, spec.branch, spec.base_sha)

    def prepare(self, spec: FrozenWorkerSpec) -> WorkerWorkspace:
        if not self.root.is_dir():
            raise RuntimeError("controller repository root does not exist")
        worktree = self.expected_path(spec)
        if worktree == self.root:
            raise RuntimeError("controller checkout cannot be worker worktree")

        if worktree.exists():
            return self._verify_existing(spec, worktree)

        worktree.parent.mkdir(parents=True, exist_ok=True)
        self._run(["git", "cat-file", "-e", f"{spec.base_sha}^{{commit}}"], spec=spec)

        branch_probe = self._run(
            ["git", "show-ref", "--verify", f"refs/heads/{spec.branch}"],
            spec=spec,
            check=False,
        )
        if branch_probe.returncode == 0:
            # A deterministic branch collision without its expected worktree is
            # ambiguous. Do not force-reset it; preserve it for inspection.
            raise RuntimeError(
                "deterministic worker branch already exists without expected worktree"
            )

        self._run(
            [
                "git",
                "worktree",
                "add",
                "-b",
                spec.branch,
                str(worktree),
                spec.base_sha,
            ],
            spec=spec,
        )
        return self._verify_existing(spec, worktree)

    def verify_for_run(self, spec: FrozenWorkerSpec, worktree: Path) -> WorkerWorkspace:
        expected = self.expected_path(spec)
        actual = Path(worktree).resolve()
        if actual != expected:
            raise RuntimeError("worker worktree path does not match deterministic identity")
        return self._verify_existing(spec, actual)
