# Skyforge Minecraft Adapter Boundary Proposal

## Purpose

This note defines the first concrete game-backend boundary that should follow accepted terrain semantics. It deliberately avoids selecting a Minecraft version or loader. The purpose is to preserve dependency direction and identify what a Minecraft-facing module must consume from Skyforge.

## Dependency boundary

A future Minecraft-facing module may depend on Skyforge core modules:

```text
skyforge-kernel
      ^
skyforge-model
      ^
skyforge-recipes
      ^
skyforge-world
      ^
skyforge-minecraft / skyforge-neoforge / other adapter
```

No core module may import Minecraft or loader APIs.

## Runtime responsibility split

### Skyforge owns

- deterministic world-volume identity;
- island/group/archipelago planning;
- compiled procedural density geometry;
- conservative spatial world queries;
- structural terrain semantics;
- deterministic realization independent of chunk order.

### Minecraft adapter owns

- translation from Minecraft chunk/section coordinates into Skyforge `WorldBounds` or equivalent sampled region;
- access to native biome/environment information;
- concrete block-state selection;
- chunk/section writes;
- Minecraft lifecycle hooks;
- registry integration;
- backend-specific caching and scheduling;
- compatibility with Minecraft terrain/feature stages.

## Intended chunk path

Conceptually:

```text
Minecraft requests or prepares a chunk
        -> adapter computes world-space query bounds
        -> SkyIslandWorldCatalog.query(bounds)
        -> adapter samples only relevant Skyforge volumes
        -> SkyIslandTerrainSemantic at block/sample positions
        -> adapter combines semantic with native biome/environment context
        -> adapter selects BlockState
        -> adapter writes the backend chunk/section
```

The adapter must not rerun group or archipelago planning for every chunk request.

## World-plan lifetime

A Minecraft world should obtain or derive a deterministic Skyforge regional/world plan from the world seed or an explicitly persisted Skyforge plan.

The intended lifecycle is:

```text
world seed / Skyforge configuration
        -> deterministic regional planning
        -> compiled SkyIslandWorldCatalog
        -> long-lived world-generation context
        -> repeated chunk queries
```

The first adapter proof may keep the catalog in memory. Persistence and cross-session cache formats are deferred.

## Chunk independence

SF-IMP-0028 already establishes that disjoint tile ownership plus conservative closed catalog queries can reproduce monolithic geometry exactly. The Minecraft adapter should preserve the same property:

- chunk generation order must not change geometry;
- neighboring chunks may conservatively consider the same island;
- each backend voxel/block position is written by the chunk/section that owns that position;
- no geometry seam may appear because an island crosses a chunk boundary.

## Native biome participation

The adapter should prefer Minecraft's native biome/environment system where that system already owns the concept.

Example mapping:

```text
Skyforge SURFACE_MANTLE
+ native biome/environment
-> backend surface palette

Skyforge UNDERSIDE_SHELL
+ native biome/environment if relevant
-> backend underside palette
```

Skyforge should not duplicate Minecraft climate variables solely to drive block selection.

## First adapter proof scope

The first concrete proof should be intentionally narrow:

1. one deterministic Skyforge regional plan;
2. one small set of chunk-like backend regions;
3. AIR versus structural terrain semantics mapped to a minimal block palette;
4. native biome/environment lookup permitted but optional for the first geometry-write proof;
5. exact deterministic chunk results across generation order permutations;
6. seam-crossing islands remain continuous;
7. no planner invocation in the per-block hot path;
8. no Minecraft imports outside the adapter module.

## Non-goals for the first adapter

Do not require the first proof to solve:

- final biome compatibility;
- vegetation/features;
- caves or structures;
- fluids;
- lighting integration;
- multiplayer scheduling policy;
- final caching strategy;
- every Minecraft worldgen stage;
- every loader/version combination.

The adapter exists first to prove the boundary and obtain real performance/integration evidence.

## Module naming

Do not choose `skyforge-neoforge`, `skyforge-fabric`, or another loader-specific module name until the concrete integration target is confirmed. A temporary `skyforge-minecraft-reference` or similar module may be useful if a loader-neutral Minecraft API seam is genuinely practical, but this should not be assumed in advance.

## Evidence required before production direction

The first adapter should report:

- chunk dimensions and sampled vertical range;
- candidate island count per chunk;
- density/semantic evaluation counts;
- block-write count;
- cold and warm generation latency;
- cache footprint;
- deterministic output identity;
- seam checks across neighboring chunks.

Those measurements feed the live/preload/hybrid decision rather than being treated as incidental profiling.
