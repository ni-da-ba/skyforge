# Hydrology reset tranche D2 — hard geomorphic qualification

**Status:** post-C2 evidence-backed envelope; pure-INCISED corpus re-evaluated after C3  
**Depends on:** D1 diagnostics, C2 curvature correction, and fixed pure-INCISED evidence  
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

A macro reach containing more than one profile kind receives the explicit `MIXED` aggregate
envelope. D1 diagnostics are reach-aggregate maxima, so those maxima cannot safely be attributed to
one constituent profile after the fact. Mixed reaches therefore do not inherit whichever constituent
class happens to be most permissive, and they are not synthesized by taking per-metric minima.

## Calibration rule

The first envelope was frozen after reviewing the D1 `reach-manifest.csv`, then its
curvature×width safety bound was tightened to 1.0 for every class after C2's semantic-corridor
geometry materially changed the curvature evidence.
The chosen bounds satisfy:

1. each limit corresponds to a named physical failure mode;
2. the unit/normalization is explicit;
3. reach-level/profile-level corpus distributions are recorded;
4. synthetic or controlled tests demonstrate valid and invalid sides;
5. bounds are not selected merely to make the current corpus pass.

Existing specimens are allowed to fail. In particular, a candidate with extreme excavation demand
must fail closed rather than force a rewrite of the threshold.

### First envelope

| class | lowering | lateral grade | containment | depth/width | relief/valley | excavation burden | curvature×width | ridge fraction | longitudinal grade |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Alluvial | 0.050 | 0.75 | 1.0 | 0.35 | 0.35 | 0.040 | 1.0 | 0.15 | 0.50 |
| Incised | 0.080 | 1.50 | 2.0 | 0.45 | 0.75 | 0.050 | 1.0 | 0.25 | 1.50 |
| Cascade | 0.050 | 2.00 | 2.0 | 0.35 | 1.00 | 0.050 | 1.0 | 0.40 | 3.50 |
| Mixed | 0.080 | 2.00 | 3.0 | 0.35 | 0.80 | 0.040 | 1.0 | 0.35 | 3.25 |

The fixed pure-INCISED corpus now includes keys 2084 and 2093 in namespace 8/81. Across their eight
pure-INCISED semantic reaches, seven remain inside the existing envelope. Key 2093 reach 708→559 is
rejected solely for `BANK_CONTAINMENT`: measured maximum containment deficit 2.685111576 world units
against the existing 2.0 limit. The envelope is intentionally retained unchanged rather than widened
to make that specimen pass.

Representative pure-INCISED maxima remain comfortably inside the other existing bounds: centerline
lowering ≤0.0311 potential, lateral recovery grade ≤0.6494, depth/width ≤0.2268,
relief/valley-width ≤0.3247, normalized excavation burden ≤0.0249, curvature×width ≤0.7965,
ridge fraction 0, and longitudinal grade ≤0.6469.

Absolute excavation volume remains evidence only. It scales with reach length and island scale, so a
single world-unit volume ceiling would be a false universal constraint; normalized excavation burden
is the hard gate in this tranche.

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
