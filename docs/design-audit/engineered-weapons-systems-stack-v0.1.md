# Engineered Weapons Systems Stack v0.1

**Snapshot:** 2026-09-29  
**Status:** Provisional strong selection / integration contract. This document records the preferred engineered-weapons ecosystem for the Minecraft 1.21.1 NeoForge target. It is not a claim that all cross-mod interactions are already runtime-proven.

## Decision

Skyforge should treat vehicle combat as one engineered system rather than a collection of unrelated weapon mods.

The preferred direction is:

~~~text
Create
  -> Sable + Create Aeronautics
       -> physical vehicle, mass, motion, control, recoil response

Create: Big Cannons
  -> canonical conventional ballistics / artillery / ammunition industry

CBC Neo Warfare
  -> canonical medium cannon / rotary cannon / advanced CBC ammunition / ERA extension

Create: Radars
  -> preferred radar detection and track infrastructure

Create Aero Radars
  -> preferred radar-to-Aeronautics physical turret-actuation bridge

CC:Tweaked + Create: Avionics
  -> programmable aircraft computation, sensors, actuators, navigation, automation

Create: Fire Control
  -> preferred advanced stabilization / optics / EO / thermal / guided-weapon fire-control layer

Mianbao's NewModernWarfare
  -> preferred source of aircraft stores, guided-munition hardware, pylons/racks,
     APS, countermeasures, CIWS/anti-missile systems, and selected specialist military assets

CBC Terminal Ballistics
  -> leading penetration / armor-interaction extension if runtime testing remains favorable

Create Crafts & Additions + Sable compatibility
  -> aircraft electrical generation / storage / motors / wiring where applicable

Drive By Wire + Tweaked Controllers
  -> physical/manual cockpit-control infrastructure
~~~

The stack is intentionally modular. A component is retained because it contributes a distinct physical,
industrial, sensing, control, defensive, or ordnance role—not merely because it adds more weapons.

## Governing principles

1. **Sable owns vehicle rigid-body truth.** Weapon recoil, impacts, mass effects, and vehicle motion should
   resolve through the existing Sable/Aeronautics physics substrate rather than a parallel vehicle simulation.
2. **CBC owns conventional ballistic weapon truth.** Prefer CBC/CBC-family weapons for guns, cannon manufacture,
   propellant, shells, and ballistic weapon construction.
3. **Neo Warfare extends CBC rather than replacing it.** Its medium cannons, rotary cannons, specialized rounds,
   launchers, armor, and ERA are favored over duplicate non-CBC direct-fire systems where capability is equivalent.
4. **Create: Radars is the preferred radar/track infrastructure.** Do not casually allow multiple independent
   radar truths. Fire Control radar functionality should be retained only where it adds a distinct fire-control or
   seeker role or can consume/defer to the selected radar authority.
5. **Create: Avionics + CC:Tweaked own general-purpose programmable aircraft I/O.** Fire Control should not replace
   the general aircraft-computing layer.
6. **Fire Control owns advanced weapon-system electronics.** Stabilization, EO/thermal observation, target locking,
   remote weapon stations, and guided-weapon control are the main reasons to retain it.
7. **Mianbao is a hardware/content library, not a parallel industrial economy.** Retain useful ordnance, racks,
   defensive systems, countermeasures, CIWS, drones, and specialist equipment while normalizing recipes and
   suppressing redundant direct-fire/radar hardware where better Create/CBC implementations exist.
8. **One economy.** All retained mods consume the existing Skyforge/Create/CBC/CC&A material and process graph.
   Duplicate mod-local materials or arbitrary gem-tier progression should not survive merely because upstream
   recipes use them.
9. **Cross-mod compatibility is a first-class acceptance gate.** A nominally installed item does not count as
   integrated until sensing, guidance, interception, penetration, recoil, save/reload, and Sable-sublevel behavior
   have been tested where relevant.
10. **Prefer data/config/tag integration before bespoke Java.** Use common tags, recipe overrides, datapacks,
    configuration, and existing compatibility addons before writing Skyforge behavioral adapters.

## Preferred component roles

| Capability | Preferred owner / source | Current disposition |
|---|---|---|
| Vehicle physics | Sable | Core |
| Aircraft assembly / flight systems | Create Aeronautics | Core |
| Large cannon / autocannon ballistics | Create: Big Cannons | Retained |
| Medium cannon | CBC Neo Warfare | Strong provisional |
| Rotary cannon | CBC Neo Warfare | Strong provisional |
| Cannon metallurgy / ammunition manufacturing | CBC + Neo Warfare | Strong provisional |
| HEAT / AP / APDS / APFSDS / APHE / specialist gun ammunition | CBC / Neo Warfare | Strong provisional |
| Vehicle armor / ERA | Neo Warfare first | Strong provisional |
| Penetration / terminal ballistics | CBC Terminal Ballistics | Strong prototype |
| Radar detection / tracks | Create: Radars | Strong provisional |
| Radar-directed physical turret actuation | Create Aero Radars | Strong provisional |
| General programmable avionics | Create: Avionics + CC:Tweaked | Accepted baseline |
| Manual cockpit input | Tweaked Controllers | Strong provisional |
| Physical onboard control cabling | Drive By Wire | Strong provisional |
| Advanced stabilization / EO / thermal / fire control | Create: Fire Control | Strong provisional |
| Aircraft bombs / racks / pylons | Mianbao | Strong provisional |
| Guided missile hardware / launchers | Mianbao + Fire Control | Strong provisional |
| Unguided rockets | Compare Neo Warfare vs Mianbao by role | Open |
| Torpedoes | Compare Neo Warfare vs Mianbao by role | Open |
| APS | Mianbao | Strong provisional; cross-mod proof required |
| Smoke / countermeasures | Mianbao | Strong provisional; cross-mod proof required |
| CIWS / anti-missile systems | Mianbao / Fire Control ecosystem | Strong provisional |
| Drones / UCAV content | Mianbao assets under Skyforge faction/population authority | Conditional strong |
| Aircraft electrical systems | Create Crafts & Additions + Sable compat | Strong provisional |
| Recoil and impact response | CBC -> Sable | Native direction; acceptance required |

## Content selection rules

### CBC and Neo Warfare

Retain broadly. These mods already reinforce the preferred industrial grammar:

~~~text
retained metals
  -> heat / alloy / casting / machining
  -> barrel / breech / recoil / launcher components
  -> cartridges / shells / propellant
  -> physical weapon
  -> recoil / ballistic consequence
~~~

Prefer CBC/Neo Warfare over Mianbao where both provide the same direct-fire weapon class.

### Mianbao

Retain primarily for capability gaps:

- aircraft bombs and guided bombs;
- missile bodies and launch hardware;
- pylons / racks / rocket pods;
- APS;
- smoke / countermeasures;
- CIWS and anti-missile hardware;
- selected torpedo systems;
- selected drones / UCAV content;
- cockpit / military support assets where they fill a real role.

Treat the following as likely duplicate-suppression candidates unless testing finds a distinct role:

- generic direct-fire guns duplicated by CBC / Neo Warfare;
- generic rotary cannon duplicated by Neo Warfare;
- generic ERA duplicated by Neo Warfare;
- radar/control blocks duplicated by Create: Radars, Avionics, or Fire Control.

Skyforge owns spawn/faction semantics for Mianbao drones and autonomous threats; installing the content does not
authorize unrestricted ambient population.

### Fire Control

Retain for advanced control capabilities, not merely because it bundles more weapons.

Primary value:

- dual-axis stabilization;
- gun sights / observation;
- thermal imaging;
- electro-optical tracking;
- target locking;
- guided-weapon control;
- connected displays / remote control;
- IFF / transponder behavior where supported;
- integration with Aeronautics/Sable and CBC-family weapons.

Fire Control may coexist with Create: Radars only if the final integration preserves a clear sensor authority.
Where Fire Control has a duplicate radar path, prefer consuming or deferring to shared tracks over maintaining two
independent environmental truths.

## Recipe and progression doctrine

The combat stack should reinforce the canonical industrial graph rather than create separate mod economies.

Candidate normalization language:

~~~text
GUN STRUCTURE / MOUNTS
  -> Wrought Iron / Cast Iron / Bronze / Steel / Nethersteel
  -> Create bearings / shafts / mechanisms

MECHANICAL TRAVERSE
  -> Create kinetics + Brass + Steel

ELECTRIC TRAVERSE / SERVOS
  -> CC&A motors + Copper / Gold conductors + capacitors

BASIC AVIONICS
  -> Brass + Redstone + Quartz + Electron Tubes + CC components

RADAR
  -> Precision Mechanisms + electronics + electrical power + conductors

EO / THERMAL
  -> optics + electronics + advanced power / conductor requirements

FIRE-CONTROL COMPUTER
  -> CC / Avionics components + precision mechanisms + electrical hardware

UNGUIDED ROCKET
  -> structural metal + propellant + physical warhead

GUIDED MISSILE
  -> rocket / motor + warhead + seeker + control electronics + power

COUNTERMEASURES / APS
  -> explosives / interceptors + sensors + control electronics

ADVANCED AMMUNITION
  -> CBC shell manufacturing + material / penetrator / fuse / propellant specialization
~~~

Do not use arbitrary Diamond/Netherite "Mk I -> Mk V" upgrades where a process/material/capability progression can
express the technology more coherently.

Sophistication should be expensive because it adds additional industries and components, not merely because recipes
multiply raw material counts.

## Intended technological progression

A provisional capability progression is:

~~~text
C0 MANUAL BALLISTICS
  fixed/manual CBC weapons
  pilot aims aircraft or manual mount

C1 MECHANIZED WEAPONS
  Neo Warfare medium/rotary guns
  physical launchers / racks
  improved ammunition and armor

C2 INSTRUMENTED VEHICLE
  Avionics sensors
  CC computation
  manual cockpit controllers / onboard cabling

C3 RADAR-DIRECTED
  Create: Radars tracks
  Aero Radars physical turret actuation
  radar-informed interception / aiming

C4 INTEGRATED FIRE CONTROL
  stabilization
  EO / thermal
  remote weapon stations
  target identification / advanced tracking

C5 GUIDED WEAPONS / ACTIVE DEFENSE
  guided missiles / bombs
  countermeasures
  APS
  CIWS / anti-missile systems
~~~

Later capability must improve how earlier hardware is used rather than automatically invalidating it. A rotary cannon
remains a rotary cannon after advanced fire control is unlocked; the weapon system becomes better because sensing,
stabilization, computation, and control improve.

## Compatibility work backlog

### WEAP-COMP-1 — Cross-mod projectile / defense matrix

Build a deterministic matrix covering at minimum:

- CBC AP / HE / HEAT where available;
- Neo Warfare APDS / APFSDS / APHE / HEAT / rockets;
- Mianbao guided missiles / rockets / bombs;
- Fire Control-controlled missiles;
- representative torpedoes;
- representative countermeasure / interceptor systems.

Against:

- Neo Warfare ERA / composite armor;
- Mianbao ERA;
- Mianbao APS variants;
- Mianbao smoke / countermeasures;
- Mianbao CIWS / anti-missile systems;
- Fire Control / compatible anti-missile systems.

Record whether each interaction is native, bridged, unsupported, or behaviorally incorrect.

### WEAP-COMP-2 — Radar / guidance authority

Prove which systems can consume which tracks and locks:

- Create: Radars -> Aero Radars;
- Create: Radars -> CC:Tweaked;
- Create: Radars -> Fire Control where possible;
- Fire Control radar / EO -> Mianbao guided weapons;
- IFF / transponder propagation;
- countermeasure effects on radar, EO, IR, laser, and command guidance.

Do not accept two independent radar truths without an explicit reason.

### WEAP-COMP-3 — CBC / Sable recoil and moving fire

Representative test craft:

- light fixed autocannon aircraft;
- medium-cannon aircraft;
- rotary-cannon aircraft;
- large airship artillery mount.

Measure:

- linear recoil;
- pitch / yaw / roll impulse;
- velocity inheritance;
- firing while maneuvering;
- repeated-fire stability;
- projectile impact force on Sable targets;
- save/reload and assembly/disassembly behavior.

Tune upstream/configurable recoil first; do not add bespoke aircraft weapon penalties before observing real behavior.

### WEAP-COMP-4 — Terminal Ballistics / armor interoperability

Test:

- CBC ammunition;
- Neo Warfare ammunition;
- Mianbao direct-fire projectiles if retained;
- Sable-sublevel armor;
- Copycats+ geometry/material behavior;
- ERA and composite layers.

Determine the canonical armor/penetration vocabulary and suppress redundant systems.

### WEAP-COMP-5 — Launcher / missile ownership

Compare Neo Warfare and Mianbao for:

- unguided rockets;
- guided missiles;
- multiblock launchers;
- pylons/racks;
- torpedoes;
- warhead integration.

Prefer Neo Warfare when CBC-style physical construction is superior; prefer Mianbao where Fire Control guidance,
aircraft stores, or defensive-system integration is materially stronger. Preserve both only where they occupy distinct
roles or technology levels.

### WEAP-COMP-6 — Recipe and item normalization

Extract every survival-obtainable item and recipe from:

- CBC;
- CBC Neo Warfare;
- Create: Radars;
- Create Aero Radars;
- Create: Fire Control;
- Mianbao's NewModernWarfare;
- CBC Terminal Ballistics;
- Create: Avionics;
- CC&A / Sable compat;
- Drive By Wire;
- Tweaked Controllers;
- any retained weapon compatibility addon.

For every duplicate material or functional component, assign:

~~~text
KEEP STOCK
NORMALIZE RECIPE
COMMON-TAG BRIDGE
HIDE / DISABLE
DEV-ONLY
REJECT
~~~

Add the resulting decisions to the unified industrial production graph and exact recipe collision manifest.

### WEAP-COMP-7 — Mianbao content governance

Classify every Mianbao item/entity by role:

~~~text
PRIMARY
  fills a real Skyforge capability gap

ALTERNATE
  distinct lower/higher-tech or doctrinal option

DEPENDENCY-ONLY
  required internally but not intended as normal progression

DUPLICATE
  replaced by CBC / Neo Warfare / Radars / Avionics / Fire Control

DISABLED
  conflicts with Skyforge progression or world authority
~~~

Explicitly govern drone / autonomous-entity spawning through Skyforge faction/threat semantics.

### WEAP-COMP-8 — Aircraft electrical/control closure

Prove on assembled Sable craft:

- CC&A electrical generation/storage/distribution;
- Sable-compatible wiring;
- Drive By Wire;
- Tweaked Controllers;
- Avionics peripherals;
- Fire Control electronics;
- radar and powered turret loads.

Ensure sophisticated combat craft require coherent onboard power/control infrastructure rather than disconnected magic blocks.

### WEAP-COMP-9 — Persistence and multiplayer

For the integrated vehicle:

- assemble / disassemble;
- save / reload;
- chunk unload / reload;
- blueprint / schematic where supported;
- server/client resync;
- gun/radar/fire-control binding persistence;
- ammunition state;
- IFF / channel state;
- remote-control ownership;
- multiple simultaneous targets / vehicles.

### WEAP-COMP-10 — Performance budget

Characterize:

- radar scan cost;
- projectile density;
- rotary-cannon sustained fire;
- missile swarms;
- CIWS interception;
- APS;
- multiple moving Sable craft;
- CC computers and fire-control loops.

Do not accept a combat capability whose ordinary use destroys the world-generation / simulation frame budget.

## Acceptance boundary

This document authorizes the stack as the **preferred strong prototype**, not as fully accepted runtime capability.

Promotion to a locked production stack requires:

1. exact 1.21.1 NeoForge version and license/distribution revalidation;
2. loader/dependency closure;
3. recipe/item inventory;
4. cross-mod defense matrix;
5. Sable moving-vehicle acceptance;
6. radar/fire-control authority resolution;
7. recipe/material normalization;
8. persistence/multiplayer proof;
9. performance characterization;
10. representative human play for handling, readability, and fun after machine correctness gates pass.

## Current disposition

~~~text
KEEP / CORE
  Create
  Sable
  Create Aeronautics
  Create: Big Cannons
  CC:Tweaked
  Create: Avionics

STRONG PROVISIONAL IMPORT
  CBC Neo Warfare
  Create: Radars
  Create Aero Radars
  Create: Fire Control
  Mianbao's NewModernWarfare
  CBC Terminal Ballistics
  Drive By Wire
  Tweaked Controllers
  Create Crafts & Additions Sable compatibility

DEPENDENCY / SUPPORT
  GeckoLib
  Copycats+ where required by retained ballistic integration
  other exact upstream libraries as established by loader closure

SELECTIVE / SUBJECT TO NORMALIZATION
  Mianbao direct-fire guns
  Mianbao radar/control duplicates
  duplicate ERA
  duplicate rocket / torpedo families
  optional CBC ammunition expansions
  other Fire Control optional integrations

NOT YET A DECISION
  exact guided-missile hardware owner by class
  exact unguided-rocket owner
  exact torpedo owner
  exact APS interoperability policy
  exact radar-to-Fire-Control bridge
  final countermeasure model
  final combat-tech recipe costs
~~~

## Design principle

> A Skyforge combat vehicle should be engineered as a machine: physical airframe, propulsion, power,
> sensing, computation, control, weapon mounting, ammunition, recoil management, protection, and logistics
> should reinforce one another. Installing a mod does not grant it an independent progression tree or semantic
> authority.
