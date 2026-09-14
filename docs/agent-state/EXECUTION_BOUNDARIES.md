# Skyforge Execution Boundaries

**Status:** Canonical infrastructure and execution-authority contract  
**Owner intent:** DigitalOcean provides always-on orchestration plus lightweight bounded Codex editing; GitHub/GitHub Actions provide durable project state and automated machine validation; Nicholas' local workstation provides manual/interactive verification.

These boundaries are hard architecture invariants. They apply to every agent, orchestration worker, recovery procedure, roadmap task, and future infrastructure change unless the project owner explicitly changes this file and the corresponding implementation through an accepted repository change.

## 1. DigitalOcean droplet — orchestration + lightweight editing, never project validation

The `skyforge-orchestrator` DigitalOcean Droplet exists to keep the low-usage event-driven controller available and to host the already-authenticated bounded Codex editing worker that the original orchestration pilot used successfully.

Allowed on the Droplet:

- receive and validate GitHub webhooks;
- maintain controller state, event journals, leases, queue/reconciliation state, and value telemetry;
- run Luna classification/orchestration logic;
- run one bounded Luna/Terra Codex worker in an isolated Git worktree to inspect repository state and create source/document/configuration edits;
- perform lightweight edit-support operations such as file reads/searches, git inspection, and `git diff --check`;
- inspect GitHub repository/PR/issue/Actions state through APIs;
- commit/push a bounded worker delta and create/update controller-managed GitHub PRs/comments/claims;
- run Caddy, systemd, health checks, Python dependency synchronization for the orchestrator itself, and minimal controller maintenance.

Forbidden on the Droplet:

- Gradle/`gradlew`, Java/`javac`, NeoForge/Minecraft builds or launches;
- project compilation, unit/integration/lifecycle tests, benchmarks, generators, performance suites, or other automated project evidence;
- provisioning or using a project JDK/toolchain, including legacy `hosted_jdk.py` / `with_hosted_jdk.py` behavior;
- treating a local worker's unverified output as passed machine evidence;
- running expensive project verification because GitHub Actions is slow, failing, quota-limited, or unavailable;
- subjective visual/listening/product verification.

The worker is an **editor**, not a validation runner. It may make a bounded implementation change and describe the checks that GitHub Actions must run. If correctness cannot be determined without executing a forbidden project command, the worker must leave that evidence to GitHub Actions rather than fabricating a result.

The systemd service must retain resource limits and Java/toolchain masks so a model mistake cannot monopolize the 2 GB host. A worker that violates resource bounds is disposable; durable controller state and GitHub remain authoritative.

## 2. GitHub — durable project truth and automated machine validation

GitHub is authoritative for project source, branches, PRs, issues, accepted evidence, and automated machine execution.

All worker edits become durable only after the deterministic controller commits/pushes them to GitHub. GitHub Actions owns automated validation, including:

- builds and compilation;
- unit, integration, lifecycle, determinism, persistence, performance, and regression tests;
- Gradle/Java/NeoForge machine verification;
- generated machine evidence that does not require subjective human judgment.

The normal autonomous path is therefore:

```text
GitHub event
    -> DigitalOcean deterministic controller
    -> Luna classifier
    -> bounded Codex editing worker in isolated worktree
    -> controller commit/push/draft PR
    -> GitHub Actions validation
    -> controller observes result and advances/repairs/gates
```

This path uses the Codex/ChatGPT authentication already installed on the orchestrator host. It does **not** require GitHub Copilot's cloud coding agent or `copilot-swe-agent[bot]` assignment.

Ordinary ChatGPT/manual producer agents may also create GitHub branches/PRs directly. The controller must not race a healthy external producer.

If GitHub Actions is unavailable, machine validation is `WAIT_CI` / infrastructure-blocked. Do not move builds/tests to DigitalOcean or Nicholas' workstation merely to keep the pipeline moving.

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
| Event orchestration/classification | DigitalOcean Droplet | repair controller; preserve durable state |
| Bounded autonomous source drafting/editing | DigitalOcean Codex worker or an explicitly active external producer | block/retry producer; do not invent a different execution surface |
| Automated project builds/tests/evidence | GitHub Actions | wait/block; never run them on Droplet/local as fallback |
| Human/manual/visual/listening verification | Nicholas' local workstation | leave human gate pending |

A task that crosses categories must split its evidence: the worker creates the bounded delta, GitHub Actions proves the machine-verifiable portion, and the local workstation completes any manual gate.

## 5. Orchestrator dispatch contract

The orchestrator may classify, prioritize, claim, and execute one bounded **editing** task. `DISPATCH` may create/resume one isolated Luna/Terra worker worktree on the Droplet, but that worker is prohibited from running project validation.

A valid autonomous dispatch therefore produces one of:

1. a bounded edit-only Codex worker handoff resulting in a controller-managed GitHub PR;
2. a precise handoff/hold for an already-running external producer; or
3. `WAIT_CI`, `HUMAN_GATE`, `NOOP`, or an explicit infrastructure block.

After the PR exists, automated evidence comes from GitHub Actions. Red CI may authorize another bounded edit-only repair worker, but never local Gradle/Java/NeoForge execution.

## 6. Hosted-JDK incident boundary

The September 12 hosted-JDK change crossed the original pilot's useful boundary by provisioning Java 25 on the 2 GB controller and instructing workers to run Gradle/NeoForge validation there. DR-20 then demonstrated the resulting resource failure mode.

That path is retired:

- hosted JDK bootstrap is not part of controller dependency sync;
- legacy ignored project toolchains are removed at service startup;
- the service sets an invalid `JAVA_HOME` and masks the legacy toolchain/system-JDK path;
- the service has CPU/memory/task limits;
- worker instructions explicitly prohibit project build/test/benchmark/generator execution;
- all machine validation returns to GitHub Actions.

The preserved branch `recovery/dr20-pre-boundary-20260914-034114` remains recovery/reference material for the interrupted DR-20 delta. It is not permission to restore hosted Gradle verification.

## 7. Agent startup rule

Every Skyforge agent must read this contract before choosing an execution surface. Older documentation that says the hosted worker should provision Java or run Gradle/NeoForge tests on DigitalOcean is superseded by this file.

When documentation, prompts, roadmap manifests, or retained controller state conflict with this contract, this contract wins until the stale text is repaired. In particular, a retained pre-boundary instruction mentioning `with_hosted_jdk.py` has no execution authority.
