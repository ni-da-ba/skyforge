# Hydrology reset tranche D2 — hard geomorphic qualification

**Status:** policy mechanism implemented; calibrated default pending post-C1 reach corpus  
**Depends on:** post-C1 D1 diagnostics  
**Terrain mutation:** forbidden  
**Minecraft changes:** none

## Purpose

D2 turns objective D1 measurements into a hard pre-authoring decision.

A candidate channel may be topologically valid and hydraulically downhill yet still be physically
unsuitable because realizing it would require quarry-like excavation, excessive lateral recovery,
uncontained water, an implausibly deep/narrow section, an excessively tight bend, or unacceptable
ridge occupancy.

D2 makes those failures first-class. A rejection is never permission for a later layer to excavate
more aggressively.

## Violation taxonomy

- `CENTERLINE_LOWERING`
- `LATERAL_RECOVERY_GRADE`
- `BANK_CONTAINMENT`
- `DEPTH_TO_WIDTH`
- `RELIEF_TO_VALLEY_WIDTH`
- `EXCAVATION_BURDEN`
- `EXCAVATION_VOLUME`
- `CURVATURE_TO_WIDTH`
- `RIDGE_OCCUPANCY`
- `LONGITUDINAL_GRADE`

## Profile-aware envelopes

Alluvial, incised, and cascade profiles receive explicit envelopes.

A macro reach containing more than one profile kind receives the most restrictive bound for each
metric. A permissive cascade subsection therefore cannot launder an otherwise alluvial reach into a
looser acceptance class.

## Calibration rule

The code intentionally has **no production default envelope yet**.

The first default may be frozen only after the post-C1 D1 `reach-manifest.csv` is reviewed. The
chosen bounds must satisfy:

1. each limit corresponds to a named physical failure mode;
2. the unit/normalization is explicit;
3. reach-level/profile-level corpus distributions are recorded;
4. synthetic or controlled tests demonstrate valid and invalid sides;
5. bounds are not selected merely to make the current corpus pass.

Existing specimens are allowed to fail. In particular, a candidate with extreme excavation demand
must fail closed rather than force a rewrite of the threshold.

## Failure behavior

Rejected candidate:

```text
refine/reroute within semantic authority
    -> bounded shared-node adjustment
    -> semantically valid alternate fate/drop/hidden transfer
    -> fail closed
```

Forbidden:

```text
rejection -> increase excavation until it fits
```

## Determinism

D2 metrics, selected limits, violation sets, and accept/reject results are strict Skyforge-authored
deterministic state.

Minecraft-native decorative/ecological variation remains outside this contract unless it changes
authored or materially player-relevant geometry/state.
