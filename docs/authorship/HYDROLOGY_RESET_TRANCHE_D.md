# Hydrology reset tranche D — hard geomorphic qualification

**Status:** dependent implementation candidate under issue #1084  
**Depends on:** Tranche C  
**Terrain mutation:** forbidden  
**Minecraft changes:** none

## Purpose

This tranche introduces the gate the retired H6 line lacked: candidate hydrology can now be
**mathematically rejected before terrain authoring**.

A route being connected, deterministic, and hydraulically downhill is necessary but not sufficient.
It must also be compatible with the terrain at a scale that will not produce quarry walls, slot
canyons, extreme rescue excavation, or implausibly tight bends.

## Measured quantities

For each hydraulic macro reach the qualifier records:

- maximum required centerline lowering in normalized potential;
- the same lowering scaled by the island relief budget into authored world units;
- maximum lateral recovery grade from the proposed bed to unmodified valley-side terrain;
- normalized excavation burden;
- an integrated excavation-volume proxy;
- maximum local curvature multiplied by bankfull width;
- pre-carving ridge-occupancy fraction;
- maximum longitudinal water-surface grade in relief-scaled world units.

## Explicit rejection reasons

A rejected reach carries one or more first-class reasons:

- `CENTERLINE_LOWERING`
- `LATERAL_RECOVERY_GRADE`
- `EXCAVATION_BURDEN`
- `CURVATURE_TO_WIDTH`
- `RIDGE_OCCUPANCY`
- `LONGITUDINAL_GRADE`

Nothing downstream is allowed to reinterpret a rejection as permission to carve more aggressively.

## Calibration policy

The initial policy is deliberately **provisional**. Thresholds are centralized in
`SkyIslandGeomorphicQualificationPolicy`; they are not universal claims about natural rivers.

Profile classes receive different envelopes because an alluvial reach, an incised reach, and a
cascade are supposed to express different terrain relationships. The proving-ground/corpus evidence
will determine whether the initial values are too strict or too permissive before this layer becomes
production authoring authority.

The important architectural decision is already final: these quantities are hard qualification
inputs rather than post-hoc visual diagnostics.

## Determinism scope

All quantities and accept/reject outcomes in this layer are strict Skyforge-authored deterministic
state.

Later Minecraft/mod-native fleshing does not need byte-identical decorative population when that
variation leaves authored hydromorphology, persistent ownership, hydraulic connectivity,
traversability, and materially player-visible structure unchanged. Native population digest
variation therefore does not weaken this layer's determinism contract.

## Development behavior

The expected later retry hierarchy remains:

1. route/refine within semantic authority;
2. move bounded shared node geometry where allowed;
3. choose an authored alternate fate such as retained water, explicit drop, or hidden transfer when
   semantically justified;
4. reject/fail closed.

The forbidden fallback remains: **increase excavation until the original route fits.**

## Acceptance for this tranche

Tests must prove:

- deterministic metrics and decisions on the fixed corpus;
- all metrics remain finite;
- a gentle synthetic alluvial reach can pass;
- a quarry-like synthetic reach is rejected for the expected objective reasons;
- qualification performs no terrain mutation.

This tranche still does not author the final cross-section or modify the world. It establishes the
mathematical right to say "no" before the terrain-realization layer exists.
