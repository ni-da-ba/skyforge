# Structures, Dungeons, Settlements, and Realization Modes

**Snapshot:** 2026-09-05  
**Status:** Working design direction.

## Proven current structure foundation

Accepted SF-IMP work has already demonstrated:

- native Minecraft structure candidate interception;
- generic support evaluation;
- piece-aware floor-contact footprints;
- bounded fill-only foundation accommodation;
- preservation of native structure identity and weighted fallback behavior;
- rejection of unsupported/multi-volume starts;
- proof/rejection of detached structure pieces beneath an island underside.

See:

- [SF-IMP-0045](../architecture/Skyforge_SF-IMP-0045_Structure_Candidate_Admission.md)
- [SF-IMP-0047](../architecture/Skyforge_SF-IMP-0047_Piece_Aware_Structure_Footprints.md)
- [SF-IMP-0046 acceptance](../reviews/SF-IMP-0046-terrain-accommodation-acceptance.md)
- [SF-IMP-0050 acceptance](../reviews/SF-IMP-0050-detached-underside-contradiction-acceptance.md)

Important boundary:

After [SF-IMP-0052 terrain-domain isolation](../reviews/SF-IMP-0052-terrain-domain-isolation-acceptance.md), base-world structures no longer observe Skyforge terrain. Structures have not yet been restored as a production exact-volume island population phase.

The current generic structure work also explicitly defers:

- multi-elevation structure support;
- underground occupancy envelopes;
- excavation;
- roads/bridges/stairs;
- semantic settlement adaptation;
- exact per-block structure masks.

## Canonical realization modes

### 1. Surface-supported

Existing island -> structure.

Examples:

- towers;
- huts;
- temples;
- forts;
- shrines;
- compact ruins.

Current implementation readiness: highest.

### 2. Bounded accommodation

Existing island -> small fill-only foundation adaptation -> structure.

Useful when a suitable site exists but needs modest support.

Current implementation foundation: already proven for native structures.

### 3. Settlement/network realization

Existing island/cluster -> multi-building settlement plus roads/docks/infrastructure.

Examples:

- villages;
- airfields;
- ports;
- industrial settlements;
- faction compounds.

Needs semantic settlement adaptation rather than treating each building independently.

### 4. Subsurface-embedded

Existing exact island volume -> reserve/fit underground occupancy -> dungeon/mineshaft/Stronghold/Trial Chamber.

Requires a new underground occupancy/excavation contract.

### 5. Cliff/underside-attached

Cliff or underside morphology -> attached structure.

Examples:

- hanging docks;
- cranes;
- cliff monasteries;
- mine mouths;
- underside ruins;
- lift infrastructure.

Requires anchor/orientation/support semantics beyond horizontal surface footprints.

### 6. Detached

Open air or water -> independently supported structure.

Examples:

- balloons;
- airships;
- floating platforms;
- vessels.

Requires explicit detached-structure semantics rather than treating unsupported geometry as an error.

### 7. Structure-seeded terrain

Required or exceptional structure intent -> author a suitable island/terrain envelope around it.

This is a first-class world-authorship mode, not a fallback hack.

Use when a required structure cannot simply be rejected.

## Structure importance

Candidate importance classes:

```text
OPTIONAL
PREFERRED
REQUIRED
PROGRESSION_CRITICAL
```

Optional structures may fail admission.

Required/progression-critical structures must either:

- find an already suitable Skyforge site; or
- cause suitable terrain to be authored around their required location.

## Structure terrain envelope

Future generic abstraction should express what a structure requires from terrain.

Candidate fields:

```text
requiredFootprint
requiredInteriorVolume
requiredCover
allowedSurfaceExposure
requiredSupport
accessRequirements
environmentalContext
```

Examples:

### Watchtower
- small surface footprint;
- no cover;
- visible surface exposure.

### Stronghold
- very large interior volume;
- substantial cover;
- reliable progression location.

### Trial Chamber
- large interior envelope;
- moderate cover;
- structure-bound encounter mechanics.

### Ancient City, buried
- enormous cavern/interior volume;
- Deep Dark semantic context;
- substantial cover.

### Ancient City, exposed
- enormous surface footprint;
- intentional visibility;
- exceptional site.

## Vanilla/progression structures

### Strongholds

Treat as progression-critical.

Preferred policy:

> Do not move the Stronghold to a convenient island; preserve required/vanilla-compatible coordinate topology where practical and author a suitable island around it when necessary.

This preserves deterministic progression/navigation behavior while allowing the world to remain a sky-island world.

A Stronghold-bearing island should have enough actual geological mass to contain the structure rather than a thin shell.

### Trial Chambers

Preferred mode: subsurface-embedded.

If no suitable volume exists, generate a sufficiently thick Trial-bearing island.

Trial spawner behavior belongs to the structure/encounter population domain, not ambient hostile budgets.

### Ancient Cities

Support two modes:

1. buried city inside a large Deep-Dark-bearing island;
2. rare exposed/structure-seeded city on top of an enormous island.

The exposed mode is intentionally exceptional and can create a major Distant Horizons destination.

Island-local depth, not absolute world Y, should eventually drive Deep Dark semantics.

### Mineshafts

Prefer resource/history-derived placement:

```text
ore-rich island
+ historical extraction
+ abandonment
-> mineshaft
```

### Villages

Settlement/network mode.

Require enough usable area, water/agricultural suitability, infrastructure intent, and appropriate civilization pressure.

### Ocean Monuments

Future water-volume realization on ocean-island types.

### Ruined portals / temples / huts

Mostly surface-supported or cave-contextual.

## Structure content audit

### Illager Structures — leading faction structure library

Strong fit because it includes:

- forts/towers/camps;
- stables/smeltery/extraction;
- balloon tower/floating outpost;
- maritime structures;
- cold expedition sites;
- ruins/monastic sites.

Use its assets, but let Skyforge faction semantics own placement.

### YUNG's Better Dungeons — strong candidate

Native 1.21.1 NeoForge.

Useful dungeon families include zombie/skeleton/spider-oriented complexes and other expanded dungeon content.

Important integration detail: some structures use structure spawn overrides with large hostile pack ranges. These must count as STRUCTURE population rather than ambient cave population.

### YUNG's Better Mineshafts — likely

High conceptual fit with Skyforge geology and historical extraction.

Requires subsurface occupancy integration.

### YUNG's Better Strongholds — likely later

Good progression realization if vanilla-compatible Stronghold navigation remains.

### YUNG's selected temples/huts/monuments — selective

Adopt individually based on whether their gameplay/visual language fits the intended regional destination.

### Towns & Towers — strong civilian settlement prototype

Potential leading village asset source.

Use civilian settlement content selectively; avoid redundant pillager-worldgen overlap with Illager Structures.

### Explorify or Structory — A/B for ordinary landmarks

Skyforge needs mundane/low-intensity landmarks, not only dungeons and megastructures.

Useful roles:

- guideposts;
- caches;
- watchtowers;
- shrines;
- small ruins;
- isolated houses;
- small farms;
- low-stakes historical sites.

Choose one after visual/world-density testing rather than automatically stacking both.

### Create Aeronautics Structures — likely pending license/distribution verification

Highly aligned source for:

- derelict aircraft;
- balloons;
- engineering ruins;
- wrecks.

Use as historical/traffic/weather-risk evidence rather than unrestricted structure spam.

### Repurposed Structures — reserve library

Useful for regionally appropriate variants of familiar Minecraft structures. Promote only specific families when needed.

### When Dungeons Arise — surgical only

Potentially useful exceptional structures, especially airborne/naval/large ruins.

Do not allow unrestricted megastructure generation to compete with Skyforge terrain composition.

### Dungeons & Taverns — reserve

Interesting structure/navigation content, but the 1.21.1 line is not the preferred foundation while the more active feature line targets later versions.

### CTOV — alternate settlement reserve

Use only if Towns & Towers fails visual/gameplay testing.

### IDAS — omit initially

Skyforge itself should be the integration layer rather than importing another broad cross-mod structure ecosystem.

## Structure play pattern

Skyforge changes the normal Minecraft structure experience from:

```text
walk -> notice -> enter
```

to:

```text
see silhouette
-> identify
-> alter route
-> plan approach
-> land / board / infiltrate
-> explore
```

Structures should therefore have meaningful distance signatures.

Examples:

- tower -> vertical silhouette;
- settlement -> lights/smoke;
- airfield -> cleared plateau/beacon;
- illager fort -> banners/towers/balloon;
- industry -> cranes/stacks;
- wreck -> broken craft on cliff/underside.

## Aviation-aware dungeon design

A dungeon can have three spatial phases:

1. **Approach** — weather, route, defenses, landing difficulty.
2. **Access** — gate, roof landing, cave mouth, cliff entrance, underside approach, damaged wall.
3. **Interior** — normal dungeon/trial/boss gameplay.

Different aircraft should solve access differently.

## Structure rewards

Not every structure needs treasure.

Possible values:

- shelter;
- trade;
- maps/information;
- weather observations;
- repair;
- fuel;
- docking;
- navigation infrastructure;
- environmental storytelling;
- salvage;
- capability unlocks.

The structure should answer why the player cared enough to travel there.
