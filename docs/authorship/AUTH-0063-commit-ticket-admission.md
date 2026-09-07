# AUTH-0063 — Commit-ticket admission capability

## Purpose

AUTH-0063 defines the backend-neutral capability issued when one exact AUTH-0062 prepared-work
validation is CURRENT and is explicitly admitted to downstream commit coordination.

A commit ticket means:

> this exact prepared-work validation passed the CURRENT gate and was admitted for a named commit
> coordination attempt.

It does **not** mean that backend mutation started, completed, or succeeded.

## Ticket identity

`SkyIslandPublishedWorldCommitTicketId` schema 1 contains:

    schemaVersion
    positive ticketSequence
    exact SkyIslandPublishedWorldPreparedWorkId

The explicit ticket sequence separates distinct coordination admissions for the same prepared work.

It is not:

- a backend success sequence;
- a hidden retry counter;
- a content hash;
- inferred automatically.

The canonical token retains the exact prepared-work token visibly.

## Ticket capability

`SkyIslandPublishedWorldCommitTicket` retains:

- exact ticket identity;
- exact AUTH-0062 prepared-work validation used for admission.

Construction requires:

- the validation is CURRENT;
- the ticket ID names the exact prepared-work identity from that validation.

The ticket therefore retains the complete provenance chain:

    ticket
      -> prepared-work validation
      -> prepared work
      -> snapshot binding
      -> snapshot/view/publication/volume/support proof

## Issuance gate

`SkyIslandPublishedWorldCommitTicketIssuer.issue(validation, ticketSequence)` accepts only an
AUTH-0062 `SkyIslandPublishedWorldPreparedWorkValidation`.

The issuer has no raw prepared-work overload.

Before issuing it calls `requireCurrent()`.

Therefore STALE and INACTIVE validations cannot issue tickets.

## No hidden revalidation

The issuer does not accept an activation state and does not silently refresh the validation.

A ticket records that the supplied immutable validation was CURRENT.

As with AUTH-0061/0062, a concrete backend requiring atomic currentness from validation through
mutation must provide its own synchronization boundary.

An older CURRENT validation value does not become magically aware of later activation changes.

This limitation is explicit and intentional.

## Admission versus outcome

AUTH-0063 introduces only admission to commit coordination.

It does not introduce:

- STARTED;
- SUCCEEDED;
- FAILED;
- ROLLED_BACK;
- persisted;
- applied-block count.

Those are backend outcome concepts and belong to a later explicit acknowledgement contract.

## Explicit admissions and retries

Two ticket sequences can explicitly identify two admissions for the same prepared work.

AUTH-0063 does not itself decide whether a backend permits re-admission or retry.

No automatic retry exists.

Any policy restricting duplicate/replayed tickets must be enforced by the downstream commit
coordinator or a later admission registry contract.

## Acceptance gate

Reject AUTH-0063 if:

- zero/negative ticket sequence is accepted;
- ticket identity omits prepared-work identity;
- STALE or INACTIVE validation can issue a ticket;
- a ticket can attach an ID for another prepared work;
- issuer exposes a raw-work shortcut;
- issuance silently revalidates, refreshes, or retries;
- ticket construction claims backend success;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0063 uses a 1280×720 architecture/proof atlas with:

- `CURRENT_ADMISSION`;
- `TICKET_IDENTITY`;
- `STALE_BLOCKED`;
- `INACTIVE_BLOCKED`;
- `EXACT_PROVENANCE`;
- `NO_OUTCOME_CLAIM`.

The atlas is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0064 direction is **commit outcome acknowledgement**.

It should define immutable backend-neutral acknowledgement identity that binds to one exact
AUTH-0063 ticket and distinguishes explicit outcome classes without allowing a success
acknowledgement to be fabricated from authorship-layer preparation alone.
