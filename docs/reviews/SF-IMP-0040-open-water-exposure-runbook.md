# SF-IMP-0040 — Open-Water Exposure In-Game Runbook

**Status:** Accepted

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

Origin-area chunks report lines resembling:

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

Save/quit behavior should remain clean. The marker blocks themselves are ordinary vanilla blocks; the new suitability selector does not add persisted Skyforge state.

## Accepted client result — desert specimen

The accepted SF-IMP-0040 client specimen generated over dry desert terrain rather than ocean.

Across the origin-area diagnostic chunks:

```text
dryLand=256
dryOpen≈242..256
submergedWaterFloor=0
openWaterFloor=0
dryOpenQueries=14
dryOpenEmitted≈13..14
submergedQueries=4
submergedEmitted=0
openWaterQueries=2
openWaterEmitted=0
```

This is accepted as the live negative/exclusion proof:

- the SF-IMP-0040 development resources and parameterized open-water selector loaded successfully;
- dry placement remained active;
- aquatic selectors were queried but emitted nothing on dry terrain;
- no false `submerged_water_floor` or `open_water_floor` targets were created;
- the client saved and shut down cleanly.

The run did not provide a live positive diamond-marker observation because there were no aquatic candidates. We deliberately do not require repeated random world creation solely to obtain an ocean seed. Positive open-water classification and capped-column rejection remain covered by automated tests; SF-IMP-0039 already proved the generic live supplemental configured-feature realization path on submerged terrain.

## Pass criteria

SF-IMP-0040 is accepted with the following evidence:

- the development world loads cleanly;
- accepted SF-IMP-0036 through SF-IMP-0039 behavior remains intact;
- automated tests establish `open_water_floor` as a subset of `submerged_water_floor` and reject capped flooded columns;
- the live desert specimen shows zero aquatic candidates/emissions while the open-water feature chain is still queried;
- the highest vanilla-owned surface is not systematically retargeted;
- production packaging excludes all development markers/resources;
- save/shutdown remains clean.

A naturally occurring future ocean specimen should be used as an opportunistic positive live regression for diamond placement, but that observation is not required to hold SF-IMP-0040 open.

## Explicit limitations

This is still an engineering exposure classifier rather than final aquatic ecology. Actual kelp/seagrass behavior, water-depth preferences, horizontal enclosure, currents, biome policy, modded fluids, and future Skyforge biome/environment fields remain later work.