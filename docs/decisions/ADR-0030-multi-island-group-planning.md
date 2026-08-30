# ADR-0030: Multi-Island Group Planning

- **Status:** Proposed pending implementation and local acceptance
- **Date:** 2026-08-30
- **Work item:** SF-IMP-0026

## Context

SF-IMP-0025 completes the first provider-neutral single-island morphology stack: built-in and external providers can define primary morphology, participate in pairwise hybridization, receive bounded local detail, and contribute positive secondary geography without requiring enum knowledge or provider-local node names.

Skyforge now needs to move from isolated suspended volumes toward world-scale organization. The next layer is not another single-island morphology feature. It is a deterministic planner for chains and groups of suspended islands.

The group layer must remain orthogonal to morphology implementation. A group planner should be able to place:

- one built-in provider;
- one external provider;
- one pairwise provider blend;
- and, later, any generalized morphology composition that satisfies the same morphology-specification boundary.

The planner must therefore depend on provider identities and provider-composition specifications rather than `MorphologyFamily`.

## Decision

SF-IMP-0026 introduces a recipe-layer multi-island planning model. The first accepted boundary produces immutable member plans rather than one monolithic density graph.

A group request supplies:

- one root seed;
- one signal-free schema-1 member descriptor template whose center and suspension elevation define the group anchor;
- one morphology specification for each member;
- one explicit reserved horizontal radius per member;
- one minimum requested inter-island gap;
- one bounded suspension-elevation jitter;
- one deterministic chain or cluster layout.

The planner returns an immutable ordered list of member plans. Each member plan contains:

- a stable ordinal/member identifier;
- a semantically derived geometry seed;
- a complete `SkyIslandVolumeDescriptor` with world-space center, suspension elevation, and orientation;
- the exact requested morphology specification;
- the reserved horizontal placement radius.

The member list is a placement/composition plan. It does not merge all members into one graph and does not require a provider registry in order to plan positions.

## Morphology specification boundary

The first group planner accepts two morphology-specification forms:

1. a single `MorphologyProviderId` plus independent detail and secondary amplitudes;
2. one canonical `MorphologyProviderBlend` plus independent detail and secondary amplitudes.

This deliberately preserves a representation for a true single-provider island rather than encoding single providers as a zero-weight blend with an irrelevant second parent.

The morphology-specification interface is intended to admit a future normalized N-way provider mixture without changing the group planner contract.

## Explicit placement reservation

The planner does **not** infer world-space extent from `SkyIslandVolumeDescriptor.nominalRadius()`.

An arbitrary morphology provider may exceed the nominal radius intentionally, so using nominal radius as a hard placement bound would reintroduce built-in morphology assumptions through the group layer.

Instead every request supplies an explicit `reservedHorizontalRadius`. The layout must guarantee center-to-center spacing of at least:

`2 * reservedHorizontalRadius + minimumGap`

for every member pair.

Later geometry/evidence stages must verify that compiled island geometry actually respects the reserved placement envelope. A future provider certification API may expose proven bounds, but SF-IMP-0026 does not invent such metadata prematurely.

## Deterministic layouts

### Chain

A chain is generated in a local along/across frame and rotated into world space by a requested heading.

The chain layout supports:

- nominal center spacing;
- bounded deterministic spacing jitter;
- bounded lateral jitter;
- a smooth broad curvature amplitude;
- bounded member-orientation jitter.

Longitudinal coordinates remain strictly ordered. Lateral curvature and jitter therefore cannot reduce Euclidean separation below the deterministic longitudinal separation.

### Cluster

A cluster is generated through deterministic candidate placement around the group anchor. Each accepted candidate must satisfy the same explicit center-separation requirement against every previously accepted member.

Candidate generation is seed-derived and bounded; radial search expands deterministically until a valid point is found. Member zero occupies the group anchor.

## Seed identity

All member and placement randomness is derived through `SeedDerivation` with stable semantic namespaces. No mutable random stream is shared across members.

This ensures that:

- repeated planning is exact;
- member geometry seeds are stable and independent;
- schedule or iteration changes do not silently perturb unrelated members;
- later higher-level planners can derive independent group seeds hierarchically.

## Initial acceptance requirements

SF-IMP-0026 planning must demonstrate:

1. invalid layouts, amplitudes, reservation radii, gaps, and templates fail early;
2. planning is exactly deterministic for the same request;
3. changing the root seed changes derived member identity/placement without changing requested morphology order;
4. member geometry seeds are unique within the accepted corpus;
5. all planned descriptors preserve template geometry controls except seed, center, suspension elevation, and ridge orientation;
6. morphology specifications are retained byte-for-byte/equality-exact and require no built-in enum;
7. chain members remain ordered along the requested heading and satisfy the reservation/gap constraint;
8. cluster members satisfy the reservation/gap constraint for every pair;
9. chain and cluster member centers remain centered around the requested group anchor within numerical tolerance;
10. group planning has no provider-registry or backend dependency.

## Follow-on within or immediately after SF-IMP-0026

Once the planner contract passes focused tests, the reference layer should compile a mixed group containing built-in, blended, and external-provider members and produce group-scale evidence. The preferred realization is a collection of independently compiled island volumes plus a reference union/evidence sampler, not a single giant procedural graph.

That approach keeps group planning compatible with future Minecraft/worldgen placement, where islands can be realized independently while still sharing one deterministic plan.

## Deferred work

SF-IMP-0026 does not initially define:

- biome/material assignment across a group;
- terrain bridges or physically connected island masses;
- navigation/gameplay semantics between members;
- group hierarchy such as province -> archipelago -> chain -> island;
- provider discovery/loading;
- descriptor schema 3;
- arbitrary normalized N-way morphology mixtures;
- provider-certified placement envelopes;
- backend chunk scheduling or Minecraft integration.
