# Cross-Dimension Route and Infrastructure Grammar v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design contract. Defines semantic route/infrastructure concepts without fixing terrain distances or concrete structure templates.

## Core principle

> Skyforge should author worlds in which routes are consequences of geography, capability, and value—not arbitrary lines drawn after terrain generation.

A route exists because:

- something worth reaching exists;
- a traveler has a capability envelope;
- the terrain admits one or more movement modes;
- the trip can be repeated or recovered from;
- the movement mode carries an appropriate payload.

The world generator should therefore reason about **route intent** before final realization.

## Shared route model

Candidate neutral model:

~~~text
ROUTE_NODE {
    domain
    role
    value
    services
    hazards
    stagingCapacity
    visibility
    recoveryValue
}

ROUTE_EDGE {
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
}
~~~

This is a semantic design model, not yet a code API.

## Route capability classes

Movement should be evaluated by capability, not raw distance alone.

Candidate classes:

~~~text
WALK
CLIMB
BRIDGE
ROPE_OR_LIFT
RAIL
GLIDE
THERMAL_GLIDE
ELYTRA_SOAR
SMALL_AIRCRAFT
CARGO_AIRCRAFT
LARGE_AIRSHIP
LAVA_CROSSING
PORTAL
GATEWAY
~~~

Future mechanics may add modes without changing the basic model.

## Payload classes

Reuse the freight distinction already established:

~~~text
PERSONAL
PERSONAL_PLUS_GEAR
PORTABLE_CONTAINER
BULK_ITEM
FLUID
ENTITY
MULTI_PLAYER
CONTRAPTION
AUTOMATED_THROUGHPUT
~~~

A route solved for PERSONAL is not automatically solved for BULK_ITEM.

This is critical to keeping gliders, Elytra, aircraft, rail, and portals meaningfully distinct.

## Directionality

Some movement is inherently directional.

Examples:

~~~text
GLIDE
    high -> low
    may require separate return route

THERMAL_GLIDE
    depends on lift availability and approach

RAIL
    usually reversible

POWERED_AIRCRAFT
    usually reversible if fuel / landing / weather allow

PORTAL
    usually bidirectional but topology may be constrained

END_GATEWAY
    semantically paired / progression-gated
~~~

Starting-region and progression-critical authoring must never assume:

~~~text
A can reach B
therefore
B can reach A
~~~

## Reliability

A route should carry a reliability class.

Candidate conceptual scale:

~~~text
OPPORTUNISTIC
    weather / timing / skill dependent

PREPARED
    player has built or discovered enabling infrastructure

ROUTINE
    normal operation succeeds under ordinary conditions

ALL_WEATHER / HIGH_RELIABILITY
    mature infrastructure, redundancy, instrumentation
~~~

No exact numeric probabilities are implied.

## Recovery profile

Every meaningful route should have a failure/recovery story.

Candidate classes:

~~~text
SELF_RECOVERABLE
    failure leaves traveler locally recoverable

ASSISTED_RECOVERY
    rescue / alternate mode / nearby site useful

HIGH_CONSEQUENCE
    failure can strand or destroy major assets

RITUAL / EXCEPTIONAL
    special progression edge with unique recovery rules
~~~

This matters especially in the End.

# Shared infrastructure roles

Infrastructure should be modeled by **service**, not by building skin.

Candidate roles:

~~~text
ARRIVAL_TERMINAL
DEPARTURE_STAGING
LANDING_SITE
AIRFIELD
MOORING_SITE
FUEL_DEPOT
REPAIR_SITE
WAREHOUSE
CARGO_TRANSFER
RAIL_TERMINAL
PORTAL_TERMINAL
GATEWAY_TERMINAL
BEACON
WEATHER_STATION
RADAR_SITE
NAVIGATION_MARKER
RESCUE_DEPOT
MINE
QUARRY
FARM
REFINERY
FOUNDRY
PROCESSING_SITE
MARKET
FORTIFICATION
SALVAGE_SITE
EXCEPTIONAL_DESTINATION
~~~

A concrete generated settlement may combine several roles.

## Player and civilization should share a visible language

Generated civilization should demonstrate the same route grammar the player can later reproduce.

Example:

~~~text
civilization mining route

MINE
    -> stockpile
    -> cargo transfer
    -> airfield / rail terminal
    -> warehouse / processor
~~~

The player should be able to imitate:

~~~text
my mine
    -> my loading area
    -> my aircraft
    -> my refinery
~~~

without learning a separate NPC infrastructure system.

## Infrastructure is optional realization of route semantics

A route does not require a generated road/rail/airfield.

Examples:

~~~text
natural walking saddle
    route exists
    no constructed infrastructure

glider edge
    route exists
    maybe only launch marker

civilization cargo lane
    route exists
    likely airfield / beacons / fuel

abandoned route
    route intent remains legible
    infrastructure may be broken
~~~

This allows Skyforge to author meaning without overbuilding the world.

# Overworld route grammar

## Core route question

> How do I turn separated geography into a productive network?

Overworld route networks should be broad, visible, and increasingly reliable.

## Common node roles

~~~text
STARTING_SETTLEMENT
FRONTIER_HOMESTEAD
AGRICULTURAL_SITE
MINE
QUARRY
INDUSTRIAL_SITE
OILFIELD
REFINERY
FUEL_DEPOT
AIRFIELD
MOORING_SITE
WEATHER_STATION
BEACON
REGIONAL_HUB
SALVAGE_SITE
EXCEPTIONAL_WILDERNESS_SITE
STRONGHOLD
~~~

## Common edge modes

~~~text
WALK / CLIMB
BRIDGE
ROPE_OR_LIFT
GLIDE
THERMAL_GLIDE
SMALL_AIRCRAFT
CARGO_AIRCRAFT
AIRSHIP
RAIL where terrain/infrastructure supports it
~~~

The Overworld should make the transition from opportunistic to routine movement highly visible.

### Example maturation

~~~text
P0
    walk / bridge

P0.5
    glide / thermal route

P2
    small aircraft

P3
    cargo route + fuel / landing

P4
    weather + navigation instruments

P5
    scheduled distributed logistics
~~~

## Overworld route cues

At distance:

- airfield clearings;
- hangars;
- smoke/plumes;
- navigation towers;
- weather masts;
- cultivated land;
- industrial silhouettes;
- route lighting.

At approach:

- landing direction;
- cargo apron;
- fuel/storage;
- windsock/weather cue;
- road/path to settlement.

At ground level:

- loading equipment;
- repair;
- storage;
- maps/instruments.

## Overworld worldgen obligations

Skyforge should be capable of authoring:

- clear approach volume for intended airfields;
- meaningful but not universally flat landing terrain;
- visible route landmarks;
- resource sites that justify movement;
- alternate routes for resilient mature regions;
- starter regions with directed traversal closure.

Do not create one universal "airfield island" morphology.

The same semantic role can be realized on many terrain families.

# Nether route grammar

## Core route question

> Is what lies beyond this obstacle worth making reliably reachable?

The Nether route system should be more constrained and more topological.

A player should often care about **the sequence of spaces** between nodes.

## Common node roles

~~~text
PORTAL_TERMINAL
SAFE_ROOM
FORTRESS_STATION
BASTION_APPROACH
PIGLIN_BARTER_SITE
WOLFRAMITE_MINE
QUARTZ_MINE
ANCIENT_DEBRIS_OPERATION
FOUNDRY
LAVA_CROSSING
MAJOR_VAULT
CHOKE_POINT
RAIL_JUNCTION
COMPACT_AIRCRAFT_STAGING
SALVAGE_SITE
EXCEPTIONAL_HOSTILE_SITE
~~~

## Common edge modes

~~~text
WALK
TUNNEL
BRIDGE
RAIL
COMPACT_AIRCRAFT
CARGO_AIRCRAFT in major chambers
LAVA_CROSSING
PORTAL
~~~

Large-airship edges should be exceptional and geometry-dependent.

## Nether route topology

A healthy region may look conceptually like:

~~~text
PORTAL TERMINAL
      |
      +-- tunnel --> QUARTZ DISTRICT
      |
      +-- rail --> FORTRESS STATION
      |
      +-- narrow choke --> BASTION APPROACH
      |
      +-- major vault
             |
             +-- compact-air route --> WOLFRAMITE MINE
             |
             +-- bridge / rail --> FOUNDRY
~~~

This is not a required literal topology.

It illustrates that different edges can reward different modes.

## Difficulty must purchase value

Nether traversal should not be difficult everywhere for its own sake.

A costly route should tend to lead toward one or more of:

- progression;
- renewable farm value;
- resource extraction;
- faction/structure content;
- advanced process capability;
- exceptional exploration.

The design target is:

~~~text
difficulty
    -> anticipation
    -> meaningful discovery
    -> infrastructure decision
~~~

not:

~~~text
difficulty
    -> more netherrack
~~~

## Corridor civilization

Generated Nether civilization/hostile infrastructure can make routes visible through:

- fortifications;
- bridges;
- patrol points;
- lava crossings;
- storage;
- extraction scars;
- abandoned tunnels;
- Piglin occupation;
- fortress connections.

The player should be able to infer:

> Someone cared enough about this route to defend it.

## Nether aircraft semantics

Aircraft are route modes, not universal free-space traversal.

### Compact aircraft

Strong for:

- vault crossing;
- scouting;
- rescue;
- lava bypass;
- moving modest valuable cargo.

### Cargo aircraft

Strong where:

- chamber size;
- landing/staging;
- route clearance;

support it.

### Rail/tunnel

Strong where:

- corridor geometry is constrained;
- repeated bulk throughput matters;
- weather/aircraft operation is inconvenient.

Skyforge should prefer geometry that creates these differences naturally.

## Nether worldgen obligations

If Skyforge authors the Nether, it should be able to reason about:

- major connected chambers;
- narrow connectors;
- staging shelves;
- lava barriers;
- portal-safe arrival volume;
- critical structure access;
- route redundancy;
- structure/faction claims on choke points;
- mining districts connected to viable export routes.

Do not guarantee an easy path to every site.

Guarantee that progression-critical sites have **some valid route**.

# End route grammar

## Core route question

> Can I reach that distant thing, survive the trip, and build enough infrastructure that the next expedition becomes possible?

The End route graph should emphasize:

- negative space;
- range;
- information;
- staging;
- recovery;
- sparse exceptional nodes.

## Common node roles

~~~text
CENTRAL_END_TERMINAL
DRAGON_ARENA
END_GATEWAY
OUTER_GATEWAY_STAGING
FORWARD_AIRFIELD
NAVIGATION_BEACON
RESCUE_DEPOT
END_CITY
END_SHIP
SHULKER_FACILITY
END_STONE_QUARRY
LEVITITE_PROCESSING_SITE
EXCEPTIONAL_ANOMALY
LEGENDARY_SITE
RETURN_GATEWAY
~~~

## Common edge modes

~~~text
GATEWAY
ELYTRA_SOAR
SMALL_AIRCRAFT
EXPEDITION_AIRCRAFT
LEVITITE_SUPPORTED_AIRCRAFT
CARGO_AIRCRAFT
rare bridge / local walk
~~~

The End should not require ground connections between all important nodes.

Negative-space crossing is part of its identity.

## Expedition chain

A healthy End network may emerge as:

~~~text
CENTRAL TERMINAL
      |
      v
END GATEWAY
      |
      v
OUTER STAGING
      |
      +-- scout --> NAVIGATION BEACON
      |
      +-- expedition --> END CITY
      |                    |
      |                    +--> END SHIP
      |
      +-- industrial route --> END STONE / LEVITITE SITE
      |
      +-- long-range route --> FORWARD DEPOT
                                 |
                                 +--> EXCEPTIONAL SITE
~~~

Again, this is semantic, not a required generated layout.

## Information as route infrastructure

The End should make information unusually valuable.

Potential infrastructure:

- coordinate records;
- beacon chains;
- radar;
- CC navigation;
- maps to structures where third-party content supports them;
- distinctive silhouettes;
- gateway pairing knowledge.

A navigation node can be valuable even if it contains little loot.

## Recovery as route infrastructure

End infrastructure should frequently answer:

> What happens if my aircraft fails between here and home?

Useful services:

~~~text
SPARE_GLIDER / ELYTRA KIT
SPARE_FUEL
REPAIR_PARTS
RESCUE_AIRCRAFT
SAFE_LANDING
FOOD / WATER
NAVIGATION_RECORD
RETURN_GATEWAY
~~~

The existence of these services can make a forward base economically rational even if the surrounding terrain is resource-poor.

## Levitite industrial route

If actual craft demand proves meaningful:

~~~text
END_STONE_QUARRY
    -> crushing
    -> zinc / water import
    -> heated mixing
    -> crystallization
    -> LEVITITE_PROCESSING_SITE
    -> aircraft works / export
~~~

This is a strong candidate for the End's most important industrial route.

Do not lock the scale until real vehicle tests establish material demand.

## End structure information chains

Third-party End content may support an especially useful route pattern:

~~~text
minor site
    -> clue / map / signal
    -> major site
    -> clue / route extension
~~~

This is preferable to independent random treasure structures.

Example semantic roles:

~~~text
OUTPOST / HOUSE
    -> information

STATION / SHIP
    -> staging / clue

ANCIENT TOWER
    -> encounter / reward

END CITY
    -> major expedition target
~~~

Skyforge should preserve or create these relationships where existing content allows them.

## End worldgen obligations

Skyforge should be able to author:

- meaningful line-of-sight or signal relationships;
- some destinations with viable landing/staging;
- vertical route differences;
- recovery nodes;
- gateway-safe destination volumes;
- enough negative space for route commitment;
- enough landmark variation for navigation;
- sparse high-value nodes.

Do not make every island safe to land on.

Do not make every long route unrecoverable.

# Cross-dimensional edges

Dimensions are themselves nodes in a larger progression/logistics graph.

Candidate high-level relationships:

~~~text
OVERWORLD
    -> NETHER PORTAL
        -> NETHER OPERATIONS

OVERWORLD
    -> STRONGHOLD / END PORTAL
        -> CENTRAL END
            -> END GATEWAY
                -> OUTER END
~~~

## Interdomain edge capabilities

Cross-dimension travel should explicitly consider:

~~~text
PLAYER
ITEM
PORTABLE_CONTAINER
ENTITY
ASSEMBLED_CONTRAPTION
FLUID / BULK FREIGHT
AUTOMATED_TRANSFER
~~~

Do not assume all portal/gateway edges support all classes.

This is particularly important for Aeronautics contraptions.

## Portal distance

Nether coordinate compression is a separate capability from Nether content.

Current interim prototype remains:

~~~text
coordinate_scale = 1.0
~~~

until broader portal topology is intentionally designed.

The route system must not accidentally let a cheap dimension edge dominate all Overworld regional movement.

# Route-value hierarchy

Not every node deserves mature infrastructure.

Candidate levels:

~~~text
DISCOVERY
    worth seeing once

EXPEDITION
    worth reaching repeatedly but not inhabiting

OUTPOST
    worth staging from

OPERATION
    worth repeated extraction/farm/process

HUB
    worth connecting multiple routes through

REGIONAL_ANCHOR
    major network value / redundancy
~~~

This prevents every structure from turning into a city.

## Structure versus route role

A structure should declare why it participates in the route graph.

Examples:

~~~text
END CITY
    EXPEDITION / loot / shulkers / landmark

NETHER FORTRESS
    PROGRESSION + FARM + ROUTE ANCHOR

OVERWORLD WEATHER STATION
    INFORMATION SERVICE

ABANDONED AIRFIELD
    SALVAGE + STAGING

BASTION
    HOSTILE FACTION + BARTER / SALVAGE
~~~

This makes structure placement downstream of gameplay meaning.

# Route evidence and legibility

A route should often leave evidence before the player fully discovers it.

Possible evidence:

- beacons;
- lights;
- smoke;
- rails;
- bridges;
- towers;
- wrecks;
- traffic;
- signs/maps;
- carved tunnels;
- cleared approaches;
- abandoned supply caches;
- patrols;
- fauna patterns;
- industrial plume.

The exploration loop becomes:

~~~text
SEE EVIDENCE
    -> INFER ROUTE / DESTINATION
    -> PREPARE
    -> TRAVEL
    -> DISCOVER
~~~

This is the infrastructure analogue of Skyforge's broader SEE -> WONDER -> IDENTIFY -> PLAN -> TRAVEL -> DISCOVER loop.

# Generated route plans

A future semantic planner may produce a route plan before concrete structures.

Candidate conceptual output:

~~~text
REGIONAL_ROUTE_PLAN {
    nodes
    requiredEdges
    optionalEdges
    progressionEdges
    freightEdges
    recoveryEdges
    civilizationEdges
}
~~~

Each edge can then constrain:

- terrain;
- structure reservation;
- resource placement;
- civilization layout;
- navigation cues.

Do not code this abstraction until authorship/implementation work proves the right interface.

# Acceptance tests

## ROUTE-1 — value before route

Every important generated route has a destination/service reason.

## ROUTE-2 — capability specificity

A personal route does not automatically imply a freight route.

## ROUTE-3 — directed traversal

Glide/thermal edges explicitly prove return/recovery where progression-critical.

## ROUTE-4 — no universal mode

At least some mature regions reward different movement modes.

## ROUTE-5 — infrastructure payoff

Building a depot, airfield, rail, corridor, beacon, or forward base materially improves repeated operation.

## ROUTE-6 — civilization legibility

Generated civilization routes use a spatial language players can understand and imitate.

## ROUTE-7 — Nether difficulty/value coupling

Tight or dangerous Nether traversal repeatedly leads to enough valuable nodes that route construction feels worthwhile.

## ROUTE-8 — End staging payoff

Forward End staging measurably increases safe expedition range, recovery, or operational capability.

## ROUTE-9 — portal non-dominance

Cross-dimension edges do not erase Overworld aviation geography or ordinary route planning.

## ROUTE-10 — structure compatibility

Progression-critical structures have at least one valid approach/return plan.

## ROUTE-11 — sparse realization

A semantic route graph does not require visible infrastructure on every edge.

## ROUTE-12 — explainability

A player-facing or debug explanation can trace:

~~~text
destination value
    -> route intent
    -> movement capability
    -> terrain accommodation
    -> infrastructure / evidence
~~~

# Immediate content-authoring consequences

## Overworld

Future authored province recipes should include:

- route-value nodes;
- candidate airfield/port sites;
- resource-to-hub relationships;
- beacon/weather coverage opportunities;
- alternate mature routes where justified.

## Nether

Future cavern-province recipe should include:

- critical node set;
- connectivity graph;
- edge-mode constraints;
- choke/vault hierarchy;
- portal foothold;
- fortress/bastion/resource relationships.

This should precede final cavern morphology.

## End

Future outer-End pilot should include:

- gateway arrival;
- at least one forward-staging candidate;
- one major expedition target;
- one navigation/recovery relationship;
- one Levitite/resource or exceptional-site relationship;
- explicit flight edges.

This should precede locking End landmass families.

# Acceptance principle

> Geography becomes gameplay when something worth reaching, a route worth learning, and infrastructure worth building all agree with one another.
