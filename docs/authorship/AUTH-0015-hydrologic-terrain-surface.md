# AUTH-0015 — Hydrologically Adjusted Terrain Surface

AUTH-0015 applies the accepted hydrologic terrain-response semantics to a **derived** coarse elevation surface while preserving the original authored semantic field unchanged.

## Dependency

```text
original semantic elevation
    -> watershed / channel / waterbody planning
    -> channel profiles / drop events
    -> hydrologic terrain influence
    -> derived hydrologically adjusted surface
    -> later continuous terrain realization / backend projection
```

## Purpose

AUTH-0014 says where terrain should tend to incise, accumulate material, broaden into a floodplain, or respond to a discrete drop. AUTH-0015 turns those tendencies into a bounded normalized elevation adjustment on the same 49x49 island-local planning lattice.

This is the first authorship milestone where hydrology visibly changes the terrain surface itself. It is still not Minecraft terrain and does not modify the compiled upper-surface graph used by current backends.

## Preservation boundary

`SkyIslandSemanticFieldSet.elevationTendency()` remains authoritative as the **original authored morphology**. AUTH-0015 does not mutate or replace that field.

Instead, `SkyIslandHydrologicTerrainSurfacePlanner` emits one derived surface sample for every active watershed cell. Each sample records both the original and adjusted normalized elevation potentials plus the component adjustments that produced the change.

This preserves a clean causal chain and keeps before/after evidence possible.

## Terrain response

For a non-reserved cell carrying AUTH-0014 influence:

- incision lowers the surface in proportion to accepted incision support;
- drop shaping adds localized lowering around accepted waterfall/cascade events;
- deposition can raise the surface, attenuated where incision support is already strong;
- floodplain shaping pulls an accepted riparian cell toward the mean grade of the channel reach that owns it. This can raise or lower the lateral cell and therefore broadens low-gradient corridors without blindly lowering every floodplain.

The first-pass transform caps net normalized change per coarse cell at:

- `0.16` lowering;
- `0.08` raising.

These bounds are semantic calibration limits, not metres, blocks, or Minecraft Y offsets.

## Ownership precedence

Standing-water semantics remain protected exactly. AUTH-0009 retained-water footprint cells and AUTH-0010 waterbody-margin cells retain their original terrain elevation in AUTH-0015.

Cells with no AUTH-0014 hydrologic terrain influence are also unchanged.

## Evidence

The deterministic `authorship-hydrologic-terrain-surface-v1` corpus reuses keys 77, 118, 241, 512, 811, and 83.

Each island panel is intentionally designed for direct visual reading:

- **BEFORE** — original normalized authored elevation;
- **AFTER** — the same coarse surface after hydrologic shaping;
- **CHANGE** — only the difference, with blue for lowering and orange for raising.

Accepted channels are overlaid in blue on AFTER and CHANGE. Retained standing-water cells are shown in cyan.

The grayscale elevation scale is identical in BEFORE and AFTER, so visible differences represent actual AUTH-0015 shaping rather than rescaled rendering.

`manifest.csv` summarizes changed/lowered/raised cell counts and adjustment magnitudes. `cells.csv` preserves per-cell before/after values and each component adjustment.

## Acceptance gate

Reject AUTH-0015 if:

- river shaping changes large unrelated regions of the island;
- retained-water or waterbody-margin terrain moves;
- cascade-heavy islands flatten into broad plains;
- low-gradient river systems show no lateral grading/deposition at all;
- normalized elevation changes exceed the accepted bounds;
- the BEFORE/AFTER evidence cannot be visually attributed to the accepted hydrologic network.

## Deferred

AUTH-0015 does **not** yet author:

- a continuous sub-grid modified elevation field;
- literal channel widths or depths;
- bank polygons or meanders;
- sediment transport iteration;
- physical erosion simulation;
- waterfall cliff or plunge-pool geometry;
- updates to compiled upper-surface/density graphs;
- world-Y coordinates;
- Minecraft blocks, fluids, biomes, or placed features.

Those remain downstream milestones after the coarse semantic surface is accepted.
