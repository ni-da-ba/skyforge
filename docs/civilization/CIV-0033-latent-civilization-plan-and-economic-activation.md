# CIV-0033 — Latent Civilization Plan and Economic Activation

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Basis in existing repository design

This decision extends, rather than replaces, the existing civilization architecture in:

- `docs/design-audit/civilization-and-settlement-system-v0.1.md`;
- `docs/design-audit/civilization-archetypes-and-infrastructure-teaching-v0.1.md`;
- `docs/design-audit/cross-dimension-route-and-infrastructure-grammar-v0.1.md`;
- the accepted Authorship handoff around AUTH-0087 / AUTH-0092 / AUTH-0096 / AUTH-0097 recorded on Bootstrap Province issue #224.

Existing repository direction is authoritative for the semantic hierarchy:

```text
PROVINCE CIVILIZATION CONTEXT
        ↓
CLUSTER CIVILIZATION PLAN
        ↓
ISLAND FUNCTIONAL ROLES
        ↓
CONCRETE MINECRAFT REALIZATION
```

The cluster remains the principal functional unit for medium and large settlements. This precommit does not introduce a second settlement hierarchy or a second backend geometry graph.

## Core decision

> Latent civilization is a thin persistent/derivable civilization plan over existing province, cluster, island-role, and route semantics. Detailed markets, inventories, shipment records, contracts, and physical actors are activated only where gameplay or economic resolution requires them.

The latent layer must be cheap enough to exist across a large procedural world while still explaining why settlements, specialized islands, routes, infrastructure, and regional traffic exist.

## Province-level latent context

Reuse the existing broad regional condition and coarse axes rather than creating a new macroeconomic object.

Candidate existing regional conditions include:

```text
WILD
FRONTIER
SETTLED
INDUSTRIAL
ABANDONED
CONTESTED
RESTRICTED
EXCEPTIONAL_ENCLAVE
```

Existing coarse axes include concepts such as:

```text
populationIntensity
infrastructureIntensity
maintenance
tradeIntensity
trafficIntensity
agricultureIntensity
industryIntensity
extractionIntensity
militaryPressure
historicalDepth
abandonment
conflictPressure
routeImportance
```

These values describe regional civilization pressure and capability. They do not imply per-capita simulation, continuously balanced markets, or individually simulated actors.

## Cluster-level latent plan

A cluster plan is the main latent economic/civilization unit for an ordinary settlement network.

It should contain or deterministically derive only information needed to answer questions such as:

- what kind of settlement/network this is;
- its population/activity class;
- faction/control and institutional relationship;
- maintenance/maturity state;
- which functional roles exist across its islands;
- which broad needs and capacities it supplies, consumes, imports, exports, or ignores;
- what transport/navigation/storage capability exists;
- which route relationships are structurally plausible/recognized;
- whether detailed economic state has been activated.

It should not require local villagers, exact shop inventories, individual companies, aircraft, historic shipments, or household accounts.

## Island functional roles

Reuse the existing island-role vocabulary as the spatial decomposition of a cluster plan, for example:

```text
RESIDENTIAL
MARKET_CIVIC
AGRICULTURAL
PASTORAL
QUARRY
MINE
INDUSTRIAL
AIRFIELD
DOCK_PORT
STORAGE
BEACON_NAVIGATION
WEATHER_STATION
RESOURCE_PROCESSING
DEFENSIVE
MILITARY
RUINED
...
```

Roles consume accepted terrain/geology/ecology/site evidence. They remain semantic roles, not promises that a specific structure template has already been instantiated.

The existing Authorship boundary remains intact: Content may rank/select role candidates from accepted published island associations and capability evidence; Authorship does not need to own a new civilization-route topology merely for this purpose.

## Broad latent needs/capacities

At the latent level, prefer the repository's existing coarse support vocabulary:

```text
FOOD
WATER
HOUSING
FUEL
RAW_MATERIAL
MANUFACTURED_GOODS
STORAGE
TRANSPORT
NAVIGATION
DEFENSE
TRADE
```

with relationships such as:

```text
SUPPLY
CONSUME
IMPORT
EXPORT
IGNORE
```

This is sufficient to establish a plausible support story and route pressure before detailed economics are needed.

The more detailed commodity ontology developed in later CIV precommits is a refinement at the activated economic layer. It should not force every latent settlement to maintain per-commodity stock and price state.

Example:

```text
LATENT CLUSTER
RAW_MATERIAL = EXPORT
FOOD = IMPORT
MANUFACTURED_GOODS = IMPORT
TRANSPORT = SUPPLY

        ↓ economic activation

DETAILED MARKET
iron / copper / fuel / provisions / components
local target stocks
current stocks
quotes
incoming/outgoing shipments
```

## Route semantics

Do not introduce a separate civilization geometry graph when the accepted authored publication already exposes the island associations/centers needed for Content route policy.

Route intent should consume the existing neutral route grammar concepts:

```text
ROUTE_NODE
    domain
    role
    value
    services
    hazards
    stagingCapacity
    visibility
    recoveryValue

ROUTE_EDGE
    source
    destination
    movementMode
    directionality
    payloadClass
    reliability
    preparationCost
    environmentalSensitivity
    recoveryProfile
    infrastructureRequirement
```

Guild/economic state may annotate recognized routes with commercial information such as traffic pressure, throughput, security state, or institutional recognition, but that is downstream of the shared route semantics rather than a competing topology.

## Latent inter-cluster economy

Unactivated regions do not require explicit shipments to maintain commercial plausibility.

Their coarse civilization plan may represent:

- structural import/export dependencies;
- route connectivity;
- broad traffic/trade intensity;
- support capacity;
- persistent major disruptions or control conditions.

Regional commerce can therefore be reasoned about as coarse flow pressure over the sparse route network.

Do not create one persistent shipment object for every implied NPC movement across the world.

A concrete shipment record is justified when at least one of the following becomes true:

```text
- a market transaction or contract requires custody/accounting;
- a player-visible or player-interceptable freighter is selected for realization;
- an incident becomes gameplay-relevant;
- a player-owned commercial operation participates;
- a detailed activated market must reconcile an actual arrival/departure;
```

Otherwise, aggregate route/flow state is sufficient.

## Activation boundary

Economic activation is independent of player discovery.

A cluster may be activated because:

- the player approaches or queries its market;
- a Guild information service needs a detailed quote/catalogue;
- a player contract or shipment references it;
- an activated neighboring market needs a concrete supplier/customer relationship;
- an incident or authored event makes it directly relevant.

Player discovery by itself need not force every possible subsystem to instantiate, and lack of player discovery does not mean the settlement is nonexistent.

## Hierarchical lifecycle

Preferred conceptual lifecycle:

```text
PROCEDURAL WORLD / AUTHORED PUBLICATION
        ↓
PROVINCE CIVILIZATION CONTEXT
        ↓
CLUSTER LATENT PLAN
        ↓ as required
ACTIVATED CLUSTER ECONOMY
        ↓ as required
CONCRETE MINECRAFT REALIZATION
```

The activated cluster economy may contain persistent stocks, catalogues, prices, detailed demand, supplier relationships, shipment records, Guild state, and player-caused history.

Minecraft realization remains a separate observation/interactivity step and must not be equated with economic activation.

## Persistence policy

Persist only state that can no longer be safely reconstructed from authoritative procedural inputs or that records consequential history.

Likely persistent examples:

- activated economic state;
- player-caused market changes that have not yet reconciled;
- recognized/altered route status where history matters;
- player commercial facilities;
- actual shipment/incident records with custody or consequences;
- damaged/restored civic capability where physical modification matters.

Pure latent descriptors that remain deterministic may be regenerated rather than individually serialized, subject to implementation profiling and versioning requirements.

## Minecraft materiality constraint

Existing civilization design requires ordinary Minecraft modification to remain meaningful.

Semantic capability must therefore not silently preserve a service whose required realized infrastructure has been physically destroyed once that infrastructure is gameplay-relevant and tracked.

The latent plan describes intended/available civilization capability before realization. Once a specific civic asset or service has been realized and materially altered by the player, reconciliation must respect the physical consequence rather than regenerating the semantic fiction unchanged.

## Computational constraints

The latent civilization system must not require:

- per-tick global economy updates;
- all-pairs settlement connectivity;
- per-NPC simulation;
- per-aircraft off-screen simulation;
- detailed inventories for every settlement;
- retroactive shipment histories;
- a duplicate Authorship regional geometry graph.

Prefer:

```text
province context
+ sparse cluster plans
+ functional island roles
+ sparse route intent/edges
+ broad need/capacity relationships
+ lazy detailed activation
```

## Design invariants

> Civilization is a regional condition and functional network, not a structure-placement probability.

> The cluster is the principal functional unit for settlement-scale civilization planning.

> Latent civilization carries structure and causality; activated economics carries accounting.

> Broad latent needs/capacities refine into detailed commodities only when detailed economics is required.

> Existing authored island associations and capability evidence supply geometry; civilization Content owns role and route policy.

> Do not pay the persistence or simulation cost for detail until that detail can affect gameplay.
