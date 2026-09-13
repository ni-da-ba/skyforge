#!/usr/bin/env python3
"""Fail closed on cheap deterministic validation before controller PR handoff.

Workers remain responsible for objective-specific verification. This extension adds a small outer-
controller safety net for mistakes that should never consume a GitHub CI round trip: malformed diffs
and Java sources that do not compile. Validation runs before the worker is promoted from ``editing``
to durable ``handoff`` so a failed preflight remains repairable instead of becoming a replay loop.
"""

from __future__ import annotations

import sys
from pathlib import Path
from typing import Iterable

import skyforge_control_replay_base as replay_base


core = replay_base.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_pre_handoff_validation_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_MARK_WORKER_HANDOFF = core.Orchestrator._mark_worker_handoff


def _java_compile_tasks(paths: Iterable[str]) -> list[str]:
    """Return the smallest Gradle compile task set implied by changed Java sources."""
    tasks: set[str] = set()
    for raw in paths:
        path = str(raw or "").replace("\\", "/").lstrip("./")
        parts = path.split("/")
        if len(parts) < 2 or not path.endswith(".java"):
            continue
        module = parts[0]
        if not module.startswith("skyforge-"):
            continue
        if "/src/test/java/" in f"/{path}":
            tasks.add(f":{module}:compileTestJava")
        elif "/src/main/java/" in f"/{path}":
            tasks.add(f":{module}:compileJava")
    return sorted(tasks)


def _run_pre_handoff_validation(
    self: core.Orchestrator,
    worktree: Path,
    changed_paths: list[str],
) -> None:
    """Run cheap deterministic checks before durable worker handoff state is written."""
    diff_check = core._run(
        ["git", "diff", "--check", "HEAD"],
        cwd=worktree,
        check=False,
        timeout=60,
    )
    if diff_check.returncode != 0:
        self._metric("pre_handoff_validation_failures")
        raise RuntimeError("Pre-handoff validation failed: git diff --check rejected worker changes")

    tasks = _java_compile_tasks(changed_paths)
    if not tasks:
        self._metric("pre_handoff_validation_passes")
        return

    wrapper = self.root / "scripts" / "orchestrator" / "with_hosted_jdk.py"
    gradlew = worktree / "gradlew"
    result = core._run(
        [
            sys.executable,
            str(wrapper),
            "--",
            str(gradlew),
            "-p",
            str(worktree),
            "--no-daemon",
            *tasks,
        ],
        cwd=worktree,
        check=False,
        timeout=900,
    )
    if result.returncode != 0:
        self._metric("pre_handoff_validation_failures")
        summary = (result.stderr or result.stdout or "compile task failed").strip()[-2000:]
        raise RuntimeError(
            "Pre-handoff Java compilation failed before durable handoff; "
            f"tasks={tasks}; tail={summary}"
        )

    self._metric("pre_handoff_validation_passes")


def _pending_worktree(self: core.Orchestrator) -> Path:
    with self._state_lock:
        pending = self.state.data.get("pending_worker")
        if not isinstance(pending, dict):
            raise RuntimeError("Cannot validate worker handoff without pending worker state")
        raw_worktree = pending.get("worktree")
    if not raw_worktree:
        raise RuntimeError("Cannot validate worker handoff without an isolated worktree")
    worktree = Path(str(raw_worktree))
    if not worktree.is_absolute():
        worktree = self.root / worktree
    if worktree.resolve() == self.root.resolve():
        raise RuntimeError("Refusing pre-handoff validation in the controller checkout")
    return worktree


def _mark_worker_handoff(self: core.Orchestrator, worker_summary: str) -> None:
    """Validate mutable worker output before changing its durable stage to ``handoff``."""
    worktree = _pending_worktree(self)
    changed_paths = self._changed_paths(worktree)
    if changed_paths:
        try:
            _run_pre_handoff_validation(self, worktree, changed_paths)
        except Exception as exc:
            with self._state_lock:
                pending = self.state.data.get("pending_worker")
                if isinstance(pending, dict):
                    # Keep the worker explicitly repairable. A later dispatch resumes the isolated
                    # editing worktree rather than replaying an already-terminal handoff forever.
                    pending["stage"] = "editing"
                    pending["last_pre_handoff_validation_error"] = {
                        "at": core._utc_now(),
                        "kind": type(exc).__name__,
                        "summary": str(exc)[-2000:],
                    }
                    self.state.data["last_pre_handoff_validation_error"] = dict(
                        pending["last_pre_handoff_validation_error"]
                    )
                    self.state.save()
            raise
    _ORIGINAL_MARK_WORKER_HANDOFF(self, worker_summary)


core.Orchestrator._mark_worker_handoff = _mark_worker_handoff
