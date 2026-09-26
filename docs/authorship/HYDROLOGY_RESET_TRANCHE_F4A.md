# Hydrology reset tranche F4A — component-gated continuous terrain candidate

**Status:** quantitative terrain-candidate evidence under issue #1084  
**Depends on:** F3E complete terminal-component admission  
**Minecraft changes:** none  
**Production terrain authority:** none

## Purpose

F3E establishes the first complete drainage components that are mathematically admissible from source
through terminal fate. F4A asks the next narrower question:

> Can those already-qualified components be expressed as a backend-neutral continuous terrain delta
> without reintroducing historical hydraulic authority or widening D2 after carving?

F4A is still an evidence tranche. It does not authorize Minecraft discretization.

## Authority chain

F4A consumes:

- accepted C2 continuous centerlines and width/depth skeleton geometry;
- F3D solved ordinary-span water-surface and bed samples;
- F3E complete-component admission;
- the existing backend-neutral continuous fluvial cross-section response.

F4A does **not** consume the historical clipped hydraulic network's water-surface solution as reset
authority.

## Narrow supported realization class

An F3E-qualified terminal component is eligible for F4A only when every constituent reach:

- contains exactly one F3D ordinary span;
- that span is `SOLVED_QUALIFIED`;
- the span covers station 0→1 of the parent reach;
- contains no CASCADE transition ownership;
- touches no confluence requiring transition-specific terrain;
- terminates at an authoritative `EDGE_OUTLET`.

Any otherwise-qualified component outside this class is explicitly deferred for later
transition-specific terrain work.

## Reconstructed hydraulic geometry

For each supported reach, F4A reconstructs a `SkyIslandHydraulicReachGeometry` from:

- the accepted C2 geomorphic route and continuous centerline;
- F3D solved hydraulic samples;
- unchanged C2 discharge, bankfull width, and water-depth samples.

The reconstruction verifies sample-by-sample agreement with the C2 skeleton before terrain
evaluation.

## Terrain response

F4A reuses `SkyIslandQualifiedFluvialTerrainField` only as the continuous cross-section primitive.

That field:

- never raises terrain;
- carries semantic provenance;
- fails closed on overlapping unrelated reach envelopes;
- returns exactly zero delta away from admitted reaches.

F4A component admission controls which reaches may enter that field.

## Post-realization qualification

Every realized F4A reach is remeasured on the candidate terrain field and must re-pass the current D2
policy.

A post-realization D2 failure is an implementation error, not permission to widen thresholds or carve
harder.

## First intended evidence set

The fixed F3E corpus admits two complete ordinary EDGE_OUTLET components in 8/81/77:

- 709→559 / terminal 559;
- 1742→1842 / terminal 1842.

F4A must prove those exact components can produce deterministic continuous terrain candidates while:

- primary-287 remains zero-delta;
- lake-609 remains zero-delta;
- confluence-632 remains zero-delta until its transition-owned downstream geometry is solved;
- every realized reach re-passes D2.

## First fixed-corpus result

The first F4A evidence run realizes exactly the two F3E-qualified 8/81/77 components and excludes all
fixed negative/deferred controls.

### Reach 709→559 / terminal 559

- F3E disposition: `QUALIFIED`;
- F4A disposition: `REALIZED`;
- centerline samples: 35;
- affected centerline samples: 35;
- minimum terrain delta potential: `-0.032263256`;
- mean terrain delta potential: `-0.031626352`;
- maximum terrain delta potential: `-0.026177953`;
- post-realization D2: accepted.

### Reach 1742→1842 / terminal 1842

- F3E disposition: `QUALIFIED`;
- F4A disposition: `REALIZED`;
- centerline samples: 24;
- affected centerline samples: 24;
- minimum terrain delta potential: `-0.032065716`;
- mean terrain delta potential: `-0.029976568`;
- maximum terrain delta potential: `-0.016076075`;
- post-realization D2: accepted.

### Fail-closed controls

The fixed corpus grants no F4A terrain candidate to:

- primary-287: `PHYSICAL_REJECTION`;
- confluence-632: `TRANSITION_DEFERRED`;
- lake-609: `PHYSICAL_REJECTION` with retained-open-water terminal authority still absent;
- control-118: `TRANSITION_DEFERRED`;
- stress-512: `TRANSITION_DEFERRED`.

The realized deltas are continuous potential-space evidence. They are **not** voxel excavation
allowances. A later discretization tranche must measure its error against this field rather than
treating these potential differences as permission for backend reconciliation.

## Hard invariants

1. only F3E `QUALIFIED` components can enter the F4A candidate;
2. every F3E-qualified component is either realized or explicitly deferred;
3. realized reach identities equal terrain-field reach identities exactly;
4. realized reach identities equal post-realization qualification identities exactly;
5. F3D solved samples must preserve accepted C2 position/discharge/width/depth geometry;
6. no historical clipped hydraulic head solution is reset terrain authority;
7. every non-qualified F3E component contributes exactly zero F4A terrain delta;
8. F4A never raises terrain;
9. every realized reach re-passes D2 after terrain response;
10. F4A remains backend-neutral and grants no Minecraft authority.

## Next boundary

If fixed F4A candidates pass quantitative evidence, the next tranche should define voxelization-scale
discretization bounds against this already-qualified continuous field.

That backend boundary must permit only ordinary quantization error. It must not introduce multi-block
reconciliation excavation or rescue a continuous solution that failed F3E/F4A.
