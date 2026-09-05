# Atmosphere, Aviation, Navigation, and Horizon Audit

**Snapshot:** 2026-09-05  
**Status:** Working design direction.

## Aviation substrate

The Create Aeronautics ecosystem is non-negotiable for the intended Minecraft experience.

Leading baseline:

- Create;
- Sable;
- Create Aeronautics.

Additional likely/prototype components include Create Diesel Generators, advanced propulsion/turbine addons, transmission/linkage, collision damage, one electricity integration, and advanced logistics.

The intended fantasy is:

> The world makes the player become an aeronautical engineer.

No one vehicle should trivialize geography. Landing, payload, fuel, weather, control authority, and route planning should remain meaningful.

## Wind/atmosphere authority

Per-environmental-quantity rule:

> One authoritative source.

Working authority model:

```text
Skyforge
  semantic world / climate / geography
        |
        v
authoritative atmosphere
  instantaneous wind / weather state
        |
        +--> Sable / Aeronautics vehicle physics
        +--> gliders / soaring
        +--> sensors / CC / radar
        +--> visuals / sound
        +--> ecology
```

### Aerodynamics4MC — leading prototype

Current leading candidate for authoritative wind/atmosphere.

Reasons:

- MIT;
- 1.21.1 NeoForge/Fabric;
- server-authoritative large/mesoscale wind layers;
- terrain interaction;
- altitude shear;
- turbulence/gust/updraft diagnostics;
- public gameplay-wind API;
- Create Aeronautics compatibility path that computes relative airflow/AoA.

It is young and not yet a locked dependency.

### Sable wind seam

Sable already contains a conceptual air-relative velocity seam:

```text
V_relative = V_object - V_air
```

Stock lift/drag does not fully consume ambient wind in the desired way, so integration must be acceptance-tested.

### Wind Tunnel — strong development/test tool

Native 1.21.1 NeoForge, MIT, and aligned with Sable/Aeronautics.

Useful for controlled proof of:

- headwind/tailwind;
- crosswind;
- updraft;
- shear;
- gust/turbulence;
- lift/drag/side-force and moments.

### Secondary weather candidates

- Aeronautics: Winds & Weather — useful presentation/content prototype, not preferred authority.
- Weather2 + Expanded Weather2 Dynamics — severe-weather experiment only; do not make it the foundational atmosphere authority without deeper performance validation.
- Particle Rain — low-risk weather visuals.
- Simple Clouds — strong spectacle candidate, but renderer/performance risk.
- AmbientSounds — strong atmosphere candidate.
- Sound Physics Remastered — strong acoustic candidate.

## Sable dimension-physics audit

Sable already provides data-driven per-dimension physics.

Current source exposes:

~~~text
base_gravity
base_pressure
pressure_function
universal_drag
magnetic_north
~~~

and allows datapack overrides by dimension.

This is highly relevant to Nether/End design.

### Aerodynamic coupling

Sable's current `BlockSubLevelLiftProvider` reads local air pressure and multiplies:

- parallel drag;
- directionless drag;
- lift;

by that pressure.

Its `BlockEntityPropeller` likewise multiplies propeller thrust by current air pressure.

Therefore dimension pressure is already a **real aircraft-performance input**, not merely environmental metadata.

### Current End defaults

The audited Sable built-in End profile is approximately:

~~~text
Y 0     pressure 1.0000
Y 200   pressure 0.4493
Y 216   pressure 0.4215
Y 256   pressure 0.0000
~~~

with ordinary downward gravity.

Consequences to test:

- reduced propeller thrust at higher End altitude;
- reduced wing/control-surface authority;
- altitude becoming an engineering/route decision;
- value of larger aerodynamic surfaces or later low-pressure propulsion.

Do not overwrite this profile casually merely to make Overworld aircraft behave identically in the End.

### Current Nether defaults

The audited Sable built-in Nether profile is approximately:

~~~text
Y 0     pressure 1.1366
Y 32    pressure 1.0000
Y 88    pressure 0.7993
Y 128   pressure 0.0000
~~~

with ordinary downward gravity.

This may naturally support:

- ordinary aerodynamic flight in lower/mid Nether spaces;
- deteriorating performance near the roof;
- a physical reason not to make roof-level aircraft transit dominant.

Exact gameplay remains an in-game validation question.

### Pressure ownership

If Skyforge later supplies dimension environment profiles, preserve the one-authority rule.

Preferred relationship:

~~~text
Skyforge semantic dimension environment
    -> Sable dimension-physics datapack/profile
    -> Aeronautics lift / drag / propeller behavior
~~~

Do not implement a second independent Skyforge pressure force on vehicles.

## Flight behavior requirements

Wind must affect real relative-airflow behavior rather than apply arbitrary lateral force.

Expected outcomes:

- fixed wing: ground track, crosswind correction, takeoff/landing, route efficiency;
- glider: thermals/updrafts/ridge lift as energy and route-planning resources;
- soaring fauna: thermal/ridge-lift selection, circling/climb behavior, and visible cues to local lift;
- balloon: air-mass drift and altitude-selection gameplay;
- airship: propulsion versus wind and exposed-area consequences;
- high altitude: exploitable wind layers;
- storms: gust/turbulence and route hazard.

## Thermals as shared world language

Skyforge should treat rising air as a shared environmental mechanic rather than a glider-only trick.

Conceptually:

~~~text
UPDRAFT / THERMAL AVAILABILITY
    = atmospheric convection
    + terrain / ridge interaction
    + weather forcing
    + local heat sources
~~~

Natural and anthropogenic sources may both matter.

Examples:

- sun-heated terrain or authored convection -> natural thermal;
- windward cliff/ridge -> orographic lift;
- lava field -> persistent local thermal;
- fire/campfire/industrial heat -> small anthropogenic thermal contribution.

The exact physical model can remain simplified. The important requirement is semantic coherence: **gliders and soaring fauna should be responding to the same kind of environmental opportunity.**

This gives the player non-instrumented atmospheric information through animal behavior.

A circling adapted red-tailed hawk / ordinary thermal soarer can function as a visible clue:

~~~text
bird circles and gains altitude
        -> likely lift nearby
        -> player investigates
        -> glider can exploit the same air
~~~

That is preferable to making every useful atmospheric quantity visible only through HUD instrumentation.

Player-built thermal routes are allowed in principle. A chain of campfires or stronger heat sources may create a low-throughput personal soaring route. This should be balanced against powered aircraft by logistics capability rather than by forbidding the route.

Powered aviation still owns:

- freight;
- flexible departure;
- schedule independence;
- route reversibility without prepared thermal sites;
- industrial throughput;
- operation when the convenient soaring line is unavailable.

The authoritative atmosphere system remains the owner of ambient wind/weather. Local block heat may be treated as a forcing/input term or compatibility proxy rather than a second weather simulation.

## Weather legibility

The player should understand wind/weather without computers through:

- vanes;
- cup anemometers;
- flags;
- windsocks;
- bells;
- clouds;
- vegetation;
- particles.

CC:Tweaked can add quantitative instrumentation and automation later.

## Rendering guardrail

Avoid multiple mods competing to own the sky renderer.

Presentation tiers:

1. Core meaning must survive without shaders.
2. Enhanced presentation may improve with shaders.
3. Optional spectacle may rely on specialized rendering.

High-risk renderer features such as volumetric cloud strata should not become the sole carrier of gameplay meaning.

## Distant Horizons — core presentation dependency

Distant Horizons is considered essential to the experience because Skyforge terrain and aviation both depend on long-distance perception.

Principle:

> Aeronautics lets the player cross the world; Distant Horizons lets the player perceive it.

Separate Sable Render Distance or equivalent distant-contraption support is a strong likely companion so physics objects remain visible beyond vanilla distance.

Future acceptance should consider distant representation for:

- airships;
- balloons;
- Sky Whales;
- large predators;
- beacons;
- plumes;
- major weather;
- settlement lighting.

## Horizon composition

World authoring should preserve:

- large negative spaces;
- distinct cluster silhouettes;
- selective verticality;
- very low-frequency exceptional landmarks;
- indirect province transitions through morphology/palette/ecology/weather;
- distance layering.

Atmosphere should modulate rather than erase the horizon.

### Layering without roofing

Ordinary island placement should strongly limit persistent overhead occlusion of habitable lower surfaces.

Vertical separation alone does not solve Minecraft skylight obstruction if X/Z overlap remains.

Authoring should favor:

- horizontal offset;
- size hierarchy;
- limited overlap;
- no routine multi-layer roofing.

Permanent-shadow/twilight islands are allowed as exceptional authored ecological conditions.

Suggested future authoring metrics:

```text
surfaceSkyExposure
largestContiguousOccludedPatch
numberOfOverheadLayers
overlapWithHabitableTerrain
```

## Advanced propulsion candidate

### Create Propulsion: Simulated — strong prototype, not yet locked

The current 1.21.1 NeoForge project adds:

- liquid/chemical thrusters;
- solid-fuel thrusters;
- ion thrusters;
- vectored-thrust variants;
- Sable/Aeronautics force integration.

It also contains configurable atmospheric-pressure behavior and dimension atmosphere data.

The audited current source tests a potentially useful distinction:

~~~text
chemical thruster
    remains usable at low/vacuum pressure

ion thruster
    performs best toward vacuum
    weaker in dense atmosphere
~~~

However, its common config currently defaults the atmospheric-pressure effect **off**.

Therefore this behavior is not yet a Skyforge assumption.

It is a strong R&D candidate for:

- End-adapted propulsion;
- high-altitude flight;
- differentiated late-game aircraft;
- possible Nether specialized craft.

Selection should follow actual gameplay testing and recipe/progression audit.

## Navigation

### CC:Tweaked

Likely core advanced infrastructure:

- wireless modem/rednet;
- GPS;
- onboard computation;
- bearing/range/course;
- automation and autopilot.

Skyforge should not create a bespoke beacon system if CC primitives solve the need.

### Create: Radars

Physical/unknown contact sensing candidate.

### Create: Avionics / similar

Useful telemetry/control integration with CC if stable on the final stack.

### Navigation progression

```text
eyes / landmarks
-> compass / map
-> beacon receiver
-> radar
-> radar + identification
-> CC/autopilot
```

Avoid omniscient navigation/teleport systems that erase distance and infrastructure.

## Civilization and destination signaling

Settlements should signal their presence through:

- lights;
- smoke/plumes;
- traffic;
- beacons;
- towers;
- flags/windsocks.

Abandoned locations should communicate absence through:

- skeletal silhouettes;
- missing lights;
- ruined infrastructure;
- wreckage.

Destination classes:

- Resource;
- Infrastructure;
- Ecological;
- Historical;
- Phenomenon;
- Legendary.

Desired loop:

```text
SEE -> WONDER -> IDENTIFY -> PLAN -> TRAVEL -> DISCOVER
```
