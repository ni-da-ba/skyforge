# AUTH-0100 — Regional petroleum-system opportunity inventory

## Purpose

CONTENT C22 / issue #357 is the first concrete downstream policy consumer of accepted AUTH-0098
petroleum-system geological opportunity.

C22 fixes petroleum as:

- R3 / mature-industry;
- STRATEGIC_NODE availability;
- not first-flight critical;
- intentionally absent from many ordinary provinces;
- valuable through concentration, refining, fuel logistics, and freight;
- eligible only where accepted AUTH-0098 petroleum-system opportunity is nonzero.

C22 also explicitly requests a canonical regional AUTH-0098 inventory so a later petroleum-bearing
province planner can inspect exact eligible-island coverage and deterministic candidate ranking before
Content selects or replans a strategic-node province.

AUTH-0100 closes only that backend-neutral evidence gap.

## Accepted source boundary

The public profiler accepts exactly one:

```text
SkyIslandPublishedAuthoredRealizationBinding
```

For every canonical AUTH-0046 association carried by that AUTH-0087 binding, AUTH-0100 computes one
exact AUTH-0098 petroleum-system opportunity profile from the associated authored descriptor.

Callers cannot provide:

- arbitrary island subsets;
- custom AUTH-0098 profiles;
- alternative publication order;
- geological eligibility thresholds;
- STRATEGIC_NODE frequency;
- province guarantees;
- trade/salvage policy;
- deposit quantities;
- Minecraft/mod petroleum identities.

## Exact regional inventory

Each regional entry retains:

```text
AUTH-0046 association
+ exact associated authored descriptor
+ exact AUTH-0098 island profile
```

The regional profile requires complete coverage of the AUTH-0087 association catalog in canonical
association order.

Missing, extra, reordered, or descriptor-substituted entries fail closed.

## Geological eligibility

AUTH-0100 exposes exactly the C22-requested raw AUTH-0098 zero/nonzero boundary:

```text
peakSystemOpportunity == 0
    -> no accepted petroleum-system opportunity

peakSystemOpportunity > 0
    -> accepted petroleum-system opportunity exists
```

AUTH-0100 introduces no new magnitude threshold.

This is geological eligibility evidence only. It does not make an island a strategic node and does
not guarantee petroleum in an ordinary province.

A published region with zero eligible islands tells a downstream Content planner that this exact
authored scope cannot host a petroleum strategic node without replanning or another Content-owned
access policy. Authorship does not perform that replan.

## Candidate ordering

AUTH-0100 exposes eligible entries ranked by unchanged AUTH-0098 mean system opportunity.

Ordering is:

1. descending unchanged `meanSystemOpportunity()`;
2. existing canonical AUTH-0087 association order for exact ties.

Java's stable sort preserves canonical order for exact ties. No new key, threshold, or random source
is introduced.

This ranked view is descriptive geological evidence only. It is not:

- a selected petroleum strategic node;
- a province-selection decision;
- a recoverable deposit;
- reserves, grade, pressure, thickness, or volume;
- extraction accessibility;
- refinery or pumpjack placement;
- route cost;
- a progression guarantee.

## Regional summaries

AUTH-0100 exposes only conservative descriptive summaries:

- island count;
- eligible-island count;
- canonical eligible-island list;
- eligible list ranked by unchanged AUTH-0098 mean system opportunity;
- strongest mean system opportunity when an eligible island exists.

No area weighting is added because AUTH-0098 opportunity is not accepted as physical petroleum-volume
density.

Opportunity is not summed or relabeled as regional reserves or industrial supply.

## Ownership boundaries

### Authorship

Owns:

- exact AUTH-0087 / AUTH-0046 provenance;
- exact AUTH-0098 per-island petroleum-system opportunity;
- raw regional eligible coverage;
- deterministic descriptive ordering.

### Content / Experience

C22 and later Content work own:

- R3 / STRATEGIC_NODE availability;
- strategic-node frequency;
- whether a petroleum-bearing province is intentionally requested;
- province selection/replanning;
- no ordinary-province hard guarantee;
- progression timing;
- bounded trade/salvage introduction or emergency access;
- refinery/fuel/logistics role and route pressure.

### Implementation

Owns:

- concrete petroleum block/fluid/resource identity;
- deposit geometry/count/thickness/volume;
- pressure, depletion, extraction, accessibility;
- pumpjack/refinery integration;
- exact worldgen/placement;
- persistence and lifecycle.

## Acceptance gate

Reject AUTH-0100 if:

- the public profiler accepts anything other than one exact AUTH-0087 binding;
- a regional entry is not generated from the exact associated authored descriptor;
- missing/reordered/substituted association coverage is accepted;
- geological eligibility uses any threshold other than AUTH-0098 `peakSystemOpportunity() > 0`;
- ranking modifies AUTH-0098 mean system opportunity;
- exact ranking ties invent a new key or random source;
- opportunity is summed or relabeled as reserves, quantity, or industrial supply;
- C22 STRATEGIC_NODE frequency, guarantee, trade/salvage, progression, or selection policy enters Authorship;
- Minecraft/mod petroleum identity, deposit placement, extraction, or lifecycle enters the world contract.

## Verification target

Machine proof demonstrates:

- exact AUTH-0087 association coverage/order;
- exact AUTH-0098 descriptor provenance;
- deterministic repeated profiling;
- exact eligibility count from the AUTH-0098 zero/nonzero boundary;
- descending unchanged mean-system-opportunity ranking;
- missing/reordered/substituted entries fail closed;
- only the exact published binding is accepted by the public profiler;
- no Content or Implementation petroleum policy appears in the Authorship API.

Evidence identity:

```text
authorship-regional-petroleum-system-opportunity-v1
```

Files:

- `index.html`;
- `atlas.png`;
- `manifest.csv`;
- `inventory.csv`;
- `ranking.csv`.

This milestone requires no Minecraft manual run or human visual gate.

## Next boundary

AUTH-0100 gives C22 the regional geological inventory it explicitly requested.

Do not add deposit quantity, a literal oilfield, regional reserves, strategic-node frequency, province
guarantees, or Minecraft realization in Authorship. The next petroleum work should occur in Content
policy or Implementation realization unless a later executable consumer demonstrates another missing
backend-neutral world cause.
