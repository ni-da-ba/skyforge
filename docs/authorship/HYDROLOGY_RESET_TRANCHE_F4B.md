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

Production F4B projection consumes one exact AUTH-0046 authored-realization association.

The association already proves that:

- the authored descriptor and realized volume are explicitly paired rather than inferred;
- nominal radius matches exactly;
- a schema-2 realized morphology family, when present, matches the authored morphology family.

F4B additionally requires the F4A candidate to belong to that same authored descriptor. No realized
volume may be selected from scale, morphology, spatial proximity, list position, or seed similarity.
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
- moving the physical volume in X/Z must not change the semantic hydrologic delta;
- compiled support/thickness is admitted per placed physical volume, because seeded detail is sampled in the horizontal world X/Z plane; F4B does not assume thickness is translation-invariant;
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

The accepted F4A 8/81/77 components remain the only positive semantic controls. F4B now binds
them to the established AUTH-0046 schema-2 association fixture family rather than projecting onto an
unassociated standalone volume.

The association fixture is a **candidate**, not a presumed success. The focused evidence run must
prove that every sampled positive control retains sufficient compiled column support. If any required
sample exhausts support, that exact association is inadmissible and F4B fails closed.

A deliberately weaker associated physical fixture remains a negative control and must reject when
the authorized cut exceeds local compiled thickness. This demonstrates that F4B preserves a
translation-neutral hydrologic delta while keeping physical-support admission realization-specific
and fail-closed. Runtime projection must retain the exact association and column-thickness checks.

The positive controls are:

- reach 709→559;
- reach 1742→1842.

Primary-287 and lake-609 remain zero-delta controls. Confluence-632 remains excluded until its
transition-owned downstream geometry is solved.

## First fixed-corpus result

The exact AUTH-0046 association fixture admits both F4A-positive 8/81/77 reaches without modifying
the authorized semantic cut or compiled underside.

Across both realized reaches:

- projected centerline samples: `59`;
- affected samples: `59`;
- minimum world-space terrain delta: `-4.480910369`;
- maximum world-space terrain delta: `-2.232739641`;
- minimum remaining compiled column thickness: `36.994664581`;
- maximum remaining compiled column thickness: `76.826056586`.

Per reach:

- 709→559: 35/35 affected; delta `-4.480910369 .. -3.635747789`; minimum remaining
  thickness `36.994664581`;
- 1742→1842: 24/24 affected; delta `-4.453474879 .. -2.232739641`; minimum remaining
  thickness `45.833595170`.

Primary-287, confluence-632, and lake-609 remain exact zero-projection controls because F4A grants
them no terrain candidate.

The conservative integer X/Z envelope around both realized reach cross-sections also passes physical
support admission:

- F4A/F4B-authorized integer columns: `8,969`;
- columns with non-zero continuous terrain cut: `5,858`;
- minimum integer-column sampled delta: `-6.941952488`;
- maximum integer-column sampled delta: `0.000000000`;
- minimum remaining compiled thickness over the full authorized integer envelope:
  `14.064577406` world units.

This envelope check is still F4B projection evidence rather than voxelization. It exists to prove
that the later voxelizer is not being handed physically unsupported bank or valley columns hidden
outside the centerline samples.

The minimum accepted support margin is therefore tens of world units, not a near-zero tolerance
artifact. The deliberately weaker associated fixture still proves the opposite case: insufficient
local support rejects rather than reducing the cut or invoking backend reconciliation.

## Next boundary

If F4B passes with positive remaining compiled thickness, the next tranche may define a deterministic
integer-surface quantizer and measure:

- absolute quantization error against `targetUpperWorld`;
- extra backend excavation beyond the continuous target;
- continuity along accepted channel centerlines;
- exact zero mutation outside F4A/F4B authority.

Any correction larger than ordinary voxel quantization is a reset failure, not a backend repair
budget.
