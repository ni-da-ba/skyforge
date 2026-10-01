# Platform v2 R5C21 — Nonproduction hosted execution rehearsal acceptance

Status: **hosted execution path accepted by exact nonproduction evidence; production mutation remains disabled**

Parent migration: #767  
Tranche: #892  
Predecessor: R5C20 production activation safety envelope.

## Purpose

R5C20 requires an explicit `hosted_execution_path_accepted` signal before Platform v2 can
reach operator review for production activation.

R5C21 supplies that evidence using the accepted durable Platform-v2 chain, without
enabling hosted worker execution or any remote mutation capability.

## Evidence chain

The read-only evaluator requires one exact durable chain:

1. R5C15 `HostedTaskDispatchPlan` in `READY_FOR_CLASSIFIER`;
2. R5C16 matching `ClassifierRunRecord` in `COMPLETE`;
3. R5C17 matching `HostedAdmissionRecord` in `ADMITTED`;
4. R5C18 matching `WorkerRunRecord` in `HANDOFF_READY`;
5. R5C19 matching `DormantHandoffCommitRecord` in `COMMITTED`.

Identity must agree across every boundary:

`plan -> classifier request/run -> admission -> attempt -> worker -> local commit`

The evaluator also requires the ordinary remote-effect ledger to be empty.

A successful decision therefore proves the complete **local execution path** while proving
that the rehearsal did not cross into push/PR/GitHub effects.

## End-to-end rehearsal

The acceptance test uses a real temporary Git repository and the actual v2 state stores.

It performs:

- exact signed issue-comment task ingress through `HostedV2Substrate`;
- explicit hosted task claim;
- fresh read-only GitHub task preflight through the accepted fake live-truth transport;
- explicit quota-gated classifier proposal;
- fresh repository-owned dispatch admission;
- deterministic isolated worker worktree creation;
- bounded worker-provider execution;
- controller-owned path-validated local attempt commit;
- final read-only hosted-execution evidence evaluation.

The classifier and worker are deterministic fake providers so the rehearsal does not spend
external provider quota or contact a remote service.

The Git worktree, branch, file delta, commit and durable identities are real.

## Acceptance requirements

Evidence is accepted only when:

- the plan has the exact persisted `TaskPipelineSeed`;
- the classifier request exactly equals the plan request;
- the classifier run is COMPLETE;
- the admission references the exact plan/event/issue/classifier run;
- the admission contains reconstructible frozen task/attempt/worker identity;
- the worker run matches the admitted worker spec and is HANDOFF_READY;
- the handoff commit matches the exact admission, worker run, attempt, branch and base SHA;
- the handoff outcome is COMMITTED, not merely NO_CHANGE;
- the remote-effect ledger contains zero records.

Missing, incomplete, drifted, or remote-effect-crossed chains are BLOCKED.

## Isolation

R5C21 does not wire dormant execution into the hosted runtime.

`platform_v2_hosted_runtime.py` still does not import:

- `dormant_worker`;
- `dormant_handoff_commit`;
- `worker_provider`;
- `worker_workspace`.

The evidence evaluator itself is read-only and has no subprocess, provider, workspace,
writer-fence, save, push, PR, issue, or remote-effect execution surface.

## R5C20 implication

After exact-head CI and Orchestrator Smoke acceptance of this tranche, the project may set:

`hosted_execution_path_accepted = true`

for the R5C20 production-activation envelope.

This tranche does **not** satisfy or clear:

- primary Windows workstation preservation audit;
- DR-70 migration/human-review hold;
- production remote-effect-path acceptance;
- production cutover/rollback rehearsal acceptance.

Release-3 live shadow/parity already has its own formal acceptance boundary in R3J / PR #826.

## Safety

R5C21 performs no deployment, systemd/Caddy change, production writer transition, live
worker execution, push, GitHub mutation, remote effect, legacy-state mutation, or DR-70
modification.
