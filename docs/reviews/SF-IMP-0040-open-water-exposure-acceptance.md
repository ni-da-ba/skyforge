# SF-IMP-0040 — Open-Water Exposure Acceptance

**Status:** Accepted  
**Date:** 2026-08-31

## Accepted scope

SF-IMP-0040 refines Minecraft-owned supplemental aquatic suitability by adding:

```text
open_water_floor
```

The new class is a strict subset of accepted `submerged_water_floor`. It represents a solid submerged support whose contiguous vertical Minecraft-water column reaches air before encountering a solid/non-water ceiling.

This remains adapter-local physical suitability. It does not introduce Skyforge climate, biome, ocean, or ecology ownership.

## Automated evidence

The focused SF-IMP-0040 verifier covers:

- `open_water_floor` codec/selector availability;
- positive classification when a water column reaches air;
- rejection when a solid ceiling caps the water column;
- preservation of `submerged_water_floor` for capped flooded floors;
- regression compatibility with accepted dry/submerged classes;
- accepted SF-IMP-0036 through SF-IMP-0039 integration behavior;
- backend-neutral module independence;
- production-JAR exclusion of development-only markers/resources.

Repository-wide validation remains part of the milestone gate.

## Real-client evidence

The accepted client specimen generated over dry desert terrain.

Representative origin-area diagnostics reported:

```text
dryLand=256
dryOpen=256
submergedWaterFloor=0
openWaterFloor=0
dryOpenQueries=14
dryOpenEmitted=14
submergedQueries=4
submergedEmitted=0
openWaterQueries=2
openWaterEmitted=0
```

Some edge chunks reduced `dryOpen` below `dryLand` as expected from the stricter openness/support criteria, while aquatic counts remained zero.

This proves the live development resources and parameterized `open_water_floor` path load and execute without falsely promoting dry terrain to an aquatic class. The client saved and shut down cleanly.

## Positive aquatic evidence boundary

The SF-IMP-0040 client specimen contained no aquatic candidates, so no live diamond `open_water_floor` marker was observed.

This is an explicit limitation, not a hidden claim. Acceptance rests on:

1. automated positive/negative classification tests for the new exposure rule;
2. the live 0040 false-positive/runtime-loading proof;
3. the already accepted SF-IMP-0039 live proof that the same supplemental configured-feature pipeline can realize selected submerged positions in real Minecraft terrain.

Repeated random world creation solely to obtain an ocean seed is not required for this milestone. A future naturally occurring ocean specimen should be used as an opportunistic live positive regression.

## Accepted invariants

1. `open_water_floor` is a subset of `submerged_water_floor`.
2. A capped flooded cavity may remain `submerged_water_floor` while being rejected as `open_water_floor`.
3. A floating Skyforge island above an air gap does not itself invalidate an otherwise open ocean water column.
4. Dry terrain cannot become an aquatic suitability class merely because the feature chain queries it.
5. Highest-surface ownership remains vanilla.
6. No horizontal world scan or neighbor-chunk load is introduced.
7. The rule remains Minecraft/NeoForge adapter logic.
8. Future Skyforge-owned backend-neutral biome/environment fields remain compatible with this adapter rule.
9. Development marker data remains absent from the production artifact.

## Deferred work

- real kelp/seagrass feature-family adaptation;
- water-depth preferences;
- horizontal cave/enclosure metrics;
- flowing-water/current policy;
- modded-fluid support;
- biome/environment field ownership in Skyforge;
- broader feature-family compatibility;
- structure-start integration;
- morphology/playability refinement.
