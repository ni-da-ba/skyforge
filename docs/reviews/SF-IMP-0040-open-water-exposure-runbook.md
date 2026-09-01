# SF-IMP-0040 — Open-Water Exposure In-Game Runbook

**Status:** Implementation ready for automated validation.

## Purpose

SF-IMP-0040 refines accepted aquatic suitability by separating general submerged floor from a vertically open water column.

The accepted `submerged_water_floor` class remains intact. The new class is:

```text
open_water_floor
```

It requires a submerged floor whose contiguous water column reaches air before any solid/non-water ceiling.

## Development world

Use:

```text
Skyforge Development (SF-IMP-0040)
```

Development markers:

- emerald = `dry_open`;
- lapis = `submerged_water_floor`;
- diamond = `open_water_floor`.

All markers are development-only and excluded from the production JAR.

## Automated preflight

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0040
git pull --ff-only
scripts\verify-sf-imp-0040-open-water-exposure.bat
gradlew.bat check
```

Do not proceed to the client test if either command fails.

## Launch

```bat
gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runClient
```

Create a **new disposable world** using the SF-IMP-0040 development type.

Teleport to the Massif:

```text
/tp @s 0 300 0
```

## Diagnostic log

Origin-area chunks should report lines resembling:

```text
SF-IMP-0040 exposure diagnostic chunk=[x, z] dryLand=N dryOpen=O submergedWaterFloor=W openWaterFloor=P dryOpenQueries=Q dryOpenEmitted=E submergedQueries=U submergedEmitted=V openWaterQueries=R openWaterEmitted=S
```

Required numerical relationships:

```text
dryOpen <= dryLand
openWaterFloor <= submergedWaterFloor
```

The relevant feature may still query a class and emit zero positions when that class is absent.

## Ocean evidence

If the specimen is above ocean, inspect the seabed beneath it.

Expected behavior:

- lapis may appear on general submerged floor candidates;
- diamond may appear where the vertical water column reaches an air surface;
- the floating Massif above the air gap should not prevent diamond placement;
- no emerald should appear underwater.

## Flooded-cavity evidence

If you encounter an enclosed flooded cavity with a solid ceiling above its water column, it may still receive a lapis marker but should not receive a diamond marker from `open_water_floor`.

A cave or shaft that is vertically open all the way to an air surface can legitimately still qualify. This milestone tests vertical exposure, not complete three-dimensional cave enclosure.

## Highest surface exclusion

No supplemental color should systematically decorate the vanilla highest Massif surface.

## Persistence

Save, quit, and reopen the same world once.

Confirm:

- Massif persists;
- diagnostic markers persist;
- no codec/registry error appears;
- no chunk corruption or obvious duplicate generation occurs.

## Pass criteria

SF-IMP-0040 passes manually when:

- the development world loads cleanly;
- accepted SF-IMP-0036 through SF-IMP-0039 behavior remains intact;
- diagnostics maintain `openWaterFloor <= submergedWaterFloor`;
- at least one `open_water_floor` target is consumed and visibly realized when such candidates exist;
- enclosed capped water columns are not promoted to `open_water_floor` where they can be inspected;
- the highest vanilla-owned surface is not systematically retargeted; and
- save/reload is clean.

## Explicit limitations

This is still an engineering exposure classifier rather than final aquatic ecology. Actual kelp/seagrass behavior, water-depth preferences, horizontal enclosure, currents, biome policy, modded fluids, and future Skyforge biome/environment fields remain later work.
