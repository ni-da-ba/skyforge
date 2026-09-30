# Hydrology reset tranche F4H — refined-field voxel re-quantization

**Status:** fixed-association evidence under issue #1084  
**Depends on:** F4G shallower-only continuous refinement  
**Terrain mutation / water placement / Minecraft authority:** none

## Purpose

F4G can raise the accepted continuous terrain target where its world-head solve permits shallower
terrain. F4H re-quantizes that exact refined field against the same compiled physical association
used by the already-accepted F4C removal plan.

This is a comparison and evidence boundary, not a second realization authority.

## Hard invariants

For the fixed 8/81/77 association, F4H must establish:

- identical authorized integer X/Z columns to F4C;
- exact compiled solid support and removal-only ceiling quantization remain intact;
- every refined target maximum-solid Y is equal to or above its F4C value;
- no refined column removes more solid blocks than its F4C counterpart;
- aggregate removed blocks do not increase;
- each quantization residual remains nonnegative and below one block;
- the continuous refined terrain remains shallower-only and the complete realized reaches remain
  D2-qualified.

The deterministic reference corpus checks the summary and reach evidence on repeated runs. A
missing, added, or newly blocked column is a regression; it is not silently treated as partial success.

## Scope and interpretation

The fixed specimen remains association-specific. F4H does not repair the rejected key-287 route,
promote retained-basin candidates, define water voxels, or authorize Minecraft mutation. The ordinary
key-77 fixture is evidence of a limited technically qualified case only; it is not DR-70 product
acceptance or a substitute for the key-287 human gate.

## Validation

The Hydrology Reset Geometry workflow runs
`HydrologyRefinedVoxelQuantizationCorpusTest` whenever the F4H planner, corpus, test, workflow, or
this tranche contract changes. It prints the deterministic summary and per-reach report for review.

## Fixed 8/81/77 result

On exact head `ff105192a529b7133a24e6502a9cad362cf5b5d0`, the deterministic corpus reported:

| Measure | F4C baseline | F4H refined |
| --- | ---: | ---: |
| Authorized integer columns | 9,005 | 9,005 |
| Removed solid blocks | 15,997 | 15,987 |
| Columns quantized shallower | — | 10 |
| Columns quantized unchanged | — | 8,995 |
| Columns quantized deeper | — | 0 |
| Continuous columns shallower | — | 174 |
| Continuous columns unchanged | — | 8,831 |
| Continuous columns deeper | — | 0 |

Maximum/mean continuous recovery was 0.162492698 / 0.001480386 world units. The quantization
residual ranged from 0.000147845 to 0.999880972 world units. Both reaches re-passed D2
(`postD2Rejected=0`); one of two centerlines consumed the solved F4F correction, while the
bank-containment-infeasible reach remained unchanged.

Hydrology Reset Geometry passed on that exact head ([run 36789947793](https://github.com/ni-da-ba/skyforge/actions/runs/36789947793)); the repository build check also passed ([run 36789947710](https://github.com/ni-da-ba/skyforge/actions/runs/36789947710)). These results verify the limited F4H invariants only. They do not authorize voxel placement or a Minecraft/product review.
