# AUTH-0074 — Outcome-checkpoint consumption preparation provenance

## Purpose

AUTH-0074 defines an immutable backend-neutral preparation unit for downstream audit/storage
consumption of one exact AUTH-0072 checkpoint-consumption outcome checkpoint.

A prepared consumption is explicitly bound to:

- one exact AUTH-0073 outcome-checkpoint binding;
- one exact AUTH-0072 outcome-checkpoint identity;
- one explicit downstream target identity;
- one positive preparation sequence;
- the CURRENT AUTH-0073 validation captured at preparation time.

AUTH-0074 performs no storage or network operation.

## Target identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionTargetId`
schema 1 contains a nonblank canonical namespace/key pair.

Leading/trailing whitespace is rejected rather than silently normalized.

The canonical token uses URL-safe Base64 of UTF-8 components and begins
`sfackcpouttarget:v1:`.

Target identity is descriptive only; it opens no file, socket, database, replica, or Minecraft
world.

## Prepared-consumption identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointPreparedConsumptionId`
schema 1 contains:

- positive preparation sequence;
- exact AUTH-0072 outcome-checkpoint ID;
- exact AUTH-0074 target ID.

These are independent identity axes.

Changing sequence, target, or checkpoint changes prepared-consumption identity.

## CURRENT-only preparation

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeCheckpointConsumptionPreparer.prepare(...)`
accepts:

- AUTH-0073 binding validation;
- explicit preparation sequence;
- explicit target identity.

It has no raw-binding overload.

The supplied validation must be CURRENT. STALE and INACTIVE fail closed before preparation.

## Exact captured provenance

The prepared object retains:

- exact prepared-consumption ID;
- exact AUTH-0073 binding;
- exact CURRENT preparation validation.

Construction requires exact binding/validation equality and exact checkpoint-ID equality.

## Execution-time currentness

Preparation-time CURRENT does not remain true automatically.

`validateForExecution(prepared, activationState)` revalidates the captured AUTH-0073 binding and
returns CURRENT, STALE, or INACTIVE.

`requireCurrent()` rejects STALE and INACTIVE.

The prepared unit keeps its original checkpoint, target, and preparation sequence after becoming
stale.

## Atomicity boundary

AUTH-0074 supplies identity/currentness provenance only.

A concrete audit/storage adapter must provide any synchronization or transaction boundary spanning:

1. final currentness validation;
2. external I/O;
3. downstream outcome acknowledgement.

## Explicit non-goals

AUTH-0074 does not:

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

Reject AUTH-0074 if:

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

AUTH-0074 uses a 1280×720 architecture/proof atlas:

- `TARGET_IDENTITY`;
- `CURRENT_PREPARE`;
- `AXIS_SEPARATION`;
- `STALE_EXECUTION_BLOCKED`;
- `INACTIVE_BLOCKED`;
- `NO_IO_CLAIM`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0075 direction is **outcome-checkpoint consumption ticket/admission identity**:
explicitly admit one CURRENT AUTH-0074 prepared consumption to a downstream audit/storage
coordinator while keeping admission distinct from I/O success.
