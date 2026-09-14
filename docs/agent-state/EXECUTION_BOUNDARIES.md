# Skyforge Execution Boundaries

**Status:** Canonical infrastructure and execution-authority contract  
**Owner intent:** DigitalOcean is the orchestration control plane only; GitHub is the automated machine-execution plane; Nicholas' local workstation is the manual verification plane.

These boundaries are hard safety/architecture invariants. They apply to every agent, orchestration worker, recovery procedure, roadmap task, and future infrastructure change unless the project owner explicitly changes this file and the corresponding implementation through an accepted repository change.

## 1. DigitalOcean droplet — orchestration control plane only

The `skyforge-orchestrator` DigitalOcean Droplet exists only to keep the lightweight controller available.

Allowed on the Droplet:

- receive and validate GitHub webhooks;
- maintain controller state, event journals, leases, queue/reconciliation state, and value telemetry;
- run the Luna/classification/orchestration logic needed to decide what should happen next;
- inspect GitHub repository/PR/issue/Actions state through APIs;
- create/update GitHub control-plane handoffs, comments, claims, cloud-agent assignments, and dispatch records;
- run Caddy, systemd, health checks, dependency synchronization for the orchestrator itself, and other minimal control-plane maintenance;
- keep a minimal clean repository checkout only when required to load controller code/policy or update the controller runtime.

Forbidden on the Droplet:

- implementation/authorship/content/presentation/music development work;
- producer worktrees used to edit project deliverables;
- Gradle, Java/NeoForge/Minecraft, native build, compile, unit/integration, benchmark, or project test execution;
- use of `hosted_jdk.py`, `with_hosted_jdk.py`, or any equivalent hosted project-toolchain wrapper for milestone verification;
- launching Terra/Sol/Luna as an implementation worker that edits project deliverables on Droplet storage;
- treating the Droplet as a remote Codex development workspace;
- using the Droplet as a fallback runner when GitHub Actions is unavailable, slow, quota-limited, or failing.

The only exception is a bounded **recovery evacuation** of pre-existing Droplet-side work created before this contract: preserve/export the existing bytes to GitHub without modifying the recovered deliverable and without running project builds/tests. Recovery is infrastructure maintenance, not permission to continue development on the Droplet.

If a controller code path would execute project development or automated validation on the Droplet, it must fail closed and surface an infrastructure-policy error rather than run the command.

## 2. GitHub — durable development and automated machine execution

GitHub is authoritative for project source, branches, PRs, issues, accepted evidence, and automated execution.

All non-manual project work must be persisted through GitHub. Automated validation runs on GitHub-hosted execution (normally GitHub Actions), including:

- builds and compilation;
- unit, integration, lifecycle, determinism, persistence, performance, and regression tests;
- Gradle/Java/NeoForge machine verification;
- generated machine evidence that does not require subjective human judgment;
- automation used to repair or develop project code, when such an agent/runner is available through the GitHub execution surface.

A producer may reason in ChatGPT/Codex, but its durable edits and machine evidence must flow through GitHub. The orchestration control plane dispatches work **to GitHub-backed execution/handoffs**; it does not host the producer itself.

The default autonomous producer for issue-backed roadmap work is GitHub's cloud coding-agent assignment surface. The control plane may use the GitHub API to assign the bounded issue to the configured cloud agent (default API assignee `copilot-swe-agent[bot]`). That coding session executes on GitHub's cloud/Actions-backed execution plane, not on DigitalOcean. The controller retains roadmap ownership until the agent-created PR is linked and reaches a terminal state.

If the configured GitHub cloud agent is unavailable, unauthorized, disabled, or quota-blocked, the task is infrastructure-blocked and must fail closed. Do not fall back to a DigitalOcean producer. A repository owner may enable/authorize the GitHub agent and then resume the controller.

If GitHub automated execution is unavailable, the task is `WAIT_CI` / infrastructure-blocked. Do not move automated execution to the DigitalOcean Droplet or Nicholas' local workstation merely to keep the pipeline moving.

## 3. Nicholas' local workstation — manual/interactive verification only

The local workstation is the authority for verification that genuinely requires the owner, a local interactive client, peripherals, or subjective inspection, including:

- Minecraft/NeoForge interactive launch and play checks;
- visual terrain/world review;
- listening/music judgment;
- taste/aesthetic/product judgment;
- manual reproduction that requires the real local client environment;
- owner approval and other human gates.

Local manual evidence may be recorded back into GitHub, but the workstation is not the fallback automated CI/build farm. Routine automated tests that can run in GitHub Actions belong in GitHub Actions.

If a required manual gate cannot currently be run locally, leave it pending. Do not simulate or substitute it on the Droplet.

## 4. No-fallback rule

Execution authority does not migrate merely because one plane is inconvenient:

| Work | Authority | If unavailable |
| --- | --- | --- |
| Orchestration/control-plane operation | DigitalOcean Droplet | repair the orchestrator; do not move development there |
| Project development + automated machine evidence | GitHub / GitHub Actions | block/wait; do not fall back to Droplet or local |
| Human/manual/visual/listening verification | Nicholas' local workstation | leave human gate pending; do not automate it elsewhere |

A task that crosses categories must split its evidence: GitHub completes the machine-verifiable portion; the local workstation completes the manual gate; the Droplet only coordinates and records the transition.

## 5. Orchestrator dispatch contract

The orchestrator may classify, prioritize, claim, and hand off a bounded task. It must not execute the task's project commands on the Droplet.

A valid autonomous dispatch must therefore produce one of:

1. an issue-backed GitHub cloud-agent assignment whose project edits execute on GitHub and whose machine checks run in GitHub Actions;
2. a precise issue/PR handoff for an already-running external producer; or
3. `WAIT_CI`, `HUMAN_GATE`, `NOOP`, or an explicit infrastructure block.

`DISPATCH` never means "spawn a hosted worker on the Droplet." If the GitHub producer cannot be assigned safely, fail closed and surface the exact authorization/capability block.

GitHub-agent assignment is durable producer ownership. The issue remains the task authority, and the controller records `github_agent_dispatches[task:<issue>]` until the agent-created PR is cross-referenced from the issue. The roadmap must not launch a successor task while that assignment or its linked PR remains active.

## 6. Recovery of legacy hosted-worker state

At adoption of this contract, DR-20 / issue #492 has a preserved pre-contract Droplet recovery bundle containing information-bearing hydrology work. The DigitalOcean provider snapshot is the independent safety copy until evacuation completes.

Recovery sequence:

1. keep orchestration paused for new producer dispatch while the legacy hosted worker is attached;
2. export the preserved recovery bundle/delta to a GitHub recovery branch **without editing it and without running project tests on the Droplet**;
3. record the recovery branch/head on issue #492;
4. retire the legacy hosted worker/worktree only after the exported GitHub branch exists;
5. refresh the controller checkout and installed systemd unit to a `main` containing this execution-boundary contract and GitHub dispatch runtime;
6. resume orchestration with Droplet-side producer execution disabled;
7. continue DR-20 from current `main` through the GitHub execution plane; the recovery branch is reference/salvage material rather than authority for new hosted development;
8. keep automated verification in GitHub Actions and any later visual/manual gate on the local workstation.

The repository-versioned `scripts/orchestrator/recover_host_to_control_plane.py` utility implements this migration and must fail closed before journal retirement if the recovery branch cannot be pushed.

## 7. Agent startup rule

Every Skyforge agent must read this contract before choosing an execution surface. Any older documentation that describes the DigitalOcean host as a "remote Codex workspace", "hosted worker", or project build/test machine is superseded by this file.

When documentation, prompts, roadmap manifests, or recovery instructions conflict with this contract, this contract wins until the conflict is repaired in the repository.