# General Engineering Power Stack v0.1

**Snapshot:** 2026-09-29  
**Status:** Provisional strong selection / integration contract. Numerical balance, exact recipes, and compatibility tuning remain later work.

## Decision

Skyforge should use a small number of interacting energy domains rather than multiple parallel tech trees.

~~~text
MECHANICAL
  Create rotational power
  -> direct machinery
  -> bearings / actuators
  -> alternator

ELECTRICAL
  Create Crafts & Additions
  -> transmission
  -> storage
  -> computation / avionics / control
  -> motor -> mechanical work
  -> ion propulsion

CHEMICAL / FLUID
  Create Diesel Generators petroleum
  -> diesel / gasoline / refined fuels
  -> compact mechanical engines
  -> SJE / Aero Propulsion aviation fuel
  -> Create Propulsion reaction fuel

THERMAL
  Create heat / steam / superheat
  -> stationary power
  -> process gates
  -> refinery efficiency
  -> advanced metallurgy
~~~

The domains should reinforce each other through conversion and logistics while preserving distinct engineering reasons to use each one.

## Selected stack

| Capability | Preferred system | Disposition |
|---|---|---|
| General mechanical power | Create | Core |
| Steam / boiler power | Create | Core |
| Physical vehicle / moving-machine physics | Sable + Create Aeronautics | Core |
| Electrical generation / transmission / storage / motors | Create Crafts & Additions | Strong keep |
| Mobile electrical compatibility | CC&A Sable compatibility | Strong provisional |
| Petroleum extraction / refining / diesel power | Create Diesel Generators | Strong keep |
| Advanced atmospheric propulsion | SJE / Aero Propulsion | Strong provisional keep |
| Reaction / vector / solid / ion propulsion | Create Propulsion: Simulated | Strong provisional keep |
| Linear physical actuation | Create Linear Bearing | Strong provisional |
| General computation / control | CC:Tweaked + Create: Avionics | Accepted baseline |
| Physical control cabling / manual input | Drive By Wire + Tweaked Controllers | Strong provisional |
| Advanced/nuclear heat | Create: New Age | Reserve only |
| Foundry | Create: Metallurgy | Separate unresolved A/B |

## Mechanical domain

Create rotational power remains the default language of industrial work.

Preferred interpretation:

~~~text
water / wind / simple heat
  -> direct rotational power
  -> local machines

steam
  -> large stationary power
  -> infrastructure-heavy but sustainable

diesel
  -> compact dispatchable mechanical power
  -> recurring strategic-fuel cost

electric motor
  -> remote / distributed mechanical power
  -> convenience bought with conversion loss and electrical infrastructure
~~~

Electricity must not make shafts, gearboxes, steam, or direct kinetic systems obsolete.

## Linear Bearing

Create Linear Bearing is a strong provisional actuator because it adds a physical translational joint to the Sable/Aeronautics vocabulary.

Target roles include:

- landing gear;
- cargo doors and ramps;
- lifts/elevators;
- cranes;
- telescoping machinery;
- deployable structures;
- vehicle bays;
- moving ballast;
- selected weapon/equipment mounts.

It does not define a new energy domain. It is an actuator that should consume or respond to the existing Create/Sable control and mechanical vocabulary.

Required compatibility work:

- constrained-axis behavior;
- safe assembly/disassembly;
- persistence;
- moving-Sable-vehicle behavior;
- network/control integration;
- force/mass behavior under load.

## Electrical domain

Create Crafts & Additions remains the single preferred general electricity ecosystem.

Current progression intent:

~~~text
MECHANICAL
  -> Alternator
  -> electrical network
  -> storage
  -> Motor
  -> MECHANICAL

electrical network
  -> CC / avionics / sensors / fire control
  -> high-current specialist consumers
  -> ion propulsion
~~~

Material doctrine:

- Copper: baseline electrical conductor / fluid infrastructure;
- Zinc: capacitors + Brass + later Levitite;
- Brass: mature motor/storage/control machinery;
- Gold: baseline mature conductor tier;
- Electrum: optional high-current manufactured material only if actual integrated loads justify it;
- Silver geology: excluded.

The Modular Accumulator should not force Silver/Electrum progression if the existing Gold-compatible wire tag can express baseline storage.

## Chemical / petroleum domain

Create Diesel Generators remains the preferred petroleum machinery library.

Authority split:

~~~text
Skyforge
  -> petroleum geography / strategic fields / availability

Create Diesel Generators
  -> pumpjack / crude fluid / refining / storage / diesel engines
~~~

Petroleum should produce durable infrastructure:

~~~text
FIELD
  -> PUMPJACK
  -> CRUDE STORAGE
  -> REFINERY
  -> DIESEL / GASOLINE / AVIATION OR REACTION FUELS
  -> TANK FARM / DEPOT
  -> INDUSTRIAL + AVIATION CONSUMERS
~~~

Petroleum is not a first-flight requirement. It becomes valuable for mature aviation, compact engines, heavy machinery, and recurring logistics.

## Propulsion boundary

The preferred propulsion hierarchy is:

~~~text
Aeronautics propellers
  -> efficient ordinary atmospheric flight

SJE / Aero Propulsion
  -> constructed high-performance air-breathing engines
  -> petroleum / aviation-fuel economy

Create Propulsion: Simulated chemical / solid / vector
  -> reaction propulsion
  -> VTOL / high-thrust / thin-air / specialist roles

Create Propulsion: Simulated ion
  -> high-electricity low-pressure specialist propulsion
~~~

Exact fuel costs, oxidizer balance, stock solid-fuel cleanup, Platinum normalization, and pressure curves remain part of the later recipe/balance pass.

Do not add additional generic thruster families unless a demonstrated capability gap remains.

## Thermal domain

Create heat/superheat remains important independently of any foundry mod.

Retained roles include:

- heated processing;
- Brass;
- CBC Cast Iron / Bronze / Steel;
- superheated petroleum refining;
- Nethersteel;
- Create boiler/steam power;
- retained advanced processes.

Create: New Age is reserved as the leading future nuclear/advanced-heat candidate only if Skyforge later needs a nuclear branch. Its duplicate electrical/material progression is not adopted by default.

## Conversion and balance doctrine

1. Conversion systems should lose enough energy or require enough infrastructure that closed-loop conversion is not advantageous.
2. Mechanical power is cheapest for direct local work.
3. Electricity earns its place through transmission, storage, control, and high-current specialist consumers.
4. Diesel earns its place through compactness and dispatchability, not universal efficiency.
5. Steam earns its place through scalable stationary infrastructure and broad fuel/heat access.
6. Petroleum creates recurring logistics and strategic geography.
7. Jet propulsion wins on efficient atmospheric high-performance flight.
8. Chemical reaction propulsion wins on high thrust and atmosphere independence but pays in fuel/oxidizer/endurance.
9. Ion propulsion wins only in low-pressure endurance niches and should demand substantial electrical infrastructure.
10. Recipe sophistication should combine existing retained industries rather than introduce one new ore per machine.

## Recipe doctrine

Prefer the existing material graph:

~~~text
IRON / ANDESITE
  -> basic machinery

COPPER
  -> fluids / wiring

ZINC
  -> capacitors / Brass / Levitite

BRASS
  -> mature engines / control / storage / logistics

GOLD
  -> mature electrical distribution

REDSTONE + QUARTZ
  -> control / instrumentation

STEEL
  -> heavy structural machinery where appropriate

PETROLEUM
  -> stored chemical energy / refined fuels

NETHER HEAT
  -> superheat / process efficiency
~~~

Do not introduce Silver, Platinum ore, Tungsten, Obdurium, or other geology solely to satisfy stock recipes from retained addons.

## Deferred compatibility / balance work

- exact CC&A transfer rates and high-current demand;
- Electrum retain/remove decision;
- Sable electrical-wire persistence and moving-vehicle behavior;
- Alternator/Motor conversion tuning;
- steam vs diesel SU economics;
- SJE fuel/thrust/pressure behavior;
- Create Propulsion fuel/oxidizer/ion balance;
- removal/normalization of trivial solid rocket fuels;
- Platinum recipe/worldgen normalization;
- Linear Bearing constrained motion and persistence;
- onboard auxiliary power / Stirling-engine role;
- performance under large factories and multiple moving vehicles.

## Acceptance boundary

This document establishes the preferred general energy architecture. It does not lock numerical values or exact recipes.

Production lock requires:

1. exact dependency/version closure;
2. cross-mod recipe inventory;
3. representative stationary power tests;
4. representative moving-vehicle electrical tests;
5. propulsion pressure/fuel tests;
6. conversion-loop exploit checks;
7. persistence/multiplayer verification;
8. performance characterization;
9. final recipe/balance normalization.

## Design principle

> Mechanical, electrical, chemical, and thermal technologies should solve different engineering problems and convert between one another without collapsing into a single universally superior power system.
