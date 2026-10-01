# Platform v2 R5C13 — Durable external producer ownership

Status: **explicit v2 external/manual ownership service; hosted activation remains disabled**

Parent migration: #767  
Tranche: #876  
Predecessor: R5C12 ordinary lifecycle completion.

## Purpose

The legacy controller implements manual/external producer ownership through
`skyforge_external_producer_claim_runtime.py`, an import-time runtime extension.

The hosted v2 ingress deliberately imports only the base legacy classifier, so those
extension commands are not automatically available. R5C13 moves the semantic behavior
into explicit Platform-v2 software.

## Commands

R5C13 recognizes trusted issue-comment controls:

`/skyforge-claim-external`

with optional legacy-compatible options:

- `pr=<positive integer>`
- `lane=<Implementation|Authorship|Content|Music|Presentation|Audit>`
- `branch=<safe branch name>`

and:

`/skyforge-release-external`

with no options.

Malformed recognized controls fail closed. Unrelated comments are ignored. An untrusted
actor's command may be recognized structurally but never changes durable authority.

## Durable ledger

Active semantic claims are stored under:

`.skyforge-platform-v2/external-claims.json`

with the standard atomic backup.

The authority identity retained by v2 is:

- governing issue number;
- claiming actor;
- optional lane;
- optional branch;
- optional bound PR number.

Legacy timestamps, metrics and last-action bookkeeping remain diagnostics rather than
authority.

The ledger enforces at most one active claim per issue.

## Cutover import

R5C13 imports either:

- the normalized R5A `LegacyOperationalProjection.as_dict()` form
  (`external_claims: [...]`); or
- raw legacy state `external_producer_claims: {...}`.

Only active claims become v2 authority.

This lets the final cutover project existing manual ownership into v2 without
reconstructing it from chat or reissuing claim commands.

## Admission and release

A new claim is admitted only through the accepted pure
`classify_claim_admission(...)` policy with an explicit current
`ControllerIssueOwner`.

Controller-owned work therefore cannot be stolen by a manual claim race.

Explicit release requires a trusted actor and removes only the exact issue-scoped claim.

The accepted `classify_external_dispatch_hold(...)` policy remains the worker-spend
guard: a matching active external claim blocks classifier/worker dispatch while retaining
the underlying durable task authority.

## Model-free retirement

For a claim bound to a PR, the read-only adapter permits only:

`gh pr view <pr> --repo <repo> --json state,mergedAt --jq=.`

The claim remains active while the PR is OPEN and retires only when the bound PR is
provably CLOSED or MERGED.

For an unbound claim, the adapter permits only:

`gh api repos/<repo>/issues/<issue>`

and retires only when the governing issue is provably CLOSED.

Unavailable, malformed or ambiguous remote truth keeps the claim active. Uncertainty is
never permission to race a manual producer.

The read validator has no Git/GitHub mutation command.

## Safety boundary

R5C13 does not:

- import into or activate the hosted production runtime;
- mutate GitHub;
- invoke classifier or worker providers;
- mutate legacy controller state;
- alter any existing external producer claim;
- alter DR-70 or its human gate.

A later hosted-integration tranche will compose this explicit service with signed ingress
and current controller ownership observations before the production writer cutover.
