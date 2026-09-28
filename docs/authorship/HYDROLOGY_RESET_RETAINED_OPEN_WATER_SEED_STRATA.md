# Retained open-water seed-strata discovery

**Status:** deterministic candidate discovery under #1084  
**Authority:** none; no production threshold or terrain policy change  
**Minecraft changes:** none

## Question

The existing retained-basin calibration note records no POND or LAKE candidates in its fixed
proving-ground namespaces, including a broad key scan. Before deciding that the semantic classifier
must be redesigned, test whether the absence is specific to the one canonical world seed.

## Fixed experiment

The new evidence corpus keeps the two already-used identity namespaces (province/cluster 8/81 and
6/61) fixed and crosses them with the three established signed seed strata:

- seed-min = Long.MIN_VALUE;
- seed-zero = 0;
- seed-skyforge = the canonical Skyforge seed.

Within every seed/namespace stratum it scans island keys 1 through 128, for 768 deterministic
identities total. It records the exact identity, morphology, descriptor budgets, and counts of
existing POND, LAKE, and WETLAND classifications plus the exact retained-sink semantic predictors. For any POND/LAKE candidate it also records the
existing continuous E1 geometry diagnostics; a geometry failure is explicit and does not grant
authority.

## Interpretation boundary

This is a bounded discovery corpus, not a random sample or a prevalence estimate. It changes no
classifier weights/thresholds, does not promote any basin to terrain authority, and cannot authorize
Minecraft realization. A candidate can support a later fixed E1/E2 calibration corpus; no candidate
leaves the existing retained-open-water path fail-closed and makes a classifier/product decision
explicit rather than silently weakening policy.

The machine-readable output is hydrology-retained-basin-seed-strata-v1; semantics.csv records all retained-sink classifier inputs and outcomes. The
Hydrology Retained Basin Discovery workflow prints a compact summary and uploads the complete
manifests.
