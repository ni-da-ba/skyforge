# ADR-0031: Hierarchical Archipelago Composition

- **Status:** Focused planner proof accepted; regional realization and human visual acceptance pending
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

## Focused planner acceptance

The focused SF-IMP-0027 planner proof demonstrates:

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

The user-reported local planner build completed successfully before regional realization work proceeded.

## Regional realization

The reference layer realizes an archipelago as independently compiled island volumes grouped beneath their accepted child plans. It does not concatenate child groups or island graphs into one procedural graph.

The regional evidence sampler records:

- union occupancy;
- horizontal upper and underside envelopes;
- horizontal child-group ownership for visual review;
- per-island and per-group sampled solid counts;
- total connected-component count;
- overlapping-solid count;
- cross-group overlapping-solid count;
- review-domain face contacts;
- realized regional bounds;
- stable occupancy identity.

A temporary compact group-owner byte volume is used only during sampling to detect cross-group overlaps and is discarded afterward. Full voxel ownership arrays are deliberately not retained because they are unnecessary for review and would make deterministic regional replay memory-bound.

The first regional topology grid uses 32-unit horizontal and 8-unit vertical spacing. Lower-level morphology and group acceptance remain authoritative at their finer resolutions; the regional grid exists to prove hierarchy, survival, separation, corridors, and clipping.

## Regional reference corpus

The first corpus contains two four-group archipelagos across the three established seeds:

### Hub

- one seven-island `ANCHOR` cluster with the largest group reservation;
- one five-island `SECONDARY` chain;
- one three-island `SATELLITE` cluster;
- one two-island `OUTLIER` chain;
- 17 islands total.

### Arc

- one five-island major cluster;
- one four-island chain;
- one three-island satellite cluster;
- one two-island outlier pair;
- 14 islands total;
- broad curvature and substantial deliberate empty sky between child formations.

Both scenes mix built-in providers, the genuine external `reference:crescent` provider, and provider blends with full bounded detail and provider-aware secondary morphology.

For each of the six regional realizations the numerical gate requires:

1. every planned child group contributes positive sampled occupancy;
2. every planned island contributes positive sampled occupancy;
3. N planned islands produce exactly N union connected components;
4. zero sampled overlap between independent islands;
5. zero sampled cross-group overlap;
6. zero regional-domain face contacts;
7. the planned minimum group-envelope gap remains satisfied;
8. repeated hierarchy planning, child planning, island compilation, and regional sampling produce the identical occupancy SHA-256.

## Regional visual evidence

The stable-seed Hub and Arc atlas writes:

- `plan.png` — child-group reservation envelopes, semantic roles, group centers, and island centers;
- `top-down-groups.png` — realized island geometry colored by child group;
- `upper-envelope.png` and `underside-envelope.png` — regional vertical structure;
- `isometric.png` — fit-to-scene regional upper surfaces colored by child group.

The isometric renderer fits actual realized points to the canvas rather than applying the fixed scale that made SF-IMP-0026 isometrics visually undersized.

Human review must confirm more than numerical separation:

- the Hub has a clearly dominant anchor formation and subordinate supporting formations;
- the Arc reads as an ordered regional corridor rather than four unrelated groups;
- substantial empty sky remains visually intentional rather than accidental;
- child-group identities survive realization;
- regional organization adds hierarchy beyond the accepted single-group cluster/chain views.

## Local verifier

`scripts\verify-sf-imp-0027.bat` runs:

1. Java runtime;
2. the accepted hierarchical planner proof;
3. six-scene regional realization acceptance;
4. the stable-seed Hub/Arc regional visual atlas.

The atlas is written to `skyforge-reference\build\evidence\hierarchical-archipelago-v1`.

## Next step

If the numerical and human regional gates pass, Skyforge will have an accepted hierarchy from morphology provider -> island -> group -> archipelago. The next work should move toward backend/world realization semantics, biome/material interpretation, or regional authoring controls rather than immediately adding another spatial hierarchy level.

## Deferred work

SF-IMP-0027 does not yet define:

- biome/material zoning across groups;
- gameplay routes or bridges between formations;
- multiple nested archipelago levels beyond the first group hierarchy;
- provider-certified spatial bounds;
- generalized N-way morphology mixtures;
- Minecraft/backend chunk realization;
- streaming or preloading policy.
