# Pre-Bootstrap Development Platform Gate

**Status:** OWNER-APPROVED / DEFERRED UNTIL DR-70 HUMAN RE-REVIEW ACCEPTANCE  
**Decision date:** 2026-09-20 (America/Chicago)  
**Activation boundary:** after DR-70 machine evidence and human re-review are accepted; before Bootstrap Province becomes the primary program focus  
**Current authority:** persistence only. This document does **not** authorize work that displaces, mutates, pauses, rebases, or otherwise interferes with active DR-70 execution.

## 1. Purpose

Before Skyforge enters full Bootstrap Province production, harden the development workflow enough that Bootstrap benefits from the platform rather than becoming the workload on which basic coordination is still being invented.

The gate is deliberately bounded. It is not a requirement to finish the final Skyforge Studio, perfect every platform feature, or suspend all product work for an open-ended infrastructure program.

Target outcome:

```text
Nicholas states an objective or judgment
        |
        v
Skyforge development backend
        |
        +--> authoritative current state / significant-event history
        +--> bounded context + authority package
        +--> dependency/conflict-aware task graph
        +--> workers / GitHub / CI
        +--> artifact-bound human gates
        |
        +--> Operations Console
        +--> ChatGPT / MCP
        +--> sf CLI (when useful)
```

The clients may differ in presentation, but they must observe and mutate the same validated backend state.

## 2. Governing principles

### 2.1 One backend, multiple clients

The web console, ChatGPT/MCP, and later `sf` CLI / full Studio are clients of one development backend. They must not maintain independent authoritative interpretations of project state.

GitHub remains source/code/PR/CI authority. The development backend owns durable workflow concepts such as objectives, task authority, worker ownership, human gates, traces, and roadmap progression.

Conversation history is never the sole durable holder of project-significant truth.

### 2.2 Thin clients, typed domain operations

Expose project operations such as:

```text
get_project_status
list_objectives
get_objective
list_workers
list_human_gates
get_human_gate
list_artifacts
get_objective_trace
get_platform_health

submit_objective
submit_human_review
pause_objective
resume_objective
cancel_objective
retry_classified_failure
continue_skyforge
```

Do not expose raw shell execution, unrestricted database mutation, arbitrary Git operations, credentials, or an authority bypass merely because a client is trusted.

Every state-changing operation must pass through the same validation, idempotence, conflict, ownership, and audit machinery regardless of whether it originated from the console, ChatGPT, CLI, or controller.

### 2.3 Current state plus significant-event history

Prefer a practical hybrid rather than forcing the whole platform into pure event sourcing:

```text
authoritative structured current state
+
append-only project-significant event history
+
deterministic/reconciled read projections
```

Human decisions, authority changes, accepted/rejected gates, blockers, milestone transitions, material evidence, and corrections must be durable events.

### 2.4 Artifact-bound human judgment

A human review must identify the exact artifact/build/specimen being judged and, for a repeated gate, the material delta intended to answer the previous finding.

The console should make these reviews easy to perform. ChatGPT should be able to retrieve the same artifacts and gate record for discussion. Neither client may self-pass a subjective product gate.

### 2.5 Real-time console, current-state ChatGPT

The Operations Console should receive near-real-time backend updates through an appropriate subscription mechanism such as SSE/WebSocket or equivalent.

ChatGPT does not need to be a continuously streaming monitor. When asked, it should query the same live backend and reason from the current authoritative state.

### 2.6 Context before concurrency

Automatic bounded context/scope packaging is the highest-priority throughput optimization. Significant parallelism should not be enabled until lifecycle/ownership behavior has adversarial evidence.

## 3. Required pre-Bootstrap sequence

DR-70 remains untouched until its existing human re-review is accepted.

After that acceptance, execute this bounded workflow-convergence sequence:

```text
DR-70 ACCEPTED
    -> finish OPT-2 automatic context/scope packaging
    -> finish P0 X-1 project-event authority and X-4 delta-aware human gates
    -> OPT-4 routine platform upgrade / operability
    -> MVP shared development API + Operations Console + ChatGPT/MCP read surface
    -> X-6 lifecycle/adversarial hardening sufficient for concurrency
    -> OPT-5 basic multi-objective conflict-aware scheduling
    -> minimum viable OPT-6 Continue Skyforge progression
    -> PRE-BOOTSTRAP PLATFORM GATE ACCEPTED
    -> Bootstrap Province becomes PRIMARY_ACTIVE
```

The UI/API work may overlap other gate work once its backing state contracts are stable. Do not serialize independent work merely to preserve the textual order above.

Already-authorized bounded Bootstrap work may continue under the existing-work exception where repository authority permits it, but full Bootstrap Province must not become the primary convergence workload until this gate is accepted.

## 4. MVP Operations Console

The pre-Bootstrap UI is an operational console, not the final Skyforge Studio.

It should expose at minimum:

- current program phase, milestone, main SHA, and blockers;
- active / ready / blocked / human-gated / completed objectives;
- dependency and conflict relationships;
- worker state, ownership, branch/PR, and last meaningful progress;
- CI / verification state;
- human gates with exact artifacts, prior findings, and material deltas;
- artifact access for screenshots, renders, audio, reports, and builds where available;
- end-to-end objective trace;
- platform health / queue / retry state;
- compact throughput/cost metrics where already available.

Minimum controls:

- submit a natural-language objective;
- open and submit a human review;
- pause/resume/cancel an objective through validated domain operations;
- retry/reconcile a classified operational failure;
- invoke `Continue Skyforge` once OPT-6 is admitted.

The first implementation may be visually plain.

## 5. ChatGPT / MCP surface

ChatGPT should be able to inspect the same backend state visible in the console rather than reconstructing current execution from conversation history.

Initial priority is read access plus artifact retrieval. Typed write operations may be enabled only where the active client/runtime supports the required authorization and the backend preserves identical authority checks.

A representative interaction should be possible:

```text
Nicholas: What's happening with Skyforge?

ChatGPT:
    queries current project/objective/worker/gate state
    -> explains live blockers and dependencies

Nicholas: Show me the Guild Hall gate.

ChatGPT:
    retrieves the exact gate + artifacts + previous finding + material delta

Nicholas: Accept it, with this note.

ChatGPT:
    submit_human_review(...)
    -> durable typed project event
    -> backend reconciliation
    -> console reflects the same accepted result
```

No important project fact should need to be copied manually from ChatGPT back into the development backend.

## 6. Pre-Bootstrap acceptance demonstrations

The gate is not accepted from unit tests alone. It must complete representative real workflows.

### A. Ordinary implementation objective

A plain-language bounded objective must progress through:

```text
intent
-> authoritative scope/context
-> task admission
-> worker
-> tests
-> PR
-> CI
-> merge/completion
-> state reconciliation
```

without Nicholas manually preparing a handoff or repeatedly issuing `proceed`.

### B. Human-gated objective

A real artifact must progress through:

```text
worker/result
-> machine qualification
-> exact artifact + material delta
-> human review in the control surface
-> durable judgment event
-> dependent state resumes/reconciles
```

An unchanged artifact must not cause the same gate to be resurfaced.

### C. Concurrent objectives

At least two independent real objectives must progress concurrently without authority corruption, while a representative conflicting pair is correctly serialized.

A human-gated objective must be able to wait without blocking unrelated work.

## 7. Exit criteria

Bootstrap Province may become the primary product-convergence focus when all of the following are true:

- normal bounded work can start from ordinary-language objectives;
- routine workers receive automatically derived bounded context rather than broad repository archaeology;
- significant human/project decisions are durable and reconciled;
- repeated human gates require a persisted material delta;
- ordinary Platform-v2 upgrades/restarts have a supported bounded path;
- an Operations Console provides trustworthy current project/objective/worker/gate visibility;
- ChatGPT can query the same development backend and retrieve the same gate/artifact identities;
- lifecycle/adversarial coverage is strong enough for bounded multi-objective concurrency;
- two independent real objectives have demonstrated safe parallel progress;
- one conflicting pair has demonstrated correct serialization;
- a human gate has demonstrated that unrelated work continues;
- minimum viable `Continue Skyforge` can advance an already-authorized multi-step chain without repeated manual continuation prompts.

## 8. Explicit non-goals before Bootstrap

Do not require:

- final Skyforge Studio UX;
- integrated IDE or arbitrary terminal;
- custom Git/CI replacements;
- 3-D world rendering inside the console;
- universal agent framework abstractions;
- complete formal verification of every platform state;
- perfect provider/model routing;
- exhaustive dashboards/telemetry;
- a second production backend;
- speculative infrastructure without a measured workflow consumer.

The platform must remain smaller than the game.

## 9. Bootstrap transition

Once this gate is accepted, the workflow platform returns to a supporting role.

Bootstrap Province becomes the primary forcing function. Platform changes thereafter should normally be justified by measured friction, correctness failures, or capabilities required by real Bootstrap work.

The intended steady-state division of labor is:

> The console provides continuous situational awareness. ChatGPT provides reasoning and conversational control. The development backend provides shared authoritative workflow state. GitHub remains the code/PR/CI authority. Humans retain strategy, aesthetic, play, and listening judgment.
