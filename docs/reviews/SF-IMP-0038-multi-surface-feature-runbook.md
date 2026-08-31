# SF-IMP-0038 — Supplemental Multi-Surface Feature In-Game Runbook

**Status:** Automated verification and repository-wide build passed; live modifier emission proven; final configured-feature realization proof pending.

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

Do not proceed to the client test if either command fails.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Create a **new disposable world** using `Skyforge Development (SF-IMP-0038)`. Do not reuse an already-generated 0037/0038 world after changing the development probes.

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

For example, the origin-area logs included chunks with more than 200 available supplemental positions and multiple emitted positions from ten feature queries.

The inspected world happened to place the Massif above an ocean. No kelp was visible under the island, but this does not exercise the grass probe: `minecraft:patch_grass` is the injected realistic feature, not kelp, and the current land-surface index requires air above a solid support. A submerged seabed therefore is intentionally not a land target.

This run establishes that the copied feature reaches the custom placement modifier and that the modifier emits supplemental positions in a live client. It does not by itself prove that the configured feature subsequently places a block.

## Deterministic marker diagnostic

The follow-up development resources add `skyforge:additional_surface_marker`:

1. vanilla `minecraft:count` creates repeated attempts;
2. vanilla `minecraft:in_square` scatters columns;
3. `skyforge:additional_surfaces` emits lower valid positions;
4. vanilla `minecraft:biome` retains biome-membership filtering;
5. vanilla `minecraft:simple_block` attempts to place `minecraft:gold_block` at the resulting position.

At the end of each origin-area decoration scope, the development log now reports:

```text
SF-IMP-0038 feature diagnostic chunk=[x, z] availableAdditionalPositions=N modifierQueries=Q emittedPositions=E markerBlocksAtEmittedPositions=M
```

`M > 0` is the environment-independent end-to-end proof that a vanilla configured feature actually consumed a supplemental position and wrote into the live chunk.

## Manual checks

### 1. Existing integration remains intact

The Massif should still exist, use Minecraft-native exposed surface material, retain caves/ores/lighting interaction, and leave native terrain intact where Skyforge is AIR.

No new 16-block seam or missing-column pattern should appear.

### 2. Verify deterministic configured-feature realization

Near the origin, at least one diagnostic line should report:

```text
markerBlocksAtEmittedPositions > 0
```

Gold blocks may also be visible on lower Skyforge ledges or lower dry ground. Visual discovery is useful but not required if the log proves realized markers.

### 3. Dry-ground grass remains useful evidence when available

If the Minecraft seed places dry native terrain beneath the Massif, short-grass patches should be capable of appearing there despite the upper island owning the vanilla top heightmap.

If the Massif is over an ocean, absence of kelp is not a failure of this land-surface milestone. Underwater/submerged-surface suitability is a separate policy problem.

### 4. Highest surface should not receive the supplemental copy systematically

The Massif top may still contain ordinary vanilla vegetation. That is expected.

The supplemental index intentionally excludes the vanilla highest surface, so the diagnostic probes should not systematically duplicate placement there.

### 5. Persistence

Save, quit and reopen the same world. The world should reload without placement-modifier codec/registry failures or chunk corruption.

Generated marker blocks, vegetation and the Massif should persist normally.

## Pass criteria

SF-IMP-0038 manual acceptance passes when:

- the 0038 world type loads without codec/registry/worldgen failure;
- the accepted Massif and native-surface behavior remain intact;
- origin-area diagnostics prove non-zero additional surfaces, modifier queries and emitted positions;
- the deterministic vanilla `simple_block` probe produces `markerBlocksAtEmittedPositions > 0` on at least one supplemental lower surface;
- the highest vanilla-owned surface is not systematically re-targeted by the supplemental index;
- save/reload remains clean.

Dry-ground `patch_grass` recovery remains desirable empirical evidence but is no longer dependent on randomly receiving a land seed for the deterministic placement proof.

## Explicit limitations

This milestone does not yet claim production-quality biome-native feature replay. The development grass and gold-marker probes are intentionally narrow. Trees, flowers, snow/ice, submerged vegetation such as kelp/seagrass, modded vegetation families, arbitrary placement chains and feature-selection policy remain later empirical work.
