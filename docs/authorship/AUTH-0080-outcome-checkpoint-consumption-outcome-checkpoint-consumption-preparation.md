# AUTH-0080 — Outcome-checkpoint-consumption outcome-checkpoint consumption preparation provenance

## Purpose

AUTH-0080 defines an immutable backend-neutral preparation unit for downstream audit/storage
consumption of one exact AUTH-0078 outcome-checkpoint-consumption outcome checkpoint.

A prepared consumption is explicitly bound to:

- one exact AUTH-0079 outcome-checkpoint-consumption outcome-checkpoint binding;
- one exact AUTH-0078 outcome-checkpoint-consumption outcome-checkpoint identity;
- one explicit downstream target identity;
- one positive preparation sequence;
- the CURRENT AUTH-0079 validation captured at preparation time.

AUTH-0080 performs no storage or network operation.

## Target identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionTargetId`
schema 1 contains a nonblank canonical namespace/key pair.

Leading/trailing whitespace is rejected rather than silently normalized.

The canonical token uses URL-safe Base64 of UTF-8 components and begins
`sfackcpoutcpouttarget:v1:`.

Target identity is descriptive only; it opens no file, socket, database, replica, or Minecraft
world.

## Prepared-consumption identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId`
schema 1 contains:

- positive preparation sequence;
- exact AUTH-0078 checkpoint ID;
- exact AUTH-0080 target ID.

These are independent identity axes.

Changing sequence, target, or checkpoint changes prepared-consumption identity.

Its canonical token begins `sfackcpoutcpoutprep:v1:`.

## CURRENT-only preparation

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionOutcomeCheckpointConsumptionPreparer.prepare(...)`
accepts:

- AUTH-0079 binding validation;
- explicit preparation sequence;
- explicit target identity.

It has no raw-binding overload.

The supplied validation must be CURRENT. STALE and INACTIVE fail closed before preparation.

## Exact captured provenance

The prepared object retains:

- exact prepared-consumption ID;
- exact AUTH-0079 binding;
- exact CURRENT preparation validation.

Construction requires exact binding/validation equality and exact checkpoint-ID equality.

## Execution-time currentness

Preparation-time CURRENT does not remain true automatically.

`validateForExecution(prepared, activationState)` revalidates the captured AUTH-0079 binding and
returns CURRENT, STALE, or INACTIVE.

`requireCurrent()` rejects STALE and INACTIVE.

The prepared unit keeps its original checkpoint, target, and preparation sequence after becoming
stale.

## Atomicity boundary

AUTH-0080 supplies identity/currentness provenance only.

A concrete audit/storage adapter must provide any synchronization or transaction boundary spanning:

1. final currentness validation;
2. external I/O;
3. downstream outcome acknowledgement.

## Explicit non-goals

AUTH-0080 does not:

- persist outcome checkpoints;
- replicate them;
- connect to external systems;
- infer target identity;
- infer preparation sequence;
- refresh/rebind stale work;
- retry;
- mutate Minecraft/NeoForge state;
- claim durability.

## Acceptance gate

Reject AUTH-0080 if:

- blank or whitespace-padded target identity is accepted;
- prepared identity omits checkpoint, target, or preparation sequence;
- non-positive preparation sequence is accepted;
- raw bindings bypass CURRENT validation;
- STALE/INACTIVE validation can prepare work;
- checkpoint/binding/validation substitution succeeds;
- stale execution passes `requireCurrent()`;
- target/checkpoint is silently rewritten;
- storage/network/Minecraft I/O enters the preparer.

## Visual evidence

AUTH-0080 uses a 1280×720 architecture/proof atlas:

- `TARGET_IDENTITY`;
- `CURRENT_PREPARE`;
- `AXIS_SEPARATION`;
- `STALE_EXECUTION_BLOCKED`;
- `INACTIVE_BLOCKED`;
- `NO_IO_CLAIM`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0081 direction is **outcome-checkpoint-consumption outcome-checkpoint consumption
ticket/admission identity**: explicitly admit one CURRENT AUTH-0080 prepared consumption to a
downstream audit/storage coordinator while keeping admission distinct from I/O success.
