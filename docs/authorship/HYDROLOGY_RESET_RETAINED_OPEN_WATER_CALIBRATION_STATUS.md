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

The follow-on E1 v4 corpus records marching-squares shoreline perimeter, the normalized isoperimetric
ratio P² / (4πA), explicit contour-loop/topology measurements, and the signed range of exact terminal
channel datum offsets from each basin water datum. These quantify sampled
planform compactness and closure; they are diagnostics only, not calibrated rejection thresholds.

The expanded v5 corpus crossed 11 seed strata with the same two namespaces and 1,024 keys
(22,528 identities). Exact-head Hydrology Retained Basin Discovery run #13 succeeded with 0 POND,
6 LAKE, 195 WETLAND candidates, and 0 geometry-measurement failures. All six lakes had one matched
terminal reach, one closed shoreline loop, and zero non-degree-two shoreline vertices. However, every
matched channel terminal was below its basin datum, by 11.06–11.59 times that lake's measured spill
headroom:

| Seed / namespace / key | Spill headroom | Signed channel offset | Absolute mismatch / headroom |
| --- | ---: | ---: | ---: |
| seed-skyforge / 8-81 / 609 | 4.9209 | -55.8019 | 11.3399 |
| u64-midpoint-01 / 6-61 / 35 | 2.5060 | -27.8673 | 11.1201 |
| u64-midpoint-01 / 6-61 / 200 | 0.4851 | -5.4458 | 11.2252 |
| u64-midpoint-04 / 6-61 / 624 | 6.8032 | -75.2277 | 11.0577 |
| u64-midpoint-05 / 8-81 / 500 | 7.4340 | -84.8912 | 11.4194 |
| u64-midpoint-07 / 6-61 / 772 | 6.5057 | -75.4288 | 11.5943 |

The broader scan therefore found more naturally occurring, geometrically closed lakes, but did not find a
near-level channel/basin junction. This is not evidence that a waterfall/outlet transition is impossible;
it is evidence that the current terminal-to-retained-sink association cannot be treated as an ordinary
level junction. No outlet/drop policy or E2 threshold is inferred, and all six remain unqualified for
production realization. The exact evidence is retained in Actions run #13 (artifact 11011155455,
SHA-256 `fb2eb29c46d69dfb01d525bf9a3cf838bef1a16f5ffd05cd037472d21dcd2408`).

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
