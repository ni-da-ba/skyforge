# Hydrology reset tranche C1 — bounded continuous channel centerlines

**Status:** corrective geometry tranche under issue #1084  
**Depends on:** merged Tranche C  
**Terrain mutation:** none  
**Minecraft changes:** none

## Why this tranche exists

Tranche A deliberately used a fine deterministic lattice for terrain-aware least-cost search. That is
appropriate for route discovery, but the polyline emitted by a grid search contains directional
stair-stepping that is an artifact of numerical representation rather than intended river geometry.

Hydraulic grade and especially curvature/width qualification must therefore not treat the raw search
polyline as the final physical centerline.

C1 inserts an explicit representation boundary:

```text
terrain-aware fine-lattice search path
    -> bounded continuous centerline
    -> hydraulic profile / cross-section diagnostics
```

The search path remains routing authority. The centerline may regularize its shape but may not escape
the accepted search tube, abandon the island, climb materially worse terrain, or move shared graph
endpoints.

## Regularization

The first implementation uses two bounded Chaikin subdivision passes followed by approximately
uniform arc-length resampling.

This is a numerical regularizer, not procedural meander synthesis.

Every candidate point is checked against the original fine search path. It is projected back to that
path when any of the following occur:

- distance from the search path exceeds a bounded fraction of planning spacing;
- the smoothed point climbs materially higher pre-hydrologic terrain than its corridor projection;
- the point leaves the authored island interiority envelope.

The constraint is applied again after resampling so interpolation cannot reintroduce a shortcut.

## Network invariant

Source, confluence, and terminal endpoints remain exact:

```text
centerline.first == searchRoute.first
centerline.last  == searchRoute.last
```

Because Tranche B already makes incident reaches share one physical graph-node position, C1 cannot
split a confluence.

## Hydraulic authority change

Tranche C hydraulic sampling now occurs on the continuous centerline rather than the raw A* lattice.

Therefore:

- path length used by minimum downstream grade is continuous-centerline length;
- water-surface samples are located on the continuous centerline;
- bankfull width/depth samples are located on the continuous centerline;
- later curvature diagnostics operate on physical centerline geometry instead of grid stair steps.

This changes the diagnostic basis and therefore invalidates D0/D1 calibration generated from the raw
search lattice. Those evidence tranches must be regenerated after C1.

## Acceptance

Machine tests must prove:

- deterministic regularization;
- exact endpoint preservation;
- bounded deviation from search authority;
- reduced turn severity on a synthetic stair-step path;
- no smoothing shortcut through a synthetic high-terrain corner;
- hydraulic samples align one-for-one with continuous-centerline samples;
- existing shared-node water-datum and downstream-grade invariants remain intact.

## Non-goals

C1 does not:

- invent meanders;
- change semantic drainage topology;
- reroute around a different valley;
- carve terrain;
- define geomorphic rejection thresholds;
- solve retained-water basin contours;
- change Minecraft realization.

Its purpose is to ensure subsequent physical metrics are measured on a continuous geometric object
rather than a search-grid artifact.
