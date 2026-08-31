# SF-IMP-0036 Post-Surface Worldgen Acceptance

**Status:** Accepted  
**Date:** 2026-08-31

## Scope

SF-IMP-0036 replaces the late new-chunk lifecycle proof as the primary terrain-generation path with a supported registered `NoiseBasedChunkGenerator` subtype that realizes Skyforge immediately after vanilla surface construction and before carvers, final heightmap priming, biome decoration/features and lighting.

The legacy `ChunkEvent.Load(isNewChunk=true)` implementation remains only as historical/provisional lifecycle evidence; the SF-IMP-0036 development specimen does not depend on it.

## Accepted worldgen path

```text
vanilla BIOMES
    -> vanilla NOISE
    -> vanilla SURFACE
    -> Skyforge additive realization
    -> vanilla CARVERS
    -> final heightmap priming
    -> vanilla FEATURES / biome decoration
    -> vanilla lighting initialization
    -> vanilla LIGHT
```

The concrete generator codec is `skyforge:noise_overlay`.

## Automated evidence

`scripts\verify-sf-imp-0036-post-surface-worldgen.bat` passed, establishing:

- backend-neutral independence remains intact;
- the registered generator and development resources compile under Minecraft 1.21.1 / NeoForge 21.1.249;
- the post-surface binding is inert when not installed;
- an installed binding performs additive real-`ChunkAccess` realization;
- Skyforge AIR preserves backend-native blocks;
- final Minecraft heightmap priming observes elevated Skyforge terrain;
- the development runtime uses the new post-surface binding rather than the legacy load-event binding;
- development-only world-preset resources are available to ModDev but absent from the production JAR.

A repository-wide `gradlew.bat check` also passed on the implementation used for the interactive proof.

## Real-client evidence

A new world created with the development-only `Skyforge Development (SF-IMP-0036)` world type successfully generated the documented floating Massif.

Manual inspection observed:

- cave formations through the Massif;
- ore placements in the elevated island;
- grass/vegetation behavior;
- trees on the island;
- no immediate catastrophic worldgen failure;
- no reported obvious chunk-ownership seam;
- normal enough lighting to satisfy the acceptance gate;
- native terrain retained beneath/around the additive Skyforge volume.

These observations are stronger than simple visibility. Caves are consistent with carvers seeing the post-surface volume, while ore and tree placement are consistent with later feature/biome-decoration systems observing elevated Skyforge terrain.

## Persistence and log evidence

The world was saved, closed, reopened and inspected again. The Massif persisted without observed duplicate realization or corruption.

The accepted client log corroborates the intended integration path:

- Skyforge is discovered and loaded as mod id `skyforge`;
- the runtime explicitly reports `Skyforge post-surface development specimen enabled`;
- the integrated Overworld generates successfully;
- all dimensions save cleanly;
- the integrated server shuts down and starts the same world again;
- the player logs into the reloaded world without a Skyforge worldgen/codec/registry exception;
- the ModDev run exits successfully.

The first login position was approximately `y=245`. This is retained as supporting evidence consistent with elevated heightmap/spawn behavior, not as a dedicated acceptance claim about Minecraft's spawn subsystem.

## Explicit non-claims

SF-IMP-0036 does not establish that:

- structure starts understand Skyforge terrain; `STRUCTURE_STARTS` occurs earlier;
- vanilla `SurfaceSystem` processed the island; Skyforge is inserted after surface construction;
- current vanilla/modded feature placement is aesthetically or mechanically suitable for floating terrain;
- current cave generation is desirable;
- the dirt/stone/deepslate engineering palette is production-ready;
- the development-only world preset is the final user-facing configuration mechanism.

Grass observed on the island is therefore not treated as proof of vanilla surface-rule participation.

## Architectural conclusion

The empirical Minecraft path is now:

```text
backend-neutral Skyforge plan
    -> deterministic island geometry
    -> structural terrain semantics
    -> Minecraft material projection
    -> registered NoiseBasedChunkGenerator
    -> post-surface additive ChunkAccess realization
    -> vanilla carvers
    -> final heightmaps
    -> vanilla features / biome decoration
    -> lighting
    -> persistent playable Minecraft world
```

Minecraft now demonstrably treats Skyforge terrain as part of the downstream world-generation environment rather than merely receiving it after generation has effectively completed.

## Next boundary

The next work should improve Minecraft-native interpretation of this terrain rather than inventing another broad climate/material abstraction upstream. High-value candidates are backend-native biome/surface material adaptation, geometry-derived suitability for features/structures, and explicit structure-start strategy. Morphology/playability work remains independently valuable, particularly the previously observed oversized Massif underside.
