# AUTH-0012 — Semantic Channel Profile Planning

AUTH-0012 derives coarse geomorphic realization potentials for every accepted AUTH-0007 routed channel reach.

## Dependency

```text
semantic geography
    -> hydrology / watershed topology
    -> selected channel network + hierarchy
    -> riparian corridor semantics
    -> channel profile planning
    -> later terrain carving / backend realization
```

## Purpose

AUTH-0007 identifies routed reaches and network hierarchy. AUTH-0011 identifies dry land influenced by those reaches. Neither yet distinguishes a broad low-gradient river reach from a narrow incising channel or a steep cascade.

AUTH-0012 introduces that missing semantic profile without assigning literal block widths, depths, bed elevations, or Minecraft feature IDs.

## Inputs

For each accepted channel segment the planner combines:

- AUTH-0007 relative discharge;
- AUTH-0007 corridor scale;
- normalized authored surface drop between the source and downstream watershed cells;
- descriptor rock competence;
- descriptor erosion maturity.

The resulting quantities remain dimensionless values in `[0, 1]`.

## Output semantics

`SkyIslandChannelProfile` records:

- the accepted routed `SkyIslandChannelSegment`;
- `gradientPotential`;
- `streamPowerPotential`;
- `bankfullWidthPotential`;
- `depthPotential`;
- `incisionPotential`;
- a coarse geomorphic `SkyIslandChannelProfileKind`.

Profile kinds are:

- `ALLUVIAL` — comparatively broad/lower-gradient reaches whose downstream realization may favor wider banks and depositional geometry;
- `INCISED` — reaches with stronger long-term erosive/incision potential;
- `CASCADE` — steep, energetic reaches that should not be realized as broad placid channels.

These names describe realization intent, not a claim that the coarse semantic lattice is a complete fluvial geomorphology model.

## Policy

Surface drop is normalized against a fixed semantic reference rather than converted to world Y distance. Stream power combines that gradient signal with relative discharge and network corridor scale.

Incision potential combines stream power, substrate erodibility (`1 - rockCompetence`), and erosion maturity.

Width potential is driven primarily by network scale and discharge, but is reduced on steep reaches. This is deliberate: channel importance and literal breadth are not the same semantic quantity.

Depth potential combines discharge, network scale, and incision.

Classification currently resolves in priority order:

1. sufficiently steep/energetic reaches -> `CASCADE`;
2. otherwise sufficiently incised reaches -> `INCISED`;
3. remaining reaches -> `ALLUVIAL`.

## Evidence

The deterministic `authorship-channel-profiles-v1` corpus reuses keys 77, 118, 241, 512, 811, and 83.

The atlas renders:

- accepted AUTH-0011 riparian cells in pale green;
- accepted standing water in pale blue;
- `ALLUVIAL` reaches in teal;
- `INCISED` reaches in purple;
- `CASCADE` reaches in orange;
- stroke thickness from `bankfullWidthPotential`.

`manifest.csv` summarizes profile counts and maxima per island. `profiles.csv` records each routed reach and all derived profile potentials.

The visual gate should reject the milestone if steep reaches become indiscriminately broad, profile classes are spatially incoherent, every island collapses to one class despite materially different gradients, or the profile overlay obscures/breaks accepted channel topology.

## Deferred

AUTH-0012 does **not** define:

- literal channel width or depth in blocks/metres;
- final bed elevation;
- terrain carving;
- bank geometry;
- meanders or sub-grid path naturalization;
- sediment transport simulation;
- erosion/deposition feedback;
- floodplain inundation frequency;
- fluid placement;
- Minecraft biome or block identity;
- waterfall block geometry;
- irregular island-domain naturalization.
