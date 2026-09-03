# Skyforge Current Runtime Architecture

**Snapshot:** 2026-09-03 (America/Chicago)  
**Accepted Minecraft integration through:** SF-IMP-0057  
**Accepted branch:** `main`  
**Accepted integration merge:** `9b93ebd25a7917b7b934e89144480c1f026c286c`  
**Current Minecraft follow-on:** SF-IMP-0051 / issue #52 — cross-volume terrain-matching structure projection  
**Parallel lane:** backend-neutral Skyforge authorship, developed independently under `auth/*`

This document is the concise current-state runtime architecture and implementation handoff. Milestone acceptance records and ADRs remain authoritative for individual contracts.

## Executive state

Skyforge is a deterministic, backend-neutral procedural world-synthesis engine. Minecraft 1.21.1 through NeoForge 21.1 is the first realization backend, not the foundation of the world model.

The accepted Minecraft path can now:

- compile semantic island intent into deterministic finite suspended volumes;
- compose independent islands without collapsing vertically stacked X/Z regions into one global surface;
- query exact three-dimensional ownership through the backend-neutral world catalog;
- preserve ordinary Minecraft BASE_WORLD generation as a separate generation domain;
- realize Skyforge-owned solid terrain additively into real chunks;
- scope height and biome behavior to one exact island-owned operation;
- reuse live registered Minecraft biome vegetation inside exact volumes;
- coordinate native surface population idempotently per volume/chunk/phase;
- survey an entire finite Skyforge volume for native occupancy before destructive realization;
- reject conflicting volumes atomically without mutating the native conflict;
- admit clear volumes only after complete finite footprint evidence exists;
- defer catch-up until target chunks are already stable and loaded, without forcing future generation;
- preserve Minecraft native post-processing work while deferred population runs on stable chunks;
- synchronize deferred stable-chunk terrain mutations to lighting and tracking clients.

The principal unresolved Minecraft integration defect is no longer physical collision or deferred population lifecycle. It is **cross-domain terrain projection by native structures**: a structure rooted in one world domain must not use an unrelated vertically stacked Skyforge surface merely because a vanilla heightmap reports it as highest at the same X/Z.

## Architectural ownership

Module ownership remains:

- `skyforge-kernel` — coordinates, field contracts, typed procedural graphs, canonical serialization, validation, deterministic reference evaluation.
- `skyforge-model` — backend-neutral semantic descriptors and validation.
- `skyforge-recipes` — deterministic compilation from semantic/geological intent to procedural graphs.
- `skyforge-world` — exact world volumes, placement, ownership, support/composition policy, and backend-neutral authored world semantics.
- `skyforge-reference` — deterministic corpora, sampled evidence, topology/morphology analysis, hashes and visual diagnostics.
- `skyforge-neoforge-1211` — Minecraft/NeoForge realization, registry translation, exact-volume lifecycle integration, development fixtures and packaging.

Dependency direction is strictly downstream:

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

Minecraft and NeoForge concepts may not leak upward into the neutral engine merely to simplify an adapter problem.

Guiding rule:

> Skyforge decides what bounded world should exist; a backend decides how to express that authored world using its own mature content and runtime machinery.

## Runtime domain model

Minecraft and Skyforge are treated as separate generation domains.

```text
Minecraft BASE_WORLD
    -> native terrain
    -> native structures
    -> native generation lifecycle

Skyforge island domain
    -> exact finite volume
    -> physical admission
    -> additive terrain realization
    -> exact-volume biome/population execution
    -> deferred stable-chunk lifecycle adaptation when necessary
```

BASE_WORLD generation does not globally see all Skyforge terrain as another highest surface. An explicit Skyforge operation may opt into one exact island domain.

The mature model must continue to support multiple independent domains at identical X/Z positions, for example:

```text
Y=400  Skyforge island A
Y=250  Skyforge island B
Y=70   Minecraft BASE_WORLD
```

No global column-level answer may collapse those domains together.

## Exact-volume realization

Normal additive composition uses:

```text
Skyforge solid -> write resolved Skyforge BlockState
Skyforge AIR   -> preserve existing backend state
```

AIR means absence of Skyforge ownership, not permission to erase Minecraft terrain.

A planned volume may not write until physical admission reaches a terminal result.

## SF-IMP-0056 — physical volume admission

Acceptance record: [`SF-IMP-0056-physical-admission-acceptance.md`](../reviews/SF-IMP-0056-physical-admission-acceptance.md)

SF-IMP-0056 separated two previously conflated concerns:

```text
generation-domain isolation != physical occupancy compatibility
```

The accepted lifecycle is:

```text
PLANNED -> ADMITTED
PLANNED -> REJECTED
```

Properties:

- evidence is collected only for Skyforge-owned solid coordinates;
- any detected native occupancy conflict rejects the whole planned volume;
- rejection is terminal and leaves the native conflict unchanged;
- admission occurs only after every required chunk in the finite footprint has reported evidence;
- unresolved volumes fail closed and may not partially realize;
- deferred realization stores immutable work descriptions rather than retaining mutable generation chunks/regions;
- catch-up uses `ServerChunkCache#getChunkNow` and therefore does not create generation tickets for unavailable chunks;
- native surface population begins only after physical admission.

Accepted runtime proof:

- lower volume rejected on native bedrock and preserved the conflict;
- upper volume admitted after 25 / 25 required chunks;
- pending catch-up reached 0;
- upper terrain materialized;
- 21 / 21 actual surface-bearing chunks completed native population.

The 25 required admission chunks and 21 surface-population chunks are intentionally different sets: footprint corner chunks may be required for complete occupancy evidence while containing no exact island surface.

## SF-IMP-0057 — deferred native lifecycle semantics

Acceptance record: [`SF-IMP-0057-deferred-post-processing-acceptance.md`](../reviews/SF-IMP-0057-deferred-post-processing-acceptance.md)

SF-IMP-0057 repaired two lifecycle differences exposed by stable loaded-chunk catch-up.

### Native post-processing preservation

A promoted `LevelChunk` may legitimately retain native post-processing marks. Therefore deferred Skyforge population must not assume the queue is empty and must not consume unrelated Minecraft work.

Accepted behavior:

1. snapshot/detach pre-existing native post-processing marks for the touched chunk;
2. run one exact Skyforge population operation with an isolated live queue;
3. resolve Skyforge-created marks through `LevelChunk#postProcessGeneration()` while exact-volume scope remains active;
4. require the isolated Skyforge queue to drain;
5. restore the native snapshot unchanged;
6. on abnormal scope completion, clear unflushed Skyforge marks and restore native state before failing closed.

Direct `WorldGenRegion` population remains unchanged.

### Stable-chunk mutation synchronization

Low-level generation-style chunk writes are sufficient before a chunk becomes client-visible but are not lifecycle-equivalent after promotion to a stable loaded chunk.

Deferred catch-up therefore enters a narrow stable-chunk mutation scope that submits actual changed positions to Minecraft lighting and block-change synchronization. It does **not** replace the generation writer with ordinary gameplay-style block updates or introduce broad neighbor-update side effects.

This repaired the observed state in which authoritative server collision existed while a client rendered part of the island as stale/invisible/dark.

## Native population scope

The currently accepted generic native population phase remains deliberately narrow: **`VEGETAL_DECORATION`**.

Skyforge can resolve an exact volume to a final-registry Minecraft biome during its owning operation and invoke the biome's registered native feature list through Minecraft machinery.

Current exclusions include:

- ores and general underground decoration;
- carvers/cave systems;
- complete hydrology realization;
- structures generated as island-owned local-world content;
- top-layer modification beyond currently admitted paths;
- persistent client-visible authored biome identity.

These are future bounded-miniature-world capabilities, not evidence that the current surface-population path is incomplete for its accepted scope.

## Biome semantics and future authorship

The current Minecraft bridge is transitional: it can make an exact island population operation execute against a selected registered biome, but Minecraft biome identity is not yet the upstream semantic source of an island.

The intended direction is:

```text
Skyforge island identity
    -> authored environmental fields
    -> local ecological classification
    -> backend biome expression
    -> Minecraft registered biome / native content
```

A sufficiently large island should ultimately behave as a bounded miniature world and may contain several real vanilla/modded biome expressions, structures, caves, water systems and other local-world content where spatial support permits.

The parallel `auth/*` lane is developing those upstream backend-neutral semantics independently. The Minecraft implementation lane should consume them later through an explicit contract rather than duplicating or constraining them with Minecraft-specific ontology.

Issue #78 / SF-IMP-0058, client-visible exact-volume biome presentation, remains deliberately deferred until that upstream authorship direction makes the persistent representation worth finalizing.

## Active Minecraft boundary — issue #52 / SF-IMP-0051

Issue #52 records a concrete stacked-surface defect observed during the SF-IMP-0050 proof.

A vanilla village rooted in ordinary lower Overworld terrain projected stray terrain-matching path/plank blocks onto a vertically separated Skyforge island above it. No village building itself belonged to the island. The likely mechanism is vanilla jigsaw `terrain_matching` projection consulting a global heightmap such as `WORLD_SURFACE_WG` and therefore selecting the unrelated upper surface.

Required invariant:

> Terrain adaptation/projection for a native structure must not cross onto an unrelated vertically separated world volume merely because that volume is the highest heightmap surface at the same X/Z.

Constraints:

- preserve the lower native structure when possible;
- do not add a village blacklist;
- do not build a per-structure compatibility table;
- unknown/modded jigsaw structures using ordinary projection mechanics should benefit automatically;
- stacked Skyforge islands must remain independent;
- vanilla behavior must remain unchanged when no vertical-domain ambiguity exists;
- explicit future multi-volume structures may span volumes only through an operation that deliberately owns those volumes.

### Investigation order

1. locate the vanilla jigsaw terrain-matching height/surface query seam used during piece projection/placement;
2. determine whether the structure execution context already carries enough information to identify its owning world domain or structure reference surface;
3. prefer a structure-scoped height/surface view over copying vanilla structure-placement logic;
4. constrain projection rather than reject the whole structure;
5. add a deterministic stacked-surface fixture that proves lower BASE_WORLD structure projection does not touch an upper Skyforge volume;
6. add the reciprocal proof needed for future structures rooted inside a Skyforge island;
7. preserve an unambiguous control case showing ordinary vanilla terrain matching is unchanged.

Do not implement this by globally hiding Skyforge terrain from all structure logic; island-owned structures will eventually need to see their own local terrain.

## Non-regression rules

- BASE_WORLD generation remains observationally isolated from Skyforge unless an explicit operation owns a Skyforge domain.
- Exact ownership is three-dimensional, not column-global.
- PLANNED physical volumes fail closed.
- REJECTED volumes leave no destructive terrain/population work behind.
- ADMITTED deferred catch-up never forces unavailable chunks.
- Native population never begins before admission when the physical-admission stage is installed.
- Deferred population preserves unrelated native post-processing state.
- Stable-chunk deferred writes synchronize lighting/client state without changing normal generation writer semantics.
- Population replay remains idempotent.
- Mutable generation chunks/regions are not retained as long-lived deferred work.
- Backend-neutral modules remain free of Minecraft/NeoForge APIs.
- Deterministic identity, exact volume ownership and canonical evidence behavior remain stable unless an explicit versioned contract changes them.

## Next implementation procedure

The Minecraft implementation lane should now continue issue #52 from current accepted `main`.

1. branch from the latest `main` only after checking whether the authorship lane has advanced it;
2. keep all #52 implementation changes on a dedicated `agent/*` branch;
3. do not modify `auth/*` branches or fold authorship semantic work into the structure fix;
4. first build the smallest reliable observation seam for terrain-matching projection before changing behavior;
5. preserve a control case proving normal vanilla projection still works;
6. require exact-head CI plus a dedicated runtime/visual proof before merge;
7. add a permanent acceptance record under `docs/reviews/` only after the runtime invariant is demonstrated.

The target is not merely to remove stray village blocks. The target is to establish **structure-owned terrain projection** as another bounded-world invariant that will later support both native BASE_WORLD structures and structures legitimately generated inside Skyforge miniature worlds.
