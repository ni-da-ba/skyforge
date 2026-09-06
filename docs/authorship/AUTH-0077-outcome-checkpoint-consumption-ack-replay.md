# AUTH-0077 — Outcome-checkpoint consumption acknowledgement replay admission

## Purpose

AUTH-0077 defines an immutable admission set for exact AUTH-0076 outcome-checkpoint consumption
outcome acknowledgements.

It prevents downstream consumers from silently accepting replayed, contradictory, or
sequence-colliding audit/storage outcome records.

## Admission invariants

Within one acknowledgement set:

- an exact AUTH-0075 coordination ticket may have at most one admitted AUTH-0076 outcome
  acknowledgement;
- an acknowledgement sequence may be used at most once;
- exact acknowledgement replay is rejected;
- a second acknowledgement for the same ticket is rejected even if the outcome is the same;
- a contradictory SUCCEEDED/FAILED acknowledgement for the same ticket is rejected;
- acknowledgement-sequence reuse across different tickets is rejected.

No winner is selected.

## Canonical order

Admitted acknowledgements are stored in ascending positive acknowledgement-sequence order.

Caller list order therefore does not affect canonical set representation.

## Immutable admission

`admit(acknowledgement)` returns a new set.

The prior set remains unchanged.

The returned acknowledgement list is immutable.

Admission reconstructs and revalidates the complete set, keeping replay/contradiction/sequence
invariants centralized.

## Exact ticket lookup

`forTicket(ticketId)` performs exact AUTH-0075 ticket-identity lookup.

There is no lookup by:

- latest sequence;
- preferred outcome;
- success preference;
- failure preference;
- winner.

## Duplicate versus contradiction

Both of these are invalid:

- same ticket + same outcome + another acknowledgement;
- same ticket + different outcome.

The first is duplicate/replay ambiguity.

The second is contradictory external outcome evidence.

Neither is resolved automatically.

Any later reconciliation policy must be explicit and separately versioned.

## Sequence reuse

Acknowledgement sequence is a set-wide identity/audit-order axis.

Two acknowledgements for different AUTH-0075 tickets may not share one acknowledgement sequence.

## Explicit non-goals

AUTH-0077 does not:

- replace an acknowledgement;
- select a newest acknowledgement;
- select SUCCEEDED over FAILED;
- select FAILED over SUCCEEDED;
- upsert;
- merge contradictory evidence;
- authenticate backend evidence;
- perform persistence/replication/audit I/O;
- mutate Minecraft/NeoForge state.

## Acceptance gate

Reject AUTH-0077 if:

- exact replay is admitted;
- a second acknowledgement for one AUTH-0075 ticket is admitted;
- contradictory outcomes are silently resolved;
- acknowledgement-sequence reuse is admitted;
- caller order changes canonical order;
- `admit` mutates the existing set;
- returned lists are mutable;
- replace/latest/winner/upsert semantics appear;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0077 uses a 1280×720 architecture/proof atlas with:

- `CANONICAL_SET`;
- `IMMUTABLE_ADMIT`;
- `REPLAY_BLOCKED`;
- `CONTRADICTION_BLOCKED`;
- `SEQUENCE_REUSE_BLOCKED`;
- `NO_WINNER_SELECTION`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0078 direction is **outcome-checkpoint consumption outcome acknowledgement
checkpoint/publication identity**: give one validated AUTH-0077 acknowledgement set an explicit
immutable revision/content identity for downstream audit/persistence handoff without claiming
actual storage.
