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


## Fixed-corpus result after C2

The clean C2-based diagnostic corpus materially supports explicit drop ownership for the primary
key-287 mixed reach:

```text
profiles                    = 33
profile transitions         = 6
cascade profiles            = 10
net authored drop           = 0.800350105
accumulated downhill drop   = 1.648662414
cascade-owned downhill drop = 1.382973349
cascade share               = 0.838845683
maximum single drop         = 0.224431487
```

Thus approximately 83.88% of accumulated downhill drop is already associated with authored CASCADE
segments. This is materially different from a mixed reach whose pressure is distributed across
ordinary profiles.

The evidence authorizes the **next mathematical experiment**, not terrain mutation: partition the
hydraulic boundary-value problem at authored cascade/drop transitions, give those transitions
explicit discontinuity ownership, and require every intervening ordinary subreach to independently
satisfy the bounded longitudinal solve and D2. If those ordinary subreaches remain infeasible, the
remaining failure belongs to route/node refinement or fail-closed rejection; the drop classification
must not be used to excuse unrelated excavation, lateral-recovery, containment, or ridge failures.

The control corpus also shows that cascade share alone is not an acceptance criterion. Some short
control reaches carry roughly 61% of downhill drop in one cascade segment, while non-cascade controls
carry zero. D3 therefore remains diagnostic evidence tied to semantic profile structure rather than a
global numeric threshold.
