# CIV-0021 — Route Formation, Evolution, and Logistics Inspiration

Status: PRECOMMIT LOCK

## Decision

Skyforge inter-settlement transport shall be represented as a sparse procedural graph whose edges emerge from authoritative civilization semantics rather than from a manually authored universal route list.

Candidate routes are scored from settlement complementarity, population/demand importance, strategic importance, distance, hazard, infrastructure burden, and network value. Existing routes possess institutional inertia and are reconsidered only on coarse intervals or when meaningful state changes occur.

The resulting graph should produce feeder links, regional hubs, major corridors, alternate routes, frontier spurs, and strategic chokepoints rather than universal direct connectivity.

## Real-logistics inspiration

Skyforge should borrow structural logic from real transport systems rather than reproduce modern logistics literally.

Useful principles include:

- **Hub-and-spoke consolidation.** Small flows aggregate through hubs because consolidation creates economies of scale and supports reliable long-haul movement.
- **Transshipment and centrality.** A node can become important because of its network position even if it is not itself a large producer or population center.
- **Reliability as a first-class variable.** A shorter route may be economically worse than a longer route if delays, hazards, congestion, or failures are more likely.
- **Capacity constraints.** Routes and hubs should have bounded semantic throughput; disruption or rerouting can consume spare capacity elsewhere.
- **Chokepoints and alternate corridors.** A small number of strategically important links can carry disproportionate traffic, but mature networks may develop redundancy where the economic value justifies it.
- **Feeder versus trunk service.** Small settlements generally connect to regional hubs, while high-volume or strategically important pairs may sustain direct long-range service.
- **Backhaul imbalance.** Complementary production and demand should matter. Routes with valuable cargo in one direction and little return traffic should behave differently from balanced corridors.
- **Infrastructure inertia.** Existing docks, beacons, warehouses, agencies, and route knowledge make established links more durable than a purely instantaneous optimizer would suggest.
- **Disruption-driven adaptation.** Sustained hazard, piracy, infrastructure failure, congestion, or economic change may cause routes to degrade, divert, strengthen, or disappear.

These principles are semantic inputs to Skyforge's civilization and logistics systems, not prescriptions for exact modern freight economics.

## Route formation

A route becomes attractive when there is sufficient benefit to justify the cost and risk of maintaining the connection. Conceptually:

```text
route_value =
    trade_complementarity
  + settlement importance
  + strategic importance
  + network centrality benefit
  - travel cost
  - hazard cost
  - infrastructure cost
```

The exact implementation may differ, but the evaluation should remain explainable and deterministic.

Candidate generation should be sparse and bounded. Geographic proximity, regional membership, existing network topology, economic complementarity, and strategic criteria should reduce the candidate set before scoring. Skyforge should not continuously perform unrestricted all-pairs global optimization.

## Hubs and transshipment

Settlement population alone does not determine hub status. A small settlement may become a major transfer point if it lies on a safe crossing, connects otherwise separated regions, has strong infrastructure, or offers an efficient transshipment location.

Typical topology:

```text
small settlement ─┐
                  ├─ regional hub ─ major corridor ─ regional hub
resource outpost ─┘                                  └─ frontier feeder
```

Direct links may appear where traffic volume, strategic importance, or reliability benefits justify bypassing intermediate hubs.

## Route persistence and evolution

Routes should have lifecycle state such as:

```text
PROPOSED
ACTIVE
DEGRADED
DIVERTED
SUSPENDED
ABANDONED
```

Formation and retention thresholds should differ. Established routes persist through modest short-term fluctuations because infrastructure and institutional investment create inertia.

Routes are reconsidered on meaningful changes such as:

- settlement growth or decline;
- major changes in production or demand;
- new infrastructure;
- discovery or recognition of a node;
- sustained piracy or other hazard;
- repeated route failures;
- dragon or weather changes;
- player establishment of an authorized Guild agency;
- loss or restoration of critical navigation infrastructure.

The network should not thrash in response to minor temporary price movement.

## Redundancy and fragility

Major regions may support alternate routes where the economic or strategic value justifies resilience. Small frontier settlements may legitimately depend on a single vulnerable connection.

A disruption should therefore produce consequences proportional to topology:

```text
major route failure
    -> diversion to alternates
    -> capacity pressure / longer travel
    -> moderate shortages and price changes
```

or, for an isolated frontier node:

```text
sole feeder failure
    -> severe connectivity loss
    -> larger shortage / contract / recovery pressure
```

## Player influence

Players may eventually alter the graph by discovering settlements, surveying routes, constructing or repairing navigation infrastructure, establishing Guild agencies, proving new freight connections, or suppressing hazards.

Player-built infrastructure should become part of the same civilization network rather than a separate player-only economy.

## Computational constraint

The authoritative graph remains coarse semantic state. Route economics and evolution are evaluated periodically or event-driven. Physical aircraft realization remains delegated to the Minecraft backend and Aeronautics Automated only when observation or interaction requires it.

Rendered traffic is a representation of semantic throughput, not the numerical source of economic throughput.

## Canonical rules

> Transport routes emerge from settlement complementarity, importance, geography, hazard, infrastructure, and network value.

> Skyforge should borrow the structural logic of real logistics chains—hub concentration, feeder service, reliability, capacity, transshipment, chokepoints, and adaptation—without requiring a full logistics-industry simulation.

> Existing routes possess institutional inertia and evolve only on coarse intervals or meaningful state changes.

> Players may eventually reshape the civilization graph through discovery, infrastructure, safe operation, and Guild integration.
