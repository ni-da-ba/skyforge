# SF-IMP-0039 — Surface Suitability Acceptance

**Status:** Accepted  
**Date:** 2026-08-31

## Accepted scope

SF-IMP-0039 adds the first explicit Minecraft-owned distinction between lower-surface **reachability** and **feature suitability**.

Accepted production code adds `skyforge:suitable_surfaces` with these initial adapter-local suitability classes:

- `dry_land`;
- `dry_open`;
- `submerged_water_floor`.

The accepted SF-IMP-0038 `skyforge:additional_surfaces` primitive remains unchanged as the dry lower-surface reachability baseline.

## Automated evidence

The focused verifier passed:

```bat
scripts\verify-sf-imp-0039-surface-suitability.bat
```

The repository-wide gate also passed:

```bat
gradlew.bat check
```

The automated suite establishes:

- codec/registry availability for `skyforge:suitable_surfaces`;
- exact regression compatibility for `skyforge:additional_surfaces`;
- `dry_open` acceptance on sufficiently thick surfaces with sufficient headroom;
- `dry_open` rejection under a low ceiling while `dry_land` remains reachable;
- `dry_open` rejection on thin support while `dry_land` remains reachable;
- `submerged_water_floor` recognition independent from dry land;
- accepted SF-IMP-0036 through SF-IMP-0038 integration regressions;
- backend-neutral module independence;
- exclusion of development-only marker/feature resources from the production JAR.

## Real-client evidence

A new SF-IMP-0039 development world placed the deterministic Massif above ocean terrain.

Origin-area diagnostics consistently reported:

```text
dryLand=0
dryOpen=0
submergedWaterFloor=256
dryOpenQueries=14
dryOpenEmitted=0
submergedQueries=4
submergedEmitted=4
```

This proves wrong-class suppression as well as correct-class reachability:

- dry feature probes were invoked but emitted zero targets;
- submerged feature probes emitted targets;
- sparse lapis `minecraft:simple_block` markers were visibly realized on the seabed below the Massif;
- dry-open emerald/grass probes did not place underwater;
- no systematic marker duplication appeared on the vanilla highest Massif surface.

The user observed generally fewer markers inside flooded caves than on the exposed seabed. This is useful empirical evidence but is not promoted to an open-water suitability guarantee.

## Persistence evidence

The same world was saved, closed and reopened.

Reload was clean:

- Massif persisted;
- marker blocks persisted;
- no suitability placement codec/registry error occurred;
- no chunk corruption or obvious duplicate generation was observed.

## Accepted invariants

1. Reachability and suitability are separate concepts.
2. `skyforge:additional_surfaces` remains the accepted SF-IMP-0038 dry reachability primitive.
3. `skyforge:suitable_surfaces` is adapter-local and parameterized by physical Minecraft suitability.
4. `dry_open` is stricter than `dry_land`.
5. `submerged_water_floor` is independently selectable and is not treated as dry land.
6. The vanilla highest surface remains outside supplemental placement.
7. Minecraft block/fluid state owns these concrete physical suitability decisions for this milestone.
8. No backend-neutral Minecraft biome, block, fluid or feature concepts are introduced.
9. This milestone does not foreclose later Skyforge ownership of backend-neutral biome/environment fields.
10. Development-only markers remain diagnostics rather than intended game content.

## Deferred work

- open-ocean versus flooded-cavity aquatic suitability;
- true kelp/seagrass feature behavior;
- local horizontal footprint / slope / edge-clearance metrics;
- tree-scale suitability;
- snow/ice and other surface families;
- production feature-family adaptation and mod compatibility;
- backend-neutral Skyforge biome/environment field ownership;
- structure-start integration;
- morphology/playability refinement.
