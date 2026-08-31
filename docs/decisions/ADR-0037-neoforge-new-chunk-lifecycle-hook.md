# ADR-0037: First NeoForge New-Chunk Lifecycle Hook

- **Status:** Implementation prepared; focused local validation pending
- **Date:** 2026-08-31
- **Work item:** SF-IMP-0033

## Context

SF-IMP-0031 proved concrete Minecraft chunk-coordinate/material-key projection. SF-IMP-0032 then proved strict live block-registry resolution and exact mutation of real Minecraft `ChunkAccess` storage through `ProtoChunk`.

The remaining gap was lifecycle integration: Skyforge could write a chunk when called directly, but no real NeoForge lifecycle callback delivered newly generated chunks to the adapter.

NeoForge 1.21.1 exposes `ChunkEvent.Load` on `NeoForge.EVENT_BUS`. Its `isNewChunk()` flag is true only for a newly generated chunk on the logical server. NeoForge also warns that this event can occur before the chunk is promoted to `FULL`, so event handlers must not perform arbitrary level/chunk loading from the callback.

NeoForge's 1.21.1 patch posts this event while promoting a generated protochunk to a loaded level chunk and marks `newChunk` according to whether the source was already an `ImposterProtoChunk`.

## Decision

SF-IMP-0033 promotes `skyforge-neoforge-1211` from a test-only exploded-mod identity to a real minimal NeoForge mod boundary:

- production `META-INF/neoforge.mods.toml` with mod id `skyforge`;
- real `@Mod("skyforge")` entrypoint;
- real `@EventBusSubscriber` listener on `NeoForge.EVENT_BUS`;
- realization only for `ChunkEvent.Load` where `isNewChunk()` is true;
- no neighboring-chunk or level lookup from inside the callback;
- no realization unless an explicit adapter-local runtime binding is installed.

The lifecycle path is:

```text
NeoForge ChunkEvent.Load
    -> require isNewChunk()
    -> require installed backend level binding
    -> use event ChunkAccess only
    -> materialize accepted Skyforge semantics for its owned build interval
    -> resolve live BlockStates
    -> write Skyforge solid overlay
```

## Additive composition rule discovered by integration

The exact SF-IMP-0032 writer writes every materialized position, including `minecraft:air`. That remains correct for isolated equivalence tests or a backend in which Skyforge owns the entire target interval.

It is not correct for ordinary Minecraft composition. Writing Skyforge AIR into a native generated chunk would erase backend terrain.

SF-IMP-0033 therefore adds a concrete Minecraft overlay mode:

```text
Skyforge solid -> write resolved Skyforge BlockState
Skyforge AIR   -> preserve existing Minecraft state
```

This is a backend composition rule, not a change to Skyforge density semantics. Skyforge AIR still means that Skyforge contributes no solid at that position.

The exact writer remains available and unchanged in meaning for isolated/dedicated ownership proofs.

## Runtime binding

The event subscriber is inert by default.

An adapter-local runtime binding supplies:

- a backend-side `LevelAccessor` predicate;
- the accepted `SkyforgeNeoForge1211ChunkAdapter`;
- the accepted `SkyforgeNeoForge1211ChunkWriter`.

The predicate exists because a concrete Minecraft integration must not accidentally apply one catalog to every dimension. This dimension/environment selection remains backend-owned and does not add dimension concepts to `skyforge-world`.

Only one binding can be installed at a time in this initial proof. The handle returned by installation removes exactly that binding when closed.

## Event safety

The callback does not request chunks from the level and does not rerun island/group/archipelago planning.

It consumes only:

- `event.getLevel()` for backend-owned scope selection;
- `event.getChunk()` as the target `ChunkAccess`;
- the already-installed backend-neutral world catalog through the accepted adapter.

This respects NeoForge's warning about chunk-loading deadlocks during `ChunkEvent.Load`.

## Lifecycle timing limitation

`ChunkEvent.Load(isNewChunk=true)` is deliberately accepted only as the first production-shaped lifecycle seam.

It is not yet the final terrain-generation stage because vanilla terrain, structures/features, and some derived chunk data may have been produced before this callback. Consequently SF-IMP-0033 does **not** claim that:

- vanilla placed features see Skyforge surfaces;
- structures fit Skyforge terrain;
- heightmaps are finalized correctly for all downstream consumers;
- lighting is finalized as if Skyforge terrain existed during earlier generation stages.

Those are explicit requirements for the next earlier-stage worldgen integration work.

## Focused acceptance criteria

SF-IMP-0033 requires:

1. the production `skyforge` mod is discovered and loaded by the real NeoForge FML JUnit runtime;
2. the lifecycle subscriber is wired to `NeoForge.EVENT_BUS`;
3. an event with `isNewChunk() == false` causes no Skyforge realization;
4. an event with `isNewChunk() == true` reaches the installed runtime binding;
5. a rejected level binding causes no mutation;
6. the callback touches only the event's own `ChunkAccess`;
7. Skyforge solid samples resolve and write to real Minecraft chunk storage;
8. Skyforge AIR preserves pre-existing backend-native blocks in overlay mode;
9. the exact SF-IMP-0032 writer remains valid for exact ownership/equivalence tests;
10. backend-neutral modules remain free of Minecraft/NeoForge imports;
11. repository-wide validation remains green.

## Explicit non-goals

SF-IMP-0033 does not yet add:

- a custom `ChunkGenerator`;
- an earlier chunk-status generation task integration;
- production world-plan/config bootstrap;
- a default engineering island in normal gameplay;
- biome-aware material selection;
- native feature/structure participation on Skyforge surfaces;
- explicit heightmap/lighting finalization policy;
- optional-mod compatibility;
- final live/preload/hybrid runtime strategy;
- production spatial/caching optimization.

## Consequence

After acceptance the Minecraft-facing stack becomes event-driven:

```text
NeoForge newly generated chunk
    -> Skyforge world-catalog realization
    -> terrain semantics
    -> Minecraft block keys
    -> live BlockStates
    -> additive native chunk storage
```

The next integration question is no longer whether Skyforge can receive and mutate a real generated chunk. It is **which earlier Minecraft generation stage should own Skyforge terrain so native heightmaps, lighting, features, and structures can reason about it correctly**.
