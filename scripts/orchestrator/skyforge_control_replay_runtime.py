#!/usr/bin/env python3
"""Hosted Skyforge runtime with idempotent replay-safe operator controls.

The quota runtime installs the adaptive provider governor and merged-handoff retirement logic. This
thin final wrapper makes ``/skyforge-discard-worker`` idempotent when reconciliation replays an
already-consumed discard command after the worker has been retired. A no-worker discard cannot delete
or mutate project work, so treating it as an acknowledged no-op lets the durable issue-comment cursor
advance instead of retrying the same historical control forever.
"""

from __future__ import annotations

from typing import Any

import skyforge_quota_runtime as quota_runtime


core = quota_runtime.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_control_replay_runtime.py"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_DISCARD_PENDING_WORKER = core.Orchestrator.discard_pending_worker


def discard_pending_worker(self: core.Orchestrator, *, actor: str | None = None) -> None:
    """Acknowledge a replayed discard after its target worker is already gone.

    The paused requirement remains authoritative. Existing pending workers still delegate to the
    quota runtime's normal guarded discard/merged-handoff retirement implementation.
    """
    with self._state_lock:
        if not self.state.data.get("paused"):
            raise RuntimeError("Worker discard requires the controller to be paused")
        pending = self.state.data.get("pending_worker")
        if isinstance(pending, dict):
            delegate = True
        else:
            delegate = False
            self.state.data["last_worker_discard_noop"] = {
                "at": core._utc_now(),
                "actor": actor,
                "reason": "discard control replayed after pending worker was already retired",
            }
            self.state.save()

    if delegate:
        _ORIGINAL_DISCARD_PENDING_WORKER(self, actor=actor)
        return

    self._metric("operator_discard_noops")


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(quota_runtime.health_snapshot(self))
    with self._state_lock:
        snapshot["last_worker_discard_noop"] = self.state.data.get("last_worker_discard_noop")
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_control_replay_extension_installed", False):
        return
    core.Orchestrator.discard_pending_worker = discard_pending_worker
    core.Orchestrator.health_snapshot = health_snapshot
    core._skyforge_control_replay_extension_installed = True


install_extension()


def main() -> int:
    return quota_runtime.main()


if __name__ == "__main__":
    raise SystemExit(main())
