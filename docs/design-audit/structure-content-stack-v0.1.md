# Structure Content Stack v0.1

**Snapshot:** 2026-09-30  
**Status:** PROVISIONALLY CLOSED. Production lock remains contingent on pinned-pack runtime, visual-language, licensing/distribution, and exact-volume integration acceptance.

## Decision

Skyforge has sufficient third-party and vanilla structure/content vocabulary for V1. Do not continue broad structure-mod discovery before integration unless a concrete semantic role remains unrealized.

The retained approach is:

~~~text
SKYFORGE
  owns semantic role
  owns site planning
  owns density
  owns history / maintenance state
  owns ownership / faction state
  owns route / resource relationships
  owns provenance
  owns terrain accommodation / anchor modes

MODS / VANILLA
  supply buildings
  supply templates
  supply machinery
  supply local entities
  supply loot hooks
  supply vehicles / prefabs
  supply decorative vocabulary
~~~

## Provisional stack

### Active civilian civilization
- Towns & Towers — leading civilian village / settlement vocabulary.
- Vanilla villages / professions / golems — local civilian simulation and fallback.

### Frontier / history / low-intensity sites
- Structory and/or Explorify — retain through integration A/B; they may both survive if their role split proves real under Skyforge density control.
- Moog's Voyager Structures — reserve asset quarry only; promote individual structures when a concrete role remains.

Leading role split:

~~~text
Explorify
  SUPPLY_CACHE
  GUIDEPOST / ROUTE_MARKER
  WATCH_POST
  FARMSTEAD
  CAMPSITE
  practical frontier sites

Structory
  HISTORICAL_SITE
  RUIN
  FIRETOWER
  COTTAGE / STABLE
  GRAVEYARD
  atmospheric abandonment/history
~~~

### Industrial / technical structures
- Create: Structures Arise — strong provisional keep.

Leading reusable assets include:
- fluid tanks -> fuel depots / petroleum storage / airfield tank farms;
- crusher/crane sites -> quarry / mine / cargo-transfer infrastructure;
- containers -> freight staging / warehouse adjuncts;
- mine train -> extraction history / logistics;
- lost train station -> abandoned route-network evidence;
- windmill -> agricultural processing;
- airborne / pillager assets -> selective faction/history use.

Native generation is not semantic authority.

### Aviation history / wrecks / airfield assets
- Creat Aeronautics Structures (MIT project) — strong provisional keep.
- Aeronautical Explorations — strong narrow external candidate for abandoned airstrip / ruined petrol-station roles.
- Create Aeronautics Structures (ARR project) — reserve only if the preferred aviation sources leave a concrete gap.
- Abandoned Structures: Aviation — reserve; currently redundant.

### Dynamic aviation realization
- Create Aeronautics Discovery — strong integration prototype.

Use its supported physical-realization layer where it passes pinned-pack testing:
- custom NBT aircraft prefabs;
- Sable physics assembly;
- custom datapack flyovers;
- structure-linked permanent patrols;
- crew/entity export;
- straight / altitude / orbit / avoidance / terrain autopilot goals.

Authority boundary:

~~~text
Skyforge civilization / faction / mission semantics
        ↓
thin adapter
        ↓
Create Aeronautics Discovery physical realization
        ↓
Sable / Aeronautics
~~~

Discovery may own prefab assembly and local flight realization. It may not independently own world-level civilization, faction geography, mission selection, or strategic traffic semantics.

### Navigation / weather / radar structures
- Radio Towers Lite — strong narrow MIT candidate for tower shells.

Preferred realization:

~~~text
tower shell
  + Create Radars
  + CC:Tweaked
  + Avionics / communications
  + A4MC / Skyforge weather instrumentation
  + retained power/storage
  =
BEACON_NAVIGATION
WEATHER_STATION
RADAR_POST
RADIO_RELAY
~~~

Do not add a dedicated observatory/communications structure ecosystem unless integration demonstrates a remaining gap.

### Hostile civilization
- Illager Structures — primary hostile-civilization architecture library.
- Town/civilization state determines whether assets are active, occupied, abandoned, industrial, military, etc.
- Faction aircraft are realized separately through the retained aviation substrate.

### Mythic / ecological / exceptional
- Ice & Fire CE — selected roosts, caves, lairs, nests, maritime/mythic sites, and related threat-signaling structures.
- Mowzie's Mobs — selected exceptional encounter sites where useful.
- Bosses of Mass Destruction — focused legendary destinations.
- When Dungeons Arise — surgical exceptional-asset quarry only; no unrestricted megastructure worldgen.

### Subsurface / progression
- YUNG's Better Mineshafts — leading historical extraction / subsurface mine candidate.
- YUNG's Better Dungeons — leading expanded dungeon candidate.
- selected YUNG stronghold / temple / hut / monument families only when they fill a specific retained role.
- vanilla progression-critical structures remain guaranteed under Skyforge structure-seeded terrain policy.

### Future maritime stack
Defer production lock until ocean-island gameplay is authored.

Current candidates:
- Currents of Trade;
- Little Logistics;
- Hopo Better Underwater Ruins;
- YUNG's Better Ocean Monuments;
- Ice & Fire Sea Serpents as threat content.

## Residual Skyforge-owned layouts / realization grammars

No broad bespoke building catalogue is justified.

Remaining bespoke work is primarily world-aware composition:

~~~text
AIRFIELD_SITE_PLAN
CLIFF_DOCK_SITE_PLAN
UNDERSIDE_SITE_PLAN
MOORING / ROUTE_BEACON_SITE_PLAN
WEATHER / RADAR FUNCTIONAL_OVERLAY
~~~

### Airfield

The content gap is effectively solved. Skyforge owns:
- runway / landing-surface geometry;
- clear approach corridors;
- relation between hangar, fuel, maintenance, cargo, navigation, and settlement roles.

Reuse:
- Aeronautical Explorations airstrip / petrol-station vocabulary where suitable;
- Structures Arise fluid tanks / containers / workshops;
- Create / Aeronautics machinery;
- Radio Towers Lite or other retained tower shells;
- parked or Discovery-realized craft.

### Cliff dock / hanging port

This is an anchor/orientation/support problem, not a missing-mod problem.

Reuse:
- Create cranes / gantries;
- Structures Arise container / crusher-crane vocabulary;
- Supplementaries ropes/chains/detail;
- retained storage / warehouse assets;
- Aeronautics mooring context.

Skyforge supplies cliff-anchor selection, local frame, support, cargo clearance, and route semantics.

### Underside installation

Also a realization-mode problem.

Potential roles:
- mine mouth;
- salvage platform;
- military/watch site;
- ancient ruin;
- maintenance access;
- hanging industrial equipment.

Skyforge must provide underside anchor/orientation logic and populate it with retained assets.

## Progression and acquisition policy

### Capability-gated, not recipe-gated

Skyforge explicitly allows:
- salvage;
- repair;
- capture;
- trade;
- discovery;

to bypass parts of the normal manufacturing sequence.

Rule:

> Structures and world assets may shortcut acquisition, but should not trivially shortcut the supporting capability ecosystem.

A player may acquire an advanced machine before being able to manufacture it, provided meaningful capability was required to reach, capture, repair, recover, or operate it.

This supports multiple player archetypes:

~~~text
engineer
  builds the aircraft

explorer
  finds and restores one

fighter
  captures one

trader
  acquires components / assets

logistician
  keeps the machine supplied and operational
~~~

Not every player must become an expert engineer.

### Capital acquisition vs operating economy

Capturing or salvaging an aircraft may bypass capital cost without bypassing:
- fuel;
- ammunition;
- repair;
- replacement components;
- navigation;
- logistics;
- infrastructure support.

Avoid turnkey starter-area assets that erase the first-flight capability transition without meaningful effort.

## Generated machinery doctrine

Generated machinery should obey the same world rules as player machinery.

Preferred states:

1. **Functional** — real retained machinery actually works.
2. **Plausibly incomplete** — coherent machine with damaged/missing components.
3. **Static evidence** — only when live function would be too fragile/expensive, while remaining mechanically plausible.

Avoid civilization-only fake technology that visually imitates Create/Aeronautics while violating their logic.

## Asset-level curation

Dependency retention does not imply retention of every bundled structure.

Per-asset outcomes may be:

~~~text
KEEP_AS_IS
KEEP_WITH_PALETTE_PASS
KEEP_WITH_FUNCTIONALIZATION
KEEP_WITH_MAJOR_ADAPTATION
REFERENCE_ONLY
DISABLE
~~~

Example:
- retain Structures Arise as a dependency;
- retain its fluid tank and lost station;
- disable a visually or semantically unsuitable castle.

Do not reopen the whole dependency decision for every individual asset.

## Content richness vs realized density

Installed catalogue size is not realized site density.

Rule:

> More retained assets expand Skyforge's choice set; they do not authorize more structures per province.

Skyforge settlement/history/threat/geology planning chooses a sparse, meaningful subset.

Avoid modpack-worldgen soup.

## Structure provenance

Where practical, realized sites should preserve semantic provenance sufficient for later systems to reason about them.

Conceptual fields:

~~~text
siteId
semanticRole
sourceAsset
civilization / faction
maintenanceState
historyState
routeContext
resourceContext
~~~

Exact storage format is implementation-deferred.

Provenance enables:
- traffic support;
- restoration;
- occupation;
- capture;
- loot/state transitions;
- debugging / telemetry.

## Persistence and consequences

The architecture should leave room for consequential player action without requiring a grand-strategy simulator.

Examples:
- destroyed faction air-support site -> lower local patrol capability;
- repaired beacon -> restored navigation service;
- restored fuel depot -> route-support capability;
- captured aircraft -> no longer available at source;
- occupied/cleared structure -> changed local faction state.

These consequences may be implemented incrementally, but structure realization should not destroy the information needed to support them.

## Visual-language gate

Visual coherence is deferred to a dedicated audit, but is a production acceptance gate.

Semantic usefulness alone does not guarantee final retention.

Each retained structure family must eventually pass:
- block/material palette fit;
- technology-language fit;
- scale;
- silhouette / Distant Horizons readability;
- detail density;
- fantasy register appropriate to role;
- age/maintenance-state compatibility;
- terrain integration;
- faction identity;
- mechanical honesty.

Visual unity does not require sameness; mythic, civilian, industrial, hostile, and historical structures may differ intentionally.

## Integration gates

Before production lock:

1. Pin exact 1.21.1 NeoForge artifacts and dependency graphs.
2. Validate selected Structures Arise assets on the pinned stack.
3. Prove stock generation can be disabled/subordinated where required.
4. Validate Aeronautics structure assets in exact-volume worlds.
5. Validate Discovery custom prefab assembly and local autopilot on the pinned Sable/Aeronautics stack.
6. Demonstrate at least one civilian and one hostile structure-linked aircraft realization.
7. Test Aeronautical Explorations airstrip/petrol-station content and governance.
8. Test Radio Towers Lite as functional navigation/weather/radar shells.
9. Resolve Structory / Explorify through actual role/visual testing.
10. Prototype an active airfield from retained assets.
11. Prototype one cliff dock.
12. Prototype one underside installation.
13. Verify the latter two require new placement/orientation machinery rather than new content assets.
14. Audit visual coherence before final dependency lock.
15. Keep structure density/provenance observable.

## Closure rule

> Do not perform another broad structure-mod search before integration unless a concrete semantic role remains unrealized.

The remaining hard structure work is primarily **site planning, semantic composition, anchoring, state, and integration**—the parts Skyforge should own.
