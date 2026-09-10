# CIV-0035 — Economic Dormancy, Compaction, and Consequential Persistence

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Context

This decision extends the latent/activated civilization architecture established by CIV-0033 and the cadence/lazy-reconciliation model established by CIV-0034.

Skyforge must allow detailed economic state to become computationally cold without erasing player-caused history, unresolved obligations, or materially meaningful world changes.

## Core decision

> Detailed civilization can become computationally cold without becoming historically blank.

Economic activation and physical realization are distinct concerns.

Preferred conceptual dimensions:

```text
ECONOMIC DETAIL

LATENT
   ↓ first detailed need
DORMANT_DETAILED
   ↕
HOT_DETAILED

PHYSICAL REALIZATION

UNREALIZED
   ↕
REALIZED
```

A cluster may therefore retain authoritative detailed economic state while consuming essentially no continuous simulation budget.

## Dormancy is not reset

When a detailed cluster leaves active relevance, do not regenerate it from the original warm-start inputs merely because the player moved away.

Example:

```text
player purchases most local fuel
        ↓
market stock becomes authoritative
        ↓
player leaves region
        ↓
DORMANT_DETAILED
        ↓
no continuous simulation required
        ↓
player returns later
        ↓
lazy reconcile from last authoritative snapshot
```

The same rule applies to:

- player purchases/sales;
- player commercial supply;
- shortages and surpluses;
- piracy and route disruption;
- contracts and claims;
- altered Guild relationships;
- damaged/restored infrastructure;
- player facilities and logistics nodes;
- consequential incident state.

## Safe collapse to pure latent state

A temporarily activated cluster may discard detailed state and return to purely deterministic latent state only when the discarded detail has left no durable causal footprint.

A cluster is eligible for full latent collapse only when all relevant conditions are satisfied:

```text
- no player transaction or meaningful observation depends on the detailed state;
- no unresolved shipment, incident, contract, loan, claim, order, reservation, or custody relation references it;
- no player-caused economic or infrastructure modification must persist;
- no realized physical modification requires semantic acknowledgement;
- no active external system depends on a concrete supplier/customer relation from it;
- discarded details can be reconstructed deterministically without changing a later observable result.
```

If those conditions are not met, retain a dormant detailed snapshot rather than resetting.

## Snapshot compaction

Persist the present consequence of ordinary economic history rather than a complete event log.

A compact dormant state may contain:

```text
EconomicSnapshot
    current aggregate stocks
    current demand / market pressures
    relevant supplier and route relationships
    persistent player-commercial relations
    lastReconciledTime
    initialization / schema version

ConsequentialFrontier
    unresolved shipments
    unresolved contracts / claims / reservations
    persistent disruptions
    player-caused effects
    tracked physical capability changes
    other obligations whose history is still gameplay-relevant
```

Old routine events may be discarded once their consequences have been fully absorbed into the authoritative snapshot.

## History with gameplay value

Some historical records are themselves meaningful and therefore are not reduced to anonymous aggregate state.

Examples include:

- Guild contract records;
- vessel incident / black-box records;
- insurance and liability evidence;
- bonded-cargo custody records;
- registration/certification history where required;
- player-authored commercial or institutional records that are explicitly exposed to gameplay.

These records are persisted because provenance matters, not because all economic activity requires historical logging.

## Physical materiality

Once realized infrastructure has become gameplay-relevant, physical player modification must survive economic dormancy.

Examples:

```text
beacon destroyed
→ navigation capability cannot silently reappear from latent intent

warehouse transfer interface removed
→ associated service must reconcile against actual loss

Guild facility restored by player
→ restored capability must remain recognized after region unload
```

The semantic layer may remember intended civilization function, but current authoritative capability must respect tracked physical alteration.

## Dormant reconciliation

A dormant detailed cluster does not require periodic ticking.

When reactivated, apply CIV-0034 lazy reconciliation from `lastReconciledTime` using:

- stable production/consumption conditions;
- known route and communication state;
- recorded discontinuities;
- unresolved obligations;
- bounded economic constraints.

This preserves continuity without maintaining continuous simulation cost.

## Scaling target

The architecture should support a world in which:

```text
many latent clusters
    → tiny deterministic/semantic cost

some previously relevant clusters
    → compact dormant snapshots

few currently relevant clusters
    → active detailed reconciliation

very few locations
    → concrete Minecraft realization
```

Exploration may increase persistent save-state footprint, but it must not permanently increase active world-simulation cost in direct proportion to every place ever visited.

## Anti-reset invariant

No system may intentionally create a gameplay exploit where leaving/reloading/allowing dormancy restores:

- market stock;
- prices;
- contracts;
- cargo;
- destroyed services;
- settlement capability;
- route conditions;
- Guild obligations;
- other state whose previous value was materially changed by the player or by a committed system event.

## Design invariants

> Detailed civilization can become computationally cold without becoming historically blank.

> Dormancy is a simulation-budget decision, not a world-state reset.

> Persist the current consequence of routine history rather than the complete routine history itself.

> Preserve explicit historical records only where provenance itself has gameplay value.

> Player-caused physical and economic consequences survive unload, dormancy, and reactivation.

> Exploration may grow save-state footprint modestly; it must not permanently grow active simulation cost one-for-one with every visited settlement.
