# AUTH-0094 — Regional base-metal opportunity inventory

## Purpose

CONTENT C20 consumes AUTH-0093 Iron/Copper/Zinc geological opportunity and fixes the gameplay
availability policy:

- Iron — COMMON_REGIONAL, first-flight critical, STARTING_CLUSTER guarantee;
- Copper — COMMON_REGIONAL, post-flight, POST_FLIGHT_PROVINCE guarantee;
- Zinc — COMMON_REGIONAL, post-flight, POST_FLIGHT_PROVINCE guarantee.

C20 also requires hard guarantees to be satisfied by selecting or re-planning authored scopes that
contain eligible geology rather than injecting a resource into a zero-opportunity island.

AUTH-0093 is island-local. C20 therefore needs one exact regional inventory that can answer which
published authored islands carry accepted AUTH-0093 evidence while preserving the same explicit
publication/authorship provenance already accepted by AUTH-0087.

AUTH-0094 closes only that gap.

## Accepted source boundary

The public profiler accepts exactly one:

```text
SkyIslandPublishedAuthoredRealizationBinding
```

For every canonical AUTH-0046 association carried by that binding, AUTH-0094 computes one exact
AUTH-0093 island profile from the associated authored descriptor.

Callers cannot provide:

- arbitrary island subsets;
- custom AUTH-0093 profiles;
- alternative publication order;
- availability classes;
- guarantee scope;
- quantity thresholds;
- Minecraft resource identities.

## Exact regional inventory

Each regional entry retains:

```text
AUTH-0046 association
+ exact associated authored descriptor
+ exact AUTH-0093 island profile
```

The regional profile requires complete coverage of the AUTH-0087 association catalog in canonical
association order.

Missing, extra, reordered, or descriptor-substituted entries fail closed.

## Geological eligibility

AUTH-0094 exposes the same raw geological zero/nonzero boundary already consumed by C20:

```text
peak AUTH-0093 opportunity == 0
    -> no accepted geological opportunity

peak AUTH-0093 opportunity > 0
    -> accepted geological opportunity exists
```

AUTH-0094 does not introduce a new magnitude threshold.

This is geological evidence, not a guarantee decision.

A region with zero eligible islands for a C20-required metal tells the downstream planner that the
current authored scope cannot satisfy that guarantee without re-planning or another guaranteed
access path. AUTH-0094 itself does not perform that re-plan.

## Candidate ordering

AUTH-0094 may expose eligible entries ranked by unchanged AUTH-0093 mean opportunity.

Ordering is:

1. descending unchanged AUTH-0093 mean opportunity;
2. existing canonical AUTH-0087 association order for exact ties.

Java's stable sort is used over the canonical eligible list, so no new key or random source is
introduced for ties.

This ranked view is descriptive geological evidence only.

It is not:

- a selected mine site;
- a deposit placement decision;
- resource availability classification;
- grade/reserves/volume;
- accessibility;
- route cost;
- a Bootstrap guarantee result.

CONTENT C20 remains the owner of availability and guarantee semantics.

## Regional summaries

The first regional inventory exposes only conservative descriptive summaries:

- island count;
- eligible-island count by Iron/Copper/Zinc;
- canonical eligible-island list;
- eligible list ranked by unchanged mean opportunity;
- strongest mean opportunity by element when any eligible island exists.

No area weighting is added because AUTH-0093 normalized geological opportunity is not accepted as a
physical ore-volume density.

No sum of opportunity is interpreted as regional reserves.

## Ownership boundaries

Authorship owns:

- exact AUTH-0087/AUTH-0046 provenance;
- exact AUTH-0093 per-island geological opportunity;
- raw regional inventory and descriptive ordering.

Content owns:

- COMMON_REGIONAL / other availability classes;
- STARTING_CLUSTER / POST_FLIGHT_PROVINCE guarantees;
- trade/salvage alternatives;
- progression criticality;
- resource-site selection policy.

Implementation owns:

- Minecraft/mod ore identity;
- deposit geometry;
- counts, volume, grade, accessibility;
- placement/worldgen;
- persistence and lifecycle.

## Acceptance gate

Reject AUTH-0094 if:

- the public profiler accepts anything other than one exact AUTH-0087 binding;
- a regional entry is not generated from the exact associated authored descriptor;
- missing/reordered/substituted association coverage is accepted;
- geological eligibility uses any threshold other than the existing AUTH-0093 zero/nonzero boundary;
- ranking modifies AUTH-0093 mean opportunity;
- exact ranking ties invent a new tie-break key or random source;
- regional opportunity is summed or relabeled as reserves/deposit volume;
- C20 availability/guarantee semantics move into Authorship;
- Minecraft ore/block/registry/placement/lifecycle behavior enters the world contract.

## Verification target

Machine proof should demonstrate:

- exact AUTH-0087 association coverage/order;
- exact AUTH-0093 descriptor provenance;
- deterministic repeated profiling;
- exact eligibility counts from the AUTH-0093 zero/nonzero boundary;
- descending unchanged mean-opportunity ranking;
- missing/reordered/substituted entries fail closed;
- only the exact published binding is accepted by the public profiler.

This milestone is architecture/provenance evidence. It does not require human visual review.

## Next boundary

After AUTH-0094, C20 has enough Authorship evidence to select or re-plan a regional scope around
eligible Iron/Copper/Zinc geology.

Do not open AUTH-0095 merely to add deposit quantity. Deposit scale, grade, accessibility, and
Minecraft realization remain unaccepted and must be justified by a concrete downstream resource
realization requirement.
