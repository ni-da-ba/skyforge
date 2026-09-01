# Skyforge — SF-IMP-0039 Surface Suitability Architecture Update

**Snapshot:** 2026-08-31  
**Accepted through:** SF-IMP-0039

This delta advances the accepted Minecraft integration architecture from lower-surface reachability to explicit physical feature suitability. ADR-0043 is authoritative for the decision.

## Accepted placement path

```text
semantic island geometry
    -> post-surface realization
    -> native Minecraft surface adaptation
    -> vanilla carvers
    -> feature-stage live chunk
    -> additional lower-surface discovery
    -> physical suitability classification
    -> skyforge:suitable_surfaces PlacementModifier
    -> ordinary Minecraft PlacedFeature chain
    -> ordinary Minecraft ConfiguredFeature
    -> suitable lower-surface mutation
```

The original vanilla feature pipeline remains responsible for the ordinary highest-surface target. Supplemental placement operates only on lower independently valid surfaces.

## What is now empirically established

- multi-surface reachability can be filtered into distinct Minecraft physical environments;
- a dry feature probe can execute yet emit zero targets in an all-submerged lower environment;
- submerged floor positions can be selected independently of dry land;
- a normal configured feature can realize visible blocks through the suitability-aware path;
- the parameterized placement modifier survives world save/reload;
- accepted worldgen, material-adaptation and lower-surface invariants remain intact.

The accepted live ocean proof was especially useful because it demonstrated **negative selection** as well as positive selection: `dry_open` emitted zero while `submerged_water_floor` emitted valid targets.

## Current suitability vocabulary

### `dry_land`

Basic lower dry surface: non-fluid support with air above.

### `dry_open`

A stricter dry surface requiring local vertical clearance and supporting thickness. It is a first vegetation-oriented engineering filter, not a universal plant/tree rule.

### `submerged_water_floor`

Solid non-fluid support with Minecraft water immediately above.

These are backend-specific physical categories. They are not Skyforge biome classes.

## Environment ownership boundary

For the current integration phase:

- Skyforge owns world composition, island geometry and structural semantics;
- Minecraft supplies concrete block/fluid/biome environment state;
- the NeoForge adapter combines the two to classify physical suitability;
- Minecraft configured features remain responsible for their own feature-specific behavior.

This boundary is **not permanent policy against Skyforge-owned biome fields**.

A later architecture may introduce backend-neutral Skyforge biome/environment fields when world-composition intent requires them. The intended layering is:

```text
Skyforge biome/environment fields
        -> backend mapping/adaptation
        -> Minecraft biome/block/fluid environment
        -> Minecraft feature and survival semantics
```

That future system should control distribution and intent without duplicating Minecraft representation rules unnecessarily.

## Newly exposed next problem

SF-IMP-0039 proves class separation, but its categories are still purely local and vertical.

A real feature family frequently needs additional geometric context:

- horizontal support footprint;
- distance to island edge;
- local slope / relief;
- vertical water-column depth;
- open-water exposure versus flooded cave enclosure;
- sufficient substrate depth for larger vegetation.

The next implementation should add only the smallest geometry/exposure facts needed to make at least one real feature family behave correctly. It should avoid turning suitability into an unbounded hard-coded taxonomy.

## Recommended near-term sequence

1. introduce local footprint/exposure metrics usable by suitability rules;
2. distinguish open submerged floor from enclosed flooded-cavity floor;
3. prove one actual aquatic vegetation family and one larger dry feature family where feasible;
4. preserve Minecraft-native configured/placed feature definitions rather than building a second feature engine;
5. audit modded-feature composition once vanilla families are understood;
6. revisit backend-neutral biome/environment fields as a separate world-composition milestone;
7. keep structure-start timing separate from feature-stage work;
8. return to morphology/playability refinement after these integration seams are stable.
