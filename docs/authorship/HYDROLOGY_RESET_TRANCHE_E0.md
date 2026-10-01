# Hydrology reset tranche E0 — continuous retained-basin candidates

**Status:** dependent candidate geometry under issue #1084  
**Depends on:** D1 diagnostics  
**Minecraft changes:** none  
**Terrain mutation:** none

## Purpose

Replace the old authority transition

```text
retained semantic sink -> coarse inundated watershed cells -> smoothed raster shoreline
```

with

```text
retained semantic sink + spill authority
    -> solved water datum
    -> sink-connected continuous-terrain sublevel set
    -> interpolated fine shoreline evidence
```

The existing retained-water semantics remain useful. The historical coarse footprint does not define
the new physical shoreline.

## Preserved semantic authority

The planner consumes AUTH-0008-style:

- sink identity;
- catchment/inflow;
- kind;
- retention/saturation/persistence;
- spill elevation.

For POND and LAKE candidates, the current semantic fill fraction determines a candidate water datum
bounded by the sink spill surface.

WETLAND candidates are deliberately deferred. A wetland is primarily a saturated-margin/ecology
condition and should not be forced through the same open-water basin contract.

## Continuous sublevel solve

The raw authored elevation field used by watershed planning is sampled at one quarter of the
watershed spacing on a globally aligned fine lattice.

For candidate datum `h`, eligible samples satisfy:

```text
z(x,z) <= h
```

inside the active island domain.

Only the component connected to the semantic sink is retained. The coarse equal-spill depression is
used to construct a padded search neighborhood; it is not used as the physical wet mask.

Where a connected wet sample borders a dry sample, the shoreline crossing is linearly interpolated
on the underlying elevation values. These crossings are numerical contour evidence, not yet a
backend polygon.

## Boundary diagnostic

If the sink-connected sublevel component reaches the padded search boundary, the candidate records
`reachesSearchBoundary=true`.

That is a qualification signal: the semantic depression/search neighborhood did not contain a closed
basin at the proposed datum. The solver does not excavate a retaining bowl or truncate the lake to
make it fit.

## Deliberate non-goals

E0 does not yet:

- grade littoral terrain;
- author bathymetry;
- merge adjacent semantic sinks;
- solve river/lake inlet and outlet junctions;
- publish a final contour polygon;
- modify the old production footprint planner;
- place Minecraft water.

The next basin qualification pass will measure closure, shoreline grade, required basin modification,
and channel-datum compatibility before any production authority changes.
