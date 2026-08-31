# ADR-0031: Hierarchical Archipelago Composition

- **Status:** Proposed; focused planner proof pending local validation
- **Date:** 2026-08-30
- **Work item:** SF-IMP-0027

## Context

SF-IMP-0026 accepts provider-neutral multi-island chain and cluster planning plus independent group realization. Its visual evidence proves that groups preserve island morphology, spacing, topology, elevation variation, and deterministic identity at scene scale.

The accepted group corpus also exposes the next limitation: a single chain reads as an intentional formation, while a single cluster remains closer to an organic island field than a mature archipelago. Skyforge needs a higher spatial hierarchy capable of expressing dominant formations, supporting groups, satellites, outliers, and deliberate corridors of empty sky.

The higher layer must not flatten child islands into one global member list or duplicate the accepted group planner. It should arrange complete group templates and preserve their independent identity.

## Decision

SF-IMP-0027 introduces an archipelago planning layer above `SkyIslandGroupPlan`.

The hierarchy is:

```text
archipelago
  -> child group placement / role / reservation
      -> accepted chain or cluster group plan
          -> independently seeded island members
```

An archipelago request supplies:

- one root seed;
- one world-space archipelago anchor and base suspension elevation;
- one minimum inter-group envelope gap;
- an ordered list of reusable child-group templates;
- one high-level archipelago layout.

Each child-group template supplies:

- a stable semantic identifier;
- one semantic role (`ANCHOR`, `SECONDARY`, `SATELLITE`, `OUTLIER`);
- the accepted group-level member template, morphology list, member reservation, member gap, member elevation jitter, and chain/cluster layout;
- one explicit higher-level `reservedGroupRadius`.

The archipelago planner derives a distinct child-group root seed, instantiates the accepted group planner at the chosen world-space anchor/orientation/elevation, validates that every member reservation fits inside the child group's declared higher-level envelope, and preserves the resulting `SkyIslandGroupPlan` as an independent child object.

## Explicit nested reservations

SF-IMP-0027 extends the reservation principle from SF-IMP-0026 rather than inferring spatial extent from member count or descriptor radius.

For every pair of child groups A and B, the archipelago planner requires:

```text
centerDistance(A, B) >= reservedGroupRadius(A) + reservedGroupRadius(B) + minimumGroupGap
```

After a child group is planned, every child member must also satisfy:

```text
distance(memberCenter, groupCenter) + memberReservedRadius <= reservedGroupRadius
```

A bad higher-level reservation therefore fails deterministically instead of allowing child geometry to leak across archipelago spacing assumptions.

## High-level layouts

### Arc

`Arc` is an ordered regional corridor. It uses a preferred group spacing, deterministic spacing jitter, broad curvature, lateral variation, group-orientation variation, and elevation variation.

Preferred spacing is stylistic rather than a safety guarantee. Each adjacent edge is raised to the pair-specific reservation requirement when necessary. Longitudinal ordering is retained, so non-adjacent separation remains conservative.

The complete group-center set is translated so its mean equals the requested archipelago anchor.

### Hub

`Hub` models one dominant formation surrounded by supporting groups.

Group template 0 must have `ANCHOR` role and remains exactly at the requested archipelago center and base suspension elevation. Secondary, satellite, and outlier groups are placed by deterministic expanding candidate search with pair-specific reservation checks.

The hub is intentionally not recentered after placement: preserving the semantic anchor at the requested world location is more important than making the group-center centroid coincide with that location.

## Seed identity

All hierarchy-local randomness uses `SeedDerivation` semantic namespaces. Child-group root seeds are derived independently from the archipelago root, after which the already accepted group planner derives island-member seeds from each child-group root.

This produces stable hierarchical identity without sharing mutable random streams across levels.

## Initial acceptance requirements

The focused SF-IMP-0027 planner proof must demonstrate:

1. repeated planning of the same request is equality-exact;
2. group identifiers and semantic roles remain in requested order;
3. child-group root seeds are unique;
4. island-member seeds remain unique across the complete hierarchy;
5. changing the archipelago root seed changes derived hierarchy identity without changing requested group order;
6. arc group centers remain centered on the requested archipelago anchor;
7. arc planning raises unsafe preferred spacing to pair-specific reservation requirements;
8. hub group 0 remains exactly at the requested center/base elevation and requires `ANCHOR` role;
9. all group pairs satisfy the requested higher-level envelope gap;
10. every accepted child group fits inside its declared higher-level reservation;
11. deliberately undersized higher-level reservations fail deterministically;
12. morphology intent remains opaque to the archipelago planner, including arbitrary external provider IDs.

## Follow-on

After focused planner acceptance, the reference layer should realize a heterogeneous archipelago containing multiple accepted child groups and produce evidence at two resolutions:

- an archipelago-scale plan/occupancy view proving hierarchy, corridors, group separation, and visual dominance;
- selected group/member drill-down views reusing the accepted lower-level evidence rather than resampling every island at full single-island resolution.

The preferred first visual corpus should include one arc/corridor archipelago and one hub archipelago with a dominant central group, secondary formations, satellites, and at least one outlier.

## Deferred work

SF-IMP-0027 does not yet define:

- biome/material zoning across groups;
- gameplay routes or bridges between formations;
- multiple nested archipelago levels beyond the first group hierarchy;
- provider-certified spatial bounds;
- generalized N-way morphology mixtures;
- Minecraft/backend chunk realization;
- streaming or preloading policy.
