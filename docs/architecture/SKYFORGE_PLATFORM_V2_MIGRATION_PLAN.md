# Skyforge Platform v2 Migration Plan

**Status:** Proposed durable migration authority; migration must not begin until the preservation gate in the companion backup/rollback runbook is satisfied.  
**Baseline main at plan creation:** `4ec14d54684ef82f48a8df4582180b89c3a87aaa`  
**Date:** 2026-09-17 (America/Chicago)  
**Companion runbook:** `docs/operations/SKYFORGE_PLATFORM_V2_BACKUP_AND_ROLLBACK.md`

## 1. Purpose

Skyforge has reached the point where the existing procedural-world engine architecture is substantially sound, while the development control plane has accumulated enough runtime layering, compatibility patching, durable state, recovery logic, and parallel authority that further accretion creates avoidable long-term risk.

This migration therefore does **not** replace the accepted Skyforge world engine. It replaces and hardens the orchestration/control-plane architecture around that engine, then improves developer-facing tooling so routine Skyforge work becomes repository-first, task-bounded, disposable-agent driven, and increasingly Studio/CLI centered.

The migration must preserve accepted project truth, current Java engine semantics, deterministic procedural identity, Git history, human gates, GitHub Actions evidence, NeoForge realization, and existing accepted world behavior unless a later independently authorized product task intentionally changes them.

## 2. Core architectural conclusion

The following existing product architecture remains authoritative and should be preserved:

```text
semantic descriptors
    -> versioned recipes
    -> canonical immutable ProceduralGraph
    -> normative ReferenceEvaluator
    -> backend-neutral world composition
    -> NeoForge/Minecraft realization
```

Future optimization may add a derived `ExecutionPlan`, optimized Java evaluator, native evaluator, or GPU evaluator, but `ProceduralGraph` and the normative reference path remain the semantic oracle until a separately authorized architecture decision changes that rule.

The migration target is therefore:

```text
PRODUCT PLANE
semantic model -> recipes -> ProceduralGraph -> reference/optimized evaluators -> world -> backend

CONTROL PLANE
GitHub events -> durable inbox -> deterministic state reducer -> durable state/effects ->
GitHub/Codex/workspace adapters -> CI evidence -> human gate or exact-SHA merge

DEVELOPER PLANE
sf CLI -> Skyforge Studio -> Minecraft only when integration/experience review is required
```

## 3. Non-goals

This migration must not be used as justification to:

- rewrite the Java engine in Rust;
- rewrite the NeoForge backend;
- split the monorepo;
- introduce Kubernetes, Redis, a managed database, a message broker, or microservices;
- change AI provider during control-plane cutover;
- move automated Java/Gradle/NeoForge verification onto the DigitalOcean host;
- replace GitHub as accepted project authority;
- replace explicit human visual/product gates with machine scoring;
- alter accepted procedural output merely to simplify the migration;
- delete or rewrite accepted Git history;
- combine controller refactor, persistence migration, provider migration, and engine-language migration into one cutover.

## 4. Non-negotiable safety invariants

The migration may advance only while all of these remain true:

1. Exactly one controller may have repository-mutating authority at any time.
2. No worker or model receives direct merge authority.
3. Every remote side effect has a durable idempotency identity.
4. A PR may merge only when current PR head SHA, required CI/evidence SHA, reviewed/accepted SHA, and task-spec identity agree.
5. An active task attempt cannot silently change meaning because a roadmap or task specification changes.
6. Human gates cannot be machine-passed.
7. A network-isolated worker cannot fabricate required external evidence.
8. Platform migration work must not change canonical procedural behavior unless separately authorized.
9. Old and new controllers must be replay-compared before v2 receives mutation authority.
10. Crash/restart at every remote-effect boundary must converge to exactly-once logical behavior after reconciliation.
11. Rollback must not require reconstruction from ChatGPT conversations.
12. Repository, tests, accepted evidence, current main, and durable task authority remain superior to conversational recollection.
13. The migration must preserve a known-good pre-migration repository snapshot and independently restorable Git backup before the first mutating v2 canary.

## 5. Target control-plane architecture

The v2 controller should use explicit composition, not import-time monkey-patching.

Suggested layout:

```text
scripts/orchestrator/v2/
  domain/
    event.py
    task.py
    attempt.py
    transition.py
    evidence.py
  policies/
    event_filter.py
    routing.py
    roadmap.py
    retry.py
    quota.py
    human_gate.py
    merge.py
    validation.py
  ports/
    github.py
    agent.py
    state_store.py
    clock.py
    workspace.py
  adapters/
    github_cli.py
    codex.py
    json_state_store.py
    sqlite_store.py
    filesystem_workspace.py
  services/
    reconciler.py
    dispatcher.py
    worker_service.py
    handoff.py
    merge_service.py
  runtime/
    shadow.py
    hosted.py
  cli.py
```

The controller core should be reducible to:

```text
current durable state + durable event -> deterministic transition plan
```

The transition plan may emit durable effects. Network actions occur only through adapters after the transition/effect record is durable.

No import should change controller behavior. No runtime extension should replace class methods or module helpers after definition. Existing behavior must be migrated into named policies/services with regression coverage.

## 6. Durable task model

The current machine roadmap is retained conceptually and evolved into versioned task specifications.

Each bounded task should carry at minimum:

```yaml
spec_version: 2
id: <stable task id>
lane: <role>
authority:
  issue: <number>
base_policy: current-main-at-attempt-start
contracts: []
scope:
  allowed_paths: []
  forbidden_paths: []
acceptance:
  required_evidence: []
  human_gate: null
execution:
  worker_capability: <tier/capability>
  external_network_required: false
  max_attempts: 1
stop_boundary: <text>
```

A canonical `spec_hash` is computed from the normalized task specification. When an attempt begins, its exact task spec, base SHA, and spec hash are frozen. Later roadmap edits can create a new task revision but cannot mutate the meaning of an in-flight attempt.

Every attempt receives a stable `attempt_id` and records:

- task/spec hash;
- base main SHA;
- worker identity/provider/tier;
- produced commit/head SHA;
- PR identity;
- evidence identities and exact SHAs;
- human-gate state;
- terminal outcome.

## 7. Durable effects and idempotency

Remote actions must become first-class durable effects. Example effect identities:

```text
dispatch-worker:<attempt-id>
create-pr:<attempt-id>
post-human-gate:<task-id>:<spec-hash>
merge-pr:<pr-number>:<expected-head-sha>
close-issue:<issue-number>:<task-id>
```

Before a remote effect is executed, its intent is durably recorded. After completion, its remote identity/result is recorded. If the process crashes in between, reconciliation determines whether the remote effect already occurred and completes state without duplicating the logical action.

## 8. Writer fencing

Before v2 shadow/cutover work, add controller writer fencing to the current hosted architecture.

Required properties:

- exclusive operating-system/process lock on the active host;
- durable `controller_instance_uuid`;
- monotonic or otherwise unambiguous fence generation/epoch;
- all mutating controller operations require ownership of the active fence;
- a second process or restored/replacement host must fail closed rather than both writing;
- shadow instances are explicitly read-only and must not acquire mutation authority.

A migration cutover is invalid unless the old writer is visibly disabled/revoked before the new writer obtains the mutation fence.

## 9. Exact-SHA acceptance fencing

Every merge-eligible task should expose one explicit predicate equivalent to:

```text
current_pr_head_sha
  == reviewed_or_accepted_sha
  == all_required_machine_evidence_sha

AND task_spec_hash == accepted_task_spec_hash
AND no unresolved human/product gate
AND branch protection is verifiably active
AND controller owns the task attempt
AND no superseding authority exists
```

If the PR head changes, stale evidence is invalidated according to dependency/evidence portability policy rather than being implicitly trusted.

## 10. Persistence strategy

### Initial migration

Retain the current JSON state store while v2 behavior is built and replay-compared. Do not combine semantic refactor with database migration.

### Later migration

After v2 control-plane semantics are accepted, migrate the state-store adapter to SQLite on the existing host.

SQLite target concepts:

```text
controller_meta
events
tasks
task_attempts
worker_runs
managed_prs
evidence
human_gates
roadmap_runs
effects_outbox
provider_usage
```

Use transactions, foreign keys, uniqueness constraints, and WAL mode where appropriate. SQLite remains local to the single authoritative controller; no managed database service is required.

## 11. Agent/provider architecture

Persistent lane conversations cease to be operational authority. Roles remain, but workers are ephemeral.

Retain conceptual lanes such as Authorship, Implementation, Content, Music/Audio, and Audit as versioned role policies. The orchestrator assembles a bounded context package containing only:

- exact task specification;
- exact base SHA;
- role policy;
- relevant contracts;
- relevant source/dependency slice;
- relevant tests/evidence;
- known prior failure/blocker information;
- stop boundary.

Codex remains the initial provider during migration. Introduce a provider port/interface but do not change provider as part of cutover. Alternative providers may be evaluated later against a Skyforge-specific task benchmark.

The controller, not the model, owns authority, Git/GitHub mutation, task state, merge decisions, and evidence state.

## 12. Evidence/CI architecture

Retain the current evidence-economy policy: exhaustive cheap deterministic evidence, representative expensive evidence, automatic widening on unexpected failures, and explicit human gates for qualitative claims.

Over time, encode evidence classes in machine-readable proof specifications and consolidate historical bespoke workflows into reusable workflows where permission, runner, and trigger semantics permit.

A task attempt should ultimately expose a machine-readable evidence manifest containing at minimum:

```text
task id
attempt id
spec hash
base SHA
result/head SHA
evidence type -> status + SHA + artifact identity
human-gate state
merge eligibility + reason
```

Existing accepted historical evidence must not be discarded merely to normalize workflow structure.

## 13. PR lifecycle classes

Normalize open PRs into explicit lifecycle classes:

- `Delivery` — expected to converge into current main; orchestrator may own lifecycle.
- `HumanGate` — machine work complete; awaiting explicit human judgment.
- `Prototype` — experimental/research branch; never treated as delivery authority.
- `Archive` — retained historical proof/snapshot; excluded from ordinary orchestration.

This prevents long-lived proof branches from being interpreted as unfinished production work.

## 14. Developer experience target

The long-term human-facing workflow should center on:

```text
Skyforge Studio + one project-control conversation/dashboard
```

VS Code, GitHub pages, terminal, and Minecraft remain available but become task-specific tools instead of required coordination surfaces.

The developer-facing tool chain should evolve in this order:

```text
existing skyforge-reference capability
    -> stable inspection API
    -> `sf` CLI
    -> Skyforge Studio GUI
```

Initial CLI targets:

```text
sf compile
sf render
sf inspect
sf compare
sf explain
sf validate
sf bench
sf launch
```

Studio is a client of these stable capabilities, not a new source of project truth.

Studio should eventually support procedural field/volume visualization, seed comparison, before/after/difference views, graph inspection, semantic provenance, point explanation, hydrology/geology/ecology/cave overlays, candidate review, evidence state, and bounded human-gate submission.

Minecraft remains the integration/experience authority for questions such as flight/traversal feel, in-game visual coherence, native feature compatibility, gameplay, persistence, and multiplayer/runtime behavior.

## 15. Migration releases and gates

### Release 0 — Preservation and baseline

Before any mutating v2 work:

- execute the companion backup/rollback runbook;
- capture immutable/independent repository backups;
- record baseline main SHA;
- record active issue/PR/task/controller ownership;
- record current orchestrator state backup;
- preserve current deployment configuration without secrets in repository-safe form;
- prove at least one restore path.

**Exit:** preservation gate signed off; no migration work proceeds otherwise.

### Release 1 — Safety foundation

Implement independently mergeable tranches for:

- controller writer fence;
- explicit controller instance/fence telemetry;
- durable task/spec/attempt identity;
- exact-SHA acceptance predicate;
- PR lifecycle classification;
- replay/failure corpus harness.

The existing controller remains production authority.

**Exit:** second writer cannot mutate; representative failures can be replayed; old system is safer than pre-migration baseline.

### Release 2 — Orchestrator v2 pure core

Build v2 domain/policies/ports with no production mutation authority.

Migrate existing control-plane semantics into explicit policies/services. Use the existing state store adapter initially.

**Exit:** representative historical/replay corpus yields equivalent legal transitions or all intentional differences are documented and accepted.

### Release 3 — Live shadow

Run v2 against live repository/controller events in read-only shadow mode.

Shadow may observe and predict but may not dispatch workers, push, create/modify PRs, comment, merge, close issues, or mutate authoritative state.

Compare old-controller decisions and v2 transition plans. Record disagreements as migration defects or intentional policy changes requiring explicit review.

**Exit:** sustained agreement across ordinary activity, recovery cases, human gates, manual/external producer transitions, and roadmap progression.

### Release 4 — Canary mutation authority

Transfer a narrowly scoped low-risk task class (prefer documentation/state-only work) to v2.

Old controller remains disabled for that authority and remains available only as rollback software. Writer fencing prevents dual ownership.

Exercise restart, GitHub outage/reconciliation, stale event, and head movement during canary.

**Exit:** complete task lifecycle through exact-SHA acceptance/merge or human gate with no authority ambiguity and successful recovery drills.

### Release 5 — Full control-plane cutover

Transfer ordinary task ownership to v2. Disable old controller mutation authority. Retain old implementation and compatible snapshot for rollback until v2 has passed a sustained operational acceptance period.

**Exit:** v2 owns normal orchestration; no monkey-patch runtime layer is required for production behavior.

### Release 6 — SQLite state-store migration

Migrate storage behind the already-stable v2 state-store port.

Perform import from current durable state, dual verification/read checks as appropriate, backup/restore test, and replay equivalence before making SQLite authoritative.

**Exit:** transactionally durable state and effects; JSON export remains available for inspection/recovery.

### Release 7 — Task/evidence normalization

Promote typed task specs, generated context packages, machine-readable evidence manifests, and reusable workflow/proof specifications.

Reduce lane-state documents to concise human-readable status/rationale views rather than operational state that must be manually reconciled.

**Exit:** disposable worker can become operational from task/context package plus repository, without persistent chat history.

### Release 8 — Developer tooling

Create stable `sf` inspection/authoring CLI over accepted engine/reference capabilities. Build Skyforge Studio on that stable interface.

**Exit:** most Authorship/procedural inspection can be performed without booting Minecraft; Minecraft remains required for actual backend/game experience review.

### Release 9 — Optional evaluator optimization

Only after profiling demonstrates material value, add derived graph optimization/ExecutionPlan and optional native/GPU evaluators behind differential conformance to `ReferenceEvaluator`.

This is not required for Platform v2 acceptance.

## 16. Required replay/failure corpus

Before v2 receives broad mutation authority, explicitly cover at minimum:

- duplicate webhook;
- out-of-order webhook;
- missed webhook followed by reconciliation;
- restart after classification but before dispatch;
- restart during worker;
- worker timeout/failure;
- provider quota/capacity exhaustion;
- push succeeds but local state update fails;
- PR creation succeeds but local state update fails;
- comment succeeds but local state update fails;
- CI evidence becomes stale because PR head moves;
- merge attempt against changed head;
- human gate active;
- task/roadmap spec changes during active attempt;
- issue closes outside controller;
- PR merges outside controller;
- PR closes without merge;
- GitHub temporarily unavailable;
- stale local state after host restore;
- two controllers attempt ownership;
- old trusted control/task comment replayed against new state;
- external/manual producer becomes active;
- worker edits protected path;
- worker requires unavailable external evidence;
- malformed/corrupt local state;
- pending remote effect discovered after restart.

## 17. Rollback policy

Rollback is a first-class supported transition, not an emergency improvisation.

Each release must define its rollback boundary before it receives production authority.

General rule:

```text
new controller fails
    -> revoke/stop new writer
    -> preserve diagnostic state
    -> restore/verify old compatible state snapshot if needed
    -> acquire fence with old controller
    -> reconcile GitHub current truth
    -> resume old controller
```

Never run old and new controllers concurrently with write authority.

Accepted Git commits/PR merges performed by v2 remain accepted repository history unless independently reverted for product reasons; rollback changes controller software/state ownership, not Git history.

## 18. Infrastructure and cost expectation

Required infrastructure remains:

- existing GitHub repository;
- existing GitHub Actions validation;
- existing DigitalOcean controller host;
- Caddy/systemd/firewall boundary;
- current Codex provider initially;
- local Windows workstation for Studio/Minecraft/manual review.

New required recurring infrastructure cost should be approximately zero. SQLite and Studio are local software. A temporary second staging Droplet may be used for clean-deployment/failover drills but is optional and should be destroyed after use. No managed database, Redis, Kubernetes, persistent second controller, or cloud GPU is required for Platform v2.

## 19. Expected benefits at completion

### Reliability

- single-writer enforcement;
- exact-SHA merge fencing;
- transactional/idempotent remote effects;
- replayable controller decisions;
- explicit task revision/attempt identity;
- simpler restart/recovery semantics;
- fewer import-order/control-plane coupling risks.

### Development velocity

- disposable workers receive generated bounded context instead of reconstructing broad project history;
- less repeated reading and chat-context dependence;
- standardized evidence requirements;
- fewer bespoke CI/workflow implementations;
- Studio/CLI reduce Minecraft launches for procedural debugging.

### Human workload

Nicholas should spend less time on:

- lane/agent handoffs;
- repeated `merge and proceed` coordination;
- transferring conclusions between conversations;
- determining whether agents know current state;
- manually locating machine evidence.

Human attention should increasingly concentrate on product direction, visual/experiential judgment, tradeoffs, and explicit gates.

### Maintainability

The control plane becomes ordinary explicit software architecture rather than a large core plus chained runtime modifications. AI providers, future backends, Studio, and additional task roles can then be introduced behind stable interfaces without changing controller authority semantics.

## 20. Start condition

Migration safety work may begin as soon as this plan and the backup/rollback runbook are accepted on `main` and the Release 0 preservation gate is completed.

Product development does not need to stop. Release 1 safety work may proceed alongside a human/product gate because it must not alter world-generation semantics.

Do not transfer real mutation authority to v2 while an unrelated urgent product repair is simultaneously changing the same control-plane contracts. Prefer a clean product boundary for shadow/canary/cutover.

## 21. Completion definition

Platform v2 is complete when:

- v2 is the sole production orchestrator;
- runtime monkey-patching is no longer required for production control behavior;
- writer fencing and exact-SHA acceptance are active;
- durable effects are idempotent/reconcilable;
- task attempts are versioned and frozen by spec hash/base SHA;
- restart/recovery/failure corpus is green;
- persistent conversation history is unnecessary for ordinary task execution;
- evidence state is machine-readable;
- rollback has been demonstrated;
- accepted Java engine/NeoForge behavior remains preserved;
- `sf`/Studio work can proceed as a separate developer-experience evolution without reopening control-plane safety.

This document is migration architecture authority, not authorization to bypass individual task, evidence, human-gate, or branch-protection requirements.