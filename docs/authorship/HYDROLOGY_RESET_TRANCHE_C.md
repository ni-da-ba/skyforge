# Hydrology reset tranche C — candidate longitudinal hydraulics

**Status:** dependent implementation candidate under issue #1084  
**Depends on:** Tranche B / PR #1087  
**Minecraft changes:** none  
**Terrain mutation:** none

## Purpose

Tranches A-B establish a terrain-aware route network with shared source/confluence/terminal geometry.
Tranche C adds a hydraulic candidate profile while preserving the reset rule that an infeasible river
must be detectable **before** terrain is changed.

This tranche computes:

- one shared water-surface datum per physical network node;
- a downstream-nonclimbing free-surface profile along every macro reach;
- discharge-scaled bankfull width and water depth;
- the centerline lowering that would be required to place the bed.

It does not apply that lowering.

## Terrain authority

Hydraulic candidate targets are evaluated against `SkyIslandPreHydrologicTerrainField`, not the
legacy AUTH-0016 hydrologically adjusted terrain. Thus required lowering measures what the new river
would need to do to the underlying authored terrain rather than measuring against terrain already
incised by the retired system.

## Hydraulic geometry

Skyforge uses the standard hydraulic-geometry family

```text
W = a * Q^b
D = c * Q^f
```

as a structural relationship, not as a claim that one empirical river calibration applies literally
to a floating Minecraft island.

The implementation therefore uses dimensionless normalized discharge and Skyforge-scale coefficients.
The first calibration uses width exponent `b = 0.50` and depth exponent `f = 0.32`, with explicit
minimum headwater dimensions. Coefficients remain project calibration parameters and must be judged
across the proving-ground/stress corpus rather than copied from one real watershed.

The important invariant is monotonicity: greater accepted discharge cannot produce a narrower or
shallower ordinary channel.

Reference basis: Leopold & Maddock (1953), USGS Professional Paper 252.

## Shared longitudinal datum

Every source/confluence/terminal already has one physical position from Tranche B. Tranche C assigns
that node exactly one candidate water-surface potential.

Nodes are processed in directed acyclic network order. A downstream node may follow its local terrain
target or be lowered enough to remain below every incoming upstream datum by a small positive
minimum grade.

This means a difficult route manifests as **required lowering**. The solver does not raise a barrier,
invent a levee, or ask Minecraft to rescue the profile.

## Interior profile

For each route sample, the preferred free surface sits below the unmodified local terrain by a small
depth-dependent freeboard. The solved value is clamped between:

- the maximum level permitted by the previous upstream sample; and
- the minimum level needed to reach the already-shared downstream datum without an uphill step.

The result follows terrain where possible while exactly preserving both shared endpoint datums.

The bed is then:

```text
bed = waterSurface - waterDepth
```

and diagnostic centerline lowering is:

```text
requiredLowering = max(0, originalTerrain - bed)
```

That value is evidence for the later geomorphic plausibility gate, **not permission to excavate**.

## Industry-practice correspondence

The reset deliberately mirrors two established ideas:

- hydraulic channel dimensions commonly scale with discharge through power-law hydraulic geometry;
- least-cost DEM breaching workflows measure modification cost and can leave paths unresolved when a
  configured cost/length limit is exceeded instead of forcing an arbitrarily deep breach.

Skyforge is using those principles as procedural-world constraints, not attempting a full
sediment-transport or open-channel CFD simulation.

## Acceptance

Tranche C must prove:

- deterministic hydraulic profiles;
- exact shared water-surface datum at every network junction;
- ordinary free surfaces never climb downstream;
- width and depth are monotone with discharge;
- all diagnostic lowering values are finite/non-negative;
- the planner can report substantial required lowering without changing the supplied terrain field.

## Still deferred

Tranche C does not yet decide whether a measured modification is acceptable.

The next qualification/cross-section layer must add hard limits for:

- centerline excavation;
- lateral bank/valley grade;
- depth relative to recoverable valley width;
- excavation volume;
- curvature relative to width;
- profile-specific maximum slope;
- ridge occupancy;
- required backend reconciliation.

Retained-water basin contours remain a separate later tranche.
