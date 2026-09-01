# SF-IMP-0039 — Minecraft Surface Suitability In-Game Runbook

**Status:** Accepted

## Purpose

SF-IMP-0039 separates **surface reachability** from **feature suitability** while keeping all environment interpretation inside the Minecraft adapter for this milestone.

The accepted SF-IMP-0038 `skyforge:additional_surfaces` primitive remains intact. A new `skyforge:suitable_surfaces` modifier selects a requested physical placement class.

This milestone does not foreclose future Skyforge-owned biome/environment fields. It deliberately uses Minecraft-native state for the first physical suitability layer while leaving higher-level biome-field ownership for a later, explicit design milestone.

## Development world

Use:

```text
Skyforge Development (SF-IMP-0039)
```

The Massif remains the same deterministic engineering specimen around the origin.

The development data includes three probes:

- vanilla `minecraft:patch_grass` routed through `dry_open`;
- sparse **emerald blocks** routed through `dry_open`;
- sparse **lapis blocks** routed through `submerged_water_floor`.

These markers are diagnostics only and are excluded from the production JAR.

## Automated preflight

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0039
git pull --ff-only
scripts\verify-sf-imp-0039-surface-suitability.bat
gradlew.bat check
```

Both automated gates passed on 2026-08-31.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Create a **new disposable world** using the SF-IMP-0039 world type. Do not reuse a previously generated 0038 world.

Teleport to the specimen:

```text
/tp @s 0 300 0
```

## Suitability diagnostics

Origin-area logs use:

```text
SF-IMP-0039 suitability diagnostic chunk=[x, z] dryLand=N dryOpen=O submergedWaterFloor=W dryOpenQueries=Q dryOpenEmitted=E submergedQueries=U submergedEmitted=V
```

The invariant `dryOpen <= dryLand` must hold for every chunk.

## Accepted live evidence — 2026-08-31

The accepted client world placed the engineering Massif over ocean terrain.

Across the origin-area diagnostic chunks, the observed result was consistently:

```text
dryLand=0
dryOpen=0
submergedWaterFloor=256
dryOpenQueries=14
dryOpenEmitted=0
submergedQueries=4
submergedEmitted=4
```

This is a strong class-separation proof:

- the dry probes executed but emitted nothing because no dry candidate class existed;
- the submerged selector exposed a water-floor candidate in every inspected column;
- the submerged development feature consumed those candidates and emitted placements;
- sparse lapis markers were visibly realized on the ocean floor beneath the Massif;
- no corresponding dry-open markers appeared underwater.

The user observed generally fewer lapis markers in flooded cave systems than across the open seabed. That observation is compatible with the sparse diagnostic distribution, but SF-IMP-0039 does **not** claim an explicit open-ocean-versus-flooded-cave distinction. A later aquatic suitability refinement may add one if a real feature family requires it.

## Existing integration evidence

The accepted behavior from SF-IMP-0036 through SF-IMP-0038 remained visually intact:

- Massif geometry appeared normally;
- native surface adaptation remained plausible;
- no obvious new ownership seam appeared;
- downstream worldgen remained functional;
- native terrain remained present where Skyforge was AIR;
- no systematic colored-marker duplication was observed on the highest vanilla-owned Massif surface.

## Persistence evidence

The accepted world was saved, closed and reopened once after the suitability marker generation.

The reload was clean:

- the Massif persisted;
- the lapis markers persisted;
- no placement-modifier codec/registry errors appeared;
- no chunk corruption or obvious duplicate generation was observed.

## Acceptance conclusion

SF-IMP-0039 passes because:

- the registered parameterized modifier loads and composes with Minecraft feature placement;
- automated tests distinguish reachability from the first three suitability classes;
- wrong-class emission is suppressed in a real ocean environment;
- the correct submerged class produces visible configured-feature realization;
- highest-surface ownership remains with vanilla;
- save/reload remains stable;
- development-only diagnostics remain outside production artifacts.

## Explicit limitations

This is not a final vegetation policy. The 8-block headroom and 3-block support-thickness thresholds are first engineering criteria intended to make suitability explicit and testable. Horizontal footprint, slopes, trees, snow/ice, true kelp/seagrass behavior, open-water-versus-flooded-cavity aquatic distinctions, modded feature families, and higher-level Skyforge biome/environment field ownership remain later work.
