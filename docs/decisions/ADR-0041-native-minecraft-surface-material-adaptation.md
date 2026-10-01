# ADR-0041 — Native Minecraft Surface Material Adaptation

**Status:** Accepted

## Context

SF-IMP-0036 moved Skyforge terrain into a registered `NoiseBasedChunkGenerator` immediately after vanilla surface construction. That timing is early enough for later carvers, final heightmaps, features and lighting to observe Skyforge terrain, but vanilla `SurfaceSystem` has already finished before Skyforge blocks exist.

The current engineering palette therefore still projects Skyforge structural semantics directly to dirt, stone and deepslate. Hard-coding a parallel Skyforge biome or climate table would violate the accepted backend boundary, while rerunning vanilla `SurfaceSystem` against independently generated floating geometry would couple Skyforge to internal preliminary-surface assumptions that SF-IMP-0036 deliberately avoided.

Minecraft has already produced a concrete native surface result in the chunk by the time Skyforge runs. That result is a backend-authored representation decision and can be reused directly.

## Decision

The first Minecraft-native surface adaptation samples the already-built native Minecraft surface in each chunk column before Skyforge writes any blocks.

For each `(x, z)` column:

1. scan the post-surface `ChunkAccess` from top to bottom;
2. ignore air and fluid blocks;
3. record the first remaining block as that column's native surface-top material;
4. inspect the accepted Skyforge materialization for exposed solid tops, defined as a Skyforge solid with Skyforge AIR immediately above it;
5. when an exposed Skyforge top is above the native surface, replace only that top block key with the sampled native surface block key;
6. leave all other Skyforge materialization entries unchanged;
7. perform the existing additive solid overlay into the real Minecraft chunk.

Conceptually:

```text
vanilla NOISE
    -> vanilla SURFACE
    -> snapshot native surface-top material per column
    -> Skyforge semantic materialization
    -> adapt exposed Skyforge top keys from native surface snapshot
    -> additive live BlockState write
    -> vanilla CARVERS / HEIGHTMAPS / FEATURES / LIGHTING
```

The implementation remains entirely inside `skyforge-neoforge-1211`. No biome, climate, block ID or Minecraft surface concept is added to kernel/model/recipes/world.

## Why copy Minecraft's surface result rather than classify biomes in Skyforge

The native surface block is the output of Minecraft's actual backend rules. Sampling it has several advantages over a new Skyforge biome table:

- Minecraft remains authoritative for concrete material representation;
- vanilla biome/surface-rule changes can flow through without a duplicated climate model;
- a modded surface block can be inherited if it is already present in the active Minecraft block registry and native surface result;
- Skyforge structural semantics remain unchanged;
- the adaptation does not require upstream modules to know Minecraft biome IDs, tags or registry keys.

This is intentionally a representation adapter, not a new environmental simulation layer.

## Why only the exposed top block

SF-IMP-0037 is the smallest useful proof. Replacing the exposed top gives Minecraft-native visual/environmental continuity while preserving accepted structural palette choices for the rest of the island.

Copying an arbitrary depth of native filler material would require a separate policy for mantle thickness, falling blocks, shore/ocean columns and vertically varying biome context. Those concerns should be justified by observed results rather than folded into the first adapter.

## Multiple vertically separated islands

The adapter treats every Skyforge solid segment whose immediately higher Skyforge sample is AIR as an exposed top. It therefore does not assume only one island can occupy a vertical `(x, z)` column.

The same native column surface material may currently be reused for more than one elevated segment. That is an explicit first-proof limitation, not a new world-composition constraint.

## Terrain intersections

A sampled native surface at or above a Skyforge exposed top is not copied onto that top. Such a sample is treated as a terrain intersection rather than a floating exposed surface.

Skyforge AIR remains non-destructive and native Minecraft terrain remains authoritative outside Skyforge solids.

## Binding policy

The existing unadapted post-surface install path remains available for exact engineering-palette tests.

A distinct native-surface-adapted binding opts into this representation behavior. The SF-IMP-0037 ModDev specimen uses the adapted binding so the real-client proof cannot silently pass through the old engineering-only path.

## Invariants

1. Backend-neutral modules remain free of Minecraft/NeoForge APIs.
2. Skyforge density and structural terrain semantics remain authoritative for occupancy and geometry.
3. Surface adaptation changes only concrete Minecraft representation.
4. The adapter never changes Skyforge AIR into a solid block.
5. The adapter never removes a Skyforge solid.
6. Native fluid blocks are not copied as Skyforge surface solids.
7. Native terrain at or above a Skyforge top prevents that top from being treated as a floating exposed surface.
8. No planner/group/archipelago work enters the per-chunk hot path.
9. The development world preset remains development-only and excluded from the production JAR.

## Explicit limitations

SF-IMP-0037 does **not** claim that:

- vanilla `SurfaceSystem` has actually run over Skyforge geometry;
- the sampled ground-level surface always represents a vertically different high-altitude biome perfectly;
- native subsurface/filler depth has been reproduced;
- beaches, ocean floors, snow layers, powder snow, fluids or shore transitions are production-tuned;
- structure placement is Skyforge-aware;
- every modded surface system is automatically compatible;
- the current Massif morphology is final.

A further accepted integration observation is that Minecraft's normal top-surface heightmaps are single-valued per `(x,z)`. When an elevated Skyforge island occupies a column, the upper island may become the heightmap target while preserved native ground below remains physically present. This can redirect heightmap-driven vegetation/features away from the lower ground and requires a separate multi-surface suitability/placement strategy if Skyforge needs independently decorated stacked surfaces.

## Acceptance criteria

ADR-0041 becomes Accepted only after:

1. an automated test proves air and fluids are skipped while sampling the native surface;
2. an automated test proves every elevated exposed Skyforge segment in a column can inherit the sampled native top material;
3. non-top Skyforge material remains unchanged;
4. Skyforge solids at/below the native surface are not incorrectly adapted as floating tops;
5. solid occupancy and candidate-volume metadata remain unchanged by adaptation;
6. the real post-surface binding can opt into native surface adaptation while the unadapted engineering path remains available;
7. the SF-IMP-0037 development runtime explicitly uses the adapted binding and not `ChunkEvent.Load`;
8. backend-neutral independence remains green;
9. focused NeoForge tests and repository-wide `check` pass;
10. a real ModDev world visibly retains the Massif, native terrain, later worldgen interaction and persistence while its exposed surface uses Minecraft's already-built native surface material rather than only the fixed engineering top representation.

## Acceptance evidence — 2026-08-31

All criteria above passed.

Automated validation passed through `scripts\verify-sf-imp-0037-native-surface-adaptation.bat` and repository-wide `gradlew.bat check`, including the final regression diagnostic proving that preserved lower ground remains present while Minecraft's final single-valued world-surface heightmap selects the elevated Skyforge surface.

In the real ModDev client, the Massif remained geometrically intact and persistent, native terrain remained additive, the exposed island top appeared remarkably like the surrounding Minecraft terrain, copied native material remained shallow rather than replacing the whole island interior, and later world-generation/lighting behavior remained functional. The sparse vegetation observed directly beneath the Massif is recorded as the heightmap/multi-surface integration boundary described above, not as a failure of native material adaptation.
