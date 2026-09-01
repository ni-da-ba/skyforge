# SF-IMP-0039 — Minecraft Surface Suitability In-Game Runbook

**Status:** Implementation ready for automated validation.

## Purpose

SF-IMP-0039 separates **surface reachability** from **feature suitability** while keeping all environment interpretation inside the Minecraft adapter.

The accepted SF-IMP-0038 `skyforge:additional_surfaces` primitive remains intact. A new `skyforge:suitable_surfaces` modifier selects a requested physical placement class.

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

Do not proceed to the client if either command fails.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Create a **new disposable world** using the SF-IMP-0039 world type. Do not reuse a previously generated 0038 world.

Teleport to the specimen:

```text
/tp @s 0 300 0
```

## What to inspect

### Existing integration

The accepted behavior should remain intact:

- Massif geometry appears normally;
- native surface adaptation remains plausible;
- caves, ores, lighting and downstream worldgen do not obviously regress;
- no obvious 16-block ownership seams appear;
- native terrain remains intact where Skyforge is AIR.

### Suitability diagnostics

Origin-area logs should contain lines resembling:

```text
SF-IMP-0039 suitability diagnostic chunk=[x, z] dryLand=N dryOpen=O submergedWaterFloor=W dryOpenQueries=Q dryOpenEmitted=E submergedQueries=U submergedEmitted=V
```

Expected invariants:

- `dryOpen <= dryLand` for every chunk;
- if `dryOpen > 0`, the dry-open development probes should query that class;
- if `submergedWaterFloor > 0`, the submerged development probe should query that class.

### Land seed

If dry terrain exists below/inside the Massif footprint, inspect the lower surfaces.

Expected evidence:

- a few emerald marker blocks may appear on broad, high-clearance lower dry surfaces;
- the ground should **not** be carpeted with markers as in the 0038 gold diagnostic;
- `minecraft:patch_grass` may also occur on suitable dry-open ground;
- obvious tight cavity floors and very thin carved shelves should be much less likely to receive the dry-open marker.

### Ocean seed

If the Massif sits above ocean, inspect the seabed below it.

Expected evidence:

- origin-area logs should report nonzero `submergedWaterFloor` where a suitable seabed lies below Skyforge;
- sparse lapis marker blocks may appear at those submerged floor positions;
- absence of grass/emerald underwater is correct;
- lapis should replace a water placement cell immediately above solid seabed support, demonstrating that the aquatic class is separate from dry land.

A seed does not need to demonstrate both dry and submerged classes. The environment determines which class exists.

### Highest surface exclusion

The colored supplemental markers should not systematically decorate the vanilla highest Massif surface. That surface remains vanilla-owned.

### Persistence

Save, quit, and reload the same world.

Confirm:

- the Massif persists;
- suitability marker blocks persist;
- no placement-modifier codec/registry errors appear;
- no chunk corruption or duplicate generation is apparent.

## Pass criteria

Manual acceptance passes when:

- the SF-IMP-0039 world loads successfully;
- accepted 0036–0038 integration remains visually intact;
- logs show coherent suitability counts with `dryOpen <= dryLand`;
- at least one suitability class that exists in the generated environment is actually consumed by its development probe;
- a corresponding sparse colored marker is visibly realized on a lower supplemental surface;
- no systematic marker duplication occurs on the highest surface; and
- save/reload is clean.

## Explicit limitations

This is not a final vegetation policy. The 8-block headroom and 3-block support-thickness thresholds are first engineering criteria intended to make suitability explicit and testable. Horizontal footprint, slopes, trees, snow/ice, true kelp/seagrass behavior and modded feature families remain later work.
