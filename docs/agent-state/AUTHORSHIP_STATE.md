# Skyforge Authorship Agent State

**Lane:** Authorship  
**Status:** Canonical live lane handoff  
**Updated:** 2026-09-06 (America/Chicago)  
**Main snapshot at latest Authorship acceptance:** `04fb6dab33cf80e8b82b42fc9d0fc7837cce31c4`  
**Highest MERGED / ACCEPTED Authorship milestone:** **AUTH-0087**

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

### AUTH-0087 — published authored-realization binding

PR #254 merged as `04fb6dab33cf80e8b82b42fc9d0fc7837cce31c4`.

Exact acceptance:

- accepted head: `bc8e92842bca275b656de58592664096dbe63134`;
- synchronized base: `d0e4329ed95088a38a5a0f8f2880736a34986616`;
- CI PASS, run `34079821151`;
- evidence artifact `10003436186`;
- digest `sha256:7379ff41b44eb5cddf0709605fd7e8b98e0b39def2ad039071dab99c2fc21de3`.

Accepted invariants:

- one accepted AUTH-0058 publication is paired with one explicit AUTH-0046 association catalog;
- realization root, volume count, exact volume identities, and exact realized-volume values must match;
- missing, extra, substituted, and foreign-root association sets fail closed;
- association identity is never inferred from order, geometry seed, center, bounds, radius, or morphology;
- authored-world and realization-root identity domains remain independent;
- the accepted AUTH-0049 material composer may be exposed from the proven association set;
- the same general binding may support later authored world-space semantics without duplicating the gate;
- no Minecraft/NeoForge identity, placement, lifecycle, geology threshold, ecology threshold, or material threshold enters the contract.

Reference: `docs/authorship/AUTH-0087-published-authored-realization-binding.md`.

## IN PROGRESS

### AUTH-0088 — published surface ecology projection

Stacked development branch: `auth/auth-0088-published-surface-ecology`.

Current work is **unaccepted** and was initially stacked on the AUTH-0087 candidate. Before any AUTH-0088
acceptance it must be recomposed onto current `main` after this state merge and receive fresh exact-head CI.

Intended boundary:

- exact published volume ID + world-space X/Z -> explicit AUTH-0046 local frame;
- require authoritative compiled horizontal column support;
- require current naturalized authored-domain interiority;
- expose unchanged AUTH-0003 `SkyIslandEcologySample` only where both gates hold;
- no physical Y/quart-cell policy and no Minecraft biome identity in Authorship.

## PROPOSED

### Production consumption priorities

- Implementation may consume AUTH-0087 + AUTH-0049 to replace the generic proof material palette with
  exact authored material application keys; concrete BlockState/material binding remains adapter-owned.
- AUTH-0088 should provide the missing backend-neutral surface-ecology projection needed by the existing
  exact-volume biome resolver without taking over Minecraft vertical/quart-cell presentation policy.
- After AUTH-0088, prefer demonstrated ecology/geography or Bootstrap Province authorship failures over
  recursive publication/provenance protocol work.

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
- may consume AUTH-0087 as the exact publication/authorship association gate before AUTH-0049 material composition;
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

1. Merge this AUTH-0087 state boundary.
2. Recompose the stacked AUTH-0088 surface-ecology work onto resulting current `main`.
3. Complete exact-volume-ID/world-XZ ecology projection with physical-column and authored-domain fail-closed gates.
4. Require fresh exact-head repository CI/evidence before AUTH-0088 acceptance.
5. Keep SF-IMP-0080's human ecology-showcase gate and issue #214 morphology-quality gate independent.
