# Hydrology reset tranche F0 — qualified continuous realization contract

**Status:** design-only staging under issue #1084  
**Depends on:** accepted D2 river qualification and E2 retained-basin qualification  
**Terrain mutation:** not implemented in F0  
**Minecraft changes:** none

## Purpose

F is the first reset layer that may eventually change terrain. F0 freezes the authority boundary
before that implementation exists.

The realization layer may consume only candidates that passed the upstream hard qualification
contracts. A rejected reach or basin contributes **no terrain delta**.

```text
semantic drainage
  -> continuous route/network
  -> hydraulic geometry
  -> D2 river qualification
  -> continuous retained basin
  -> E2 basin qualification
  -> F qualified continuous realization
  -> Minecraft discretization
```

## Output contract

F will expose a backend-neutral continuous field describing, at any island-local horizontal
position:

- original pre-hydrologic terrain elevation;
- accepted target terrain elevation;
- signed terrain delta;
- water-surface potential when the sample belongs to an accepted wet domain;
- wet-domain kind / provenance;
- local fluvial profile kind where applicable;
- whether the sample is channel bed, bankfull corridor, valley recovery, littoral margin, retained
  basin, or unaffected terrain;
- exact semantic provenance for any accepted river/basin transition.

The field must not contain Minecraft block IDs or voxel coordinates.

## River realization

For an accepted reach, the target terrain must be constructed from the solved C1 centerline and
hydraulic samples rather than from coarse watershed cell centers.

Cross-sections must:

- own a finite bankfull corridor;
- recover continuously to unchanged terrain over a bounded valley envelope;
- preserve the already-qualified lateral grade / relief-width / depth-width limits;
- avoid synthetic levee creation unless a later explicit landform semantics owns one;
- preserve exact shared confluence geometry;
- treat cascade/drop discontinuities explicitly rather than letting ordinary reaches climb.

The implementation must re-run the same D2 diagnostics against the **realized** field. Acceptance of
the pre-carving candidate is necessary but not sufficient if the actual cross-section transform
introduces a new violation.

## Retained-water realization

For an accepted basin:

- the open-water datum is the E0 continuous basin datum;
- shoreline follows the accepted continuous sublevel contour;
- littoral recovery must blend into existing terrain rather than flattening a coarse cell mask;
- bathymetry may shape the basin interior only inside an explicit bounded depth budget;
- river/lake transitions exist only at exact semantic junctions;
- no retaining bowl may be excavated merely to make an unqualified basin close.

Wetlands remain a separate saturated-margin/ecology realization and are not open-water basins.

## Composition rule

When accepted river, confluence, drop, and basin influences overlap, composition is semantic and
ordered, not repeated independent min/max carving.

The realization planner must resolve the local feature relationship first and emit one target
surface. The backend must never receive multiple contradictory terrain instructions and choose the
deepest cut.

## Failure behavior

If realization cannot satisfy the qualified envelope:

```text
refine bounded realization parameters
    -> requalify
    -> fail closed
```

Forbidden:

```text
qualification failure -> increase cut/fill budget
```

## Minecraft boundary

The later Minecraft adapter receives an already-qualified continuous terrain/water field.

Allowed backend correction:
- voxel quantization;
- one-block-scale connectivity/edge cleanup where mathematically equivalent.

Forbidden backend correction:
- rerouting;
- multi-block rescue excavation;
- synthetic levee manufacture;
- inventing basin geometry;
- changing hydraulic datum to make blocks fit.
