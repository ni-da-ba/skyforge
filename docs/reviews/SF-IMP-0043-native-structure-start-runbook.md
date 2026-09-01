# SF-IMP-0043 — Native Structure-Start In-Game Runbook

**Status:** Positive client evidence observed; persistence confirmation pending.

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

## Why pyramids should be near the origin

The development-only structure set uses:

```text
spacing = 4
separation = 3
```

The random-spread offset window is therefore one chunk wide. Chunk `(0,0)` is a deterministic candidate, and each neighboring four-chunk placement region can also contribute its own candidate as chunks are generated. **Several pyramids in the inspected development area are therefore expected and are not duplicate-generation evidence.**

The development datapack also broadens the desert-pyramid biome tag to Overworld biomes so the random native biome at the origin cannot suppress the proof.

If a pyramid is not immediately obvious, run:

```text
/locate structure minecraft:desert_pyramid
```

## Positive proof

Pass behavior:

- at least one normal vanilla desert pyramid exists on/against the development Massif;
- its foundation/start is associated with the elevated island surface rather than exclusively with ordinary terrain far below;
- the pyramid is made of normal vanilla structure pieces/blocks;
- the Massif itself still realizes normally through the post-surface seam;
- there is no catastrophic terrain corruption, chunk seam, or structure displacement.

The pyramid may intersect nearby island material according to normal Minecraft structure behavior. This milestone is about **early height visibility and native placement**, not final structure aesthetics, footprint suitability, or terrain flattening.

A development-forced candidate that lies completely outside the Massif can still generate on ordinary native ground. That does not invalidate a positive island-associated structure elsewhere in the same run.

## Observed client evidence

The first positive client run produced several vanilla desert pyramids in the development region:

- one forced candidate lay completely off the Massif and generated on ordinary native ground;
- several others intersected the Massif at varying depths;
- the island-associated pyramids demonstrate that the native structure-start path is consuming terrain information that includes the elevated Skyforge geometry rather than remaining wholly blind to it.

This result also exposes the next architectural problem: **height visibility is not the same as structure-footprint suitability**. A structure whose terrain samples span sloped island surface or cross an island edge can choose a vertical anchor that causes substantial embedding. Skyforge must not solve that by projecting phantom island height outside the actual geometry; future structure integration needs an explicit footprint/support suitability layer.

The same client run did not show kelp beneath the Massif. That is not an SF-IMP-0043 regression: supplemental aquatic vegetation has not yet been generalized. The earlier multi-surface milestones proved the placement mechanism and aquatic surface classification, not production kelp replay.

## Failure signatures

Report the result and relevant log output if any of these occur:

- datapack/registry error involving `sf_imp_0043_desert_pyramids`;
- invalid biome-tag reference involving `c:is_overworld`;
- `/locate structure minecraft:desert_pyramid` finds no nearby development-forced structure;
- **all** island-overlapping pyramid candidates remain anchored only to preserved native ground far below the Massif;
- a pyramid start exists but later piece placement is catastrophically displaced;
- the Massif disappears or generation regresses;
- crash or chunk corruption.

## Persistence

After a positive result:

1. save and quit;
2. relaunch the client;
3. reopen the same world;
4. confirm both Massif and the island-associated pyramid(s) persist without duplicate regeneration or registry errors.

## Pass criteria

SF-IMP-0043 passes manually when:

- the development world loads cleanly;
- vanilla desert-pyramid structure generation occurs in the deterministic development placement regions;
- at least one structure is vertically associated with the elevated Skyforge terrain rather than lower native ground alone;
- terrain remains stable enough to demonstrate the integration seam; and
- save/reload is clean.

## Explicit limitations

The forced pyramid spacing and broadened biome tag are development fixtures only. This run does not establish production structure density, general structure suitability, villages/jigsaw support, modded structures, foundation shaping, stacked-island selection, aquatic vegetation replay, or final biome policy.
