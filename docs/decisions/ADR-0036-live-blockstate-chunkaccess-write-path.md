# ADR-0036: Live BlockState Resolution and ChunkAccess Write Path

- **Status:** Accepted
- **Date:** 2026-08-31
- **Work item:** SF-IMP-0032

## Context

SF-IMP-0031 proved that a real Minecraft 1.21.1 / NeoForge 21.1 adapter can translate `ChunkPos` ownership into Skyforge world bounds, query the accepted world catalog, classify backend-neutral terrain semantics, and project them to concrete Minecraft block registry keys while preserving occupancy and chunk-seam continuity.

That proof intentionally stopped before live registry resolution and chunk mutation. SF-IMP-0032 demonstrates that the accepted representation can be resolved through Minecraft's actual block registry and stored in an actual Minecraft `ChunkAccess` implementation without moving planning or Minecraft concepts upstream.

## Decision

SF-IMP-0032 accepts two adapter-owned stages:

```text
accepted MinecraftChunkMaterialization
    -> strict Minecraft block-registry lookup
    -> live BlockState
    -> ChunkAccess.setBlockState(...)
```

No new abstraction is added to `skyforge-world`.

## Strict registry resolution

`MinecraftBlockStateResolver` resolves a `ResourceLocation` through `BuiltInRegistries.BLOCK` and returns that block's default `BlockState`.

Unknown keys fail explicitly.

This is intentional. Minecraft's block registry is defaulted, so blindly calling `get` on an unknown identifier can conceal registry drift by returning the registry default. Skyforge must not silently turn a stale or missing compatibility key into valid terrain.

The rule is therefore:

```text
containsKey(key) == false
    -> explicit adapter failure
```

This directly carries forward a major Aetherial Companion lesson: optional integration must prove that the active registry contains the identifier it intends to use.

## Chunk ownership and vertical ownership

`SkyforgeNeoForge1211ChunkWriter` accepts exactly:

- one real Minecraft `ChunkAccess`;
- one already-accepted `MinecraftChunkMaterialization`.

Before mutation it verifies:

1. the materialization `ChunkPos` exactly equals the target chunk's `ChunkPos`;
2. the materialization vertical interval lies inside the target chunk's build-height interval.

The writer never writes positions outside the materialization's owned 16 x H x 16 block coordinates.

## Occupancy authority

For every block key, the writer resolves a live `BlockState` and verifies:

```text
minecraft:air key <=> resolved state is air
non-air key        <=> resolved state is non-air
```

This ensures registry resolution cannot change the authoritative Skyforge solid/air decision.

The writer then calls real Minecraft `ChunkAccess.setBlockState(...)` and immediately verifies the same state is readable through `ChunkAccess.getBlockState(...)`.

## Real NeoForge test runtime

The accepted proof runs under ModDevGradle's FML-aware JUnit environment rather than treating Minecraft as an ordinary Java library.

The focused verifier demonstrated:

- Gradle launching the NeoForge test JVM on provisioned Java 21;
- ModLauncher/FML startup for Minecraft 1.21.1 / NeoForge 21.1.249;
- test-only `skyforge_adapter` mod discovery through an exploded development-mod layout;
- a test-only `META-INF/neoforge.mods.toml`, so production adapter resources still do not claim a distributable mod lifecycle;
- JUnit 5.14.1 in the NeoForge adapter module, matching the ModDevGradle integration-test stack;
- real vanilla block-registry initialization before the Skyforge assertions execute.

The test-only mod metadata is infrastructure for the FML integration harness, not a production mod entrypoint.

## Real chunk implementation used by the proof

The focused test uses Minecraft's real `ProtoChunk` implementation rather than a fake or hand-written `ChunkAccess` subclass.

A minimal test-only biome registry is constructed because `ProtoChunk` sections require biome storage. The synthetic biome is registered under `Biomes.PLAINS`, because vanilla `LevelChunkSection` initialization requires the plains key to exist in the supplied registry.

The proof therefore exercises actual Minecraft 1.21.1:

- block registry lookup;
- default `BlockState` creation;
- `ProtoChunk` section allocation;
- `ChunkAccess.setBlockState`;
- `ChunkAccess.getBlockState`;
- negative chunk coordinates;
- adjacent chunk ownership.

It still does not launch a normal game/server world or register a production world-generation lifecycle hook.

## Seam proof

The same deterministic Massif specimen used for SF-IMP-0031 is materialized across chunks `(-1, 0)` and `(0, 0)`.

Both materializations are written into independent real `ProtoChunk` instances. At least one corresponding `(y,z)` row remains non-air on both stored states at world x=-1 and x=0.

This proves the accepted seam invariant survives registry resolution and actual Minecraft chunk-section storage.

## Accepted gates

SF-IMP-0032 passed both required local gates on 2026-08-31:

1. `scripts\verify-sf-imp-0032-chunk-writer.bat` — **PASS**;
2. repository-wide `gradlew.bat check` — **PASS**.

No visual gate is required because SF-IMP-0032 does not alter morphology, density geometry, terrain-semantic classification, or spatial composition. The relevant proof is exact state/occupancy/read-back behavior in real Minecraft storage.

## Acceptance criteria

SF-IMP-0032 demonstrates:

1. known vanilla block keys resolve to their actual live default `BlockState`s;
2. an unknown block key fails explicitly instead of falling through the defaulted block registry;
3. accepted materializations write into real `ProtoChunk` instances;
4. every stored state exactly equals the state resolved from the accepted materialization key;
5. the number of stored non-air states equals the accepted materialization solid count;
6. positions immediately outside the written vertical interval remain untouched;
7. the x=-1 / x=0 island seam remains continuous after real chunk storage;
8. a materialization cannot be written into the wrong `ChunkPos`;
9. a materialization cannot exceed the target chunk's vertical build interval;
10. backend-neutral modules remain free of Minecraft/NeoForge imports;
11. repository-wide validation remains green.

## Explicit non-goals

SF-IMP-0032 does not yet add:

- a production NeoForge `ChunkGenerator` or worldgen lifecycle registration;
- a normal server/game launch acceptance test;
- biome-aware material selection;
- replacement of native terrain outside Skyforge-owned positions;
- heightmap or lighting finalization policy;
- structure/feature generation;
- optional-mod registry adapters;
- async scheduling or production region caching;
- persistence of pre-materialized chunk results.

Those concerns become meaningful only after the core live storage write path is proven.

## Consequence

The Minecraft-facing pipeline is now accepted through:

```text
Skyforge world catalog
    -> accepted terrain semantic
    -> Minecraft block key
    -> live Minecraft BlockState
    -> real Minecraft chunk storage
```

The remaining gap to an in-game proof is lifecycle integration rather than geometry or representation translation.
