# Hydrology reset tranche D2 — hard geomorphic safety qualification

**Status:** candidate hard-safety contract under issue #1084  
**Depends on:** post-C1 D1 diagnostics  
**Terrain mutation:** forbidden  
**Minecraft changes:** none

## Purpose

D2 is the first post-reset layer allowed to say that a hydrologic candidate is **not physically
publishable**.

The contract is intentionally one-way:

```text
candidate -> diagnostics -> accept OR reject
```

A rejection never authorizes deeper excavation, steeper banks, synthetic levees, backend rescue, or
a return to the original coarse route.

## Safety-v1 envelope

The first envelope is a permissive **hard-safety** boundary, not a target natural-stream style:

| Metric | Maximum |
| --- | ---: |
| normalized centerline lowering | 0.20 |
| required incision / bankfull width | 1.00 |
| lateral recovery grade | 2.00 |
| bank containment deficit / water depth | 1.00 |
| water depth / bankfull width | 0.50 |
| normalized excavation burden | 0.10 |
| curvature * bankfull width (W/R approximation) | 1.00 |
| ridge-sample fraction | 0.50 |
| ordinary free-surface longitudinal grade | 1.00 |
| raw-terrain uphill step potential | 0.03 |

These bounds are deliberately broad. Passing D2 means only "not obviously pathological"; later
profile authoring may impose much narrower alluvial/incised/cascade targets.

## Curvature basis

USGS observations across varied natural rivers report the modal radius-of-curvature to channel-width
ratio `R/W` around 2-3. The D1 curvature metric is approximately the inverse, `W/R`. D2 uses
`W/R <= 1` as a permissive safety boundary: a bend may be much tighter than the typical observed
range and still pass, but its radius may not become smaller than its own channel width.

References:

- Leopold & Wolman (1960), *River meanders*.
- Williams (1986), *River meanders and channel size*, Journal of Hydrology 88.

## Interpretation of other bounds

The remaining limits are procedural safety guards rather than claimed universal fluvial constants:

- **incision/width** prevents a narrow route from demanding a deep quarry cut;
- **lateral recovery grade** prevents near-vertical recovery to untouched terrain;
- **containment/depth** prevents a candidate water surface from sitting multiple channel depths above
  its natural bank and requiring an invented levee;
- **depth/width** prevents slot-channel hydraulic geometry;
- **excavation burden** caps broad normalized terrain surgery;
- **ridge occupancy** is a coarse pressure signal, intentionally given a loose first limit;
- **longitudinal grade** treats slopes steeper than 1:1 as explicit drop/waterfall territory rather
  than ordinary continuous free surface;
- **raw uphill step** prevents the route solver from hiding a substantial terrain barrier inside
  later excavation.

## Expected corpus behavior

D2 is **not** calibrated to make the current fixed corpus pass.

The post-C1 D1 evidence already indicates that multiple current candidates violate curvature,
containment, incision, lateral recovery, and/or longitudinal safety. That is a successful result:
those candidates should return upstream for rerouting, hydraulic re-solving, explicit-drop
classification, or fail-closed handling before terrain authoring exists.

## Retry hierarchy

For a rejected candidate:

1. refine/reroute within semantic drainage authority;
2. move shared source/confluence/terminal geometry within its bounded region;
3. re-solve hydraulic datum/profile;
4. classify a semantically valid explicit drop, retained fate, or hidden transfer where appropriate;
5. fail closed.

Forbidden fallback:

```text
increase terrain modification until the candidate passes
```

## Determinism

D2 measurements and accept/reject results are strict Skyforge-authored deterministic state.
Downstream native decorative/ecological variation remains outside this contract unless it changes
authored hydromorphology or materially player-relevant structure.
