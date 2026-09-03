# AUTH-0009 — Retained Waterbody Footprint Planning

AUTH-0009 converts accepted retained-waterbody candidates into connected coarse semantic inundation footprints.

## Dependency

```text
semantic geography
    -> local hydrology and ecology
    -> priority-flood watershed topology
    -> retained-waterbody candidates
    -> retained-waterbody footprint planning
    -> later shoreline naturalization and backend realization
```

## Priority-flood spill metadata

The accepted AUTH-0005 watershed planner already computes the lowest priority-flood spill surface needed to connect every active planning cell to the island boundary. AUTH-0009 preserves two pieces of that information on each `SkyIslandWatershedCell`:

- `spillSurfacePotential`: normalized semantic elevation potential of the priority-flood spill surface;
- `fillDepthPotential`: `spillSurfacePotential - surfacePotential`.

These values are not Minecraft Y coordinates, metres, blocks, or literal fluid depths. They remain normalized semantic planning values in the same island-local elevation space used by the existing field system.

## Catchments, depressions, and source anchors

AUTH-0008 catchments describe drainage-tree provenance: a catchment cell is one whose routed downstream path terminates at a retained sink. That is appropriate for inflow accounting and retained-water evidence, but it is not equivalent to geometric basin area.

AUTH-0009 derives geometry from the priority-flood depression instead. Cells are considered part of the same candidate depression when they:

1. have positive fill depth;
2. share the retained sink's priority-flood spill surface; and
3. are connected to the retained sink on the coarse planning lattice.

This prevents a retained waterbody from collapsing into a one-cell-wide drainage-tree branch.

A second distinction is equally important: multiple AUTH-0008 retained-sink candidates may occupy one geometric depression. AUTH-0009 therefore treats each candidate as a **source anchor**, first builds its provisional depression-based inundation footprint, and then transitively coalesces provisional footprints that overlap.

The resulting geometric footprint preserves every contributing AUTH-0008 candidate in `sourceCandidates`. Coalescing does not erase the upstream inflow/provenance semantics; it only prevents one physical depression from being counted as several overlapping waterbodies.

If overlapping source candidates disagree on `POND`, `LAKE`, or `WETLAND`, the footprint retains that disagreement explicitly through its source list and `hasMixedKinds()` diagnostic. AUTH-0009 does not silently resolve mixed semantics.

## Footprint planning

Each retained-waterbody candidate receives a deterministic fill fraction based on its already accepted kind and persistence:

- `WETLAND` remains relatively shallow;
- `POND` occupies an intermediate portion of its available fill depth;
- `LAKE` may approach more of the available spill depth.

The candidate water-surface potential is interpolated between the retained sink surface and its priority-flood spill surface. Within the connected depression, a cell is eligible when its authored surface lies at or below that planned water surface.

Eligibility alone does not imply inundation. AUTH-0009 performs an eight-neighbor flood fill from the candidate source anchor and accepts only eligible cells connected to that source.

After provisional footprints are generated, overlapping footprints are grouped transitively. A merged waterbody:

- retains all source candidates;
- uses the highest candidate water-surface potential without exceeding the shared spill surface;
- uses the union of contributing depression cells;
- refloods eligible cells from all source anchors;
- records one connected geometric footprint rather than duplicate overlapping bodies.

## Output semantics

`SkyIslandWaterbodyFootprint` records:

- all AUTH-0008 `sourceCandidates` contributing to the geometric body;
- planned water-surface potential;
- priority-flood spill-surface potential;
- maximum source fill fraction;
- connected depression cell count;
- connected inundated footprint cells.

Each `SkyIslandWaterbodyFootprintCell` records:

- watershed-cell identity and island-local position;
- authored surface potential;
- relative water-depth potential beneath the planned surface;
- whether the cell lies on the coarse footprint shoreline.

The footprint reports an `inundatedDepressionFraction`; AUTH-0008 catchment size remains separately available on each source candidate for inflow interpretation.

## Evidence

The deterministic `authorship-waterbody-footprints-v1` corpus uses retained-basin key 83 plus the same dry/outflow controls used by AUTH-0008: keys 77, 118, 241, 512, and 811.

The atlas renders:

- accepted AUTH-0007 channel segments in gray;
- connected depression-based footprint cells by waterbody kind;
- purple for a coalesced footprint whose source candidates have mixed kinds;
- dark borders on coarse shoreline cells;
- every retained source anchor as a black dot.

`manifest.csv` distinguishes geometric `footprints` from upstream `sourceCandidates`. `footprints.csv` records source sink IDs/kinds, depression size, inundated depression fraction, shoreline count, semantic water/spill surfaces, and maximum semantic depth.

For key 83, the accepted geometry should coalesce the two AUTH-0008 wetland source candidates into one geometric wetland footprint if their provisional inundation footprints overlap. The five control islands are expected to remain empty.

## Evidence-gate history

AUTH-0009 deliberately retained two failed visual gates during development rather than weakening review criteria:

1. a green build using drainage-tree catchments produced narrow channel-like footprints and was rejected;
2. a green build using connected spill depressions produced basin-like geometry, but revealed two source candidates generating the same overlapping body and was rejected for double-counting.

The coalescing model exists specifically to resolve the second failure while preserving both AUTH-0008 source anchors.

## Deferred

- sub-grid shoreline smoothing and irregular shoreline geometry;
- literal Minecraft water Y levels;
- fluid placement and block realization;
- bathymetry in blocks;
- seasonal water-level simulation;
- wetland vegetation realization;
- river-bank and channel geometry;
- erosion feedback into terrain fields;
- irregular island-domain naturalization.
