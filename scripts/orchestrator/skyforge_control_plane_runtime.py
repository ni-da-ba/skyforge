#!/usr/bin/env python3
"""Skyforge hosted orchestration runtime with lightweight Codex editing.

This restores the proven pre-JDK execution model: the DigitalOcean host runs the lightweight
classifier and bounded Codex editing worker in an isolated worktree, the deterministic controller
commits/pushes the resulting delta, and GitHub Actions owns automated project validation.

The host is deliberately *not* a project build machine. Gradle/Java/NeoForge/Minecraft builds,
project tests, benchmarks, generators, and other machine evidence run in GitHub Actions. Manual or
subjective verification remains on Nicholas' local workstation. See
``docs/agent-state/EXECUTION_BOUNDARIES.md``.
"""

from __future__ import annotations

from typing import Any

import skyforge_roadmap_runtime as roadmap_runtime
import skyforge_roadmap_closed_issue_recovery_runtime as roadmap_closed_issue_recovery_runtime


core = roadmap_runtime.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_control_plane_runtime.py"
CURRENT_PROJECT_STATE_PATH = "docs/agent-state/CURRENT_PROJECT_STATE.md"
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)
core.PROTECTED_WORKER_PATHS.add(CURRENT_PROJECT_STATE_PATH)

_ORIGINAL_WORKER = core.Orchestrator._worker
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot
_ORIGINAL_DRAIN_AND_DISPATCH = core.Orchestrator._drain_and_dispatch
_ORIGINAL_ROADMAP_STATE_LOCKED = roadmap_runtime._roadmap_state_locked

_EDIT_ONLY_GUARD = """

HOSTED EXECUTION BOUNDARY — EDITING ONLY:
The DigitalOcean host is allowed to run this bounded Codex worker only to inspect repository state and
produce source/document/configuration edits in its isolated worktree. It is not a project validation
runner.

Do NOT run project builds, compilers, test suites, benchmarks, generators, game/client launches, or
runtime validation on this host. In particular, do not run Gradle/gradlew, Java/javac,
NeoForge/Minecraft tasks, ``hosted_jdk.py``, ``with_hosted_jdk.py``, or any replacement project
build-toolchain bootstrap. Do not download or provision a project JDK/toolchain.

Lightweight editing support such as reading files, searching text, inspecting git state/diffs, and
``git diff --check`` is permitted. Persist the bounded source delta and report what GitHub Actions
must validate after controller handoff. If a conclusion genuinely depends on machine validation, do
not fabricate the result: hand off the edit and let GitHub Actions provide the evidence, or report a
gate if the edit itself cannot be made safely without that evidence.
"""

# The historical base prompt predates the hosted-JDK incident and asks workers to run local tests.
# Override that sentence at runtime rather than duplicating the large base module. The appended guard
# is intentionally stronger and later than any stale roadmap wording retained in durable state.
core.WORKER_INSTRUCTIONS = core.WORKER_INSTRUCTIONS.replace(
    "Read AGENTS.md, PROGRAM_CHARTER.md, VALIDATION_POLICY.md, the relevant lane state,\n"
    "CROSS_LANE_CONTRACTS.md, and only the source/tests/history needed for the objective.",
    "Read AGENTS.md and docs/agent-state/CURRENT_PROJECT_STATE.md first, verify that compact snapshot "
    "against current main/live task authority, then read PROGRAM_CHARTER.md, VALIDATION_POLICY.md, "
    "the relevant lane state, CROSS_LANE_CONTRACTS.md, and only the source/tests/history needed for "
    "the objective.",
)
core.WORKER_INSTRUCTIONS = core.WORKER_INSTRUCTIONS.replace(
    "Make local source/test/doc changes and run appropriate local verification.",
    "Make only the bounded local source/document/configuration edits required by the objective. "
    "Automated project verification belongs to GitHub Actions, not this host.",
)
if _EDIT_ONLY_GUARD.strip() not in core.WORKER_INSTRUCTIONS:
    core.WORKER_INSTRUCTIONS = core.WORKER_INSTRUCTIONS.rstrip() + _EDIT_ONLY_GUARD


def _roadmap_state_locked_with_current_gate_messages(
    self: core.Orchestrator,
    manifest: roadmap_runtime.roadmap_policy.RoadmapManifest,
) -> dict[str, Any]:
    """Keep durable gate identity while deriving human-facing prose from current authority.

    A blocked human gate remains blocked across protected manifest edits. Its timestamp and identity
    are durable facts; the explanatory message is a view of the current manifest and must not remain
    stale merely because the gate was first surfaced under an older wording.
    """
    state = _ORIGINAL_ROADMAP_STATE_LOCKED(self, manifest)
    blocked = state.get("blocked_nodes")
    if not isinstance(blocked, dict):
        return state

    by_id = {node.node_id: node for node in manifest.nodes}
    changed = False
    for node_id, entry in blocked.items():
        node = by_id.get(str(node_id))
        if node is None or node.kind != "gate" or not node.human_message:
            continue
        if not isinstance(entry, dict):
            continue
        current_reason = node.human_message[:1000]
        if entry.get("reason") == current_reason:
            continue
        entry["reason"] = current_reason
        changed = True

    if changed:
        state["last_gate_message_refresh_at"] = core._utc_now()
        self.state.save()
    return state


# Roadmap runtime functions resolve this module-global helper at call time. Wrapping it here keeps the
# pre-gate hotfix local to the hosted control plane while preserving roadmap blocking/completion state.
roadmap_runtime._roadmap_state_locked = _roadmap_state_locked_with_current_gate_messages


def _terminal_human_gate_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    """Return model-free terminal-gate status without mutating durable roadmap state.

    A terminal gate is quiescent only when the exact current manifest matches the persisted
    fingerprint, no roadmap node remains eligible, no roadmap node is active, and at least one blocked
    node is a human gate. A manifest edit deliberately breaks quiescence so new roadmap authority can
    be reconciled normally.
    """
    try:
        manifest = roadmap_runtime._roadmap_manifest(self)
    except Exception as exc:
        return {
            "latched": False,
            "reason": f"manifest unavailable: {type(exc).__name__}",
            "gate_ids": [],
        }

    with self._state_lock:
        state = self.state.data.get("roadmap")
        if not isinstance(state, dict) or state.get("roadmap_id") != manifest.roadmap_id:
            return {"latched": False, "reason": "roadmap state not initialized", "gate_ids": []}
        if state.get("manifest_fingerprint") != manifest.fingerprint:
            return {"latched": False, "reason": "roadmap manifest changed", "gate_ids": []}
        if isinstance(state.get("active"), dict):
            return {"latched": False, "reason": "roadmap node active", "gate_ids": []}
        completed_runs = {
            str(key): int(value or 0)
            for key, value in (state.get("completed_runs") or {}).items()
        }
        blocked_nodes = {
            str(key): value
            for key, value in (state.get("blocked_nodes") or {}).items()
        }

    by_id = {node.node_id: node for node in manifest.nodes}
    gate_ids = sorted(
        node_id
        for node_id in blocked_nodes
        if node_id in by_id and by_id[node_id].kind == "gate"
    )
    if not gate_ids:
        return {"latched": False, "reason": "no blocked human gate", "gate_ids": []}

    next_node = roadmap_runtime.roadmap_policy.select_next_node(
        manifest,
        completed_runs=completed_runs,
        blocked_nodes=set(blocked_nodes),
    )
    if next_node is not None:
        return {
            "latched": False,
            "reason": f"eligible roadmap node remains: {next_node.node_id}",
            "gate_ids": gate_ids,
        }
    return {
        "latched": True,
        "reason": "terminal human gate latched; ordinary lifecycle noise is model-free",
        "gate_ids": gate_ids,
    }


def _protected_pending_authority_exists_locked(self: core.Orchestrator) -> bool:
    for value in (self.state.data.get("pending_events") or []):
        if not isinstance(value, dict):
            continue
        event = core.EventDecision.from_state(value)
        if event.event == "roadmap" or event.signal_kind in core.PROTECTED_AUTHORITY_SIGNAL_KINDS:
            return True
    return False


def _retire_terminal_gate_noise_locked(self: core.Orchestrator) -> int:
    """Retire ordinary queued wake noise while a terminal human gate is latched.

    Protected task/restart/human-gate/loop-risk authority is never retired here. The caller also
    refuses this path while a classifier decision or worker is already in flight.
    """
    pending = [
        value
        for value in (self.state.data.get("pending_events") or [])
        if isinstance(value, dict)
    ]
    if not pending or _protected_pending_authority_exists_locked(self):
        return 0

    retired = [str(value) for value in (self.state.data.get("retired_event_keys") or []) if value]
    seen = set(retired)
    for value in pending:
        key = core._event_key(value)
        if key not in seen:
            retired.append(key)
            seen.add(key)
    self.state.data["retired_event_keys"] = retired[-core.DEFAULT_MAX_RETIRED_EVENT_KEYS :]
    self.state.data["pending_events"] = []
    metrics = self.state.data.setdefault("metrics", {})
    metrics["terminal_gate_events_retired_model_free"] = int(
        metrics.get("terminal_gate_events_retired_model_free") or 0
    ) + len(pending)
    self.state.data["last_terminal_gate_quiescence"] = {
        "at": core._utc_now(),
        "retired_events": len(pending),
        "reason": "terminal human gate latched; no protected authority pending",
    }
    self.state.save()
    return len(pending)


def _terminal_gate_quiescent_drain(self: core.Orchestrator) -> None:
    gate = _terminal_human_gate_snapshot(self)
    if gate.get("latched"):
        with self._state_lock:
            in_flight = bool(
                self.state.data.get("pending_decision")
                or isinstance(self.state.data.get("pending_worker"), dict)
                or self.state.data.get("blocked_kind")
            )
            protected = _protected_pending_authority_exists_locked(self)
            if not in_flight and not protected:
                retired = _retire_terminal_gate_noise_locked(self)
                if retired:
                    print(
                        f"[orchestrator] terminal human gate quiescence retired {retired} "
                        "ordinary event(s) without classifier/model use",
                        flush=True,
                    )
                return
    _ORIGINAL_DRAIN_AND_DISPATCH(self)


def _edit_only_worker(
    self: core.Orchestrator,
    prompt: str,
    worker_tier: str,
    worker_root=None,
) -> str:
    """Run the existing bounded Codex worker with a non-negotiable no-build/no-test guard."""
    guarded_prompt = prompt.replace(
        "Persist the bounded result as local file changes and tests.",
        "Persist the bounded result as local file changes only. Do not run project builds/tests here; "
        "GitHub Actions owns automated validation.",
    )
    guarded_prompt = guarded_prompt.rstrip() + _EDIT_ONLY_GUARD
    return _ORIGINAL_WORKER(self, guarded_prompt, worker_tier, worker_root)


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    snapshot["execution_boundary"] = {
        "mode": "digitalocean-orchestrator-lightweight-editing",
        "hosted_project_workers_enabled": True,
        "hosted_worker_mode": "codex-edit-only",
        "hosted_project_validation_enabled": False,
        "project_builds_tests_benchmarks_on_host": False,
        "automated_project_validation": "github-actions",
        "github_cloud_agent_required": False,
        "manual_verification": "nicholas-local-workstation",
    }
    snapshot["terminal_gate_quiescence"] = _terminal_human_gate_snapshot(self)
    snapshot["context_checkpoint"] = {
        "path": CURRENT_PROJECT_STATE_PATH,
        "purpose": "compact bootstrap cache; verify against current main/live authority",
    }
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_lightweight_hosted_editing_extension_installed", False):
        return
    core.Orchestrator._worker = _edit_only_worker
    core.Orchestrator._drain_and_dispatch = _terminal_gate_quiescent_drain
    core.Orchestrator.health_snapshot = health_snapshot
    core._skyforge_lightweight_hosted_editing_extension_installed = True


install_extension()


def main() -> int:
    return roadmap_runtime.main()


if __name__ == "__main__":
    raise SystemExit(main())
