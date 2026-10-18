# CIV-0044 — Wild Blue Yonder Civilization Mod Stack and Alpha Aircraft Slice

**Status:** Provisional Alpha Freeze  
**Target:** Minecraft 1.21.1 / NeoForge  
**Date:** 2026-09-30

## Purpose
Freeze the provisional civilization stack for the Wild Blue Yonder alpha. Alpha targets a coherent procedural civilization vertical slice, not a complete civilization simulator. Skyforge owns composition and orchestration; mods primarily supply content and existing mechanics.

## Civilian civilization
### Population and roles
- **Vanilla Villagers** — baseline population, professions, schedules, trading, breeding, reputation, and village mechanics.
- **More Villagers** — additional professions, trades, and workstations.
- **Guard Villagers** — settlement defense and armed civilian presence.
- **Easy NPC** — special-purpose/authored actors such as captains, merchants, named NPCs, and dialogue roles.

### Civilian structure library
Provisionally retain all as structure/content sources, not authorities over Skyforge placement:
- **ChoiceTheorem's Overhauled Village (CTOV)** — deep settlement and biome/theme vocabulary.
- **Towns and Towers** — complementary village, outpost, ship, and individual structure vocabulary.
- **Create: Structures** — Create-flavored industrial/technological village structures.
- **Create Aeronautics Structures** — engineering, aviation, airship/balloon, and related structure vocabulary.

Skyforge determines where civilization belongs through semantic fields and structure suitability. Native structure injection/worldgen is subject to integration testing and may be suppressed, redirected, filtered, or adapted. CTOV and Towns and Towers are both retained provisionally; reduce only if joint integration proves unstable or redundant.

## Illager / marauding civilization
- **Vanilla Illagers** — hostile baseline.
- **Illager Invasion** — expanded hostile roster and raid/combat variety.
- **It Takes a Pillage Continuation** — additional Illagers and hostile sites such as camps/bastilles.
- **Illager Structures** — expanded hostile structure vocabulary, including content useful for pirate/aerial-world interpretation.

Civilian content should remain comparable in perceived worldbuilding sophistication: settlement, industry, aviation, defense, and traffic should answer hostile sites, fortifications, raids, and armed aircraft.

## Economy and logistics
- Retain the **existing Automated Logistics solution** as the provisional logistics/economic substrate.
- Alpha does not require a deep simulated economy.
- Logistics may establish coarse causal facts such as cargo at A needing transport to B.
- Skyforge civilization orchestration translates those facts into routes, shipments, and traffic.
- Off-screen shipments retain only enough state to remain coherent when observed.

## Alpha aircraft vertical slice
Civilian and hostile civilization share one aircraft infrastructure.

### Initial fleet scope
Target approximately four useful aircraft archetypes:
1. Civilian freight airship.
2. Civilian light/utility airplane, broadly Bellanca-like.
3. Illager armed raid/patrol aircraft.
4. A second armed Illager class for interception, site defense, raids, or another distinct combat role.

Use the existing **Skyforge aircraft compiler / asset pipeline** to create or assist in creating this small validated fleet. Full procedural aircraft engineering is not required for civilization alpha. Later expansion should add archetypes/assets to the common infrastructure rather than create faction-specific vehicle systems.

## NPC aircraft simulation and AI
### Core rule
**Abstract when unobserved; physically real when interaction matters.**

### Coarse/off-screen state
Outside relevant player simulation range, an aircraft may be a lightweight persistent record containing only necessary continuity state: identity/archetype, owner/faction, route progress, mission state, cargo, crew abstraction, and condition/damage as required. No full contraption, block simulation, pathfinding, or onboard NPC automation needs to run merely because the aircraft conceptually exists.

### Materialized state
When interaction becomes relevant, the record materializes as a **real Create/Sable-compatible physical aircraft**. Supported interactions may include interception, weapons damage, crashing, boarding, looting, component interaction, and potentially capture/theft. Avoid a separate fake-physical-aircraft ruleset if possible.

### AI authority
NPC aircraft intelligence is **Skyforge-owned and server-side/external to the craft**. The physical aircraft exposes controls/interfaces; Skyforge AI supplies high-level intent such as route following, approach, patrol, intercept, attack, flee, loiter, and docking/landing where supported.

NPC control is **not** player-facing CC:Tweaked/Lua avionics hardware installed aboard every NPC craft.

### Capture and progression
CC:Tweaked and player-programmable avionics remain a separate player-facing progression path. If a player captures or assumes control of an NPC aircraft, the **external NPC controller detaches or relinquishes authority**. Capture therefore does not automatically grant hidden high-end automation or bypass programmable-avionics progression. The physical aircraft may remain capturable; the NPC brain is not loot embedded in it.

## Skyforge-owned alpha integration
Required bespoke/integration work is deliberately narrow:
1. Civilization and structure suitability from semantic fields.
2. Civilian/hostile site assignment and procedural placement hooks.
3. Structure-library adapters and controls for imported mod structures.
4. Minimal faction/ownership identity.
5. Minimal route/activity generation.
6. Coarse persistent shipment and aircraft records.
7. Materialize/dematerialize lifecycle for traffic.
8. Shared aircraft archetype/template interface.
9. External NPC aircraft controller and player-control handoff.
10. Integration with logistics causes without requiring a full economy.

## Explicitly deferred beyond alpha
- Deep economic simulation.
- Detailed diplomacy/politics.
- Rich guild/faction strategy simulation.
- Procedural NPC social simulation.
- Large aircraft catalogs.
- Full procedural NPC aircraft engineering.
- NPC reliance on player-equivalent onboard Lua automation.
- Sophisticated off-screen combat unless later required.
- Extensive dynamic settlement growth/evolution.
- High-fidelity simulation of every civilian journey or transaction.

## Provisional freeze
**Civilian:** Vanilla Villagers + More Villagers + Guard Villagers + Easy NPC + CTOV + Towns and Towers + Create: Structures + Create Aeronautics Structures.

**Hostile:** Vanilla Illagers + Illager Invasion + It Takes a Pillage Continuation + Illager Structures.

**Economy/logistics:** existing Automated Logistics solution + minimal Skyforge orchestration.

**Aircraft:** existing Create/Sable physical stack + Skyforge aircraft compiler/assets + Skyforge external AI + coarse-state lifecycle.

**World composition:** Skyforge retains authority over semantic suitability, placement, composition, routes, persistence, and realization.

All inclusions remain provisional pending compatibility, performance, licensing/distribution, and integration testing before final release commitment.
