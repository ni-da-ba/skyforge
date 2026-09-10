# CIV-0019 — NPC Freight Realization and Simulation Budget

Status: precommit / design lock

## Decision

NPC freight is real and authoritative, but physical Aeronautics simulation is not the default representation of the world economy.

Skyforge civilization/logistics state remains authoritative. Create Aeronautics: Automated Logistics is the current candidate execution layer for physically realized airship routes because it already supports recorded station-to-station routes, unloaded route progress, persistence across chunk unload/reload/server restart, and physical return near stops for docking and cargo interaction.

The integration must remain replaceable: Skyforge owns shipment meaning and economic state; the Aeronautics automation layer owns physical route execution when a shipment is materialized.

## Feasibility constraints from Automated Logistics

Current Automated Logistics behavior is compatible with the intended Skyforge architecture:

- vehicles can progress while unloaded;
- physical docking and cargo transfer occur when vehicles return near stops;
- station chunk loading is configurable and can be disabled when another loader is used;
- active vehicle limits exist as a configurable control;
- route/schedule state survives chunk unloads, reloads, and server restarts.

Important limitations must shape Skyforge integration:

- routes are recorded paths, not dynamic pathfinding;
- there is no obstacle avoidance or automatic rerouting;
- loaded vehicles move physically and may collide with terrain or other vehicles;
- routes belong to their recorded vehicle rather than being generic shared paths.

Therefore Skyforge must not assume that Automated Logistics will perform dynamic navigation, hazard avoidance, or civilization-level routing decisions. Those remain Skyforge responsibilities or future integration work.

## Simulation tiers

### Tier 0 — Semantic only

Default for distant freight.

Persist only shipment / route facts needed by civilization and economy, such as origin, destination, cargo class/quantity, carrier, departure/arrival times, risk, status, and authoritative outcome.

No Aeronautics vessel, loaded chunks, crew AI, or physical cargo entity is required.

### Tier 1 — Scheduled physical route

Use when a route is close enough to the player, visually important, or operationally relevant.

A corresponding physical vehicle may be materialized/executed using the Aeronautics automation layer. Its semantic shipment identity remains authoritative.

### Tier 2 — Interactive encounter

Use only when the player can materially affect the shipment: boarding, piracy, escort, rescue, salvage, inspection, docking interaction, combat, or another direct intervention.

At this level physical simulation determines the encounter outcome, which is then collapsed back into authoritative semantic state.

## Materialization criteria

A shipment should become physical only when at least one of the following applies:

- it will be visible/near a player and contributes useful world activity;
- it is arriving at or departing from a player-relevant port;
- it is part of an accepted player contract;
- the player attempts to intercept or investigate it;
- a distress/security/salvage event becomes directly relevant;
- a curated authored scene explicitly requires it.

Do not materialize traffic merely because a semantic shipment exists.

## Route and corridor requirements

Because the current automation layer uses recorded paths rather than pathfinding, civilization-generated NPC freight must operate on known validated corridors/route legs.

Skyforge should treat the route network as semantic infrastructure and associate physical realizations with safe recorded trajectories. Dynamic hazards may cause the semantic layer to delay, divert, suspend, or choose another route; the physical addon should not be expected to discover a new safe path in real time.

## Minimal believable physical behavior

Initial NPC freight realization should require very little NPC intelligence. A believable freight operation needs primarily:

- depart;
- follow route;
- queue/hold when necessary;
- dock;
- transfer cargo;
- wait according to schedule/conditions;
- depart again;
- enter distress/interception state when directly relevant.

Crew can initially be sparse, role-based, and largely representational. Do not require continuously simulated crew routines, needs, conversations, or decision-making to make freight legitimate.

## Cargo authority and anti-duplication

Every materialized freight vessel must correspond to one authoritative semantic shipment. Physical cargo is an embodiment of that shipment, not an additional copy of it.

When an interactive encounter ends, cargo and vessel results must reconcile back to semantic state exactly once.

**Invariant:** semantic freight may become physical and physical freight may collapse back to semantic state, but materialization must never duplicate shipment value.

## Incident handling

Distant NPC-to-NPC freight incidents remain coarse. Do not create detailed insurance claims, wreck scenes, combat logs, or individual crew histories unless the incident becomes player-relevant.

Player-relevant incidents may promote a shipment to interactive physical simulation and then persist only the facts or altered scene state needed afterward.

## Piracy budget

Piracy remains computationally feasible because player piracy occurs only against physically relevant shipments. Distant NPC piracy remains semantic.

The system must never run persistent pirate pursuit, escort patrols, or Aeronautics combat across unloaded world space solely to maintain economic state.

## Performance rule

**Simulate physically when observation or player agency justifies the cost; otherwise simulate semantically.**

Skyforge should place explicit budgets/caps on concurrently materialized autonomous vessels and loaded logistics infrastructure. Exact limits are an implementation/performance-testing decision, not a civilization-design constant.

## Integration caution

Automated Logistics is a strong fit for the intended physical execution layer, but world-generated NPC traffic may require an adapter because its current route model is per-recorded-vehicle. Do not couple civilization semantics directly to addon-internal storage formats until an implementation spike confirms the cleanest integration boundary.
