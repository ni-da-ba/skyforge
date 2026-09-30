# Structure Content Gap and Reuse Audit v0.1

**Snapshot:** 2026-09-30  
**Status:** Provisional structure-content closure. This document preserves the gap-to-asset decisions before integrated runtime/visual testing.

## Objective

Minimize bespoke Skyforge structure content without surrendering semantic placement authority.

The audit asks:

> Which remaining Skyforge structure roles can be satisfied by existing Minecraft 1.21.1 NeoForge content, and which are actually geometry/placement problems rather than missing assets?

Default order remains:

1. reuse existing dependency content;
2. add a narrow existing dependency only when it closes a real role gap;
3. compose existing blocks/assets under a small Skyforge site plan;
4. author new bespoke blocks/content only when all of the above fail.

## Existing broad structure vocabulary

Already covered well enough for V1:

- active civilian cores -> Towns & Towers;
- hostile civilization -> Illager Structures;
- industrial / technical history -> Create: Structures Arise;
- aviation history / wrecks / airships / balloons -> Creat Aeronautics Structures (MIT project);
- dynamic physical aircraft / flyovers / structure-linked patrols -> Create Aeronautics Discovery;
- mines -> YUNG's Better Mineshafts;
- dungeons -> YUNG's Better Dungeons;
- mythic roosts/lairs/ruins -> selected Ice & Fire CE content;
- ordinary frontier/history texture -> Structory and/or Explorify pending final A/B;
- exceptional megastructures -> surgical When Dungeons Arise use only;
- generic vanilla-style structure variants -> reserve libraries only when a real role remains.

Large installed catalogues do not imply dense realization. Skyforge owns admission, density, state, provenance, and site semantics.

## High-value retained discoveries

### Create: Structures Arise — strong provisional keep

Current 1.21.1 NeoForge content includes data-driven structures such as:

- createfluidtank;
- createcontainer;
- createcushercrane;
- minetrain;
- createlosttrainstation;
- windmill;
- pillagersteampunkairship;
- createminiskyvillage;
- createairdrop;
- industrial/ruin/tower/castle assets.

Leading Skyforge reinterpretations:

~~~text
createfluidtank
  -> fuel depot
  -> petroleum storage
  -> airfield tank farm
  -> industrial fluid site

createcushercrane
  -> quarry loading site
  -> mine bulk-transfer site
  -> industrial cargo node

createcontainer
  -> freight staging
  -> cargo cache
  -> warehouse adjunct

minetrain
  -> historical extraction
  -> mine logistics

createlosttrainstation
  -> abandoned route network
  -> industrial history

windmill
  -> agricultural processing

pillagersteampunkairship
  -> faction wreck / parked craft / asset reference

createminiskyvillage
  -> exceptional engineered enclave only if useful
~~~

The mod exposes per-structure enablement, weights, spacing, dimension filters, and biome filters, so it is unusually amenable to Skyforge governance.

### Creat Aeronautics Structures — strong provisional keep

Use the MIT-licensed 1.21.1 NeoForge project as aviation/history vocabulary:

- ruined engineering houses;
- abandoned boats;
- floating airships;
- hot-air balloons;
- robber/faction balloons;
- engineer-oriented settlement texture.

Primary semantic roles:

~~~text
ENGINEERING_HOMESTEAD
ABANDONED_WORKSHOP
AVIATION_WRECK
DERELICT_AIRSHIP
BALLOON_SITE
EARLY_AVIATION_EVIDENCE
~~~

Do not confuse this project with similarly named ARR Aeronautics structure projects.

### Create Aeronautics Discovery — strong integration prototype

Discovery is more than a content mod. Its current supported extension surface includes:

- custom NBT physical craft prefabs;
- physics assembly into Sable;
- custom flyover events through datapacks;
- structure-linked permanent patrols;
- entity/crew export with prefab templates;
- autopilot goal composition;
- straight flight;
- altitude hold;
- orbit;
- obstacle avoidance;
- terrain response.

Current built-in examples already attach pillager aircraft patrols to pillager outposts and woodland mansions.

Preferred architecture:

~~~text
Skyforge civilization / faction / mission semantics
        ↓
thin realization adapter
        ↓
Create Aeronautics Discovery prefab + patrol/flyover substrate
        ↓
Sable / Aeronautics physical craft
~~~

Discovery must not become the world-level civilization authority.

The project is an external ARR dependency. Use its supported datapack/API/config seams; do not fork or redistribute modified source/assets without permission.

## Residual gap audit

### 1. Airfield / hangar / fuel service

**Status: mostly solved by reuse; only site topology remains Skyforge-owned.**

#### Direct asset source: Aeronautical Explorations

Current 1.21.1 NeoForge project includes at minimum:

- abandoned airstrip;
- ruined petrol station;
- lost cargo / related aviation debris visible in project material.

This is the closest direct match found for the remaining aviation-infrastructure gap.

Disposition:

> **Strong narrow prototype / external dependency candidate.**

Caveats:

- All Rights Reserved;
- no public governance/source seam was verified during this audit;
- it should be treated as an unmodified external dependency;
- native placement may need Skyforge interception/admission rather than asset redistribution.

#### Active airfield realization

Do not require a separate active-airport mod.

Preferred composition:

~~~text
Skyforge AIRFIELD site geometry
  + clear approach / landing surface
  + Aeronautical Explorations airstrip shell where usable
  + Create: Structures Arise fluid tank / container / workshop assets
  + Create/Aeronautics blocks
  + parked or Discovery-materialized aircraft
  + navigation tower / beacon
  + maintenance-state overlay
~~~

The bespoke portion is the semantic site plan and approach-clearance geometry, not the buildings/blocks.

### 2. Navigation / weather / radar station

**Status: direct shell available; functional overlay remains Skyforge integration.**

#### Radio Towers Lite

Current 1.21.1 NeoForge MIT project contains only three tower structures:

- standard tower;
- fenced tower;
- overrun/pillager tower.

This is a high-quality narrow candidate because it closes a visible vertical-infrastructure role without importing a broad worldgen catalogue.

Disposition:

> **Strong narrow asset candidate.**

Preferred reinterpretation:

~~~text
tower shell
  + Create Radars hardware
  + CC:Tweaked computer
  + Avionics / communications
  + A4MC / Skyforge weather instrumentation
  + power/storage
  =
BEACON_NAVIGATION / WEATHER_STATION / RADAR_POST / RADIO_RELAY
~~~

The full apocalypse/airdrop mod is unnecessary if the Lite project suffices.

A dedicated observatory mod was inspected but does not materially improve this role: its fantasy encounter/loot semantics and ARR licensing add baggage while the required weather/radar function still needs an integration overlay.

### 3. Mooring tower / route beacon

**Status: no new dependency required.**

Compose from:

- Radio Towers Lite shell where appropriate;
- Structory/Explorify tower assets if visually preferable;
- Aeronautics blocks;
- Supplementaries ordinary detail;
- CC/radar/navigation systems;
- Skyforge route semantics.

A mooring mast is a small functional layout, not a justification for another structure ecosystem.

### 4. Cliff dock / hanging cargo port

**Status: genuine Skyforge realization-mode requirement, not a missing-content dependency.**

No convincing 1.21.1 NeoForge structure package was found whose core value is correctly anchoring functional logistics into a vertical floating-island cliff.

The hard problem is:

- cliff anchor selection;
- orientation;
- support;
- open-air approach;
- cargo clearance;
- vertical attachment;
- relation to route and settlement roles.

Reuse existing content inside the layout:

~~~text
Create crane / gantry machinery
Create: Structures Arise container / crusher-crane vocabulary
Supplementaries ropes/chains/detail
storage / warehouse blocks
Aeronautics mooring/vehicle context
Skyforge cliff-anchor transform
~~~

Do not add a generic harbor mod merely to obtain horizontal wooden docks and then pretend it solved this geometry.

### 5. Underside installation

**Status: genuine Skyforge realization-mode requirement, not a missing-content dependency.**

Same conclusion as cliff docks.

Potential roles:

- mine mouth;
- salvage platform;
- military/watch site;
- ancient ruin;
- maintenance access;
- hanging industrial equipment.

Required work is an UNDERSIDE/CLIFF anchor-and-orientation realization mode capable of placing/reorienting existing structure pieces and machinery against non-horizontal terrain.

This should reuse existing palettes/assets rather than introduce an underside-content mod.

### 6. Detached sky settlement / platform

**Status: already sufficiently covered; no new dependency justified.**

Available vocabulary already includes:

- Structures Arise mini sky village / airborne structures;
- Create Aeronautics Structures airships/balloons;
- Discovery physical prefabs;
- Towns & Towers for civilian buildings;
- existing Sky Village mods as reserve only.

Create: Sky Village exists for 1.21.1 NeoForge, but is ARR and overlaps current capabilities. Do not add it unless integrated testing proves the retained stack cannot realize a needed detached civilian enclave.

### 7. Abandoned aviation

**Status: covered redundantly; avoid stacking.**

Available options include:

- Creat Aeronautics Structures;
- Aeronautical Explorations;
- Abandoned Structures: Aviation;
- Structures Arise airborne/technical assets.

Abandoned Structures: Aviation is a valid 1.21.1 NeoForge external dependency but currently adds no role that clearly survives the combination above. Keep as reserve.

### 8. Maritime / ocean-island logistics

**Status: future-domain candidate, not required for current Overworld structure closure.**

Promising existing components:

- Currents of Trade -> village docks, harbormaster, trader ships, harbor progression;
- Little Logistics -> functional dock/crane/routing system;
- Ice & Fire CE -> sea serpents;
- Hopo Better Underwater Ruins -> underwater historical texture;
- YUNG's Better Ocean Monuments -> major maritime destination.

Currents of Trade is extremely young as of this snapshot, so track rather than lock it.

When ocean-island realization begins, audit this as a dedicated maritime stack.

## Frontier/history asset libraries

### Structory vs Explorify

Do not decide only by aesthetics.

Leading role split:

~~~text
Explorify
  -> SUPPLY_CACHE
  -> GUIDEPOST / ROUTE_MARKER
  -> WATCH_POST
  -> FARMSTEAD
  -> CAMPSITE
  -> practical frontier sites

Structory
  -> HISTORICAL_SITE
  -> RUIN
  -> FIRETOWER
  -> COTTAGE / STABLE
  -> GRAVEYARD
  -> atmospheric abandonment/history
~~~

It may be acceptable to retain both selectively if Skyforge admission keeps local realized density sparse and they fill genuinely different semantic roles.

### Moog's Voyager Structures

Treat as a reserve asset quarry, not a default 100+ structure generator.

Potential useful roles include:

- religious/cathedral;
- cartographer/navigation tower;
- barn;
- railway/history;
- small ship;
- houses;
- ocean tower.

Promote individual roles only if the preferred narrow libraries leave a demonstrated gap.

## Rejected / unnecessary gap fillers

### Create: Sky Village

Interesting detached-settlement reference, but currently redundant with retained Create/Aeronautics structure vocabulary and ARR.

### Generic aviation ruin packs

Do not stack multiple aviation-ruin dependencies simply for variety.

### Broad generic structure packs

Do not add another catalogue merely because one asset is attractive if an existing retained dependency or a small Skyforge layout can satisfy the role.

### Dedicated observatory structure

Not necessary for V1. A weather/navigation/radar station is primarily a functional infrastructure assembly, and the tower shell already has better narrow candidates.

## Residual bespoke structure budget

After this audit, likely V1 bespoke content requirements are close to zero.

Remaining Skyforge-owned layouts / realization grammars:

~~~text
AIRFIELD_SITE_PLAN
CLIFF_DOCK_SITE_PLAN
UNDERSIDE_SITE_PLAN
MOORING / ROUTE_BEACON_SITE_PLAN
WEATHER / RADAR FUNCTIONAL_OVERLAY
~~~

These should use retained blocks and structure assets.

No bespoke block set, mob, texture family, or large building catalogue is currently justified for these roles.

## Integrated role examples

### Active airfield

~~~text
Skyforge AIRFIELD geometry
  -> adapted airstrip / hangar shell
  -> Structures Arise fluid tanks + cargo
  -> Create maintenance machinery
  -> Radio/Navigation tower
  -> parked / Discovery aircraft
~~~

### Abandoned airfield

~~~text
same semantic site role
  + ABANDONED maintenance state
  -> Aeronautical Explorations / Aeronautics Structures ruin assets
  -> broken machinery
  -> salvage
  -> no normal traffic
~~~

### Route/weather station

~~~text
small exposed island
  -> Radio Tower shell
  -> A4MC sensor source
  -> CC computer
  -> radar/radio/navigation hardware
  -> route service capability
~~~

### Cliff cargo port

~~~text
Skyforge cliff anchor
  -> Create crane / gantry
  -> cargo containers/storage
  -> mooring edge
  -> route linkage
~~~

### Underside mine

~~~text
Skyforge underside anchor
  -> mine entrance / platform
  -> crane/hoist
  -> mine/logistics content
  -> appropriate threat/history state
~~~

## Acceptance gates

Before production lock:

1. Pin exact 1.21.1 NeoForge artifacts and dependency graphs.
2. Verify Create: Structures Arise selected assets on the pinned pack.
3. Verify native Structures Arise generation can be disabled/subordinated without losing manual/Skyforge realization.
4. Test Creat Aeronautics Structures assets under exact-volume structure placement.
5. Test Create Aeronautics Discovery custom prefab assembly on the pinned Sable/Aeronautics stack.
6. Test structure-linked civilian and illager patrol/flyover realization.
7. Test Aeronautical Explorations abandoned airstrip/petrol station and determine whether Skyforge can admit/suppress placement cleanly.
8. Test Radio Towers Lite as weather/radar/navigation shells.
9. A/B Structory vs Explorify role coverage; retain both only if the role split proves real.
10. Prototype one cliff dock and one underside installation using only retained blocks/assets.
11. Verify those two prototypes demonstrate the need for placement/orientation machinery rather than new content.
12. Keep structure density sparse and provenance-observable.

## Closure rule

Do not perform another broad structure-mod search before integration unless a concrete semantic role remains unrealized after these tests.

> The remaining hard structure problems are mostly where and how to place existing content in a floating-island world, not where to find more buildings.
