# Platform v2 R5C24 — Gated production hosted runtime assembly

Status: **production-shaped hosted runtime accepted for source/test validation; live writer cutover is not performed by this tranche**

Parent migration: #767  
Tranche: #899  
Predecessors: R5C15–R5C23.

## Purpose

R5C15 through R5C23 separately accepted:

- signed task planning and fresh authority preflight;
- durable classifier proposals;
- repository-authoritative dispatch admission;
- isolated worker execution;
- controller-owned bounded local commits;
- strict exactly-once Git/GitHub effects;
- restart-safe managed PR observation and lifecycle;
- nonproduction hosted execution rehearsal;
- disposable-base live remote-effect rehearsal;
- process-level cutover/rollback rehearsal.

R5C24 assembles those already-accepted capabilities into one production-shaped hosted
runtime path while keeping activation fail-closed and operator-controlled.

It does not switch the production service or writer.

## Default-off behavior

Without explicit production execution activation, `platform_v2_hosted_runtime.py`
continues to behave as the accepted hosted substrate.

Webhook receipt may:

- verify signed GitHub delivery identity;
- capture exact task authority;
- persist the durable inbox.

It does not automatically enable or invoke the mutation-capable coordinator.

Health reports production execution as disabled and all mutation/worker/effect authority
flags remain false.

There is no HTTP endpoint that changes activation state or directly executes a task.

## Activation evidence

Mutation-capable mode requires both:

`--enable-production-execution`

and:

`--activation-evidence <path>`

The activation evidence is not a trusted boolean. R5C24 parses and recomputes the complete
R5C20 `ProductionActivationInput`.

The evidence file binds:

- the exact lower-level cutover readiness decision;
- primary workstation preservation PASS;
- explicit DR-70 migration-hold clearance;
- hosted shadow/parity acceptance;
- hosted execution-path acceptance;
- remote-effect-path acceptance;
- cutover/rollback rehearsal acceptance;
- explicit operator production-execution request;
- accepted main SHA;
- observed legacy writer revocation;
- current writer authority = `NONE`.

The file also contains the canonical production-activation-input digest. A changed
activation field invalidates the digest.

At startup the runtime independently reads the actual checkout `HEAD`.

The execution gate is READY only if:

1. R5C20 recomputes to `READY_FOR_OPERATOR_REVIEW`;
2. the operator explicitly requested production execution;
3. supplied accepted main equals the R5C20 accepted main;
4. actual checkout HEAD equals accepted main;
5. legacy writer revocation is observably confirmed;
6. writer authority is `NONE`.

This preserves the rehearsed:

`LEGACY -> NONE -> V2`

boundary. A direct `LEGACY -> V2` activation is impossible through this gate.

## Runtime coordinator

`HostedExecutionCoordinator.advance_once(...)` advances at most one durable lifecycle
boundary per call.

The order is:

1. protected task claim;
2. fresh task preflight;
3. durable classifier proposal;
4. fresh repository-authoritative dispatch admission;
5. isolated worker execution;
6. bounded controller-owned local commit;
7. exactly-once push and draft-PR handoff;
8. managed PR observation / ready / exact merge lifecycle.

Each call reloads the durable state needed for its boundary.

Before every call, the coordinator rechecks the controller checkout HEAD against the
accepted activation SHA. Checkout drift blocks further execution before mutation.

## Authority and identity reuse

R5C24 does not create a parallel task or effect identity model.

It reuses the accepted stores and exact identities from R5C15–R5C23.

The remote handoff is constructed from the exact R5C17 admitted frozen worker authority
and the R5C19 bounded local commit.

Once a managed draft PR is reached, the full `ManagedOrdinaryHandoff` is persisted in the
existing ordinary pipeline ledger, making later managed-PR lifecycle recovery
restart-safe.

## Remote effects

The production-shaped default adapter is the accepted strict
`GhGitOrdinaryEffectAdapter`.

Push, PR creation, and any later managed lifecycle mutation retain the accepted
idempotency/effect-ledger contracts.

Tests can inject a fake remote transport; the hosted runtime itself does not weaken the
underlying command allowlists.

## Nonproduction end-to-end coverage

The R5C24 integration test uses a real temporary Git repository and restarts the hosted
runtime between every durable boundary:

`TASK_CLAIMED`
-> `PREFLIGHT_ADVANCED`
-> `CLASSIFIER_ADVANCED`
-> `ADMISSION_ADVANCED`
-> `WORKER_ADVANCED`
-> `LOCAL_COMMIT_ADVANCED`
-> `REMOTE_HANDOFF_ADVANCED`.

The classifier and worker are deterministic fake providers.

Local Git branch/worktree/commit behavior is real.

Remote push/PR behavior uses the accepted fake effect transport, so repository CI does not
mutate live GitHub.

The result must contain exactly one reconstructible managed handoff bound to the admitted
attempt and exact changed paths.

## Restart behavior

Because every stage uses the previously accepted durable store, restarting between stages
does not reconstruct task meaning from conversation or transient process memory.

Repeated boundaries reconcile or return existing durable state rather than repeating
logical provider/effect work.

## Service-entrypoint contract

The existing hosted script now has an activation-ready process contract. This is source
and test acceptance only.

R5C24 does **not**:

- edit the installed systemd service;
- restart or stop the production controller;
- alter the reverse proxy;
- move production writer authority;
- activate the v2 process on the production webhook port.

Those actions remain a separate operator cutover tranche.

## DR-70

R5C24 does not inspect or machine-pass the DR-70 human re-review.

The activation gate consumes the explicit operator-level
`dr70_migration_hold_cleared` fact through R5C20. It cannot infer that value from CI,
merge state, roadmap quiescence, or the presence of a human-gate record.

## Acceptance meaning

Once exact-head CI and Orchestrator Smoke accept R5C24, the lower-level cutover input:

`hosted_v2_runtime_accepted = true`

may be supported by an actual assembled, gated production-shaped runtime rather than
separate dormant components.

That still does not itself authorize or perform the production writer switch.
