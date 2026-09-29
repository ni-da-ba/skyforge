# Retained open-water seed-strata discovery

**Status:** deterministic candidate discovery under #1084  
**Authority:** none; no production threshold or terrain policy change  
**Minecraft changes:** none

## Question

The existing proving-ground scan found one open-water lake in the 6,144-identity v4 corpus, and that
lake's channel-to-basin datum offset is materially adverse. Expand deterministic seed coverage before
concluding that the current retained-water classifier lacks naturally occurring calibration specimens.

## Fixed v5 experiment

The corpus keeps the two existing identity namespaces (province/cluster 8/81 and 6/61) and island keys
1 through 1024 fixed. It crosses those 2,048 identity positions with eleven exact world-seed strata:

- three existing anchors: `Long.MIN_VALUE`, zero, and the canonical Skyforge seed;
- eight evenly spaced unsigned 64-bit midpoint values:
  `0x1000000000000000`, `0x3000000000000000`, `0x5000000000000000`,
  `0x7000000000000000`, `0x9000000000000000`, `0xB000000000000000`,
  `0xD000000000000000`, and `0xF000000000000000`.

This yields 22,528 exact deterministic identities. It is a purposive candidate-discovery grid, not a random
sample and not a prevalence estimate. It asks whether naturally occurring POND/LAKE specimens with
usable continuous geometry exist across a wider seed range; it does not select a classifier policy.

For every identity the corpus records morphology, descriptor budgets, counts of existing POND, LAKE,
and WETLAND classifications, and exact retained-sink semantic predictors. For each POND/LAKE candidate
it records the existing continuous E1 geometry diagnostics, including shoreline topology and signed
channel-datum offsets. A geometry failure is explicit and never grants authority.

## v5 exact-head result

Hydrology Retained Basin Discovery run #13 passed for the 22,528-identity v5 grid: 0 POND, 6 LAKE,
195 WETLAND candidates, and no continuous-geometry failures. All six LAKE candidates had one matched
terminal reach, a closed contour, and a channel water surface below the basin datum by 11.06–11.59×
measured spill headroom. The six exact identities and measurements are recorded in
`HYDROLOGY_RESET_RETAINED_OPEN_WATER_CALIBRATION_STATUS.md`. No candidate is promoted to E2 or
Minecraft authority.

## Interpretation boundary

The scan changes no classifier weights or thresholds and does not promote any basin to terrain authority.
A newly found candidate can support a later E1/E2 qualification corpus. Until that qualification is
accepted, all retained-open-water production and Minecraft realization remain fail-closed.

Machine-readable output is `hydrology-retained-basin-seed-strata-v5`; the workflow prints a compact
summary and uploads the complete manifests.
