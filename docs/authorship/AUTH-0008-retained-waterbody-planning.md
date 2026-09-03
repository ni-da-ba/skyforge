# AUTH-0008 — Retained Waterbody Planning

AUTH-0008 converts retained watershed sinks into backend-neutral waterbody candidates with topology-derived contributing catchments.

## Dependency

```text
semantic geography
    -> local hydrology and ecology
    -> watershed topology and accumulation
    -> retained sinks
    -> retained-waterbody semantics
    -> later water-surface geometry and terrain realization
```

## Candidate semantics

Each retained sink produces exactly one `SkyIslandWaterbodyCandidate` containing:

- the retained watershed-cell anchor;
- the number and fraction of active watershed cells whose routed paths terminate at that sink;
- island-relative inflow from the sink's accumulated authored runoff;
- local retention and ecological saturation potentials;
- a normalized persistence signal;
- a normalized basin-scale signal derived from catchment fraction;
- a semantic kind: `POND`, `LAKE`, or `WETLAND`.

The contributing catchment is not randomized and is not inferred from Euclidean distance. Each watershed cell follows the already accepted acyclic downstream topology to its terminal outlet or retained sink. Only cells whose terminal is a retained sink contribute to that candidate's catchment.

## Classification

Waterbody kind is a deterministic interpretation of the same authored causes already present in the island model.

- `WETLAND` favors high saturation and retention where open-water dominance is weaker.
- `LAKE` requires stronger open-water support from inflow, basin scale, and persistence.
- `POND` is the residual retained-water case when neither broader wetland nor lake semantics dominate.

No extra random stream selects waterbody kind.

## Scale and persistence

`basinScale` converts catchment fraction into a normalized downstream geometry signal using an 18% island-catchment reference scale. It is deliberately not a radius, shoreline, area in blocks, or water level.

`persistence` combines relative inflow, retention, saturation, and basin scale. It represents how strongly the semantic evidence supports persistent retained water; it does not specify seasonal simulation, evaporation, or fluid volume.

## Evidence

Retained sinks are intentionally uncommon after the AUTH-0005 priority-flood correction, so the deterministic `authorship-waterbodies-v1` corpus does not search for six rare cases or alter watershed policy to manufacture them. It uses the accepted key-83 retained-basin case together with five accepted drainage controls from AUTH-0006/0007.

The atlas renders the accepted AUTH-0007 channel network in gray and waterbody planning anchors by kind:

- cyan: pond;
- dark blue: lake;
- teal: wetland.

Outer symbol size follows semantic basin scale and the inner dot follows persistence. These symbols are diagnostic markers only. They are intentionally not drawn as shorelines because AUTH-0008 does not yet author water-surface geometry. `manifest.csv` summarizes each island while `candidates.csv` records every retained candidate and its input/output metrics. The control panels verify that AUTH-0008 does not invent waterbodies where upstream watershed topology retained none.

## Deferred

- explicit basin footprint and shoreline geometry;
- spill elevation and water-surface level;
- lake depth or bathymetry;
- wetland spatial extent;
- river-bank and channel geometry;
- waterfall drop geometry;
- erosion feedback into terrain fields;
- irregular island-domain naturalization;
- backend realization.
