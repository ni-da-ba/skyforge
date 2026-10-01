# Platform v2 R5C18 — Dormant isolated worker execution

Status: **dormant local capability only; not wired to hosted runtime or production authority**

Parent migration: #767  
Tranche: #886  
Predecessor: R5C17 hosted admission-only worker freezing.

## Purpose

R5C17 can durably freeze an exact admitted `FrozenWorkerSpec` without launching it.

R5C18 consumes only that admitted identity and exercises the already-accepted isolated
worktree + durable worker-provider state machine.

The capability remains disconnected from `platform_v2_hosted_runtime.py`, webhooks,
systemd, Caddy, and production writer authority.

## Admission prerequisite

R5C18 reads the durable R5C17 admission record.

It proceeds only when:

- the record outcome is `ADMITTED`;
- frozen task, attempt, and worker identity are all present and reconstructible.

Blocked, reclassify, non-dispatch, or absent admission records cannot create a worktree or
provider call.

## Fresh quota before first spend

Before the first worker provider call, R5C18 evaluates the accepted quota policy again.

A provider/local quota block occurs before:

- deterministic branch creation;
- worktree creation;
- worker-run record creation;
- provider invocation.

Once a durable worker run exists, recovery follows the worker-run state machine rather
than attempting to consume another provider allowance.

## Deterministic isolated worktree

The worker worktree remains:

`.skyforge-platform-v2/worktrees/<deterministic-worker-branch>`

and is bound to:

- exact frozen branch;
- exact frozen base SHA;
- exact admitted worker attempt.

Preparation refuses ambiguous branch collisions, controller-checkout reuse, dirty
pre-existing worktrees, branch drift, or head drift.

## Durable provider execution

R5C18 reuses `WorkerRunStore` + `advance_worker_run(...)`.

The established states remain:

- `PREPARED`
- `RUNNING`
- `HANDOFF_READY`
- `FAILED`
- `INTERRUPTED`

Provider spend is preceded by durable `RUNNING`.

On success, `HANDOFF_READY` is durable.

A provider failure is durable and never automatically retried.

If the controller dies while the provider is running, restart converts the existing
`RUNNING` record to `INTERRUPTED` / `RECOVERY_REQUIRED` without making a second
provider call.

## Dirty-worktree preservation

A provider may have written partial local changes before an interruption.

R5C18 therefore adds a recovery identity check that verifies:

- deterministic worktree path;
- Git worktree identity;
- exact worker branch;
- exact frozen base HEAD;

without requiring a clean worktree.

The recovery path does not reset, clean, checkout, discard, or otherwise rewrite dirty
provider work.

This preserves partial evidence for explicit later reconciliation.

## No handoff commit yet

`HANDOFF_READY` means only that the bounded worker provider completed and its local
worktree is preserved.

R5C18 does **not**:

- validate final changed-path scope for controller acceptance;
- create the controller-owned bounded commit;
- push a branch;
- create or update a PR;
- execute remote effects;
- merge;
- post a human gate.

Those remain later capability boundaries.

## Hosted/runtime isolation

The hosted runtime does not import:

- `dormant_worker`;
- `worker_provider`;
- `worker_workspace`.

No webhook or health endpoint can launch R5C18.

No deployment/systemd/Caddy change is part of this tranche.

## Preservation hold

R5C18 changes source/tests only.

It does not activate local repository mutation on the live production controller.

Before any live worker mutation authority is enabled, the existing prerequisites remain:

- exact writer-authority/cutover safety;
- DR-70 gate/dependency safety;
- completion of the primary Windows workstation preservation audit;
- required shadow/parity evidence.

This tranche proves the local execution primitive without crossing those operational
boundaries.
