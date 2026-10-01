# AUTH-0068 — Checkpoint consumption preparation provenance

## Purpose

AUTH-0068 defines an immutable backend-neutral preparation unit for downstream consumption of one
exact AUTH-0066 acknowledgement checkpoint.

The intended downstream consumers include persistence and replication adapters, but AUTH-0068
itself performs no storage or network operation.

A prepared consumption is explicitly bound to:

- one exact AUTH-0067 checkpoint binding;
- one exact checkpoint identity;
- one explicit downstream target identity;
- one positive preparation sequence;
- a CURRENT checkpoint-binding validation captured at preparation time.

## Target identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionTargetId` schema 1 contains:

- nonblank canonical namespace;
- nonblank canonical key.

Leading or trailing whitespace is rejected rather than silently normalized.

Its canonical token uses URL-safe Base64 encoding of the UTF-8 namespace/key components so delimiter
characters inside target names cannot create ambiguous tokens.

The target identity is descriptive only.

It does not open:

- a file;
- a socket;
- a database;
- a remote replica;
- a Minecraft world.

## Prepared-consumption identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumptionId` schema 1 contains:

- positive explicit preparation sequence;
- exact AUTH-0066 checkpoint identity;
- exact AUTH-0068 target identity.

These are independent identity axes.

Therefore:

- same checkpoint/target + different sequence => different prepared-consumption ID;
- same checkpoint/sequence + different target => different ID;
- same target/sequence + different checkpoint => different ID.

The preparation sequence is explicit and is not inferred from target or checkpoint revisions.

## CURRENT-only preparation

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionPreparer.prepare(...)` accepts:

- AUTH-0067 checkpoint-binding validation;
- explicit preparation sequence;
- explicit target identity.

It has no raw-binding overload.

The supplied validation must be CURRENT.

STALE and INACTIVE validation fail before a prepared unit can be created.

## Exact captured provenance

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointPreparedConsumption` retains:

- exact prepared-consumption identity;
- exact AUTH-0067 binding;
- exact CURRENT validation used for preparation.

Construction requires:

- captured validation is CURRENT;
- captured validation belongs to the exact binding;
- prepared identity checkpoint ID equals the binding checkpoint ID.

This prevents checkpoint or validation substitution after preparation.

## Execution-time currentness

Preparation-time CURRENT status is not a permanent assertion.

Before a downstream adapter executes I/O, it can call:

`validateForExecution(preparedConsumption, activationState)`

This revalidates the exact captured AUTH-0067 binding against the supplied immutable activation
state.

The result is:

- CURRENT;
- STALE;
- INACTIVE.

`requireCurrent()` fails STALE and INACTIVE.

## No hidden retargeting or refresh

A prepared consumption keeps its original:

- checkpoint;
- target;
- preparation sequence.

If checkpoint activation changes, the old prepared consumption becomes STALE when revalidated.

It is not silently moved to:

- the new checkpoint;
- another target;
- a later sequence.

A new preparation requires an explicit new call.

## Atomicity and I/O boundary

AUTH-0068 provides identity and currentness provenance only.

CURRENT validation against an immutable activation-state value is not an atomic transaction with
later external storage or replication I/O.

A concrete adapter must establish its own synchronization/transaction boundary around:

1. final currentness validation;
2. actual I/O;
3. outcome acknowledgement.

AUTH-0068 does not claim persistence, durability, replication, fsync, remote receipt, or backend
success.

## Explicit non-goals

AUTH-0068 does not:

- write files;
- connect to remote services;
- mutate Minecraft/NeoForge state;
- persist checkpoints;
- replicate checkpoints;
- infer target identity;
- infer preparation sequence;
- retry stale work;
- refresh checkpoint bindings;
- perform atomic external commits.

## Acceptance gate

Reject AUTH-0068 if:

- target namespace/key may be blank;
- prepared identity omits checkpoint, target, or preparation sequence;
- non-positive preparation sequence is accepted;
- raw checkpoint bindings can bypass CURRENT validation;
- STALE or INACTIVE validation can prepare a unit;
- prepared identity can name a different checkpoint than the binding;
- preparation validation can belong to another binding;
- stale execution validation passes `requireCurrent()`;
- target or checkpoint is silently rewritten;
- storage/network/Minecraft I/O enters the contract.

## Visual evidence

AUTH-0068 uses a 1280×720 architecture/proof atlas with:

- `TARGET_IDENTITY`;
- `CURRENT_PREPARE`;
- `AXIS_SEPARATION`;
- `STALE_EXECUTION_BLOCKED`;
- `INACTIVE_BLOCKED`;
- `NO_IO_CLAIM`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0069 direction is **checkpoint consumption ticket/admission identity**: explicitly admit
one CURRENT AUTH-0068 prepared consumption to a downstream I/O coordinator, while still separating
admission from actual persistence/replication success.
