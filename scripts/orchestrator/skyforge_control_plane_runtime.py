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
core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)

_ORIGINAL_WORKER = core.Orchestrator._worker
_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot
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
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_lightweight_hosted_editing_extension_installed", False):
        return
    core.Orchestrator._worker = _edit_only_worker
    core.Orchestrator.health_snapshot = health_snapshot
    core._skyforge_lightweight_hosted_editing_extension_installed = True


install_extension()


def main() -> int:
    return roadmap_runtime.main()


if __name__ == "__main__":
    raise SystemExit(main())
