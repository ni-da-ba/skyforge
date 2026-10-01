# AUTH-0071 — Checkpoint-consumption outcome replay admission

## Purpose

AUTH-0071 defines an immutable admission set for exact AUTH-0070 checkpoint-consumption outcome
acknowledgements.

It prevents downstream consumers from silently accepting replayed, contradictory, or
sequence-colliding persistence/replication outcome records.

## Admission invariants

Within one acknowledgement set:

- an exact AUTH-0069 I/O-admission ticket may have at most one admitted AUTH-0070 outcome
  acknowledgement;
- an acknowledgement sequence may be used at most once;
- exact acknowledgement replay is rejected;
- a second acknowledgement for the same I/O ticket is rejected even if the outcome is the same;
- a contradictory SUCCEEDED/FAILED acknowledgement for the same I/O ticket is rejected;
- acknowledgement-sequence reuse across different I/O tickets is rejected.

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

`forTicket(ticketId)` performs exact AUTH-0069 ticket-identity lookup.

There is no lookup by:

- latest sequence;
- preferred outcome;
- success preference;
- failure preference;
- winner.

## Duplicate versus contradiction

Both of these are invalid:

- same I/O ticket + same outcome + another acknowledgement;
- same I/O ticket + different outcome.

The first is duplicate/replay ambiguity.

The second is contradictory external outcome evidence.

Neither is resolved automatically.

Any later reconciliation policy must be explicit and separately versioned.

## Sequence reuse

Acknowledgement sequence is a set-wide identity/audit-order axis.

Two acknowledgements for different I/O tickets may not share one acknowledgement sequence.

## Explicit non-goals

AUTH-0071 does not:

- replace an acknowledgement;
- select a newest acknowledgement;
- select SUCCEEDED over FAILED;
- select FAILED over SUCCEEDED;
- upsert;
- merge contradictory evidence;
- authenticate backend evidence;
- perform persistence/replication I/O;
- mutate Minecraft/NeoForge state.

## Acceptance gate

Reject AUTH-0071 if:

- exact replay is admitted;
- a second acknowledgement for one I/O ticket is admitted;
- contradictory outcomes are silently resolved;
- acknowledgement-sequence reuse is admitted;
- caller order changes canonical order;
- `admit` mutates the existing set;
- returned lists are mutable;
- replace/latest/winner/upsert semantics appear;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0071 uses a 1280×720 architecture/proof atlas with:

- `CANONICAL_SET`;
- `IMMUTABLE_ADMIT`;
- `REPLAY_BLOCKED`;
- `CONTRADICTION_BLOCKED`;
- `SEQUENCE_REUSE_BLOCKED`;
- `NO_WINNER_SELECTION`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0072 direction is **checkpoint-consumption outcome checkpoint/publication identity**:
give one validated AUTH-0071 outcome set an explicit immutable revision/content identity for
downstream audit/persistence handoff without claiming actual storage.
