# AUTH-0072 — Checkpoint-consumption outcome checkpoint identity

## Purpose

AUTH-0072 gives one exact validated AUTH-0071 checkpoint-consumption outcome set an explicit
immutable checkpoint identity suitable for downstream audit or persistence handoff.

The checkpoint does not mean that any storage or replication occurred.

## Checkpoint identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointId` schema 1
contains:

- positive explicit `checkpointRevision`;
- exact canonical ordered AUTH-0070 acknowledgement-ID list.

The acknowledgement identity may be empty, allowing explicit checkpointing of the valid empty
AUTH-0071 set.

## Version axis

`checkpointRevision` versions the whole outcome-set checkpoint.

It is distinct from:

- AUTH-0070 acknowledgement sequence;
- AUTH-0069 ticket sequence;
- AUTH-0068 preparation sequence;
- AUTH-0066 checkpoint revision;
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
- no duplicate AUTH-0069 I/O-ticket identity.

This preserves AUTH-0071 admission invariants even for directly constructed IDs.

## Exact checkpoint binding

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpoint` binds:

- exact checkpoint ID;
- exact validated AUTH-0071 outcome set.

Construction recomputes the ordered acknowledgement-ID list from the set and requires exact
identity equality.

An ID from another set cannot bind.

## Publisher seam

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPublisher`
creates only the immutable checkpoint capability.

It performs no:

- file I/O;
- persistence;
- replication;
- network activity;
- backend mutation;
- Minecraft/NeoForge world access.

## Query preservation

Exact AUTH-0069 I/O-ticket lookup delegates to the captured AUTH-0071 set.

No latest/winner semantics are introduced.

## Acceptance gate

Reject AUTH-0072 if:

- zero/negative checkpoint revision is accepted;
- checkpoint identity omits exact acknowledgement identity;
- noncanonical acknowledgement identity is accepted;
- duplicate I/O-ticket identity is accepted;
- checkpoint ID from another set can bind;
- revision reuse hides changed set contents;
- publisher performs persistence/replication/backend mutation;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0072 uses a 1280×720 architecture/proof atlas with:

- `EXACT_CHECKPOINT`;
- `EMPTY_CHECKPOINT`;
- `REVISION_AXIS`;
- `CONTENT_AXIS`;
- `FORGED_BINDING_BLOCKED`;
- `NO_STORAGE_CLAIM`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0073 direction is **outcome-checkpoint activation/currentness** for audit/storage
consumers: explicitly select one AUTH-0072 outcome checkpoint generation and provide exact
CURRENT/STALE/INACTIVE binding semantics without implying durability.
