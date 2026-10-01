# Platform v2 R5C10 — Durable managed-handoff identity

Status: **restart-safe managed handoff identity; hosted execution remains disabled**

Parent migration: #767  
Tranche: #870  
Predecessor: R5C6 ordinary pipeline; R5C9 hosted task preflight.

## Purpose

A successful R5C6 worker handoff already creates a bounded commit, pushes the frozen
branch, creates an exact draft PR, and returns a `ManagedOrdinaryHandoff`. Before
R5C10, the pipeline ledger retained only the PR number and handoff digest.

That was insufficient for a production controller restart: later CI/review/merge
processing needs the complete frozen handoff authority.

R5C10 makes that identity reconstructible without granting new mutation authority.

## Durable identity

A completed ordinary pipeline now persists the complete managed handoff:

- task ID;
- repository authority key;
- task-spec hash;
- lane;
- attempt ID;
- repository;
- frozen base SHA;
- branch;
- expected PR head SHA;
- exact PR title/body;
- governing issue when present;
- PR number;
- changed paths;
- auto-merge eligibility.

The canonical handoff digest remains the integrity identity.

## Reload validation

On reload, an embedded managed handoff must agree with the pipeline record on:

- canonical handoff digest;
- PR number;
- task-spec hash;
- attempt ID.

Mutation scope and changed-path structure are also reconstructed through strict typed
parsers.

Any mismatch fails closed rather than producing managed authority.

## Backward compatibility

Pre-R5C10 pipeline records may have stage `COMPLETE` plus a PR number/handoff digest
but no embedded handoff.

Those records remain readable and visible. They are deliberately excluded from
`reconstructible_managed_handoffs()` and exposed through
`incomplete_completed_records()`.

A later lifecycle service must not automate merge or ownership transitions from such an
incomplete historical record without separate reconciliation.

## Safety boundary

R5C10 does not:

- read or mutate GitHub;
- call a classifier or worker;
- create or merge a PR;
- acquire hosted production authority;
- import the ordinary pipeline into the hosted runtime;
- alter legacy state;
- alter DR-70 authority.

It only makes already-produced v2 handoff authority restart-safe so R5C11 can observe and
reconcile managed PRs without reconstructing semantics from conversation or memory.
