# AUTH-0010 — Retained Waterbody Margin Planning

AUTH-0010 derives dry semantic transition zones around accepted AUTH-0009 retained-waterbody footprints.

## Dependency

```text
semantic geography
    -> hydrology / ecology
    -> watershed topology
    -> retained-waterbody candidates
    -> retained-waterbody footprints
    -> waterbody margin planning
    -> later shoreline ecology / terrain realization
```

## Scope

AUTH-0010 does not change the accepted water footprint. It evaluates active watershed cells within two coarse lattice steps of each AUTH-0009 shoreline and assigns dry cells a waterbody-margin role when local semantics support that transition.

The planner combines:

- shoreline proximity;
- AUTH-0003 saturation potential;
- AUTH-0004 retention potential;
- normalized elevation head above the planned AUTH-0009 water surface.

Cells below the acceptance threshold remain ordinary dry terrain. Margin cells are classified as:

- `SHORE_TRANSITION`: a dry near-shore transition supported primarily by proximity and low elevation head;
- `SATURATED_FRINGE`: a dry but strongly moisture-supported transition where saturation and retention are high enough to justify a wet fringe signal.

These labels are semantic planning roles. They are not Minecraft biome IDs, vegetation placements, fluid blocks, mud blocks, beaches, or literal shoreline geometry.

## Ownership

A dry watershed cell can belong to at most one planned waterbody margin. If two nearby waterbody footprints compete for the same cell, AUTH-0010 assigns it to the footprint with the stronger margin potential. Exact ties resolve by deterministic footprint ordinal.

This prevents overlapping margin halos from silently double-counting one dry cell while preserving independent geometric waterbodies from AUTH-0009.

## Evidence

The deterministic `authorship-waterbody-margins-v1` corpus uses retained-basin key 83 plus the same five dry/outflow controls used by AUTH-0008 and AUTH-0009: 77, 118, 241, 512, and 811.

The atlas renders:

- accepted channel segments in gray;
- accepted AUTH-0009 waterbody cells in blue/teal;
- dry `SHORE_TRANSITION` cells in gold;
- dry `SATURATED_FRINGE` cells in green;
- retained-source anchors as black dots.

`manifest.csv` summarizes island-level water and margin counts. `margins.csv` records per-waterbody source IDs, water-cell count, dry-margin count, margin-kind counts, and mean/max margin potential.

Control islands are expected to remain margin-free because AUTH-0010 cannot create a margin without an upstream AUTH-0009 waterbody footprint.

## Acceptance gate

Reject the milestone if:

- margin cells overlap the accepted water footprint;
- dry controls gain invented margins;
- margin ownership duplicates cells between waterbodies;
- the fringe expands into a broad island-scale halo instead of remaining shoreline-local;
- the wetland control produces no saturated-fringe signal;
- visual evidence implies final Minecraft shoreline or biome geometry.

## Deferred

- sub-grid shoreline smoothing and irregular shoreline geometry;
- riparian corridors along flowing channels;
- wetland vegetation and substrate realization;
- literal Minecraft biome assignment;
- fluid placement and block-level water surfaces;
- erosion feedback;
- irregular island-domain naturalization.
