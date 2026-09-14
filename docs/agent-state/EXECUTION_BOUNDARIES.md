# Skyforge Execution Surface Boundaries

**Status:** canonical, machine-facing execution policy  
**Owner:** Program / Audit  
**Precedence:** this document overrides any older wording in `ORCHESTRATION_PROTOCOL.md`, lane state, issue text, worker prompts, or historical PRs that assigns project build/test/runtime work to the hosted orchestrator machine.

## 1. Non-negotiable three-surface model

Skyforge uses three execution surfaces. They are intentionally not interchangeable.

| Surface | Owns | Must not own |
| --- | --- | --- |
| **DigitalOcean `skyforge-orchestrator` droplet** | Always-on orchestration control plane: webhook ingress, durable orchestration state, GitHub observation/coordination, classifier/model-control calls, bounded source-authoring handoff, branch/PR bookkeeping, controller health and controller-only integrity checks | Project builds, Gradle/NeoForge execution, unit/integration/acceptance tests, benchmark/evidence generation, Minecraft runtime, visual/play/listening review, or use as a general development workstation |
| **GitHub / GitHub Actions** | Durable repository truth; branches and PRs; **all automated project machine work and verification**: compilation, unit/integration tests, Gradle/NeoForge runs, generated evidence, benchmarks, deterministic acceptance checks, workflow artifacts | Human qualitative judgment or interactive local play/listening review |
| **Project owner's local machine** | **All manual/human-gated execution**: Minecraft launch/flight/exploration, visual comparison, listening/taste review, interactive inspection, hardware-specific/manual checks, and explicit owner judgment | Serving as required automated CI evidence when a GitHub Actions check can express the invariant |

The droplet exists to **orchestrate development, not to impersonate CI or the owner's workstation**.

## 2. Hosted orchestrator worker rule

A model turn launched by the hosted orchestrator is part of the orchestration control plane only when it is bounded to repository reasoning/source authoring and handoff.

A hosted worker **MAY**:

- read repository state and the bounded task authority supplied by the controller;
- edit source, tests, documentation, fixtures, and configuration within its authorized scope;
- perform controller-level Git hygiene needed to create an auditable handoff;
- commit/push/open or update the controller-managed PR through the deterministic outer controller.

A hosted worker **MUST NOT**:

- run `gradle`, `gradlew`, NeoForge/Minecraft, Java project launchers, test runners, benchmark harnesses, evidence generators, render/audio runtime checks, or acceptance workflows on the droplet;
- download/provision a project JDK, game runtime, test toolchain, or other project execution dependency for the purpose of verification;
- claim PASS from static inspection when the acceptance contract requires executable machine evidence;
- substitute a droplet-local result for a GitHub Actions check;
- perform a human/visual/play/listening gate.

If a bounded change requires executable verification, the worker stops after producing the source handoff. The PR is then verified by GitHub Actions. A failed Action becomes new machine evidence for a subsequent bounded repair turn; the repair worker still does not execute the project test locally on the droplet.

## 3. What is allowed on the droplet

Controller operations are not project verification. The following remain allowed because they are required to operate the control plane itself:

- `git` operations used to synchronize the stable controller checkout and hand off branches;
- `gh` API/CLI calls used to observe GitHub, post status/gates, push controller-managed branches, and create/update PRs;
- Python needed to run the orchestrator, its small controller-only helpers, dependency synchronization for the orchestrator itself, and webhook/TLS/value-report plumbing;
- model-free integrity checks over controller state and Git metadata;
- the lightweight read-only classifier and bounded source-authoring model turns.

Controller code itself is validated in GitHub Actions (`orchestrator-smoke` / normal CI) before deployment. The production droplet may perform startup/health checks only; it is not the validation environment for a control-plane change.

## 4. GitHub Actions is the automated evidence authority

Any statement equivalent to “tests pass,” “the build succeeds,” “the runtime fixture reproduces,” “the benchmark meets the threshold,” or “machine acceptance is green” must be backed by the relevant GitHub Actions run for the exact PR head unless the canonical validation policy explicitly defines a different remote machine-evidence source.

Workers may use existing green portable evidence under `VALIDATION_POLICY.md`; they should not rerun expensive workflows without a distinct uncertainty. When new executable evidence is required, request/allow the appropriate GitHub workflow and wait for the workflow event rather than executing the workload on the droplet.

## 5. Local machine is the human gate surface

When repository policy requires human judgment, the orchestrator must stop and surface a precise local review packet. The packet should include the exact branch/PR/head, launch command, world/run directory or artifact, specimen IDs/seeds, review route, and pass/fail questions.

The project owner's local machine is the only default surface for:

- Minecraft visual/flight/exploration acceptance;
- art/render inspection;
- music/audio listening and taste judgment;
- manual interaction checks that cannot be encoded as automated GitHub evidence.

The owner result is recorded back into durable GitHub state before automation continues.

## 6. Failure behavior

Execution-surface uncertainty fails closed.

- If a hosted task asks for a droplet-local build/test, **do not run it**. Produce the bounded source handoff and let GitHub Actions verify it.
- If GitHub Actions cannot express the required check yet, author/fix the workflow in an ordinary reviewed PR; do not temporarily move the workload to the droplet.
- If the remaining uncertainty is qualitative/manual, surface a `HUMAN_GATE` with local instructions; do not infer acceptance.
- If an older issue, roadmap node, prompt, or PR body says to use `with_hosted_jdk.py` or otherwise run project verification on the hosted worker, that instruction is superseded by this document.

## 7. Migration rule for legacy hosted workers

Legacy durable worker state created before this boundary may be resumed only to preserve/finish repository edits and hand them to GitHub. It must resume under the current no-project-execution rule.

Do not discard a dirty legacy worktree merely to simplify migration. Preserve its diff, produce an auditable PR handoff when safe, and let GitHub Actions establish machine validity. Once the handoff is durable, retire the old worktree/toolchain state.

## 8. Agent bootstrap requirement

Every Skyforge agent must read this file before selecting or executing work. Every orchestration/worker prompt must preserve these boundaries explicitly.

When documents disagree, use this ordering for execution location:

1. `EXECUTION_BOUNDARIES.md`;
2. `VALIDATION_POLICY.md` for what evidence is required;
3. `ORCHESTRATION_PROTOCOL.md` for dispatch/state semantics;
4. lane/task documents for the bounded objective.

No lane or task may silently redefine where computation or human judgment runs.
