# Platform v2 R4B — Pre-staged documentation canary executor

R4B introduces the first mutation-capable Platform-v2 adapter, but **does not authorize a live canary by itself**.

## Authority class

The executor supports exactly one task class:

`PRESTAGED_DOCUMENTATION_PR`

A candidate branch and commit must already exist. Platform v2 cannot create, edit, or push that branch. The frozen task contract binds:

- one canary issue;
- exact accepted-main base SHA;
- exact candidate/head SHA;
- one `platform/v2-canary/*` branch;
- one file under `docs/operations/platform-v2-canary/`;
- exact PR title/body;
- frozen task-spec hash and attempt identity.

## Mutations permitted

Only:

1. create the exact PR if provably absent;
2. merge that exact PR after exact-head acceptance.

The GitHub command validator rejects issue mutation, comments, workflow dispatch, branch push, file editing, general API write methods, and merge commands lacking the expected-head binding.

## Durable effect protocol

Remote mutations use a separate state store:

`.skyforge-platform-v2/canary-state.json`

with backup:

`.skyforge-platform-v2/canary-state.json.bak`

Legacy `.skyforge-orchestrator/state.json` is never written by the executor.

For both `CREATE_PR` and `MERGE_PR`:

1. write a canonical PENDING effect before the remote mutation;
2. observe exact remote truth;
3. execute only when the effect is provably absent;
4. after restart, reconcile an exact already-present effect without re-executing it;
5. block on conflicting or unknown remote identity.

A successful remote merge may advance `main` before local completion. R4B therefore permits read-only reconciliation of a previously recorded PENDING merge before reapplying the frozen-base mutation guard. This is required to recover the post-merge crash window safely.

## Exact acceptance

The merge path requires:

- current candidate head == frozen expected head;
- current accepted main == frozen base immediately before new mutation;
- exactly one changed path, equal to the frozen documentation path;
- exact PR title/body/base/head identity;
- protected `build` context PASS on the exact candidate head;
- evidence SHA == reviewed SHA == current head;
- accepted task-spec hash == frozen task-spec hash;
- `decide_mechanical(...)` returns `MERGE_ELIGIBLE`.

## Writer exclusion

Each invocation holds the dedicated Platform-v2 writer fence. The legacy controller must also hold an active external-producer claim for the exact canary issue and branch, excluding legacy from that authority before v2 mutation can be authorized.

## Live boundary

The repository mutation gate currently records workstation preservation PASS but keeps:

- `canary_enabled = false`;
- `canary_issue_number = null`.

A separate accepted tranche must create/authorize one exact canary before this executor is run against GitHub with mutation authority.
