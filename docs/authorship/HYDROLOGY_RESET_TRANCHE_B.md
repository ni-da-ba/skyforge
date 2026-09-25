# Hydrology reset tranche B — shared physical network geometry

**Status:** dependent implementation candidate under issue #1084  
**Depends on:** tranche A / PR #1086  
**Minecraft changes:** none

## Purpose

Tranche A removes ordinary 49x49 planning cells from the set of mandatory physical river waypoints.
Tranche B removes a second failure mode: independently solved reaches must not choose different
physical realizations of the same source, confluence, or terminal and expect downstream rasterization
to repair the gap.

Every semantic graph node now receives exactly one shared physical position before reach routing.

## Pre-hydrologic substrate

All node placement and route search operate on `SkyIslandPreHydrologicTerrainField`, which exposes the
authored elevation tendency **before** AUTH-0014/AUTH-0015 incision, deposition, floodplain, drop,
or retained-waterbody terrain response. The legacy continuous hydrologic terrain field is not an
allowed substrate for the reset because it already contains consequences of the retired hydrology
architecture.

This boundary is explicit so a later compiled-surface projection can replace the semantic elevation
carrier without reintroducing legacy hydrologic shaping.

## Shared node solve

For each semantic source, confluence, or terminal, Skyforge defines a bounded search region around
the coarse graph node. Candidates lie on the same globally aligned fine lattice used by the route
solver.

Candidate cost favors:

- low ridge / positive valley-floor position;
- lower pre-fluvial terrain;
- small displacement from the semantic graph node;
- adequate island interiority.

Confluences receive a larger search radius than simple sources/terminals because a junction is a
network-level geometric event rather than an exact legacy cell center.

The selected physical point is then used by every incident reach.

## Network invariant

For semantic node `v` and every incident reach `e`:

```text
endpoint(e, v) == physicalPosition(v)
```

This is exact in backend-neutral world coordinates. Minecraft is never asked to bridge several
nearby but inconsistent river endpoints.

## Globally aligned fine lattice

Both node selection and route search use:

```text
fineStep = planningSpacing / 4
x = i * fineStep
z = j * fineStep
```

for integer `i,j`.

Global alignment matters because a per-reach local search origin would allow two reaches to use
different sub-grid coordinate systems even when they share semantic authority.

## Deliberate limitations

This remains candidate geometry, not publishable hydrology.

Tranche B does not yet:

- jointly optimize all routes and node positions in one global nonlinear solve;
- solve channel bed or water-surface elevation;
- determine channel width/depth;
- prove lateral bank slopes or excavation volume;
- solve retained water;
- carve terrain;
- rasterize Minecraft blocks.

A later hydraulic/geomorphic qualification stage may reject a shared node or route and request a new
candidate. The important invariant established here is that network connectivity is mathematical
input to physical geometry, not a voxel repair problem.
