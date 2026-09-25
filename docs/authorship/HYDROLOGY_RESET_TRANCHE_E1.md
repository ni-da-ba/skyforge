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
- whether the sink-connected sublevel set escapes the padded search boundary;
- shoreline crossing count.

A basin that reaches the search boundary is not silently truncated or excavated into a bowl.

These diagnostics remain evidence only. Hard basin qualification and river/lake datum compatibility
are the next step.
