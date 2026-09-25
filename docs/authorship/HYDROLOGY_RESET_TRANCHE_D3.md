# Hydrology reset tranche D3 — hydraulic discontinuity diagnostics

**Status:** diagnostic-only under issue #1084  
**Depends on:** D2 hard qualification  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

D2 correctly rejects some long mixed-profile macro reaches, including primary key 287, because one
continuous source-to-terminal hydraulic profile would require excessive terrain lowering.

D3 measures whether that pressure is associated with already-authored cascade/profile-transition
structure before any corrective retry is implemented.

For each semantic macro reach, D3 records:

- profile count;
- profile-transition count;
- cascade-profile count;
- source and terminal authored surface potential;
- net authored source-to-terminal drop;
- accumulated downhill segment drop;
- downhill drop carried by CASCADE segments;
- cascade share of downhill drop;
- maximum single-segment drop.

These diagnostics answer whether a rejected macro reach plausibly contains explicit drop structure
that should own hydraulic discontinuities rather than forcing ordinary continuous grade.

## Decision rule

D3 does **not** automatically split mixed reaches.

A later corrective tranche may introduce explicit hydraulic drop anchors only if the fixed evidence
shows that:

1. the rejected reach contains real authored cascade/drop structure;
2. that structure explains a material portion of the longitudinal pressure;
3. the correction preserves semantic drainage topology;
4. the resulting ordinary subreaches independently re-pass D2.

Otherwise the retry must target route/node geometry instead.

## Critical invariant

D3 changes no route, profile, hydraulic datum, qualification result, terrain, or backend behavior.
