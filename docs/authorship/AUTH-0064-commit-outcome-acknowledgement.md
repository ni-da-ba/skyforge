# AUTH-0064 — Commit outcome acknowledgement

## Purpose

AUTH-0064 defines the backend-neutral structure used to bind a downstream commit coordinator's
outcome attestation to one exact AUTH-0063 commit ticket.

The authorship layer may validate provenance. It must not manufacture backend success.

## Outcome classes

`SkyIslandPublishedWorldCommitOutcome` schema 1 recognizes:

- `SUCCEEDED`;
- `FAILED`.

These values are backend-reported outcomes. Skyforge world/authorship code does not infer them from
a commit ticket, prepared work, or currentness validation.

## External attestation seam

`SkyIslandPublishedWorldCommitOutcomeAttestation` is implemented by a downstream coordinator.

It supplies:

- schema version;
- exact AUTH-0063 ticket identity;
- explicit outcome;
- nonblank backend-owned evidence token.

The backend owns the evidence token's trust and authentication model.

AUTH-0064 validates that a token exists, but deliberately does not interpret its backend-specific
meaning.

## Acknowledgement identity

`SkyIslandPublishedWorldCommitAcknowledgementId` schema 1 contains:

    positive acknowledgementSequence
    exact commit ticket identity

Changing the acknowledgement sequence changes acknowledgement identity.

The canonical token visibly retains the exact ticket identity.

## Structural binding

`SkyIslandPublishedWorldCommitAcknowledgement` retains:

- acknowledgement ID;
- exact commit ticket;
- exact external outcome attestation.

Construction requires:

- acknowledgement ID names the exact ticket;
- supported attestation schema;
- attestation names the exact same ticket;
- non-null outcome;
- nonblank evidence token.

The acknowledgement can therefore expose exact prepared-work provenance through the retained
ticket.

## No success factory

`SkyIslandPublishedWorldCommitAcknowledgementBinder` exposes one operation:

    bind(ticket, externalAttestation, acknowledgementSequence)

It cannot create an attestation.

There is intentionally no:

- `success(ticket)`;
- `failure(ticket)`;
- `acknowledgeSuccess(...)`;
- outcome inference from ticket admission;
- backend mutation.

A concrete backend must supply the attestation.

## Success and failure symmetry

Structurally valid externally supplied SUCCEEDED and FAILED attestations are equally representable.

AUTH-0064 does not reinterpret failure as absence and does not upgrade ticket admission into
success.

## Trust boundary

AUTH-0064 proves structural provenance:

    acknowledgement
      -> external attestation
      -> exact ticket
      -> exact prepared work
      -> exact snapshot/proof chain

It does not cryptographically authenticate, persist, or independently verify the backend evidence
token.

That trust model belongs to the downstream backend/adapter.

## Acceptance gate

Reject AUTH-0064 if:

- acknowledgement sequence is not positive;
- acknowledgement ID can name a different ticket;
- attestation can name a different ticket;
- unsupported attestation schema is accepted;
- null outcome is accepted;
- blank evidence token is accepted;
- binder can create/infer outcome without an attestation;
- ticket admission itself is treated as success;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0064 uses a 1280×720 architecture/proof atlas with:

- `SUCCESS_ATTESTED`;
- `FAILURE_ATTESTED`;
- `EXACT_TICKET`;
- `MALFORMED_BLOCKED`;
- `SEQUENCE_IDENTITY`;
- `NO_SUCCESS_FACTORY`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0065 direction is **acknowledgement-set / replay admission**: define how downstream
consumers admit immutable acknowledgement records without silently accepting duplicate ticket
outcomes, contradictory success/failure attestations, or sequence reuse.
