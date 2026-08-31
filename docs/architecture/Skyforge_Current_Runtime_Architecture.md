# Skyforge Current Runtime Architecture

**Snapshot:** 2026-08-31  
**Accepted through:** SF-IMP-0031  
**Next integration boundary:** live Minecraft chunk write / registry resolution

This document is the concise current-state handoff. Individual ADRs remain authoritative for their accepted contracts.

## Runtime pipeline

```text
semantic island intent
    -> morphology provider / provider blend
    -> independently compiled suspended island volume
    -> deterministic island-group planning
    -> hierarchical archipelago planning
    -> bounded world catalog
    -> spatial region/chunk query
    -> deterministic density realization
    -> backend-neutral structural terrain semantics
    -> minimal adapter-visible context (world position + terrain semantic)
    -> backend-native environment/material policy
    -> concrete backend realization
```

The first concrete backend proof now exists for Minecraft 1.21.1 / NeoForge 21.1.249.

## Accepted module ownership

- `skyforge-kernel` — graph representation, coordinates, signals, validation, reference evaluation.
- `skyforge-model` — semantic descriptors and descriptor validation.
- `skyforge-recipes` — deterministic descriptor/provider/group/archipelago compilation and planning.
- `skyforge-world` — bounded runtime catalog, spatial queries, terrain semantics, backend-neutral sample context.
- `skyforge-reference` — evidence generation, reference providers, sampling, metrics, visual review artifacts.
- `skyforge-neoforge-1211` — first concrete Minecraft/NeoForge adapter proof.

Dependency direction remains strictly downstream:

```text
skyforge-kernel
      ^
skyforge-model
      ^
skyforge-recipes
      ^
skyforge-world
      ^
skyforge-neoforge-1211
```

Minecraft/NeoForge APIs are forbidden from backend-neutral modules. The root verification gate explicitly checks `skyforge-world` in addition to kernel/model/recipes.

## Island geometry

Accepted geometry remains backend-neutral and independently compiled.

- Suspended volumes have explicit upper and underside surfaces plus exact signed-density intersection.
- Built-in and custom morphology providers coexist behind the public provider contract.
- Pairwise provider hybrids blend structural fields before rebuilding the common volume.
- Provider-authored primary surfaces remain authoritative through canonicalization.
- Family/provider-aware secondary morphology and enrichment compose without changing accepted footprint sign.
- Individual islands are never collapsed into one giant group or regional density graph.

## Spatial hierarchy

The accepted composition hierarchy is:

```text
archipelago
  -> child group placement / role / reservation
      -> chain or cluster group plan
          -> independently seeded island members
              -> independently compiled island volumes
```

Important invariants:

- composition owns occurrence and relationships;
- morphology owns form;
- explicit reservations provide deterministic non-overlap contracts;
- seed derivation preserves deterministic identity at every hierarchy level;
- group and archipelago planning are not rerun in the backend chunk hot path.

## Backend-neutral world catalog

An accepted archipelago compiles into `SkyIslandWorldCatalog`, containing one `SkyIslandWorldVolume` per independent island. Each volume carries:

- stable nested world identity;
- conservative world-space query bounds;
- the compiled backend-neutral island graph set.

Backends call `query(WorldBounds)` to retrieve only volumes that may affect a requested region.

The current catalog deliberately uses deterministic linear scanning. Spatial acceleration remains an internal optimization and is not part of the semantic contract.

Horizontal query bounds use explicit member reservations. Vertical query bounds use explicit conservative reservations rather than assuming arbitrary providers obey built-in formulas.

## Structural terrain semantics

SF-IMP-0029 accepted continuous backend-neutral terrain roles:

```text
AIR
EDGE_SHELL
SURFACE_MANTLE
UNDERSIDE_SHELL
SHALLOW_INTERIOR
DEEP_MASS
```

The compiled density remains authoritative for AIR/solid occupancy. Solid samples are classified from continuous geometry, including distances to the compiled upper and underside surfaces, rather than from voxel-neighbor patterns.

This keeps the meaning independent of Minecraft chunk dimensions and sampling resolution.

## Minimal backend context

SF-IMP-0030 accepted `SkyIslandTerrainSampleContext` with exactly:

- world-space `x`, `y`, `z`;
- accepted `SkyIslandTerrainSemantic`.

The context deliberately excludes climate, biome, concrete material, registry IDs, suitability, per-sample island identity, group role, and archipelago role until a concrete behavior demonstrates that the hot path needs them.

Stable identity remains available at world-catalog level.

Guiding rule:

> Skyforge owns the concepts necessary to express Skyforge. A backend owns concepts that exist only because of that backend. Shared abstractions are introduced only after concrete integration demonstrates a genuinely shared need.

## First concrete Minecraft/NeoForge adapter

SF-IMP-0031 accepts `skyforge-neoforge-1211` as the first real backend proof. The target is a historical/comparative baseline, not a permanent release-version commitment.

Accepted toolchain:

- Minecraft 1.21.1;
- NeoForge 21.1.249;
- ModDevGradle 2.0.144;
- Java 21 backend toolchain/runtime compatibility.

The workspace may continue running Gradle on JDK 25. Gradle provisions Java 21 for the NeoForge module when needed, and reusable backend-neutral runtime artifacts emit Java 21-compatible bytecode/API usage.

The accepted chunk proof uses real Minecraft types:

- `net.minecraft.world.level.ChunkPos`;
- `net.minecraft.resources.ResourceLocation`.

Current concrete path:

```text
Minecraft ChunkPos + vertical interval
    -> exact closed WorldBounds for the chunk
    -> SkyIslandWorldCatalog.query(...)
    -> relevant compiled volumes only
    -> terrain semantic interpretation
    -> minimal Skyforge sample context
    -> backend-owned vanilla block registry keys
    -> immutable 16 x H x 16 chunk materialization
```

The engineering proof palette is intentionally minimal:

```text
AIR                 -> minecraft:air
SURFACE_MANTLE      -> minecraft:dirt
EDGE_SHELL          -> minecraft:stone
UNDERSIDE_SHELL     -> minecraft:stone
SHALLOW_INTERIOR    -> minecraft:stone
DEEP_MASS           -> minecraft:deepslate
```

Those mappings prove representation ownership and occupancy preservation. They are not the final terrain palette.

## Minecraft-specific invariants now demonstrated

The focused SF-IMP-0031 proof established:

- exact negative `ChunkPos` coordinate translation;
- real Minecraft/NeoForge compile linkage;
- AIR/solid preservation through concrete registry-key projection;
- chunk-level catalog culling;
- deterministic repeated realization;
- generation-order independence;
- continuity for an island crossing the `x=-1 / x=0` chunk ownership boundary;
- a distant chunk receiving zero island candidates and remaining air.

The validated runtime head is recorded in `docs/reviews/SF-IMP-0031-neoforge-adapter-acceptance.md`.

## Predecessor inheritance

Recovered Aetherial Islands / Aetherial Companion artifacts confirm that many current concerns are empirical rather than speculative: explicit island/cluster identity, stacking control, ocean-frequency tuning, structure fitting, biome adaptation, ore compatibility, registry drift, starter search, and diagnostics all appeared in the predecessor lineage.

The modern inheritance rule remains:

> Keep Companion's questions. Keep its compatibility lessons. Keep its diagnostic instincts. Do not keep its need to fight the terrain generator.

World rules control occurrence and relationships. Descriptors control identity. Recipes control form. Fields control realization. The Minecraft backend controls representation.

Detailed recovery record: `docs/history/Aetherial_Islands_Companion_Lessons_Learned.md`.

## Compatibility and suitability staging

Two predecessor lessons remain deliberately next-stage requirements rather than being folded into SF-IMP-0031.

### Geometry-derived suitability

Biome validity does not prove floating terrain can physically support a feature or structure. Before broad structure/feature integration, Skyforge should expose only the geometry-derived facts a concrete backend actually needs, such as:

- terrain thickness;
- distance to surface;
- distance to underside;
- available surface area or later continuous slope/exposure.

The backend should combine those facts with native biome/tag/feature/structure rules.

### Registry-aware optional integration

Optional compatibility must probe active registries/tags/capabilities before referencing third-party keys. Mod presence alone does not prove a historical registry ID still exists.

Compatibility adapters remain backend-side and must fail gracefully.

Historical Companion patches are not to be ported automatically; reproduce the underlying problem first.

## Runtime realization policy remains deferred

The accepted architecture supports multiple production strategies:

```text
A. live chunk realization
B. whole-archipelago preload/materialization
C. hybrid regional cache
```

No strategy has been selected yet.

The benchmark must compare identical deterministic worlds under the same access patterns and first verify correctness hashes before evaluating performance. Measure cold/warm latency, density/semantic evaluations, memory, throughput, concurrency, cache hit rate, preload cost, persistence size, and seam correctness.

Optimization ladder remains:

```text
linear catalog
    -> spatial index
    -> compiled evaluator cache
    -> region result cache
    -> persistent materialized cache
```

Do not promote an optimization into architecture before measurements justify it.

## Deliberately deferred

The following are not accepted core/runtime requirements yet:

- live `ChunkAccess` mutation;
- live `BlockState` registry resolution;
- NeoForge chunk-generator/worldgen lifecycle registration;
- biome-aware material selection;
- broad structures/features/vegetation/ores/caves/fluids;
- generalized N-way morphology mixtures;
- another hierarchy level above archipelagos;
- a parallel Skyforge climate simulator;
- speculative backend-neutral material taxonomies;
- per-sample world-volume/group identity without demonstrated need;
- historical Companion compatibility hacks without reproduced need;
- provider-certified exact spatial bounds;
- production spatial index;
- persistent world-plan/cache serialization;
- final live/preload/hybrid realization policy;
- Minecraft 1.21.1 as the permanent release target.

## Near-term sequence

1. Add the first live Minecraft write boundary: resolve accepted backend material keys to actual `BlockState` and write into a controlled `ChunkAccess`/equivalent test path without moving planning into the hot loop.
2. Add the minimum geometry-derived suitability needed before broad vanilla/modded structures or placed features.
3. Add registry probing/provenance infrastructure before optional-mod compatibility work.
4. Reproduce specific predecessor compatibility problems before adapting any historical patch.
5. Benchmark live, preloaded, and hybrid realization on identical deterministic worlds.
6. Choose production caching/spatial-index policy from measurements.
7. Enrich environment/material semantics only when concrete backend-independent Skyforge behavior requires it.
