# Manufacturing and Foundry Stack v0.1

**Snapshot:** 2026-09-29  
**Status:** Provisional closure. Exact recipe economics, throughput, ore yield, and CBC balance remain deferred.

## Decision

Skyforge should retain Create: Metallurgy primarily for **industrial process verbs**, not for its native material progression.

The preferred manufacturing split is:

~~~text
CREATE
  -> crushing / washing / pressing / sawing / mixing / sequenced assembly

CREATE: METALLURGY
  -> industrial melting / molten handling / alloying / casting / foundry tooling

CBC
  -> canonical Cast Iron / Bronze / Steel / Nethersteel identities
  -> cannon casting / boring / built-up cannon manufacture

SKYFORGE
  -> recipe normalization
  -> material authority
  -> workshop-vs-industrial balance
~~~

## Core principle

The foundry should increase **scale, automation, throughput, logistics integration, and direct molten-metal handling**. It should not become a mandatory tax on access to ordinary CBC weapons.

Therefore:

- CBC's simpler workshop-scale metal routes remain available unless later balance testing proves otherwise.
- Metallurgy provides a developed industrial route to the same canonical materials.
- There is no separate “foundry Steel” or “CBC Steel.”
- Mature plants may route molten metal directly into CBC cannon casting without unnecessary ingot-remelt loops.

## Material disposition

| Material | Disposition |
|---|---|
| Iron | retained; Vanilla semantic owner |
| Copper | retained; Vanilla semantic owner |
| Gold | retained; Vanilla semantic owner |
| Zinc | retained; Create semantic owner |
| Brass | retained; Create semantic owner |
| Cast Iron | retained; CBC semantic owner |
| Bronze | retained; CBC semantic owner; Tin-free Skyforge route preferred |
| Steel | retained; CBC semantic owner; Metallurgy industrial production route |
| Nethersteel | retained; CBC semantic owner; specialist superheated alloy |
| Coke | retain as foundry process material |
| Graphite | retain as casting/tooling material |
| Refractory Mortar | retain as foundry/refractory process material |
| Slag | retain as metallurgical byproduct/recycling material |
| Tungsten | reserve specialist material only; no current progression dependency |
| Wolframite | native Nether worldgen disabled; reserve only |
| Obdurium | remove/hide from player-facing progression |
| Tin | do not introduce solely for Bronze |
| Silver | no geology; possible secondary/byproduct material only if Electrum earns retention |
| Electrum | conditional high-current material only if integrated electrical demand proves useful |

## CBC relationship

CBC remains authoritative for cannon manufacturing.

Retain:

- Cannon Casts;
- Cannon Drill / boring;
- built-up cannon manufacture;
- cannon material behavior;
- ammunition and weapon assembly;
- CBC canonical solid/fluid identities where practical.

Metallurgy should complement rather than replace this downstream manufacturing.

### Workshop route

A player making a small number of weapons may use CBC's simpler existing routes, for example:

- Iron + carbon -> Cast Iron;
- Copper/Zinc/Cinder -> Bronze;
- Iron + carbon -> Steel;
- superheated Steel/Cast Iron + Netherite Scrap -> Nethersteel.

These remain intentionally accessible pending later balancing.

### Industrial route

A developed plant can instead use:

- ore preparation;
- foundry melting;
- coke / carbon processing;
- molten alloying;
- slag handling;
- ladles/faucets/tundishes;
- direct bulk casting;
- direct molten feed into CBC cannon casting.

The payoff should be industrial scale rather than prerequisite complexity.

## Create: Metallurgy retained content

Strongly favored:

- Foundry Basin;
- Foundry Mixer;
- Casting Table;
- Casting Basin;
- Industrial Crucible;
- ladles;
- faucets;
- tundishes;
- graphite molds;
- Mechanical Belt Grinder where it adds useful fabrication;
- Coke;
- Graphite;
- Refractory Mortar;
- Slag.

Native material/worldgen content is subject to normalization.

## Tungsten / Obdurium

Tungsten is a physically plausible specialist engineering material, but its current Metallurgy use is too self-referential to justify another strategic geology layer.

Reserve potential future uses include:

- dense penetrators;
- high-temperature components;
- electrodes;
- cutting/drilling tooling;
- specialized propulsion/turbine parts.

Until such cross-system demand exists:

- suppress native Wolframite worldgen;
- do not make Tungsten progression-required.

Obdurium lacks a sufficiently distinct semantic or mechanical role and should not gate the Industrial Crucible. Replace such recipes with retained materials/processes during the recipe pass.

## Bronze

Do not import Tin progression merely because Metallurgy's stock Bronze recipe uses Copper + Tin.

Skyforge should preserve the already-retained CBC-style Tin-free Bronze route and provide an industrial foundry equivalent that outputs canonical CBC Bronze.

## Steel

Steel remains one material.

Metallurgy's foundry path may produce the canonical CBC Steel material/fluid while CBC's simpler workshop recipe remains available.

Exact workshop-vs-foundry efficiency remains deferred.

## Metalwork

Create: Metalwork is not provisionally selected as a required dependency.

Reasons:

- its interoperability model is technically strong;
- it correctly defers to existing molten-fluid owners in many cases;
- however, it also introduces broad processing parity, optional item/fluid registrations, and ore-yield chains that exceed Skyforge's narrow compatibility need;
- Skyforge's retained material set is small enough that a dedicated compatibility datapack may be simpler and more controllable.

Disposition:

**RESERVE / reference implementation**, not canonical dependency.

A later compatibility pass may either:
1. adopt Metalwork with aggressive configuration/recipe curation; or
2. implement a thin Skyforge bridge for CBC <-> Metallurgy molten materials.

Current preference: option 2.

## Balance doctrine

The foundry only earns its place if it creates new choices.

Desired tradeoff:

~~~text
WORKSHOP ROUTE
  low setup
  low throughput
  convenient for prototypes / small batches

INDUSTRIAL FOUNDRY
  high setup
  high throughput
  automation
  recycling
  bulk molten transport
  direct cannon-cast feed
  potentially improved efficiency
~~~

Do not make ordinary weapons inaccessible behind the full foundry unless later playtesting demonstrates that such gating improves rather than harms pacing.

## Deferred acceptance work

- exact CBC/Metallurgy fluid-tag interoperability;
- canonical fluid ownership for Cast Iron/Bronze/Steel/Nethersteel;
- removal/hiding of Obdurium progression;
- Wolframite worldgen suppression;
- Tungsten reserve governance;
- workshop vs industrial throughput and efficiency;
- ore-yield/recycling economics;
- direct molten feed into CBC Cannon Casts;
- inventory/JEI cleanup;
- performance and automation testing;
- multiplayer persistence;
- exact recipe normalization.

## Design principle

> Industrial complexity should buy scale, automation, efficiency, and logistical power. It should not exist merely to make an already involved manufacturing chain more obstinate.
