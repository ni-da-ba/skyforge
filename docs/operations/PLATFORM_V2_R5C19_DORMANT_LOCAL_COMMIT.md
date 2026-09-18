# Platform v2 R5C19 — Dormant bounded local handoff commit

Status: **dormant local commit boundary only; no hosted/runtime or remote-effect activation**

Parent migration: #767  
Tranche: #888  
Predecessor: R5C18 dormant isolated worker execution.

## Purpose

R5C18 can leave one admitted disposable worker in durable `HANDOFF_READY` state with
bounded local edits preserved in its deterministic isolated worktree.

R5C19 validates that local delta and creates at most one controller-owned handoff commit.

It remains disconnected from the hosted runtime and all remote effects.

## Preconditions

R5C19 proceeds only when:

- R5C17 has a reconstructible `ADMITTED` record;
- the exact admitted worker run exists;
- that run is `HANDOFF_READY`;
- worker spec, attempt, branch, base, and deterministic worktree identity match.

Any missing/not-ready/drifted authority blocks before a commit is attempted.

## Bounded commit validation

R5C19 uses the accepted `WorkspaceCommitAdapter` with the frozen worker scope.

Before commit it verifies:

- exact worker branch;
- exact frozen base HEAD;
- changed paths remain within frozen allowed scope;
- protected exact paths/prefixes were not modified;
- `git diff --check` passes.

Out-of-scope or protected changes remain preserved and uncommitted when blocked.

## Exactly-once logical commit behavior

The local commit carries the exact attempt trailer:

`Skyforge-Attempt: <attempt_id>`

If the controller crashes after Git creates the commit but before the R5C19 JSON ledger
is saved, restart inspects the existing commit and verifies:

- exact attempt trailer;
- clean committed worktree;
- parent equals frozen base;
- changed paths are still within frozen scope.

It then persists the same logical handoff record without creating a second commit.

The durable record intentionally does not depend on whether the current process created
the commit or merely reconciled an already-existing exact commit.

## Durable commit ledger

The state store is:

`.skyforge-platform-v2/dormant-handoff-commit.json`

with the standard atomic backup.

Outcomes are:

- `COMMITTED`
- `NO_CHANGE`
- `BLOCKED`

A committed record binds:

- admission record identity;
- worker run identity;
- attempt identity;
- branch/base;
- outcome/reason;
- resulting head SHA;
- exact changed paths.

The record ID covers the resulting commit identity as well as the upstream attempt, so
head/path tampering fails closed on reload.

## No-change behavior

If the completed worker left no repository delta, R5C19 records `NO_CHANGE` with the
frozen base as the head.

No synthetic commit is created.

## Safety boundary

R5C19 does **not**:

- push the worker branch;
- create, ready, update, or merge a PR;
- mutate issues/comments;
- execute a remote effect;
- post a human gate;
- acquire production writer authority;
- mutate legacy controller state;
- modify DR-70 code/evidence/roadmap/live state.

`platform_v2_hosted_runtime.py` does not import this service or the low-level workspace
commit adapter.

## Preservation hold

This tranche changes source/tests only.

No live local-commit authority is activated on the production controller.

The existing prerequisites still apply before any dormant worker/commit capability is
wired live:

- exact writer-authority/cutover safety;
- DR-70 dependency/gate safety;
- completion of the primary Windows workstation preservation audit;
- required hosted shadow/parity evidence.

The next remote-capability boundary is branch push / managed PR handoff, and must remain
separate from this local commit primitive.
