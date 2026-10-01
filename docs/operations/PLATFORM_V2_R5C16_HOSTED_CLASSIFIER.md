# Platform v2 R5C16 — Hosted restart-safe classifier proposal

Status: **explicit classifier proposal boundary accepted in isolation; dispatch/workers/effects remain disabled**

Parent migration: #767  
Tranche: #882  
Predecessor: R5C15 durable hosted task dispatch planning.

## Purpose

R5C15 can stop at a restart-safe `READY_FOR_CLASSIFIER` task plan with an exact
`TaskPipelineSeed`.

R5C16 advances only that exact plan through the already-accepted durable classifier
proposal layer.

The classifier remains a proposal source. It owns no repository authority and R5C16 does
not perform repository dispatch admission.

## Explicit capability boundary

The hosted runtime exposes an explicit in-process method:

`advance_task_classifier(...)`

The webhook handler, task claim path and task preflight path do not call this method.

The method requires the caller to supply a `ClassifierProvider`; the hosted HTTP runtime
does not construct `CodexClassifierProvider` automatically.

Health reports:

- `explicit_classifier_proposal_enabled = true`;
- `automatic_classifier_execution_enabled = false`.

All mutation/worker authority flags remain false.

## Exact plan binding

R5C16 accepts only an active R5C15 plan whose state is
`READY_FOR_CLASSIFIER` and whose exact persisted `TaskPipelineSeed` is present.

The seed already binds:

- durable event ID;
- repository task authority identity/digest;
- issue;
- accepted `main`;
- classifier semantic input.

No plan, or a plan in any earlier state, performs provider spend.

## Quota before provider spend

Before entering the provider ledger, R5C16 evaluates the accepted
`classify_quota_admission(...)` policy.

If authoritative provider quota denies the call, the result is `QUOTA_BLOCKED`.

If provider quota is non-authoritative or absent, an explicit local budget must allow the
call.

A quota-blocked proposal creates no classifier-run record and invokes the provider zero
times.

## Durable classifier execution

R5C16 reuses `ClassifierRunStore` and `advance_classifier(...)`.

The classifier identity binds the exact request plus immutable provider model/reasoning
configuration.

The established state machine is:

- `PREPARED` saved before spend;
- `RUNNING` saved before provider call;
- `COMPLETE` contains typed proposal + raw-response digest;
- `FAILED` is durable and is not automatically retried;
- a process that restarts with `RUNNING` converts it to `INTERRUPTED` and returns
  `RECOVERY_REQUIRED` without making a second provider call.

A completed request returns `ALREADY_COMPLETE` on subsequent calls without new spend.

Provider/config drift for the same request is rejected.

## Proposal is still non-authoritative

A successful classifier result does **not**:

- widen or replace repository-owned task authority;
- perform dispatch admission;
- consume/create a worker attempt;
- launch a worker;
- create a worktree;
- acquire a writer fence;
- commit or push;
- create/ready/merge a PR;
- execute a remote effect;
- post a human gate.

The next integration boundary must freshly validate the typed classifier proposal against
repository task authority, current main, external ownership and quota before a worker can
exist.

## Safety

R5C16 does not:

- acquire production writer authority;
- automatically invoke a classifier from a webhook;
- activate workers or repository mutation;
- mutate legacy state;
- alter DR-70 code, evidence, roadmap, or live state.

It adds the first explicit provider-spend capability to the v2 hosted path while keeping
that spend durable, quota-gated, manually invoked, and separated from repository authority.
