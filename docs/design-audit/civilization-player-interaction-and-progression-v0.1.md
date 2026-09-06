# Civilization Player Interaction and Progression v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.

## Core principle

> Civilizations should give the player services, information, examples, and salvage opportunities through ordinary Minecraft/mod interactions rather than a bespoke RPG layer.

The player should benefit from finding civilization even when no quest, reputation score, faction menu, or scripted dialogue exists.

The three primary relationships are:

~~~text
ACTIVE CIVILIZATION
    -> services / trade / information / safe staging / observation

ABANDONED CIVILIZATION
    -> salvage / repair / historical inference / reusable infrastructure

HOSTILE CIVILIZATION
    -> combat / infiltration / intelligence / military-industrial salvage
~~~

These categories may overlap at contested or declining sites, but they should remain conceptually distinct.

## What civilization should provide

Candidate player-facing value categories:

~~~text
SUPPLY
TRADE
SHELTER
REPAIR
FUEL
STORAGE
NAVIGATION
WEATHER_INFORMATION
CRAFTING_INFRASTRUCTURE
TRANSPORT_INFRASTRUCTURE
SALVAGE
RESOURCE_ACCESS
KNOWLEDGE
RISK_REWARD
~~~

Not every settlement provides every category.

Specialization is desirable.

## Active settlements

### Intended role

Active civilian settlements should be places where the player can:

- rest and stage expeditions;
- obtain ordinary supplies;
- trade;
- learn route geography;
- access navigation/weather infrastructure;
- see mature machinery in context;
- obtain modest quantities of useful components;
- potentially refuel/repair if the selected mod mechanics allow it cleanly.

They should not function primarily as loot dungeons.

### Reuse-first interaction

Prefer:

- vanilla villager trading;
- vanilla beds/workstations;
- maps/cartographers;
- existing crafting and repair blocks;
- Create/Aeronautics machinery;
- ordinary containers where appropriate;
- CC/radar/navigation devices;
- Farmer's Delight food systems.

Avoid a custom settlement menu.

## Service profiles

A settlement role can expose a semantic ServiceProfile.

Candidate services:

### BASIC_SUPPLY

Food, torches, simple tools, common materials.

### SPECIALTY_TRADE

Goods reflecting local function:

- agriculture;
- mining;
- industry;
- navigation;
- maritime/aviation;
- rare regional resources.

### REPAIR_WORKSHOP

A place where the player can use existing crafting/repair machinery.

This does not require an NPC repair UI.

### FUEL_SUPPLY

Fuel availability at airfields, ports, and industrial hubs.

Realization should use the actual selected fuel/power mods rather than a Skyforge currency dispenser.

### NAVIGATION_SERVICE

May include:

- maps;
- route markers;
- coordinates;
- beacons;
- CC broadcasts;
- radar station information;
- lodestone/compass-like infrastructure where useful.

### WEATHER_SERVICE

May include:

- visible instruments;
- forecast/current-condition display;
- wind information;
- pressure/storm information if the authoritative atmosphere exposes it.

A custom Skyforge weather sensor is justified only if no existing block/API can expose required data.

### STORAGE / CARGO

Public or semipublic logistical infrastructure.

The first implementation does not need abstract warehouse ownership.

### DOCKING / LANDING

A physically usable airfield, mooring, dock, or safe approach area.

The value is spatial rather than menu-driven.

## Do not make every active settlement a full service center

A frontier homestead might provide:

~~~text
food
bed
basic trade
route hint
~~~

A route station:

~~~text
fuel
weather
navigation
minor repair
~~~

A mining settlement:

~~~text
raw materials
tools
cargo infrastructure
basic supplies
~~~

A regional hub:

~~~text
many trades
repair
fuel
maps
storage
industry
navigation
weather
~~~

This preserves the settlement network's functional hierarchy.

## Trade

### Vanilla first

Villager trades should remain the baseline civilian economy.

Skyforge may eventually bias profession prevalence and data-driven trade additions toward settlement role, but it should not replace emerald trading with a bespoke economy unless vanilla/mod mechanics prove inadequate.

### Role coherence

Examples:

- agricultural cluster -> food/farming goods;
- mining town -> tools/materials;
- industrial hub -> mechanical components where a clean trade integration exists;
- navigation center -> maps/compasses/information;
- regional hub -> broader mix.

Avoid turning every villager into a custom shopkeeper.

### Rare goods

Some settlement types may expose items that make the location worth seeking.

These should be bounded and role-appropriate.

Prefer convenience/access and partial progression assistance over mandatory exclusive gating unless later progression design explicitly requires otherwise.

## Maps and information

Civilization can make exploration more directed without omniscient mapping.

Useful information rewards:

- map to another settlement;
- map to major dungeon/landmark;
- route chart;
- coordinates of beacon stations;
- notice of hazardous route/weather area;
- local resource-region clue;
- old map recovered from abandoned infrastructure.

Cartographers and ordinary Minecraft maps should be used wherever possible.

A map should reveal **where to investigate**, not fully explain the destination.

## Navigation infrastructure as usable world content

A beacon should ideally do something beyond look decorative.

Depending on the final stack:

- visual signal;
- CC wireless/GPS broadcast;
- identifier/code;
- radar/navigation reference;
- map correspondence.

The player can learn the network and eventually reproduce the same infrastructure.

This creates progression through literacy:

~~~text
see beacon
-> understand route
-> use network
-> build own receiver/station
~~~

## Weather infrastructure

Likewise, weather stations can teach the environmental system.

Possible player progression:

~~~text
read windsock / clouds
-> use analog instruments
-> visit weather station
-> obtain quantitative wind/weather information
-> build onboard instrumentation
-> automate route decisions
~~~

The generated civilization demonstrates the ceiling without forcing the player to start there.

## Active infrastructure and ownership

Do not initially build a bespoke claim/theft/protection system.

Minecraft normally allows the player to:

- open generated containers;
- break blocks;
- alter villages.

Skyforge should not add a large social-law subsystem simply to prevent dismantling active settlements.

Design implications:

- active civilian structures should not depend on large treasure chests;
- their primary value should be services, trade, location, and observation;
- high-value salvage should be concentrated in abandoned and hostile sites;
- active machinery can be functional but need not contain huge free component stockpiles.

If playtesting reveals destructive exploitation as a major problem, solve that concrete problem later.

## Abandoned sites

### Intended role

Abandoned civilization is the main **salvage and reverse-engineering** layer.

The player should often find:

- broken machinery;
- partial infrastructure;
- components;
- maps;
- fuel remnants;
- old cargo;
- repairable systems;
- route history.

### Salvage philosophy

Prefer:

~~~text
useful part
partial system
damaged component
small stockpile
map / technical clue
~~~

over:

~~~text
complete mature base
fully working endgame aircraft
huge stock of advanced components
~~~

This makes ruins accelerate learning without replacing engineering.

## Repairable infrastructure

Some abandoned sites should support a light restoration loop using ordinary block mechanics.

Examples:

### Beacon station

- tower intact;
- signal/power component missing;
- route identity still visible;
- repair restores a useful navigation point if technically feasible.

### Weather station

- instruments partly intact;
- power/computer missing;
- rebuilding demonstrates weather instrumentation.

### Cargo crane

- mechanically plausible assembly;
- missing gearbox/belt/power source;
- repair can restore local utility.

### Airfield

- runway/approach still useful;
- beacon/fuel/repair systems damaged;
- player may reuse location even without formal settlement restoration.

### Mine

- access remains;
- processing/loading broken;
- useful geology still present.

Do not require a generalized restoration quest framework.

The world blocks themselves are the state.

## Salvage categories by role

### Agricultural

- food;
- seeds;
- tools;
- animal supplies;
- processing components.

### Mining / quarry

- tools;
- rails/carts;
- raw materials;
- mechanical parts;
- survey/navigation equipment.

### Industrial

- Create components;
- processed materials;
- storage/logistics parts;
- power/fuel components.

### Airfield / port

- propulsion/control parts;
- fuel remnants;
- repair components;
- maps;
- cargo supplies.

### Navigation / weather

- CC/computing parts;
- maps;
- compasses;
- sensor/instrument components;
- power supplies.

## Hostile civilization

### Intended role

Hostile sites should combine:

- readable territorial warning;
- deliberate encounter density;
- useful military/industrial salvage;
- intelligence about deeper hostile territory.

The reward is not merely better combat loot.

### Intelligence rewards

Potential discoveries:

- maps to forts/outposts;
- route charts;
- location of an illager stronghold;
- cargo records implied through maps/signs/loot;
- navigation equipment;
- keys/items only where existing mod content already supports them cleanly.

Avoid building a bespoke intelligence inventory system.

### Material rewards

Possible categories:

- weapons/armor from existing content;
- supplies;
- mechanical components;
- fuel;
- industrial material;
- navigation components;
- rare structure-specific loot from supplying mods.

The best hostile rewards should reflect what the site does.

## Hostile progression through geography

A hostile region can naturally provide escalating information:

~~~text
watch post
    -> local map / route clue

extraction camp
    -> supplies / industrial salvage

fort
    -> stronger loot / regional intelligence

air or naval base
    -> advanced mechanical/navigation salvage

regional stronghold
    -> exceptional structure rewards
~~~

No quest chain is required.

The player constructs the chain by exploration.

## Contested / declining sites

Mixed states can provide richer interaction without new systems.

Examples:

### Declining civilian town

- villagers still present;
- some services unavailable;
- damaged infrastructure;
- better salvage than a maintained hub;
- incomplete route coverage.

### Civilian frontier under pressure

- active population;
- fortification;
- limited trade;
- nearby wreck/abandoned sites;
- illager patrol pressure.

### Illager-occupied civilian site

- familiar civilian layout;
- faction population;
- military loot overlays;
- original infrastructure partly reused.

This reuses assets while making history playable.

## Knowledge as progression

One of civilization's most important rewards should be **understanding**.

The player learns:

- which island morphologies make good airfields;
- how mature settlements distribute functions;
- what route infrastructure looks like;
- which machinery supports mining/processing;
- how weather and navigation systems are deployed;
- how cargo networks connect specialized sites.

This knowledge has real value because the player is expected to build analogous systems.

## Recipes and hard unlocks

Do not make civilization a mandatory recipe-unlock system by default.

Create/Aeronautics progression should remain understandable through ordinary crafting/progression unless later balance testing demonstrates that discovery-based unlocks materially improve the game.

Civilization can provide:

- examples;
- components;
- convenience;
- clues;
- rare materials;
- maps;

without being the sole gate to basic engineering.

## Reward tiers

A useful generic reward ladder:

### Ordinary active site

- supplies;
- trade;
- information;
- safe staging.

### Ordinary abandoned site

- moderate salvage;
- partial infrastructure;
- maps/clues.

### Major active hub

- broad services;
- advanced trade/information;
- excellent infrastructure access.

### Dangerous abandoned site

- better salvage;
- environmental/hostile risk.

### Hostile faction site

- combat rewards;
- industrial/military salvage;
- regional intelligence.

### Exceptional landmark

- unique/rare loot from vanilla/mod structure;
- major capability/material reward where appropriate;
- strong knowledge/narrative payoff.

## Avoid reward inflation

Civilization should not make wilderness exploration irrelevant.

Important non-civilization rewards remain:

- ecology;
- rare resources;
- geology;
- phenomena;
- legendary structures;
- exceptional terrain.

Settlements improve connectivity and provide infrastructure, not all valuable content.

## Multiplayer

The first design should remain compatible with ordinary multiplayer behavior.

Avoid civilization mechanics requiring per-player world-state branches.

Useful existing multiplayer support such as Lootr can be retained if chosen for the pack.

Settlement service/navigation infrastructure should remain shared world state where possible.

## Minimal bespoke player-interaction package

Likely justified:

- functional civilization loot tables;
- asset-role/service mapping;
- route/navigation semantics;
- perhaps thin CC/weather/radar adapters;
- small infrastructure layouts.

Not justified yet:

- quest engine;
- reputation;
- dialogue trees;
- custom currencies;
- settlement ownership;
- theft/crime simulation;
- repair UI;
- dynamic contracts;
- NPC pilots;
- strategic faction UI.

## Acceptance examples

### Active mining town

Player can infer mine -> processing -> cargo flow, trade for ordinary supplies, obtain route information, and use landing/storage infrastructure.

PASS without custom quests.

### Abandoned route station

Player finds a dark beacon, damaged machinery, old map, modest salvage, and a physically useful stopping point.

PASS if the site teaches what a functioning station would do.

### Illager fort

Player sees faction logistics, fights deliberate FACTION population, recovers supplies/components, and obtains clues pointing deeper into hostile territory.

PASS without a quest marker.

### Regional civilian hub

Player can refuel/resupply/navigation-stage through existing mechanics and observe mature distributed infrastructure.

PASS even if no bespoke "hub menu" exists.

## Acceptance principles

1. Active civilization primarily provides services and information.
2. Abandoned civilization primarily provides salvage and reverse-engineering.
3. Hostile civilization primarily provides deliberate risk, material rewards, and territorial intelligence.
4. Existing Minecraft/mod interactions are preferred to custom UI.
5. Civilization teaches progression but does not automatically gate all engineering recipes.
6. Loot reflects site function.
7. Repair uses ordinary blocks/mechanics where practical rather than a restoration quest system.
8. Active-site ownership/theft is not a v0.1 system.
9. Knowledge and navigation are legitimate rewards.
10. The player should be able to engage deeply with civilization without Skyforge becoming an RPG mod.
