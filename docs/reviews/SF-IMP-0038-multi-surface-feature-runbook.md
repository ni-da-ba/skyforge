# SF-IMP-0038 — Supplemental Multi-Surface Feature In-Game Runbook

**Status:** Automated verification and repository-wide build passed; manual in-game acceptance pending.

## Purpose

SF-IMP-0038 tests the first mechanism for placing a normal Minecraft configured surface feature on valid surfaces that vanilla's single-valued heightmap cannot simultaneously target.

The accepted SF-IMP-0036/0037 generator and native-surface behavior remain unchanged. The new mechanism supplements rather than replaces vanilla feature placement.

## Development proof

- World type: `Skyforge Development (SF-IMP-0038)`
- Massif: same deterministic development specimen used by the preceding integration proofs
- New registered placement modifier: `skyforge:additional_surfaces`
- Development-only copied feature: `skyforge:additional_surface_grass`
- Configured feature: vanilla `minecraft:patch_grass`
- Distribution chain: vanilla plains-grass noise count -> in-square scatter -> Skyforge additional surfaces -> biome filter
- Biome modifier: development-only injection in the vegetal-decoration step

The copied feature is deliberately excluded from the production JAR. It is an integration probe for the placement primitive, not the final cross-biome vegetation policy.

## Automated preflight

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0038
git pull --ff-only
scripts\verify-sf-imp-0038-multi-surface-features.bat
gradlew.bat check
```

Both automated gates passed on 2026-08-31. Manual client acceptance remains required before SF-IMP-0038 can be accepted.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Create a **new disposable world** using `Skyforge Development (SF-IMP-0038)`. Do not reuse an already-generated 0037 world.

Teleport to the engineering specimen:

```text
/tp @s 0 300 0
```

## What changed relative to SF-IMP-0037

Vanilla still sees the upper Massif as the single `WORLD_SURFACE_WG` target. Its ordinary placed features continue unchanged.

During `applyBiomeDecoration`, Skyforge now computes the additional surfaces in each column from the live post-carver chunk plus accepted Skyforge occupancy. The development grass copy substitutes those positions for its heightmap step.

For a simple ground + island column, the intended behavior is therefore:

```text
upper Massif surface  <- vanilla heightmap-driven feature target
████████████████████

air gap

native ground         <- supplemental additional-surface target
████████████████████
```

With future stacked islands, lower Skyforge tops can also become supplemental positions while the highest surface remains vanilla-owned.

## Manual checks

### 1. Existing integration remains intact

The Massif should still exist, use Minecraft-native exposed surface material, retain caves/ores/lighting interaction, and leave native terrain intact where Skyforge is AIR.

No new 16-block seam or missing-column pattern should appear.

### 2. Inspect native ground beneath the Massif

Fly below the island and inspect ordinary land under its footprint.

Compared with the sparse-ground observation from SF-IMP-0037, short-grass patches should now be capable of appearing on the preserved lower ground even though the upper island remains the vanilla heightmap target.

A perfectly uniform recovery is not expected: the copied feature keeps a stochastic vanilla noise/scatter distribution and the configured grass feature still has its own placement/survival behavior.

### 3. Highest surface should not receive the supplemental copy

The Massif top may still contain normal vanilla vegetation. That is expected.

What should **not** occur is an obvious systematic doubling of the diagnostic grass density caused by the supplemental copy also targeting the highest surface. `skyforge:additional_surfaces` intentionally excludes that surface.

### 4. Persistence

Save, quit and reopen the same world. The world should reload without placement-modifier codec/registry failures or chunk corruption.

The generated vegetation and Massif should persist normally.

## Strong optional evidence

If the first seed makes the lower ground difficult to inspect, create a second disposable 0038 world with a different Minecraft seed. The fixed Skyforge geometry can then overlay a different native landscape while the supplemental mechanism remains the same.

## Pass criteria

SF-IMP-0038 manual acceptance passes when:

- the 0038 world type loads without codec/registry/worldgen failure;
- the accepted Massif and native-surface behavior remain intact;
- a normal Minecraft configured surface feature can visibly occur on preserved lower ground beneath the island despite the upper island owning the vanilla top heightmap;
- no systematic duplicate placement is observed on the highest surface;
- save/reload remains clean.

## Explicit limitations

This milestone does not yet claim production-quality biome-native feature replay. The development grass copy is intentionally narrow. Trees, flowers, snow/ice, modded vegetation families, arbitrary placement chains and feature-selection policy remain later empirical work.
