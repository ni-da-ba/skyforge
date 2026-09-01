# ADR-0046 — Early Generator Height Query Bridge

**Status:** Accepted

## Context

Skyforge terrain is physically written after vanilla surface construction. That timing is now sufficient for heightmap-driven feature decoration, especially with the accepted supplemental-surface path, but Minecraft structure starts are created earlier.

Minecraft's `ChunkGenerator` exposes non-mutating `getBaseHeight(...)` queries that early world-generation systems can use before chunk terrain is fully materialized. The custom `SkyforgeNoiseBasedChunkGenerator` previously inherited the vanilla noise-only answer, so an early caller could not observe an elevated Skyforge island even though the same island would later be written into the chunk.

## Decision

When a Skyforge runtime binding is active, `SkyforgeNoiseBasedChunkGenerator.getBaseHeight(...)` returns the higher of:

1. vanilla `NoiseBasedChunkGenerator` base height; and
2. the authoritative Skyforge materialization's height for the queried column and Minecraft heightmap type.

The Skyforge query is non-mutating. It materializes the relevant backend column/chunk from the already-compiled runtime, resolves its backend-owned block states, and applies Minecraft's own heightmap predicate. The answer follows Minecraft's height convention: one block above the highest matching block.

When no Skyforge binding is active, the generator remains exactly vanilla for this query.

## Why this precedes structure integration

Physically writing Skyforge earlier would entangle the engine with more of Minecraft's internal chunk-status pipeline and risk invalidating the post-surface integration already proven through SF-IMP-0036–0041.

A query bridge lets early native systems reason about Skyforge geometry before blocks exist, while retaining the accepted physical realization seam.

## Invariants

1. No chunk mutation occurs during an early height query.
2. No runtime binding means no Skyforge contribution.
3. Vanilla terrain remains part of the final answer through `max(vanilla, skyforge)`.
4. The query uses the accepted compiled runtime; world/group planning is not rerun.
5. Minecraft's own heightmap predicate determines whether a backend block state counts.
6. Backend-neutral modules remain free of Minecraft APIs.
7. Physical Skyforge realization remains post-surface.
8. This milestone does not claim that structures are already Skyforge-aware; it establishes the prerequisite query seam.

## Performance note

The first proof may materialize more data than an optimized height-only query ultimately requires. Query caching, column-specialized evaluation and other acceleration remain implementation optimizations after correctness is established.

## Acceptance evidence

The focused SF-IMP-0042 verifier and repository-wide `gradlew.bat check` both passed on 2026-08-31.

The accepted automated evidence establishes:

- inactive queries return no Skyforge answer;
- an active development binding reports the exact first-free Y above the Massif's highest matching solid;
- both `WORLD_SURFACE_WG` and `OCEAN_FLOOR_WG` recognize the current solid engineering palette;
- a query far outside the development specimen returns no Skyforge answer;
- the actual chunk generator compiles with the `getBaseHeight` bridge;
- accepted post-surface and feature-stage regressions remain green;
- backend independence and repository-wide checks remain green.

No separate client visual gate is required because this milestone changes a non-mutating pre-write query. The first visible consumer proof is deferred to the following structure-integration milestone.
