# Civilization and Settlement System v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.


## Infrastructure-teaching companion

The concrete settlement/archetype layer is developed in [Civilization Archetypes and Infrastructure Teaching v0.1](civilization-archetypes-and-infrastructure-teaching-v0.1.md). It treats civilization density as a later experience-tuning parameter and focuses this architecture on functional networks, observation, technically plausible infrastructure, and role-specific salvage/loot.

## Core principle

> Civilization is a regional condition and functional network, not a structure-placement probability.

Skyforge should not create civilization by independently scattering villages, farms, towers, factories, docks, and forts.

Semantic planning should first establish:

- whether a province or cluster is inhabited;
- how strongly developed it is;
- what functions and industries it supports;
- what infrastructure connects those functions;
- whether the system is active, frontier, declining, abandoned, hostile, or exceptional;
- which islands perform which roles.

Minecraft and selected mods then supply buildings, villagers, blocks, loot, local AI, and other concrete realization.

## Simulation boundary

Skyforge should **not** become a continuously simulated grand-strategy economy.

Persistent world-level civilization state should remain coarse and explainable.

Skyforge needs enough state to answer:

- Why is a settlement here?
- What does it live on?
- What does it import/export?
- Why is this neighboring island farmed, mined, fortified, abandoned, or empty?
- What infrastructure should be visible?
- What traffic is plausible?
- Which faction controls the area?
- How maintained or abandoned is it?
- How dangerous is the surrounding region?

Minecraft handles loaded local simulation:

- villagers;
- animals;
- raids;
- containers;
- machines;
- redstone/Create systems;
- local combat;
- player modification.

## Semantic hierarchy

### Province civilization context

Candidate high-level states:

~~~text
WILD
FRONTIER
SETTLED
INDUSTRIAL
ABANDONED
CONTESTED
RESTRICTED
EXCEPTIONAL_ENCLAVE
~~~

This is a broad regional condition, not a biome replacement.

Useful coarse axes:

~~~text
populationIntensity
infrastructureIntensity
maintenance
tradeIntensity
trafficIntensity
agricultureIntensity
industryIntensity
extractionIntensity
militaryPressure
historicalDepth
abandonment
conflictPressure
routeImportance
~~~

### Cluster civilization plan

A cluster is the most important functional unit for medium/large settlements.

A cluster may represent:

- one isolated homestead;
- one village;
- one frontier settlement;
- one mining network;
- one agricultural network;
- one industrial hub;
- one port/airfield complex;
- one military/faction enclave;
- one abandoned network;
- one regional town distributed across several islands.

### Island functional role

Candidate roles:

~~~text
NONE
RESIDENTIAL
MARKET_CIVIC
AGRICULTURAL
PASTORAL
QUARRY
MINE
INDUSTRIAL
AIRFIELD
DOCK_PORT
STORAGE
BEACON_NAVIGATION
WEATHER_STATION
RELIGIOUS_MONASTIC
DEFENSIVE
MILITARY
RESOURCE_PROCESSING
WILD_RESERVE
RUINED
FORBIDDEN
~~~

Most islands should remain NONE, wild, or only lightly used.

## Settlements should exploit archipelago geography

A major Skyforge settlement should usually be a **network of specialized islands**, not one giant flattened village island.

Illustrative town:

~~~text
                    BEACON
                      |
          FARM -- RESIDENTIAL -- MARKET
            \        |           /
             \    AIRFIELD     STORAGE
              \      |          /
               MILL--+--INDUSTRIAL
                         |
                        MINE
~~~

The graph is semantic.

It does not imply literal bridges between every island.

Inter-island edges may represent:

- routine air route;
- cargo route;
- visual/navigation relationship;
- short physical bridge where geography supports it;
- lift/cable connection if future content supports it.

## Settlement scale

### Tier S0 — isolated site

Examples:

- farmstead;
- weather station;
- mine camp;
- shrine;
- lone house;
- ranger/lookout site;
- small military post.

Usually one island or one small site on a larger island.

### Tier S1 — hamlet / village

One primary inhabited island.

May have one or two nearby satellites:

- farm;
- quarry;
- beacon;
- dock.

Minecraft village mechanics may represent much of the local population.

### Tier S2 — town

One important core island plus several functional satellites.

Typical roles:

- residential/civic core;
- market/storage;
- agriculture;
- cargo dock or airfield;
- one productive/extractive satellite;
- navigation infrastructure.

### Tier S3 — regional hub

Multi-island functional network.

Possible components:

- multiple residential districts;
- major market;
- dedicated cargo/industrial island;
- airfield;
- several farms;
- mines/quarries;
- beacon/weather station;
- defense post.

This should be uncommon and regionally legible.

### Tier S4 — capital / exceptional center

Rare.

May materially influence cluster and route planning.

Potential traits:

- multiple settlement cores;
- major air/naval infrastructure;
- industry;
- specialized districts;
- high maintenance/lighting/traffic;
- strong defensive or civic silhouette.

The world should contain enough empty sky that these feel extraordinary.

## Functional dependency model

Settlements should have coarse **needs and capacities**, not a fully simulated commodity economy.

Candidate need/capacity categories:

~~~text
FOOD
WATER
HOUSING
FUEL
RAW_MATERIAL
MANUFACTURED_GOODS
STORAGE
TRANSPORT
NAVIGATION
DEFENSE
TRADE
~~~

An island or cluster can:

~~~text
SUPPLY
CONSUME
IMPORT
EXPORT
IGNORE
~~~

for each relevant category.

The planner only needs to ensure that a settlement has a plausible support story.

Example mining town:

~~~text
MINE ISLAND
  exports RAW_MATERIAL
  consumes FOOD / FUEL / TRANSPORT

FARM ISLAND
  exports FOOD
  consumes MANUFACTURED_GOODS

INDUSTRIAL ISLAND
  consumes RAW_MATERIAL / FUEL
  exports MANUFACTURED_GOODS

AIRFIELD / DOCK
  supplies TRANSPORT / TRADE
~~~

No continuous numerical market simulation is required.

## Specialization is desirable

A major design goal is to make geography economically meaningful.

A settlement should not necessarily be self-sufficient on every island.

### Agricultural island

Prefers:

- productive ecology;
- water availability;
- usable surface;
- good sky exposure;
- manageable wind/weather exposure.

Potential realization:

- farms;
- barns;
- Farmer's Delight/Slice & Dice content;
- livestock;
- windmill or processing machinery where appropriate.

### Pastoral island

Can tolerate somewhat rougher terrain.

May support:

- livestock;
- shepherd buildings;
- feed/storage;
- fenced/managed open ground.

### Mining island

Requires:

- mineral-bearing geology;
- workable connected rock volume;
- access;
- historical extraction semantics.

May have very poor agriculture.

### Quarry island

Prefers accessible surface/near-surface useful material.

May show:

- cut faces;
- crane/loading infrastructure;
- stockpiles;
- industrial scars.

### Industrial island

Prefers:

- cargo access;
- local buildable patches;
- fuel/material supply;
- separation from dense residential use;
- room for machinery.

Create blocks should dominate concrete realization rather than a new bespoke industrial block set.

### Airfield island

Requires:

- low-relief local surface;
- open approach corridors;
- low obstacle/occlusion risk;
- strategic route position.

Could include:

- hangars;
- windsocks;
- beacons;
- fuel/storage;
- repair facilities.

### Dock / port island

Requires:

- suitable cliff/edge anchor;
- cargo access;
- open approach;
- regional route value.

Possible forms:

- cliff dock;
- hanging cargo platform;
- mooring mast;
- crane yard;
- warehouse.

### Beacon / weather island

Can be small, exposed, and relatively unsuitable for habitation.

Useful because:

- route visibility;
- wind/weather monitoring;
- communication;
- radar/navigation infrastructure.

This is an example of a small island becoming important without becoming heavily settled.

## Morphology should produce functional diversity

Settlement roles should consume terrain capabilities rather than demand one island family.

### Residential

Can plausibly occupy:

- Tableland;
- Massif shoulders;
- Basin floors/rims;
- Spine ridges;
- Lobed shoulders.

### Agriculture

Stronger preference for broad low/moderate-relief surfaces and water.

### Industry

Can tolerate less attractive terrain if local working surfaces and transport exist.

### Airfield

Requires specific local geometry.

### Dock

Requires local cliff/edge geometry.

### Mining

Driven primarily by geology/interior capacity.

This naturally distributes functions across an archipelago.

## Settlement spatial grammar

Large settlements should have internal logic.

### Core and satellites

Common pattern:

~~~text
core residential/civic island
    |
    +--> food-producing satellite
    +--> extractive satellite
    +--> cargo/airfield satellite
    +--> navigation satellite
~~~

### Linear route settlement

Possible along a Spine-like or elongated cluster.

~~~text
beacon -> village -> warehouse -> airfield -> mine
~~~

### Basin-centered settlement

Protected central habitation with agriculture or storage around rim/floor.

### Distributed frontier

Several small isolated sites linked by one route rather than one dense core.

### Industrial complex

Production, storage, transport, and extraction dominate; residential presence may be small.

## Buildings should adapt to terrain

Do not flatten whole islands for settlement.

Settlement planner should prefer:

- several usable patches;
- paths following contours;
- stairs/terraces where Minecraft assets allow;
- bridges only across sensible local gaps;
- cliff infrastructure on real cliff anchors.

Towns & Towers or vanilla village jigsaw content may provide building vocabulary, but Skyforge should own the site plan and island role.

## Local architectural vocabulary

Initial strategy:

1. reuse vanilla regional palettes;
2. reuse Towns & Towers / selected structure-library assets;
3. use Supplementaries for ordinary detail;
4. use Create/Aeronautics blocks for infrastructure;
5. add bespoke architecture only where the role cannot be expressed coherently.

Architecture may adapt to:

- climate;
- local material palette;
- civilization maintenance level;
- industry;
- faction;
- exposure.

Avoid making every province a separate bespoke block set.

## Villagers

Vanilla villagers remain useful as **local civilian simulation**.

They can provide:

- professions;
- trade;
- local population;
- bells/golems;
- farms;
- homes;
- social texture.

But a Skyforge settlement is semantically larger than one vanilla village boundary.

One Skyforge town may contain:

- one vanilla village core;
- one separate industrial island with few/no villagers;
- one farm satellite;
- one airfield;
- one beacon station.

Skyforge owns the network.

Minecraft owns the local entities.

## Population scale

Do not persist thousands of simulated civilians.

World-level settlement state may know:

- population class;
- activity;
- maintenance;
- economic role;
- route importance.

Concrete villagers exist through ordinary loaded-chunk mechanics.

Population classes could be qualitative:

~~~text
OUTPOST
HAMLET
VILLAGE
TOWN
HUB
CAPITAL
~~~

The semantic class influences structure count/density without requiring one entity per abstract resident.

## Profession and economic role

Vanilla villager professions can be treated as local manifestations of cluster roles.

Examples:

- farmer -> agricultural capacity;
- fisherman -> water/coastal settlement;
- toolsmith/weaponsmith/armorer -> industrial/craft center;
- librarian/cartographer -> civic/navigation center;
- cleric -> religious/civic site.

Do not require perfect one-to-one correspondence.

The planner needs thematic coherence, not accounting.

## Trade and player services

Settlements should be useful for more than beds and emerald trades.

Potential service categories:

~~~text
FOOD
BASIC_SUPPLIES
REPAIR
FUEL
STORAGE
MAPS_NAVIGATION
RARE_GOODS
CRAFT_COMPONENTS
TRANSPORT_INFRASTRUCTURE
INFORMATION
~~~

Prefer existing mechanics before bespoke UI.

Examples:

- villager trades;
- Farmer's Delight food;
- Create component supply;
- CC/navigation infrastructure;
- maps;
- loot/salvage;
- public docking/refueling structures.

A custom contract/quest system should only be added if later gameplay proves a real need.

## Logistics

Civilization is where Create/Aeronautics becomes ordinary infrastructure rather than player-only technology.

### Semantic route network

Each settled cluster can carry coarse route edges:

~~~text
origin island role
destination island role
traffic class
cargo class
maintenance
risk
~~~

These do not require every aircraft to be persistently simulated.

They support:

- placement of docks/airfields;
- wreck likelihood;
- visible traffic when loaded;
- trade/import plausibility;
- route beacons;
- infrastructure density.

### Traffic realization

Potential tiers:

1. environmental evidence only: lights, contrails/plumes, parked craft, docks;
2. occasional scripted/AI local craft where safe;
3. active physical Sable/Aeronautics vehicles only when technically justified.

Do not make unloaded civilization depend on continuously simulated contraptions.

## Navigation network

Settled regions should be easier to navigate.

Potential coverage gradient:

~~~text
SETTLED CORE    dense beacons / route markers / radar
FRONTIER        partial coverage
ABANDONED       degraded/failed coverage
WILD            absent
~~~

Existing CC:Tweaked/radar primitives should supply functionality where possible.

Civilization semantics supply:

- where stations exist;
- whether they are maintained;
- what routes they cover.

## Long-distance visual signature

Settlement experience begins before arrival.

### Active civilian settlement

Potential signals:

- warm lights at night;
- smoke/plumes;
- visible cultivated patches;
- moving/parked aircraft;
- beacon flashes;
- towers;
- cranes/hangars;
- regular geometry embedded in natural terrain.

### Industrial settlement

- stacks;
- machinery silhouette;
- cranes;
- cargo areas;
- denser lighting.

### Frontier settlement

- few structures;
- one beacon;
- limited farm;
- sparse infrastructure.

### Abandoned settlement

- no route traffic;
- failed/dark beacons;
- skeletal structures;
- wreckage;
- vegetation return;
- missing lights.

Distant Horizons makes these signatures important world-composition inputs.

## Maintenance state

Settlements should have a coarse maintenance axis.

Candidate states:

~~~text
MAINTAINED
WORN
DECLINING
ABANDONED
RUINED
RECLAIMED
~~~

Maintenance influences:

- structure condition;
- lighting;
- route infrastructure;
- spawn/threat behavior;
- loot/salvage;
- vegetation;
- traffic;
- villager presence.

This is more useful than merely selecting "ruined village structure."

## Historical lifecycle

A settlement may be authored directly into a historical state.

Possible history:

~~~text
prosperous settlement
-> trade/resource decline
-> maintenance loss
-> abandonment
-> ecological recolonization
-> scavenger/hostile occupation
~~~

Skyforge does not need to simulate this transition in real time.

The final state and traces can be authored from a deterministic history descriptor.

Future persistence may allow player action to alter maintenance/occupation, but that is not required for first generation.

## Abandoned settlement ecology

Abandonment should alter ecological feasibility.

Potential changes:

- rats/crows/scavengers increase;
- shy wildlife gradually returns;
- large predators may occupy outer abandoned zones;
- vegetation invades farms/roads;
- nests/roosts use buildings.

This can reuse the ecology relationship system rather than create special abandoned-spawn lists.

## Hostile spawning in settlements

Maintained civilian cores should strongly suppress ambient hostile realization.

Reason:

- lighting;
- maintenance;
- activity;
- patrol/golem presence;
- semantic civilization pressure.

This is not an invulnerability field.

Danger can still come from:

- raids;
- faction incursions;
- nearby wilderness;
- abandoned districts;
- deliberate dungeon structures;
- exceptional threats.

Unmaintained settlements gradually lose this suppression.

## Defense

Avoid turning all civilian settlements into fortresses.

Defense intensity should depend on:

- frontier status;
- hostile faction pressure;
- route importance;
- settlement tier;
- historical conflict.

Possible infrastructure:

- walls on suitable local terrain;
- guard/watch towers;
- beacon alarms;
- iron golems;
- defensive cliff positions;
- protected air approaches.

A peaceful agricultural cluster should not look military merely because illagers exist somewhere in the world.

## Illager civilization

Illagers should use the same high-level civilization framework with different semantics.

### Functional roles

~~~text
WATCH_POST
CAMP
FORT
BARRACKS
STORAGE
INDUSTRIAL
AIR_BASE
NAVAL_BASE
PRISON
EXTRACTION
REGIONAL_STRONGHOLD
~~~

Illager Structures supplies much of the concrete structure vocabulary.

Friends & Foes / It Takes a Pillage supply a compact entity/combat vocabulary.

### Territorial hierarchy

Potential progression through controlled territory:

~~~text
route warning / abandoned civilian edge
-> watch post
-> patrol
-> supply/extraction site
-> fort
-> industrial/military center
-> regional stronghold
~~~

This is more legible than random pillager structures.

### Economy

Illagers also require logistics.

Their structures can imply:

- extraction;
- storage;
- military production;
- food/supply;
- air/naval movement.

This makes hostile territory feel inhabited rather than spawned.

## Civilian and illager borders

Contested regions can express conflict through world state:

- abandoned farms;
- fortified settlements;
- wrecks;
- patrols;
- ruined beacons;
- reduced civilian traffic;
- scavengers;
- military outposts.

Do not require a live strategic war simulation.

A deterministic conflict/history field can author the evidence.

## Other factions

Do not invent a large faction catalogue before the setting needs one.

The civilization system should remain generic enough to support future:

- neutral enclaves;
- monasteries;
- trader networks;
- player-aligned colonies;
- modded factions.

But the first implementation can focus on:

1. civilian/villager settlement networks;
2. illager hostile networks;
3. abandoned/historical sites.

## Structures and settlement plans

Concrete structures should be subordinate to island roles.

Example civilian town:

~~~text
SettlementPlan: town
    |
    +--> residential core
    |       -> vanilla/Towns & Towers village assets
    |
    +--> agricultural satellite
    |       -> farm/barn assets
    |
    +--> airfield
    |       -> Skyforge/Create/Aeronautics layout
    |
    +--> storage/market
    |       -> existing blocks/structure assets
    |
    +--> beacon station
            -> compact bespoke layout
~~~

The settlement plan exists before individual structure candidates.

## Candidate content stack

### Civilian structures
- vanilla villages;
- Towns & Towers as leading prototype;
- Explorify or Structory for smaller supporting sites;
- selected YUNG structures where semantically appropriate.

### Detail / ordinary life
- Supplementaries;
- Farmer's Delight;
- Slice & Dice.

### Engineering
- Create;
- Create Aeronautics/Sable;
- selected propulsion/power/storage/logistics addons.

### Hostile civilization
- vanilla illagers;
- Friends & Foes;
- It Takes a Pillage Continuation;
- Illager Structures.

No extra villager/faction-AI dependency should be added until playtesting demonstrates a concrete gap.

## Civilization rarity and world rhythm

Open sky remains the dominant state.

Qualitative target:

~~~text
MOST CLUSTERS
  wild / untouched / only traces

SOME
  frontier / isolated habitation

FEWER
  villages / specialized settlements

RARE
  towns / industrial networks

VERY RARE
  regional hubs / capitals / major hostile centers
~~~

Large urbanized skylines should therefore be memorable.

## Player relationship

Initial civilization gameplay can rely on existing Minecraft affordances:

- trade;
- shelter;
- supplies;
- repair/crafting infrastructure;
- maps;
- safe-ish staging points;
- fuel;
- navigation;
- salvage;
- hostile territory.

Do not immediately add reputation, ownership, taxation, quests, diplomacy, or dynamic conquest systems.

Those should be justified by actual gameplay needs later.

## Potential player-built integration

Long-term, the same semantic roles could potentially be exposed to player settlements.

Examples:

- player establishes beacon;
- builds airfield;
- restores abandoned settlement;
- creates trade route.

But generated-world civilization should not depend on solving that problem first.

## Performance model

Persist coarse network facts, not continuous actors.

World-level persistence may include:

- settlement identity;
- tier;
- island roles;
- faction/control;
- maintenance state;
- route edges;
- historical/abandonment state.

Loaded Minecraft chunks realize:

- villagers;
- animals;
- golems;
- machines;
- hostile entities;
- local effects.

This keeps the global world cheap.

## Debug/evidence requirements

Future authoring evidence should show:

- province civilization state;
- cluster settlement plan;
- island functional roles;
- site capability matches;
- route edges;
- maintenance/abandonment;
- faction control.

Visual atlas should make it possible to inspect whether:

- settlements form coherent networks;
- morphology remains diverse;
- functions correlate with terrain/geology;
- wild clusters remain dominant;
- major hubs are rare;
- infrastructure is legible from a distance.

## Acceptance principles

1. A Skyforge settlement must look adapted to separated islands.
2. Major settlements should usually be functional archipelago networks.
3. Economic specialization should follow real terrain/ecology/geology.
4. Concrete Minecraft structures realize a semantic plan rather than define it.
5. Villagers simulate local civilians; they do not define the complete settlement.
6. Civilization should improve navigation/logistics gameplay without erasing distance.
7. Wild space remains the majority condition.
8. Abandonment and hostile control are world states, not merely alternate structure palettes.
9. Persistent civilization simulation remains coarse and cheap.
10. No new bespoke system is added where vanilla/mod mechanics already provide adequate local realization.
