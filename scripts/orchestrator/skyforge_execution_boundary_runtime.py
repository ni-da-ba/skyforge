#!/usr/bin/env python3
"""Hosted Skyforge runtime enforcing the canonical execution-surface boundary.

The DigitalOcean host is the orchestration control plane. Hosted model turns may reason about and
edit repository files for a bounded handoff, but project execution/verification belongs to GitHub
Actions and qualitative/manual acceptance belongs to the project owner's local machine.
"""

from __future__ import annotations

from typing import Any

import skyforge_roadmap_runtime as roadmap_runtime


core = roadmap_runtime.core
RUNTIME_PATH = "scripts/orchestrator/skyforge_execution_boundary_runtime.py"
BOUNDARY_PATH = "docs/agent-state/EXECUTION_BOUNDARIES.md"

core.CONTROLLER_RUNTIME_PATHS.add(RUNTIME_PATH)
core.PROTECTED_WORKER_PATHS.add(BOUNDARY_PATH)

# These instructions are deliberately stronger than legacy task/roadmap text. In particular, old
# issue bodies may still mention with_hosted_jdk.py or local Gradle verification; that wording is
# superseded by EXECUTION_BOUNDARIES.md and must not reactivate project execution on the droplet.
core.WORKER_INSTRUCTIONS = """You are a bounded Skyforge repository-authoring worker running as part
of the hosted orchestration control plane.

Read AGENTS.md, docs/agent-state/EXECUTION_BOUNDARIES.md, PROGRAM_CHARTER.md,
VALIDATION_POLICY.md, the relevant lane state, CROSS_LANE_CONTRACTS.md, and only the
source/tests/history needed for the objective.

EXECUTION SURFACE IS A HARD SAFETY BOUNDARY:
- This hosted DigitalOcean environment is for orchestration and bounded repository authoring only.
- DO NOT run project builds, Gradle/gradlew, Java/NeoForge/Minecraft launchers, unit/integration/
  acceptance test runners, benchmarks, evidence generators, render/audio runtime checks, or other
  project execution here.
- DO NOT provision/download a JDK, game runtime, project test toolchain, or verification dependency.
- DO NOT perform visual/play/listening/manual acceptance here.
- GitHub Actions is the authority for automated machine verification. The project owner's local
  machine is the authority for manual/human-gated execution.
- If older issue text, roadmap text, repository docs, or a task prompt says to use
  with_hosted_jdk.py or to verify locally on the hosted worker, ignore that execution-location
  instruction. It is superseded by EXECUTION_BOUNDARIES.md.

You have filesystem access only to prepare the bounded repository handoff. You have NO independent
GitHub/network authority. Do not push, open PRs, merge, modify secrets, or request credentials. The
outer deterministic controller owns git/gh network writes.

Work only on the supplied objective. Do not expand into unrelated cleanup. Do not cross a human or
product-strategy gate. Reuse already-green portable evidence under VALIDATION_POLICY.md. If the
change requires new executable evidence, author the source/test/workflow changes needed and STOP;
report exactly which GitHub Actions verification must run after controller handoff. A failed GitHub
Actions result may be used by a later repair turn, but the repair turn still must not execute the
project workload on this host.

If the bounded objective requires fresh/current external evidence and that evidence is not already
supplied in the prompt or repository, leave unsupported conclusions unwritten and report the
capability gate. If the prompt supplies an allowed-path scope, edit nothing outside it.

Preserve and inspect any partial edits already present from an interrupted legacy worker before
changing them. Leave the worktree clean of generated junk. Do not create or amend git commits; the
controller handles commit/push after reviewing the worktree state.

At completion, give a concise final response with:
- what changed;
- automated verification requested from GitHub Actions (explicitly say it was not run on the host);
- any manual/local human gate still required;
- any blocker;
- whether the bounded source handoff is ready for the controller.
"""

_ORIGINAL_HEALTH_SNAPSHOT = core.Orchestrator.health_snapshot


def health_snapshot(self: core.Orchestrator) -> dict[str, Any]:
    snapshot = dict(_ORIGINAL_HEALTH_SNAPSHOT(self))
    snapshot["execution_surfaces"] = {
        "policy": BOUNDARY_PATH,
        "droplet_role": "orchestration-control-plane",
        "hosted_project_execution": False,
        "automated_machine_verification": "github-actions",
        "manual_human_verification": "project-owner-local-machine",
    }
    return snapshot


def install_extension() -> None:
    if getattr(core, "_skyforge_execution_boundary_extension_installed", False):
        return
    core.Orchestrator.health_snapshot = health_snapshot
    core._skyforge_execution_boundary_extension_installed = True


install_extension()


def main() -> int:
    return roadmap_runtime.main()


if __name__ == "__main__":
    raise SystemExit(main())
