# SF-IMP-0043 — Native Structure-Start In-Game Runbook

**Status:** Accepted.

## Purpose

Prove that Minecraft's native structure-start path can observe an elevated Skyforge island through the accepted early generator height-query bridge before the island is physically written.

The development proof uses vanilla `minecraft:desert_pyramid`. No Skyforge-authored structure or marker block is involved.

## Automated preflight

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0043
git pull --ff-only
scripts\verify-sf-imp-0043-native-structure-starts.bat
gradlew.bat check
```

Both automated gates passed for the accepted milestone.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Create a **new disposable world** using:

```text
Skyforge Development (SF-IMP-0043)
```

Then:

```text
/tp @s 0 300 0
/gamemode spectator
```

## Why pyramids should be near the origin

The development-only structure set uses:

```text
spacing = 4
separation = 3
```

The random-spread offset window is one chunk wide. Chunk `(0,0)` is a deterministic candidate, and each neighboring four-chunk placement region can also contribute its own candidate as chunks are generated. Several pyramids in the inspected development area are therefore expected and are not duplicate-generation evidence.

The development datapack also broadens the desert-pyramid biome tag to Overworld biomes so the random native biome at the origin cannot suppress the proof.

## Accepted client evidence

The accepted run produced several vanilla desert pyramids:

- one forced candidate lay completely off the Massif and generated on ordinary native ground;
- several others intersected the Massif at varying depths;
- the island-associated pyramids demonstrate that the native structure-start path consumes terrain information containing elevated Skyforge geometry rather than remaining wholly blind to it;
- save/reload of the same world was clean, with terrain and structures persisting without registry/codec errors or duplicate regeneration.

This result establishes early structure height visibility but also exposes the next architectural problem: **height visibility is not structure-footprint suitability**. A structure whose terrain samples span sloped island surface or cross an island edge can choose an anchor that embeds the structure. Future integration needs a separate footprint/support suitability layer rather than falsifying the generic `getBaseHeight(...)` answer.

The same client run did not show kelp beneath the Massif. That is not an SF-IMP-0043 regression: supplemental aquatic vegetation has not yet been generalized into production ecology replay.

## Pass criteria

SF-IMP-0043 is accepted because:

- the development world loaded cleanly;
- vanilla desert-pyramid generation occurred in the deterministic development placement regions;
- multiple structures were vertically associated with elevated Skyforge terrain rather than lower native ground alone;
- terrain remained stable enough to demonstrate the integration seam; and
- save/reload was clean.

## Explicit limitations

The forced pyramid spacing and broadened biome tag are development fixtures only. This run does not establish production structure density, general structure suitability, villages/jigsaw support, modded structures, foundation shaping, stacked-island selection, aquatic vegetation replay, or final biome policy.
