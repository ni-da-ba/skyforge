# AUTH-0073 — Outcome-checkpoint activation and exact binding currentness

## Purpose

AUTH-0073 explicitly selects one AUTH-0072 checkpoint-consumption outcome checkpoint generation for
downstream audit/storage consumers and allows those consumers to bind to the exact active checkpoint.

The contract distinguishes:

- CURRENT;
- STALE;
- INACTIVE.

Activation does not imply persistence, replication, or durability.

## Activation state

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointActivationState`
is immutable.

- `inactive()` contains no active checkpoint.
- `activateInitial(checkpoint)` is valid only from inactive state.
- `replace(expectedCurrent, replacement)` requires exact expected-current identity and a strictly
  higher AUTH-0072 checkpoint revision.

Replacement returns a new state; the prior state remains unchanged.

AUTH-0073 introduces no extra activation-revision axis. AUTH-0072 `checkpointRevision` is the
generation axis.

## Exact binding

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointBinding`
captures the exact active checkpoint capability.

Ticket lookup delegates to the captured checkpoint, never to later activation state.

Its canonical token begins `sfackcpoutbinding:v1:`.

## Currentness validation

The binder validates one exact binding against one supplied immutable activation state:

- CURRENT: active checkpoint ID exactly equals the bound checkpoint ID.
- STALE: a different checkpoint ID is active.
- INACTIVE: no checkpoint is active.

Impossible validation tuples fail closed.

`requireCurrent()` rejects STALE and INACTIVE.

## No hidden refresh

The binder exposes only:

- `bind`;
- `validate`.

There is no refresh, rebind, latest, or retry operation.

After replacement, an old binding becomes STALE but remains bound to the original outcome checkpoint
and original exact ticket lookup.

## Durability boundary

Activation means only:

> this immutable AUTH-0072 outcome-checkpoint generation is selected for downstream consumers.

It does not mean persisted, replicated, fsynced, remotely acknowledged, or durable.

## Acceptance gate

Reject AUTH-0073 if:

- initial activation overwrites an active checkpoint;
- replacement does not require exact expected-current identity;
- replacement allows same/lower checkpoint revision;
- stale bindings follow the replacement checkpoint;
- CURRENT/STALE/INACTIVE validation accepts impossible tuples;
- `requireCurrent()` accepts STALE or INACTIVE;
- hidden refresh/latest/retry semantics appear;
- storage/durability claims enter activation semantics;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0073 uses a 1280×720 architecture/proof atlas:

- `ACTIVATE_EXACT`;
- `CURRENT_BINDING`;
- `STALE_NO_REFRESH`;
- `INACTIVE_DISTINCT`;
- `CAS_REPLACEMENT`;
- `NO_DURABILITY_CLAIM`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0074 direction is **outcome-checkpoint consumption preparation provenance**: bind one
downstream audit/storage preparation unit to an exact CURRENT AUTH-0073 binding and explicit target
identity while still performing no I/O.
