# Skyforge Authorship Agent State

**Lane:** Authorship  
**Status:** Canonical live lane handoff  
**Updated:** 2026-09-06 (America/Chicago)  
**Main snapshot at latest Authorship acceptance:** `a55e500c86f0baf910af809985956ac398742706`  
**Highest MERGED / ACCEPTED Authorship milestone:** **AUTH-0086**

Read first:

- [Program charter](PROGRAM_CHARTER.md)
- [Cross-lane contracts](CROSS_LANE_CONTRACTS.md)
- current `docs/authorship/` milestone documents
- recent Authorship PRs/commits named below

Detailed evidence remains in source/tests, CI artifacts, PRs, and dedicated authorship/review docs.

## MERGED / ACCEPTED

### Direction after the publication/provenance tranche

The generic recursive publication/provenance tranche ended at AUTH-0081. Do not resume protocol
recursion without a concrete production consumer.

AUTH-0082 / PR #220 was **closed unmerged**. Preserve the numbering gap; do not report it as accepted.

### AUTH-0083 — production morphology visual-review corpus

Merged/accepted as deterministic review machinery:

- 41 isolated production morphology specimens;
- production provider-neutral compiler path;
- top/underside/section/isometric diagnostics;
- exact Minecraft handoff IDs;
- no invented aesthetic thresholds.

Human aesthetic approval is still a separate manual gate under issue #214.

Reference: `docs/authorship/AUTH-0083-production-morphology-visual-review.md`.

### AUTH-0084 — regional morphology review corpus

Merged/accepted as deterministic regional review machinery:

- sparse, chain, cluster, Hub, Arc contexts;
- existing group/archipelago planners reused;
- descriptive regional diagnostics;
- exact Minecraft views/routes handoff;
- no premature aesthetic thresholds.

Human regional approval is still a separate manual gate under issue #214.

Reference: `docs/authorship/AUTH-0084-production-regional-morphology-review.md`.

### AUTH-0085 — native spring semantic admission

PR #234 merged as `335978905f5c5e235c07a114a653a3be24536c47`.

Exact acceptance:

- accepted head: `597d2ae58f2ed6047f6227974f9f41fbc2b2808f`;
- synchronized base: `0368261e59da1501266427980780848e331e3e7a`;
- CI #1335 PASS, run `34070501775`;
- evidence artifact `10000402074`;
- digest `sha256:52d06c88fcb4633c2919dd0d3207dec09666751cc0ae8946539de57fbaaa53ab`.

Accepted invariants:

- water requires authored cave interior + accepted AUTH-0023 `AQUIFER_BODY`;
- no second aquifer threshold;
- molten candidates fail closed without geothermal semantics;
- exact cave/aquifer provenance;
- cross-island semantic composition rejected;
- no Minecraft types, registry lookup, placement, storage, propagation, or I/O in the world contract.

Reference: `docs/authorship/AUTH-0085-native-spring-semantic-admission.md`.

### AUTH-0086 — visible hydrologic realization intent

PR #241 merged as `a55e500c86f0baf910af809985956ac398742706`.

Exact acceptance:

- accepted head: `0de4791f74d224d6f7b3611f833fe8c10c293018`;
- synchronized base: `5d4a293d6c57e3db4a52ec33dabd8725bbd4b091`;
- CI PASS, run `34077642262`;
- evidence artifact `10002704618`;
- digest `sha256:e647a1807bc42570e8a5eb2e8916636312778f058e91280e5df652e450e86a47`.

Accepted invariants:

- exactly one visible channel-water intent per accepted coherent naturalized channel path;
- exactly one retained-water intent per accepted footprint + dry margin;
- direct cascade/waterfall/edge-discharge mapping with no new threshold;
- exact source/riparian/margin provenance and deterministic source ordering;
- omitted, reordered, substituted, whole-plan cross-island, and mislabeled nested cross-island sources fail closed;
- no Minecraft registry, placement, fluid update, scheduling, storage, or I/O behavior in `skyforge-world`;
- 1280x720 architecture/provenance atlas plus count/provenance CSV evidence.

Reference: `docs/authorship/AUTH-0086-visible-hydrologic-realization-intent.md`.

## IN PROGRESS

No Authorship milestone is currently accepted as in progress. The next candidate boundary is AUTH-0087
only after the physical visible-hydrology responsibility split is made explicit.

## PROPOSED

### Next priority — physical visible-hydrology bridge

After AUTH-0086 merges, define the smallest handoff needed to turn its intents into physical water
realization.

Known constraint:

- AUTH-0009 waterbody `waterSurfacePotential`, depth potentials, and related hydrologic scalars are
  normalized semantic potentials, **not** literal world-space Y coordinates;
- physical vertical authority already exists through compiled volume columns and
  `SkyIslandSemanticDepthRealizationTransform`;
- do not invent a naive scalar-to-Y mapping;
- preserve exact-volume ownership and reuse existing Implementation fluid provenance/fencing.

Determine whether this is best expressed as thin backend-neutral rasterization support or handed
directly to Implementation. Do not grow new hydrology ontology without a concrete failure mode.

### Later semantic priorities

Subject to morphology/hydrology findings:

1. geology-to-visible-palette expression;
2. ecology/geography coupling;
3. deterministic Bootstrap Province convergence;
4. geothermal/volcanic semantics only if player/content design requires them.

## Architectural decisions / invariants

- `skyforge-world` remains backend-neutral.
- Authorship describes meaning/intent; it does not execute Minecraft mutation.
- Reuse accepted planners/source objects instead of recomputing parallel thresholds.
- Exact provenance and deterministic order are part of the contract.
- A backend may choose realization technique but may not silently change authored intent.
- Human aesthetic thresholds must be evidence-driven.
- Native cave springs are supplemental hydrology, never substitutes for authored surface channels,
  waterbodies, cascades, waterfalls, or edge discharge.
- Do not restart generic publication/provenance recursion without a concrete production consumer.

## Cross-lane dependencies

Implementation:

- consumes AUTH-0085 for semantically valid native spring admission;
- may consume accepted AUTH-0086 as the authoritative visible authored-hydrology intent contract;
- owns block-space realization, chunk lifecycle, fluid propagation/fencing, and live Minecraft proofs;
- issue #214 requires Minecraft rendering of AUTH-0083/AUTH-0084 deterministic handoff IDs.

Content / Experience:

- may motivate Bootstrap Province, geothermal, or other semantic capabilities;
- design records are not evidence that a semantic system is already accepted.

Shared coordination details: [Cross-lane contracts](CROSS_LANE_CONTRACTS.md).

## Known hazards / technical debt

- Some older README/runtime-overview text lags recent implementation merges; use git, reviews, and
  lane state for current boundaries.
- AUTH-0082 is a closed-unmerged historical branch.
- Concurrent lane merges frequently move `main` during authorship CI; exact-head acceptance requires
  recomposition and rerun whenever the base moves.
- AUTH-0083/AUTH-0084 prove review machinery, not aesthetic production quality.
- No geothermal/volcanic semantics currently exist.
- Semantic hydrology potentials are not block coordinates.

## Verification procedure

For Authorship changes:

1. keep neutral modules free of Minecraft/NeoForge imports;
2. run repository CI on the exact candidate head;
3. inspect world/reference tests for deterministic provenance and fail-closed invariants;
4. require declared evidence entry points under `skyforge-reference/build/evidence`;
5. record exact CI run and evidence artifact/digest for accepted proof corpora;
6. re-check `main` immediately before merge;
7. if `main` moved, recompose and rerun exact-head CI;
8. merge with an expected-head lock.

Architecture/provenance atlases do not require human review unless the milestone explicitly claims
aesthetic/player-view acceptance.

## MANUAL VERIFICATION REQUIRED

Issue #214 remains open.

Human review still needs the AUTH-0083/AUTH-0084 reference + Minecraft matrices, especially:

- long-range family identity;
- macro/meso hierarchy;
- rim/coast transitions;
- underside quality;
- regional negative space, navigation, and repetition;
- above/approach/below Minecraft views;
- short flight routes around/under representative islands;
- explicit decision on whether an underside-secondary vocabulary is needed.

## Ordered next work

1. Resolve the physical visible-hydrology responsibility split against existing compiled column authority.
2. If a backend-neutral mapping contract is genuinely required, open AUTH-0087 with a concrete downstream
   consumer and no naive semantic-potential-to-world-Y conversion.
3. Otherwise hand AUTH-0086 directly to Implementation and move Authorship to geology-to-visible-palette
   expression.
4. Keep issue #214 as the independent human morphology gate; do not infer aesthetic acceptance from
   hydrology architecture evidence.
