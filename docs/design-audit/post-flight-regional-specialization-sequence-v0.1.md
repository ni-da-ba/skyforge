# Post-Flight Regional Specialization Sequence v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Concrete recipe counts and travel distances remain playtest-dependent.

## Core principle

> The first powered aircraft should immediately reveal why the world is geographically distributed.

Resource geography is justified by **capability payoff**, not by nominal material tier.

See [Post-Flight Capability Payoff Audit v0.1](post-flight-capability-payoff-audit-v0.1.md).

Skyforge should not wait several progression tiers before resource geography matters.

Once the player proves a practical first aircraft, the world may begin shifting from:

~~~text
local bootstrap completeness
~~~

toward:

~~~text
regional specialization
    -> repeatable routes
    -> freight
    -> industry
~~~

The first post-flight loop should be understandable without a quest book.

## Precondition

The current leading first powered aircraft is intentionally pre-brass and pre-petroleum.

Current non-mandatory material set includes:

~~~text
COPPER
ZINC
BRASS
PETROLEUM
ELECTRICITY
GOLD
DIAMOND
NETHER MATERIAL
~~~

subject to the remaining in-game first-aircraft proof.

Therefore those resources are available as **rewards for flight** rather than prerequisites for it.

## Sequence overview

Preferred broad sequence:

~~~text
P2 FIRST POWERED FLIGHT
    |
    +--> copper / zinc regional engineering
    |       |
    |       +--> fluid handling
    |       +--> brass
    |       +--> improved Create logistics/control
    |       +--> more mature aircraft hardware
    |
    +--> redstone / richer mineral districts
    |       |
    |       +--> automation
    |       +--> remote control / signaling
    |       +--> navigation infrastructure
    |
    +--> petroleum strategic nodes
    |       |
    |       +--> refining
    |       +--> higher-energy fuel
    |       +--> heavy aviation / industrial power
    |
    +--> electrical / advanced processing
    |       |
    |       +--> instrumentation
    |       +--> CC / radar integration
    |       +--> mature distributed industry
    |
    +--> END / DIMENSIONAL AVIATION
            |
            +--> End Stone -> End Stone Powder
            +--> zinc + water + heat -> Levitite Blend
            +--> crystallized Levitite
            +--> passive lift-support aircraft
            +--> later low-pressure propulsion if selected
~~~

This is a capability sequence, not a rigid linear tech tree.

## R2-A — copper and zinc districts

### Role

Copper and zinc are the strongest first regional-engineering pair.

They should be:

- common enough that the player can deliberately find them;
- concentrated enough that some districts are visibly valuable;
- unnecessary for the tested first aircraft;
- useful immediately after acquisition.

### Copper

Current Create use makes copper especially useful for fluid infrastructure.

Source-backed examples include:

- fluid pipes;
- fluid tanks;
- hose pulley;
- spout;
- valve-related components;
- Brass production.

This makes copper a natural bridge between early mechanical industry and later fuel/refining logistics.

### Zinc

Zinc's strongest immediate identity is:

~~~text
ZINC
    + COPPER
    + HEATED MIXING
    -> BRASS
~~~

Do not make zinc an ultra-rare expeditionary ore.

Its geography should instead support:

- identifiable mining districts;
- regional trade;
- mining settlements;
- repeated moderate-volume hauling.

### First route pressure

A normal starter cluster should not need industrial copper/zinc abundance.

A plausible authored pattern is:

~~~text
STARTER CLUSTER
    enough for first powered aircraft
    little or no industrial copper/zinc

NEARBY REGIONAL DISTRICT
    strong copper or zinc geology
    visible mine / settlement / geological evidence

SECONDARY DISTRICT
    complementary metal or richer deposit
~~~

The player's first aircraft therefore creates an immediate question:

> Where do I establish my first useful route?

## R2-B — Brass as the first manufactured regional reward

Current Create 1.21.1 source produces Brass through **heated mixing of copper and zinc**.

That is an excellent progression structure because Brass combines:

- regional extraction;
- processing;
- heat;
- workshop expansion.

Brass should feel like:

> I can now build a more mature engineering system because I can reach and combine distributed resources.

Source-backed Create uses include:

- Brass Funnels;
- Brass Tunnels;
- Mechanical Arms;
- Redstone Links;
- Smart Fluid Pipes;
- Brass Casings.

The current Aeronautics recipe audit also found a Brass Casing dependency in the Propeller Bearing path.

Therefore Brass can naturally improve:

- sorting;
- routing;
- automation;
- remote/control infrastructure;
- fluid systems;
- later aviation hardware.

### Heat / Nether interaction

Do not accidentally turn Brass into a new first-flight gate.

The current recipe requires heated mixing.

If the final selected heat path requires Nether/Blaze progression, that is acceptable because the first aircraft already exists.

This can produce a useful intersection:

~~~text
regional copper/zinc
    + dimension progression / heat
    -> Brass
~~~

But the pack should not require Brass to obtain the aircraft needed to reach the regional metals.

## R2-C — redstone and richer mineral districts

Redstone should become increasingly important after the player is mobile.

Roles:

- automation;
- signaling;
- sensing;
- Create control components;
- future CC/radar infrastructure;
- settlement technical specialization.

Redstone need not be a strategic-node resource like petroleum.

Preferred geography:

~~~text
ordinary regional access
    + some richer deep-geology districts
~~~

The player should be able to obtain useful quantities without an expedition to a legendary site.

Large redstone-rich districts can still justify:

- mining centers;
- industrial trade;
- freight routes.

## R2 resource quantity split

For copper, zinc, redstone, and iron, distinguish **sample access** from **industrial supply**.

~~~text
SMALL OCCURRENCE
    enough to experiment / repair / build first advanced component

RICH DISTRICT
    enough to sustain industry / export / route traffic
~~~

This avoids two failures:

1. total absence causing recipe frustration;
2. tiny local deposits supplying an entire industrial game.

A starter province may contain small examples without containing every large deposit.

## R3 — petroleum as the first true strategic-node resource

Petroleum should be qualitatively different from copper/zinc.

Preferred behavior:

~~~text
many provinces
    no oilfield

some provinces
    evidence / imported refined fuel

selected geological districts
    commercial petroleum field
    pumpjacks
    refinery
    tank farm
    fuel export
~~~

This creates a strong reason for:

- cargo aircraft;
- tanker aircraft/airships;
- fuel depots;
- trade;
- route protection;
- settlement specialization.

### Capability reward

Petroleum should improve:

- sustained engine power;
- heavy aircraft viability;
- operational range;
- industrial generation;
- mature fuel logistics.

It should not be required for the first practical aircraft.

### Freight identity

Petroleum is especially valuable because fluid logistics are difficult to replace with personal glider couriering.

A player may carry emergency fuel.

A mature region should move fuel through tanks, vehicles, depots, and routes.

## R3/R4 — electricity and instrumentation

Create Crafts & Additions currently leads the electricity slot.

Current 1.21.1 source suggests electricity itself has an internal maturity gradient:

~~~text
Alternator
    Andesite Alloy + iron + copper spool
    -> mechanical-to-electric generation can appear relatively early

Electric Motor
    Brass + capacitor + copper spool
    -> electric-to-kinetic conversion is more mature

Modular Accumulator
    Brass Casing + capacitor + copper + electrum wire
    -> storage adds another dependency layer

Digital Adapter
    wired modem + Brass + redstone
    -> CC/Create coordination
~~~

Its strategic value is not merely another power tier.

Electricity should enable:

- motors/alternators;
- distributed power;
- batteries/storage;
- instrumentation;
- weather sensing;
- radar;
- CC-based control;
- remote infrastructure.

Potential input geography includes:

- copper;
- zinc;
- Brass;
- redstone;
- gold;
- silver/electrum only if the final selected stack supplies a coherent Silver path;
- manufactured electrical components.

Do not add a Skyforge Silver resource merely because the current Modular Accumulator recipe references electrum. Resolve the final tag/recipe ecology first.

This should emerge after the player already understands mechanical Create and aviation.

Design goal:

> Mechanical systems move the world; electrical systems help the player measure, coordinate, and automate it.

## R3/R4 — metallurgy

Create: Metallurgy should remain processing depth rather than worldgen authority.

Nether-native Wolframite can remain dimension-native for now.

Advanced metallurgy should reward:

- wider exploration;
- better processing;
- specialized industrial sites;

without forcing every ordinary aircraft upgrade through exceptional loot.

## Later dimensional aviation — End Levitite

Current Aeronautics gives the End a direct capability reward rather than merely rare loot.

Source-backed chain:

~~~text
End Stone
-> End Stone Powder
+ zinc + water + heated mixing
-> Levitite Blend
-> crystallization
-> Levitite
~~~

Aeronautics' own Ponder behavior gives Levitite a useful progression identity:

- enough Levitite can keep a contraption afloat;
- Levitite alone cannot make it climb;
- additional force remains necessary;
- low-speed resistance creates a handling cost.

This means the mature Overworld zinc economy remains relevant when the player reaches the End.

The End can therefore broaden aviation from:

~~~text
aerodynamic lift + engines
~~~

toward:

~~~text
aerodynamic lift
+ passive levitation support
+ propulsion
+ later low-pressure-specialized propulsion
~~~

without invalidating first flight or the regional R2/R3 economy.

See [End Aeronautics Progression Contract v0.1](end-aeronautics-progression-contract-v0.1.md).

## Province-level specialization examples

### Engineering-metal province

Exports:

- copper;
- zinc;
- iron;
- Brass or semi-finished goods where industry exists.

Imports:

- food;
- fuel;
- specialist machinery.

Visible evidence:

- mines;
- spoil;
- rail/haulage;
- workshops;
- cranes;
- warehouses.

### Fuel province

Exports:

- crude petroleum;
- refined fuel.

Imports:

- metal;
- machinery;
- food.

Visible evidence:

- derricks/pumpjacks;
- tank farms;
- refinery stacks;
- fuel depots;
- heavy freight infrastructure.

### Agricultural province

Exports:

- food;
- fiber;
- livestock;
- biomass.

Imports:

- machinery;
- metal;
- fuel.

Visible evidence:

- terraces;
- fields;
- mills;
- storage;
- livestock infrastructure.

### Technical / regional hub

Imports:

- bulk raw materials;
- fuel;
- food.

Exports:

- components;
- information;
- repair;
- navigation services;
- finished machinery.

Visible evidence:

- airfield;
- radar/weather station;
- beacon;
- large workshops;
- warehouses.

## Civilization teaching

Generated settlements should make this sequence observable.

A player who has not read a quest book might infer:

~~~text
mining settlement
    -> metal comes from here

refinery settlement
    -> fuel comes from here

regional airfield
    -> these places are connected by aircraft

warehouse / cargo apron
    -> throughput matters

weather / radar station
    -> mature aviation uses information
~~~

Civilization is therefore a demonstration of the same logistics grammar the player can build.

## Starter-region authoring implication

The starter cluster must contain the first-aircraft closure.

It should **not** also need to contain industrial quantities of every R2 resource.

Preferred pattern:

~~~text
STARTING GROUP
    survival + local mobility

STARTING CLUSTER
    workshop + adhesive + first powered aircraft

NEARBY R2 REGIONS
    copper / zinc / richer iron / redstone specialization

BROADER R3 REGIONS
    petroleum / mature industrial specialization
~~~

Exact spatial scales remain empirical.

## No hard single route

The player may obtain early R2 materials through:

- direct mining;
- civilization trade;
- limited salvage;
- multiple regional deposits.

Do not require one specific generated settlement or one exact island.

The important guarantee is semantic reachability, not a prescribed itinerary.

## Trade and salvage

### R2

Trade/salvage may provide enough copper/zinc/Brass/redstone to:

- repair;
- experiment;
- craft a small number of advanced components.

### R3

Trade may provide emergency fuel or small refined quantities.

It should not automatically supply mature industrial throughput without a functioning economy/route.

### R4

Exceptional components may be bought or salvaged in small quantities, but mature automation should still require industrial production.

## Mobility-bypass interaction

This sequence assumes powered aviation remains economically meaningful.

Therefore evaluate it together with:

- glider thermal routes;
- Elytra rocket suppression;
- portal coordinate scaling;
- portable storage;
- teleport dependencies.

A resource geography design is not valid if another mobility/storage mechanic makes its routes irrelevant.

## Acceptance tests

### PFR-1 — immediate reason to fly

Within ordinary post-flight play, the player can identify at least one useful regional destination whose resources improve engineering capability.

### PFR-2 — no bootstrap regression

Copper, zinc, Brass, petroleum, electricity, gold, diamond, or Nether material is not silently reintroduced into the leading first-aircraft closure without an explicit recipe-proof update.

### PFR-3 — copper/zinc usefulness

Acquiring regional copper/zinc creates a real capability increase rather than merely decorative material variety.

### PFR-4 — Brass payoff

Brass unlocks meaningful Create/aviation/logistics capability after first flight.

### PFR-5 — sample versus industry

Small local occurrences permit experimentation while rich districts remain economically valuable.

### PFR-6 — petroleum route pressure

Petroleum concentration creates a reason for freight infrastructure rather than a hard pre-flight dependency.

### PFR-7 — civilization legibility

Representative mature settlements visibly communicate what they produce/import and why routes exist.

### PFR-8 — multiple acquisition paths

No ordinary R2 progression depends on one unique settlement, structure, or lucky loot roll.

### PFR-9 — logistics survives convenience

Gliders, portable storage, portals, and teleport systems do not make the regional resource network decorative.

## Evidence required before lock

During pack playtesting, record:

- first destination chosen after powered flight;
- why the player chose it;
- what capability the acquired resource unlocked;
- number of trips before cargo capacity became desirable;
- whether trade/salvage bypassed extraction too strongly;
- whether player-portable storage suppressed freight demand;
- whether petroleum created useful route pressure;
- whether any resource unexpectedly became a first-flight dependency.

## Acceptance principle

> First flight should not end progression. It should turn the horizon into an economy.
