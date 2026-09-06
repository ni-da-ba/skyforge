# AUTH-0078 — Outcome-checkpoint consumption outcome checkpoint identity

## Purpose

AUTH-0078 gives one exact validated AUTH-0077 outcome-checkpoint consumption acknowledgement set
an explicit immutable checkpoint identity suitable for downstream audit or persistence handoff.

The checkpoint does not mean that any storage or replication occurred.

## Checkpoint identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointId`
schema 1 contains:

- positive explicit `checkpointRevision`;
- exact canonical ordered AUTH-0076 acknowledgement-ID list.

The acknowledgement identity may be empty, allowing explicit checkpointing of the valid empty
AUTH-0077 set.

## Version axis

`checkpointRevision` versions the whole outcome-set checkpoint.

It is distinct from:

- AUTH-0076 acknowledgement sequence;
- AUTH-0075 ticket sequence;
- AUTH-0074 preparation sequence;
- AUTH-0072 checkpoint revision;
- backend storage revision.

The revision is explicit and is not a content hash.

Because exact acknowledgement identity is also part of the checkpoint ID:

- same set + different revision => different checkpoint identity;
- changed set + same revision => different checkpoint identity.

Revision reuse cannot hide changed contents.

## Canonical identity validation

Direct checkpoint-ID construction requires:

- supported schema;
- positive checkpoint revision;
- null-free acknowledgement identities;
- strict ascending acknowledgement sequence;
- no duplicate AUTH-0075 coordination-ticket identity.

This preserves AUTH-0077 admission invariants even for directly constructed IDs.

## Exact checkpoint binding

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpoint`
binds:

- exact checkpoint ID;
- exact validated AUTH-0077 acknowledgement set.

Construction recomputes the ordered acknowledgement-ID list from the set and requires exact
identity equality.

An ID from another set cannot bind.

## Publisher seam

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPublisher`
creates only the immutable checkpoint capability.

It performs no:

- file I/O;
- persistence;
- replication;
- network activity;
- backend mutation;
- Minecraft/NeoForge world access.

## Query preservation

Exact AUTH-0075 ticket lookup delegates to the captured AUTH-0077 set.

No latest/winner semantics are introduced.

## Acceptance gate

Reject AUTH-0078 if:

- zero/negative checkpoint revision is accepted;
- checkpoint identity omits exact acknowledgement identity;
- noncanonical acknowledgement identity is accepted;
- duplicate AUTH-0075 ticket identity is accepted;
- checkpoint ID from another set can bind;
- revision reuse hides changed set contents;
- publisher performs persistence/replication/backend mutation;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0078 uses a 1280×720 architecture/proof atlas with:

- `EXACT_CHECKPOINT`;
- `EMPTY_CHECKPOINT`;
- `REVISION_AXIS`;
- `CONTENT_AXIS`;
- `FORGED_BINDING_BLOCKED`;
- `NO_STORAGE_CLAIM`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0079 direction is **outcome-checkpoint consumption outcome-checkpoint activation/currentness**:
explicitly select one AUTH-0078 checkpoint generation and provide exact CURRENT/STALE/INACTIVE
binding semantics without implying durability.
