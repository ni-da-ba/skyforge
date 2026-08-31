# SF-IMP-0036 — Post-Surface In-Game Worldgen Runbook

**Status:** Automated implementation gates passed; manual worldgen-interaction checks passed; save/reload persistence confirmation pending.

## Purpose

This run proves that the SF-IMP-0036 `SkyforgeNoiseBasedChunkGenerator` works in an actual Minecraft 1.21.1 integrated-server/client world, not only in isolated `ProtoChunk` tests.

Unlike SF-IMP-0034, the development runtime no longer installs the late `ChunkEvent.Load(isNewChunk=true)` realization path. The development specimen is installed only into `SkyforgeNeoForge1211SurfaceStage`, and the island can therefore appear only when the selected chunk generator calls that stage from `buildSurface`.

The selectable world preset used for this proof is contained in the `development` Gradle source set. It is available to the ModDev client but is not included in the production/distributable jar.

## Development specimen

- World type: `Skyforge Development (SF-IMP-0036)`
- Generator codec: `skyforge:noise_overlay`
- Vanilla base generator: Overworld `minecraft:overworld` noise settings and multi-noise biome source
- Skyforge morphology: `MASSIF`
- Skyforge root seed: `0x534b59464f524745`
- Skyforge center: `x=0, z=0`
- Suspension elevation: approximately `y=224`
- Inspection position: `x=0, y=300, z=0`
- Skyforge realization mode: additive solid overlay
- Engineering palette: dirt / stone / deepslate

The ordinary Minecraft world seed may be any value. It affects the vanilla terrain under/around the fixed development specimen but not the specimen's Skyforge root seed.

## Launch

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0036
git pull --ff-only
scripts\verify-sf-imp-0036-post-surface-worldgen.bat
gradlew.bat check
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

The run uses the isolated game directory:

```text
skyforge-neoforge-1211/run-sf-imp-0036
```

Do not reuse an SF-IMP-0034 world.

## World creation

1. Choose **Singleplayer → Create New World**.
2. Set game mode to **Creative** and enable commands if the UI requires it for `/tp`.
3. Open the world-generation/world-type controls.
4. Cycle **World Type** until it reads **Skyforge Development (SF-IMP-0036)**.
5. Create a **new disposable world**.
6. After spawning, run:

```text
/tp @s 0 300 0
```

7. Look downward and around the origin.

If the Skyforge development world type is absent, stop and report that result rather than creating a Default world. A Default world intentionally uses Minecraft's normal generator and should not invoke the SF-IMP-0036 post-surface seam.

## Required visual checks

### 1. Earlier-stage realization is actually active

A substantial floating Massif should be visible around the origin.

This is stronger evidence than the SF-IMP-0034 visual proof: the development runtime now has no active `ChunkEvent.Load` binding. A visible specimen in this world therefore demonstrates delivery through the selected `skyforge:noise_overlay` generator's `buildSurface` path.

### 2. Vanilla terrain remains additive

Inspect beneath and around the island. Ordinary vanilla Overworld terrain should still exist wherever Skyforge contributes AIR. There must not be a large cuboidal or columnar region erased to air around the specimen.

### 3. Chunk ownership remains seamless

Fly around the perimeter, top, and underside. Look especially along apparent 16-block boundaries for:

- missing strips;
- duplicated strips;
- abrupt one-chunk truncations;
- vertical walls caused only by chunk ownership errors.

Natural morphology discontinuity is not the same thing as a chunk seam.

### 4. Later lighting sees the island

Inspect the top and underside in daylight. The island should be lit as real terrain rather than remaining globally full-bright or black because it was inserted after lighting.

Minor lighting oddities should be recorded, but a systematic unlit/full-bright specimen is a failure of this milestone's intended stage ordering.

### 5. Later worldgen interaction is observable, but not prescribed

Look for evidence that post-surface vanilla systems have had the opportunity to interact with the island, such as:

- a cave/carver opening;
- heightmap-driven feature placement at elevated terrain;
- other biome-decoration behavior.

The presence of any particular tree, ore, cave, or feature is **not** required. Suitability and biome-surface adaptation have not yet been designed, and random generation may legitimately produce no obvious example in this specimen. Record anything notable rather than treating aesthetics as an acceptance criterion.

### 6. Persistence

Save and quit to the title screen, re-enter the same world, and return to the origin if necessary.

The island must persist without duplicate realization or obvious corruption.

## Observed manual evidence — 2026-08-31

The development world launched successfully with the Skyforge Massif present. Inspection reported:

- cave formations cutting through the Massif;
- ore placements in the elevated terrain;
- grass/vegetation behavior on the island;
- trees generated on the Massif;
- no immediate worldgen breakage or obvious catastrophic interaction.

This is direct visual evidence that Minecraft is treating the inserted Skyforge volume as world terrain early enough for multiple downstream generation systems to interact with it. In particular, cave formations are consistent with post-surface carver participation, while ore and tree placement are consistent with later feature/biome-decoration stages observing the elevated terrain.

Grass itself is not treated as proof that vanilla `SurfaceSystem` processed the Massif, because SF-IMP-0036 deliberately inserts after vanilla surface construction. It may result from later feature behavior or block updates. Likewise, this evidence does not imply that structure starts are Skyforge-aware.

The remaining manual acceptance check is save/reload persistence through the new generator path.

## Screenshots / useful evidence

If convenient, capture:

1. a broad silhouette from above or oblique view;
2. the underside;
3. the top surface;
4. one apparent chunk-boundary region;
5. any cave, vegetation, lighting, or feature interaction that appears to involve the floating terrain;
6. any anomaly.

Screenshots are useful for diagnosis but are not mandatory if the observations are unambiguous.

## Pass criteria

SF-IMP-0036's interactive gate passes when all of the following are true:

- the development world type is available in the ModDev client;
- a new world using it starts without a worldgen/codec/registry crash;
- the documented floating Massif is visible around the origin;
- native terrain is not globally erased by Skyforge AIR;
- there is no obvious 16-block ownership seam or missing strip;
- lighting recognizes the generated island;
- save/reload preserves the result;
- no evidence suggests the old late load-event path is required for the specimen.

## Known limitations that do not by themselves fail SF-IMP-0036

- the dirt/stone/deepslate engineering palette is intentionally crude;
- vanilla biome surface rules have already run before Skyforge realization;
- vegetation may be sparse or absent because Skyforge's surface material is not yet biome-adapted;
- carvers may produce unattractive or no visible cavities;
- structure starts are computed before this insertion point and are not expected to understand Skyforge terrain;
- native terrain can intersect the lower parts of a floating island depending on the vanilla world seed;
- this development world preset is a ModDev-only validation fixture, not the eventual user-facing world configuration system.
