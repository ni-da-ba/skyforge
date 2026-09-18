# Skyforge development-platform optimization roadmap

**Status:** Canonical post-migration optimization plan when merged  
**Baseline production platform:** Platform-v2 at `ef0f1b513b1fb421528c22012674e8642af2e644`  
**Date:** 2026-09-18 (America/Chicago)

## 1. Purpose

Platform-v2 migration is complete. This roadmap defines how Skyforge development should continue becoming faster, cheaper, safer, and less manually coordinated **while real Skyforge product work continues**.

The optimization program is not a second infrastructure migration. Its governing loop is:

```text
real Skyforge objective
    -> execute through Platform-v2
    -> observe measurable friction/failure
    -> generalize the platform fix
    -> verify against the real workload
    -> continue product development
```

Optimization must therefore be workload-driven. Do not suspend substantive Skyforge development for speculative platform polishing unless a platform deficiency blocks or materially slows the current product path.

## 2. Current baseline

The accepted production baseline already provides:

- one authoritative Platform-v2 writer and a disabled legacy rollback path;
- durable ingress, replay suppression, task authority, planning, classifier, admission, worker, handoff, completion, and provider accounting;
- bounded isolated workers with controller-owned Git/GitHub effects;
- exact-head CI/merge fencing and machine-enforced human gates;
- production rollback/cutover and protected-authority transfer;
- restart/reconciliation behavior and fail-safe `NONE` writer states;
- one accepted end-to-end production soak from signed task authority through auto-merge and durable cleanup.

The remaining problem is primarily **developer throughput and ergonomics**, not basic orchestration safety.

## 3. Optimization doctrine

Every optimization tranche should retire a concrete source of friction visible in actual work. Prefer measurements such as:

- human coordination turns per completed task;
- wall-clock time from objective to accepted merge/gate;
- classifier/worker calls per accepted task;
- context bytes/files delivered to workers;
- duplicate/retry/reclassification rate;
- percentage of tasks needing manual GitHub/operator intervention;
- time lost to repository reconstruction;
- number of unrelated objectives blocked by one human gate;
- CI/evidence cost per distinct risk retired;
- recovery time after controller/provider/remote-effect interruption.

A tranche is not justified merely because it is architecturally elegant. It should improve one or more of these quantities or eliminate a demonstrated correctness/operability risk.

## 4. End-state

The target human experience is:

```text
Nicholas states an objective in ordinary language
        |
        v
objective/intent compiler
        |
        v
authoritative context + bounded task graph
        |
        v
conflict-aware scheduler
        |
        +--> disposable workers / tools / simulations
        |
        +--> CI/evidence / GitHub lifecycle
        |
        +--> human gate only when human judgment is genuinely required
        v
accepted repository/game result + concise report
```

Representative commands should eventually be sufficient:

```text
Investigate why rivers are too frequent on small islands.
Fix weird waterfalls.
Develop the jungle music.
Continue settlement architecture.
Continue DR-70.
Continue Skyforge.
Show me the current blocker.
```

The system should infer the operational meaning of `investigate`, `fix`, `develop`, `continue`, and `show`, derive bounded task authority, gather context, schedule work, and stop at the correct authority boundary.

The optimization program reaches its planned final point when this objective-to-result loop is the normal workflow, multiple independent objectives can progress safely, the user rarely has to reason about agents/PR mechanics, and further improvements are ordinary metrics-driven maintenance rather than a dedicated platform program.

## 5. Ordered optimization phases

### OPT-0 — Production Platform-v2 baseline — COMPLETE

**Goal:** establish a durable production job system rather than controller/conversation memory.

Accepted by the production cutover, post-cutover soak, and authority-transfer bootstrap. Do not reopen this phase absent a concrete regression.

### OPT-1 — Natural objective intake / intent compiler — NEXT

**Goal:** remove hand-authored `NEW TASK` JSON from the user's normal workflow.

Build a deterministic/validated objective-intake layer that converts ordinary requests into an explicit candidate task specification. Initial intent classes:

- `INVESTIGATE` — research/diagnosis; no automatic code mutation;
- `FIX` — diagnose, implement, test, deliver;
- `DEVELOP` — design plus bounded implementation/evidence;
- `CONTINUE` — reconstruct current authoritative state and advance already-authorized work;
- `SHOW` — inspect/explain only.

Required properties:

- objective text itself is not mutation authority;
- candidate authority is visible/auditable and validated against repository ownership/contracts;
- ambiguity that changes product semantics stops at a human decision rather than being guessed;
- routine decomposition/issue creation/task authority can become controller-owned once policy permits.

**Exit:** normal bounded work can start from a plain-language objective without Nicholas manually constructing task JSON or GitHub comments.

### OPT-2 — Automatic context acquisition and task packaging

**Goal:** minimize reconstruction cost and worker context while improving task correctness.

For each admitted objective, derive a bounded context package from:

- current main and active ownership;
- compact project/lane state;
- relevant contracts;
- directly relevant source/test slices;
- accepted evidence and prior failures;
- exact stop boundary and allowed/protected paths.

Prefer retrieval/typed manifests over broad repository dumps. Record context-package identity so attempts are reproducible.

**Exit:** routine workers no longer perform broad repository archaeology; context size and reconstruction turns fall materially on real tasks.

### OPT-3 — Workload-driven DR-70 execution / platform feedback loop

**Goal:** make real Skyforge development the primary platform benchmark.

Resume DR-70 and subsequent product work through OPT-1/2 surfaces. Any general friction becomes a bounded platform repair, then execution returns immediately to the product task.

Do not delay DR-70 for unrelated platform polish. Human visual judgment remains human.

**Exit:** several substantive product tasks, including at least one machine implementation task and one human-gated path, complete through the optimized intake/context flow without bespoke orchestration.

### OPT-4 — Routine self-upgrade and operability

**Goal:** make Platform-v2 upgrades ordinary rather than bespoke operator events.

Compose accepted primitives into a supported upgrade workflow:

```text
preflight
 -> rollback to paused legacy / NONE as required
 -> transfer protected authority explicitly
 -> exact-main update
 -> reconciliation/quiescence proof
 -> regenerate activation evidence
 -> LEGACY -> NONE -> V2
 -> health/soak proof
```

Longer term, remove dependency on legacy as the ordinary upgrade bridge once an equally strong native V2 restart/upgrade fence exists. Preserve an emergency rollback path.

**Exit:** a normal accepted-main platform update needs one bounded operator action (or safely controller-managed equivalent), not custom scripts.

### OPT-5 — Multi-objective, conflict-aware scheduling

**Goal:** allow independent Skyforge work to progress concurrently without cross-authority corruption.

Add durable objective/task graph scheduling with:

- resource/ownership claims;
- path/subsystem conflict detection;
- dependency edges;
- concurrency limits/provider budgets;
- isolation for independent work;
- serialization for conflicting work;
- human-gated jobs that wait without blocking unrelated objectives.

Example:

```text
Hydrology repair -------\
Canopy repair ---------- scheduler -> bounded attempts
Jungle music -----------/
Settlement audit -------/
```

**Exit:** at least two independent real objectives progress concurrently, while a conflicting pair is correctly serialized and a human gate does not block unrelated work.

### OPT-6 — First-class `Continue Skyforge` program progression

**Goal:** make roadmap progression itself a durable platform service.

`Continue Skyforge` should:

1. reconstruct authoritative project state;
2. inspect active claims/PRs/human gates;
3. apply program/lane roadmap selection rules;
4. identify the smallest next eligible objective;
5. create bounded task authority/context;
6. execute until a meaningful stop boundary;
7. continue only while authority remains clear.

The platform must not invent new product semantics just to remain busy. When the next choice is genuinely strategic/aesthetic, surface the decision.

**Exit:** an idle but unblocked program can autonomously select and advance already-authorized next work without a manually authored task issue.

### OPT-7 — `sf` developer CLI

**Goal:** expose the platform through a stable local developer surface.

Likely commands:

```text
sf status
sf objective "Fix weird waterfalls"
sf investigate "river frequency on small islands"
sf continue [DR-70|skyforge]
sf tasks
sf gate
sf evidence <task>
sf show <artifact/specimen>
```

The CLI is an interface to the same durable authority/state; it must not create a second orchestration truth.

**Exit:** normal inspection/objective submission/operator status no longer requires direct GitHub state manipulation or bespoke shell commands.

### OPT-8 — Skyforge Studio / developer workspace

**Goal:** make the optimal workflow visible and low-friction rather than terminal/GitHub-centric.

Studio should expose:

- objective composer;
- current task graph/ownership;
- progress and evidence;
- relevant world/specimen previews;
- human visual/listening/play gates;
- concise decision requests;
- history/reproducibility links;
- platform health/budget without exposing internal noise by default.

GitHub remains accepted repository authority. Studio is a project-control and review surface, not a replacement VCS.

**Exit:** the user's normal Skyforge loop can remain in one project-control workspace except when a specialist tool (Minecraft, DAW, profiler, IDE) is genuinely useful.

### OPT-9 — Product-plane execution optimization

**Goal:** optimize world-generation execution without conflating authored semantics with runtime mechanics.

Preserve:

```text
semantic authoring
 -> versioned typed world/ProceduralGraph semantics
 -> normative reference evaluator
```

Add only when profiling justifies it:

```text
ProceduralGraph
 -> derived ExecutionPlan / DAG / field graph
 -> optimized Java/native/GPU evaluator
 -> backend realization
```

AI should increasingly choose **what** coherent authored world content should exist; deterministic systems should efficiently realize **how** it is evaluated and placed. Native/GPU work is profiling-driven, not an architectural fashion goal.

**Exit:** known generation bottlenecks meet practical development/runtime budgets with semantic equivalence against the reference path.

### OPT-10 — Optimization convergence / steady state

**Goal:** stop treating optimization as its own major program.

Declare the dedicated optimization roadmap converged when:

- plain-language objective intake is normal;
- context packaging is automatic and bounded;
- routine platform upgrades are supported;
- independent objectives can run concurrently with conflict serialization;
- `Continue Skyforge` can advance authorized roadmap work;
- CLI/Studio provide the normal developer surface;
- real product tasks demonstrate reliable objective-to-result execution;
- human intervention is concentrated at real strategy/aesthetic/play/listening gates;
- remaining performance work is identified by measurements/profiling, not platform incompleteness.

After OPT-10, platform changes are ordinary product engineering: fix measured regressions, improve bottlenecks, or add capabilities demanded by actual Skyforge work.

## 6. Sequencing rule

The roadmap order is directional, not a requirement to finish all platform work before product work. The preferred cadence is:

```text
OPT-1 objective intake
 -> use on DR-70
 -> OPT-2 context packaging where DR-70 demonstrates need
 -> continue DR-70
 -> OPT-4 routine upgrades as platform iteration itself becomes friction
 -> OPT-5 concurrency once multiple useful objectives exist
 -> OPT-6 Continue Skyforge
 -> OPT-7/8 interface convergence
 -> OPT-9 profiling-driven product execution work throughout when warranted
```

OPT-3 therefore overlaps later phases: real product development is the benchmark and forcing function.

## 7. Anti-patterns

Do not:

- rebuild a second controller architecture now that V2 is accepted;
- introduce distributed infrastructure merely to imitate a large production service;
- create concurrency before durable conflict/ownership semantics exist;
- let a UI become an alternate source of task authority;
- send whole-repository context to every worker;
- automatically retry uncertain provider calls in ways that can duplicate spend/effects;
- convert human visual/listening/gameplay gates into model self-approval;
- optimize native/GPU execution without profiling evidence;
- continue a platform tranche after its measured friction has been retired merely because more polishing is possible.

## 8. Current next action

Begin **OPT-1: Natural objective intake / intent compiler**, then immediately exercise it on real DR-70 work. The existing Platform-v2 typed task-authority path remains the safe backend while the human-facing front end is simplified.
