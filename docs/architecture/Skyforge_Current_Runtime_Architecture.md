# Skyforge Current Runtime Architecture

**Snapshot:** 2026-08-31  
**Accepted through:** SF-IMP-0033  
**Next integration boundary:** earlier Minecraft world-generation insertion point and first controlled in-game/runClient proof

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
    -> backend-owned block registry key
    -> live Minecraft BlockState
    -> additive real Minecraft ChunkAccess storage
    -> NeoForge newly-generated-chunk lifecycle delivery
```

The concrete backend proof now reaches a real FML-loaded NeoForge mod, a real NeoForge lifecycle event, and real Minecraft chunk mutation.

## Accepted module ownership

- `skyforge-kernel` — graph representation, coordinates, signals, validation, reference evaluation.
- `skyforge-model` — semantic descriptors and descriptor validation.
- `skyforge-recipes` — deterministic descriptor/provider/group/archipelago compilation and planning.
- `skyforge-world` — bounded runtime catalog, spatial queries, terrain semantics, backend-neutral sample context.
- `skyforge-reference` — evidence generation, reference providers, sampling, metrics and visual review artifacts.
- `skyforge-neoforge-1211` — concrete Minecraft 1.21.1 / NeoForge 21.1 adapter, registry/state translation, live chunk storage and lifecycle proof.

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

Minecraft/NeoForge APIs are forbidden from backend-neutral modules. The root verification gate explicitly checks kernel/model/recipes/world independence.

## Island geometry

Accepted geometry remains backend-neutral and independently compiled.

- Suspended volumes have explicit upper and underside surfaces plus exact signed-density intersection.
- Built-in and custom morphology providers coexist behind the public provider contract.
- Current built-in families are MASSIF, TABLELAND, SPINE, BASIN and LOBED.
- Pairwise provider hybrids blend structural fields before rebuilding the common volume.
- Provider-authored primary surfaces remain authoritative through canonicalization.
- Family/provider-aware secondary morphology and bounded enrichment compose without changing accepted footprint sign.
- Individual islands are never collapsed into one giant group or regional density graph.

Generalized N-way morphology mixing remains deferred until a concrete need justifies it.

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

This preserves future control over island frequency, vertical stacking, chain/cluster layout, ocean/outlier frequency and other world rules without making those concerns part of morphology density.

## Backend-neutral world catalog

An accepted archipelago compiles into `SkyIslandWorldCatalog`, containing one `SkyIslandWorldVolume` per independent island. Each volume carries:

- stable nested world identity;
- conservative world-space query bounds;
- the compiled backend-neutral island graph set.

Backends call `query(WorldBounds)` to retrieve only volumes that may affect a requested region.

The current catalog deliberately uses deterministic linear scanning. Spatial acceleration remains an internal optimization, not part of the semantic contract.

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

This keeps structural meaning independent of Minecraft chunk dimensions and sampling resolution.

## Minimal backend context

SF-IMP-0030 accepted `SkyIslandTerrainSampleContext` with exactly:

- world-space `x`, `y`, `z`;
- accepted `SkyIslandTerrainSemantic`.

The context deliberately excludes climate, biome, concrete material, registry IDs, suitability, per-sample island identity, group role and archipelago role until concrete behavior demonstrates that the hot path needs them.

Guiding rule:

> Skyforge owns the concepts necessary to express Skyforge. A backend owns concepts that exist only because of that backend. Shared abstractions are introduced only after concrete integration demonstrates a genuinely shared need.

## Concrete Minecraft/NeoForge adapter

Minecraft 1.21.1 / NeoForge 21.1.249 is an accepted engineering baseline, not a permanent release-version commitment.

Accepted toolchain:

- Minecraft 1.21.1;
- NeoForge 21.1.249;
- ModDevGradle 2.0.144;
- Java 21 backend toolchain/runtime compatibility.

The workspace may continue running Gradle on JDK 25. Gradle provisions Java 21 for the NeoForge module when needed, while reusable backend-neutral runtime artifacts emit Java 21-compatible bytecode/API usage.

### SF-IMP-0031 — chunk materialization

Accepted path:

```text
Minecraft ChunkPos + vertical interval
    -> exact closed WorldBounds
    -> SkyIslandWorldCatalog.query(...)
    -> relevant compiled volumes
    -> terrain semantic interpretation
    -> backend-owned vanilla block registry keys
    -> immutable 16 x H x 16 materialization
```

Engineering proof palette:

```text
AIR                 -> minecraft:air
SURFACE_MANTLE      -> minecraft:dirt
EDGE_SHELL          -> minecraft:stone
UNDERSIDE_SHELL     -> minecraft:stone
SHALLOW_INTERIOR    -> minecraft:stone
DEEP_MASS           -> minecraft:deepslate
```

These mappings prove representation ownership and occupancy preservation; they are not the final terrain palette.

### SF-IMP-0032 — live registry/state/chunk storage

Accepted extension:

```text
accepted MinecraftChunkMaterialization
    -> strict BuiltInRegistries.BLOCK lookup
    -> live default BlockState
    -> ChunkAccess.setBlockState(...)
    -> ChunkAccess.getBlockState(...) read-back proof
```

Accepted properties:

- unknown registry keys fail explicitly instead of falling through Minecraft's defaulted block registry;
- a materialization may only write to the exact matching `ChunkPos`;
- its vertical interval must remain inside the target chunk's build interval;
- registry resolution preserves Skyforge AIR/solid occupancy;
- every written state reads back exactly from actual Minecraft chunk storage;
- the stored non-air count equals the accepted materialization solid count;
- positions outside the written interval remain untouched;
- the x=-1 / x=0 island seam remains continuous after real storage.

The proof uses real `ProtoChunk` instances under ModDevGradle's FML-aware JUnit environment.

### SF-IMP-0033 — real NeoForge lifecycle delivery

SF-IMP-0033 promotes the adapter module to a real minimal NeoForge mod boundary with:

- production `META-INF/neoforge.mods.toml`;
- `@Mod("skyforge")` entrypoint;
- `@EventBusSubscriber` lifecycle listener;
- `ChunkEvent.Load` delivery through `NeoForge.EVENT_BUS`;
- realization only when `event.isNewChunk()` is true;
- adapter-local level/dimension selection;
- no neighboring chunk or arbitrary level lookup from the callback;
- no default engineering island unless a runtime binding is deliberately installed.

Accepted lifecycle path:

```text
FML-loaded Skyforge mod
    -> NeoForge ChunkEvent.Load
    -> require isNewChunk()
    -> backend-local level selector
    -> accepted chunk adapter
    -> accepted materialization
    -> live BlockState resolution
    -> additive solid overlay into the event ChunkAccess
```

## Additive Minecraft composition

The first live lifecycle integration demonstrated a concrete backend rule that isolated exact-storage tests did not need.

For normal Minecraft composition:

```text
Skyforge solid -> write the resolved Skyforge BlockState
Skyforge AIR   -> preserve Minecraft's existing block
```

This prevents a floating-island overlay from erasing native terrain wherever Skyforge contributes no solid.

The SF-IMP-0032 exact writer remains available for exact-ownership/equivalence tests. Overlay semantics are backend composition behavior, not a change to Skyforge density or AIR meaning.

## Minecraft-specific invariants now demonstrated

Across SF-IMP-0031 through SF-IMP-0033, the concrete backend demonstrates:

- exact negative `ChunkPos` coordinate translation;
- real Minecraft/NeoForge compile linkage;
- deterministic chunk-local catalog culling and realization;
- AIR/solid preservation through registry-key projection;
- generation-order independence;
- x=-1 / x=0 seam continuity;
- strict live block-registry resolution;
- real `BlockState` creation;
- real `ProtoChunk`/`ChunkAccess` section allocation and mutation;
- exact stored-state read-back;
- real production mod discovery by FML;
- real `NeoForge.EVENT_BUS` lifecycle delivery;
- existing chunks ignored by the Skyforge new-chunk path;
- backend level selection can reject chunks without mutation;
- Skyforge AIR preserves pre-existing native Minecraft terrain in overlay mode.

Acceptance records:

- `docs/reviews/SF-IMP-0031-neoforge-adapter-acceptance.md`;
- `docs/reviews/SF-IMP-0032-live-chunk-writer-acceptance.md`;
- `docs/reviews/SF-IMP-0033-neoforge-lifecycle-acceptance.md`.

## Current lifecycle limitation

`ChunkEvent.Load(isNewChunk=true)` is accepted as the first real lifecycle seam, but not as the final world-generation insertion point.

NeoForge posts this event while a generated chunk is being promoted/loaded. Earlier vanilla terrain-generation work may already have occurred. Therefore current acceptance does not claim that:

- vanilla placed features or vegetation see Skyforge surfaces;
- structures evaluate or fit Skyforge terrain;
- heightmaps are finalized correctly for all consumers;
- lighting behaves as if Skyforge terrain existed during earlier generation phases.

The next Minecraft integration task is to identify the earliest practical generation seam required for native systems that must reason about Skyforge terrain.

## Predecessor inheritance

Recovered Aetherial Islands / Aetherial Companion artifacts confirm that many current concerns are empirical rather than speculative: explicit island/cluster identity, stacking control, ocean-frequency tuning, structure fitting, biome adaptation, ore compatibility, registry drift, starter search and diagnostics all appeared in the predecessor lineage.

The modern inheritance rule remains:

> Keep Companion's questions. Keep its compatibility lessons. Keep its diagnostic instincts. Do not keep its need to fight the terrain generator.

World rules control occurrence and relationships. Descriptors control identity. Recipes control form. Fields control realization. The Minecraft backend controls representation.

Detailed recovery record: `docs/history/Aetherial_Islands_Companion_Lessons_Learned.md`.

## Compatibility and suitability staging

### Geometry-derived suitability

Biome validity does not prove floating terrain can physically support a feature or structure. Before broad structure/feature integration, Skyforge should expose only geometry-derived facts a concrete backend actually needs, such as terrain thickness, distance to surface/underside and later surface area/slope/exposure.

The backend should combine those facts with native biome/tag/feature/structure rules.

### Registry-aware optional integration

Optional compatibility must probe active registries/tags/capabilities before referencing third-party keys. Mod presence alone does not prove a historical registry ID still exists.

Compatibility adapters remain backend-side and must fail gracefully. SF-IMP-0032's strict unknown-key failure is the first concrete enforcement of that principle.

Historical Companion patches are not to be ported automatically; reproduce the underlying problem first.

## Runtime realization policy remains deferred

The accepted architecture supports multiple production strategies:

```text
A. live chunk realization
B. whole-archipelago preload/materialization
C. hybrid regional cache
```

No strategy has been selected yet.

The benchmark must compare identical deterministic worlds under the same access patterns and verify correctness hashes before evaluating performance. Measure cold/warm latency, density/semantic evaluations, memory, throughput, concurrency, cache hit rate, preload cost, persistence size and seam correctness.

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

- the final earlier worldgen insertion strategy;
- normal interactive in-game/server acceptance proof;
- production world-plan/config bootstrap;
- biome-aware material selection;
- heightmap and lighting finalization policy;
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

1. Run the first controlled interactive `runClient` / packaged-mod smoke test to prove the production mod loads in a normal Minecraft client environment without introducing an automatic engineering island.
2. Identify and prove the earlier chunk-generation stage required for Skyforge terrain to participate correctly in native heightmaps, lighting, features and structures.
3. Add the minimum geometry-derived suitability required by the first concrete vanilla/modded feature or structure integration.
4. Add registry probing/provenance infrastructure before optional-mod compatibility work.
5. Reproduce specific predecessor compatibility problems before adapting historical patches.
6. Benchmark live, preloaded and hybrid realization on identical deterministic worlds.
7. Choose production caching/spatial-index policy from measurements.
8. Enrich environment/material semantics only when concrete backend-independent Skyforge behavior requires it.
