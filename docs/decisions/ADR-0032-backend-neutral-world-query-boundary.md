# ADR-0032: Backend-Neutral World Query Boundary

- **Status:** Accepted; world-catalog and tiled-backend proofs locally validated
- **Date:** 2026-08-30
- **Work item:** SF-IMP-0028

## Context

SF-IMP-0027 accepts a deterministic hierarchy from morphology provider to island to group to archipelago. The next requirement is not another planning level. A game/world backend must be able to ask which independently compiled Skyforge volumes can affect one spatial region without embedding morphology, group, or archipelago planning logic into chunk generation.

Minecraft is an important eventual backend, but the first runtime contract should not use Minecraft classes, chunk types, or block APIs. Otherwise Skyforge's semantic architecture would become coupled to one realization target before the backend requirements are understood.

## Decision

SF-IMP-0028 introduces a new `skyforge-world` module between recipe/planning code and concrete game backends.

The accepted boundary is:

```text
accepted archipelago plan
        -> independent island compilation
        -> bounded SkyIslandWorldVolume entries
        -> immutable SkyIslandWorldCatalog
        -> query(WorldBounds)
        -> backend-relevant compiled volumes
        -> backend realization
```

Concrete backends depend on the world catalog instead of rerunning group/archipelago planning during region generation.

## World bounds

`WorldBounds` is a finite axis-aligned world-space box with closed bounds. Intersection is boundary-inclusive.

Boundary inclusion is intentionally conservative. Adjacent backend regions may both receive an island whose reservation touches their shared boundary. Duplicate consideration is preferable to false-negative culling that creates terrain seams or missing geometry.

The first catalog uses a deterministic linear scan. The API does not expose that implementation choice, allowing later replacement with a grid, interval structure, R-tree, or backend-specific cache without changing query semantics.

## Explicit provider-safe bounds

Horizontal bounds use the explicit `reservedHorizontalRadius` already carried by every accepted island member plan.

Vertical bounds are **not** inferred from descriptor `upperElevation` or `undersideDepth`. The explicit morphology provider SPI permits custom providers to author surfaces that do not obey built-in formula assumptions. Until providers can supply certified spatial bounds, SF-IMP-0028 therefore requires a conservative `SkyIslandWorldVerticalReservation` around each suspension elevation.

This is deliberately conservative. A backend may perform extra work, but it must not silently omit provider-authored geometry because Skyforge guessed a bound that the provider contract never guaranteed.

## Stable volume identity

Each `SkyIslandWorldVolume` carries `SkyIslandWorldVolumeId` containing:

- archipelago root seed;
- stable child-group identifier;
- child-group ordinal;
- member ordinal;
- deterministic geometry seed.

This preserves nested deterministic identity after the planner objects themselves leave the backend hot path.

## Compiled graph handoff

Each world entry carries the existing backend-neutral `CompiledSkyIslandVolume` graph set.

The world module does not prescribe how a backend evaluates, translates, caches, vectorizes, or lowers those graphs. A Minecraft adapter, voxel exporter, native terrain backend, or offline preloader may all consume the same bounded compiled volume entry differently.

The world catalog itself contains no `MorphologyFamily` switch and performs no provider-specific dispatch. Provider resolution occurs once during catalog compilation through the accepted morphology-spec compiler.

## World-catalog acceptance

The focused SF-IMP-0028 world-boundary proof demonstrates:

1. invalid world bounds and vertical reservations fail early;
2. closed bounds treat exact boundary contact as relevant;
3. an accepted archipelago compiles to exactly one world volume per planned island;
4. world identities preserve root/group/member/geometry identity in deterministic plan order;
5. repeated catalog compilation preserves world identities and conservative bounds exactly;
6. a local region query returns only spatially relevant volumes;
7. an enclosing regional query returns all volumes in stable plan order;
8. an empty-sky query returns no volumes;
9. vertical queries above the explicit reservation cull the island;
10. a query touching the exact vertical reservation boundary still returns the island.

The first local run exposed one unrelated over-strict group-request invariant: pairwise member spacing was required even for a one-member group. SF-IMP-0028 corrected `SkyIslandGroupRequest` so pairwise spacing is enforced only when two or more members exist, and added a focused regression. Multi-member spacing behavior is unchanged.

The user-reported local Java 25 verifier completed successfully after that correction.

## Reference tiled backend

SF-IMP-0028 also accepts `ReferenceTiledSkyIslandBackend`, a material-neutral backend proof that consumes only the world catalog and compiled density graphs.

A backend region is represented by `WorldSampleGrid`, which defines one deterministic global lattice. Tiling partitions the lattice by integer sample indexes rather than by independently rounded world coordinates. Each sample index therefore belongs to exactly one tile even though conservative catalog query bounds remain closed and may return the same island to adjacent tiles.

For each tile the backend:

1. constructs the tile's closed world-space query bounds;
2. calls `SkyIslandWorldCatalog.query(...)` independently;
3. evaluates only the returned compiled density graphs;
4. writes only the tile's disjoint lattice indexes into the regional occupancy array.

Compiled density evaluators are cached by stable `SkyIslandWorldVolumeId`; planner or morphology-provider logic is not invoked during tile realization.

## Tiled equivalence and seam acceptance

The tiled backend proof demonstrates:

1. monolithic realization and tiled realization produce byte-identical regional occupancy;
2. the occupancy SHA-256 is identical for monolithic and tiled execution;
3. changing tile dimensions, including irregular partial edge tiles, does not change occupancy;
4. repeated tiled realization is deterministic;
5. an island deliberately crossing a tile boundary remains occupied on both sides of the seam;
6. independent per-tile catalog queries do not omit seam-crossing geometry;
7. conservative query overlap cannot cause duplicate voxel ownership because tiles own disjoint lattice indexes;
8. tiled candidate references are lower than naive `tileCount * catalogVolumeCount`, proving that regional culling is active;
9. invalid grid and tile parameters fail deterministically.

The user-reported local Java 25 verifier completed successfully for the catalog and tiled-backend tests.

## Live generation versus preloading

The accepted boundary deliberately does **not** choose between live generation and preloaded realization.

The same contract supports all three policies:

### Live region/chunk realization

A backend queries the world catalog for the current region and evaluates only returned volumes. The tiled equivalence proof establishes that generation order and tile boundaries need not change geometry.

### Preloaded regional realization

A backend queries a larger planned region once, evaluates it ahead of time, and stores a backend-specific cache or materialized world representation. Skyforge's world catalog remains the source of deterministic geometry identity.

### Hybrid realization

A backend may preload archipelago-scale regions or other coarse cells and serve smaller chunk requests from the cached result, falling back to direct world-catalog evaluation where needed.

No one policy is declared superior yet. The correct Minecraft integration policy should be chosen from measured generation latency, memory footprint, cache hit behavior, world-edit requirements, server concurrency, and acceptable first-load cost rather than from architecture speculation.

## Deliberate non-goals

SF-IMP-0028 does not yet define:

- Minecraft chunk classes or block palettes;
- biome/material interpretation;
- production graph evaluation strategy;
- provider-certified spatial bounds;
- production spatial acceleration structure;
- persistent world-plan serialization;
- cross-session cache format;
- asynchronous chunk scheduling;
- the final live/preload/hybrid policy.

Those concerns can now be added above or behind the accepted world-query boundary without changing island/group/archipelago planning semantics.

## Consequences

Skyforge now has an accepted backend-neutral path from regional semantic planning to independently queryable runtime geometry:

```text
morphology provider
    -> island
    -> group
    -> archipelago
    -> world catalog
    -> region/tile query
    -> deterministic density realization
```

This is sufficient to begin a concrete Minecraft-like adapter or a backend performance harness without coupling core planning APIs to Minecraft.

## Next step

Do not add another spatial hierarchy level. The next work should measure or realize one of the downstream concerns now enabled by this boundary. The preferred sequence is:

1. establish a material/biome interpretation contract that can convert geometric occupancy into backend-neutral terrain semantics;
2. add a Minecraft-like voxel/chunk reference adapter over the same `SkyIslandWorldCatalog`;
3. benchmark live, preloaded, and hybrid region realization using identical deterministic worlds;
4. choose production caching/streaming policy from measured evidence.
