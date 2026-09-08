# Wave C26 — local AUTH-0098 petroleum source admission

**Status:** IN PROGRESS  
**Issue:** #381  
**Predecessor:** C25 / PR #377  
**Authorship input:** AUTH-0098  
**Parent vertical slice:** #224

## Purpose

C25 suppresses Create: Diesel Generators' independent chunk-oil geography and hands literal
petroleum-source realization to Implementation.

C26 closes one remaining semantic ambiguity before that adapter is built:

> An island being petroleum-eligible does not authorize a literal source anywhere on the island.

Literal-source realization must remain subordinate to the **local** accepted AUTH-0098 system
opportunity.

## Local admission rule

For every exact AUTH-0098 cell:

```text
systemOpportunity == 0
    -> INELIGIBLE

systemOpportunity > 0
    -> ELIGIBLE FOR DOWNSTREAM REALIZATION CONSIDERATION
```

No threshold above zero is introduced.

The C26 plan exposes:

- every eligible AUTH-0098 cell in exact source order/provenance;
- deterministic x/z source columns containing at least one eligible cell, ordered by the first
  canonical eligible-cell occurrence.

This column view is useful to the C25 pumpjack bridge because the retained pumpjack is vertically
source-oriented, but it is still semantic planning evidence rather than block-space well geometry.

## Admission is not selection

C26 does not say:

- every eligible cell contains literal petroleum;
- every eligible column gets a well;
- the highest opportunity cell is selected;
- opportunity magnitude means reserves, pressure, saturation, grade, thickness, volume, extraction
  rate, depletion, or rarity.

Implementation chooses concrete source geometry only from locally admissible support and remains
responsible for exact physical placement.

## Relationship to C22-C25

```text
AUTH-0098
    local source/reservoir/seal opportunity
        |
AUTH-0100
    regional exact inventory
        |
C22
    STRATEGIC_NODE / R3 policy
        |
C23
    province feasibility
        |
C24
    coarse surface infrastructure candidates
        |
C25
    suppress competing Diesel Generators native oil
        |
C26
    restore local AUTH-0098 admission for literal source realization
        |
Implementation
    concrete source/depletion/pumpjack bridge
```

## Authorship consequence

AUTH-0098 already contains the local evidence needed by this consumer.

C26 therefore creates **no new Authorship request**. Do not add AUTH-0101 merely to wrap, rank, or
restate the same cells.

A future Authorship milestone is justified only if a concrete downstream requirement demonstrates a
backend-neutral world cause that AUTH-0098/AUTH-0096/AUTH-0097 do not expose.

## Implementation handoff

The C25 adapter should preserve:

- exact C23 petroleum-province/island eligibility;
- exact C26 local nonzero source admission;
- C24 extraction-interface intent;
- exact Skyforge volume ownership.

Implementation owns:

- which eligible cells/columns become literal sources;
- deposit geometry/count/thickness/volume;
- pressure/depletion/extraction model;
- exact vertical/offset pumpjack bridge;
- block/tag/fluid integration;
- persistence and lifecycle.

## Acceptance

1. local eligibility is exactly AUTH-0098 `systemOpportunity() > 0`;
2. eligible cell order/provenance is unchanged;
3. eligible x/z columns derive only from nonzero cells and preserve first canonical occurrence order;
4. zero-opportunity cells cannot enter the admissible views;
5. no selected cell/column or magnitude-derived physical quantity is exposed;
6. public planner accepts only an exact AUTH-0098 profile;
7. targeted tests and repository CI pass;
8. state records the Implementation local-source realization handoff and no new Authorship producer.

No Minecraft manual or visual gate applies to C26.
