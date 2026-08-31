# ADR-0032: Backend-Neutral World Query Boundary

- **Status:** Proposed; focused world-catalog proof pending local validation
- **Date:** 2026-08-30
- **Work item:** SF-IMP-0028

## Context

SF-IMP-0027 accepts a deterministic hierarchy from morphology provider to island to group to archipelago. The next requirement is not another planning level. A game/world backend must be able to ask which independently compiled Skyforge volumes can affect one spatial region without embedding morphology, group, or archipelago planning logic into chunk generation.

Minecraft is an important eventual backend, but the first runtime contract should not use Minecraft classes, chunk types, or block APIs. Otherwise Skyforge's semantic architecture would become coupled to one realization target before the backend requirements are understood.

## Decision

SF-IMP-0028 introduces a new `skyforge-world` module between recipe/planning code and concrete game backends.

The first boundary is:

```text
accepted archipelago plan
        -> independent island compilation
        -> bounded SkyIslandWorldVolume entries
        -> immutable SkyIslandWorldCatalog
        -> query(WorldBounds)
        -> backend-relevant compiled volumes
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

## Initial acceptance requirements

The focused SF-IMP-0028 world-boundary proof must demonstrate:

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

## Deliberate non-goals

This first boundary does not yet define:

- Minecraft chunk classes or block palettes;
- biome/material interpretation;
- production graph evaluation strategy;
- provider-certified spatial bounds;
- spatial acceleration structure;
- persistent world-plan serialization;
- cross-session cache format;
- asynchronous chunk scheduling;
- preloading/streaming policy.

Those concerns should be added above or behind this boundary once the basic region-query contract is locally proven.

## Next step

After the focused catalog proof passes, SF-IMP-0028 should add a reference backend adapter that consumes a `WorldBounds` region and the returned compiled volumes to produce deterministic region occupancy/material-neutral density evidence. That proof will measure query selectivity and verify that querying independently by region reproduces the same geometry as evaluating the corresponding complete regional catalog.
