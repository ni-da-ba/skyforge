# Resource Role Matrix v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design matrix. Concrete recipe-tier assignments remain provisional until the final modpack recipe audit.

## Purpose

Translate the resource-geography model into an initial concrete classification for vanilla and leading engineering-stack resources.

The matrix is intentionally conservative:

- survival/first-flight inputs skew common;
- strategic resources skew geographically concentrated;
- rare progression materials retain direct-mining paths;
- trade/salvage provide limited alternatives;
- third-party worldgen remains subordinate to Skyforge geography.

## Availability vocabulary

~~~text
U  = UBIQUITOUS / local-cluster expected
C  = COMMON_REGIONAL
S  = SPECIALIZED_REGIONAL
N  = STRATEGIC_NODE
E  = EXCEPTIONAL
~~~

## Access vocabulary

~~~text
MINE
HARVEST
RENEW
TRADE
SALVAGE
STRUCTURE
DIMENSION
~~~

## Initial matrix

| Resource / family | Availability | First-flight critical? | Primary cause | Alternative access | Civilization role | Notes |
|---|---:|---:|---|---|---|---|
| Wood / renewable building material | U | Yes | ecology/climate | trade | settlement/farm | Starting region must have a renewable construction path |
| Food | U | Yes | ecology/agriculture | trade | farms/markets | Not every island needs food production |
| Water | U/C | Yes | hydrology | trade/storage only as backup | settlement/agriculture | Starting region requires viable water access |
| Stone / aggregate | U | Yes | island geology | trade | quarry/building | Ordinary island mass supplies baseline |
| Andesite / early Create stone family | U/C, locally guaranteed | Yes | island lithology | trade/salvage | workshop/quarry | Audited Andesite Alloy path can use iron nuggets; no zinc requirement |
| Wool / fiber / sail material | U/C, locally guaranteed | Yes | ecology/agriculture | trade | farms/textiles | Required by leading early aerodynamic-surface path |
| Adhesive-path inputs | U/C, selected-path guarantee | Yes | ecology/resource path | trade/salvage | workshop supply | Physics Assembler closure requires Super Glue or Honey Glue path |
| Charcoal / biomass fuel | U/C | Yes | ecology | trade | farms/frontier | Portable Engine can use ordinary fuel; petroleum not required |
| Coal | C/S | No | geology | trade | mines/industry | Large coal districts can be strategic without making coal mandatory |
| Iron | C, locally guaranteed | Yes | mineral geology | trade/salvage | mining/industry | Major early workshop/aircraft cost; bootstrap completeness requires reasonable access |
| Copper | C | No | mineral geology | trade/salvage | fluid/electrical/industry | Preferred early-R2 regional engineering metal; not current first-flight closure |
| Zinc (Create) | C | No | mineral geology | trade/salvage | Create industry/mining | Preferred early-R2 regional engineering metal; native worldgen redirected through Skyforge |
| Brass (Create) | manufactured from C+Z | No | heated processing | trade/salvage limited | advanced Create industry | Natural immediate post-flight reward; current Create recipe is heated copper + zinc mixing |
| Redstone | C/S | No, early-mid | deep mineral geology | trade/structure | automation/electrical | Reliable regional access before advanced controls |
| Gold | S | No | mineral geology | trade/structure | electrical/trade | Stronger regional identity acceptable |
| Lapis | C/S | No | mineral geology | trade | vanilla enchanting | No need to elevate to strategic status |
| Diamond | S | No | deep mature geology | trade/structure limited | high-value mining | Direct mining remains valid; favor large deep islands |
| Emerald ore | S | No | regional geology | villager economy | trade | Ore need not supply most currency circulation |
| Quartz | DIMENSION | No | Nether | trade/structure optional | advanced recipes | Keep Nether identity unless later design says otherwise |
| Blaze products | DIMENSION | No | Nether | trade only if intentionally limited | progression | Preserve normal dimension progression |
| End materials | DIMENSION/E | No | End | exceptional trade only | late progression | Preserve End identity |
| Crude petroleum | N | No | subsurface geology | trade/salvage limited | oilfields/refineries | Strong logistics resource; not first-flight gate |
| Refined diesel/fuels | S/N | No | processing/logistics | trade/salvage | airfields/industry | Regional network commodity |
| Electrical storage/components | S by production | No | industry/recipes | trade/salvage | hubs/weather/nav | Worldgen quantity in active sites should be budgeted |
| Advanced propulsion/control components | S/E by production | No | industry/progression | salvage/hostile reward limited | airfields/military | Active-site hoover-sensitive |
| Metallurgical intermediates/alloys | S by processing | No | ore + industry | trade/salvage | industrial hubs | Processing geography more important than raw worldgen |
| Rare artifacts | E | No | exceptional structures/ecology | none/structure | exploration | Not bulk industrial inputs |
| Boss/legendary drops | E | No | encounter | none | exceptional | Should unlock/improve special capability, not basic survival |

## Bootstrap completeness set

The current recipe audit has already narrowed the leading **powered first-flight** material closure.

A normal starting region should currently prove access to:

~~~text
survival
-> iron-class tools
-> basic Create workshop
-> iron + andesite / Andesite Alloy
-> wool/fiber aerodynamic material
-> ordinary fuel
-> adhesive path
-> local crossing / recovery capability
-> first powered Aeronautics aircraft
-> basic repair
-> basic navigation
~~~

Copper, zinc, Brass, petroleum, electricity, gold, diamond, and Nether materials are **not currently demonstrated as mandatory** for the leading first-flight prototype.

Every normal starting-region world plan should prove the tested closure once the manual aircraft run is complete.

The closure may include trade/salvage only when those alternatives are reliably present rather than probabilistic lucky finds.

## Resource-scale split

A resource can be locally present while still having meaningful regional specialization.

Example iron:

~~~text
small ordinary occurrence
-> enough for tools / early machines

rich iron district
-> industrial mine
-> town
-> freight export
~~~

Therefore a future resource descriptor should separate:

~~~text
presence
grade / quality
deposit scale
accessibility
processing difficulty
~~~

This avoids using rarity as the only economic lever.

## First-flight audit

Any ingredient required by the final first-flight recipe path must be classified as:

- UBIQUITOUS; or
- COMMON_REGIONAL with an explicit starting-region guarantee.

This includes indirect dependencies.

Example:

~~~text
flight block recipe
needs component A
component A needs zinc
zinc therefore participates in first-flight closure
~~~

However, the current audit found that the leading first aircraft can avoid this zinc path.

The audit must continue to follow recipes transitively rather than classify materials from Create familiarity or intuition.

## Strategic petroleum policy

Crude oil is the strongest current candidate for a true strategic-node resource.

Desired distribution:

~~~text
many regions:
    no petroleum

some regions:
    small petroleum indications / trade

few suitable geological districts:
    large commercial deposits
    pumpjacks
    refineries
    fuel storage
    freight routes
~~~

This makes fuel geography visible.

However the player must always retain at least one non-petroleum route to practical early air mobility.

## Electricity-material policy

If Create Crafts & Additions is selected:

- copper/gold/redstone demand should be audited against geography;
- electrical infrastructure should be concentrated more strongly in mature settlements than basic mechanical Create;
- batteries/capacitors/advanced electrical equipment in active settlements must pass the civic-hoover audit;
- electricity should extend mechanical infrastructure rather than replace it wholesale.

## Metallurgy policy

If Create: Metallurgy is selected:

- use its processing mechanics;
- map its raw-material requirements onto Skyforge semantic resource families;
- avoid duplicate independently generated ores;
- review any unique ore such as wolframite/tungsten-class material for R3/R4 role rather than allowing default distribution to define Skyforge geography.

## Civilization trade profiles

### Agricultural cluster

Likely exports:

- food;
- seeds;
- renewable biological materials.

Likely imports:

- machinery;
- fuel;
- metals.

### Mining settlement

Exports:

- raw ore;
- stone;
- mineral products.

Imports:

- food;
- machinery;
- fuel;
- replacement components.

### Industrial hub

Imports:

- ores;
- fuel;
- bulk food.

Exports:

- processed materials;
- components;
- machinery.

### Fuel region

Exports:

- crude/refined fuel.

Imports:

- machinery;
- food;
- metals.

### Regional hub

Provides broad trade but need not produce most goods locally.

## Salvage policy by tier

### R0/R1 resources

Salvage can provide useful emergency quantities but should rarely be the only guaranteed source.

### R2 resources

Salvage is a strong introduction path.

The player may recover enough to:

- repair;
- experiment;
- build one key component.

### R3 resources

Salvage can accelerate entry but should not sustain industrial throughput.

### R4 resources

Exceptional salvage/structure rewards may be the intended source.

## Resource absence as information

A province with no petroleum or poor metal geology should make that absence legible through civilization:

- few/no refineries;
- imported fuel storage;
- dependence on agriculture/trade;
- smaller industrial footprint.

Conversely, a resource-rich region advertises itself through:

- mines;
- quarries;
- industry;
- freight infrastructure;
- settlement specialization.

This lets players infer resource geography before directly mining every island.

## Worldgen-authority implications

The implementation audit should inventory every selected mod that adds:

- ore features;
- deposits;
- oil reservoirs;
- geodes;
- plants/crops;
- structure-specific raw resources.

Each source receives one of:

~~~text
DISABLE_NATIVE_WORLDGEN
REDIRECT_THROUGH_SKYFORGE
ALLOW_DIMENSION_NATIVE
ALLOW_STRUCTURE_ONLY
ALLOW_AS_IS_WITH_PROOF
~~~

Default for Overworld resource worldgen should be **REDIRECT_THROUGH_SKYFORGE** or **DISABLE_NATIVE_WORLDGEN** when Skyforge already owns the semantic layer.

## Acceptance principle

> Foundational resources are reliable enough to prevent soft locks; strategic resources are concentrated enough to create routes; exceptional resources are rare enough to create expeditions.
