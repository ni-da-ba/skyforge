# Platform v2 R5C11 — Managed PR truth observation

Status: **read-only managed-PR lifecycle observation; hosted mutation remains disabled**

Parent migration: #767  
Tranche: #872  
Predecessor: R5C10 durable managed-handoff identity.

## Purpose

R5C10 made the complete worker-to-PR handoff reconstructible after restart. R5C11 binds
that frozen authority to current GitHub PR truth and feeds the accepted pure Platform-v2
managed-PR reducer.

R5C11 is observation and policy only. It cannot ready, merge, comment, push, or otherwise
mutate GitHub.

## Exact remote reads

For one durable handoff the adapter permits exactly:

- `gh pr view <pr> --repo <repo> --json <fixed fields> --jq=.`
- `gh pr diff <pr> --repo <repo> --name-only`

The fixed PR fields include:

- PR number/state/merged state;
- draft and merge-state status;
- status-check rollup;
- base branch;
- head branch and exact head SHA;
- title/body;
- GitHub review decision.

No other command is admitted by the validator.

## Frozen identity checks

Current PR truth must match the R5C10 handoff on:

- PR number;
- base branch = `main`;
- exact managed branch;
- exact expected head SHA;
- exact frozen PR title/body.

Live changed paths may not widen the durable handoff path set. Any identity/head/path drift
returns `REJECTED` and cannot enter the mechanical reducer.

## CI projection

CI is projected conservatively:

- no checks -> `UNKNOWN`;
- any active check -> `PENDING` unless a completed failure is already present;
- any completed failing check -> `FAIL`;
- `PASS` requires no active/failing checks and at least one successful check;
- skipped/neutral checks do not create successful evidence by themselves.

Exact evidence SHA is populated only for an auto-merge-eligible active PR whose CI is
`PASS`.

## Review and task-spec acceptance

For machine-only auto-merge authority, exact frozen identity/path agreement with no
`REVIEW_REQUIRED` or `CHANGES_REQUESTED` decision permits the mechanical acceptance
projection:

- reviewed SHA = exact expected head;
- accepted task-spec hash = exact frozen task-spec hash.

A GitHub review requirement/requested change clears those fields and creates a human
review gate.

A handoff whose repository authority is not auto-merge eligible remains a human-gate
class regardless of machine CI.

## Pure reducer composition

`managed_state_from_handoff(...)` reconstructs the exact v2 managed authority from the
durable R5C10 handoff.

`decide_managed_pr_truth(...)` allows only an `OBSERVED` exact truth result into
`reduce_managed_pr(...)`.

Representative outcomes:

- exact green machine-only truth -> `MERGE_ELIGIBLE`;
- active/pending CI -> `WAIT`;
- non-auto-merge or review-required truth -> `HUMAN_GATE`;
- drifted/malformed truth -> rejected before reducer entry.

## Merge-state boundary

The current GitHub merge-state status is retained in the R5C11 truth result for the later
mutation service. R5C11 itself does not use that field to perform a merge.

The future exact merge service must still re-observe current PR/head/merge-state truth
immediately before mutation and use the existing compare-and-swap
`--match-head-commit` contract.

## Safety boundary

R5C11 does not:

- import into the hosted production runtime;
- invoke classifier/worker providers;
- acquire writer authority;
- ready or merge a draft PR;
- post a human-gate comment;
- alter legacy state;
- alter DR-70 authority.

It closes the read/policy half of the post-handoff lifecycle so the next tranche can
compose exact mutation/reconciliation without guessing state from chat or legacy runtime
patches.
