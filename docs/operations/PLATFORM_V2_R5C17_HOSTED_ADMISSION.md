# Platform v2 R5C17 — Hosted dispatch admission without worker launch

Status: **admission-only boundary; frozen worker identity may be persisted, worker execution remains disabled**

Parent migration: #767  
Tranche: #884  
Predecessor: R5C16 hosted restart-safe classifier proposal.

## Purpose

R5C16 can durably produce one typed classifier proposal for an exact protected task plan.

R5C17 revalidates repository-owned authority and accepted `main`, then applies the
accepted pure dispatch-admission policy. A successful result freezes the exact task,
attempt, and worker specification but does not execute it.

## Fresh authority before admission

Before admission, R5C17 re-reads:

- the exact governing GitHub issue;
- the exact authority comment captured from the signed webhook;
- the current accepted `main`.

The live authority must still reconstruct the same identity and
`RepositoryTaskAuthority` used to build the classifier seed.

Edited/deleted/closed/untrusted authority is blocked.

If `main` moved since classification, the proposal is recorded as `RECLASSIFY`.

If a classifier proposal names a source PR but R5C15/R5C16 did not durably capture the
classification-time PR head, R5C17 also returns `RECLASSIFY` rather than inventing
freshness evidence.

## Admission policy

R5C17 feeds the accepted `admit_dispatch(...)` policy with:

- fresh repository task authority;
- the durable classifier proposal;
- current-main freshness;
- active external/manual producer claims;
- provider quota / local budget;
- explicit attempt number.

Repository authority remains normative for:

- lane;
- objective;
- stop boundary;
- issue identity;
- maximum path scope.

Classifier path scope may narrow repository scope but cannot widen it.

Active external ownership or quota denial blocks admission.

Non-dispatch classifier decisions create no worker authority.

## Durable admission record

The admission store is:

`.skyforge-platform-v2/hosted-admission.json`

with the standard atomic backup.

One record binds:

- hosted plan ID / protected event;
- governing issue;
- classifier request/run/decision identity;
- fresh authority and hydration digests;
- fresh accepted `main`;
- attempt number;
- outcome and reason;
- admission/pending/quota digests where admission policy ran.

Outcomes are:

- `ADMITTED`
- `RECLASSIFY`
- `NOT_DISPATCH`
- `BLOCKED`

Only `ADMITTED` records may contain executable worker identity.

## Frozen worker identity

An admitted record persists and reconstructs all three identities:

1. `FrozenTaskSpec`
2. `TaskAttemptIdentity`
3. `FrozenWorkerSpec`

Reload verifies:

- task spec hash from canonical payload;
- attempt ID from task/base/attempt number;
- worker task/authority/spec/attempt/base relationships;
- deterministic worker branch;
- worker digest;
- overall admission digest.

Partial or tampered frozen identity fails closed.

This means a later edit to a task comment, roadmap, classifier prompt, or branch convention
cannot silently reinterpret an already-admitted worker attempt.

## Hosted substrate integration

`HostedV2Substrate` exposes the explicit method:

`advance_task_admission(...)`

Webhook receipt, task claiming, preflight, and classifier proposal do not automatically
call it.

Health exposes:

- `explicit_task_admission_enabled = true`
- `automatic_task_admission_enabled = false`
- active admission record ID/outcome when present

while continuing to report:

- `mutation_authority = false`
- `worker_dispatch_enabled = false`
- `remote_effect_execution_enabled = false`

The hosted runtime still does not import the low-level `dispatch_admission` module
directly; it depends on the higher-level R5C17 boundary.

## Safety boundary

R5C17 does not:

- invoke a worker provider;
- create or mutate a worktree;
- acquire a writer fence;
- commit or push;
- create, ready, update, or merge a PR;
- execute remote effects;
- post human gates;
- mutate legacy controller state;
- acquire production writer authority;
- modify DR-70 code, evidence, roadmap order, or live human-gate state.

The next capability boundary may consume an `ADMITTED` record to execute exactly the
frozen disposable worker, but worker execution remains a separate tranche.
