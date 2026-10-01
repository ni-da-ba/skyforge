# AUTH-0065 — Acknowledgement replay and contradiction admission

## Purpose

AUTH-0065 defines an immutable admission set for exact AUTH-0064 commit acknowledgements.

It prevents downstream consumers from silently accepting replayed, contradictory, or
sequence-colliding outcome records.

## Admission invariants

Within one acknowledgement set:

- an exact AUTH-0063 commit ticket may have at most one admitted acknowledgement;
- an acknowledgement sequence may be used at most once;
- exact acknowledgement replay is rejected;
- a second acknowledgement for the same ticket is rejected even if the outcome is the same;
- a contradictory SUCCEEDED/FAILED acknowledgement for the same ticket is rejected;
- sequence reuse across different tickets is rejected.

No winner is selected.

## Canonical order

Admitted acknowledgements are stored in ascending positive acknowledgement-sequence order.

Caller list order therefore does not affect the canonical set representation.

Because acknowledgement sequences are required to be positive by AUTH-0064, ordinary signed
ascending long order is sufficient.

## Immutable admission

`admit(acknowledgement)` returns a new set.

The prior set is unchanged.

Admission reconstructs and revalidates the entire set, so all replay/contradiction/sequence
invariants remain centralized in one constructor.

## Ticket lookup

`forTicket(ticketId)` performs exact ticket-identity lookup.

There is intentionally no lookup by "latest", outcome preference, acknowledgement sequence maximum,
or winner.

## Duplicate versus contradiction

AUTH-0065 intentionally treats both of these as invalid:

- same ticket + same outcome + another acknowledgement;
- same ticket + different outcome.

The first is duplicate/replay ambiguity.

The second is contradictory backend outcome evidence.

Neither case is resolved automatically.

A higher-level reconciliation process, if ever required, must be explicit and separately versioned.

## Sequence reuse

Acknowledgement sequence is a set-wide identity axis.

Two acknowledgements for different tickets may not share the same acknowledgement sequence.

This prevents sequence reuse from creating ambiguous audit ordering.

## Explicit non-goals

AUTH-0065 does not:

- replace an acknowledgement;
- select a newest acknowledgement;
- select SUCCEEDED over FAILED;
- select FAILED over SUCCEEDED;
- upsert;
- merge contradictory evidence;
- authenticate backend evidence tokens;
- mutate backend state;
- provide persistence;
- define Minecraft/NeoForge behavior.

## Acceptance gate

Reject AUTH-0065 if:

- exact replay is admitted;
- a second outcome for one ticket is admitted;
- contradictory outcomes are silently resolved;
- acknowledgement-sequence reuse is admitted;
- caller order changes canonical set order;
- `admit` mutates the existing set;
- returned acknowledgement lists are mutable;
- replace/latest/winner/upsert semantics appear;
- Minecraft/NeoForge types enter the contract.

## Visual evidence

AUTH-0065 uses a 1280×720 architecture/proof atlas with:

- `CANONICAL_SET`;
- `IMMUTABLE_ADMIT`;
- `REPLAY_BLOCKED`;
- `CONTRADICTION_BLOCKED`;
- `SEQUENCE_REUSE_BLOCKED`;
- `NO_WINNER_SELECTION`.

This is architecture/proof evidence, not an aesthetic morphology gate.

## Next boundary

A likely AUTH-0066 direction is **acknowledgement-set publication/checkpoint identity**: give one
validated immutable acknowledgement set an explicit checkpoint/revision identity for downstream
persistence or replication, without adding implicit winner selection or backend storage behavior.
