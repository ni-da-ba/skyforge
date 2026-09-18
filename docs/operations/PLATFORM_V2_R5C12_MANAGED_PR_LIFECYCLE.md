# Platform v2 R5C12 — Exact managed PR ready/merge lifecycle

Status: **managed PR mutation lifecycle complete in isolation; hosted production activation remains disabled**

Parent migration: #767  
Tranche: #874  
Predecessors: R5C10 durable handoff identity and R5C11 managed PR truth observation.

## Purpose

R5C2 created exact draft PR handoffs and provided a merge effect, but production cutover
still needed two guarantees:

1. draft -> ready must itself be a durable/idempotent mutation;
2. the full CI/review/path acceptance predicate must be freshly rechecked inside the
   final merge mutation boundary, not only at an earlier event/read.

R5C12 supplies those guarantees using the existing ordinary exactly-once effect ledger.

## Ready effect

The frozen ordinary mutation scope now derives a canonical ready effect identity using the
existing `UPDATE_PR` effect kind:

`pr:<number>:ready@<expected-head>`

The managed lifecycle adapter permits only the exact:

`gh pr ready <number> --repo <repo>`

for the bound handoff.

Before executing ready, it freshly observes the exact R5C11 truth and requires the pure
managed reducer to remain `MERGE_ELIGIBLE`.

After execution it re-observes exact remote truth. A non-draft exact PR reconciles the
ready effect as complete.

## Merge effect

The merge effect remains bound to the frozen attempt/PR/expected head.

Immediately inside `execute(MERGE_PR)`, the lifecycle adapter freshly reruns the full
R5C11 observation and pure reducer. Mutation is allowed only when:

- remote PR is still OPEN;
- exact base/branch/head/title/body/path identity remains valid;
- CI evidence still produces `MERGE_ELIGIBLE`;
- review state still permits mechanical acceptance;
- PR is no longer draft;
- merge-state status is `CLEAN`.

Only then may the exact command execute:

`gh pr merge <number> --repo <repo> --merge --match-head-commit <expected-head>`

The expected-head compare-and-swap remains the final GitHub mutation guard.

## Crash/restart reconciliation

Both ready and merge use `advance_remote_effect(...)`:

- durable PENDING is written before mutation;
- remote truth is observed twice before mutation;
- exact remote truth is confirmed after mutation;
- COMPLETE is written only after confirmation.

If the process crashes after `ready` but before local completion, restart observes the
exact non-draft PR and reconciles the pending ready effect without executing ready again.

If the process crashes after merge but before local completion, restart observes the exact
merged PR and reconciles the pending merge effect without executing merge again.

If an exact managed PR was merged externally/manually, the merge identity can likewise be
reconciled as complete without issuing another mutation.

A durable pending ready prerequisite is reconciled before merged-state reconciliation so
no prerequisite effect is left orphaned.

## Non-mutation paths

The lifecycle performs no mutation when current truth resolves to:

- human/review gate;
- pending CI;
- failed/unknown CI;
- non-auto-merge authority;
- identity/head/path drift;
- unavailable/malformed remote truth.

A post-ready acceptance change is re-observed and can stop the merge.

## Capability boundary

The lifecycle mutation validator permits only the exact frozen ready and merge commands.
Read commands remain delegated to the strict R5C11 read-only validator.

R5C12 does not import into the hosted production runtime, acquire production writer
authority, invoke classifier/worker providers, mutate legacy state, or alter DR-70
authority.

It completes the isolated ordinary task lifecycle from durable managed handoff through
safe exact merge/reconciliation. Hosted execution/cutover remains a separate later gate.
