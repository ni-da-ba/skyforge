# AUTH-0013 — Semantic Channel Drop Planning

AUTH-0013 turns accepted channel-profile and edge-outflow evidence into sparse, discrete drop events for later terrain and fluid realization.

## Dependency

```text
semantic geography
    -> hydrology / watershed topology
    -> channel selection and hierarchy
    -> riparian corridors
    -> channel geomorphic profiles
    -> discrete channel-drop planning
    -> later terrain / fluid / backend realization
```

## Purpose

AUTH-0012 says whether a routed reach tends alluvial, incised, or cascade-like and provides dimensionless width/depth/incision/gradient potentials. That still does not identify where later realization should treat a steep drainage transition as a distinct waterfall or cascade event.

AUTH-0013 supplies that event layer without choosing block heights, world Y values, fluid blocks, carved cliff geometry, or final plunge pools.

## Event kinds

- `CASCADE_STEP` — an interior local maximum in drop potential that is meaningful but not strong enough to become a distinct waterfall candidate;
- `WATERFALL` — a stronger interior drop event with high drop and stream-power support;
- `EDGE_FALL` — accepted watershed discharge leaving the island domain at an edge outlet.

An `EDGE_FALL` has zero local plunge-pool potential because the receiving surface is outside the island planning domain. Later cross-volume or backend systems may decide whether anything exists below it.

## Interior selection

Interior events are derived from AUTH-0012 channel profiles.

A reach must first exceed minimum gradient and drop-potential thresholds. Drop potential combines:

- AUTH-0012 gradient potential;
- AUTH-0012 stream-power potential;
- descriptor rock competence, which supports stable drop structure.

The planner then keeps only local maxima against immediately connected upstream/downstream channel profiles. This prevents a long steep reach from becoming an uninterrupted chain of nominal waterfalls.

The currently accepted channel network can contain multiple simple or fragmented components. Local maxima alone would therefore overproduce discrete events on some massif/tableland representatives. AUTH-0013 additionally applies a small event budget proportional to the number of accepted channel segments, retaining the strongest local maxima by drop potential and relative discharge.

The first visual evidence pass showed that parallel fragmented components could still place several strong local maxima in one small geomorphic zone. After sorting by event strength, AUTH-0013 therefore also applies island-local spatial non-maximum suppression: accepted interior events must be separated by at least `0.08 * nominalRadius`. Stronger nearby candidates win. This is a coarse semantic de-duplication rule, not a literal minimum distance between future waterfalls.

These are semantic density bounds, not Minecraft feature limits.

## Edge precedence

AUTH-0006 already exposes `EDGE_WATERFALL` hydrologic candidates from accepted watershed edge outlets. AUTH-0013 preserves every such edge-outflow candidate as an `EDGE_FALL` and suppresses an interior event whose downstream channel cell is that same edge outlet, preventing one lip from being double-counted.

## Output semantics

Each `SkyIslandChannelDrop` records:

- event kind;
- source and downstream watershed-cell identity;
- island-local position;
- `dropPotential`;
- `dischargePotential`;
- `persistencePotential`;
- `plungePoolPotential`.

All scalar values are normalized realization potentials in `[0,1]`.

Persistence combines relative discharge, descriptor hydrological potential, and—on interior drops—accepted channel corridor scale. Plunge-pool potential combines stream power, discharge, and substrate erodibility.

## Evidence

The deterministic `authorship-channel-drops-v1` corpus reuses keys 77, 118, 241, 512, 811, and 83.

The atlas renders:

- accepted AUTH-0012 channel profiles in pale gray;
- accepted AUTH-0011 riparian cells in pale green;
- accepted standing water in pale blue;
- `CASCADE_STEP` as orange circles;
- `WATERFALL` as magenta squares;
- `EDGE_FALL` as red diamonds.

Symbol size follows normalized drop potential. `manifest.csv` summarizes event counts and maxima; `drops.csv` preserves per-event provenance and scalar semantics.

The visual gate should reject this milestone if steep networks become carpets of event markers, drop events detach from accepted channel/outlet topology, edge lips are double-counted, or all interior events collapse to one class despite materially different profile support.

## Deferred

- literal waterfall height;
- world-Y or block coordinates;
- terrain cliff carving;
- fluid placement;
- final channel width/depth realization;
- plunge-pool geometry;
- spray/mist/audio/particle effects;
- cave or subterranean waterfall integration;
- cross-island receiving basins;
- Minecraft biome/block IDs;
- sub-grid meanders and naturalized bank geometry.
