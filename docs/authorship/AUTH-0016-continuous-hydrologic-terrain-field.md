# AUTH-0016 — Continuous Hydrologic Terrain Field

AUTH-0016 converts the accepted AUTH-0015 coarse hydrologic terrain surface into a continuous backend-neutral elevation field without replacing the original authored morphology.

## Dependency

~~~text
AUTH-0002 continuous authored elevation
    + AUTH-0015 coarse hydrologic adjustment anchors
    -> continuous hydrologic adjustment field
    -> continuous hydrologically shaped elevation
    -> later terrain-graph / backend projection
~~~

## Purpose

AUTH-0015 proved that accepted drainage, floodplain, deposition, and drop semantics can modify a derived terrain surface conservatively. Its output is intentionally tied to the 49x49 planning lattice, which makes the causal evidence easy to inspect but is not an appropriate final terrain representation.

AUTH-0016 removes that lattice as a sampling restriction.

The original AUTH-0002 elevation tendency remains unchanged. The new field interpolates only the signed AUTH-0015 adjustment and adds that result back to the original continuous elevation.

This means hydrology remains downstream of authored morphology rather than becoming a second independent terrain generator.

## Exact coarse-anchor preservation

Every active AUTH-0015 surface cell remains an authoritative anchor.

At the exact island-local position of any active coarse cell:

- the continuous adjustment equals the accepted AUTH-0015 net adjustment;
- the continuous adjusted elevation equals the accepted AUTH-0015 adjusted elevation.

The implementation stores zero adjustment at inactive lattice locations and uses the accepted active-cell net adjustments everywhere else.

## Interpolation

Between adjacent lattice anchors, AUTH-0016 uses tensor-product quintic smootherstep interpolation.

The interpolation weights remain in [0, 1]. Therefore the continuous adjustment is a convex blend of accepted neighboring anchor values and cannot create a stronger lowering or raising extreme than AUTH-0015 already accepted.

This first continuous pass prioritizes deterministic bounded behavior over simulated geomorphology.

## Island-boundary behavior

The watershed planner only admits cells whose semantic interiority exceeds 0.025.

AUTH-0016 applies a narrow domain fade from interiority 0 to 0.025. The fade is exactly 1 at every active watershed anchor, so accepted coarse values remain exact while adjustment smoothly approaches zero at the island boundary.

Positions outside the nominal island extent receive zero hydrologic adjustment.

## Evidence

The authorship-continuous-hydrologic-terrain-v1 corpus reuses keys 77, 118, 241, 512, 811, and 83.

Each panel is pixel-sampled from the continuous fields rather than drawing coarse cells:

- BEFORE — original AUTH-0002 continuous elevation;
- AFTER — continuous elevation after AUTH-0016 hydrologic shaping;
- CHANGE — actual continuous difference; blue lowers and orange raises.

Thin blue lines show accepted channel topology. Cyan marks retained standing-water anchors.

manifest.csv records high-resolution raster statistics including changed-sample fraction, extrema, mean absolute adjustment, and maximum coarse-anchor reproduction error.

anchors.csv records every accepted coarse anchor and its continuous reproduction error.

## Acceptance gate

Reject AUTH-0016 if:

- coarse AUTH-0015 anchors are not reproduced to floating-point tolerance;
- interpolation creates lowering or raising beyond accepted AUTH-0015 extrema;
- adjustment leaks outside the island domain;
- the continuous atlas develops square plateaus, checkerboarding, or obvious interpolation seams worse than the coarse input;
- hydrologic shaping becomes island-wide rather than network-localized;
- retained-water neighborhoods become visibly displaced relative to accepted drainage.

## Deferred

AUTH-0016 does not yet author:

- a new island-domain shape;
- meandering or sub-grid channel centerlines;
- literal channel widths or depths;
- bank polygons;
- sediment transport;
- iterative erosion;
- waterfall cliff or plunge-pool geometry;
- a compiled terrain graph using this field;
- world-Y coordinates;
- Minecraft blocks, fluids, biomes, or placed features.

The remaining visible straight/parallel channel geometry is inherited from the accepted coarse channel topology and is not solved by interpolation alone.
