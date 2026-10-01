# Platform v2 Release 4 — Canary Mutation Acceptance

Status: **ACCEPTED FOR RELEASE 5 CUTOVER READINESS**

Parent migration: #767  
Acceptance tranche: #845  
Accepted Release-4 baseline before this record: `33192df10bb3f0d8a919dafd820aa9f6a3c419f3`

## Exit criterion

The durable migration plan requires Release 4 to transfer one narrowly scoped low-risk task class to Platform v2, preserve unambiguous ownership through writer fencing, and successfully cover restart/reconciliation, GitHub outage, stale-event/head-movement, and related recovery cases.

That criterion is satisfied.

## Preservation and authority prerequisites

- Primary-workstation preservation audit: **PASS** (#830 / PR #831).
- Release-3 live shadow: **PASS**.
- Mutation gate is fail-closed and currently disabled.
- Legacy/v2 external-producer exclusion was demonstrated for the live canary.
- Platform-v2 writer fencing was demonstrated.
- Legacy production state and Platform-v2 state remain isolated.

## First live mutation canary

Canonical canary:

- authority issue: #833;
- PR: #840;
- acceptance: #841 / PR #842;
- frozen base SHA: `b05c5c0fe59202d364bbff947fd8fcce62fffcda`;
- exact candidate SHA: `914124d9aa03c449f4d5a3b5effca4cefcb8cba9`;
- branch: `platform/v2-canary/833-first-live`;
- changed path: `docs/operations/platform-v2-canary/r4c-first-live-canary.md`;
- task-spec hash: `5175d82968cb114e1186c2398ecb23aa5a91c744b35786ac44eb5bfb0915f038`;
- attempt ID: `6d5cfa98b0cfb94d95a736ee5ec7031eea422d0c45c64a7ce93b74a8afc7c14d`;
- CREATE_PR effect: `d6febc29d2f379938f88014b657455fafad2cd7078a1e1c158f57d23c5737ab5`;
- required exact-head `build`: PASS;
- MERGE_PR effect: `30f293d80070ab4606c1f7345cce1caa6a2ac5e3cbd774bc01104c834202831e`;
- merge commit: `579ad221371f4f2b0ffb585fea4a253d06ec03f4`.

Three separate executor invocations demonstrated:

1. durable CREATE_PR;
2. exact-head evidence → MERGE_ELIGIBLE → exact-head merge;
3. post-merge COMPLETE/no-op idempotence after `main` moved.

The writer fence was released after each bounded invocation. Primary and backup v2 canary state hashes matched.

## Required failure/replay corpus

R4D #843 / PR #844 added an explicit machine-readable matrix covering all 26 minimum scenarios in section 16 of the migration plan.

The matrix is:

`docs/agent-state/PLATFORM_V2_FAILURE_CORPUS.json`

Additional named contract tests were added for previously implicit cases:

- out-of-order webhook delivery;
- missed webhook + reconciliation;
- provider/local quota exhaustion;
- push succeeds but completion-state save fails;
- comment succeeds but completion-state save fails;
- dual-controller writer ownership;
- stale restored acceptance state;
- active-attempt task-spec movement.

The focused R4D suite passed 10/10. Normal CI and Orchestrator Smoke passed on the exact accepted head.

## Resolved Release-4 observations

No unresolved Release-4 semantic divergence remains.

A duplicate, concurrently-created canary path (#835/#838/#839) was explicitly de-authorized and closed without merge after the canonical #833/#840/#842 path was discovered. Its legacy external claim was explicitly released. It carries no current authority.

## Current mutation authority

The repository mutation gate is disabled:

- `canary_enabled = false`;
- `canary_issue_number = null`.

This Release-4 acceptance does **not** grant broad Platform-v2 authority.

## Release-5 boundary

Release 5 may begin only through an explicit cutover-readiness tranche that proves:

1. rollback snapshot/state/config remains restorable;
2. current legacy production state is quiescent or durably transferable;
3. old controller mutation authority can be visibly revoked;
4. v2 cannot acquire production writer authority before old authority is revoked;
5. current operational task/roadmap/external-claim state has an explicit v2 projection;
6. webhook/control ingress routing and health visibility are defined for the cutover;
7. rollback restores the old controller without requiring conversational reconstruction.

No old/new dual-writer interval is permitted.
