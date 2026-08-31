# Skyforge Current Runtime Architecture

**Snapshot:** 2026-08-30  
**Accepted through:** SF-IMP-0028  
**Active:** SF-IMP-0029

This document is a concise current-state handoff. Earlier ADRs remain authoritative for their individual accepted contracts.

## Pipeline

```text
semantic island intent
    -> morphology provider / provider blend
    -> independently compiled suspended island volume
    -> deterministic island-group planning
    -> hierarchical archipelago planning
    -> bounded world catalog
    -> spatial region/tile query
    -> deterministic density realization
    -> backend-neutral terrain semantics (SF-IMP-0029, pending acceptance)
    -> minimal adapter-visible Skyforge context (next)
    -> backend-native biome/environment/material policy
    -> concrete backend realization
```

## Predecessor inheritance

Recovered Aetherial Islands / Aetherial Companion artifacts from the old Stab City test modpacks confirm that many current Skyforge concerns are not speculative. The predecessor lineage already encountered explicit island/cluster identity, stacking control, ocean-frequency tuning, structure fitting, biome adaptation, ore compatibility, registry drift, starter search and extensive diagnostic needs.

The modern inheritance rule is:

> Keep Companion's questions. Keep its compatibility lessons. Keep its diagnostic instincts. Do not keep its need to fight the terrain generator.

Aetherial Companion had to construct semantic descriptors, caches, authority decisions, masks, reducers and gates around an upstream implicit density generator. Modern Skyforge instead owns the semantic objects and the density geometry directly.

The detailed recovery record is `docs/history/Aetherial_Islands_Companion_Lessons_Learned.md`.

## Accepted runtime invariants

### Island geometry

- Island geometry is represented by backend-neutral procedural graphs.
- Suspended volumes have explicit upper and underside surfaces plus exact signed-density intersection.
- Built-in and custom morphology providers coexist behind the public provider contract.
- Pairwise provider hybrids blend structural fields before rebuilding the common volume.
- Detail and provider-aware secondary morphology compose without changing the accepted planform sign.

### Spatial hierarchy

- Individual islands remain independently compiled objects.
- Group planning arranges islands as chains or clusters without unioning them into one giant density graph.
- Archipelago planning arranges complete child groups as Hub or Arc regional structures.
- Explicit member and group reservations provide deterministic separation contracts.
- Stable hierarchical seed derivation preserves identity at every level.
- Composition owns occurrence and relationships; morphology owns form.

### World/runtime boundary

`skyforge-world` is the backend-neutral runtime module.

An accepted archipelago compiles into `SkyIslandWorldCatalog`, containing one `SkyIslandWorldVolume` per independent island. Each entry carries:

- stable nested world identity;
- conservative world-space query bounds;
- the compiled backend-neutral island graph set.

Backends call `query(WorldBounds)` to obtain only volumes that may affect a region.

The first catalog deliberately uses a deterministic linear scan. Spatial acceleration is an internal optimization and is not part of the public semantic contract.

This random-access semantic query model is the mature successor to Companion's `IslandField` idea: ask which semantic island/world object is relevant here rather than reconstructing identity from density after the fact.

### Provider-safe bounds

Horizontal world-query bounds use the explicit member reservation already accepted by group planning.

Vertical query bounds are explicit conservative reservations. They are not inferred from descriptor upper/underside parameters because arbitrary morphology providers are not required to obey built-in vertical formulas.

### Tiled realization

The accepted reference tiled backend partitions one global lattice by integer sample indexes.

Each tile:

1. computes its closed world-space query bounds;
2. queries the world catalog independently;
3. evaluates only returned compiled density graphs;
4. owns a disjoint range of global lattice indexes.

Local acceptance demonstrated byte-identical monolithic and tiled occupancy, including irregular edge tiles and an island crossing a tile seam.

This means Skyforge geometry is compatible with live, preloaded, or hybrid chunk/region realization. The production policy is intentionally deferred until concrete backend performance is measured.

## Active terrain-semantic boundary (SF-IMP-0029)

The active branch adds continuous backend-neutral terrain roles:

```text
AIR
EDGE_SHELL
SURFACE_MANTLE
UNDERSIDE_SHELL
SHALLOW_INTERIOR
DEEP_MASS
```

The authoritative compiled density remains the solid/air decision. Solid points are classified from continuous distances to the compiled upper and underside surfaces, not from neighboring voxel occupancy.

This keeps semantic meaning independent of Minecraft chunk dimensions or sampling resolution.

The prepared local acceptance requires:

- all terrain roles to be demonstrated on real compiled geometry;
- tiled and monolithic semantic arrays to match exactly;
- semantic occupancy projection to remain byte-identical to accepted density occupancy;
- semantic bands to survive tile seams;
- visual close-specimen and regional-Hub evidence.

## Backend-context principle

The next boundary must remain deliberately smaller than a general climate or biome system.

Skyforge owns concepts required to express backend-independent Skyforge behavior. Backends remain authoritative for concepts native to them.

Therefore the next adapter seam should expose only the Skyforge information a concrete integration demonstrates that it needs, potentially including:

- terrain semantic;
- world position;
- stable world-volume identity;
- hierarchy metadata only where a real behavior requires it.

Skyforge should **not** add generic temperature, humidity, rainfall, ecology, continentalness, or similar descriptors merely to duplicate a backend's environmental model.

Likewise, no broad backend-neutral `MaterialIntent` taxonomy should be introduced until a concrete integration demonstrates a transformation that is genuinely shared across backends.

A Minecraft-facing adapter may combine `SkyIslandTerrainSemantic` directly with Minecraft-native biome/environment information to select concrete block states.

This contract is recorded in `ADR-0034-minimal-backend-context-seam.md`.

## Suitability and compatibility are staged backend requirements

The predecessor proved two important requirements that should arrive **after** the first chunk/material bridge rather than being forgotten.

### Semantic suitability

Biome validity does not guarantee that floating terrain can physically support a vanilla or modded feature/structure. Before broad structure/feature integration, Skyforge should expose geometry-derived suitability such as terrain thickness, surface/underside distance, available surface and other continuously defined geometric facts.

The concrete backend can combine those facts with native Minecraft feature/structure rules.

### Registry-aware optional integration

Optional compatibility must probe the active registry/tags/capabilities before referencing third-party keys. Mod presence alone is not sufficient evidence that a historical registry ID still exists.

Compatibility adapters belong on the backend side and must fail gracefully.

## Public-control quality

The predecessor also demonstrated that a friendly parameter name is not automatically meaningful control. Public composition parameters should be monotonic where appropriate, locally influential where possible, and statistically testable on deterministic corpora.

Implementation details such as retry counts must not become de facto world-design controls.

## Explainability

Skyforge's existing evidence culture should continue through the concrete backend. During development, a block/biome/feature decision should be traceable through backend policy to Skyforge terrain semantic, suitability, world-volume identity, compiled recipe/field provenance and descriptor/composition ancestry.

## Module ownership

- `skyforge-kernel` — graph representation, coordinates, signals, validation, reference evaluation.
- `skyforge-model` — semantic descriptors and descriptor validation.
- `skyforge-recipes` — deterministic descriptor/provider/group/archipelago compilation and planning.
- `skyforge-world` — bounded runtime catalog, spatial queries, tiled realization, terrain semantics.
- `skyforge-reference` — evidence generation, reference providers, sampling, metrics, visual review artifacts.

Minecraft/NeoForge APIs remain outside these core modules.

## Deliberately deferred

The following should not be promoted into core contracts before evidence requires them:

- generalized N-way morphology mixtures;
- another spatial hierarchy level above archipelagos;
- Minecraft block IDs or block states in `skyforge-world`;
- a parallel Skyforge climate simulator;
- speculative backend-neutral material taxonomies;
- final biome/ecoregion ownership beyond demonstrated Skyforge-specific needs;
- broad structures/vegetation/caves/ores/fluids integration before suitability exists;
- historical Companion compatibility hacks without reproducing the underlying problem in Skyforge;
- provider-certified exact spatial bounds;
- production spatial index implementation;
- persistent world-plan/cache serialization;
- final live versus preload versus hybrid generation policy.

## Near-term sequence

1. Locally validate and visually review SF-IMP-0029 terrain semantics.
2. SF-IMP-0030: prove the smallest adapter-visible Skyforge context seam; do not build a climate system or broad material taxonomy.
3. SF-IMP-0031: add the first concrete Minecraft-facing chunk/voxel adapter and let actual integration requirements discover any missing abstractions.
4. Add minimal geometry-derived suitability before broad vanilla/modded feature and structure integration.
5. Add registry probing and isolated optional-mod adapters before shipping compatibility assumptions.
6. Benchmark identical deterministic worlds under live, preloaded, and hybrid realization.
7. Choose production caching/spatial-index policy from measured evidence.
8. Enrich environmental/material semantics only where concrete backend-neutral Skyforge behavior demonstrates the need.
