# Hydrology reset tranche F2B — head-independent hydraulic geometry skeleton

**Status:** structural implementation candidate under issue #1084  
**Depends on:** accepted F2 contract and proven F2A bounded-QP primitive  
**Terrain mutation:** none  
**Hydraulic head authority:** none  
**Minecraft changes:** none

## Purpose

F2B removes a dependency inversion left by the pre-reset hydraulic planner.

The historical `SkyIslandHydraulicChannelNetworkPlanner` currently computes both:

1. head-independent geometry — C2 centerlines, discharge interpolation, width, depth, raw terrain;
2. a staging water-surface profile produced by topological clipping.

F2 must consume (1) without accidentally inheriting (2) as physical authority.

F2B therefore extracts one shared head-independent hydraulic geometry skeleton consumed by both the
legacy diagnostic planner and the new bounded-profile line.

## Shared calibration

Discharge-scaled geometry has one source of truth:

```text
w(Q) = a Q^b
d(Q) = c Q^f
freeboard(Q) = f_0 + k d(Q)
```

The existing Skyforge dimensionless coefficients and exponents are unchanged.

The legacy public/package-visible width/depth helpers delegate to this shared calibration so accepted
tests and downstream callers retain compatibility.

## Skeleton sample

Each C2 sample contains only:

- world-local horizontal position;
- downstream station fraction;
- accumulated relative discharge;
- bankfull half-width;
- hydraulic depth potential;
- pre-hydrologic terrain elevation.

It contains **no**:

- solved free-surface datum;
- bed elevation;
- required excavation;
- qualification result;
- terrain delta;
- Minecraft state.

A preferred terrain-following water-surface potential may be derived as an objective target, but is
not stored or promoted as accepted head authority.

## Skeleton reach

Each reach preserves exactly:

- semantic/geomorphic reach identity;
- current accepted C2 centerline;
- head-independent samples;
- path length;
- maximum width/depth geometry.

Reach ordering remains canonical by semantic start/end identity.

## Legacy equivalence requirement

The historical clipped planner is refactored to consume the skeleton and must preserve its existing
observable result exactly for the unchanged fixed corpus.

Machine evidence compares skeleton versus legacy output for:

- route identity;
- centerline identity;
- path length;
- station positions;
- discharge;
- bankfull width;
- depth;
- raw terrain samples;
- maximum width/depth.

Existing legacy hydraulic tests remain green. Any change to historical staging head results is a
failure of this tranche.

## New authority boundary

After F2B:

```text
semantic drainage
  -> C2 centerline
  -> head-independent hydraulic geometry skeleton
      -> legacy clipped profile (historical diagnostics only)
      -> F2C bounded profile solve (new authority path)
```

The dependency must never reverse: the skeleton does not know about either head solver.

## Stop boundary

F2B does **not**:

- solve a new water-surface profile;
- modify D2/E2 thresholds;
- change any accepted/rejected hydrology classification;
- modify terrain;
- resolve confluences/cascades/basins;
- touch Minecraft.

F2C owns the first bounded-QP consumer and its world-space head constraints.
