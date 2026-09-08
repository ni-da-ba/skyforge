# Aircraft Reuse, Automation, and Capture Governance v0.1

**Snapshot:** 2026-09-07  
**Status:** Working design direction from initial external-content reconnaissance. Not an accepted runtime milestone.

## Purpose

Record the durable conclusions from the first audit of reusable Create Aeronautics / Sable community
aircraft and automation patterns.

The audit changes the default assumption:

> Skyforge does not need to bespoke-author both the visual aircraft and the complete automation stack
> for every inhabited-airspace role.

Aircraft appearance, physical flight architecture, automation, encounter role, and player-acquisition
policy should be treated as separable concerns.

## Reuse doctrine

Preferred order for inhabited-airspace vehicles:

1. use an existing licensable functional schematic where it already fits;
2. adapt an attractive existing schematic with a proven automation/control package;
3. reuse proven mechanical/control patterns while re-authoring the exterior where redistribution or
   stylistic fit is unsuitable;
4. create a Skyforge-specific aircraft only where retained content leaves a real functional or
   aesthetic gap;
5. write bespoke flight-control/runtime code only after upstream avionics, CC:Tweaked, radar, and
   ordinary Create/Sable control mechanisms demonstrably fail.

A visually strong schematic does **not** need to have been authored as an autonomous craft. If its
mass, control surfaces, propulsion, stability, and dependency surface are acceptable, automation may
be added as a separate layer.

## Separation of concerns

Treat a realized aircraft conceptually as:

```text
AIRFRAME / VISUAL SCHEMATIC
    physical blocks, silhouette, mass, propulsion, control surfaces
        +
CONTROL PACKAGE
    manual controls and/or Avionics / CC / radar integration
        +
MISSION PACKAGE
    patrol, courier, cargo, escort, intercept, transit, etc.
        +
WORLD ROLE
    civilian, faction, salvage, wreck, exceptional
        +
ACQUISITION POLICY
    not recoverable intact / salvage-only / capture-eligible
```

This permits one attractive airframe to support multiple service variants without cloning a new
aircraft design for each role.

## What the initial external audit established

The public Create Aeronautics / Simulated ecosystem contains enough demonstrated examples to justify
a reuse-first R&D program rather than assuming a bespoke vehicle library.

Observed useful pattern classes include:

- compact fixed-wing aircraft;
- stable cargo and patrol airships;
- autonomous or assisted route-following craft;
- CC/computer-instrumented aircraft;
- radar-equipped aircraft;
- large armed cruisers and turreted vehicles;
- VTOL / tilt-rotor control experiments.

The individual external candidates, exact dependency versions, licenses, and current behavior must be
reverified before shipping. Ordinary community schematics without explicit redistribution permission
are reference/prototype material rather than automatically distributable Skyforge assets.

The persistent conclusion is architectural rather than candidate-specific:

> Community schematics can supply hulls, flight-layout patterns, stability solutions, and control
> examples; Skyforge should compose these with retained automation systems instead of solving every
> aircraft problem from first principles.

## Automation does not imply landing

Not every materialized aircraft requires a complete takeoff-land-service lifecycle.

Useful encounter/traffic classes include:

### Transit-only

Craft enters the active area on a plausible route, crosses it, then exits.

No landing requirement.

### Patrol / intercept

Craft materializes already airborne, patrols/investigates/intercepts, then exits or folds back into
coarse faction state.

No routine landing requirement.

### Airship stationkeeping

Craft loiters, orbits, or moves between broad waypoints.

Landing may be replaced by docking only where the role actually needs it.

### Destination traffic

A smaller subset of civilian/cargo craft may need reliable approach, docking, or landing because the
player is expected to observe the complete route.

### Persistent consequence

A craft that crashes, is captured, boarded, materially modified, or otherwise becomes consequential
through player action may remain physical even if its ordinary traffic lifecycle would have folded
back into coarse state.

Therefore arbitrary autonomous landing is **not** a prerequisite for inhabited-airspace viability.

## Fidelity boundary for generated aircraft

Generated civilian/faction aircraft do not need player-equivalent internal engineering fidelity merely
because they are rendered as real Sable/Create Aeronautics contraptions.

The preferred rule is:

> Simulate the physical behavior the player can directly observe or affect; abstract routine
> operating logistics that exist primarily to justify the encounter.

Examples that may remain coarse for ordinary transient traffic:

- fuel quantity and refueling cycles;
- ammunition magazines and reload labor;
- maintenance intervals;
- crew provisioning;
- spare parts;
- strategic sortie generation;
- hangar turnaround;
- route scheduling.

The corresponding infrastructure should still exist semantically and, where useful, physically in
the world. A faction airfield may therefore contain:

- fuel tanks / refinery or fuel-service structures;
- ammunition storage;
- workshops / maintenance hangars;
- radar / navigation infrastructure;
- parking or launch aprons;
- representative aircraft patterns.

Those structures justify and constrain the regional traffic capability without requiring every
materialized aircraft to contain a literal full fuel or ammunition simulation.

### Coarse capability model

A useful future regional/faction model may expose bounded capabilities such as:

```text
aircraftAvailability
sortieCapacity
fuelSupport
munitionSupport
maintenanceSupport
radarCoverage
routeCoverage
```

These are semantic capability/resource states, not necessarily item stacks.

They may control whether a patrol, cargo flight, interceptor, or armed encounter is eligible to
materialize.

Examples:

```text
airfield + fuel support
    -> ordinary powered traffic eligible

military site + munition support
    -> armed patrol / interceptor eligible

radar site destroyed
    -> reduced detection / intercept capability

fuel depot destroyed
    -> reduced or suspended powered sortie capability
```

This preserves physical causality at the infrastructure level without simulating each aircraft as an
independent logistics inventory.

### Materialized encounter fidelity

Once an aircraft is active near the player, simulate only the quantities needed for credible play.

Examples:

- bounded mission duration/range may stand in for exact fuel burn;
- bounded firing opportunities / weapon cooldown / sortie munition budget may stand in for literal
  magazine inventories;
- visible engine, wing, control, radar, and weapon damage should remain physically meaningful where
  the retained vehicle stack supports it;
- an aircraft should not obviously fire forever or remain airborne indefinitely after its supporting
  capability should have expired.

If a vehicle becomes player-owned, captured, or deliberately persistent, it may transition to a
different fidelity class. Player-operated craft should obey the ordinary player-facing fuel,
ammunition, repair, and engineering systems appropriate to that vehicle.

Thus:

```text
NPC TRAFFIC FIDELITY
    !=
PLAYER VEHICLE FIDELITY
```

without requiring the NPC craft to be visually fake.

### Infrastructure before invisible bookkeeping

Prefer visible world infrastructure over hidden per-aircraft simulation when choosing where to spend
complexity.

A fuel depot, ammunition bunker, maintenance hangar, radar tower, and parked-aircraft pattern can
communicate a functioning air arm more effectively than thousands of invisible per-vehicle inventory
updates.

Destroying or capturing that infrastructure may alter future encounter eligibility, creating
meaningful world consequences at far lower simulation cost.

## Player-acquisition invariant

> Generated aircraft must not make player aircraft construction economically optional before the
> intended reliable-flight transition.

An intact autonomous aircraft is far more progression-sensitive than an ordinary building because a
single successful theft can directly grant regional mobility.

The default must therefore distinguish:

```text
VISIBLE / ENCOUNTERABLE
    does not imply

CAPTURE-ELIGIBLE INTACT
```

## Capture policy

Prefer systemic/content solutions before magic ownership protection.

### Before reliable player flight

Ordinary generated traffic should not provide an easy turnkey aircraft.

Prefer one or more of:

- traffic remains airborne rather than parked unattended;
- AI-only/service layouts omit a convenient manual-control package;
- defeat/crash states convert the vehicle into bounded salvage rather than an intact prize;
- fuel and progression-sensitive components are deliberately budgeted;
- grounded active-airfield examples are incomplete, under maintenance, static demonstrations, or
  otherwise not a free complete vehicle;
- hostile craft present enough combat/boarding risk that any recovered components are an intentional
  salvage reward.

Do not solve this by making whole aircraft inexplicably unbreakable.

### After reliable player flight

Intact capture can become an intentional reward.

Possible later experiences include:

- boarding and stealing a hostile patrol craft;
- recovering an abandoned airship;
- repairing a damaged derelict;
- capturing an advanced vehicle whose operation still requires appropriate fuel/infrastructure.

At this stage capture expands the player's vehicle vocabulary rather than deleting the core
shipbuilding/aircraft-building progression.

## Salvage policy

Destroyed or disabled generated aircraft may be valuable without being complete vehicles.

Their salvage budget should follow the existing civilization rules:

- common structure/frame material may be freely recoverable;
- progression-sensitive propulsion, radar, computing, or weapon components must be quantity-budgeted;
- hostile/abandoned aircraft may yield better salvage than active civilian traffic;
- one encounter must not jump the player several capability stages unless deliberately designed as an
  exceptional reward.

## Aircraft-family reuse

Prefer service variants over unrelated one-off craft.

Example:

```text
UTILITY AIRFRAME
    -> civilian courier
    -> survey aircraft
    -> light cargo
    -> faction patrol
    -> wreck / derelict
```

and:

```text
SMALL AIRSHIP
    -> merchant
    -> cargo
    -> customs / patrol
    -> raider
    -> abandoned
```

Variant differences may primarily be:

- palette / markings;
- cargo;
- weapons;
- sensors;
- avionics/computer program;
- fuel load;
- crew/faction;
- damage state.

This maximizes asset leverage and makes technological relationships legible to the player.

## First prototype direction

Do not begin with general dogfighting AI.

Preferred proof order:

1. one stable existing or adapted airship flies a bounded autonomous transit/patrol route;
2. one fixed-wing craft holds useful heading/altitude/speed through retained avionics controls;
3. one moving target can be detected and investigated/intercepted;
4. only then add weapons, capture states, or more sophisticated tactical behavior.

At each step, measure how much truly bespoke Skyforge code was required.

If the proof demands a large custom flight-control, pathfinding, or fleet-simulation subsystem,
downgrade the feature before expanding scope.

## Acceptance principle

> Build an inhabited sky by composing attractive reusable aircraft, proven physical layouts, and
> retained automation tools. Make intact vehicle capture deliberate progression content, and require
> landing only for aircraft whose world role actually needs it.
