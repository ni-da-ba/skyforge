# SF-IMP-0032 Live Chunk Writer Acceptance

- **Work item:** SF-IMP-0032
- **Status:** Accepted
- **Date:** 2026-08-31
- **Accepted runtime/code head:** `b10b52fdeb42618966e4a4398ee9483cc5c7f854`
- **Base main merge commit:** `27bd3fd7bb92872f5ec34aa8d019d72207e60f0f`

## Scope

SF-IMP-0032 extends the accepted NeoForge 1.21.1 adapter from immutable Minecraft block-key materialization into live registry resolution and actual Minecraft chunk storage.

Accepted path:

```text
Skyforge world catalog
    -> terrain semantic
    -> Minecraft ResourceLocation block key
    -> strict live block-registry lookup
    -> live BlockState
    -> real ProtoChunk / ChunkAccess storage
```

No Minecraft or NeoForge concept was introduced into `skyforge-world`, `skyforge-recipes`, `skyforge-model`, or `skyforge-kernel`.

## Focused local gate

Command:

```bat
scripts\verify-sf-imp-0032-chunk-writer.bat
```

Result: **PASS**.

Verifier stages:

1. workspace Java runtime check;
2. backend-neutral module-independence check;
3. Minecraft/NeoForge compile-link proof;
4. live registry resolution and real `ProtoChunk` write/read-back proof under the FML-aware NeoForge JUnit runtime.

## Repository-wide gate

Command:

```bat
gradlew.bat check
```

Result: **PASS**.

Observed final run:

```text
BUILD SUCCESSFUL in 9s
33 actionable tasks: 2 executed, 31 up-to-date
Configuration cache entry stored.
```

Hosted GitHub Actions are not the project acceptance authority because the hosted allowance is exhausted; local validation remains authoritative.

## Concrete runtime facts proven

The accepted implementation demonstrates all of the following against Minecraft 1.21.1 / NeoForge 21.1.249:

- `BuiltInRegistries.BLOCK` resolves known vanilla block keys to live default `BlockState` instances;
- unknown block IDs fail explicitly before Minecraft's defaulted registry can conceal registry drift;
- accepted `MinecraftChunkMaterialization` data writes into real Minecraft `ProtoChunk` instances through `ChunkAccess.setBlockState(...)`;
- every written state reads back exactly through `ChunkAccess.getBlockState(...)`;
- registry resolution preserves the authoritative Skyforge AIR/solid decision;
- written non-air count equals the accepted materialization solid count;
- the writer rejects a materialization for the wrong `ChunkPos`;
- the writer rejects a vertical interval outside the target chunk build range;
- positions outside the accepted written interval remain untouched;
- the deterministic Massif remains continuous across the x=-1 / x=0 ownership seam after actual Minecraft chunk-section storage.

## NeoForge integration harness facts

The accepted test path also establishes a working integration-test environment for subsequent backend work:

- Gradle provisions and launches Java 21 for the NeoForge test JVM while the workspace may run Gradle on JDK 25;
- ModLauncher/FML starts in `forgejunitdev` mode;
- the adapter module uses ModDevGradle's FML-aware JUnit support rather than plain classpath-only Minecraft tests;
- the adapter's integration-test stack uses JUnit 5.14.1, matching ModDevGradle's proven integration-test setup;
- test-only `META-INF/neoforge.mods.toml` metadata makes the exploded `skyforge_adapter` development mod valid for FML without declaring a production mod lifecycle;
- the real `ProtoChunk` fixture satisfies `LevelChunkSection` biome initialization by providing `Biomes.PLAINS` in its test registry.

These are integration-harness requirements, not new Skyforge semantic contracts.

## Acceptance invariants

| Invariant | Result |
| --- | --- |
| Backend-neutral modules contain no Minecraft/NeoForge imports | PASS |
| Known block keys resolve to exact live default states | PASS |
| Missing block key fails explicitly | PASS |
| Real `ProtoChunk` receives accepted materialization | PASS |
| Exact state read-back after write | PASS |
| AIR/solid occupancy preserved | PASS |
| Stored solid count equals materialized solid count | PASS |
| Wrong chunk target rejected | PASS |
| Out-of-range vertical write rejected | PASS |
| Outside-interval storage untouched | PASS |
| x=-1 / x=0 seam survives real storage | PASS |
| Repository-wide validation | PASS |

## Visual gate

No visual gate is required for SF-IMP-0032.

This work item does not change island morphology, density geometry, terrain-semantic classification, group/archipelago composition, or accepted visual evidence. The relevant new invariant is exact translation into real Minecraft registry/state/storage behavior, which is covered by deterministic state and occupancy assertions.

## Explicitly deferred

SF-IMP-0032 does **not** accept:

- a production NeoForge world-generation lifecycle hook;
- a normal server/game launch acceptance proof;
- a permanent `ChunkGenerator` architecture;
- biome-aware terrain palettes;
- heightmap or lighting finalization policy;
- structures, placed features, vegetation, ores, caves, or fluids;
- optional third-party mod adapters;
- live/preload/hybrid runtime policy;
- production caching or spatial indexing.

## Next boundary

The next integration step should be the smallest production-shaped NeoForge lifecycle path that hands an actual generated chunk to the already-accepted materializer/writer. The lifecycle proof should reuse the accepted world catalog and writer rather than introducing another geometry path or speculative cross-loader abstraction.
