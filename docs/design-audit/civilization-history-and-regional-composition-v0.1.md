# Civilization History and Regional Composition v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.

## Core principle

Skyforge should author the present consequences of a small, explainable civilizational history rather than simulate centuries of social change.

History exists to explain present geography:

- why a settlement is here;
- why nearby islands specialize;
- why a route exists;
- why infrastructure is active, damaged, abandoned, or repurposed;
- why civilian and illager influence form coherent regional patterns;
- why salvage and loot appear where they do.

## Minimal history grammar

A useful first-generation site history is:

~~~text
founding cause
-> optional development
-> optional disruption
-> current state / successor use
~~~

Two to four causal steps are enough.

Candidate founding causes:

~~~text
AGRICULTURAL_VALUE
RESOURCE_EXTRACTION
TRADE_ROUTE
TRANSPORT_NODE
STRATEGIC_POSITION
SAFE_HARBOR
WEATHER_NAVIGATION
RELIGIOUS_ISOLATION
REFUGE_FRONTIER
MILITARY_CONTROL
~~~

Candidate disruptions:

~~~text
RESOURCE_DECLINE
ROUTE_SHIFT
SEVERE_WEATHER_RISK
INFRASTRUCTURE_FAILURE
CONFLICT
ILLAGER_PRESSURE
ISOLATION
ECOLOGICAL_CHANGE
CATASTROPHIC_EVENT
UNKNOWN
~~~

Candidate current states:

~~~text
ACTIVE
MAINTAINED
FRONTIER
DECLINING
ABANDONED
RUINED
RECLAIMED
REPURPOSED
OCCUPIED_HOSTILE
CONTESTED
~~~

The cause must affect visible world state rather than exist only as hidden metadata.

## History should produce traces

Preferred evidence order:

1. spatial organization;
2. structure condition;
3. functioning or broken machinery;
4. route state;
5. ecology;
6. loot;
7. signs, maps, or books only where useful.

Example abandoned mining network:

~~~text
inactive mine
+ idle processing
+ half-empty warehouse
+ dark beacon
+ abandoned worker housing
+ returning vegetation / scavengers
~~~

The player does not need a generated chronicle to understand what happened.

## Province and route composition

Civilization should be planned as a sparse graph:

~~~text
ProvinceCivilizationPlan
    |
    +--> regional hubs
    +--> villages / towns
    +--> resource sites
    +--> route stations
    +--> frontier sites
    +--> abandoned historical sites
    +--> hostile / contested nodes
    |
    +--> route relationships
~~~

Routes are first-class civilizational geography.

A maintained route can attract:

- beacons;
- weather stations;
- storage;
- fuel/repair sites;
- small markets;
- military watch;
- wrecks and older abandoned route infrastructure.

Infrastructure therefore creates geography beyond the original settlement or resource site.

## Regional archetypes

### Wild interior

No active network. Occasional historical trace, isolated site, or abandoned extraction.

### Frontier corridor

One maintained route with sparse farms, mines, stations, or outposts. Strong dependence on distant hubs.

### Settled corridor

Several connected settlement clusters with maintained navigation and storage.

### Mature regional network

One hub with feeder towns, specialized extraction/agriculture/industry, and redundant transport/navigation.

### Industrial belt

Resource-rich geography with mines, processing, storage, cargo infrastructure, and concentrated habitation.

### Declining / abandoned region

Old route graph remains legible but many nodes are dark, damaged, empty, or ecologically reclaimed.

### Contested frontier

Civilian and illager influence overlap through fortified settlements, abandoned farms, damaged routes, watch posts, wrecks, and hostile extraction sites.

No live strategic war simulation is required.

## Hub-and-feeder hierarchy

Do not scatter many equal settlements randomly.

Prefer:

~~~text
regional hub
    |
    +--> village
    |     +--> farm
    |
    +--> mining town
    |     +--> mine
    |     +--> quarry
    |
    +--> route station
    |
    +--> industrial site
~~~

Smaller settlements should lack some services and depend on the regional network.

That incompleteness teaches why logistics matters.

## Abandonment can propagate

A declining function can explain several linked ruins:

~~~text
mine closes
    |
    +--> processing declines
    +--> cargo route loses traffic
    +--> route station is abandoned
    +--> worker settlement shrinks
~~~

Skyforge authors the resulting dependency history directly. It does not simulate the transition.

## Successor use

Old sites can be reused:

~~~text
NONE
CIVILIAN_REUSE
ILLAGER_OCCUPATION
SCAVENGER_SITE
ECOLOGICAL_RECLAIMED
MONASTIC_ISOLATION
SALVAGE_CAMP
~~~

This creates variety with the same base structure assets.

One warehouse can be:

- active civilian storage;
- abandoned ruin;
- illager depot;
- scavenger salvage site.

## Contested borders

Avoid clean political map lines.

A transition may look like:

~~~text
maintained town
-> fortified farm
-> damaged route station
-> abandoned beacon
-> wreck / conflict evidence
-> illager watch post
-> extraction camp
-> fort
~~~

This gives territorial depth without bespoke border UI.

## Illager expansion

Illagers use the same historical primitives.

Founding causes can include:

- strategic position;
- extraction;
- route seizure;
- military control.

Growth may yield:

~~~text
watch post
-> camp
-> fortified storage
-> extraction
-> balloon / air base
-> regional stronghold
~~~

Illagers may occupy and repurpose civilian sites instead of receiving a unique asset for every function.

## Reuse-first realization

Civilization semantics can be bespoke while assets remain largely existing content.

### Active civilian settlement vocabulary

Use primarily:

- vanilla villages and villagers;
- Towns & Towers as the leading expanded village prototype.

### Minor sites and historical traces

A/B:

- Explorify for direct vanilla-friendly farmsteads, guideposts, caches, watchtowers, taverns, campsites, and ruins;
- Structory where atmospheric ruins and light implied history are preferable.

Prefer one primary library rather than stacking both automatically.

### Civilian defense

Default:

- iron golems;
- lighting/maintenance;
- semantic ambient-threat suppression.

Optional prototype only if playtesting shows a gap:

- Guard Villagers.

Do not build custom guard AI first.

### Illager realization

Use:

- vanilla illagers;
- Illager Structures;
- Friends & Foes;
- It Takes a Pillage.

Reuse captured civilian infrastructure where possible.

### Functional infrastructure

Use existing blocks from:

- Create;
- Create Aeronautics/Sable ecosystem;
- Supplementaries;
- Farmer's Delight / Slice & Dice;
- selected storage, power, and navigation systems.

Skyforge bespoke work should focus on small functional layouts and semantic assembly.

## Data-driven state variants

A single base layout should often support multiple states through processors, loot, details, population, and damage.

~~~text
route station
    |
    +--> ACTIVE
    |     lights + supplies + civilians
    |
    +--> DECLINING
    |     reduced supplies + damage
    |
    +--> ABANDONED
    |     broken equipment + salvage + vegetation
    |
    +--> ILLAGER_OCCUPIED
          banners + faction population + military loot
~~~

This is one of the highest-leverage ways to reduce bespoke content work.

## Bespoke work that appears justified

Necessary semantic systems:

- province civilization context;
- cluster settlement plan;
- island functional roles;
- route graph;
- maintenance/history state;
- coarse needs/capacities;
- faction/control state;
- structure/site-role matching.

Likely small layout library:

- airfields;
- cliff docks;
- mooring towers;
- weather stations;
- route beacons;
- radar/navigation posts;
- cargo transfer sites.

These should use existing block palettes wherever possible.

## Do not build initially

Avoid:

- new villager AI;
- custom guard AI;
- dynamic market economy;
- quest system;
- reputation system;
- diplomacy;
- live strategic war;
- custom settlement-builder NPCs;
- large civilization-exclusive block sets;
- persistent global autonomous aircraft simulation.

Each of these requires a demonstrated gameplay gap before it earns implementation cost.

## Tuning boundary

Civilization density, hub spacing, route density, abandoned-site frequency, and hostile territory frequency are later experience-tuning variables.

They should not constrain the architecture now.

## Acceptance principles

1. History explains present geography.
2. Histories remain short and spatially legible.
3. Routes and hubs create secondary settlement geography.
4. Functional decline can explain linked abandonment.
5. Existing assets are reused across active, ruined, and occupied states.
6. Faction borders appear through evidence gradients.
7. Civilization semantics may be bespoke; most buildings, NPCs, and machinery should not be.
8. No dynamic simulation is added without clear player-facing value.
9. Density remains a later tuning parameter.
10. Players should be able to reconstruct much of a region's story from terrain, infrastructure, ecology, and salvage.
