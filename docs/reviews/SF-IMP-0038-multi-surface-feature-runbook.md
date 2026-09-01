# SF-IMP-0038 — Supplemental Multi-Surface Feature In-Game Runbook

**Status:** Accepted.

## Purpose

SF-IMP-0038 tests the first mechanism for placing normal Minecraft configured features on valid surfaces that vanilla's single-valued heightmap cannot simultaneously target.

The accepted SF-IMP-0036/0037 generator and native-surface behavior remain unchanged. The new mechanism supplements rather than replaces vanilla feature placement.

## Development proof

- World type: `Skyforge Development (SF-IMP-0038)`
- Massif: same deterministic development specimen used by the preceding integration proofs
- New registered placement modifier: `skyforge:additional_surfaces`
- Realistic development probe: `skyforge:additional_surface_grass`, using vanilla `minecraft:patch_grass`
- Deterministic development probe: `skyforge:additional_surface_marker`, using vanilla `minecraft:simple_block` with a gold-block state provider
- Both probes are injected only through development-only NeoForge biome modifiers

The grass copy keeps the vanilla plains-grass noise-count and horizontal-scatter mechanics while replacing its single-valued heightmap step with `skyforge:additional_surfaces`. It is useful when dry native ground exists below the Massif.

The gold marker is deliberately artificial but environment-independent. It exists only to prove that a vanilla configured feature can consume the supplemental positions and actually mutate the live chunk. It is excluded from production artifacts.

## Automated preflight

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0038
git pull --ff-only
scripts\verify-sf-imp-0038-multi-surface-features.bat
gradlew.bat check
```

Both automated gates passed on 2026-08-31.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Create a **new disposable world** using `Skyforge Development (SF-IMP-0038)`.

Teleport to the engineering specimen:

```text
/tp @s 0 300 0
```

## What changed relative to SF-IMP-0037

Vanilla still sees the upper Massif as the single `WORLD_SURFACE_WG` target. Its ordinary placed features continue unchanged.

During `applyBiomeDecoration`, Skyforge computes additional surfaces in each column from the live post-carver chunk plus accepted Skyforge occupancy. A placed feature can substitute those positions for the ordinary single-valued heightmap answer.

For a simple ground + island column:

```text
upper Massif surface  <- vanilla heightmap-driven feature target
████████████████████

air gap

native ground         <- supplemental additional-surface target
████████████████████
```

For stacked islands, lower exposed Skyforge tops can also become supplemental positions while the highest surface remains vanilla-owned.

## First live diagnostic result — 2026-08-31

The first instrumented client run proved that the placement primitive executes in real biome decoration.

Chunks around the origin reported all three of the following:

- non-zero `availableAdditionalPositions`;
- non-zero `modifierQueries` from the development placed feature;
- non-zero `emittedPositions` returned by `skyforge:additional_surfaces`.

The inspected world happened to place the Massif above an ocean. No kelp was visible under the island, but this did not exercise the grass probe: `minecraft:patch_grass` was the injected realistic feature, not kelp, and the current dry-land index requires air above a solid support. A submerged seabed is therefore intentionally not a dry-land target.

This established that the copied feature reached the custom placement modifier and that the modifier emitted supplemental positions in a live client.

## Deterministic marker proof — accepted

The follow-up development resources added `skyforge:additional_surface_marker`:

1. vanilla `minecraft:count` created repeated attempts;
2. vanilla `minecraft:in_square` scattered columns;
3. `skyforge:additional_surfaces` emitted lower valid positions;
4. vanilla `minecraft:biome` retained biome-membership filtering;
5. vanilla `minecraft:simple_block` placed `minecraft:gold_block` at accepted positions.

The accepted run reported:

```text
SF-IMP-0038 feature diagnostic chunk=[x, z] availableAdditionalPositions=N modifierQueries=Q emittedPositions=E markerBlocksAtEmittedPositions=M
```

with `M > 0` across many origin-area chunks. Representative examples included origin chunk `[0,0]` with 205 available supplemental positions, 26 modifier queries, 21 emitted positions and 11 realized marker blocks, while several neighboring chunks realized 15–16 marker blocks.

The user visually observed the gold blocks on the lower supplemental surfaces and confirmed that the world remained otherwise stable. Save/reload also remained clean.

This is the decisive end-to-end proof that a normal Minecraft configured feature can consume Skyforge's multi-surface spatial answer and modify the live chunk.

## Accepted interpretation

The intentionally excessive gold-marker result also exposed the next architectural problem: **geometric availability is not the same as feature suitability**.

The primitive can identify many air-exposed lower surfaces, including lower Skyforge ledges and cavity floors. A real vegetation system must therefore decide which candidate surfaces are appropriate for a given feature family. That policy belongs in later Minecraft/backend-specific suitability work rather than in the generic multi-surface primitive itself.

Similarly, submerged seabeds are not dry-land candidates. Kelp/seagrass and other submerged features need a distinct suitability class that permits water occupancy above the support rather than reusing the dry-land rule.

## Pass criteria — satisfied

SF-IMP-0038 manual acceptance passed because:

- the 0038 world type loaded without codec/registry/worldgen failure;
- the accepted Massif and native-surface behavior remained intact;
- origin-area diagnostics proved non-zero additional surfaces, modifier queries and emitted positions;
- the deterministic vanilla `simple_block` probe produced `markerBlocksAtEmittedPositions > 0` on supplemental lower surfaces;
- the highest vanilla-owned surface was not systematically re-targeted by the supplemental index;
- save/reload remained clean.

## Explicit limitations

This milestone does not yet claim production-quality biome-native feature replay. The development grass and gold-marker probes are intentionally narrow. Trees, flowers, snow/ice, submerged vegetation such as kelp/seagrass, modded vegetation families, arbitrary placement chains and feature-selection policy remain later empirical work.

The gold-marker fixture is acceptance instrumentation only and is not intended for normal gameplay or release configuration.
