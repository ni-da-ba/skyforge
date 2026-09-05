# Dimension World-Grammar Matrix v0.1

**Snapshot:** 2026-09-05
**Status:** Working comparison matrix supporting the cross-dimension authorship strategy.

## Purpose

Identify which Skyforge concepts should plausibly be shared across Overworld, Nether, and End, and which concepts require dimension-specific semantics.

The goal is not to design all three dimensions now.

The goal is to prevent two opposite architecture failures:

~~~text
FAILURE A
copy every Overworld system three times

FAILURE B
force every dimension into one vague universal ontology
~~~

## Reuse vocabulary

~~~text
SHARED
    same semantic concept can plausibly survive

PROFILED
    same high-level concept exists but needs dimension-specific meaning/parameters

SPECIALIZED
    domain-specific subsystem or grammar is probably justified

N/A
    concept should not be forced into the dimension
~~~

## Matrix

| Axis | Overworld | Nether | End | Reuse direction |
|---|---|---|---|---|
| First-order occupancy | Air-dominant | Solid/enclosed-dominant | Void-dominant | PROFILED |
| Main authored spatial unit | Suspended island | Cavern/vault/rock system | End landmass/shard | SPECIALIZED |
| Province | Regional sky geography | Cavern/thermal/faction region | Sparse void/landmass region | SHARED |
| Cluster/system | Island cluster | Connected vault/passages | Landmass constellation | PROFILED |
| Exact 3D ownership | Required | Required | Required | SHARED |
| Deterministic planning | Required | Required | Required | SHARED |
| Primary morphology | Island family | Cavern/vault family | End landmass family | PROFILED/SPECIALIZED |
| Secondary morphology | Ridges, shelves, spurs | Columns, arches, shafts, ledges | Fractures, shards, needles, rings | PROFILED |
| Signals/detail | Bounded enrichment | Bounded enrichment | Bounded enrichment | SHARED |
| Material semantics | Geology/soil/lithology | Infernal lithology/thermal alteration | End-stone/alien material domains | PROFILED |
| Ordinary water hydrology | Core | N/A by default | N/A by default | SPECIALIZED |
| Fluid-system analogue | Water cycle/runoff | Lava/magmatic/thermal system | Rare/anomalous if any | SPECIALIZED |
| Surface concept | Upper/side/underside | Interior interfaces/margins | Upper/side/underside | PROFILED |
| Sky exposure | Major | Mostly irrelevant except huge vaults | Major | PROFILED |
| Ecology | Terrestrial/aerial/aquatic | Fungal/thermal/infernal | Sparse alien | PROFILED |
| Civilization | Villages/routes/industry | Piglin/fortress/bastion networks | Ruins/End cities/exceptional sites | PROFILED |
| Atmosphere | Wind/weather/thermals | Enclosed convection/heat/turbulence | Sparse/anomalous domain behavior | PROFILED |
| Navigation | Horizon + beacons + weather | Corridors + landmarks + maps | Void landmarks + gateways | PROFILED |
| Aviation | Foundational logistics | Constrained chamber/corridor aviation | Long void crossing / late expedition | PROFILED |
| Resource geography | Metals/agriculture/fuel | Quartz/Wolframite/heat/blaze/etc. | Chorus/shulker/End loot/etc. | PROFILED |
| Native critical sites | Stronghold etc. | Fortress/bastion/portal | Dragon arena/gateways/End cities | SPECIALIZED |
| Structure admission | Semantic + geometry | Semantic + enclosure geometry | Semantic + landmass geometry | SHARED framework |
| Structure realization | Surface/subsurface/detached | Embedded/bridged/fortified | Surface/detached/tower/ship | PROFILED |
| Negative-space composition | Open sky between islands | Caverns cut through solid | Void between landmasses | PROFILED |
| Long-distance visibility | Fundamental | Limited/conditional | Fundamental | PROFILED |
| Distant Horizons value | Very high | High in large vaults | Very high | PROFILED |
| Progression role | Primary living/industry world | Midgame dimension/resource world | Late ritual/expedition world | SPECIALIZED |
| Portal/gateway topology | Source endpoints | Cross-domain portal network | Ritual/gateway network | SHARED edge concept |
| Spawn/population governance | Skyforge semantic budgets | Skyforge semantic budgets | Skyforge semantic budgets | SHARED framework |
| Explainability/provenance | Required | Required | Required | SHARED |
| Backend realization | Minecraft blocks/biomes/features | Minecraft blocks/biomes/features | Minecraft blocks/biomes/features | SHARED boundary |

## Strongly shared concepts

These should be presumed reusable until evidence disproves it.

### Determinism

~~~text
root seed
    -> stable semantic namespaces
    -> repeatable world plan
    -> chunk-order-independent realization
~~~

No dimension should get a weaker determinism contract.

### Provenance

Every authored result should remain traceable to semantic cause.

The noun changes:

~~~text
Overworld:
block -> island -> cluster -> province

Nether:
block -> cavern/vault system -> province

End:
block -> landmass -> constellation -> province
~~~

The explainability obligation does not.

### Exact three-dimensional ownership

This is likely one of the most valuable general results from the current Overworld implementation.

All three dimensions need to distinguish physically overlapping or neighboring authored domains.

Examples:

- stacked Overworld islands;
- Nether chambers separated by rock;
- overlapping End landmasses at different elevations;
- native structure versus authored terrain ownership.

The current concrete island ownership API may change later, but the **3D ownership principle** should not.

### Structure meaning before placement

Each dimension benefits from:

~~~text
semantic site intent
    -> geometry suitability
    -> structure realization
~~~

The suitability variables differ.

The structure-authority principle does not.

### Population ownership

Vanilla/mod mobs should not independently erase authored ecological or threat structure merely because the dimension changes.

The same general population-governance concept can survive with different niche/faction rules.

## Profiled concepts

These need a common interface or policy concept, but not common environmental values.

### Environment profile

Candidate semantic contract:

~~~text
DOMAIN_ENVIRONMENT_PROFILE {
    light regime
    heat regime
    wind / flow regime
    weather capability
    convection capability
    visibility regime
    ambient hazard regime
}
~~~

Examples:

~~~text
OVERWORLD
    ordinary weather
    ordinary wind
    thermals
    precipitation

NETHER
    fixed hostile heat regime
    no rain
    convective plumes
    enclosed flows
    particulate / haze

END
    no ordinary terrestrial weather
    unusual light
    sparse/anomalous flow
    void exposure
~~~

Do not force unused fields to generate fake behavior.

### Mobility profile

Candidate capability environment:

~~~text
DOMAIN_MOBILITY_PROFILE {
    ordinary walking viability
    bridging viability
    glider viability
    powered-aircraft viability
    large-airship viability
    visibility range
    portal/gateway dependence
}
~~~

This lets the same vehicle have different practical niches by dimension without arbitrary item bans.

### Resource profile

The same resource-geography machinery can likely classify:

~~~text
UBIQUITOUS
COMMON_REGIONAL
SPECIALIZED_REGIONAL
STRATEGIC_NODE
EXCEPTIONAL
~~~

while each dimension provides different actual resources and causes.

## Specialized concepts

These should not be generalized until a real implementation requires a shared abstraction.

### Overworld water hydrology

The existing runoff/watershed/waterbody system should remain an Overworld-oriented subsystem unless another dimension genuinely needs analogous water behavior.

### Nether magmatic-fluid system

The Nether likely needs its own causal vocabulary:

~~~text
heat source
magma conduit
lava basin
lava fall
cooled margin
vent region
thermal plume
~~~

It may reuse field/planner infrastructure without pretending this is terrestrial hydrology.

### End ritual-center system

The Dragon fight is not an ordinary structure-placement problem.

It needs a dedicated compatibility/special-site contract.

### Nether enclosed-space topology

Cavern connectivity is likely more important than surface drainage or island-group spacing.

Candidate concerns:

- connected-component topology;
- route redundancy;
- choke points;
- chamber hierarchy;
- vertical connectivity;
- lava barriers;
- fortress/bastion route access.

This should be developed as a real Nether need before extracting universal graph abstractions.

## Candidate cross-domain neutral interfaces

These are design targets only, not implementation instructions.

### Domain authority

~~~text
DOMAIN_AUTHORITY {
    minecraftDimensionKey
    terrainAuthority
    populationAuthority
    structureAuthority
    resourceAuthority
    environmentAuthority
}
~~~

Possible authority values:

~~~text
VANILLA
SKYFORGE
HYBRID
SPECIAL_SITE
~~~

This would allow incremental migration.

Example:

~~~text
END
    central dragon region -> SPECIAL_SITE / VANILLA initially
    outer terrain -> SKYFORGE pilot
~~~

### Authored spatial owner

Potential future abstraction:

~~~text
AUTHORED_SPATIAL_OWNER
    stable identity
    finite/conservative bounds
    density/occupancy
    provenance
    semantic parent
~~~

Overworld island volumes could implement this.

A Nether cavern province might use it differently.

Do not extract this interface until the second real domain demonstrates the exact shared contract.

### Interdomain edge

~~~text
INTERDOMAIN_EDGE
    source
    destination
    activation
    progression
    coordinate mapping
    throughput
    freight/entity behavior
~~~

This can unify reasoning about:

- Nether portals;
- End portals;
- End gateways;

without requiring identical Minecraft mechanics.

## Cross-dimension progression matrix

| Stage | Overworld | Nether | End |
|---|---|---|---|
| Bootstrap | Primary | Not required | Not required |
| First gliding | Primary | N/A/optional | Not yet |
| First powered flight | Primary | Not required | Not required |
| Early regional industry | Primary | Optional entry possible | Not yet |
| Dimension progression | Supplies portal/stronghold route | Quartz/blaze/Nether resources | Dragon gate |
| Mature logistics | Regional aviation network | Resource/industrial expedition routes | Outer-End expedition/logistics |
| Late mobility | Aircraft + personal soarers | Compact/cavern craft possible | Elytra/powered void travel |
| Exceptional play | Legendary sites | Deep hazardous regions | Dragon/End-city/outer exceptional sites |

This is deliberately not a hard progression tree.

It records the current relationship of each dimension to the Skyforge experience.

## Cross-dimension civilization matrix

### Overworld

Primary themes:

- settlement;
- trade;
- agriculture;
- mining;
- industry;
- aviation infrastructure.

### Nether

Primary themes:

- hostile/alien civilization;
- fortified routes;
- extraction;
- bastions;
- fortresses;
- hazardous trade corridors.

### End

Primary themes:

- ruins;
- absent civilization;
- mysterious long-range infrastructure;
- End cities/ships;
- exceptional rather than ordinary habitation.

This creates three distinct readings of "civilization":

~~~text
Overworld -> living network
Nether    -> contested hostile network
End       -> sparse remnant / mystery
~~~

## Cross-dimension route grammar

### Overworld

~~~text
SEE
-> IDENTIFY
-> PLAN
-> FLY
-> LAND
-> HAUL
~~~

### Nether

~~~text
MAP
-> FIND SAFE CORRIDOR
-> CROSS CHOKE / LAVA / VAULT
-> REACH FORTIFIED OR RESOURCE NODE
-> RETURN / ESTABLISH ROUTE
~~~

### End

~~~text
GATE
-> ORIENT IN VOID
-> IDENTIFY DISTANT LANDMARK
-> CROSS NEGATIVE SPACE
-> EXPLORE EXCEPTIONAL SITE
-> FIND RETURN / NEXT GATEWAY
~~~

The player should feel a different route-planning discipline in each world.

## Dependency audit implication

Any mod that changes one dimension substantially should be classified against:

~~~text
TERRAIN AUTHORITY
BIOME AUTHORITY
STRUCTURE AUTHORITY
RESOURCE AUTHORITY
POPULATION AUTHORITY
ENVIRONMENT AUTHORITY
TRAVEL AUTHORITY
~~~

A mod may be acceptable in one category and unacceptable in another.

Example:

~~~text
Nether content mod
    structures -> KEEP
    mobs -> KEEP / governed
    world terrain -> DISABLE if Skyforge owns it
    ores -> REDIRECT
~~~

This is the same reuse-first principle already used in the Overworld, expanded dimensionally.

## Acceptance principle

> Share contracts where the dimensions share meaning. Share machinery where they share mathematics. Keep separate grammars where their worlds are supposed to feel different.
