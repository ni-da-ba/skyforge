# Hydrology reset tranche E1 — retained-basin diagnostics

**Status:** diagnostic tranche under issue #1084
**Depends on:** E0 continuous retained-basin candidates
**Terrain mutation:** none
**Minecraft changes:** none

## Purpose

E1 measures whether a continuous retained-water candidate is geometrically credible before any
littoral grading, bathymetry authoring, or Minecraft realization.

For each POND/LAKE basin, E1 records:

- equivalent basin diameter from continuous wet area;
- maximum depth in authored world units;
- maximum-depth / equivalent-diameter ratio;
- maximum pre-hydrologic shoreline grade;
- spill headroom between water datum and semantic spill surface;
- exact semantic terminal-reach count at the retained sink;
- maximum hydraulic terminal-datum mismatch against the basin water datum;
- whether the sink-connected sublevel set escapes the padded search boundary;
- shoreline crossing count, which remains a sampling diagnostic rather than a contour-shape measure;
- marching-squares shoreline perimeter from the sampled water-level/interiority level set;
- dimensionless shoreline isoperimetric ratio P² / (4πA) as a compactness/irregularity signal;
- closed contour-loop count and non-degree-two contour vertices, to establish structural closure.

The contour metrics are resolution-dependent measurements whose estimator is checked with analytic
and grid-refinement fixtures. They have no calibrated E2 limits and do not independently grant
terrain authority.

A basin that reaches the search boundary is not silently truncated or excavated into a bowl.

These diagnostics remain evidence only. Hard basin qualification and river/lake datum compatibility
are the next step.
