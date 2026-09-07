# AUTH-0066 — Acknowledgement-set checkpoint identity

## Purpose

AUTH-0066 gives one exact validated AUTH-0065 acknowledgement set an explicit immutable checkpoint
identity suitable for downstream persistence or replication handoff.

A checkpoint does not mean that any backend stored or replicated the set.

## Checkpoint identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointId` schema 1 contains:

- positive `checkpointRevision`;
- exact canonical ordered acknowledgement-ID list.

The acknowledgement identity may be empty, allowing an explicit checkpoint of the valid empty
AUTH-0065 set.

## Version axis

`checkpointRevision` versions the whole acknowledgement-set checkpoint.

It is distinct from:

- acknowledgement sequence;
- ticket sequence;
- work sequence;
- snapshot revision;
- publication revision;
- backend storage revision.

The revision is explicit and is not a content hash.

Because the exact acknowledgement identity is also part of the checkpoint ID:

- same set + different revision => different checkpoint ID;
- changed set + same revision => different checkpoint ID.

Revision reuse therefore cannot hide changed checkpoint contents.

## Canonical identity validation

Direct checkpoint-ID construction requires:

- supported schema;
- positive checkpoint revision;
- null-free acknowledgement identities;
- strict ascending acknowledgement sequence;
- no duplicate ticket identity.

This preserves the upstream AUTH-0065 admission invariants even when an ID is constructed directly.

## Exact checkpoint binding

`SkyIslandPublishedWorldCommitAcknowledgementCheckpoint` binds:

- exact checkpoint ID;
- exact validated acknowledgement set.

Construction recomputes the acknowledgement-ID list from the set and requires exact equality.

An ID from another set cannot bind.

## Publisher seam

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointPublisher.publish(set, revision)` creates
only the immutable checkpoint capability.

It performs no:

- file I/O;
- persistence;
- replication;
- network activity;
- backend mutation;
- Minecraft world access.

## Query preservation

The checkpoint delegates exact ticket lookup to the captured AUTH-0065 set.

No latest/winner semantics are added.

## Acceptance gate

Reject AUTH-0066 if:

- zero/negative checkpoint revision is accepted;
- checkpoint identity omits exact acknowledgement identity;
- noncanonical acknowledgement identity is accepted;
- duplicate ticket identity is accepted;
- checkpoint ID from another set can bind;
- revision reuse hides changed set contents;
- publisher performs persistence/replication/backend mutation;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0066 uses a 1280×720 architecture/proof atlas with:

- `EXACT_CHECKPOINT`;
- `EMPTY_CHECKPOINT`;
- `REVISION_AXIS`;
- `CONTENT_AXIS`;
- `FORGED_BINDING_BLOCKED`;
- `NO_STORAGE_CLAIM`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0067 direction is **checkpoint activation/currentness** for downstream persistence or
replication consumers: explicitly select one checkpoint generation without silently treating
publication as durable storage.
