# SF-IMP-0031 NeoForge Adapter Acceptance

- **Date:** 2026-08-31
- **Status:** ACCEPTED
- **Validated runtime head:** `b5befa64117615a3076c0bc1fffdeb1caf10dd0e`
- **Backend proof target:** Minecraft 1.21.1 / NeoForge 21.1.249

## Scope accepted

SF-IMP-0031 establishes the first concrete Minecraft-facing adapter boundary without moving Minecraft concepts into backend-neutral Skyforge modules.

Accepted runtime path:

```text
Minecraft ChunkPos + Y interval
    -> exact world-space chunk bounds
    -> SkyIslandWorldCatalog.query(...)
    -> relevant compiled island volumes only
    -> SkyIslandTerrainInterpreter
    -> SkyIslandTerrainSampleContext
    -> Minecraft ResourceLocation block-key palette
    -> immutable chunk materialization
```

The adapter is implemented in `skyforge-neoforge-1211` and depends downstream on `skyforge-world`.

## Focused local verifier

Command:

```bat
scripts\verify-sf-imp-0031-neoforge-adapter.bat
```

Result: **PASS**.

The verifier demonstrated:

- backend-independence enforcement includes `skyforge-world`;
- Gradle provisioned/used the Java 21 toolchain required by Minecraft 1.21.1;
- ModDevGradle downloaded Mojang metadata and Minecraft artifacts;
- NeoForge mappings and patches were applied;
- Minecraft/NeoForge sources/artifacts were prepared successfully;
- the production Skyforge adapter compiled against real Minecraft 1.21.1 classes;
- the Minecraft-aware test source set compiled successfully;
- negative `ChunkPos` translation was exact;
- every structural terrain semantic mapped to a concrete vanilla registry key;
- AIR/solid occupancy was preserved;
- catalog culling excluded a distant empty chunk;
- repeated chunk materialization was deterministic;
- west-then-east and east-then-west generation produced identical results;
- a Massif crossing the `x=-1 / x=0` chunk ownership seam remained continuous.

## Repository-wide gate

Command:

```bat
gradlew.bat check
```

Result: **PASS**.

This is the repository-wide merge checkpoint for the runtime head above.

## Build/runtime compatibility finding

Concrete integration established that Minecraft 1.21.1 requires a real Java 21 toolchain for ModDevGradle tasks; Java 25 plus `--release 21` alone is insufficient for tasks that launch Minecraft tooling.

Accepted response:

- the overall development workspace may continue running Gradle on JDK 25;
- Gradle can provision Java 21 for the NeoForge module through the configured Foojay toolchain resolver;
- backend-neutral runtime modules emit Java 21-compatible classfiles/API usage;
- `skyforge-reference` remains an engineering/evidence module and is not constrained by the Minecraft runtime target.

## Explicitly not accepted yet

SF-IMP-0031 does **not** claim completion of:

- a live `ChunkAccess` write path;
- live `BlockState` registry resolution;
- NeoForge worldgen lifecycle registration;
- biome-aware material selection;
- structures/features/vegetation/ores/caves/fluids;
- geometry-derived suitability;
- optional-mod registry probing or compatibility adapters;
- production caching, async scheduling, or final live/preload/hybrid policy;
- Minecraft 1.21.1 as a permanent release target.

## Visual gate

No visual gate is required for SF-IMP-0031. The work item changes backend integration and representation plumbing only; accepted island geometry, composition, and terrain-semantic classification are unchanged.

## Acceptance conclusion

SF-IMP-0031 is accepted. Skyforge has now demonstrated that its backend-neutral world catalog and structural terrain semantics can drive deterministic, seam-safe chunk-scale realization through real Minecraft 1.21.1 / NeoForge API types without requiring Minecraft to recreate Skyforge geometry or semantic identity.
