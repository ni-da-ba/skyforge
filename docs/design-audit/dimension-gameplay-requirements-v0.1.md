# Dimension Gameplay Requirements v0.1

**Snapshot:** 2026-09-05
**Status:** Working design direction. Gameplay requirements precede terrain/morphology decisions.

## Core rule

> Design each dimension around the gameplay it should create, then make Skyforge author the world that best supports that gameplay.

Skyforge's cross-dimension architecture is valuable only if it serves the experience.

Do not begin with:

~~~text
what terrain would look impressive?
~~~

Begin with:

~~~text
why does the player go here?
what decisions should they make here?
what capabilities should matter here?
what new engineering problems should appear?
what should remain difficult even after mature Overworld flight?
~~~

Terrain, structures, resources, ecology, atmosphere, and civilization are downstream answers.

## Design order

For each dimension:

~~~text
1. Gameplay role
2. Progression role
3. Traversal / logistics role
4. Resource and reward role
5. Aeronautics / physics role
6. Hazard / threat role
7. Structure / civilization role
8. Environmental legibility
9. Terrain grammar
10. Concrete Minecraft realization
~~~

This order supersedes any prior implication that a terrain grammar should be selected first.

## Shared cross-dimension constraint

The three vanilla dimensions should not become cosmetic variants of one game loop.

Preferred broad distinction:

~~~text
OVERWORLD
    build a life and a logistics network

NETHER
    penetrate and operate through a hostile enclosed resource world

END
    conduct late-game expeditions across an alien aeronautical frontier
~~~

These are working roles, not final slogans.

## Overworld gameplay role

The Overworld remains the primary world for:

- survival;
- settlement;
- farming;
- broad ecology;
- ordinary industry;
- first Create engineering;
- first gliding;
- first powered aircraft;
- regional trade;
- mature aviation/logistics networks.

It teaches the player the core Skyforge grammar.

The other dimensions should not simply restart this progression with new block palettes.

## Nether gameplay requirements

### Primary job

The Nether should provide a **hostile, enclosed, high-value operational environment**.

The player should go there because it contains capabilities/resources/sites unavailable or inefficient elsewhere.

The player should stay attentive because movement and logistics are constrained by:

- enclosure;
- lava;
- hostile populations;
- limited sightlines;
- choke points;
- route geometry;
- heat/environment;
- difficult recovery.

### Progression role

Preserve important Minecraft reasons to enter the Nether unless later design deliberately replaces them.

Likely roles include:

- Blaze progression;
- Nether Wart / brewing;
- Quartz;
- Netherite/Ancient Debris if retained in normal progression;
- Create: Metallurgy Wolframite while dimension-native;
- fortress/bastion loot;
- later heat/process capability;
- specialist fuels/materials where the selected pack supports them.

The exact reward set should be audited against the final pack.

### Traversal role

The Nether should reward **route construction and route knowledge**.

Healthy traversal may include:

- walking/tunneling;
- bridges;
- minecart/rail corridors;
- lava crossing;
- compact vehicles;
- aircraft within large chambers;
- mapped safe routes;
- fortified stations.

The goal is not to ban flight.

The goal is to prevent flight from turning the whole dimension into:

~~~text
ascend
-> point at coordinates
-> ignore everything below
~~~

### Aeronautics requirement

Sable already supplies dimension-specific physics.

Current built-in Nether defaults include:

~~~text
base gravity       = normal downward gravity
pressure at Y 0    ~= 1.1366
pressure at Y 32   = 1.0
pressure at Y 88   ~= 0.7993
pressure at Y 128  = 0.0
~~~

Sable's lift-provider and propeller APIs directly multiply aerodynamic lift/drag and propeller thrust by local air pressure.

This means the current stack already creates a useful behavior:

~~~text
LOW / MID NETHER
    aerodynamic craft remain viable

HIGH NETHER / ROOF APPROACH
    thinner air
    weaker wings
    weaker propellers

TOP OF DIMENSION
    aerodynamic authority approaches zero
~~~

That may be extremely useful.

It can discourage roof-level aviation through **physics rather than arbitrary prohibition**.

Do not lock these exact pressure values as Skyforge balance constants yet.

Instead require playtesting.

### Nether aircraft niches

Potential healthy niches:

- compact reconnaissance craft;
- VTOL/short-takeoff craft;
- small cargo craft in major vaults;
- route-maintenance aircraft;
- specialized lava-basin crossing craft.

Potentially unhealthy niche:

- enormous Overworld-style aircraft that bypass all cavern topology by living at the roof.

### Reaction/advanced propulsion

Create Propulsion: Simulated is a candidate, not yet a locked dependency.

Its current 1.21.1 branch includes:

- chemical/fluid thrusters;
- solid-fuel thrusters;
- ion thrusters;
- configurable atmospheric-pressure influence.

Current source tests define an atmospheric model where:

- chemical thrusters remain usable in vacuum;
- ion thrusters become strongest in vacuum;
- dense atmosphere weakens ion thrust.

However its global atmospheric-pressure effect is currently configurable and defaults off in the audited branch.

Therefore:

> Do not design Nether or End progression around Propulsion pressure behavior until the dependency and configuration are intentionally selected.

It remains a promising way to create later non-aerodynamic propulsion.

See [Nether Gameplay and Aviation Contract v0.1](nether-gameplay-and-aviation-contract-v0.1.md) for the source-backed route, pressure, metallurgy, structure, and recovery requirements.

## Nether terrain follows gameplay

Only after the above requirements should terrain be selected.

Current likely consequences:

- major chambers large enough for some aircraft;
- connected passages;
- choke points;
- lava barriers;
- vertical shafts;
- regions where compact craft clearly outperform large craft;
- route landmarks;
- defensible/station-worthy sites.

A solid-dominant cavern grammar remains promising because it supports these gameplay requirements.

But it is not mandatory if another grammar plays better.

## End gameplay requirements

### Primary job

The End should become a **late-game exploration and aeronautical frontier**.

It should feel like the player has reached a world where mature Overworld mobility is useful but no longer automatically sufficient.

The player should face new problems in:

- range;
- navigation;
- recovery;
- thin-air performance;
- sparse destinations;
- limited infrastructure;
- void risk;
- specialized propulsion;
- expedition planning.

### Preserve ritual progression

The central Dragon encounter remains a major progression boundary.

Initial policy:

~~~text
central End / Dragon fight
    preserve vanilla-special behavior

outer End
    preferred first Skyforge gameplay/authorship target
~~~

Do not let ordinary aircraft access invalidate the Dragon progression gate.

The exact method by which player-built contraptions enter or are constructed in the End requires a dedicated implementation audit.

Do not assume Sable/Aeronautics contraptions can simply pass through vanilla End portals.

Because End Stone is immediately present on the central island, also test whether pre-Dragon Levitite experimentation improves or trivializes the Dragon/gateway loop before imposing any artificial post-Dragon recipe gate.

### Outer End job

The outer End should provide more than:

~~~text
search until End City
-> obtain Elytra / Shulkers
-> leave
~~~

Potential long-term gameplay:

- expedition bases;
- navigation infrastructure;
- aircraft staging;
- recovery depots;
- rare structure/resource hunting;
- long-range survey;
- specialized End logistics;
- exceptional phenomena.

The dimension can remain sparse while becoming richer in decisions.

### Aeronautics is a core End consideration

Sable's current End dimension physics are already nontrivial.

Create Aeronautics itself also makes the End a technology source through the Levitite chain.

Current source-backed chain:

~~~text
End Stone
    -> crushing
    -> End Stone Powder

4 x End Stone Powder
+ 2 x zinc nuggets
+ 500 mB water
+ heated mixing
    -> 500 mB Levitite Blend

Levitite Blend
+ valid crystallization heat/catalyst
    -> Levitite
~~~

Aeronautics' own Ponder sequence demonstrates that enough Levitite keeps a simulated contraption afloat, but **cannot make it gain altitude by itself**. Additional force is required.

It also gives Levitite substantial low-speed motion resistance that drops at higher speed.

This means End progression can unlock a qualitatively different aircraft architecture without granting a universal engine.

See [End Aeronautics Progression Contract v0.1](end-aeronautics-progression-contract-v0.1.md).

Built-in defaults include:

~~~text
base gravity       = normal downward gravity
pressure at Y 0    = 1.0
pressure at Y 200  ~= 0.4493
pressure at Y 216  ~= 0.4215
pressure at Y 256  = 0.0
~~~

Because wing lift/drag and propeller thrust are scaled by local air pressure, ordinary Aeronautics craft lose performance as they climb.

This creates a natural End engineering problem:

~~~text
higher altitude
    -> thinner air
    -> less propeller thrust
    -> less wing/control authority
    -> greater risk
~~~

At ordinary End-island altitudes the air is not necessarily vacuum, but it is already meaningfully thinner than the strongest low-altitude regions.

Exact effective performance must be measured in-game.

### End aircraft progression hypothesis

A possible late-game progression is:

~~~text
OVERWORLD AIRCRAFT
    works in End
    but aerodynamic / propeller margin is reduced

END-DERIVED LEVITATION
    End Stone -> Levitite production
    passive weight support
    no self-climb
    low-speed handling cost

END-ADAPTED AIRCRAFT
    hybrid Levitite + propulsion/control
    stronger payload/stationkeeping options
    better navigation/recovery

ADVANCED PROPULSION
    reaction / ion / other low-pressure-capable propulsion
    becomes valuable for extreme routes / altitude
~~~

This is a hypothesis to test, not a recipe lock.

The important point is that the End can demand **new aircraft design**, not merely a bigger fuel tank.

### Flight should not erase End risk

Aircraft may make void travel possible.

They should not make the End safe.

Relevant risks can remain:

- power/fuel failure;
- poor landing options;
- thin-air stall/control loss;
- navigation error;
- hostile encounters;
- sparse repair infrastructure;
- long recovery distance.

Failure should be severe enough to matter but not so punitive that experimentation becomes irrational.

### Elytra role

Elytra remain thematically appropriate to the End.

Current Skyforge direction:

- preserve unpowered/personal soaring;
- suppress cheap vanilla-style firework propulsion;
- let Elytra coexist with aircraft as a personal mobility tool;
- evaluate whether End-specific pressure/wind should affect Elytra only if technically coherent and low-bespoke.

Do not forcibly turn Elytra into an aircraft replacement or make them useless.

### End terrain follows gameplay

The world should provide:

- meaningful void crossings;
- destinations visible or inferable at useful scales;
- landing/staging opportunities;
- occasional routes where altitude choice matters;
- room for aircraft maneuvering;
- some dangerous long crossings that reward preparation;
- enough negative space that navigation remains meaningful.

The exact morphology remains downstream.

Shards, plates, rings, monoliths, and other prior ideas are retained only as candidate forms if they support these requirements.

## Cross-dimension aircraft transfer

This is now a dedicated open question.

Before dimension progression is locked, determine:

1. Can a Sable/Aeronautics craft cross a vanilla Nether portal?
2. Can it cross an End portal?
3. Can a craft cross an End gateway?
4. If not, can it be packed/disassembled/reconstructed without excessive friction?
5. Are there existing compatible transfer mechanisms?
6. Would cross-dimension craft transfer undermine dimension progression?
7. Should some portals support players/items but not assembled contraptions?

Possible gameplay models:

### Model A — craft built locally

~~~text
player enters dimension
-> establishes foothold
-> imports components
-> builds dimension-specialized craft there
~~~

Strong gameplay identity, more friction.

### Model B — full craft transfer

~~~text
aircraft
-> portal/gateway infrastructure
-> enters new dimension
~~~

Excellent continuity, but technically and progression-sensitive.

### Model C — staged/late transfer

Early:

~~~text
player/items only
~~~

Later:

~~~text
large stabilized gateway
-> contraption transfer
~~~

This could become a valuable infrastructure progression if an existing mod/API supports it without excessive bespoke work.

No model is selected yet.

## Gameplay-first resource placement

Do not decide resource geography from lore alone.

For every dimension resource ask:

~~~text
what new capability does this resource unlock?
why must the player travel for it?
what quantity creates a route rather than a one-time fetch?
what transport mode should it reward?
what happens if the resource is absent locally?
~~~

### Nether example

A bulk Nether resource can justify:

- protected route;
- cargo transport;
- station;
- refinery/processor;
- repeated operation.

### End example

A high-value rare resource may justify:

- expedition;
- scouting;
- long-range aircraft;
- small return payload.

The End does not need bulk commodity traffic everywhere merely because the Overworld eventually does.

## Gameplay-first structure placement

Structures should support loops.

### Nether

Fortresses/bastions can serve:

- progression goals;
- faction/territory;
- route anchors;
- hazardous strategic destinations;
- salvage/trade/combat.

### End

End cities/ships can serve:

- expedition targets;
- navigation landmarks;
- recovery/staging sites;
- late mobility rewards;
- evidence of prior/alien infrastructure.

Do not overpopulate either dimension merely to fill space.

## Gameplay-first environmental design

Atmospheric/environmental differences should change decisions.

Good examples:

~~~text
Nether high-altitude pressure loss
    -> stay within cavern routes

End thin air
    -> redesign aircraft / choose altitude carefully

Overworld thermals
    -> glider route reading
~~~

Bad example:

~~~text
dimension has different wind number
    -> player never notices
~~~

Every important environment variable should have:

- gameplay consequence;
- legibility;
- counterplay;
- instrumentation path later.

## Dimension entry should change questions

Desired player questions:

### Entering Nether

~~~text
Where is the safe route?
Can my craft fit?
Where can I refuel/repair?
How do I cross that lava/choke point?
What route can I defend or mark?
~~~

### Entering End

~~~text
How far is the next reliable landing?
How much control margin do I have in this thin air?
What happens if I lose propulsion?
How do I navigate back?
Do I need an End-adapted craft for this expedition?
~~~

If the player's only question is "which direction is the loot?", the dimension design is too shallow.

## Acceptance tests

### DGR-1 — gameplay precedes morphology

Every major terrain decision can cite a gameplay requirement it serves.

### DGR-2 — distinct dimension jobs

Overworld, Nether, and End create different traversal/logistics/engineering decisions.

### DGR-3 — vanilla progression retained deliberately

Dragon, fortress, blaze, End-city, gateway, and other critical progression behavior is preserved or intentionally replaced—not accidentally broken.

### DGR-4 — Aeronautics matters differently

Representative aircraft experiences differ by dimension for understandable physical/environmental reasons.

### DGR-5 — End pressure has gameplay consequence

Sable's End pressure profile materially changes at least one aircraft design/operation decision if retained.

### DGR-6 — Nether roof is not dominant aviation space

Ordinary Nether play does not rationally collapse into roof-level aircraft transit.

### DGR-7 — advanced propulsion earns a role

If reaction/ion propulsion is included, it solves a real low-pressure or specialized-flight problem rather than merely replacing propellers everywhere.

### DGR-8 — aircraft transfer policy

The pack has an explicit, tested rule for moving assembled Aeronautics craft between dimensions.

### DGR-9 — failure/recovery

Dimension-specific flight failure is meaningful but has a plausible recovery strategy.

### DGR-10 — terrain remains subordinate

A visually impressive terrain concept is rejected if it undermines the target gameplay loop.

## Immediate research / prototype agenda

Before detailed Nether/End authorship:

1. Verify Sable/Aeronautics behavior in each vanilla dimension.
2. Measure wing/control/propeller performance across representative Y levels.
3. Verify hot-air/buoyancy behavior by dimension.
4. Test whether assembled crafts can traverse Nether portals, End portals, or End gateways.
5. Audit Create Propulsion: Simulated and any selected advanced propulsion against pressure.
6. Test Distant Horizons / distant contraption rendering in Nether and End.
7. Run vanilla progression compatibility for fortresses, bastions, Dragon, gateways, End cities, and ships.
8. Only then freeze terrain requirements.

## Current strongest gameplay hypothesis

~~~text
OVERWORLD
    aviation is the foundation of civilization/logistics

NETHER
    aviation is a specialized tool inside a dangerous enclosed route network

END
    aviation becomes expeditionary engineering under thin-air / void constraints
~~~

This should guide the next audit, but remains falsifiable by playtesting.

## Acceptance principle

> The world generator serves the game. A dimension earns its terrain by creating decisions the player could not have made somewhere else.
