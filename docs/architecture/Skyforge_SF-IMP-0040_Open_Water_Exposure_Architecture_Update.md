# Skyforge SF-IMP-0040 Open-Water Exposure Architecture Update

**Accepted:** 2026-08-31

## What changed

SF-IMP-0040 adds one new Minecraft-owned physical suitability class to the accepted supplemental feature-placement layer:

```text
open_water_floor
```

It refines, rather than replaces, accepted `submerged_water_floor`.

## Updated adapter-side suitability stack

```text
supplemental surface reachability
    -> Minecraft physical suitability
        -> dry_land
        -> dry_open
        -> submerged_water_floor
        -> open_water_floor
    -> Minecraft placed-feature pipeline
```

`open_water_floor` is a strict subset of `submerged_water_floor`.

The selector examines the live Minecraft column above a submerged support. A contiguous vertical water column that reaches air before any solid/non-water ceiling qualifies. A capped flooded floor remains submerged but is not promoted to open water.

## Why this remains backend-side

The rule consumes concrete Minecraft block/fluid state and answers a representation-specific placement question. It does not represent world-scale ecological or biome intent.

Accordingly, SF-IMP-0040 does not add water, ocean, biome, climate, or ecology concepts to backend-neutral modules.

This boundary is compatible with later Skyforge-owned environment fields:

```text
future Skyforge environmental intent
    -> backend feature/environment mapping
    -> Minecraft-local physical suitability
    -> concrete placed feature
```

A future Skyforge ocean/biome field may decide **where** aquatic intent belongs. The adapter can still decide whether a concrete Minecraft position is physically usable by a particular feature family.

## Exposure versus sky visibility

The accepted rule deliberately does not require direct sky visibility.

A floating Skyforge island above an air gap must not make an ordinary ocean floor look like a sealed flooded cave. Therefore the classifier stops once the contiguous water column reaches air; blocks above that air gap are irrelevant to this local exposure decision.

## Runtime evidence

The accepted SF-IMP-0040 client specimen generated over desert terrain, so it supplied a negative integration proof rather than an ocean-positive one.

The live feature pipeline successfully queried `open_water_floor` while the selector returned zero aquatic targets. Dry placement continued normally and no false aquatic markers appeared.

Positive open-water classification and capped-column rejection are covered by automated tests. The generic live realization path for supplemental submerged feature placement was already established by SF-IMP-0039.

## Preserved invariants

- backend-neutral modules remain free of Minecraft APIs;
- accepted SF-IMP-0038 reachability is unchanged;
- accepted SF-IMP-0039 suitability classes remain available;
- vanilla keeps ownership of the highest surface;
- supplemental features remain additive rather than a rerun of all vanilla decoration;
- development markers remain excluded from the production JAR;
- later Skyforge biome/environment ownership is not foreclosed.

## What this still does not solve

SF-IMP-0040 is not an aquatic ecology system. It does not yet define:

- kelp versus seagrass behavior;
- minimum/maximum water depth;
- flowing water/current suitability;
- horizontal openness;
- ocean-biome identity;
- modded fluids;
- Skyforge-owned climate/biome fields.

The main architectural result is narrower: **supplemental feature reachability, physical suitability, and environmental/biome intent are now cleanly separable concerns.**
