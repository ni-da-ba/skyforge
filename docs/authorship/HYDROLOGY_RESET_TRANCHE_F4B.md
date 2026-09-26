# Hydrology reset tranche F4B — compiled world-surface projection

**Status:** backend-neutral world-space projection evidence under issue #1084  
**Depends on:** F4A component-gated continuous terrain candidate  
**Voxel/Minecraft authority:** none

## Purpose

F4A produces an accepted hydrologic terrain **delta** in normalized semantic potential space. Minecraft,
however, materializes a compiled world-space island surface.

F4B defines the authority-preserving bridge between those domains without replacing the compiled
surface:

```text
deltaWorld = F4A.deltaPotential * authoredDescriptor.reliefBudget
targetUpperWorld = compiledUpperWorld + deltaWorld
```

The compiled upper surface remains authoritative for absolute elevation, morphology, local detail,
and placement. F4B contributes only the already-qualified hydrologic delta.

## Compatibility requirements

A semantic descriptor and compiled physical volume may be projected together only when:

- nominal radius matches;
- a schema-2 physical morphology family, when present, matches the authored morphology family;
- the F4A candidate belongs to the same authored semantic descriptor.

No implicit rescaling or morphology remapping is permitted.

## World-space sampling

For a world-space X/Z sample:

1. subtract the compiled volume center to obtain island-local X/Z;
2. sample the F4A qualified terrain field at that local position;
3. sample the compiled upper and underside surfaces at world X/Z;
4. convert only the F4A delta to world units using `reliefBudget`;
5. add that delta to the compiled upper surface.

The underside is not modified.

## Hard projection constraints

Every projected sample must satisfy:

- `deltaWorld <= 0`;
- `targetUpperWorld = originalUpperWorld + deltaWorld`;
- unaffected F4A samples project exactly zero world-space delta;
- `targetUpperWorld > compiledUndersideWorld`;
- a compiled column too thin for the already-qualified cut fails closed rather than reducing the cut, moving the underside, or selecting a different datum;
- moving the same compiled shape in X/Z must not change the hydrologic delta or column thickness;
- rejected/deferred F3E/F4A components continue to project zero hydrologic delta.

A projection that would collapse the compiled column fails closed. It does not deepen excavation or
move the underside.

## Why F4B is not voxelization

F4B remains a continuous world-space surface.

It defines no integer Y, no block occupancy, no material replacement, no water block placement, and
no reconciliation pass.

The next backend-facing tranche may compare integer voxelization against F4B, but only after F4B
quantifies the continuous world-space cut and preserved column thickness.

## First fixed evidence target

The accepted F4A 8/81/77 components remain the only positive controls. The fixed projection corpus
uses the same deterministic schema-2 projection-fixture family as the positive unit evidence; this
fixture is evidence for the projection contract, not a claim that an arbitrary compatible physical
seed must admit the cut. Runtime projection remains volume-specific and must retain the column
thickness check.

The positive controls are:

- reach 709→559;
- reach 1742→1842.

Primary-287 and lake-609 remain zero-delta controls. Confluence-632 remains excluded until its
transition-owned downstream geometry is solved.

## Next boundary

If F4B passes with positive remaining compiled thickness, the next tranche may define a deterministic
integer-surface quantizer and measure:

- absolute quantization error against `targetUpperWorld`;
- extra backend excavation beyond the continuous target;
- continuity along accepted channel centerlines;
- exact zero mutation outside F4A/F4B authority.

Any correction larger than ordinary voxel quantization is a reset failure, not a backend repair
budget.
