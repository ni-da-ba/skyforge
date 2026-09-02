# ADR-0058: Native exact-volume surface population planner

**Status:** Proposed

## Context

ADR-0057 proved that an exact Skyforge terrain volume can expose one final-registry Minecraft biome to native placed-feature execution while preserving independent vertical ownership and BASE_WORLD isolation.

That proof was intentionally fixture-driven: the development runtime selected a biome, iterated one biome generation step, and accumulated acceptance evidence. The next architectural question is how that behavior becomes a reusable runtime system without turning Skyforge into a replay of Minecraft's entire chunk decoration pipeline.

Minecraft's biome generation steps mix several different semantic systems: surface ecology, lakes and fluid behavior, underground decoration, ores, structures, and top-layer thermal effects. Treating all of them as one generic "biome population" operation would recreate the same global-world-column coupling that exact-volume generation was introduced to eliminate.

## Decision

Skyforge introduces a production-facing **native surface population planner and coordinator**.

The planner identifies:

- one exact `SkyIslandWorldVolumeId`;
- one exact-volume biome/environment resolver;
- an ordered list of semantically admitted native generation phases;
- one bounded attachment policy.

The coordinator executes each admitted `(volume, chunk, phase)` at most once during the chunk-generation lifecycle and caches the native result. Repeated requests for the same lifecycle key return the cached result without re-running placed features.

The first admitted phase is:

```text
VEGETAL_DECORATION
```

The following are explicitly **not** admitted by ADR-0058:

```text
LAKES / fluid-hydrology phases
UNDERGROUND_ORES
UNDERGROUND_DECORATION
structure phases
TOP_LAYER_MODIFICATION
```

`TOP_LAYER_MODIFICATION` is deferred even though it occurs at the surface because its snow/ice behavior crosses the later thermal/hydrology boundary. Future ADRs may admit it once those semantics are modeled explicitly.

## Phase semantics, not feature IDs

Admission occurs at semantic generation-step granularity. Skyforge does not maintain a whitelist of vanilla or modded placed-feature IDs.

Once a phase is admitted, the Minecraft adapter consumes the selected biome's **live final-registry** feature list for that phase. Datapacks and NeoForge/mod biome modifications therefore remain eligible without bespoke feature mappings.

This preserves the compatibility principle established in ADR-0057:

> reuse Minecraft definitions, not Minecraft ownership assumptions.

## Lifecycle idempotency

One exact population lifecycle key is:

```text
(volumeId, chunkPos, semanticPhase)
```

It is intentionally coarser than individual placed-feature identity. The biome's native feature list is one ordered phase payload and must not be replayed because a neighboring chunk, adapter callback, or development observer asks for the same volume/chunk phase twice.

The coordinator therefore:

1. resolves one actual owned surface sample inside the target chunk;
2. evaluates biome intent at that owned sample rather than blindly using the chunk center;
3. executes the native phase once;
4. caches the phase result;
5. returns a no-op replay result on subsequent requests.

If the surface sample, biome assignment, or attachment policy changes after the phase has completed, the replay is rejected as an invalid generation-lifecycle mutation instead of silently redecorating under different semantics.

Minecraft chunk status already prevents normal decoration from being replayed on ordinary save/reload. The coordinator guard protects Skyforge's own orchestration from duplicate execution while a chunk is being generated.

## Runtime binding

`SkyforgeNativeSurfacePopulationStage` is supplied by a higher-level plan resolver. The Minecraft chunk generator does not query Skyforge's catalog directly to decide which plans exist.

This preserves dependency direction:

```text
Skyforge world/environment runtime
        -> surface population plans
        -> Minecraft native surface population stage
        -> exact-volume native feature runner
```

The stage is inert when no resolver is installed.

The existing accepted post-realization callback is reused rather than adding a new generator override for every population milestone.

## Exact biome sample

ADR-0057's fixture initially resolved biome intent using chunk-center X/Z plus a known surface Y. ADR-0058 promotes an actual exact-volume owned surface coordinate to the biome resolver.

This is necessary for future within-island climate/ecology fields: a chunk may intersect an island only near one edge, and an empty chunk center must not become the semantic biome sample.

## Consequences

- Surface ecology now has a reusable lifecycle coordinator rather than a fixture-specific loop.
- Duplicate surface-population calls cannot duplicate native vegetation for the same exact volume/chunk/phase.
- Stacked volumes at one X/Z retain independent phase ledgers and biome semantics.
- Biome mods can continue contributing vegetation through final-registry generation settings.
- Hydrology, caves, ores, structures, and top-layer thermal effects remain explicitly outside this scheduler until separately admitted.
- The plan resolver becomes the later connection point from backend-neutral environment/climate intent to Minecraft biome semantics.

## Acceptance boundary

ADR-0058 may become **Accepted** when SF-IMP-0055 demonstrates on one exact PR head that:

1. full repository CI passes;
2. the reusable coordinator, not a fixture-specific feature loop, populates two vertically stacked exact volumes with different registered biomes;
3. visible forest/taiga native ecology remains present with persistent log/leaf evidence;
4. an immediate second request for every populated `(volume, chunk, phase)` executes zero native phases and returns cached results;
5. the completed-phase ledger matches the number of actual terrain-owning volume/chunk phase keys;
6. only `VEGETAL_DECORATION` is admitted;
7. BASE_WORLD remains visually normal beneath the islands and no cross-volume contamination is observed.
