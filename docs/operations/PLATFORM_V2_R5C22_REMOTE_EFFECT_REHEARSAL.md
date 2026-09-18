# Platform v2 R5C22 — Safe disposable-base remote-effect rehearsal

Status: **production-shaped remote-effect path accepted in nonproduction; hosted/production activation remains disabled**

Parent migration: #767  
Tranche: #894  
Live rehearsal PR: #895

## Purpose

R5C20 requires explicit acceptance of the Platform-v2 remote-effect path before
production activation can reach operator review.

R5C22 rehearses the real strict ordinary Git/GitHub adapter and exactly-once effect
executor against disposable GitHub branches only. `main` is never a mutation target.

## Compatibility extension

`OrdinaryMutationScope` now supports a frozen `base_ref`.

The default remains `main`.

For the default `main` case, R5C22 preserves:

- the original positional `issue_number` constructor slot;
- the legacy serialized scope shape (no new `base_ref` field);
- existing main-scoped scope/handoff digests;
- existing CREATE_PR and MERGE_PR effect subjects;
- existing command forms;
- the legacy `current main moved...` failure wording.

For a non-main scope, `base_ref` is serialized and bound into target-sensitive effect
identity so another base branch cannot be silently substituted.

Unsafe ref spellings are rejected.

## Strict adapter target binding

The ordinary Git/GitHub adapter now uses the exact frozen `base_ref` for:

- accepted-base SHA observation;
- PR discovery;
- PR creation;
- exact PR identity verification.

The merge command remains exact-head guarded by:

`--match-head-commit <expected_head_sha>`

and merge observation verifies the PR's exact frozen base/head/title/body identity.

## Live-rehearsal defects found and fixed

The live rehearsal surfaced two real host/API compatibility defects before production
activation.

### Missing branch returned HTTP 422

On this host, querying a nonexistent branch through the commits endpoint returns:

`HTTP 422: No commit found for SHA`

rather than 404.

The adapter now treats that exact missing-branch response as `ABSENT` only on the
allow-not-found branch-observation path.

Base-ref reads remain fail-closed.

### Installed gh does not support --slurp

The orchestration host uses GitHub CLI 2.45.0, whose `gh api` does not accept
`--slurp`.

The comment observer now uses the portable exact command:

`gh api <comments-endpoint> --paginate --jq '.[]'`

and parses one JSON object per output line.

Pagination remains enabled and malformed line-delimited output fails closed.

## Exact live evidence

Accepted `main` before, during, and after the rehearsal:

`77b2dfcc06c9e171b7b214061a685b868bfd5015`

Disposable base:

`rehearsal/platform-v2-r5c22-base-20260918141909`

Disposable head:

`rehearsal/platform-v2-r5c22-head-20260918141909`

Frozen base SHA:

`77b2dfcc06c9e171b7b214061a685b868bfd5015`

Frozen expected head SHA:

`b24926e2e2435e9dc0db2a430eaefbfb279f4a4b`

Scope digest:

`58176f8dd143c8063f6c236b89b00a328ba224333473ae0145408606b8e4815e`

Live rehearsal PR:

`#895`

PR merge commit on the disposable base:

`ea93771ec43ee85c75b28cee1e95f8db12f8f07c`

Final ordinary-effect ledger digest:

`15ea2687dc717f888d738a64c16ec5c09bb5b55193050279f5b8de2625ab8b03`

## Effect evidence

### PUSH_BRANCH

Effect ID:

`9b7f5e423f882b10588dd8f2128b19dceff7f0d499ba1ddfd8c8ea102dda1be2`

Final status:

`COMPLETE`

Remote identity:

`branch:rehearsal/platform-v2-r5c22-head-20260918141909@b24926e2e2435e9dc0db2a430eaefbfb279f4a4b`

### CREATE_PR

Effect ID:

`0025ff75ad5908384e4ecc2cea84e93b3a7d11e6d195e8e91c53c538e697de4c`

Final status:

`COMPLETE`

Remote identity:

`pr:895:OPEN@b24926e2e2435e9dc0db2a430eaefbfb279f4a4b`

The PR's exact remote truth showed:

- head ref = the disposable head;
- head SHA = the frozen expected head;
- base ref = the disposable base;
- title/body = frozen scope identity.

### POST_COMMENT crash/recovery

Effect ID:

`bf140141bc2743ff39639c71340af7008c49199d89f09ac679eb8e02f477e4f2`

An injected controller crash occurred **after remote mutation** and before local
completion.

Durable local state remained `PENDING`.

Restart observed the exact effect marker and returned:

`RECONCILED`

without executing another comment mutation.

Exactly one matching remote comment existed.

Remote identity:

`comment:5731421653`

### MERGE_PR

Effect ID:

`083445e8fe94c98a8c392fb2956876db1d121c78ed4ff265763088f4f89ee7be`

Final status:

`COMPLETE`

Execution disposition:

`EXECUTED`

Remote identity:

`merge:pr:895@b24926e2e2435e9dc0db2a430eaefbfb279f4a4b`

The merge targeted only the disposable base and used the exact expected-head guard.

## Cleanup and main safety

After evidence capture:

- GitHub had already removed the disposable head branch;
- the disposable base branch was explicitly deleted;
- neither disposable ref remains;
- PR #895 remains as durable GitHub evidence;
- the effect-marked issue #894 comment remains as crash/recovery evidence;
- `main` still resolved to
  `77b2dfcc06c9e171b7b214061a685b868bfd5015`.

## Acceptance implication

After exact-head CI and Orchestrator Smoke pass for this tranche, the project may set:

`remote_effect_path_accepted = true`

for the R5C20 production-activation envelope.

R5C22 does **not** clear:

- the primary Windows workstation preservation-audit requirement;
- the DR-70 migration/human-review hold unless explicitly cleared by the operator;
- production cutover/rollback rehearsal acceptance.

It also does not wire ordinary remote effects into the hosted runtime or grant Platform v2
production writer authority.
