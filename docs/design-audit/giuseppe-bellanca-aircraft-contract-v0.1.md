# Giuseppe Bellanca Aircraft Contract v0.1

**Snapshot:** 2026-09-06  
**Status:** Preliminary design contract; construction and flight acceptance pending.

## Purpose

Define the canonical intact aircraft that can later be realized in multiple service variants and crash states inside the Bootstrap Province.

The aircraft is named **Giuseppe Bellanca** after aircraft designer Giuseppe Mario Bellanca. It is not a replica of one historical Bellanca airframe. The design borrows the Bellanca tradition of efficient high-wing utility aircraft while adapting the machine to Skyforge's floating-island geography, atmosphere, Create Aeronautics/Sable mechanics, and progression.

## Governing requirement

> The Giuseppe Bellanca must be a real, assembleable Sable/Create Aeronautics contraption whose form follows the actual mechanical systems that make it fly.

It must not be a decorative aircraft shell around unrelated hidden physics.

## Player/world role

Primary roles:

- frontier utility transport;
- survey/reconnaissance;
- route inspection;
- weather observation;
- courier/passenger service;
- light cargo;
- remote settlement support.

Desired strengths:

- short/rough-field operation;
- cliff-edge departure;
- forgiving low-speed handling;
- useful power-off glide;
- efficient regional travel;
- strong visibility;
- instrumentation and later computation.

Explicit non-strengths:

- VTOL;
- bulk freight;
- giant Create-contraption carriage;
- very high speed;
- heavy combat;
- automatic flight without later avionics/infrastructure.

This preserves the progression niches of gliders, larger freight aircraft, petroleum-powered heavy aviation, airships, and later specialized propulsion.

## Selected configuration

Leading baseline:

- high-wing;
- strut-braced;
- tractor propeller;
- fixed rough-field landing gear;
- deep utility cabin;
- long-span fabric lifting surface;
- mechanically actuated tail/control surfaces;
- manual flight independent of computers.

The design target is visually closer to **utility aircraft + bush plane + motor-glider influence** than to a generic modern light airplane.

## Skyforge adaptation

### Three-dimensional terrain

Ordinary runway assumptions do not hold. The aircraft should support:

- small tablelands;
- ridge shoulders;
- prepared shelves;
- rough strips;
- open-sky overruns;
- cliff departures.

Terrain remains meaningful because the craft is **not VTOL**.

### Vertical atmosphere

Power-off glide and soaring are first-class operating modes.

Expected operating cycle:

~~~text
POWERED TAKEOFF
    -> CLIMB
    -> FIND USEFUL LIFT / ALTITUDE
    -> ENGINE CUT
    -> GLIDE / SOAR
    -> RESTART WHEN REQUIRED
~~~

Fuel economy can therefore reward piloting skill and atmospheric understanding.

The same authoritative atmospheric field should eventually inform:

- Aeronautics flight physics;
- gliders;
- adapted soaring fauna;
- instruments;
- CC:Tweaked;
- presentation.

### Flight safety principle

> Engine loss should cause a controllable descent, not an immediate fall.

A useful glide ratio, restart envelope, reserve-management behavior, and landing-site decision window are mandatory acceptance subjects.

## Progression position

The intact Bellanca is more mature than the first crude powered aircraft.

The current progression preserves a pre-Brass, pre-petroleum, pre-electricity first-flight closure. The Bellanca may visibly contain later systems such as:

- basic instrumentation;
- Brass-era flight reference;
- navigation equipment;
- CC:Tweaked;
- communications;
- optional later assisted-control interfaces.

Starter-wreck realization must not hand these progression stages to the player intact.

The wreck can expose them as:

- destroyed equipment;
- damaged terminals;
- logs;
- map data;
- nonfunctional instruments;
- bounded salvage.

## Computing contract

Computing is a first-class Skyforge progression axis.

The Bellanca must reserve an avionics volume but **manual flight cannot depend on the computer**.

Current Simulated source already provides ComputerCraft peripherals for:

- altitude / air pressure;
- velocity;
- gimbal attitude;
- navigation table;
- swivel bearings;
- optical sensors;
- directional receivers;
- docking connectors;
- other mechanical/control surfaces.

Skyforge should add only thin environmental measurement seams where upstream systems do not expose authoritative Skyforge/A4MC atmosphere.

No omniscient world-query computer API is intended.

## Construction language

Prefer mechanically sensible low-mass blocks:

- Create sails for lifting surfaces;
- wooden slabs/stairs/fences/trapdoors for frame/shell;
- glass panes for cockpit glazing;
- planks/logs where needed;
- exposed Create shafts/gearing where mechanically meaningful.

Use dense machinery and metal only where function justifies it.

Avoid decorative stone/cobble/storage-block mass.

## Canonical module decomposition

The intact aircraft should be authored in semantic modules:

~~~text
GB_NOSE_PROP
GB_ENGINE_BAY
GB_COCKPIT
GB_WING_CENTER
GB_PORT_WING
GB_STARBOARD_WING
GB_CARGO_CABIN
GB_TAIL_BOOM
GB_EMPENNAGE
GB_MAIN_GEAR
GB_AVIONICS
~~~

These modules support later service variants and causally coherent crash grammars without requiring multiple unrelated aircraft designs.

## Crash principle

Design order:

1. prove intact aircraft;
2. freeze useful structural/module boundaries;
3. derive service variants;
4. derive damage grammar;
5. integrate crash selection with Bootstrap Province morphology.

Crash damage must follow plausible momentum/load paths rather than random block deletion.

## Human-eye gate

Machine correctness is not aesthetic acceptance.

After the engineering mule flies, review at minimum:

- top;
- side;
- front/rear;
- three-quarter above;
- three-quarter below;
- 50-block horizon;
- 150-block horizon;
- low approach from below-island altitude;
- cockpit;
- gear/propeller.

The aircraft must read as a coherent Skyforge machine from both above and below.

## Acceptance principle

> Build the function first. Beautify only after the Sable contraption assembles, flies, glides, lands, carries cargo, restarts safely, and provides enough internal volume for believable instrumentation and later avionics.
