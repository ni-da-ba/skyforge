# Early Glider Mobility Contract v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Strong prototype selected for playtesting; not yet a locked dependency or balance target.

## Core rule

> A starter glider may solve local topological gaps. It must not solve regional logistics.

Skyforge should preserve an inexpensive early-game glider as an optional selected traversal layer between ordinary survival and powered Aeronautics flight.

The purpose is narrow:

~~~text
starting island
    -> nearby island in the same local group
    -> nearby island in the same local group
    -> workshop / resource closure
    -> first practical powered aircraft
~~~

The glider is not a second aviation progression tree.

It is a launch-height-dependent personal traversal tool that helps the player move through a tightly composed starter group while the first aircraft remains the critical transition to routine inter-cluster travel and economic geography.

## Progression position

Use the following semantic distinction:

~~~text
P0      local survival
P0.5    local glide / assisted crossing
P1      mechanical bootstrap
P2      reliable powered personal flight
P3+     cargo, industrial aviation, instrumentation, logistics
~~~

P0.5 does not need to be exposed as a player-facing tier name. It exists so world planning can distinguish a glider-reachable edge from an aircraft-reachable edge.

The player may obtain the glider before or during P1 depending on the selected recipe and starting arrangement.

## Movement envelope versus logistics envelope

A glider provides a **movement envelope**:

- one player;
- carried inventory only;
- no attached freight platform;
- no self-contained powered climb;
- able to exploit natural or constructed thermal/updraft sources;
- range determined by launch height, lift availability, gap geometry, weather, and tuning;
- strongly terrain/infrastructure-dependent departure and arrival;
- useful for local scouting, descent, emergency recovery, soaring, and short-to-moderate personal crossings.

A powered aircraft provides a **logistics envelope**:

- repeatable two-way route operation;
- practical inter-cluster reach;
- modest or better freight;
- powered altitude recovery;
- controllable departure independent of a convenient cliff;
- route choice less constrained by local height topology;
- increasingly meaningful weather tolerance, instrumentation, and infrastructure.

The first aircraft therefore remains the moment at which Skyforge is allowed to make regional specialization economically important.

## Non-substitution invariants

A bootstrap glider is acceptable only while all of these remain true:

1. It cannot create lift from nothing; sustained soaring requires environmental or constructed lift sources.
2. Player-built thermal infrastructure may extend personal glider range, but doing so should require route preparation and remain low-throughput.
3. It cannot carry contraption-scale or container-scale freight.
4. Unprepared starter-group crossings may be glider-feasible; longer routes may become glider-feasible through thermals, terrain, weather knowledge, or prepared lift infrastructure.
5. Its effectiveness should remain sensitive to launch geometry, thermal availability, wind/weather, and pilot route choice.
6. Powered aircraft must materially improve cargo capacity, route flexibility, schedule independence, takeoff freedom, and operational convenience.
7. The glider may remain useful after P2—including for long personal soaring trips—without becoming the dominant logistics solution.

The design goal is complementarity, not forced obsolescence.

## Bootstrap world-planning implication

When a glider is selected, the starting-group traversal graph may include a distinct edge type:

~~~text
GLIDE_EDGE {
    launchSite
    arrivalSite
    launchElevation
    arrivalElevation
    horizontalGap
    verticalDrop
    approachClearance
    landingMargin
    fallbackRoute
}
~~~

A GLIDE_EDGE is admissible only if the currently selected glider prototype can plausibly make the crossing with ordinary player skill and reasonable safety margin.

Do not infer glider reach from raw Euclidean distance alone.

### Directionality and recovery

Gliding is naturally asymmetric.

A high island may reach a lower island while the reverse trip is impossible.

That is acceptable for optional exploration, but progression-critical bootstrap edges must not strand the player.

For every required resource or capability site reached through gliding, the planner should prove at least one of:

- a glider-feasible return edge;
- a climb/bridge/rope/lift return path using already available capability;
- a short onward route that reconnects to the starting group;
- a guaranteed replacement/recovery path if the glider is lost.

The traversal proof therefore operates on a directed capability graph, not merely a list of pairwise distances.

## Starter-scope rule

Preferred scope:

~~~text
STARTING ISLAND
  survival

STARTING GROUP
  cheap local traversal
  optional glider closure
  first resource differentiation

STARTING CLUSTER
  complete Create workshop
  adhesive closure
  first powered-aircraft closure

STARTING PROVINCE
  post-flight specialization
  strategic resources
  mature routes / civilization
~~~

A seed does not have to require the glider.

Bridges, connected terrain, rope/lift tools, or another valid low-tier traversal mode can satisfy the same semantic requirement.

If the glider is included in the pack, however, some starter layouts should make it genuinely useful so it is not decorative redundancy.

## Recovery role

The glider is particularly valuable as a resilience tool.

After a failed aircraft attempt it can provide:

- emergency personal return;
- access to a nearby wreck or landing site;
- movement between workshop islands;
- a low-cost backup while replacement aircraft components are manufactured.

This strengthens early failure recovery without giving the player replacement freight capability for free.

## Reuse-first implementation audit

### Leading prototype: Reliable Gliders

Current preferred prototype for Minecraft 1.21.1 NeoForge:

- repository: https://github.com/evanbones/Reliable-Gliders
- audited branch: `1.21.1`
- audited source head: `eb65dfe2159ffb850c631f998cd2149f7383bd47`
- source head message/version: `1.4.1`
- license: MIT

The current source is attractive because it is mechanically small:

- one glider item;
- main/offhand use by default;
- durability consumption during glide;
- configurable horizontal-speed multiplier;
- data-tag-driven updraft blocks;
- NeoForge implementation already present.

The stock behavior is **not** acceptable unchanged for Skyforge.

### Stock recipe conflict

The audited default recipe requires:

~~~text
2 phantom membranes
3 leather
3 sticks
~~~

Phantom membranes are not an appropriate hard bootstrap dependency.

If Reliable Gliders is selected, Skyforge should supply a tiny pack-level recipe override using ordinary early material families.

The exact replacement recipe is a balance decision and should be established after playtesting. Prefer semantic inputs such as:

~~~text
WOOD / FRAME
LEATHER_OR_FIBER
CLOTH / SAIL MATERIAL
possibly a small ordinary metal cost
~~~

Do not add a rare-hostile-loot requirement merely to preserve the upstream recipe.

### Stock updraft behavior — useful prototype, not a defect

The audited default updraft tag includes:

- fire;
- campfires;
- lava;
- magma blocks.

The source scans downward for an updraft block and applies upward velocity while gliding.

That maps surprisingly well onto Skyforge's existing thermal-soaring ecology concept.

A useful early behavior loop is therefore allowed:

~~~text
heat source / natural thermal
    -> local climb
    -> glide
    -> search for next lift source
    -> continue soaring
~~~

Player-built heat sources are not inherently a problem. A prepared chain of campfires or other thermal sources can become a legitimate **personal glider route**: low-cost, low-throughput infrastructure analogous to a footpath or rope route in the sky.

The real guardrail is not "prevent long-distance gliding." It is:

> Do not let low-throughput soaring erase the economic value of powered freight, flexible routing, schedule independence, and industrial aviation.

Preferred first prototype:

1. retain the existing heat-source updraft behavior;
2. test stock strength/height before changing it;
3. tune `updraft_strength`, `updraft_height`, or eligible source tags only if gameplay shows trivial lift chaining;
4. keep the glider otherwise near-stock;
5. later integrate natural thermals/ridge lift through the authoritative atmosphere seam.

Long-term, natural and anthropogenic thermal sources should participate in one conceptual lift model:

~~~text
THERMAL / UPDRAFT AVAILABILITY
    = atmospheric convection
    + terrain / ridge effects
    + weather
    + local heat sources
~~~

The stock block-tag mechanic is an acceptable bootstrap proxy for the local-heat term even before full atmosphere integration exists.

## Alternative candidates

### Hang Glider

Retain as an A/B candidate if Reliable Gliders proves too arcade-like or insufficiently expressive.

Do not adopt it solely for extra features; dependency and handling complexity must buy a materially better Skyforge traversal experience.

### Gliders by Jeryn

Retain as a reserve candidate.

Its broader material tiers, upgrades, and heat/updraft mechanics create more risk of becoming an alternate progression system. That may be useful in another pack, but Skyforge currently needs the smallest possible local-mobility primitive.

## No bespoke glider implementation initially

The preferred implementation order is:

~~~text
existing glider mod
    -> recipe override
    -> data/config override
    -> gameplay tuning
    -> thin compatibility hook only if required
    -> bespoke implementation only if all prototypes fail
~~~

This follows the project-wide reuse-first rule.

## Interaction with atmosphere

The first glider prototype should not block later atmospheric integration.

If authoritative wind is selected, a mature design may allow:

- headwind/tailwind influence;
- turbulence risk;
- terrain-induced/ridge lift;
- authored natural thermals;
- local anthropogenic heat lift;
- weather advisories;
- soaring-fauna response to the same lift field.

But wind and natural-atmosphere state must remain consequences of the one authoritative atmosphere model.

A block heat source may contribute a local thermal forcing term without making the glider mod a second wind authority.

## Interaction with starter aircraft

The glider and first Aeronautics aircraft should be tested together.

The comparison must make the aircraft's value obvious.

Expected qualitative difference:

~~~text
GLIDER
  cheap
  personal
  local
  launch-dependent
  negligible freight
  weak route independence

FIRST AIRCRAFT
  workshop-dependent
  reusable
  powered
  inter-cluster capable
  modest freight
  two-way route capable
  foundation of logistics
~~~

If a player reasonably concludes that building the aircraft is unnecessary because the glider already reaches everything that matters, the world composition or glider tuning has failed.

## Acceptance tests

### G1 — starter-group usefulness

At least one representative valid starter layout contains a useful nearby crossing that is comfortable with the glider and meaningfully less convenient without it.

### G2 — bootstrap closure

All progression-critical pre-flight resources remain obtainable without rare hostile loot. If the glider is required for a selected starter layout, its complete recipe closure is guaranteed before that edge.

### G3 — directed recovery

Every progression-critical glider edge has a proven return/recovery path. No normal player is stranded by following the intended bootstrap route.

### G4 — thermal-route legitimacy

Natural and player-built updrafts can support deliberate soaring routes.

A route made from campfires, fire, lava, magma, or later thermal infrastructure is acceptable if it remains:

- personal-scale;
- route-prepared;
- low-throughput;
- weather/geometry sensitive;
- materially less convenient for bulk logistics than powered aircraft.

### G5 — logistics separation

Representative starter-group gaps should be comfortably glider-reachable.

Longer routes may also be reachable by a skilled player using thermals or prepared lift infrastructure; this is not itself a failure.

The failure condition is economic substitution: if the glider makes powered aircraft unnecessary for freight, flexible two-way routing, industrial travel, or routine network operation, tuning has failed.

Exact ranges are empirical tuning values, not architecture constants.

### G6 — cargo separation

The glider does not move container/contraption freight. The first powered aircraft provides a clear increase in useful cargo logistics.

### G7 — aircraft desirability

After the first aircraft is assembled, it materially expands reachable destinations and route independence. The first-flight milestone remains a major gameplay transition.

### G8 — failure resilience

Loss of an early aircraft does not force a total progression reset; the glider can participate in recovery without replacing the aircraft's logistical role.

### G9 — atmosphere authority

Any future lift/wind interaction is sourced from the selected authoritative atmosphere system rather than duplicated environmental simulation.

## Evidence required before lock

The glider dependency should remain a strong prototype until an in-game comparison records:

- recipe and acquisition time;
- practical glide ratio/range from representative launch heights;
- landing controllability;
- failure/death risk;
- durability burden;
- starter-group crossings;
- attempted inter-cluster crossings;
- natural-thermal and heat-source soaring;
- player-built thermal route feasibility;
- observed use of the same lift features by soaring fauna where available;
- recovery use;
- side-by-side utility against the first powered aircraft.

## Acceptance principle

> Let the player learn to read and ride the air cheaply before asking them to industrialize it. Soaring may travel far; logistics still belongs to powered flight.
