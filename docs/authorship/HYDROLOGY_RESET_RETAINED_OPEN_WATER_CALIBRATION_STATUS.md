# Retained open-water calibration status

**Status:** fail-closed evidence note under issue #1084  
**Production policy change:** none  
**Terrain authority:** none  
**Minecraft changes:** none

## Result

Retained open-water production qualification remains uncalibrated.

The earlier E1/E2 discovery sweep inspected namespace 8/81 keys 1 through 2048 and found no POND
candidate. A subsequent temporary discovery sweep inspected an additional 10,240 islands:

- namespace 8/81 keys 2049 through 8192;
- namespace 6/61 keys 1 through 4096.

That expanded sweep found:

- 0 POND candidates;
- 0 LAKE candidates;
- multiple WETLAND candidates near the residual POND decision region;
- 12 pure-INCISED semantic macro reaches, which are being promoted separately into fixed D2 evidence.

The temporary scanner was closed unmerged after recording this result.

A later deterministic seed-stratified v2 discovery under #1084 covered 6,144 exact identities
(three seeds × two fixed namespaces × keys 1–1,024) and found 0 POND, 1 LAKE, and 48 WETLAND
candidates, with no geometry-measurement failures. Its sole lake is the seed-skyforge, namespace
8/81, island-key 609 candidate. E1 diagnostics for that lake are:

- continuous wet area: 15,037.629 world²; equivalent diameter: 138.371 world units;
- maximum depth: 54.715 world units; depth/diameter: 0.3954;
- maximum shoreline grade: 1.8579; spill headroom: 4.9209 world units;
- exact terminal reaches: 1; maximum channel/water datum mismatch: 55.8019 world units;
- bounded-search escape: false; shoreline crossings: 368.

This is a measured candidate, not a qualified basin. In particular, the large datum mismatch is
explicit adverse evidence for the lake-609 channel transition; the discovery does not calibrate
an acceptable mismatch limit or establish retained-water production authority.

The follow-on E1 v3 corpus adds marching-squares shoreline perimeter, the normalized isoperimetric
ratio P² / (4πA), and explicit contour-loop/topology measurements. These quantify sampled
planform compactness and closure; they are diagnostics only, not calibrated rejection thresholds.

## Interpretation

This is evidence about corpus support, not evidence that POND or LAKE can never occur.

It does establish that the current sampled namespaces do not provide a defensible empirical basis for
freezing POND/LAKE E2 production limits. The classifier definition alone is not calibration evidence.

Therefore:

1. POND/LAKE E2 production authority remains fail-closed.
2. No E2 threshold may be widened merely to make the existing lake-609 transition realizable.
3. F3/F3A retained-open-water topology and interface geometry remain valid as evidence/constraint
   objects, but do not authorize basin terrain.
4. A future retained-open-water calibration tranche requires either naturally occurring fixed
   specimens from a justified broader sampling strategy or an explicit redesign of the semantic
   classifier with independent design justification and renewed evidence.
5. Minecraft hydrology remains unchanged.

## Consequence for transition work

Confluence and authored CASCADE transition solving may proceed independently.

Retained-open-water coupling may continue to expose shoreline and datum mismatch diagnostics, but a
solver result against an unqualified basin must not be converted into production terrain authority.
