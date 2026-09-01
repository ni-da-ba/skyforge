# SF-IMP-0043 — Native Structure-Start In-Game Runbook

**Status:** Implementation ready for automated validation.

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

Do not proceed to the client test if either command fails.

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
```

Spectator mode is useful:

```text
/gamemode spectator
```

## Why a pyramid should be near the origin

The development-only structure set uses:

```text
spacing = 4
separation = 3
```

The random-spread offset window is therefore one chunk wide, making chunk `(0,0)` a deterministic candidate. The development datapack also broadens the desert-pyramid biome tag to Overworld biomes so the random native biome at the origin cannot suppress the proof.

If the pyramid is not immediately obvious, run:

```text
/locate structure minecraft:desert_pyramid
```

The nearest result should be in or very near the origin region. Teleport above the reported X/Z coordinate if necessary.

## Positive proof

Pass behavior:

- a normal vanilla desert pyramid exists near the center of the development Massif;
- its foundation/start is associated with the elevated island surface rather than the ordinary terrain far below;
- the pyramid is made of normal vanilla structure pieces/blocks;
- the Massif itself still realizes normally through the post-surface seam;
- there is no obvious duplicate structure population, catastrophic terrain carving, chunk seam or corruption.

The pyramid may intersect/adjust nearby island material according to normal Minecraft structure behavior. This milestone is about early height visibility and native placement, not final structure aesthetics or suitability.

## Failure signatures

Report the result and relevant log output if any of these occur:

- datapack/registry error involving `sf_imp_0043_desert_pyramids`;
- invalid biome-tag reference involving `c:is_overworld`;
- `/locate structure minecraft:desert_pyramid` finds no nearby development-forced structure;
- the pyramid generates on the preserved native ground beneath the Massif rather than on/against the elevated island;
- the pyramid start exists but later piece placement is catastrophically displaced;
- the Massif disappears or generation regresses;
- crash or chunk corruption.

## Persistence

After a positive result:

1. save and quit;
2. relaunch the client;
3. reopen the same world;
4. confirm both Massif and pyramid persist without duplicate regeneration or registry errors.

## Pass criteria

SF-IMP-0043 passes manually when:

- the development world loads cleanly;
- vanilla desert-pyramid structure generation occurs near the deterministic origin candidate;
- the structure is vertically anchored to the elevated Skyforge terrain rather than lower native ground;
- terrain remains stable enough to demonstrate the integration seam; and
- save/reload is clean.

## Explicit limitations

The forced pyramid spacing and broadened biome tag are development fixtures only. This run does not establish production structure density, general structure suitability, villages/jigsaw support, modded structures, foundation shaping, stacked-island selection or final biome policy.
