# Platform v2 R5C15 — Durable hosted task dispatch planning

Status: **hosted protected-task planning accepted in isolation; classifier/worker/effect execution remains disabled**

Parent migration: #767  
Tranche: #880  
Predecessor: R5C14 durable roadmap and human-gate authority.

## Purpose

The hosted Platform-v2 substrate already accepted signed webhook deliveries, durably
captured exact task authority, and could perform a direct read-only task preflight.

R5C15 makes ownership of that next task step restart-safe without enabling execution.

The hosted process can now explicitly:

1. select the next protected task under the accepted inbox precedence;
2. durably claim exactly that task;
3. verify its signed-webhook authority capture;
4. freshly hydrate current GitHub task truth;
5. persist the exact classifier-ready `TaskPipelineSeed`.

It still cannot invoke a classifier provider, worker, worktree, Git/GitHub mutation,
remote effect, merge, human-gate post, or writer fence.

## Durable plan

The task-planning store is:

`.skyforge-platform-v2/hosted-task-plan.json`

with the standard atomic backup.

Only one active plan may exist.

The plan identity binds:

- durable event ID;
- governing issue;
- exact captured task-authority-record digest.

Planning states are:

- `CLAIMED`
- `WAIT_REMOTE`
- `READY_FOR_CLASSIFIER`
- `BLOCKED`

`READY_FOR_CLASSIFIER` additionally persists the exact reconstructible
`TaskPipelineSeed` and read-only preflight digest.

## Protected-task claim

`claim_next_protected_task(...)` uses the accepted inbox
`select_dispatch_batch(...)` precedence.

It claims only a single protected task signal.

Before creating a plan it verifies:

- the task has a valid issue identity;
- no supplied external/manual producer claim owns that issue;
- an exact signed-webhook `TaskAuthorityEventRecord` exists;
- captured issue identity matches the durable event.

Ordinary wake events are never claimed by this task planner.

An already-active plan prevents a second claim.

## Fresh preflight

`advance_claimed_task_preflight(...)` uses the accepted R5C9 read-only preflight.

Before remote reads, the durable authority-capture digest must still match the plan.

Fresh GitHub truth then verifies:

- issue identity/state;
- exact authority comment identity/revision;
- trusted actor;
- typed task directive;
- current accepted `main`.

Outcomes:

- executable current truth -> `READY_FOR_CLASSIFIER` with exact seed;
- temporary GitHub/read unavailability -> `WAIT_REMOTE`;
- edited/deleted/closed/untrusted/non-executable authority -> `BLOCKED`.

A `WAIT_REMOTE` retry retains the same plan identity.

A `READY_FOR_CLASSIFIER` or `BLOCKED` plan is terminal for this planning tranche and is
not silently recomputed.

## Hosted substrate integration

`HostedV2Substrate` now exposes explicit in-process methods:

- `claim_next_task_plan(...)`
- `advance_task_plan_preflight(...)`

These methods are **not called by `handle_webhook(...)`**.

Receiving a webhook therefore still only performs the already-accepted signed ingress and
authority capture. It does not automatically claim, preflight, classify, dispatch, or
mutate work.

The hosted health snapshot reports:

- whether task planning is available;
- active plan ID;
- active plan status.

It continues to report:

- `mutation_authority = false`;
- `worker_dispatch_enabled = false`;
- `remote_effect_execution_enabled = false`;
- `ordinary_v2_mutation_authority = false`.

## Concurrency/restart safety

The runtime snapshots the durable plan and captured-authority ledger before read-only
preflight.

After remote reads it reloads current plan state before saving the result. If plan
ownership changed while the read was in flight, the stale result is refused rather than
overwriting newer authority.

Restart reloads the exact plan and, when classifier-ready, the exact
`TaskPipelineSeed`.

Protected task events also remain protected by inbox compaction while new ordinary
deliveries arrive.

## Capability boundary

R5C15 contains no automatic hosted execution loop.

The task planner does not import or invoke:

- classifier providers;
- worker providers;
- ordinary pipeline execution;
- worktree creation;
- writer fences;
- remote-effect adapters.

The hosted HTTP substrate retains no subprocess or repository-mutation surface.

## Safety

R5C15 does not:

- acquire production writer authority;
- invoke Codex/classifier/worker providers;
- create a worktree or commit;
- push a branch;
- create/ready/merge a PR;
- post a human gate;
- mutate legacy controller state;
- alter DR-70 code, evidence, roadmap, or live state.

The next hosted tranche may add restart-safe classifier ownership, but it must remain a
separate capability boundary before worker or remote-effect activation.
