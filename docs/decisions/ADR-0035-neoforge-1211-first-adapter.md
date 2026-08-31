# ADR-0035: First Concrete NeoForge 1.21.1 Adapter Proof

- **Status:** Implementation prepared; focused local validation pending
- **Date:** 2026-08-31
- **Work item:** SF-IMP-0031

## Context

SF-IMP-0028 established the backend-neutral world catalog and seam-safe tiled realization boundary. SF-IMP-0029 added continuous structural terrain semantics. SF-IMP-0030 proved that a backend may consume only world position plus accepted terrain semantic and choose its own representation without changing Skyforge occupancy.

The next question is whether that boundary survives contact with a real Minecraft/NeoForge API rather than another reference-only backend.

The recovered Aetherial Islands / Aetherial Companion lineage gives a concrete historical baseline: the Stab City predecessor artifacts target Minecraft 1.21.1 / NeoForge 21.1-era worldgen. For that reason the first adapter proof targets Minecraft 1.21.1 and NeoForge rather than selecting a newer game version solely because it exists.

This is a proof baseline, not a permanent release-version commitment.

## Toolchain baseline

The adapter uses:

- Minecraft 1.21.1;
- NeoForge 21.1.249;
- ModDevGradle 2.0.144;
- Java 21 bytecode/API target.

The Skyforge workspace itself remains validated on JDK 25. For this compile-only integration proof, the adapter compiles with the workspace JDK using `--release 21`. A later actual Minecraft process/game-server launch must be run on a Java 21 runtime.

## Module boundary

A new versioned proof module is introduced:

```text
skyforge-neoforge-1211
```

Dependency direction remains:

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

`skyforge-world` remains backend-neutral.

The root backend-independence verification is strengthened to include `skyforge-world`, so Minecraft/NeoForge imports in that module become a hard build failure.

## First concrete types

The proof deliberately uses real Minecraft 1.21.1 types while avoiding a full game launch:

- `net.minecraft.world.level.ChunkPos` for actual Minecraft chunk identity and block-coordinate translation;
- `net.minecraft.resources.ResourceLocation` for concrete vanilla block registry keys.

The first proof does not yet instantiate or mutate `ChunkAccess` and does not resolve registry keys to live `BlockState` instances. That is deferred to the next lifecycle hook after this boundary is compile- and behavior-proven.

This keeps registry/bootstrap and server lifecycle complexity out of the first integration gate without retreating to invented Minecraft-like types.

## Chunk translation

`MinecraftChunkBounds` translates one Minecraft `ChunkPos` plus an explicit vertical interval into closed Skyforge `WorldBounds` matching the exact block coordinates owned by the chunk.

For example, chunk `(-2, 3)` maps horizontally to:

```text
x = [-32, -17]
z = [ 48,  63]
```

Negative chunk coordinates are an explicit acceptance case.

## Materialization path

The first concrete path is:

```text
Minecraft ChunkPos + Y interval
        -> exact closed WorldBounds
        -> SkyIslandWorldCatalog.query(...)
        -> only relevant independently compiled volumes
        -> SkyIslandTerrainInterpreter
        -> SkyIslandTerrainSampleContext
        -> Minecraft-owned vanilla block-key palette
        -> immutable 16 x H x 16 chunk materialization
```

Composition planning is never rerun in the chunk hot path.

## Initial block-key palette

The first proof uses a deliberately minimal vanilla representation:

```text
AIR                 -> minecraft:air
SURFACE_MANTLE      -> minecraft:dirt
EDGE_SHELL          -> minecraft:stone
UNDERSIDE_SHELL     -> minecraft:stone
SHALLOW_INTERIOR    -> minecraft:stone
DEEP_MASS           -> minecraft:deepslate
```

These mappings are engineering proof values, not a final Skyforge material palette and not an assertion that every biome should use these blocks.

No block key enters a backend-neutral module.

## Occupancy authority

For every sample, the adapter verifies that its concrete Minecraft representation preserves the accepted Skyforge occupancy decision:

```text
Skyforge AIR   <=> minecraft:air
Skyforge solid <=> non-air Minecraft block key
```

The adapter may change representation. It may not create or erase Skyforge geometry.

## Determinism and seam proof

The focused proof uses one real compiled Massif centered on the Minecraft chunk boundary between chunks `(-1, 0)` and `(0, 0)`.

Acceptance requires:

1. both chunks independently query the same catalog where spatially relevant;
2. generating west then east produces exactly the same block-key arrays as east then west;
3. both chunks contain solid material;
4. at least one world-space row remains solid across the x=-1 / x=0 ownership boundary;
5. the materialization contains both surface and deep-mass representation where the morphology supports them;
6. a distant empty chunk receives zero candidate volumes and remains entirely air.

This is the first Minecraft-specific restatement of the seam/order invariants accepted in SF-IMP-0028.

## Acceptance criteria

SF-IMP-0031 focused acceptance requires:

1. the NeoForge/Minecraft dependency resolves and the adapter compiles against real 1.21.1 classes;
2. backend-neutral modules remain free of Minecraft/NeoForge imports;
3. negative `ChunkPos` coordinates translate to exact closed block bounds;
4. every terrain semantic projects to a concrete vanilla block registry key;
5. the projection preserves AIR/solid occupancy;
6. chunk materialization queries only catalog candidates relevant to that chunk interval;
7. repeated generation is deterministic;
8. chunk generation order does not alter output;
9. an island crossing a chunk ownership boundary remains continuous;
10. an empty distant chunk performs no island evaluation and remains air.

## Explicit non-goals

This work item does not yet implement:

- a live `ChunkAccess` write hook;
- `BlockState` registry resolution;
- a NeoForge worldgen lifecycle event or chunk generator registration;
- biome lookup or biome-aware block selection;
- structures, placed features, vegetation, ores, caves or fluids;
- registry probing for optional third-party mods;
- compatibility adapters for Create, Terralith, Biomes O' Plenty or Lithostitched;
- production caching or async scheduling;
- a final live/preload/hybrid policy;
- commitment to Minecraft 1.21.1 as the eventual release target.

Those should be introduced only after this first concrete API boundary passes.

## Consequence

If accepted, Skyforge will have crossed the first actual Minecraft boundary while preserving the architecture established by the predecessor lessons:

> Skyforge arrives at the backend with the world already semantically and geometrically correct. The backend translates and represents it; it does not recreate or repair the world model.
