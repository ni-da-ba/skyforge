# AUTH-0062 — Prepared-work provenance and commit-intent identity

## Purpose

AUTH-0062 defines an immutable backend-neutral envelope for one unit of work prepared from an exact
AUTH-0061 snapshot binding.

The goal is to preserve enough identity and proof provenance that a downstream adapter can answer:

- exactly which snapshot was used;
- exactly which world-space region was prepared;
- exactly which proof-carrying publication entries were observed during preparation;
- whether that same snapshot binding is still CURRENT before backend-visible commit.

AUTH-0062 does not perform the commit.

## Prepared-work identity

`SkyIslandPublishedWorldPreparedWorkId` schema 1 contains:

    schemaVersion
    workSequence
    exact AUTH-0060 snapshot identity
    exact WorldBounds region

`workSequence` is a positive explicit work identity axis. It is not a content hash.

Changing any of:

- work sequence;
- snapshot identity;
- region

changes prepared-work identity.

The canonical token preserves all three axes. Region doubles are represented by exact IEEE-754 bit
patterns rather than locale-dependent decimal formatting.

## Exact binding provenance

A prepared-work ID is created from an AUTH-0061
`SkyIslandPublishedWorldSnapshotBinding`.

`SkyIslandPublishedWorldPreparedWork` retains that binding exactly.

Construction rejects an ID whose snapshot identity differs from the binding snapshot identity.

## Exact query evidence

Preparation captures:

    binding.query(region)

and stores that exact ordered proof-carrying result as `queryEvidence`.

Construction recomputes the exact bound-snapshot region query and requires equality with the supplied
evidence.

This prevents a caller from:

- substituting another publication;
- dropping a hit;
- adding a hit;
- reordering evidence;
- changing a support certificate;
- using evidence from another snapshot.

The stored evidence is defensively copied and immutable.

## Preparation seam

`SkyIslandPublishedWorldPreparedWorkPreparer.prepare(binding, workSequence, region)` performs only:

1. deterministic identity construction;
2. exact region query against the immutable binding;
3. immutable provenance capture.

It does not inspect later activation state and does not mutate a backend.

## Commit validation

`validateForCommit(preparedWork, activationState)` reuses the AUTH-0061 binding validator for the
exact work binding.

The result is `SkyIslandPublishedWorldPreparedWorkValidation`.

Its status is therefore exactly:

- CURRENT;
- STALE;
- INACTIVE.

The validation object rejects a binding-validation result belonging to another work binding.

## Currentness rule

`requireCurrent()` is the handoff gate.

Prepared work whose binding is STALE or INACTIVE cannot pass it.

The prepared-work object itself remains unchanged and retains its original preparation provenance.

There is no automatic re-preparation or refresh.

## Stale work semantics

If activation moves from snapshot S80 to S81 after work was prepared against S80:

- the work ID remains bound to S80;
- its evidence remains the S80 query result;
- validation against S81 returns STALE;
- `requireCurrent()` fails;
- no evidence is rewritten to S81.

If work against S81 is desired, it must be prepared explicitly from a binding to S81.

## Empty query evidence

An empty query result is valid evidence.

AUTH-0062 proves what was queried, not that every work region must contain a published volume.

Backend-specific work admission may impose additional non-empty or task-specific requirements later.

## Atomicity boundary

As in AUTH-0061, CURRENT validation is an identity predicate against a supplied immutable activation
state.

AUTH-0062 does not create an atomic transaction spanning validation and backend mutation.

A concrete adapter must enforce its own synchronization protocol at the commit boundary.

## Explicit non-goals

AUTH-0062 does not:

- mutate terrain;
- define BlockState or material mapping;
- load chunks;
- reserve backend locks;
- provide atomic commit;
- serialize work queues;
- retry stale work;
- refresh stale bindings;
- choose a newer publication or snapshot;
- infer work sequence numbers;
- change AUTH-0059 spatial query semantics.

## Acceptance gate

Reject AUTH-0062 if:

- prepared-work identity omits snapshot, work sequence, or exact region;
- zero/negative work sequence is accepted;
- an ID can bind a different snapshot;
- supplied query evidence can differ from the exact binding query;
- evidence remains mutable through caller-owned lists;
- a changed snapshot or region produces the same identity;
- commit validation can use another work binding;
- STALE or INACTIVE work passes `requireCurrent()`;
- stale work is silently refreshed;
- Minecraft/NeoForge types enter the contract;
- the contract claims atomic backend commit guarantees.

## Visual evidence

AUTH-0062 uses a 1280×720 architecture/proof atlas with six panels:

- `WORK_IDENTITY`;
- `EXACT_EVIDENCE`;
- `IMMUTABLE_CAPTURE`;
- `CURRENT_GATE`;
- `STALE_BLOCKED`;
- `INACTIVE_BLOCKED`.

The corpus records identity separation, exact evidence capture, forged-evidence rejection,
defensive-copy behavior, and CURRENT/STALE/INACTIVE commit validation.

## Next boundary

A likely AUTH-0063 direction is **backend commit ticket / adapter acknowledgement identity**.

It should define a backend-neutral handoff object that can be issued only for CURRENT prepared work
and that records the exact prepared-work identity accepted by a downstream commit coordinator,
without claiming that the backend mutation itself has already succeeded.
