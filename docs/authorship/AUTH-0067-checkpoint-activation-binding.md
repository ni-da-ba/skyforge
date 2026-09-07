# AUTH-0067 — Checkpoint activation and exact binding currentness

## Purpose

AUTH-0067 explicitly selects one AUTH-0066 acknowledgement checkpoint generation for downstream
consumers and allows those consumers to bind to the exact active checkpoint.

The contract distinguishes:

- CURRENT;
- STALE;
- INACTIVE.

It does not imply that a checkpoint has been persisted, replicated, or made durable.

## Activation state

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointActivationState` is immutable.

`inactive()` contains no active checkpoint.

`activateInitial(checkpoint)` is valid only from an inactive state.

Re-activating an already active state is rejected.

## Checkpoint generation axis

AUTH-0067 does not introduce another activation revision.

The AUTH-0066 `checkpointRevision` already versions the whole acknowledgement-set checkpoint and
therefore serves as the checkpoint-generation axis.

## Explicit compare-and-replace

`replace(expectedCurrent, replacement)` requires:

- an active checkpoint;
- exact equality with the expected current checkpoint ID;
- replacement checkpoint revision strictly greater than the current checkpoint revision.

Replacement returns a new immutable state.

The prior state remains unchanged.

There is no latest/newest selection.

## Exact binding

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinding` captures the exact active
checkpoint capability.

The binding retains:

- schema version;
- exact checkpoint;
- exact checkpoint ID.

Its canonical token exposes the full checkpoint identity.

Ticket lookup delegates to the captured checkpoint, not to later activation state.

## Currentness validation

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointBinder.validate(binding, state)` returns:

### CURRENT

The supplied activation state has an active checkpoint whose ID exactly equals the binding's
checkpoint ID.

### STALE

The supplied activation state has a different active checkpoint ID.

### INACTIVE

The supplied activation state has no active checkpoint.

## Validation invariants

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointBindingValidation` rejects impossible
tuples:

- CURRENT without the exact bound checkpoint ID;
- STALE without a different active checkpoint ID;
- INACTIVE with any active checkpoint ID.

`requireCurrent()` fails STALE and INACTIVE explicitly.

## No hidden refresh

The binder exposes only:

- `bind`;
- `validate`.

There is no:

- refresh;
- rebind;
- latest;
- retry.

After activation replacement, an old binding remains bound to the old checkpoint and becomes STALE.
It does not silently follow the new checkpoint.

## Durability boundary

Checkpoint activation means only:

> this immutable checkpoint generation is currently selected for downstream consumers.

It does not mean:

- persisted;
- replicated;
- fsynced;
- acknowledged by remote storage;
- durable;
- Minecraft state applied.

A downstream storage/replication adapter owns those semantics.

## Acceptance gate

Reject AUTH-0067 if:

- initial activation can overwrite an already active checkpoint;
- replacement does not require exact expected-current identity;
- replacement allows same/lower checkpoint revision;
- binding can refresh itself;
- stale binding lookup follows the new checkpoint;
- CURRENT/STALE/INACTIVE validation accepts impossible identity tuples;
- `requireCurrent()` accepts STALE or INACTIVE;
- persistence/durability claims enter activation semantics;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0067 uses a 1280×720 architecture/proof atlas with:

- `ACTIVATE_EXACT`;
- `CURRENT_BINDING`;
- `STALE_NO_REFRESH`;
- `INACTIVE_DISTINCT`;
- `CAS_REPLACEMENT`;
- `DURABILITY_BOUNDARY`.

The atlas is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0068 direction is **checkpoint consumption/preparation provenance**: bind a downstream
replication/persistence preparation unit to one exact CURRENT checkpoint binding and explicit target
identity without performing I/O or claiming durability.
