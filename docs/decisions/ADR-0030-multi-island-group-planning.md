# ADR-0030: Multi-Island Group Planning

- **Status:** Planner implemented and locally focused-tested; mixed group realization acceptance pending
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

SF-IMP-0026 introduces a recipe-layer multi-island planning model. The accepted boundary produces immutable member plans rather than one monolithic density graph.

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

## Direct single-provider enrichment

Group realization exposed one missing public recipe capability: provider-aware enrichment was publicly available for pairwise provider blends, but not as a direct single-provider recipe.

SF-IMP-0026 therefore adds `EnrichedProviderMorphologySkyIslandVolumeRecipe` and `ProviderMorphologyEnrichment`. This path uses the same accepted provider-primary canonicalization, bounded-detail carrier, positive provider-secondary contribution, and exact density intersection as the hybrid path, but does not manufacture a fake second parent.

`SkyIslandMorphologySpecCompiler` dispatches:

- `ProviderMorphologySpec` to the direct single-provider enriched recipe;
- `ProviderBlendMorphologySpec` to the accepted provider-hybrid enriched recipe.

The compiler still produces one independent `CompiledSkyIslandVolume` per group member. Zero enrichment for a single provider preserves that provider's exact primary geometry graph bytes.

## Explicit placement reservation

The planner does **not** infer world-space extent from `SkyIslandVolumeDescriptor.nominalRadius()`.

An arbitrary morphology provider may exceed the nominal radius intentionally, so using nominal radius as a hard placement bound would reintroduce built-in morphology assumptions through the group layer.

Instead every request supplies an explicit `reservedHorizontalRadius`. The layout must guarantee center-to-center spacing of at least:

`2 * reservedHorizontalRadius + minimumGap`

for every member pair.

The group realization acceptance corpus verifies compiled island geometry against those reservation envelopes. A future provider certification API may expose proven bounds, but SF-IMP-0026 does not invent such metadata prematurely.

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

Candidate generation is seed-derived and bounded; radial search expands deterministically until a valid point is found. After acceptance, the complete set is translated so its actual center centroid equals the requested group anchor. Translation preserves every pairwise gap.

## Seed identity

All member and placement randomness is derived through `SeedDerivation` with stable semantic namespaces. No mutable random stream is shared across members.

This ensures that:

- repeated planning is exact;
- member geometry seeds are stable and independent;
- schedule or iteration changes do not silently perturb unrelated members;
- later higher-level planners can derive independent group seeds hierarchically.

## Planner acceptance requirements

The focused planner proof demonstrates:

1. invalid layouts, amplitudes, reservation radii, gaps, and templates fail early;
2. planning is exactly deterministic for the same request;
3. changing the root seed changes derived member identity/placement without changing requested morphology order;
4. member geometry seeds are unique within the accepted corpus;
5. all planned descriptors preserve template geometry controls except seed, center, suspension elevation, and ridge orientation;
6. morphology specifications are retained equality-exact and require no built-in enum;
7. chain members remain ordered along the requested heading and satisfy the reservation/gap constraint;
8. cluster members satisfy the reservation/gap constraint for every pair;
9. chain and cluster member centers remain centered around the requested group anchor within numerical tolerance;
10. group planning has no provider-registry or backend dependency.

The user-reported local focused planner test completed successfully before group realization work proceeded.

## Reference group realization

The reference layer realizes a group as a collection of independently compiled member volumes plus a union sampler. It does **not** concatenate all member graphs into one giant procedural graph.

The first group-scale review grid uses 16-unit horizontal and 8-unit vertical spacing. Individual morphology quality remains governed by the much finer accepted single-island corpora; this coarser grid exists to prove group organization, separation, clipping, and determinism across world-scale extents.

The union sampler records:

- union occupancy;
- per-voxel member ownership;
- horizontal upper/underside envelopes;
- per-member sampled solid counts;
- connected-component count;
- overlapping-solid count;
- domain-face contacts;
- realized union bounds;
- stable occupancy identity.

The visual writer produces:

- `plan.png` — intended centers and reservation envelopes;
- `top-down-union.png` — realized union planforms by member;
- `upper-envelope.png` and `underside-envelope.png` — group elevation structure;
- east-west and north-south group-center sections;
- `isometric.png` — group upper-surface point projection.

## Mixed-provider acceptance corpus

The numerical corpus realizes both chain and cluster layouts across the three established Skyforge seeds.

The stable reference vocabulary deliberately mixes:

- native built-in providers;
- the genuine external `reference:crescent` provider;
- built-in↔built-in pairwise blends;
- external↔built-in pairwise blends;
- full bounded detail and provider-aware secondary morphology.

For every realized group the gate requires:

1. every planned member contributes positive sampled occupancy;
2. N planned islands produce exactly N disconnected union components;
3. zero sampled solid overlap between different members;
4. zero review-domain face contacts;
5. actual realized horizontal samples remain inside their declared placement reservation within one review-cell tolerance;
6. the requested minimum reserved gap remains satisfied;
7. repeated planning, member compilation, and union sampling produce an identical occupancy SHA-256.

The human visual gate must confirm that the chain reads as an intentional ordered sky-island formation and the cluster reads as an organic group rather than a regular lattice, while retaining perceptible per-island morphology variation.

## Local verifier

`scripts\verify-sf-imp-0026.bat` runs:

1. Java runtime;
2. focused deterministic chain/cluster planner tests;
3. single-provider and provider-blend morphology-spec compiler tests;
4. six-group mixed-provider realization acceptance;
5. the stable-seed chain/cluster visual atlas.

The atlas is written to `skyforge-reference\build\evidence\multi-island-group-v1`.

## Next step

If SF-IMP-0026 passes numerical and human visual acceptance, Skyforge will have its first accepted world-scale organization primitive. The next architectural choice should concern **hierarchical spatial organization**—for example archipelagos containing multiple groups/chains—or the first backend/worldgen realization seam, rather than returning immediately to deeper single-island morphology.

## Deferred work

SF-IMP-0026 does not define:

- biome/material assignment across a group;
- terrain bridges or physically connected island masses;
- navigation/gameplay semantics between members;
- group hierarchy such as province -> archipelago -> chain -> island;
- provider discovery/loading;
- descriptor schema 3;
- arbitrary normalized N-way morphology mixtures;
- provider-certified placement envelopes;
- backend chunk scheduling or Minecraft integration.
