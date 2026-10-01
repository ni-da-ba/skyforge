# Representative Structure Realization Matrix v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design decision matrix. Not yet an accepted ADR.

This document exercises the generic [Structure Realization Contract](structure-realization-contract-v0.1.md) against representative vanilla, modded, and Skyforge-specific structures.

## Three independent authorities

A structure integration must explicitly identify three authorities.

### 1. Occurrence authority

Who decides that the structure should exist?

~~~text
MINECRAFT_TOPOLOGY
SKYFORGE_SEMANTICS
MIXED_REGIONAL
~~~

- **MINECRAFT_TOPOLOGY:** preserve a Minecraft progression/distribution contract, e.g. Java Stronghold ring topology.
- **SKYFORGE_SEMANTICS:** Skyforge decides from province/cluster/island history, faction, geology, infrastructure, or destination rhythm.
- **MIXED_REGIONAL:** Minecraft/mod data supplies approximate frequency/eligibility, while Skyforge selects a compatible site inside the allowed region.

### 2. Site authority

Who chooses or creates the physical terrain that hosts it?

~~~text
EXISTING_SITE
RELOCATED_WITHIN_REGION
STRUCTURE_SEEDED_TERRAIN
DETACHED_RESERVATION
~~~

### 3. Realization authority

Who builds the concrete blocks/entities?

Normally:

~~~text
Minecraft / mod registry structure
~~~

Skyforge should prefer to keep native jigsaw pieces, processors, loot, spawners, trial mechanics, and persistence rather than copying structures into bespoke generators.

This separation is central:

> Minecraft may own the asset and even the progression topology without owning Skyforge world composition.

## Location-constraint hierarchy

Different structures have different fidelity requirements.

~~~text
FREE
REGION_BOUND
CLUSTER_BOUND
ISLAND_BOUND
COORDINATE_ANCHORED
TOPOLOGY_ANCHORED
~~~

A Stronghold should be much less relocatable than a Trial Chamber. An ordinary ruin can be freely placed where history/surface geometry permits.

## Exact-volume structure frame requirement

The accepted post-isolation island pipeline already supplies exact-volume identity to biome population and underground features. Structure integration should gain an analogous operation scope.

Within a selected structure operation:

- height/terrain queries must observe exactly the selected Skyforge volume;
- vertically stacked islands must not compete through global highest-surface heightmaps;
- BASE_WORLD terrain must not become accidental support;
- structure processors that perform terrain matching must resolve against the intended owner;
- native/modded registry identity, RNG, jigsaw composition, loot, and processors should remain intact whenever safe.

For underground structures, absolute Minecraft Y assumptions should be translated into **island-local semantic depth** where the structure's design permits it.

## Representative matrix

| Structure | Occurrence | Preferred mode | Site policy | Population | Failure policy | Principal new work |
|---|---|---|---|---|---|---|
| Small ruin / guidepost | Skyforge semantics | Surface-supported | Existing site only | None/ambient | Skip/fallback asset | exact-volume structure population |
| Village / town | Skyforge semantics | Settlement/network | Existing suitable island; structure-seed only for intentional settlement hubs | Civilian + structure/faction | Fallback settlement scale/site | settlement planner; owner-scoped roads/terrain matching |
| Illager fort | Skyforge semantics | Surface-supported / settlement-network | Existing faction island; major strongholds may seed terrain | Faction | Smaller outpost or alternate site | faction planner; exact-volume structure scope |
| YUNG dungeon | Mixed regional / Skyforge semantics | Subsurface-embedded | Compatible island interior | Structure | Skip/alternate dungeon unless required | 3-D occupancy, excavation/reservation, structure-spawn budgeting |
| Mineshaft | Skyforge semantics | Subsurface-embedded | Ore/history-derived island interior | Spawner + ambient cave | Skip if no coherent mine | 3-D occupancy, cave/geology reconciliation |
| Trial Chamber | Mixed regional | Subsurface-embedded | Relocate within occurrence region to suitable island; seed terrain when necessary to preserve target frequency | Trial | Regional relocation -> seed island -> fail only under explicit world policy | local-depth transform; occupancy reservation |
| Stronghold | Minecraft topology | Subsurface-embedded | Reuse compatible island at intended topology point; otherwise seed terrain around Stronghold intent | Structure/spawner | Must not silently fail | topology reservation; large interior envelope |
| Ancient City, buried | Skyforge semantics / regional anomaly | Subsurface-embedded | Very large Deep-Dark-compatible island | Structure/Warden | Alternate site or seed massive island | cavern reservation; local-depth Deep Dark |
| Ancient City, exposed | Skyforge exceptional intent | Structure-seeded terrain | Purpose-authored massive island/basin | Structure/Warden | Omit if exceptional composition cannot be satisfied | exposed-city terrain archetype |
| Cliff dock / hanging infrastructure | Skyforge semantics | Cliff/underside-attached | Existing compatible cliff | Civilian/faction | Skip/alternate anchor | oriented attachment frame |
| Static airship wreck | Skyforge semantics | Detached or attached | Open-air reservation or cliff attachment | Salvage/ecology | Skip/alternate wreck pose | detached collision/clearance semantics |
| Ocean Monument | Mixed regional / ocean semantics | Submerged water-volume | Existing ocean-island water mass; otherwise ocean structure-seeded environment | Structure/guardian | Relocate regionally or seed water volume | authored ocean volume + submerged structure frame |

## 1. Small ruin / guidepost

### Purpose

Low-intensity historical texture and navigation value.

Examples:

- guidepost;
- cache;
- ruined tower;
- old camp;
- small shrine;
- isolated utility building.

### Policy

~~~text
occurrence = SKYFORGE_SEMANTICS
importance = OPTIONAL
mode = SURFACE_SUPPORTED
site = EXISTING_SITE_ONLY
~~~

Inputs may include:

~~~text
historicTraffic
abandonment
routeImportance
surfaceSuitability
settlementHistory
~~~

Do not create a new island merely because an optional ruin rolled.

If one asset fails support admission, use Minecraft/mod weighted fallback or another role-compatible asset.

### Play

Mostly:

~~~text
see -> inspect -> minor shelter / salvage / clue / navigation
~~~

Ordinary landmarks should greatly outnumber major dungeons.

## 2. Village / town

### Purpose

Represent a coherent settlement, not a bag of independently spawned houses.

### Policy

~~~text
occurrence = SKYFORGE_SEMANTICS
importance = PREFERRED
mode = SETTLEMENT_NETWORK
~~~

Required island context may include:

- adequate usable surface;
- water;
- agriculture/import capacity;
- landing/docking access;
- settlement/population intensity;
- regional traffic;
- defensibility where appropriate.

### Critical technical issue

Village jigsaw pieces, roads, terrain-matching processors, and heightmap queries must be scoped to one exact island owner.

The repository has already observed the failure mode where lower native settlement terrain-matching projected path/plank behavior onto an unrelated upper island. The future settlement scope must make that class of cross-volume projection impossible.

### Structure-seeded settlement islands

Allowed, but only when the world plan explicitly wants a settlement hub.

Do not create an island for every village candidate.

A provincial capital, major agricultural settlement, or route hub may legitimately influence island morphology before realization.

## 3. Illager fort

### Purpose

Make hostile civilization geographically coherent.

### Policy

~~~text
occurrence = SKYFORGE_SEMANTICS
context = ILLAGER_CONTROL
mode = SURFACE_SUPPORTED or SETTLEMENT_NETWORK
~~~

Ordinary hierarchy:

~~~text
watch post
-> camp
-> fort
-> regional stronghold / industrial complex
~~~

Skyforge should select from Illager Structures, vanilla, Friends & Foes, and It Takes a Pillage assets according to the faction site's role rather than enabling all generators independently.

### Terrain seeding

Small forts do not justify new islands.

A rare regional illager capital, major air/naval base, or industrial stronghold may seed or strongly influence an island if the province plan already requires the faction center.

## 4. YUNG's Better Dungeon

### Purpose

Structured ordinary underground combat.

YUNG's Better Dungeons 1.21.1 provides Catacombs, Fortresses of the Undead, Spider Caves, and redesigned small dungeons.

### Policy

~~~text
occurrence = MIXED_REGIONAL or SKYFORGE_SEMANTICS
mode = SUBSURFACE_EMBEDDED
importance = OPTIONAL/PREFERRED
~~~

An island should satisfy:

- sufficient interior volume;
- compatible cave topology;
- appropriate threat/history context;
- safe separation from island exterior except intended access breaches.

### Access

Preferred patterns:

~~~text
surface ruin -> shaft -> dungeon
cave -> dungeon breach
cliff entrance -> dungeon
mine -> dungeon intersection
~~~

Do not automatically expose every dungeon.

### Population

Some current YUNG dungeon structure definitions use structure-specific monster spawn overrides in addition to embedded spawners.

All such spawning is **STRUCTURE provenance**, not ambient cave population.

The threat governor must be allowed to clamp/replace structure spawn overrides if their vanilla pack sizes produce excessive density in Skyforge, while preserving explicit spawner blocks and intended encounter identity.

## 5. Mineshaft

### Purpose

Historical extraction that follows actual geology.

### Policy

~~~text
occurrence = SKYFORGE_SEMANTICS
mode = SUBSURFACE_EMBEDDED
importance = OPTIONAL
~~~

Desired causality:

~~~text
ore/mineral value
+ historic extraction pressure
+ settlement/industry history
-> mine/mineshaft
~~~

### Geometry

A mine should reserve a 3-D occupancy region before final cave realization.

Interaction rules:

- authored caves may intersect deliberately;
- critical mine corridors should not be obliterated by later authored caves;
- mine passages should not regularly break into the void by accident;
- occasional intentional cliff adits/failed tunnels are desirable;
- surface tailings, hoists, shafts, carts, or ruined processing structures can advertise the mine.

## 6. Trial Chamber

### Verified vanilla mechanics relevant to Skyforge

Trial Chambers are underground copper/tuff structures with procedurally generated layouts, Trial Spawners, Vaults, and Ominous variants. Natural mob spawning does not occur inside them; their combat population comes from Trial Spawners.

This is highly compatible with Skyforge's provenance model.

### Policy

~~~text
occurrence = MIXED_REGIONAL
importance = PREFERRED/REQUIRED according to final frequency policy
mode = SUBSURFACE_EMBEDDED
location = REGION_BOUND
~~~

Unlike Strongholds, there is little gameplay reason to preserve every exact vanilla X/Z coordinate.

Preferred algorithm:

~~~text
vanilla-compatible occurrence region
        |
        v
search suitable Skyforge islands in region
        |
        +--> compatible island -> embed
        |
        +--> no compatible island -> structure-seed a suitable thick island
                                  only when required to preserve desired frequency
~~~

This avoids allowing the relatively common Trial Chamber distribution to dictate the entire island layout.

### Depth

Map the vanilla concept of a medium-deep/deepslate structure into island-local semantic depth.

~~~text
deep enough to feel buried
not so deep that thin islands become invalid
~~~

### Cave interaction

Allow limited accidental-looking cave exposure, but reserve critical rooms/spawner/vault envelopes from destructive authored carving.

## 7. Stronghold

### Verified vanilla progression contract

In Java Edition, Strongholds occupy concentric rings around the world origin and are located through Eyes of Ender. They contain the End portal required for normal End progression.

### Policy

~~~text
occurrence = MINECRAFT_TOPOLOGY
importance = PROGRESSION_CRITICAL
mode = SUBSURFACE_EMBEDDED
location = TOPOLOGY_ANCHORED
fallback = STRUCTURE_SEEDED_TERRAIN
~~~

### Site rule

Do not simply move the Stronghold to whichever island is convenient.

At each intended topology location:

1. determine whether an existing/planned island can contain the required envelope;
2. if yes, reserve its interior;
3. if no, author a suitable island/cluster component around the Stronghold intent.

The island should be geologically substantial enough to contain a real Stronghold, not a thin shell.

### Surface expression

Most Stronghold-bearing islands should remain visually ordinary.

Possible subtle evidence:

- old masonry fragments;
- sinkholes;
- unusual cave geometry;
- rare End-related anomalies.

Do not make all Stronghold islands visually identical.

## 8. Ancient City — buried

### Verified vanilla mechanics relevant to Skyforge

Ancient Cities are enormous Deep Dark structures, around 220 blocks across in vanilla. Natural mobs do not spawn in Ancient Cities; danger is driven by sculk/shrieker/Warden mechanics.

### Policy

~~~text
occurrence = SKYFORGE_SEMANTICS / REGIONAL_ANOMALY
importance = EXCEPTIONAL
mode = SUBSURFACE_EMBEDDED
~~~

Requirements:

- very large island;
- very large reserved cavern;
- Deep Dark semantic context;
- strong separation from ordinary cave ecology;
- Warden/sculk threat remains structure/anomaly-owned.

### Local depth

Do not require world Y=-51 in a floating-island world.

The relevant concept is:

~~~text
deep geological interior
+ large cavern
+ low surface connectivity
+ Deep Dark semantics
~~~

## 9. Ancient City — exposed

### Policy

~~~text
occurrence = SKYFORGE_EXCEPTIONAL
importance = EXCEPTIONAL
mode = STRUCTURE_SEEDED_TERRAIN
exposure = EXPOSED/PARTIALLY_EXPOSED
~~~

This intentionally preserves the successful prior design experiment of an Ancient City carried by the top of a huge island.

### Preferred composition

Avoid a generic flat plate.

Better forms:

- huge eroded basin;
- city filling a broad collapsed caldera;
- ruined city partly swallowed by surrounding rock;
- broken city extending toward cliffs.

The structure should be visible from long distance and become a major Distant Horizons destination.

### Gameplay

Open exposure changes Warden gameplay and makes aircraft approach meaningful.

Retain enough walls, depressions, sculk fields, interior ruins, and constrained landing choices that the city does not become a trivial open-air shooting gallery.

This should be much rarer than buried Ancient Cities.

## 10. Cliff dock / hanging infrastructure

### Policy

~~~text
occurrence = SKYFORGE_SEMANTICS
mode = CLIFF_UNDERSIDE_ATTACHED
importance = OPTIONAL/PREFERRED
~~~

Required physical frame:

- attachment surface;
- local outward normal/orientation;
- sufficient rock contact;
- open clearance volume;
- approach corridor.

Potential content:

- mooring dock;
- cargo crane;
- hanging warehouse;
- lift station;
- mine entrance;
- cliff village extension.

This mode should not flatten the cliff into a horizontal structure site.

## 11. Static airship wreck

### Policy

~~~text
occurrence = SKYFORGE_SEMANTICS
mode = DETACHED or CLIFF_ATTACHED
importance = OPTIONAL
~~~

Inputs:

~~~text
historicTraffic
stormExposure
routeDifficulty
abandonment
conflictPressure
predatorPressure
~~~

### Initial realization recommendation

World-generated wrecks should normally be **static Minecraft structures made from Create/Aeronautics-compatible blocks**, not active Sable physics objects at chunk generation.

This avoids loading arbitrary persistent physics contraptions as worldgen.

Variants:

- free-floating derelict;
- cliff impact;
- underside snag;
- crashed plateau vessel.

The player may salvage/rebuild rather than simply reactivate a pre-generated physics entity.

## 12. Ocean Monument

### Policy

~~~text
occurrence = MIXED_REGIONAL / OCEAN_SEMANTICS
mode = SUBMERGED_WATER_VOLUME
importance = PREFERRED
~~~

A monument needs an actual authored marine environment:

- sufficient water footprint;
- sufficient water depth above/around it;
- coherent sea surface;
- seafloor/support;
- room for guardian navigation.

Do not place a monument into a small lake merely because the biome tag says ocean.

### Structure-seeded ocean environment

If the desired ocean-region structure distribution requires a Monument and no suitable water island exists, Skyforge may author an ocean island/water mass around the Monument intent.

Guardian behavior and future guardian farms are STRUCTURE provenance and must not be suppressed by ambient hostile budgets.

## Realization-order decision

The representative cases imply this high-level order:

~~~text
WORLD / PROVINCE / CLUSTER PLAN
        |
        +--> progression topology intents
        +--> civilization/faction intents
        +--> exceptional destination intents
        |
        v
STRUCTURE INTENT / RESERVATION PASS
        |
        +--> required structure-seeded terrain requests
        +--> settlement surface requirements
        +--> underground occupancy reservations
        +--> cliff/detached reservations
        |
        v
ISLAND / CAVE / HYDROLOGY / GEOLOGY AUTHORSHIP
        |
        v
EXACT-VOLUME STRUCTURE REALIZATION
        |
        v
STRUCTURE POPULATION / LOOT / ENCOUNTER SYSTEMS
~~~

Optional ordinary structures can still be selected later from already-authored sites, but required structures must be visible early enough to influence geometry.

## New implementation requirements derived by this pass

1. **Exact-volume structure operation scope** analogous to exact-volume population.
2. **3-D structure occupancy reservations** for underground structures.
3. **Island-local structure depth transform** for structures whose vanilla distribution assumes absolute Overworld depth.
4. **Owner-scoped terrain-matching/heightmap queries** for villages/jigsaw roads and similar processors.
5. **Structure population provenance/budget** for structure spawn overrides.
6. **Oriented cliff/underside attachment frame.**
7. **Detached structure clearance/collision reservation.**
8. **Structure-seeded terrain request** available to world/authorship planning.
9. **Progression topology reservation** for Strongholds and other coordinate-sensitive content.
10. **Water-volume structure envelope** for Monument/ocean content.

## Acceptance philosophy

The implementation should preserve native/modded realization wherever possible while making the following statement true:

> A structure appears because Skyforge has a world-level reason and a physically coherent site for it; Minecraft or the supplying mod remains responsible for realizing the actual structure and its intended gameplay mechanics.
