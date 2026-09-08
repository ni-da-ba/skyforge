# Skyforge Lightweight Agent Orchestration Protocol

**Status:** Canonical operating protocol for low-usage autonomous orchestration  
**Goal:** maximize useful autonomous progress while minimizing repeated reconstruction, idle polling,
agentic-credit burn, and unnecessary parallel workers.

## 1. Operating model

Skyforge should not run one always-awake Codex worker per lane.

Preferred architecture:

```text
ordinary hourly Audit / human observation
             |
             v
       GitHub durable state
             |
             v
lightweight Codex orchestrator thread
       (scheduled heartbeat)
             |
      only when actionable
        /          \
       v            v
 bounded worker  bounded worker
 / worktree      / worktree
       \            /
        v          v
             GitHub
               |
               v
              CI
               |
       next orchestrator wake
```

The orchestrator is a **dispatcher and gate detector**, not the default implementation worker.

One orchestrator thread is preferred to four continuously scheduled lane threads because each scheduled
wake, reconstruction, tool call, and subagent consumes agentic usage.

## 2. Product capability assumptions

Codex thread automations may return to an existing thread on a schedule and preserve its thread
context. Where the Codex surface supports multi-agent/worktree delegation, the orchestrator may
delegate bounded worker tasks inside the run.

Do not assume a general ability to inject prompts into arbitrary existing ChatGPT conversations.
GitHub is the cross-execution handoff channel.

If the active Codex surface cannot spawn a separate worker, the orchestrator may execute **one**
highest-priority bounded task itself, then stop at the same gates defined below.

## 3. Progressive-disclosure read strategy

### Orchestrator fast path

On a normal wake, read only enough state to decide whether work should run:

1. `AGENTS.md` (normally injected automatically by Codex);
2. `docs/agent-state/AUDIT_STATE.md`;
3. current open PRs/issues and recent main movement relevant to active lanes.

Do **not** fully read all lane histories, contracts, source and tests merely to discover that every lane
is waiting.

### Worker deep path

Only after dispatching a lane does that worker perform the full lane reconstruction in `AGENTS.md`.

This deliberately moves expensive context acquisition from:

```text
every orchestrator wake x every lane
```

to:

```text
only the lane that actually has work
```

## 4. Lane states understood by the orchestrator

Classify each lane as one of:

- **RUN** — bounded, technically prudent work exists now.
- **WAIT_CI** — useful work is blocked on already-running evidence; do not poll in a loop.
- **HUMAN_GATE** — human judgment/strategy/permission is required.
- **DORMANT** — no retained consumer or next prudent milestone exists.
- **WATCH** — possible process/session issue; inspect liveness before dispatch.
- **RESTART** — prior producer execution is dead/stale; launch fresh reconstruction rather than
  continuing conversational history.

A lane may be healthy while DORMANT.

## 5. Wake algorithm

On each orchestrator heartbeat:

1. Read the fast-path state.
2. Identify changes since the previous useful wake:
   - merges;
   - new/updated producer PR heads;
   - newly completed/failed relevant CI;
   - Audit interventions;
   - human decisions now required.
3. Classify each lane.
4. If any **HUMAN_GATE** exists, report it and do not silently choose for the project owner.
5. If any **RESTART** exists, reconstruct a fresh worker from GitHub.
6. Select at most **two** RUN lanes in one wake; default to **one** when either task is expensive.
7. Give each selected worker one bounded objective and explicit stop conditions.
8. When the worker returns:
   - persist meaningful progress in GitHub;
   - merge only if its existing acceptance policy permits;
   - otherwise leave a precise PR/issue handoff.
9. If only WAIT_CI/DORMANT lanes remain, stop the run immediately.
10. Do not spend agentic usage repeatedly polling for the same CI state.

## 6. Dispatch priority

Default priority:

1. unblock a merge-ready or nearly accepted milestone;
2. repair a material regression/process blocker;
3. execute the next integration risk already authorized by roadmap/contracts;
4. narrow or checkpoint an overgrown mixed-scope branch;
5. producer-local cleanup needed for acceptance;
6. speculative or future work only when explicitly authorized.

Do not prioritize activity for its own sake.

## 7. Worker prompt contract

Each dispatched worker receives:

```text
You are the Skyforge [LANE] worker for ni-da-ba/skyforge.

Reconstruct from current GitHub state using AGENTS.md and the canonical lane documents.
Do not rely on prior conversational history.

Bounded objective:
[ONE OBJECTIVE]

Acceptance / stop boundary:
[EXACT ACCEPTANCE OR HANDOFF CONDITION]

Existing evidence that may be reused:
[PORTABLE EVIDENCE OR N/A]

Do not:
- expand into unrelated work;
- rerun expensive evidence without a new uncertainty;
- change another lane's accepted contract;
- continue past a human/strategy gate.

Persist useful state in GitHub before ending.
```

The orchestrator should fill these fields from repository evidence, not from memory.

## 8. Usage economy

### Hard defaults

- **One orchestrator automation**, not one scheduled automation per producer lane.
- **No idle worker wakeups.**
- **No CI polling loops.**
- **Maximum two worker dispatches per orchestrator wake.**
- Prefer one worker for expensive Minecraft/NeoForge characterization.
- Reuse a live thread when it remains coherent; reconstruct from GitHub when stale.
- Use ordinary ChatGPT/Audit for broad reasoning, reporting, and human decision preparation.
- Reserve Codex worker execution for concrete repository work where its coding harness creates leverage.

### Cadence

Recommended starting cadence:

- active development: roughly every **2 hours**;
- known short CI wait: next useful wake after the expected evidence window rather than frequent polling;
- all lanes dormant/human-gated: reduce cadence substantially or pause;
- urgent merge/recovery: temporary shorter cadence only while it is actually information-bearing.

Codex thread automations may be configured to stop once the specified condition/gate is reached.
Cadence should be increased only when the expected value of another wake exceeds its usage cost.

## 9. Liveness

A visually frozen producer plus continuing information-bearing GitHub/Actions movement is ACTIVE.

A visually frozen producer plus repository/Actions silence is WATCH; if silence persists across the
next reasonable observation interval, classify RESTART.

Unchanged reruns, conflict churn and bookkeeping-only motion do not establish healthy progress.

A replacement worker starts from GitHub; it does not attempt to recover the dead conversation.

## 10. Gate conditions that terminate autonomous continuation

The orchestrator must stop and notify the project owner when:

- a manual Minecraft visual/play gate is ready;
- a Music listening/source-recovery gate requires human judgment;
- a HUMAN_STRATEGY_ROADMAP trigger is met;
- two lanes require a new contract that current policy does not resolve;
- a proposed validation reduction would materially lower the accepted correctness bar;
- credentials, external approvals, purchases, or destructive operations are required;
- the only remaining work is speculative product direction;
- usage limits prevent a safe continuation.

At a gate, provide:
- what changed;
- the exact decision required;
- recommended default;
- consequences of the alternatives.

## 11. Merge policy

Autonomy should aim for **short-lived coherent PRs**.

An orchestrated worker may merge when:

- lane policy already authorizes autonomous merge;
- exact required machine evidence is green;
- no human gate remains;
- no unresolved relevant contract drift exists;
- the PR head/evidence is current or portable under `VALIDATION_POLICY.md`.

Do not hold independently useful generic fixes on a long branch solely because an unrelated human or
sampled-failure gate remains.

## 12. Human role

The project owner remains responsible for:

- product direction;
- qualitative world/terrain/game/audio judgment;
- exceptional cross-lane tradeoffs;
- approval/permission gates;
- deciding when to spend additional agentic credits.

The orchestrator should convert routine supervision from:

```text
continue?
are you stuck?
merge and proceed
what happened?
```

into:

```text
repository changed -> bounded worker runs
CI changed        -> next useful action runs
human gate        -> owner is asked once
no work           -> system stays quiet
```

## 13. Initial rollout

Do not immediately move every Skyforge lane to autonomous Codex execution.

Pilot:

1. install this repository harness;
2. create **one Codex orchestrator thread automation**;
3. allow it to dispatch at most one Implementation or Content task per wake initially;
4. keep hourly Audit reporting;
5. compare for several milestones:
   - manual prompts/restarts required;
   - agentic usage consumed;
   - median time from actionable state to next commit/PR;
   - redundant CI/runtime runs;
   - dead-session recovery latency;
6. expand to two-worker dispatch only if the usage/progress ratio is favorable.

Success means fewer manual continuation/restart prompts and faster accepted milestones **without**
increasing regressions or exhausting the shared agentic allowance.
