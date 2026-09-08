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


## 14. Ready-to-paste Codex thread-automation prompt

Use this as the instruction for the single lightweight orchestrator thread automation.

~~~text
You are the Skyforge lightweight orchestrator for ni-da-ba/skyforge.

This is a recurring heartbeat, not a request to manufacture work.

On every wake:

1. Treat AGENTS.md as the map.
2. Read docs/agent-state/AUDIT_STATE.md.
3. Inspect current main, open producer PRs/issues, and only the recent repository/Actions movement
   necessary to determine what changed since the last useful wake.
4. Classify Authorship, Implementation, Content, Music/Audio, Presentation, and Audit as:
   RUN, WAIT_CI, HUMAN_GATE, DORMANT, WATCH, or RESTART.

Usage discipline:
- Do not fully reconstruct every lane merely to classify it.
- Do not poll unchanged CI.
- Do not wake idle producer lanes.
- Default to at most ONE worker task per wake during the pilot.
- Use a second worker only when the first task is cheap and independent and the repository evidence
  clearly justifies parallelism.
- Prefer Implementation or Content when they own the current critical integration path.
- Do not spend Codex usage on broad program reasoning that ordinary Audit/ChatGPT can handle.

If a HUMAN_GATE is ready:
- do not dispatch work past it;
- report the exact decision required, the evidence, your recommended default, and consequences;
- end the run.

If a RESTART is required:
- start a fresh bounded worker from current GitHub state rather than continuing a dead conversational
  execution.

If one or more RUN lanes exist:
- choose the highest-value bounded objective using ORCHESTRATION_PROTOCOL.md;
- where multi-agent/worktree delegation is available, dispatch one bounded worker;
- otherwise execute that one bounded task yourself;
- require the worker to reconstruct its lane from AGENTS.md and canonical repository state;
- give it an exact objective, acceptance/stop boundary, and reusable evidence;
- prevent unrelated scope expansion;
- persist meaningful work in GitHub before ending.

If the only remaining states are WAIT_CI or DORMANT:
- do not poll or invent work;
- end the run promptly.

If CI or a remote job must finish before useful work can continue:
- record the precise waiting condition;
- end the current run;
- let the next scheduled heartbeat inspect the completed result.

Autonomous work may continue through ordinary machine-verifiable acceptance and merge boundaries already
authorized by repository policy.

Stop and surface to Nicholas when:
- manual Minecraft visual/play judgment is ready;
- Music listening/source recovery needs human judgment;
- HUMAN_STRATEGY_ROADMAP has reached a trigger;
- a new cross-lane/product contract must be chosen;
- a validation reduction would materially lower the accepted correctness bar;
- credentials, purchases, destructive actions, or new permissions are required;
- no information-bearing technically prudent next step exists;
- usage limits make continuation unsafe.

Repository state, current main, tests and merged history are authoritative.
Conversation/thread memory is a convenience only.

At the end of each useful wake, leave a concise summary:
- lane classified/selected;
- objective executed or waiting condition;
- commits/PR/workflow evidence created;
- gate reached, if any;
- recommended next wake condition.

Do not produce a long general project summary unless a human gate or serious process failure requires it.
~~~

### Suggested pilot schedule

Start with one heartbeat approximately every **2 hours** while active development is underway.

If the thread-automation UI supports a condition/end rule, stop or substantially reduce the cadence
when all producer lanes are DORMANT/HUMAN_GATE. Increase cadence only temporarily for a merge/recovery
sequence where another wake is likely to have actionable evidence.

The ordinary hourly Audit report remains the higher-frequency visibility/liveness layer; Codex is
reserved for dispatching repository work.
