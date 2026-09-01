# SF-IMP-0042 — Early Height Query Acceptance

**Status:** Accepted  
**Date:** 2026-08-31

## Accepted scope

SF-IMP-0042 adds a non-mutating early generator query bridge so Minecraft consumers can observe authoritative Skyforge elevation before the later post-surface block write occurs.

When the runtime binding is active, `SkyforgeNoiseBasedChunkGenerator.getBaseHeight(...)` combines vanilla and Skyforge answers using the higher first-free Y. When no binding is active, the generator remains vanilla for the query.

## Automated evidence

The focused verifier passed:

```bat
scripts\verify-sf-imp-0042-early-height-queries.bat
```

The repository-wide gate also passed:

```bat
gradlew.bat check
```

The accepted suite proves:

- inactive queries provide no Skyforge contribution;
- the development Massif is visible through the early query before any chunk mutation;
- returned Skyforge height follows Minecraft's first-free-Y convention;
- `WORLD_SURFACE_WG` and `OCEAN_FLOOR_WG` both recognize the current solid engineering palette;
- distant columns outside the specimen do not receive false Skyforge height;
- the generator returns `max(vanilla, Skyforge)` rather than replacing native terrain height;
- the accepted post-surface and supplemental-feature regressions remain green;
- backend-neutral module independence remains intact.

## Accepted invariants

1. Height queries do not mutate chunks.
2. The early query consumes the already-compiled Skyforge runtime rather than rerunning world composition.
3. Vanilla remains part of the answer.
4. Minecraft heightmap predicates remain backend-owned.
5. Physical Skyforge realization stays at the accepted post-surface seam.
6. No Minecraft height/query concept enters backend-neutral modules.
7. This milestone is a prerequisite for structure integration; it does not by itself prove that a structure start will use or fit Skyforge terrain.

## Why there is no client gate

SF-IMP-0042 changes an early non-mutating generator query. There is no meaningful new visual artifact to inspect independently of a consumer that uses the query. The first visible acceptance target is therefore deferred to the structure-placement milestone that follows.

## Deferred work

- identify a concrete native structure-start path that consumes generator height information;
- prove a structure can select or fit an elevated Skyforge surface without moving terrain writes earlier;
- structure-specific footprint, support, collision and terrain-adaptation suitability;
- performance optimization of repeated early column queries;
- production world-plan/config bootstrap and later Skyforge-owned biome/environment fields.
