# Resource and Progression Geography v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.

## Core principle

> Resource geography should make travel economically meaningful after the player can travel, not make travel a prerequisite for obtaining the means to travel.

Skyforge should avoid two failures:

1. every island contains every useful resource, making geography irrelevant;
2. basic progression depends on a rare distant resource before the player has practical air mobility.

The target is **local sufficiency plus regional specialization**.

## Progression-geography bands

### R0 — survival ubiquitous

A viable inhabited/starting region should provide ordinary access to:

- wood or equivalent renewable building material;
- stone/basic aggregate;
- water;
- food;
- common fuel/heat source;
- basic iron-class metal access;
- ordinary soil/ecology needed for survival.

Not every individual island needs all of these.

The local cluster/nearby reachable area should.

### R1 — first engineering / first flight

The player's initial region should contain enough material to reach:

- basic Create machinery;
- first practical Skyforge crossing method;
- simple storage and repair;
- basic navigation;
- a reliable first aircraft/airship/glider path once recipes are finalized.

Critical rule:

> No resource that is mandatory for first reliable flight may itself require reliable flight to obtain.

This may be satisfied through:

- several nearby islands;
- bridges;
- climbing/rope/gliding;
- very short primitive flight;
- village trade;
- modest salvage.

### R2 — regional mobility and specialization

Once practical flight exists, regional geography can matter strongly.

Candidate resource roles:

- Create zinc and other engineering metals;
- better fuel sources;
- redstone/electrical materials;
- larger iron/copper deposits;
- specialized agriculture;
- industrial feedstocks;
- uncommon building/material palettes.

This is where the player first has a strong reason to establish routes.

### R3 — mature industry

Regional or province-scale specialization can gate:

- diesel/petroleum industry;
- advanced metallurgy;
- large-scale electricity;
- high-throughput processing;
- advanced aviation;
- automated logistics;
- sophisticated navigation/radar.

The player should now have the transportation capability needed to solve distributed-resource problems.

### R4 — exceptional / expeditionary

Rare resources and rewards can belong to:

- legendary structures;
- Ancient Cities;
- major dungeons;
- hostile strongholds;
- exceptional geology;
- rare ecological sites;
- bosses;
- distant provinces.

These should improve capability, efficiency, collection, or prestige without making ordinary world survival dependent on one legendary roll.

## Geographic availability classes

Each concrete resource should eventually receive one semantic distribution class.

### UBIQUITOUS

Available in most local regions.

Examples should include basic survival materials.

### COMMON_REGIONAL

Not necessarily on every island, but expected within ordinary local/regional exploration.

Good for foundational engineering metals.

### SPECIALIZED_REGIONAL

Strongly associated with specific geology, ecology, climate, or civilization.

Creates trade and route value.

### STRATEGIC_NODE

Concentrated enough that a mine, oilfield, quarry, forest, or agricultural region can become a major economic node.

### EXCEPTIONAL

Rare enough to motivate dedicated expeditions or exceptional structures.

These classes are tuning contracts, not exact frequencies.

## Geography should follow authored semantics

Skyforge already authors:

- geology;
- material affinities/assemblages;
- mineral-bearing structural units;
- hydrology;
- ecology;
- climate;
- civilization history.

Concrete Minecraft/mod resources should be mapped downstream from those semantics.

Preferred causality:

~~~text
mineralized geology
-> ore family eligibility
-> Minecraft/mod ore realization

productive moist surface
-> agriculture / plant resource eligibility

hydrocarbon-capable subsurface context
-> petroleum deposit eligibility

historical extraction
-> mine / quarry / industrial civilization
~~~

Avoid independent per-mod worldgen that ignores the authored island.

## Vanilla ore policy

Exact distributions remain a later tuning pass, but the design intent should be:

### Iron

Foundational.

Should be common enough that early engineering is not soft-locked.

Large deposits may still create meaningful mining centers.

### Copper

Also foundational for the likely engineering/electrical stack.

Should be common regionally and allowed to form locally significant deposits.

### Coal

Useful but should not become the only route to early power.

Charcoal/renewable heat should remain valid.

Large coal-bearing geology can create industrial specialization.

### Gold

Less common and more regional.

Can support trade/electrical/advanced recipe demand without being extremely scarce.

### Redstone

Important to automation and electronics.

Should require meaningful underground/resource exploration but remain reliably obtainable before advanced automation is required.

### Lapis

Ordinary vanilla progression resource.

No need to make it a major regional strategic material unless later gameplay gives it a larger role.

### Diamond

Rare deep resource.

May favor large, mature, deep geological islands.

Should remain discoverable through mining rather than becoming structure-loot-only.

### Emerald

Can remain strongly associated with particular regional geology/trade contexts.

Because villagers already use emerald currency, direct ore geography need not supply most settlement emerald circulation.

## Create zinc

Create zinc is likely a foundational engineering material.

Therefore:

- it should not be an ultra-rare strategic resource;
- it should be represented in Skyforge mineral geology rather than independent Create ore placement;
- ordinary regions should provide a realistic route to zinc before mature Create progression depends on it;
- large zinc-bearing districts can still support dedicated mining settlements.

The exact recipe dependency audit should determine whether zinc belongs to R1 or early R2.

## Petroleum and liquid fuel

Create: Diesel Generators is a strong current candidate and provides crude-oil refining and diesel-generation gameplay.

Petroleum is particularly suitable for **strategic-node geography**.

### Desired behavior

Not every island contains oil.

Instead, petroleum may occur in specific geological provinces/large islands.

That creates:

- oilfields/pumpjack sites;
- refineries;
- tank farms;
- cargo routes;
- fuel depots;
- strategic settlement value.

### Bootstrap constraint

Oil should not be mandatory for first flight.

Early movement should remain possible through:

- hot-air/buoyant flight;
- simple mechanical propulsion;
- renewable/solid fuels;
- other lower-tier methods supported by the final recipes.

Petroleum then improves:

- range;
- sustained power;
- heavy aircraft;
- industrial throughput.

### Avoid infinite-world triviality

Minecraft's infinite world means petroleum cannot be globally scarce in a conventional finite-resource sense.

Its value comes from **local concentration and logistics**, not ultimate global exhaustion.

## Renewable fuels

Renewable energy/fuel should remain a viable alternative, especially early.

Candidate roles:

- charcoal;
- biomass/fermentation where supported;
- wind for fixed infrastructure;
- water where authored hydrology permits;
- steam/heat paths from Create;
- electrical generation later.

The design should favor multiple energy paths rather than one universal fuel.

## Wind power

Avoid making airborne windmills the obvious universal solution.

Wind power is strongest when:

- stationary;
- geographically exposed;
- used for local infrastructure;
- paired with storage/electricity where needed.

Vehicle propulsion should depend on actual vehicle physics/engines rather than a loophole where the craft powers itself from its own windmill.

## Electricity

Create Crafts & Additions is a strong current candidate for the first electricity ecosystem.

Electricity should provide utility:

- sensors;
- radar;
- CC/computing;
- lighting;
- motors;
- batteries;
- distributed machinery.

It should not make mechanical Create infrastructure irrelevant.

Resource geography may make copper/gold/redstone and specialized electrical materials regionally meaningful.

Do not add multiple redundant electricity ecosystems before a real need appears.

## Metallurgy

Create: Metallurgy is a viable current 1.21.1 NeoForge candidate and should be evaluated as a **processing-depth** addition rather than a new independent worldgen authority.

Desired role:

~~~text
Skyforge geology / ore tags
-> extraction
-> Create / Metallurgy processing
-> alloys / refined materials
~~~

If the mod introduces its own ores/mineral requirements, those should be reconciled with Skyforge geology and the unified material/tag policy.

Avoid duplicate parallel ores for the same semantic metal.

## Resource localization should have causes

Useful axes include:

### Geology-driven

- iron/copper/zinc/gold/diamond;
- coal;
- petroleum;
- quarry stone;
- specialized mineral systems.

### Ecology/climate-driven

- timber types;
- crops;
- livestock;
- medicinal/food ingredients;
- special plants.

### Hydrology-driven

- water;
- aquatic resources;
- wet agriculture;
- future ocean resources.

### Civilization-driven

- processed goods;
- components;
- fuel depots;
- trade stock;
- salvage;
- maps/navigation information.

### Structure/legendary-driven

- artifacts;
- exceptional equipment;
- rare boss/structure loot.

No single axis should own all progression.

## Trade as geographic substitution

Civilization gives the player an alternative to direct extraction.

Example:

~~~text
player needs zinc
    |
    +--> find zinc geology and mine
    |
    +--> trade at mining/industrial settlement
    |
    +--> salvage limited amount from abandoned industry
~~~

These paths should differ in:

- quantity;
- reliability;
- risk;
- infrastructure requirement.

Trade should reduce frustration without erasing the meaning of resource geography.

## Salvage as partial substitution

Salvage is especially useful for acquiring **small amounts** of advanced material.

Good for:

- first sample;
- emergency repair;
- recipe experimentation;
- temporary progression acceleration.

Bad for:

- supplying the player's entire mature industrial economy indefinitely.

Large-scale production should eventually require:

- extraction;
- trade/logistics;
- automation.

## Resource scale matters

A player may find enough iron for tools almost anywhere in a reasonable region.

That does not mean every island can support a steel-scale industrial base.

Separate:

~~~text
presence
deposit quality
deposit size
accessibility
processing difficulty
~~~

A small ore occurrence satisfies personal progression.

A rich mineral district justifies a mining town and freight route.

## Spawn / starting-region guarantee

The world planner should eventually enforce a **bootstrap completeness** contract around the initial player region.

Candidate requirement:

Within a bounded early-access domain, the player can obtain enough to reach:

- ordinary survival;
- iron-level tools;
- basic Create;
- first practical crossing/flight;
- basic repair/storage/navigation.

This should be evaluated semantically before final world acceptance.

The exact radius/mobility budget belongs to playtesting.

## Regional guarantee

Broader regions may carry weaker guarantees:

- at least one foundational engineering-metal source;
- access to major vanilla progression ores;
- at least one viable fuel/power path;
- enough ecological diversity for normal play.

Strategic resources may intentionally be absent from a province.

That absence creates route/trade pressure.

## Province specialization

A mature world should support province identities such as:

### Mineral province

Strong metal/mineral geology.

Likely:

- mines;
- quarries;
- industrial settlement;
- freight traffic.

### Agricultural province

Productive climate/hydrology/surface terrain.

Likely:

- farms;
- livestock;
- food exports.

### Fuel province

Coal/petroleum/biomass advantage.

Likely:

- refineries;
- tank/storage;
- heavy industry;
- strategic routes.

### Trade corridor

Not necessarily resource-rich itself.

Its value comes from:

- safe geography;
- central route position;
- navigation infrastructure;
- markets/storage.

### Wild exceptional province

Low civilization but high ecological/geological/legendary value.

Province specialization should emerge from actual authored conditions, not from arbitrary labels assigned after the fact.

## Resource routes create gameplay

Meaningful logistics examples:

~~~text
oilfield
-> refinery
-> fuel depot
-> regional airfield

mine
-> processing
-> warehouse
-> industrial hub

farm
-> mill
-> storage
-> town / route station
~~~

The player can replicate these networks.

Civilization demonstrates them.

## Freight-preservation rule

Storage convenience must not erase geography.

Backpacks and compact storage are acceptable only if they do not make bulk freight meaningless.

Possible balancing levers later:

- capacity;
- item classes;
- bulk resource volume;
- vehicle cargo efficiency;
- fuel costs;
- automation convenience.

Do not solve this prematurely, but audit every storage dependency against the logistics fantasy.

## Progression should broaden transportation, not merely increase speed

Resource geography is most useful if different transport modes remain meaningful.

Potential progression:

~~~text
local bridging / climbing / gliding
-> primitive buoyant flight
-> reliable small aircraft
-> cargo airship / freight craft
-> long-range industrial aviation
-> automated logistics
~~~

Each step should change what resource geography the player can economically exploit.

## Civilization asset-budget integration

Every progression-relevant resource or block also participates in the active-settlement hoover policy.

For each generated civic asset, classify:

~~~text
WORLDGEN_COMMON
WORLDGEN_LIMITED
ACTIVE_SITE_AVOID
ABANDONED_SALVAGE
HOSTILE_REWARD
~~~

Resource progression and civilization structure design must be reviewed together.

## Dependency implications

Current strong candidates:

### Create: Diesel Generators

Use for:

- strategic petroleum;
- refining;
- diesel power;
- industrial fuel geography.

### Create Crafts & Additions

Use as the leading single electricity ecosystem if testing confirms fit.

### Create: Metallurgy

Use for deeper processing/alloy realization if it improves resource identity without duplicating Skyforge geology.

All remain subordinate to Skyforge resource placement semantics.

## To-build package

Likely neutral/integration concepts:

~~~text
ResourceAvailabilityClass
RegionalResourceProfile
BootstrapCompletenessRequirement
ResourceDepositQuality / Scale
Resource-to-Geology Mapping
Resource-to-Ecology Mapping
Trade Substitution Profile
Salvage Substitution Profile
Strategic Fuel Geography
Civilization Resource Dependency
Progression-Sensitive Asset Classification
Resource Evidence / Telemetry
~~~

Minecraft/mod adapters map semantic resource families to:

- ore/block/item tags;
- placed features;
- fluid/deposit systems;
- loot;
- trade;
- processing recipes.

## Evidence requirements

Future resource-authoring evidence should make it possible to inspect:

- local bootstrap completeness;
- resource distribution by island/cluster/province;
- deposit scale;
- resource-geology correlation;
- settlement specialization;
- route pressure;
- resource absence;
- trade/salvage alternatives;
- morphology bias introduced by resource requirements.

## Acceptance tests

### Bootstrap test

A normal starting region can reach first practical flight without requiring an already-flight-only resource.

### Geography test

A mature player has real reasons to travel to other regions because some resources are concentrated or absent locally.

### Non-arbitrary test

Resource concentrations correlate with authored geology/ecology/civilization.

### Trade test

Civilization can provide limited alternatives to direct extraction without making resource geography irrelevant.

### Salvage test

Abandoned/hostile sites can accelerate progression but cannot sustain arbitrary mature industry through free generated parts alone.

### Industrial scale test

Common personal-use resource presence does not imply every local island supports industrial-scale extraction.

### Mod-authority test

Third-party ore/oil/resource worldgen does not independently bypass Skyforge geography.

## Acceptance principles

1. Local survival and first practical flight must be robust.
2. Regional specialization becomes stronger after mobility exists.
3. Resource geography follows authored causes.
4. Trade and salvage are partial substitutes, not universal replacements for extraction.
5. Strategic resources create logistics rather than hard soft-locks.
6. Civilization and resource geography explain each other.
7. Infinite-world resources gain value from concentration/access/logistics, not global exhaustion.
8. Third-party processing mods do not become independent worldgen authorities.
9. Progression-sensitive generated infrastructure is budgeted against hoovering.
10. Travel should become economically useful before it becomes economically mandatory.
