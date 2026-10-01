# AUTH-0070 — External checkpoint-consumption outcome acknowledgement

## Purpose

AUTH-0070 defines the backend-neutral structure that binds a downstream persistence/replication
outcome attestation to one exact AUTH-0069 checkpoint-consumption admission ticket.

The authorship layer validates structural provenance.

It does not manufacture downstream I/O success.

## Outcome classes

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcome` recognizes:

- `SUCCEEDED`;
- `FAILED`.

These outcomes are reported by a downstream I/O coordinator.

They are not inferred from:

- checkpoint publication;
- checkpoint activation;
- CURRENT validation;
- preparation;
- ticket admission.

## External attestation seam

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionOutcomeAttestation` is
implemented by a downstream coordinator.

It supplies:

- schema version;
- exact AUTH-0069 ticket identity;
- explicit outcome;
- nonblank backend-owned evidence token.

The downstream adapter owns the evidence token's trust/authentication model.

AUTH-0070 validates that the token exists but deliberately does not interpret its backend-specific
meaning.

## Acknowledgement identity

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementId` schema 1
contains:

- positive explicit acknowledgement sequence;
- exact AUTH-0069 ticket identity.

Changing acknowledgement sequence changes acknowledgement identity.

The canonical token retains the full exact ticket identity.

## Structural binding

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgement` retains:

- acknowledgement ID;
- exact I/O-admission ticket;
- exact external outcome attestation.

Construction requires:

- acknowledgement ID names the exact ticket;
- supported attestation schema;
- attestation names the exact same ticket;
- non-null outcome;
- nonblank evidence token.

The acknowledgement therefore preserves:

    outcome acknowledgement
      -> exact I/O admission ticket
      -> exact prepared checkpoint consumption
      -> exact target
      -> exact checkpoint
      -> acknowledgement-set provenance

## No success factory

`SkyIslandPublishedWorldCommitAcknowledgementCheckpointConsumptionAcknowledgementBinder` exposes
only:

    bind(ticket, externalAttestation, acknowledgementSequence)

It cannot create an attestation.

There is intentionally no:

- `success(ticket)`;
- `failure(ticket)`;
- `acknowledgeSuccess(...)`;
- inference from admission;
- storage or network I/O.

## Success and failure symmetry

Structurally valid externally supplied SUCCEEDED and FAILED attestations are equally representable.

AUTH-0070 does not treat failure as absence and does not upgrade admission to success.

## Trust boundary

AUTH-0070 proves structural provenance only.

It does not:

- cryptographically authenticate backend evidence;
- inspect a file;
- query a database;
- contact a replica;
- verify fsync;
- verify Minecraft state;
- independently establish durability.

Those are downstream adapter concerns.

## Acceptance gate

Reject AUTH-0070 if:

- acknowledgement sequence is non-positive;
- acknowledgement ID can name another ticket;
- attestation can name another ticket;
- unsupported attestation schema is accepted;
- null outcome is accepted;
- blank evidence token is accepted;
- binder can create/infer an outcome without external attestation;
- ticket admission is treated as success;
- file/network/database/Minecraft I/O enters the binder.

## Visual evidence

AUTH-0070 uses a 1280×720 architecture/proof atlas with:

- `SUCCESS_ATTESTED`;
- `FAILURE_ATTESTED`;
- `EXACT_IO_TICKET`;
- `MALFORMED_BLOCKED`;
- `SEQUENCE_IDENTITY`;
- `NO_SUCCESS_FACTORY`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0071 direction is **checkpoint-consumption acknowledgement replay admission**:
immutably admit outcome acknowledgements while rejecting exact replay, contradictory success/failure
records for the same I/O ticket, and acknowledgement-sequence reuse without silently selecting a
winner.
