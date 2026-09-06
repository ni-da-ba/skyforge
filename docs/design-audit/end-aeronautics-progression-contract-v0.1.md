# End Aeronautics Progression Contract v0.1

**Snapshot:** 2026-09-05
**Status:** Working design direction based on current Create Aeronautics / Sable source. Exact balance and End terrain remain unproven until in-game testing.

## Core rule

> The End should not merely contain late-game flight loot. It should introduce a new aeronautical problem and reward the player with a new aeronautical capability.

Current Aeronautics/Sable source already gives Skyforge a promising gameplay loop without inventing bespoke technology:

~~~text
enter End
    -> ordinary aircraft encounters thinner-air performance
    -> End Stone becomes an aeronautical raw material
    -> process End Stone into Levitite technology
    -> build craft that can remain afloat without Levitite itself providing climb
    -> combine levitation with propulsion/control
    -> operate new vehicle classes across the End and beyond
~~~

This is a stronger design basis than choosing End terrain first.

## Source-backed Aeronautics behavior

### End pressure

Sable supplies a dimension-specific End pressure profile, but **altitude-dependent pressure loss is not unique to the End**.

All three built-in vanilla Sable profiles decrease pressure with altitude.

The relevant difference is the curve's reference altitude and usable envelope:

~~~text
OVERWORLD
    pressure ~= 1.0 at Y 63

NETHER
    pressure ~= 1.0 at Y 32

END
    pressure = 1.0 at Y 0
~~~

So the End may be thinner at ordinary island elevations than the Overworld at comparable Y, but the End should not be designed around a false premise that only it has an altitude-pressure gradient.

Its current built-in values are approximately:

~~~text
Y 0     pressure 1.0000
Y 200   pressure 0.4493
Y 216   pressure 0.4215
Y 256   pressure 0.0000
~~~

Sable's lift-provider code scales lift and drag by local air pressure.

Its propeller code likewise scales thrust by local air pressure.

Therefore ordinary aerodynamic craft naturally lose performance as they climb in the End, just as they do in the other Sable vanilla dimensions.

The End-specific design question is whether its lower-pressure envelope at relevant island elevations and its vertical terrain distribution materially change aircraft design.

These values are upstream defaults, not Skyforge balance constants.

### End Stone Powder

Current Aeronautics crushing recipe:

~~~text
minecraft:end_stone
    -> aeronautics:end_stone_powder
       + chance to retain/recover End Stone
~~~

The End Stone Powder item carries Aeronautics' Levitating.END_STONE component.

This should not be confused with raw End Stone itself carrying the item component.

Dropped item behavior with a Levitating component has zero default gravity and additional velocity damping.

### Levitite Blend

Current Aeronautics heated mixing recipe:

~~~text
4 x aeronautics:end_stone_powder
2 x create:zinc_nugget
500 mB water
HEATED MIXING
    -> 500 mB aeronautics:levitite_blend
~~~

This has an especially useful Skyforge progression shape.

The recipe joins:

~~~text
END MATERIAL
    End Stone

POST-FLIGHT OVERWORLD ENGINEERING
    zinc

COMMON INDUSTRIAL INPUT
    water

CREATE PROCESSING
    crushing + heated mixing
~~~

Levitite therefore connects dimension exploration back into the player's existing industrial network instead of forming an isolated crafting branch.

### Crystallization

Aeronautics' Ponder/source shows Levitite Blend crystallizing into Levitite when initiated by an appropriate nearby catalyst/heat source.

Current helper code explicitly recognizes suitable Blaze Burner heat states and data-driven crystallization catalyst tags.

The crystallization reaction can then propagate through adjacent Levitite Blend source fluid.

A Soul-type crystallization context produces **Pearlescent Levitite**.

The Ponder sequence also warns that once catalyzed, Levitite cannot simply be recollected as the original blend, and casting can consume certain surrounding mold blocks depending on configuration.

This is useful manufacturing texture rather than a generic crafting-table recipe.

## What Levitite actually does

Aeronautics' own Ponder sequence establishes three critical behaviors.

### 1. Enough Levitite keeps a simulated contraption afloat

The demonstrated vehicle remains suspended when a sufficient amount of Levitite is attached.

This gives Levitite a **weight-support / levitation** role.

### 2. Levitite alone cannot make the contraption gain altitude

Aeronautics explicitly teaches that simulated contraptions cannot gain altitude using Levitite alone.

Additional forces are required.

This is an excellent non-substitution property.

Levitite is not an engine.

### 3. Levitite strongly resists low-speed motion

The Ponder sequence teaches that Levitite significantly resists motion at low speed, while this resistance drops as movement speed rises.

Therefore Levitite has an intrinsic handling tradeoff rather than being free mass cancellation.

Exact force curves still require source/runtime measurement before numerical balancing.

## Skyforge capability interpretation

Treat Levitite as a new **lift-support technology**, not as a universal late-game movement upgrade.

Conceptually:

~~~text
WINGS / AERODYNAMIC LIFT
    dynamic
    pressure-dependent
    speed-dependent

HOT AIR / BUOYANCY
    atmospheric
    envelope-volume dependent

LEVITITE
    passive/near-passive weight support
    no self-climb
    low-speed resistance

PROPELLER / ENGINE
    active propulsion
    pressure-dependent in current Sable integration

REACTION / ION PROPULSION
    possible later specialized active propulsion
    candidate only
~~~

This preserves multiple aircraft design families.

## End gameplay loop

A strong first hypothesis is:

~~~text
1. ENTER END
   mature Overworld aircraft technology already exists

2. DISCOVER ENVIRONMENTAL ENVELOPE
   End-relevant altitudes may place ordinary aircraft in a lower-pressure regime than familiar Overworld operations

3. OBTAIN END STONE
   discover End-derived levitating material path

4. PROCESS END STONE
   crushing -> End Stone Powder

5. CONNECT TO EXISTING ECONOMY
   powder + zinc + water + heat -> Levitite Blend

6. CRYSTALLIZE
   create Levitite through physical fluid/catalyst process

7. BUILD END-ADAPTED CRAFT
   Levitite supports weight
   engines/control still required

8. EXPAND END OPERATIONS
   longer expeditions
   heavier staging/cargo craft
   safer stationkeeping / recovery
   eventual specialized propulsion
~~~

The exact order relative to Dragon victory remains open.

## Dragon-progression question

Because ordinary End Stone exists on the central End island, the Levitite material chain may become available **before the Dragon is defeated** if the player brings the required workshop inputs.

Do not automatically gate this away.

Test three possibilities.

### Model E-A — pre-Dragon experimentation allowed

The player can discover and make limited Levitite on the central island.

Benefits:

- entering the End immediately reveals a new engineering possibility;
- the Dragon encounter can coexist with player ingenuity;
- the player can prototype End-adapted craft before outer-End expeditions.

Risk:

- a locally built craft may trivialize the Dragon or bypass intended outer-End progression.

### Model E-B — material available, industrial scale post-Dragon

Small experimentation is possible centrally, but practical quantities, safe manufacturing, or the most useful application become attractive only after Dragon victory / outer-End access.

This could emerge naturally from gameplay without recipe gating.

### Model E-C — explicit post-Dragon gate

Use only if testing demonstrates a serious progression break and an existing low-bespoke gating mechanism solves it elegantly.

Do not add artificial recipe locks before the failure exists.

## Outer-End terrain requirement derived from Aeronautics

Do not select morphology yet.

The outer End should instead satisfy gameplay constraints such as:

### Route spacing

Provide a distribution of crossings where:

- ordinary mature Overworld aircraft can reach some destinations;
- thin-air margin matters on harder routes;
- Levitite-equipped designs create a meaningful operational advantage;
- later propulsion can expand the envelope again.

### Altitude

Vertical distribution can become gameplay-relevant because Sable pressure varies with Y.

Avoid placing every important destination at one narrow altitude if doing so makes the pressure system irrelevant.

Conversely, do not force extreme high-altitude routes merely to manufacture difficulty.

### Landing / staging

The End should provide enough viable landing surfaces, staging sites, repair footholds, and navigation anchors that expeditionary aviation is demanding rather than arbitrary.

### Failure recovery

Void failure is unusually severe.

Terrain/network design should support some combination of:

- recoverable intermediate sites;
- staged depots;
- backup personal mobility;
- rescue craft;
- return routes;
- redundant navigation.

Do not make one experimental aircraft loss equivalent to losing an entire mature progression tier.

## End resource identity

End Stone should not merely be infinite filler if it is a major Aeronautics precursor.

That does **not** require making End Stone rare.

Its economic meaning can come from processing and throughput:

~~~text
End Stone
    common raw feedstock

End Stone Powder
    processed levitating precursor

Levitite Blend
    mixed industrial intermediate

Levitite
    manufactured aircraft material
~~~

This is consistent with Skyforge's preference for value through processing/logistics rather than arbitrary raw scarcity.

### Zinc interaction

Levitite consumes zinc.

That creates a cross-regional economic relationship:

~~~text
Overworld zinc district
    -> transport zinc to End workshop

End Stone
    -> local extraction / crushing

water + heat
    -> processing infrastructure

Levitite
    -> End-adapted / advanced aircraft
~~~

This is an excellent reason for dimensional logistics if the volumes are tuned appropriately.

## Levitite logistics identity

Levitite should create new design choices without invalidating prior aircraft.

Possible healthy applications to test:

- heavy cargo craft whose weight is partly supported by Levitite;
- hovering/stationkeeping platforms;
- slow utility craft;
- End expedition craft with better low-speed safety margin;
- mobile workshops;
- vertical-lift craft where separate propulsion supplies climb;
- hybrid aircraft mixing wings and Levitite.

Potential failure cases:

- one block ratio makes every aircraft effectively weightless;
- Levitite makes wings, envelopes, and vertical propulsion obsolete;
- low-speed drag is too weak to matter;
- Levitite makes enormous freight platforms trivial too early;
- End Stone availability creates effectively free unlimited lift with negligible processing cost.

Balance through existing material ratios/process cost first.

Avoid bespoke hard vehicle classes.

## Pearlescent Levitite

Current Aeronautics provides Pearlescent Levitite through Soul-type crystallization.

Its current Levitating component uses the same documented drag fraction as ordinary Levitite in the inspected component definition, while using different particles/presentation.

Do not invent a stronger mechanical tier unless upstream gameplay or testing demonstrates one.

Treat Pearlescent Levitite as a distinct existing material variant whose role requires further audit.

## Relationship to advanced propulsion

Create Propulsion: Simulated remains a strong R&D candidate.

If selected with atmospheric effects enabled, a possible mature End progression becomes:

~~~text
LEVITITE
    solves/supports weight

CHEMICAL / SOLID THRUST
    active force less dependent on ordinary aerodynamic lift

ION THRUST
    possible very-low-pressure specialization

WINGS / PROPELLERS
    remain useful where pressure is adequate
~~~

This can produce genuine aircraft architecture choices.

Do not lock this until the addon is accepted and its recipes/performance are tested.

## Vehicle transfer remains open

The End gameplay depends heavily on whether assembled Sable/Aeronautics craft can cross End portals or End gateways.

No source evidence has yet established that capability.

Until runtime proof exists, support multiple experience models:

~~~text
IMPORT COMPLETE CRAFT
or
IMPORT COMPONENTS -> BUILD LOCAL CRAFT
or
LATE STABILIZED CONTRAPTION TRANSFER
~~~

Terrain/progression must not assume one silently.

## Interaction with Elytra

Levitite and Elytra solve different problems.

~~~text
ELYTRA
    personal mobility
    minimal freight
    strong expedition/recovery value

LEVITITE AIRCRAFT
    engineered contraption
    freight / passengers / machinery
    persistent platform
    powered control required
~~~

Suppressing vanilla firework boosting remains compatible with both.

## World-authoring consequences

If Skyforge authors the outer End, resource and site placement should support the **technology loop**, not merely scatter landmarks.

Potential semantic site classes:

~~~text
END_STONE_EXTRACTION_SITE
LEVITITE_PROCESSING_FOOTHOLD
SAFE_AIRCRAFT_STAGING_SITE
END_CITY_EXPEDITION_TARGET
GATEWAY_NODE
HIGH_ALTITUDE_ROUTE
RECOVERY_WAYPOINT
EXCEPTIONAL_END_PHENOMENON
~~~

These are semantic roles, not required bespoke structures.

Many can be realized with ordinary player-built infrastructure or existing vanilla/mod assets.

## Acceptance tests

### END-AERO-1 — pressure-envelope difference is perceptible

A representative Overworld aircraft encounters a meaningfully different pressure/performance envelope at representative End operating altitudes than during ordinary Overworld operations, without becoming unusable everywhere.

### END-AERO-2 — End Stone chain works

The full current chain is proven in-game:

~~~text
End Stone
-> End Stone Powder
-> Levitite Blend
-> crystallized Levitite
~~~

with exact quantities/process recorded.

### END-AERO-3 — levitation role

Enough Levitite demonstrably holds a representative contraption afloat.

### END-AERO-4 — no free climb

Levitite alone cannot produce sustained positive-altitude climb.

### END-AERO-5 — handling tradeoff

Low-speed resistance is substantial enough to affect vehicle design/operation, while higher-speed motion remains practical.

### END-AERO-6 — hybrid design payoff

A Levitite-equipped craft plus propulsion/control solves a meaningful End operation better than the same craft without Levitite.

### END-AERO-7 — no universal replacement

Wings, propellers, envelopes, and other vehicle families remain useful after Levitite is available.

### END-AERO-8 — Dragon compatibility

Pre-Dragon access to End Stone/Levitite does not unintentionally trivialize or bypass the desired Dragon progression.

If it does, apply the smallest low-bespoke correction.

### END-AERO-9 — dimensional logistics

The zinc/water/heat/End-Stone input combination produces useful infrastructure/logistics pressure rather than pointless recipe friction.

### END-AERO-10 — transfer policy

The pack explicitly proves how assembled aircraft enter, leave, or are reconstructed within the End.

### END-AERO-11 — terrain supports gameplay

Any eventual Skyforge End terrain corpus demonstrates useful route spacing, altitude variation, staging/landing, recovery, and navigation for the selected End aircraft progression.

## Manual evidence required

When the pack prototype exists, record side-by-side:

~~~text
A. mature Overworld aircraft in Overworld
B. same craft at ordinary End altitude
C. same craft at higher End altitude
D. Levitite-supported variant
E. later low-pressure propulsion variant if selected
~~~

Measure or observe:

- takeoff behavior;
- sustained level flight;
- climb;
- propeller authority;
- control authority;
- stall/low-speed behavior;
- payload capacity;
- energy/fuel demand;
- landing;
- hover/stationkeeping where relevant;
- recovery after power loss.

Also perform the Dragon encounter with realistic player-accessible aviation technology.

## Current gameplay hypothesis

The End can provide a progression that is neither "Elytra loot dimension" nor "Overworld but harder":

~~~text
OVERWORLD
    learn aviation

END ENTRY
    discover that atmosphere/altitude changes the rules

END INDUSTRY
    acquire levitation material

END ENGINEERING
    combine passive weight support with active propulsion

OUTER END
    operate expeditionary aircraft across severe negative space
~~~

The terrain should then be authored to make this loop excellent.

## Acceptance principle

> The End should reward the aeronautical engineer with a new way to support a machine, while still requiring them to engineer how that machine moves.
