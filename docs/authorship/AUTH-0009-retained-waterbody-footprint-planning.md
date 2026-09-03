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

## Footprint planning

Each retained-waterbody candidate receives a deterministic fill fraction based on its already accepted kind and persistence:

- `WETLAND` remains relatively shallow;
- `POND` occupies an intermediate portion of its available fill depth;
- `LAKE` may approach more of the available spill depth.

The planned water-surface potential is interpolated between the retained sink surface and its priority-flood spill surface. A watershed cell is eligible for the waterbody only when:

1. its routed terminal is that retained sink; and
2. its authored surface potential is at or below the planned water surface.

Eligibility alone does not imply inundation. AUTH-0009 performs an eight-neighbor flood fill from the retained sink and accepts only eligible cells connected to that sink. Disconnected low cells therefore cannot become part of the same waterbody merely because they lie below the same elevation threshold.

## Output semantics

`SkyIslandWaterbodyFootprint` records:

- the AUTH-0008 waterbody candidate;
- planned water-surface potential;
- priority-flood spill-surface potential;
- semantic fill fraction;
- connected footprint cells.

Each `SkyIslandWaterbodyFootprintCell` records:

- watershed-cell identity and island-local position;
- authored surface potential;
- relative water-depth potential beneath the planned surface;
- whether the cell lies on the coarse footprint shoreline.

## Evidence

The deterministic `authorship-waterbody-footprints-v1` corpus uses retained-basin key 83 plus the same dry/outflow controls used by AUTH-0008: keys 77, 118, 241, 512, and 811.

The atlas renders:

- accepted AUTH-0007 channel segments in gray;
- connected footprint cells by waterbody kind;
- dark borders on coarse shoreline cells;
- retained-sink anchors as black dots.

`manifest.csv` summarizes each island. `footprints.csv` records each retained footprint's catchment size, inundated fraction, shoreline count, fill fraction, semantic water/spill surfaces, and maximum semantic depth.

Control islands are expected to remain empty. That is positive evidence that AUTH-0009 does not create a water footprint without an upstream retained-waterbody candidate.

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
