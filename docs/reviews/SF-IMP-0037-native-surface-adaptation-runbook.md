# SF-IMP-0037 — Native Surface Adaptation In-Game Runbook

**Status:** Automated verification and repository-wide build passed; native surface adaptation observed in-game; persistence/final manual checks pending.

## Purpose

This run validates the first Minecraft-native material adaptation for Skyforge terrain.

SF-IMP-0036 proved that the Massif is inserted early enough for later Minecraft generation systems to interact with it. SF-IMP-0037 keeps that generation stage and geometry unchanged, but the development binding now samples Minecraft's already-built native surface before the Skyforge write and uses that material for exposed Skyforge tops.

This is a backend representation proof. It does not add a Skyforge climate model and it does not rerun vanilla `SurfaceSystem` over the floating island.

## Development specimen

- World type: `Skyforge Development (SF-IMP-0037)`
- Generator: `skyforge:noise_overlay`
- Morphology: same deterministic MASSIF geometry used by the preceding development proof
- Root seed: `0x534b59464f524745`
- Center: `x=0, z=0`
- Suspension elevation: approximately `y=224`
- Inspection position: `x=0, y=300, z=0`
- Realization: post-surface additive overlay
- Surface adaptation: exposed Skyforge tops inherit the already-built native surface-top block from the same Minecraft column

The ordinary Minecraft world seed may be changed between disposable worlds. Different seeds are useful because they can put different native terrain/materials beneath the fixed Skyforge specimen.

## Automated preflight

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0037
git pull --ff-only
scripts\verify-sf-imp-0037-native-surface-adaptation.bat
gradlew.bat check
```

Both automated gates passed on 2026-08-31. Manual client acceptance remains required before SF-IMP-0037 can be accepted.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Use the isolated development game directory managed by the ModDev run.

## World creation

1. Choose **Singleplayer → Create New World**.
2. Use Creative mode and enable commands.
3. Select **Skyforge Development (SF-IMP-0037)** as the world type.
4. Create a **new disposable world**. Do not reuse the SF-IMP-0036 world because already-generated chunks will not demonstrate the new surface representation.
5. Run:

```text
/tp @s 0 300 0
```

6. Inspect the Massif and the ordinary Minecraft terrain directly below/around it.

## Required manual checks

### 1. The Massif still exists and remains seamless

The same substantial floating Massif should be present around the origin. There should be no new 16-block ownership strips, missing columns or geometry truncation caused by surface adaptation.

### 2. Native terrain remains non-destructive

Ordinary Minecraft terrain beneath and around the island must remain present wherever Skyforge contributes AIR.

### 3. Exposed Skyforge tops use the native Minecraft surface material

Compare the visible top of the floating island with the ordinary surface material in the same general columns below it.

Typical examples:

- grass/grass-block terrain below should produce grass-block exposed Skyforge tops;
- sand terrain below should produce sand exposed Skyforge tops;
- stone-like native terrain should be able to produce stone-like exposed tops.

Exact coverage need not be aesthetically perfect. The acceptance question is whether the exposed top is now demonstrably derived from Minecraft's native surface result rather than always using the fixed engineering top representation.

Do not treat grass spreading after world creation as the only evidence. Inspect soon after generation if possible.

### 4. Interior representation is not globally replaced

Break or inspect a few blocks below the visible top. Surface adaptation is intentionally shallow: it should not convert the entire Massif to the copied native top block.

The existing dirt/stone/deepslate engineering substrate may still be visible beneath the adapted top. That is expected in SF-IMP-0037.

### 5. Later Minecraft worldgen still interacts

Caves, ores, vegetation, trees and lighting should remain capable of interacting with the island as demonstrated in SF-IMP-0036. The presence of every feature type is not required in every seed.

A systematic loss of later feature interaction would be a regression.

### 6. Persistence

Save and quit, re-enter the same world, and verify that the adapted Massif persists without duplicate realization or obvious corruption.

## Observed manual evidence — 2026-08-31

The first SF-IMP-0037 client inspection reported that the exposed Massif top looked **remarkably like the surrounding native Minecraft terrain**. This is the intended visual consequence of the native-surface adapter and is direct evidence that the fixed engineering dirt top is no longer the sole exposed-surface representation.

No trees or comparable vegetation were noticed on the ordinary ground directly beneath the Massif. This is being treated as an integration observation, not as evidence that the material adapter failed.

### Heightmap / multi-surface interpretation

SF-IMP-0036 already proved that Minecraft's final world-surface heightmap observes the elevated Skyforge solid. Surface-placed vegetation commonly selects Y from a top-surface heightmap. Once an island occupies a column, the same `(x,z)` column therefore has one vanilla world-surface heightmap answer: the **upper island surface**, even though the ordinary lower terrain remains physically present.

Conceptually:

```text
same (x,z) column

    upper Skyforge surface  <- vanilla top-surface heightmap target
    █████████████████████

          air gap

    preserved native ground <- real blocks, but not simultaneously the top heightmap value
    █████████████████████
```

This likely explains reduced or absent heightmap-driven vegetation directly below a floating island: attempts that would otherwise target the native ground can be redirected to the upper Skyforge surface.

A focused regression test now captures this behavior explicitly by preserving lower native ground, realizing an elevated Skyforge surface, priming Minecraft's final heightmaps, and asserting that the reported world-surface height is the upper surface rather than the lower ground.

This exposes a broader Minecraft integration boundary relevant to future vertically stacked islands: vanilla heightmaps are single-valued per `(x,z)` and cannot represent several independently decoratable surfaces in one vertical column. If Skyforge requires vegetation/features on ground plus one or more stacked islands, that requires an explicit multi-surface feature/suitability strategy rather than assuming vanilla heightmaps can express every surface simultaneously.

This limitation does **not** invalidate SF-IMP-0037's material-adaptation goal.

## Useful second-world check

If the first world generates only ordinary grassy terrain at the origin, a second disposable world with a different Minecraft seed is useful. The Skyforge geometry remains deterministic, while the native Minecraft terrain underneath can change. A visibly different inherited top material would be particularly strong evidence that Minecraft—not Skyforge's fixed engineering palette—is controlling the exposed representation.

A second seed is useful evidence, not mandatory if the first world already demonstrates the adaptation clearly.

## Pass criteria

SF-IMP-0037 manual acceptance passes when:

- the 0037 development world type is available;
- a new world starts without worldgen/registry/codec failure;
- the Massif remains present and geometrically intact;
- native terrain remains intact under Skyforge AIR;
- exposed island tops visibly inherit Minecraft-native surface material;
- the copied top material does not replace the entire island interior;
- later worldgen/lighting interaction remains functional;
- save/reload preserves the result.

The lower-ground vegetation observation above is not by itself a failure of this milestone because it follows from the single-valued vanilla heightmap boundary and is outside the narrow material-adaptation decision.

## Known limitations that do not by themselves fail this milestone

- only exposed top blocks are adapted;
- native filler/subsurface depth is not copied;
- a ground-level native surface can imperfectly represent a vertically different high-altitude biome;
- ocean-floor or shore materials may not yet be ideal for floating-island aesthetics;
- vanilla top-surface heightmaps cannot simultaneously target lower ground and one or more floating surfaces in the same `(x,z)` column;
- structures are still not Skyforge-aware;
- the current Massif geometry and engineering substrate remain preliminary.
