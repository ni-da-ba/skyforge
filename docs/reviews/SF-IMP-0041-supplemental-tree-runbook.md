# SF-IMP-0041 — Supplemental Tree In-Game Runbook

**Status:** Implementation ready for automated validation.

## Purpose

Prove that the accepted supplemental lower-surface path can originate a real multi-block Minecraft tree feature, not merely grass or diagnostic blocks.

The development proof uses `minecraft:trees_plains` with the accepted `dry_open` selector. Minecraft still owns the actual tree realization and sapling-survival checks.

## Automated preflight

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0041
git pull --ff-only
scripts\verify-sf-imp-0041-supplemental-trees.bat
gradlew.bat check
```

Do not proceed to the client test if either command fails.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Create a **new disposable world** using:

```text
Skyforge Development (SF-IMP-0041)
```

Then:

```text
/tp @s 0 300 0
```

Spectator mode is useful for inspecting beneath the Massif:

```text
/gamemode spectator
```

## What to inspect

The positive proof is a normal Minecraft oak/fancy-oak tree whose base lies on a supplemental lower `dry_open` surface beneath the elevated Massif.

Look primarily at ordinary ground underneath the island.

Expected behavior:

- real trunks and leaf canopies may appear on compatible lower soil;
- the highest Massif surface should continue to rely on vanilla feature placement rather than receiving a systematic second copy from this proof;
- sand, water, stone and other invalid substrates may reject the tree through Minecraft's own survival/feature logic;
- existing emerald/lapis/diamond blocks are older development diagnostics and are not part of the tree proof.

The development tree count is deliberately boosted for observability and is not intended production density.

## Seed handling

A random ocean or desert specimen may contain no compatible lower tree substrate. That is not a classifier failure.

If there is clearly no grass/dirt-like lower terrain beneath the Massif, create another disposable SF-IMP-0041 world rather than judging tree placement from an incompatible substrate.

## Failure signatures

Report the result if any of these occur:

- resource/registry error mentioning `additional_surface_trees_plains`;
- no trees on visibly compatible grass/dirt lower terrain after inspecting the origin region;
- trees systematically duplicate across the top Massif surface;
- trees appear rooted in obviously invalid unsupported air/water positions;
- widespread unrelated feature duplication;
- chunk seams, corruption or crash.

## Persistence

Once a positive tree is found, save, quit and reopen the same world once.

Confirm:

- Massif persists;
- supplemental tree persists;
- no feature/codec/registry error occurs;
- no obvious duplicate regeneration occurs.

## Pass criteria

SF-IMP-0041 passes manually when:

- the development world loads cleanly;
- at least one real Minecraft tree is visibly realized from a lower supplemental dry surface on compatible terrain;
- no systematic duplicate tree population appears on the highest Massif surface;
- unrelated feature families are not obviously duplicated; and
- save/reload is clean.

## Explicit limitations

This is a multi-block feature integration proof, not final ecology. Species choice, biome ownership, vegetation density, horizontal footprint metrics, cave exclusion, modded tree adaptation and Skyforge climate/ecology fields remain later work.
