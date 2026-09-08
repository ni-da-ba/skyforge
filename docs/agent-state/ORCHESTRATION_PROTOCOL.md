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

- **RUN** — bounded, technically prudent work exists now and no healthy producer is already executing it.
- **RUNNING_EXTERNAL** — an ordinary ChatGPT/manual/Codex producer is already making
  information-bearing repository or Actions progress; do not dispatch a duplicate worker.
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
3. Classify each lane. Recent information-bearing commits, PR updates, or progressing Actions from an
   existing producer classify the lane as **RUNNING_EXTERNAL** and suppress duplicate dispatch.
4. If any **HUMAN_GATE** exists, report it and do not silently choose for the project owner.
5. If any **RESTART** exists, reconstruct a fresh worker from GitHub.
6. Select at most **two** RUN lanes in one wake; default to **one** when either task is expensive.
7. Give each selected worker one bounded objective and explicit stop conditions.
8. When the worker returns:
   - persist meaningful progress in GitHub;
   - merge only if its existing acceptance policy permits;
   - otherwise leave a precise PR/issue handoff.
9. If only RUNNING_EXTERNAL/WAIT_CI/DORMANT lanes remain, stop the run immediately.
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
- Use the lowest-capability/cost model that can reliably perform the role:
  - **Luna** for the orchestration heartbeat, classification, lightweight GitHub inspection, and prompt construction;
  - **Terra** for routine bounded implementation/recomposition/testing work;
  - **Sol** only when the task demonstrates a need for frontier reasoning, difficult debugging, architecture,
    or a cheaper worker has failed to make information-bearing progress.
- Where the active Codex surface does not support model routing for delegated work, apply the same rule
  by choosing the appropriate model for the thread/task before dispatch rather than assuming Sol everywhere.
- **No idle worker wakeups.**
- **No CI polling loops.**
- **Maximum two worker dispatches per orchestrator wake.**
- Prefer one worker for expensive Minecraft/NeoForge characterization.
- Reuse a live thread when it remains coherent; reconstruct from GitHub when stale.
- Use ordinary ChatGPT/Audit for broad reasoning, reporting, and human decision preparation.
- Reserve Codex worker execution for concrete repository work where its coding harness creates leverage.

### Cadence

**Preferred pilot:** event-driven local dispatch under Section 15. In that mode, there is no periodic
Codex heartbeat while the controller is running; filtered GitHub events wake the persistent Luna
classifier only when repository state may be actionable.

**Fallback:** if the local event receiver is unavailable, use a single Codex heartbeat roughly every
two hours during active development. Reduce/pause it when lanes are dormant/human-gated.

Do not combine a two-hour Codex heartbeat with the event-driven controller unless deliberately testing
fallback behavior; that would pay twice for the same orchestration.

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
2. run the event-driven local SDK/App-Server pilot in Section 15; use the two-hour thread heartbeat
   only as fallback when that receiver is unavailable;
3. run the orchestration classifier on Luna at low effort where available;
4. allow at most one bounded worker dispatch per event batch initially, preferring Terra for routine
   work and escalating to Sol only when justified;
5. keep hourly Audit reporting as an independent liveness/negative-space supervisor;
6. compare for several milestones:
   - manual prompts/restarts required;
   - agentic usage consumed;
   - median time from actionable state to next commit/PR;
   - redundant CI/runtime runs;
   - dead-session recovery latency;
7. expand to two-worker dispatch only if the usage/progress ratio is favorable.

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
   RUN, RUNNING_EXTERNAL, WAIT_CI, HUMAN_GATE, DORMANT, WATCH, or RESTART.
   If a healthy ordinary ChatGPT/manual producer is already producing information-bearing commits,
   PR changes, or Actions movement for a lane, classify RUNNING_EXTERNAL and DO NOT dispatch a
   competing Codex worker.

Usage discipline:
- Run this orchestrator on Luna/low-cost settings where available.
- Prefer Terra for routine bounded worker execution.
- Escalate to Sol only for genuinely difficult reasoning/debugging/architecture or after a cheaper
  worker fails to produce information-bearing progress.
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

If the only remaining states are RUNNING_EXTERNAL, WAIT_CI, or DORMANT:
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

### Suggested fallback schedule

Use the heartbeat prompt only when the event-driven local controller is unavailable. In that fallback
mode, start around every **2 hours** during active development and pause/reduce it when all producer
lanes are DORMANT/HUMAN_GATE.

The ordinary hourly Audit report remains the visibility/liveness layer; Codex is reserved for
dispatching repository work.


## 15. Event-driven local SDK/App-Server pilot

### Rationale

The two-hour heartbeat is intentionally conservative but still spends a Codex turn to discover that
nothing changed. The preferred pilot is event-driven:

~~~text
GitHub event
    -> deterministic local filter/debounce
    -> no call if irrelevant
    -> persistent Luna classifier if potentially actionable
    -> fresh bounded Terra worker only for DISPATCH
    -> controller-owned git/gh handoff
    -> CI
    -> next completion event
~~~

OpenAI's Python Codex SDK is the automation surface. It controls the local Codex runtime/App Server
and exposes explicit thread start/resume. Skyforge therefore does not implement App Server JSON-RPC
directly.

### Security and authority split

The model is not given unattended GitHub/network authority in the pilot.

- Luna: read-only local classifier.
- Terra: workspace-write local edits/tests for one bounded objective.
- outer deterministic controller: fetch/checkout/commit/non-force-push/draft-PR operations.

This avoids depending on sandbox-network behavior and creates a narrow audit boundary around external
writes.

Auto-merge is disabled initially. If later enabled, it applies only to PRs recorded as
controller-managed in ignored local state and only after visible checks are terminal/green. No force
push, history rewrite, branch deletion, secret management, repository administration, purchase, or
credential action belongs in autonomous pilot scope.

### Event allowlist

Potential wakes:

- push to `main`;
- internal-repository PR closed/reopened/ready-for-review/draft transition;
- internal-repository workflow-run completion after all runs for the exact head are quiescent;
- Audit/restart/loop-risk/human-gate comments from an explicitly trusted GitHub actor;
- manual `/skyforge-orchestrate` from an explicitly trusted GitHub actor.

The default trusted issue-comment actor is `ni-da-ba`; hosted configuration may enumerate additional
actors explicitly. A valid GitHub webhook signature authenticates GitHub as the sender, not the human
authority behind a comment. Fork/external PR and workflow payloads are therefore treated as untrusted
input and filtered before Codex startup.

Ignored before Codex startup:

- non-main pushes;
- PR synchronize;
- ordinary comments;
- PR opened before first CI completion;
- non-completed workflow notifications;
- controller-authored comments;
- orchestration/Audit command text from untrusted commenters;
- fork/external PR and workflow payloads.

Trusted `/skyforge-pause` and `/skyforge-resume` comments are deterministic control commands, not
Codex wakes. Pause persists across restart, retains newly actionable events in the durable journal,
and starts no new classifier/worker dispatch. Resume schedules the retained batch. A worker already in
its bounded handoff is not destructively interrupted merely because pause arrived.

Events are debounced and subject to a minimum dispatch interval.

### Watchdog relationship

The event-driven controller and hourly Audit watchdog are deliberately **not replacements for one
another**.

~~~text
positive activity / state change
    -> webhook controller

negative space / silence / dead producer
    -> hourly Audit watchdog
~~~

A webhook cannot fire because a producer stopped doing anything. The watchdog therefore retains
liveness detection, evidence-saturation supervision, hourly human-facing summaries, and human-gate
escalation.

When Audit posts a material GitHub comment such as RESTART RECOMMENDED or LOOP RISK, that comment
becomes an actionable webhook and can wake Codex immediately. The controller preserves the trusted
directive text, signal kind, source comment identity, and signal timestamp as structured classifier
evidence rather than collapsing it to a generic wake. Thus Audit diagnoses; the event controller may
execute the bounded recovery.

For `RESTART RECOMMENDED`, Audit has already adjudicated the prior producer stale/dead at the signal
time. The classifier must not reinterpret PR/issue `updatedAt`, draft/open state, the Audit comment
itself, bookkeeping-only motion, or unchanged reruns as producer recovery. NOOP is valid only if
information-bearing evidence strictly after the signal proves recovery (for example a new producer
head/commit, genuinely attributable Actions progress, or an already controller-managed replacement).
Otherwise the bounded fresh-worker objective is dispatched. This prevents the watchdog comment that
declares a producer stale from accidentally making that same producer look recently active.

The controller additionally guards trusted restart NOOPs against immutable target state. If the target
PR remains open at the signal-time head, the first NOOP receives one constrained reclassification that
must choose bounded DISPATCH or a real HUMAN_GATE. A second unsupported NOOP becomes a HUMAN_GATE
instead of clearing the durable restart event as if recovery had been proven. Classifier-policy changes
rotate the persistent Luna parent thread automatically so superseded liveness policy cannot survive a
control-plane deployment as conversational inertia.

If Codex reaches a human gate, it posts a controller-marked GitHub gate comment and stops. The
controller ignores its own comment to prevent recursive wakeups; Audit remains responsible for
bringing the gate to the project owner.

### Local and hosted transports

The controller implementation lives under `scripts/orchestrator/` and continues to bind localhost by
default. GitHub CLI webhook forwarding remains a development/test transport.

AUDIT-0010 adds the bounded always-on transport without changing dispatch policy:

```text
GitHub repository webhook
    -> trusted HTTPS reverse proxy
    -> HMAC-SHA256 verification of the exact request body
    -> persistent GitHub delivery de-duplication
    -> existing deterministic filter/debounce
```

Hosted mode must reject unsigned or invalidly signed deliveries before event classification. The
webhook secret belongs only in host configuration, never Git or model prompts. The public reverse
proxy terminates trusted TLS; the controller itself remains localhost-only.

The controller requires a **dedicated clone** in both modes. Local state and its virtualenv live under
the ignored `.skyforge-orchestrator/` directory. The hosted service must restart on boot and preserve
that directory across process restarts.

Hosted installation must run as a dedicated **non-root** sudo-capable service user. The installer
fails closed when invoked as root. The hosted Codex runtime is the repository-pinned Python SDK; its
ChatGPT device-code authentication is stored for that service user. No API-key billing fallback is
introduced.

Before hosted activation, server-side GitHub protection for `main` must be verifiably active:
pull requests and status checks are required, force-push and branch deletion are blocked, and the
protection applies to administrators so owner-authenticated host credentials cannot bypass `main`.
The installer verifies applicable rulesets or classic branch protection and refuses activation if
this invariant cannot be established.

Because a powered-off host cannot receive webhooks, each hosted startup compares a compact current
GitHub fingerprint (main head, open PR state, recent Actions state) with the prior startup baseline.
A changed fingerprint produces exactly one synthetic `reconcile` wake so the classifier reasons from
current repository truth rather than attempting to replay every missed delivery. First startup only
establishes the baseline.

### Usage accounting

Ignored events cost no Codex turn because `openai_codex` is imported lazily only after deterministic
filtering and CI-quiescence checks.

A useful work event normally costs:
- one low-cost Luna classification turn;
- zero worker turns for NOOP/HUMAN_GATE;
- one Terra turn for a bounded DISPATCH.

This should be materially more usage-efficient than scheduled polling when repository activity is
bursty.

### Durable failure / quota semantics

An actionable webhook is not considered consumed merely because the in-memory dispatcher received it.
The local controller must journal the event batch before acknowledging it and clear that journal only
after the corresponding orchestration decision reaches a terminal handoff.

The recovery invariant is:

```text
repository event
    -> durable local journal
    -> classifier decision
    -> optional durable worker branch
    -> terminal NOOP / gate / PR handoff / managed merge
    -> clear only the consumed event keys
```

If Codex is unavailable because of account allowance, rate/capacity, authentication, or a transient
SDK/App-Server failure, the controller fails closed: preserve the event/decision/worker state, open a
bounded local circuit breaker, and accept/coalesce later events without starting more Codex turns.
When the breaker expires, reconstruct from current repository truth before continuation. An interrupted
worker with partial local changes is resumed on its recorded branch rather than discarded.

Worker completion and GitHub handoff are separate durable phases. Once the worker returns, persist a
`handoff` stage and summary before commit/push/PR operations. Handoff retries must be idempotent:
reuse an existing local commit, remote branch, or open PR rather than rerunning the worker or creating
duplicate pull requests.

A fresh event invalidates a cached non-worker classifier decision because repository truth may have
changed. A genuinely in-flight worker decision remains stable until its bounded handoff completes;
later events remain queued for a subsequent classification.

### Local cost ceiling and telemetry

The pilot has conservative controller-side call ceilings in addition to whatever account-level Codex
allowance applies. The first hosted evaluation defaults are **24 classifier attempts and 4 worker
attempts per UTC day**; both are environment-overridable. Raise them only after issue #378 shows that
useful work is being left queued at favorable yield. Reaching the local ceiling is a normal blocked
state, not a reason to discard work.

Persist counters sufficient to evaluate issue #349 by accepted-progress economics, including at least:
events seen/filtered/actionable, classifier attempts/NOOPs, worker attempts/handoffs/resumes,
retry/Codex blocks, restart replays, managed merges, pause/resume commands, and protected-path
rejections.

A bounded worker may edit lane-owned source/tests/docs but may not autonomously rewrite the control
plane that defines its own authority. Before controller handoff, reject worker changes under
`scripts/orchestrator/**`, `deploy/orchestrator/**`, `.github/**`, private orchestrator state,
`AGENTS.md`, or canonical program/validation/orchestration/human-strategy/cross-lane/Audit governance
documents. Such a change requires manual/Audit inspection rather than an autonomous commit. The
controller enters a durable safety pause and posts a human-gate record before returning the handoff
error, preventing a retry loop from repeatedly attempting the same unsafe commit.

Do not interpret these counters as token or dollar accounting unless the SDK exposes authoritative
usage fields. Their purpose is to detect runaway wakeups, low-value dispatch, and poor accepted-progress
yield before the pilot is expanded.

### Hosted value-accounting contract

Continuous hosting must be judged against accepted-progress economics rather than uptime alone.
AUDIT-0011 requires one model-free daily report that persists a machine-readable local snapshot and
posts a controller-marked GitHub summary without waking Codex.

The report must distinguish:

- overall Skyforge repository activity from controller-owned `codex/*` PR activity;
- manual `/skyforge-orchestrate` wakes from Audit/watchdog wakes;
- classifier attempts/NOOPs from Terra attempts/handoffs/no-change outcomes;
- actionable-event-to-classifier latency;
- ordinary human gates from controller/reliability failures;
- quota/rate/authentication blocking from useful worker throughput;
- overnight hosted contribution from daytime/manual progress;
- actual configured host-hour cost from model-call counters;
- host CPU-load, available-memory, and disk-utilization snapshots for right-sizing evidence.

A trailing keep/rework/cancel advisory may be computed deterministically, but it is never authority to
destroy infrastructure or cross a project gate. A quiet project interval is insufficient evidence for
cancellation by itself; compare controller yield with overall project activity.

Hosted cancellation is a two-boundary operation:

```text
host-side decommission
    -> attempt final value report
       -> if GitHub posting fails, preserve local failure evidence and CONTINUE teardown
    -> delete repository webhook
    -> disable report timer/controller/HTTPS proxy

provider control plane
    -> destroy Droplet
    -> verify no separately billable pilot resource remains
```

Telemetry or GitHub cleanup integrity must never trap continuing infrastructure cost. Missing local
reporting prerequisites, final-report posting failure, hostname-discovery failure, or webhook
lookup/deletion failure are warnings/evidence gaps; they must be recorded where possible while local
services still stop unconditionally. Provider destruction and an external webhook/billable-resource
verification remain the terminal cancellation boundary.

Do not grant the hosted worker provider credentials merely so it can self-destruct. Provider deletion
remains an explicit external action. Powering off a VM is not equivalent to cancellation.

### Rollback

Stopping the local process disables the entire event-driven layer. GitHub state, ordinary producer
chats, CI, validation policy, and hourly Audit continue unchanged.
