# Post-Flight Capability Payoff Audit v0.1

**Snapshot:** 2026-09-05  
**Status:** Source-backed working design audit for the current 1.21.1 engineering stack. Exact balance, recipes, and dependency selection remain subject to integrated pack testing.

## Core question

> Once first powered flight exists, what does each geographically distributed resource or processing system actually let the player do that they could not do before?

Skyforge resource geography is only useful when acquiring a new material creates a **capability payoff**.

Bad progression:

~~~text
fly to new district
-> obtain differently colored ingot
-> craft same kind of machine with larger number
~~~

Preferred progression:

~~~text
fly to new district
-> obtain new material / process
-> new class of engineering becomes practical
-> new routes / infrastructure become rational
~~~

## Capability categories

Use the following categories when evaluating a post-flight reward:

~~~text
MOVEMENT
LIFT_SUPPORT
PROPULSION
CARGO
FLUID_HANDLING
POWER_GENERATION
POWER_DISTRIBUTION
POWER_STORAGE
AUTOMATION
SIGNALING
COMPUTATION
NAVIGATION
SENSING
PROCESS_HEAT
METALLURGY
RESOURCE_EXTRACTION
REPAIR / RESILIENCE
NETWORK_THROUGHPUT
~~~

A material need not own a category exclusively.

The important test is whether obtaining it **materially expands the player's engineering envelope**.

# P2 transition — first flight already exists

The leading first-aircraft closure remains intentionally independent of:

- copper;
- zinc;
- Brass;
- petroleum;
- electricity;
- gold;
- diamond;
- Nether materials;
- End materials.

Therefore all of the following can function as **rewards for mobility** rather than prerequisites for mobility.

This remains subject to the manual first-aircraft proof.

# Copper — fluid and electrical infrastructure

## Current payoff

Copper is one of the strongest first post-flight engineering materials because it participates in two major branches.

### Create fluid systems

Current Create use includes:

- fluid pipes;
- fluid tanks;
- hose pulley;
- spouts / filling;
- valve and fluid-control infrastructure;
- Brass production.

Capability gain:

~~~text
COPPER
    -> FLUID_HANDLING
    -> tanks / pipes / transfer
    -> fuel / refinery / industrial-fluid logistics later
~~~

### Create Crafts & Additions electrical branch

Current 1.21.1 Create Crafts & Additions includes:

- copper wire / spool;
- Alternator;
- Electric Motor;
- electrical transmission;
- Accumulator systems;
- Digital Adapter integration.

Current source-backed Alternator recipe uses:

- Andesite Alloy;
- iron plates;
- iron rod;
- copper spool.

The current Electric Motor recipe uses:

- Andesite Alloy;
- Brass plates;
- capacitor;
- iron rod;
- copper spool.

This produces a useful asymmetry:

~~~text
EARLY ELECTRICAL GENERATION
    can plausibly appear once copper-wire infrastructure exists

MOTOR / ELECTRIC-TO-KINETIC CONVERSION
    requires a more mature Brass + capacitor branch
~~~

This gives electricity an internal progression instead of a single binary unlock.

## Geography implication

Copper should be common-regional rather than rare.

A copper-rich district can support:

- plumbing;
- tankage;
- wire manufacturing;
- early electrical generation;
- later petroleum infrastructure.

Copper is therefore a **network-enabling metal**.

# Zinc — chemistry of Create maturity and later End industry

## Current payoff

Zinc participates in several distinct capability transitions.

### Brass

Current Create recipe:

~~~text
Copper Ingot
+ Zinc Ingot
+ HEATED MIXING
    -> Brass
~~~

Brass is the major immediate payoff.

### Create Crafts & Additions capacitor

Current 1.21.1 Capacitor recipe includes:

- Zinc plate;
- Copper plate;
- Redstone Torch.

This means zinc contributes directly to the electrical-control/storage branch rather than being useful only through Brass.

### Aeronautics Levitite

Current Aeronautics Levitite Blend recipe includes zinc nuggets.

Therefore the same zinc economy later remains relevant to End-derived aircraft technology.

Conceptual persistence:

~~~text
P2 / R2
    Zinc -> Brass / Create maturity

P3 / R4
    Zinc -> electrical components

END
    Zinc -> Levitite Blend
~~~

This is desirable because a regional resource remains economically relevant instead of becoming obsolete after one tier.

## Geography implication

Zinc should support repeated moderate-volume mining and trade.

It should not be a legendary resource.

# Brass — logistics, control, mature machinery

## Current payoff

Brass is the first major **manufactured regional reward**.

Current Create uses already audited include:

- Brass Funnels;
- Brass Tunnels;
- Mechanical Arms;
- Redstone Links;
- Smart Fluid Pipes;
- Brass Casings;
- later control/logistics components.

Current selected-addon source further increases Brass importance.

### Create Crafts & Additions

Current Electric Motor consumes Brass plates.

Current Modular Accumulator consumes a Brass Casing.

Current ComputerCraft-dependent Digital Adapter recipe consumes:

- wired modem;
- Brass plate;
- Redstone Torch.

### Create Diesel Generators

Current 1.21.1 Diesel Engine recipe consumes a **Brass storage block** plus engine components and a Create Fluid Tank.

The Large Diesel Engine adds Brass plates.

The Huge Diesel Engine also consumes:

- Brass block;
- Brass plates;
- Steam Engine;
- Fluid Pipes;
- Andesite Alloy.

Therefore Brass sits at an important intersection:

~~~text
BRASS
    -> advanced Create logistics
    -> control / ComputerCraft integration
    -> electrical motor/storage infrastructure
    -> petroleum engine infrastructure
    -> some later Aeronautics hardware
~~~

## Capability identity

Brass should mean:

> The player can now build **coordinated industrial systems**, not merely more basic machines.

Primary capability categories:

- AUTOMATION;
- SIGNALING;
- FLUID_CONTROL;
- POWER_CONVERSION;
- NETWORK_THROUGHPUT;
- HEAVIER_ENGINEERING.

## Progression guardrail

Do not silently make Brass a first-flight requirement.

Its value is strongest if the player already has aircraft and now has a reason to fly for industrial maturity.

# Redstone + Quartz — control and information infrastructure

## Current payoff

Redstone is broadly useful, but the current Create stack gives Nether Quartz a specific connection to mature controls.

Current Create source-backed chain:

~~~text
Quartz
+ 8 Redstone Dust
    -> Rose Quartz

Rose Quartz
    -> polishing
    -> Polished Rose Quartz

Polished Rose Quartz
+ Iron Plate
    -> Electron Tube
~~~

Electron Tubes feed numerous Create control/logistics components.

This gives a useful cross-domain pattern:

~~~text
OVERWORLD REDSTONE
    +
NETHER QUARTZ
    -> control / logistics technology
~~~

## Capability identity

This branch should support:

- AUTOMATION;
- SIGNALING;
- CONTROL;
- sensing-adjacent infrastructure;
- mature logistics.

Redstone itself should remain common enough that ordinary automation is practical.

Quartz can help keep Nether travel technologically relevant without being made artificially rare.

# Petroleum — strategic fuel and power geography

## Current source-backed processing

Create Diesel Generators 1.21.1 currently distills:

~~~text
100 mB Crude Oil
+ HEATED
    -> 50 mB Diesel
    -> 50 mB Gasoline
~~~

and under superheated processing:

~~~text
100 mB Crude Oil
+ SUPERHEATED
    -> 75 mB Diesel
    -> 75 mB Gasoline
~~~

This gives heat/process maturity a real efficiency relationship.

## Engine progression

Current recipes include:

### Diesel Engine

Key dependencies include:

- Brass storage block;
- engine pistons;
- Create Fluid Tank;
- polished Blackstone slab;
- ignition item.

### Large Diesel Engine

Adds:

- Brass plates;
- Andesite Alloy;
- base Diesel Engine.

### Huge Diesel Engine

Uses:

- Brass storage block;
- Brass plates;
- Create Steam Engine;
- Create Fluid Pipes;
- Andesite Alloy.

This gives petroleum a strong late-industrial identity.

## Capability identity

Petroleum should unlock or strongly improve:

- PROPULSION / engine power where compatible;
- sustained industrial POWER_GENERATION;
- long-duration heavy machinery;
- FLUID_LOGISTICS;
- fuel storage/distribution;
- heavy aircraft / airship operational range;
- strategic depots.

## Why petroleum is the first true strategic-node resource

Unlike copper or zinc, petroleum naturally supports:

~~~text
FIELD
    -> PUMPJACK
    -> CRUDE STORAGE
    -> REFINERY
    -> DIESEL / GASOLINE
    -> TANK FARM
    -> DEPOT
    -> DISTRIBUTION ROUTES
~~~

This creates infrastructure even before recipe scarcity is considered.

It also creates **fluid freight**, which personal inventory cannot efficiently replace.

## Guardrail

Do not make petroleum necessary for the first powered aircraft.

It should make mature aviation better, larger, longer-ranged, or more industrial—not make basic aviation possible.

# Electricity — coordination, conversion, storage, automation

Create Crafts & Additions remains the leading single electricity ecosystem.

Current 1.21.1 public and source-backed content includes:

- Alternator;
- Electric Motor;
- Accumulator / Modular Accumulator;
- electrical transmission;
- Rolling Mill;
- Tesla Coil;
- Digital Adapter;
- ComputerCraft integration.

## Important source-backed progression asymmetry

### Alternator

Current recipe:

~~~text
Andesite Alloy
Iron Plates / Rod
Copper Spool
    -> Alternator
~~~

No Brass is present in the audited current recipe.

### Electric Motor

Current recipe:

~~~text
Andesite Alloy
Brass Plates
Capacitor
Iron Rod
Copper Spool
    -> Electric Motor
~~~

### Capacitor

Current recipe:

~~~text
Zinc Plate
Copper Plate
Redstone Torch
    -> Capacitor
~~~

### Modular Accumulator

Current recipe includes:

- Brass Casing;
- Capacitor;
- Copper Rod;
- Electrum Wire.

### Digital Adapter

When ComputerCraft is loaded:

~~~text
Wired Modem
+ Brass Plate
+ Redstone Torch
    -> Digital Adapter
~~~

## Capability interpretation

Electricity should not be treated as:

> a stronger replacement for rotational Create power.

Instead:

~~~text
MECHANICAL CREATE
    local direct machine power

ALTERNATOR
    mechanical -> electrical conversion

ELECTRICAL NETWORK
    distributed transmission / storage

MOTOR
    electrical -> kinetic conversion at remote site

CC / DIGITAL ADAPTER
    computation / coordination / control
~~~

This produces a useful maturity loop:

~~~text
mechanical industry
    -> electrical generation
    -> distribution/storage
    -> remote motorization
    -> computation / instrumentation
~~~

## Geography implication

Electrical progression can combine:

- copper;
- zinc;
- Brass;
- redstone;
- gold/silver/electrum depending final recipe ecology.

This is a good reason for a mature industrial network to pull resources from several regions.

## Silver / Electrum decision

Silver does **not** earn Skyforge raw-resource status.

Current Create Crafts & Additions only uses Silver as the conditional precursor to Electrum; it does not provide a Silver worldgen economy of its own.

Electrum itself has a potentially real capability distinction:

~~~text
Copper wire   256 FE/t
Gold wire    1024 FE/t
Electrum     8196 FE/t
~~~

while current large electrical connectors/motor/generator/storage interfaces operate around the 5000 FE/t class.

Therefore:

~~~text
SILVER GEOLOGY
    -> EXCLUDE

ELECTRUM
    -> PROVISIONAL MANUFACTURED HIGH-CURRENT MATERIAL
~~~

If integrated electrical play actually needs the high-current tier, provide a minimal manufacturing/byproduct path that does not require a Silver deposit.

If Gold-level distribution is sufficient, simplify the electrical recipes and remove Electrum from required progression.

Do not invent a regional Silver economy merely to preserve an alloy recipe.

# Nether heavy industry — heat, Steel, Nethersteel, CBC

Nether access now has a stronger industrial payoff than the previously proposed Wolframite chain.

## Blaze / superheat

Blaze progression supplies:

- brewing;
- End progression;
- Create heat capability;
- CBC Nethersteel processing.

Create's Blaze Cake chain further supports superheated processing.

Current Create recipes include:

~~~text
Crushed Netherrack
    -> Cinder Flour

Cinder Flour
+ Egg
+ Sugar
    -> Blaze Cake Base

Blaze Cake Base
+ 250 mB Lava
    -> Blaze Cake
~~~

Superheating also improves current Diesel Generators crude-oil yield.

Therefore superheat survives the subtraction audit **independently of Create: Metallurgy**.

## CBC heavy-industry materials

Current CBC creates a coherent manufactured ladder without new ores:

~~~text
Iron + Coal
    -> Cast Iron

Copper + Zinc + Cinder Flour
    -> Bronze

Iron + Coal
    -> Steel

Netherite Scrap
+ Cast Iron or Steel
+ SUPERHEATED
    -> Nethersteel
~~~

These materials materially change cannon/autocannon design.

Steel supports much stronger and longer artillery than Cast Iron/Bronze.

Nethersteel further raises safe propellant stress and ballistic performance while becoming heavier and unweldable.

This gives the Nether a clean industrial payoff:

~~~text
BLAZE / SUPERHEAT
+ NETHERITE SCRAP
    -> NETHERSTEEL
    -> ADVANCED ARTILLERY / HEAVY INDUSTRY
~~~

## Create: Metallurgy status

Create: Metallurgy is now an **optional foundry-mechanics A/B**.

Its general-purpose casting, multi-fluid crucibles, bulk melting, and foundry logistics may be enjoyable enough to retain.

Its Wolframite/Tungsten/Obdurium ladder is not.

Current decision:

~~~text
Wolframite worldgen       -> EXCLUDE
Tungsten progression      -> EXCLUDE
Obdurium progression      -> EXCLUDE
Metallurgy foundry        -> A/B TEST
~~~

If the foundry is retained, re-gate its Industrial Crucible with retained Steel/refractory inputs and bridge its molten fluids to CBC common molten-metal tags.

See [Material and Process Retention Audit v0.1](material-and-process-retention-audit-v0.1.md) and [Create: Big Cannons Industrial Integration Audit v0.1](create-big-cannons-industrial-integration-audit-v0.1.md).

# End Stone / Levitite# End Stone / Levitite — new vehicle architecture

Current Aeronautics chain:

~~~text
End Stone
    -> End Stone Powder

4 End Stone Powder
+ 2 Zinc Nuggets
+ 500 mB Water
+ HEATED MIXING
    -> 500 mB Levitite Blend

Levitite Blend
+ crystallization
    -> Levitite
~~~

Current Aeronautics Ponder establishes:

- sufficient Levitite can keep a simulated contraption afloat;
- Levitite alone cannot produce altitude gain;
- separate force remains required;
- low-speed movement faces substantial resistance that falls with speed.

## Capability identity

Levitite should unlock:

- LIFT_SUPPORT;
- new heavy-platform design;
- hybrid aircraft architecture;
- stationkeeping / utility possibilities;
- later End-specialized engineering.

It should **not** mean:

- free propulsion;
- universal replacement for wings;
- universal replacement for buoyancy.

## Economic caution

Levitite's value depends heavily on **vehicle-scale material demand**.

If a serious craft needs only a trivial amount, the End Stone economy becomes one-time acquisition.

If large useful craft consume substantial Levitite, the End gains a true industrial route:

~~~text
END_STONE_QUARRY
    -> CRUSHING
    -> ZINC / WATER IMPORT
    -> MIXING
    -> CRYSTALLIZATION
    -> AIRCRAFT WORKS
~~~

This requires runtime measurement.

# Advanced propulsion — candidate capability, not current End gate

Create Propulsion: Simulated remains a strong R&D candidate for:

- reaction thrust;
- low-pressure propulsion;
- specialized End/high-altitude vehicles.

However current source audit found the Ion Thruster recipe is not intrinsically End-gated.

Therefore:

> Treat advanced propulsion as a **capability candidate**, not as an End progression reward unless the final integration deliberately creates that relationship.

Any recipe adjustment must solve a real progression need, not merely force dimensional travel.

# Capability-payoff matrix

| Resource / system | Principal capability payoff | Route value | Repeatable demand status |
|---|---|---|---|
| Copper | fluids + electrical conductors | regional mining / industry | strong |
| Zinc | Brass + capacitors + later Levitite input | regional mining / trade | strong |
| Brass | coordinated logistics/control + mature engines/electrical systems | manufactured regional network | strong |
| Redstone | automation / signaling | mining / technical hubs | strong |
| Nether Quartz | Rose Quartz / Electron Tube control chain | Nether extraction/import | moderate/strong |
| Petroleum | fuel, refining, heavy engines, fluid logistics | strategic field/refinery routes | very strong |
| Electricity | distributed power, conversion, storage, control | multi-resource technical network | strong |
| Blaze / superheat | process heat + progression + Nethersteel | fortress / farm / industrial link | strong |
| CBC Cast Iron / Bronze / Steel | materially distinct artillery/heavy-industry materials | regional metal industry / defense | strong capital + ammunition-linked |
| CBC Nethersteel | high-pressure late artillery with weight/weldability tradeoff | Netherite + superheat industrial route | strong specialist |
| Electrum | provisional high-current electrical conductor | manufactured electrical network | retain only if Gold throughput is insufficient |
| Create: Metallurgy foundry | optional bulk/general metalworking process | industrial hub | A/B; no Wolframite dependency |
| End Stone / Levitite | passive lift support / new vehicle architecture | End industrial-expedition route | recurring demand unproven |
| Advanced propulsion | specialized low-pressure/reaction propulsion | future severe-environment routes | dependency/progression unproven |

# Progression shape

The current evidence supports a **branching capability web** more than a rigid staircase.

~~~text
FIRST FLIGHT
    |
    +--> COPPER
    |      +--> fluid systems
    |      +--> wire / alternator
    |
    +--> ZINC
    |      +--> capacitor
    |      +--> BRASS
    |              +--> logistics/control
    |              +--> motor/storage
    |              +--> diesel engines
    |
    +--> REDSTONE
    |      +--> automation
    |
    +--> NETHER QUARTZ
    |      +--> rose quartz / electron tube
    |      +--> mature control
    |
    +--> NETHER / BLAZE
    |      +--> heat / superheat
    |      +--> End progression
    |      +--> Nethersteel / advanced artillery
    |
    +--> PETROLEUM
    |      +--> refinery
    |      +--> strategic fuel network
    |
    +--> ELECTRICITY
    |      +--> distribution / storage
    |      +--> remote power
    |      +--> computation integration
    |
    +--> END
           +--> Levitite
           +--> new lift-support architecture
~~~

These branches can overlap in time.

Do not force a single quest-book line unless integrated play proves it necessary.

# Route-design consequences

The capability graph should create different route motivations.

## Regional metal route

~~~text
Copper / Zinc Mine
    -> metal transport
    -> Brass / electrical workshop
~~~

Payoff:

- better logistics;
- fluids;
- electrical infrastructure.

## Nether technical route

~~~text
Fortress / Blaze
+ Quartz Mine
+ Wolframite District
    -> heat + control material + metallurgy
~~~

Payoff:

- process maturity;
- control systems;
- foundry capability.

## Petroleum route

~~~text
Oilfield
    -> Pumpjack
    -> Refinery
    -> Fuel Depot
    -> aviation / industrial consumers
~~~

Payoff:

- repeated high-throughput network.

## End industrial route

~~~text
End Stone
+ imported Zinc / Water
    -> Levitite
    -> specialized aircraft
~~~

Payoff:

- new vehicle architecture.

This remains contingent on actual Levitite consumption.

# Trade and salvage rule

A new capability should not depend on finding one exact structure.

Trade/salvage may provide:

- sample materials;
- repair parts;
- one or two advanced components;
- hints at next capability.

But mature use should eventually connect to:

- extraction;
- manufacturing;
- repeatable trade;
- renewable farm;
- strategic supply.

# Acceptance tests

## CAP-1 — first-flight independence

No audited post-flight material silently re-enters the leading first-aircraft closure.

## CAP-2 — meaningful payoff

Every strategically distributed resource unlocks or materially improves at least one capability category.

## CAP-3 — no cosmetic tier

A new material is not retained as a progression gate if it only changes recipe color without changing engineering decisions.

## CAP-4 — branch coherence

Players can pursue useful post-flight branches in multiple orders where recipe dependencies permit.

## CAP-5 — petroleum identity

Petroleum creates sustained fluid/fuel infrastructure rather than serving as a one-time crafting reagent.

## CAP-6 — electrical identity

Electricity expands distribution, conversion, storage, instrumentation, or automation rather than replacing Create rotational power wholesale.

## CAP-7 — Nether heat payoff

Blaze/superheat materially enables processes worth maintaining after the first fortress visit.

## CAP-8 — material-subtraction integrity

Silver, Tin, Wolframite, Tungsten, and Obdurium do not enter Skyforge geology/progression unless a distinct retained engineering role independently justifies them.

## CAP-8A — CBC heavy-industry payoff

Cast Iron, Bronze, Steel, and Nethersteel produce observable engineering differences worth their manufacturing steps.

## CAP-8B — Metallurgy foundry gate

Create: Metallurgy remains only if CBC/general metal production is materially more useful or enjoyable with its foundry than without it.

## CAP-9 — Levitite demand measurement

End Stone/Levitite processing scale is based on useful vehicle designs, not assumed late-game prestige.

## CAP-10 — advanced-propulsion integrity

Reaction/ion propulsion solves a distinct operating-envelope problem if selected and does not become universal propulsion by default.

## CAP-11 — cross-domain continuity

Earlier resources such as zinc remain relevant in later technologies where existing recipes naturally support that continuity.

## CAP-12 — route payoff

A player can explain why they established each major resource route in terms of what capability it enabled.

# Manual / integrated evidence

During progression playtesting, record:

~~~text
RESOURCE ACQUIRED
    -> first new useful machine
    -> first new route/infrastructure
    -> quantity consumed
    -> repeat demand after initial construction
~~~

For each branch specifically measure:

- Copper consumed by fluid/electrical infrastructure;
- Zinc consumed directly vs through Brass;
- Brass consumed by logistics, motors, storage, engines, aircraft;
- Quartz consumed by mature Create controls;
- petroleum consumption rate under representative aviation/industry;
- electrical network size, Gold-wire limits, and whether Electrum/high-current distribution is actually needed;
- Cast Iron/Bronze/Steel/Nethersteel consumed by representative CBC installations;
- ammunition throughput and recurring Gunpowder/Brass/Redstone demand;
- CBC-only versus CBC+Metallurgy factory footprint, throughput, clarity, and enjoyment;
- Levitite required by representative utility/cargo/expedition aircraft.

# Current strongest progression thesis

> Post-flight progression should be a sequence of **new engineering verbs**, not simply new materials.

~~~text
FLY
    -> ROUTE
    -> PUMP
    -> SORT
    -> CONTROL
    -> REFINE
    -> DISTRIBUTE POWER
    -> COMPUTE / SENSE
    -> SUPPORT HEAVIER MACHINES
    -> OPERATE IN NEW DIMENSIONS
~~~

# Acceptance principle

> A resource earns geographic importance when obtaining it changes what the player can engineer strongly enough that the trip was worth making.
