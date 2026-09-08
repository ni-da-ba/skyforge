# Skyforge Authorship Agent State

**Lane:** Authorship  
**Status:** Canonical live lane handoff  
**Updated:** 2026-09-07 (America/Chicago)  
**Main snapshot at latest Authorship acceptance:** `3fa34d5c30d7d89f48179cdcde6d6b4fb1740d40`  
**Highest MERGED / ACCEPTED Authorship milestone:** **AUTH-0096**

Read first:

- [Program charter](PROGRAM_CHARTER.md)
- [Cross-lane contracts](CROSS_LANE_CONTRACTS.md)
- current `docs/authorship/` milestone documents
- recent merged Authorship PRs named below

This ledger is intentionally concise. Detailed proof remains in milestone docs, source/tests, CI runs,
artifacts, and merged PR history.

## MERGED / ACCEPTED

### Older accepted foundation

AUTH-0001 through AUTH-0081 establish the accepted deterministic authorship, geology, hydrology,
material, publication, and provenance substrate. AUTH-0082 / PR #220 was **closed unmerged**; preserve
the numbering gap. Do not restart generic publication/provenance recursion without a concrete
production consumer.

### AUTH-0083 / AUTH-0084 — production morphology review machinery

Accepted deterministic isolated/regional production morphology review corpora. These prove review
machinery, not final aesthetic quality. Human morphology/regional approval remains issue #214.

References:

- `docs/authorship/AUTH-0083-production-morphology-visual-review.md`
- `docs/authorship/AUTH-0084-production-regional-morphology-review.md`

### AUTH-0085 — native spring semantic admission

PR #234 / merge `335978905f5c5e235c07a114a653a3be24536c47`.

Water springs require exact authored cave + accepted aquifer semantics; molten candidates fail closed
without geothermal semantics. No Minecraft placement/lifecycle enters Authorship.

### AUTH-0086 — visible hydrologic realization intent

PR #241 / merge `a55e500c86f0baf910af809985956ac398742706`.

Exact channel, retained-waterbody, cascade/waterfall, and edge-discharge intent with deterministic
provenance; physical rasterization/fluid behavior remains Implementation-owned.

### AUTH-0087 — published authored-realization binding

PR #254 / merge `04fb6dab33cf80e8b82b42fc9d0fc7837cce31c4`.

One publication may bind to authorship only through an explicit exact AUTH-0046 association catalog;
no order/seed/spatial inference substitutes for that binding.

### AUTH-0088 — published surface ecology

PR #262 / merge `844ad6cd09a7cbe60529f1a7d43badc12a84a1e1`.

Exact published-volume/world-XZ ecology query over AUTH-0087 ownership. Minecraft biome/Y/persistence
policy remains downstream.

### AUTH-0089 — island ecological opportunity

PR #268 / merge `badcfbf8f9cf102a1f6f8f7bd018183d84f74e37`.

Fixed-resolution island authored-habitat/ecology summary. No species, carrying capacity, spawn,
resource, or backend biome policy.

### AUTH-0090 — regional ecological opportunity

PR #276 / merge `1a2f9cc2e76bfd5ef9a36d3547dd0a0f3835fe28`.

Exact AUTH-0087 region aggregates canonical AUTH-0089 profiles with horizontal-area weighting while
retaining exact island provenance. No regional species/resource/province class.

### AUTH-0091 — freshwater habitat opportunity

PR #279 / merge `c739fe9a521e2c75f640bbfcf79eee6abd0298cf`.

Fixed accepted watershed/waterbody stack yields exact retained-waterbody provenance, coarse inundated
planning area, shoreline-cell count, POND/LAKE/WETLAND counts, and normalized depth opportunity.
These are planning semantics, not physical fluid volume or fauna/resource eligibility.

### AUTH-0092 — regional island isolation evidence

PR #289 / merge `f7440b05279d7cb7f7f72c575e70d0e880ef184f`.

Exact acceptance:

- accepted head `69a95f79e96272b200667ebab74ef5eb4d5ce7cb`;
- synchronized base `324373a1d981fc56a466f9d9ded842bdf6dc366d`;
- CI `34150119742` PASS;
- artifact `10029142925`;
- digest `sha256:d77a7fa5b930e72af13065fb24357cafbbcbfe271ce84a4562feadc188b45490`.

One exact AUTH-0087 region yields deterministic nearest-peer provenance, horizontal center distance,
and nominal radial gap. No isolation class, route cost, gameplay range, or physical terrain-edge claim.

Reference: `docs/authorship/AUTH-0092-regional-isolation-evidence.md`.

### AUTH-0093 — base-metal geological opportunity

PR #294 / merge `636f261cdc491d80fcf9a6ae73d1c10be480ccfd`.

Exact acceptance:

- accepted head `73a0d26c7455011b8ef54f9e860b206f2d764b44`;
- synchronized base `a1d550ad0b6d8153b2560c264fbb1e6c653145b1`;
- CI `34150993175` PASS;
- artifact `10029433121`;
- digest `sha256:cffa330ffc6192bf7c39f67a725b85bc41c5827cd67c4f73e0a60342552ad0cc`.

IRON/COPPER/ZINC normalized geological opportunity is defined over exact AUTH-0033 material-family
provenance. Every nonzero opportunity is subordinate to accepted mineral-bearing structural support.
No ore id, deposit count/volume/grade, availability class, guarantee, trade, or placement policy.

Reference: `docs/authorship/AUTH-0093-base-metal-geological-opportunity.md`.

### AUTH-0094 — regional base-metal opportunity inventory

PR #307 / merge `2e9618ce170a107e4e9fe88d80a26e5b537209ab`.

Concrete consumer: accepted CONTENT C20 requires hard Iron/Copper/Zinc guarantees to select or
re-plan scopes containing eligible AUTH-0093 geology rather than inject metals into unsuitable
islands.

Exact acceptance:

- accepted head `07d931eaf98dd27be454fb16af8b819df6c9e0ee`;
- synchronized base `0b76038a0b0a98628b6b3ec0e39b5cb0cd76a8d9`;
- full repository CI `34160240174` PASS;
- scoped evidence workflow `34160240210` PASS;
- evidence artifact `10032355416`;
- digest `sha256:2ad2f784f04782ba4a3821d936bf3bd4e9490dea1121141dc13dcfc1d7ac51e7`.

Accepted invariants:

- public profiling input is exactly one AUTH-0087 published authored-realization binding;
- every canonical AUTH-0046 association is profiled through AUTH-0093 from its exact authored descriptor;
- complete association/profile coverage and canonical order are retained; missing/reordered/substituted entries fail closed;
- geological eligibility is exactly the existing AUTH-0093 zero/nonzero boundary (`peakOpportunity > 0`) with no new threshold;
- eligible islands may be descriptively ranked by unchanged AUTH-0093 mean opportunity; exact ties retain canonical association order;
- opportunity is not summed or relabeled as regional reserves, grade, deposit volume, accessibility, or site selection;
- CONTENT C20 remains owner of COMMON_REGIONAL availability, STARTING_CLUSTER / POST_FLIGHT_PROVINCE guarantees, trade/salvage, progression criticality, and scope-selection/replan policy;
- Implementation remains owner of concrete ore/block identity, deposit geometry/count/volume/grade/accessibility, worldgen, persistence, and lifecycle.

Reference: `docs/authorship/AUTH-0094-regional-base-metal-opportunity-inventory.md`.

### AUTH-0095 — morphology surface-character diagnostics

PR #313 / merge `0185274ade7a07e890cbaba9d4601404748e89c0`.

Concrete consumers: SF-IMP-0083 / #284 and human morphology follow-ups #267/#283 require broader
multi-seed/multi-scale evidence before any Massif/Tableland retuning.

Exact acceptance:

- accepted head `1b1e1fc259b49dced2e8245bfdee803e5bd3440c`;
- synchronized base `28b55a609cfeb69b271b75b348ed59270f03bd76`;
- full repository CI `34166827820` PASS;
- scoped evidence workflow `34166827821` PASS;
- evidence artifact `10034435628`;
- digest `sha256:d2fdedbaa1e73850d7efc509e8172cd2acdf128b8e97854e2d81649a9c5fce9d`.

Accepted invariants:

- AUTH-0095 reuses exactly the 25 built-in AUTH-0083 member IDs/specs/seeds/scales and the same production compiler;
- the canonical AUTH-0083 97x97 horizontal review grid gives exact adjacent spacing `R/24` at every physical scale;
- upper-surface relief and standard deviation are normalized by nominal radius;
- central-gradient and curvature distributions are deterministic and scale-covariant;
- fixed normalized lag evidence spans `R/24`, `R/12`, `R/6`, and `R/3`;
- complete 3x3 / 5x5 / 9x9 local-window relief distributions provide multi-scale surface-character evidence;
- no walkability, plateau, bench, lumpiness, or aesthetic pass/fail threshold enters Authorship;
- the new `surface-character.csv` is emitted from the exact existing AUTH-0083 generation pass rather than a competing morphology corpus.

Reference: `docs/authorship/AUTH-0095-morphology-surface-character-diagnostics.md`.

### AUTH-0096 — local surface-site capability evidence

PR #318 / merge `3fa34d5c30d7d89f48179cdcde6d6b4fb1740d40`.

Concrete consumer: Bootstrap Province #224 and the staged structure-site-capability design require
backend-neutral local place evidence before Content selects civilization/structure roles and before
Implementation performs exact concrete geometry admission.

Acceptance evidence:

- final synchronized AUTH-0096 head `2657da3d4040fbb1e1dd164e738eb0c8a3b6781c`;
- final scoped evidence workflow `34174463114` PASS;
- final evidence artifact `10036782901`;
- digest `sha256:7d90ac548679d86e61e098ea74650318ad99e599d90c680a427894b6f6317af9`;
- full repository CI `34169136662` PASS on corrected AUTH-0096 code at
  `6984769b6c1ff7c17d36a221e914f09dfb4e141b`;
- that full-CI evidence remained portable under `VALIDATION_POLICY.md` because subsequent main
  movement changed only orthogonal docs/workflows/music state while the AUTH-0096 code/test blobs and
  behavioral dependency surface remained unchanged; synchronized scoped evidence was rerun on the
  final head.

Accepted invariants:

- public profiling input is exactly one AUTH-0046 `SkyIslandAuthoredRealizationAssociation`;
- the exact accepted 49x49 watershed lattice, canonical cell identity/order, and spacing `R/24`
  are retained;
- every anchor reports descriptive exact-realization physical support/relief plus already-accepted
  authored hydrology/interiority context without a second planning grid;
- 3x3 / 5x5 / 9x9 support and relief, cardinal grade, flow, retained-waterbody, shoreline,
  waterbody-margin, riparian, coherent-channel, and hydrologic-response evidence remain raw
  measurements rather than site pass/fail policy;
- exact physical evidence is tied to the exact realized placement: accepted planar detail signals
  sample world X/Z, so Authorship must not infer translation invariance for realized fine terrain;
- no village/airfield/dungeon/buildable/walkable classification, civilization role, structure tier,
  progression policy, Minecraft identity, or exact structure admission enters Authorship.

Ownership after AUTH-0096:

- **Authorship:** deterministic backend-neutral local world/site evidence and provenance;
- **Content / Experience:** structure/civilization roles, semantic requirement thresholds, progression,
  route/service meaning, and site selection policy;
- **Implementation:** concrete structure identity/geometry/orientation, exact physical admission,
  terrain accommodation, mutation, persistence, and lifecycle.

Reference: `docs/authorship/AUTH-0096-local-surface-site-capability.md`.

## IN PROGRESS

No Authorship milestone is currently in progress.

AUTH-0096 closes the first surface-site evidence gap required by Bootstrap/structure reintegration. Do not
assign AUTH-0097 merely to enumerate every conceptual site-capability field. Add cliff/underside,
interior/cave, access, regional-composition, or other semantics only when a retained consumer requires
them. Morphology family tuning still requires the matching Minecraft matrix plus human review.

## ACTIVE CROSS-LANE BOUNDARIES

### Content / Bootstrap

- C20 is accepted and may consume AUTH-0094 to inspect/select/re-plan published scopes around eligible Iron/Copper/Zinc geology.
- Iron availability/starting-cluster closure and Copper/Zinc post-flight-province guarantees remain Content-owned.
- AUTH-0094 does not prove that a specific generated province satisfies C20; executable province planning/acceptance is downstream.
- AUTH-0096 now supplies exact local surface-site evidence for Bootstrap civilization/structure requirement matching; Content still owns role requirements, thresholds, selection, and progression meaning.

### Implementation

Implementation may consume AUTH-0093/0094 only after Content resource policy is applied. It owns all
concrete deposits, blocks/tags, quantities, accessibility, placement/worldgen, persistence, and chunk
lifecycle. SF-IMP-0083 / issue #284 may consume AUTH-0095 as the backend-neutral surface-character measurement
seam for the exact built-in seed/scale matrix. Minecraft carrier/persistence/runtime evidence remains
Implementation-owned, and AUTH-0095 does not itself authorize morphology-family retuning.
Implementation may also consume an AUTH-0096-selected candidate only after Content supplies a concrete
requirement/pairing; exact structure geometry/admission and terrain accommodation remain Implementation-owned.

### Ecology / fauna / civilization

AUTH-0089/0090/0091/0092 remain evidence inputs, not spawn/carrying-capacity/faction/province policy.
AUTH-0096 is now an additional local site/environment evidence input for structure/civilization work,
not a settlement or faction policy. Add cave/cliff, disturbance, predator-pressure, trophic,
civilization-history, geothermal, petroleum, or other semantics only when a concrete consumer proves a gap.

## Architectural invariants

- `skyforge-world` remains backend-neutral.
- Authorship describes meaning/evidence, not Minecraft mutation or gameplay tuning.
- Reuse accepted planners/source objects instead of recomputing parallel thresholds.
- Exact provenance and deterministic order are contractual.
- Backend realization may choose technique but may not silently change authored intent.
- Human aesthetic thresholds remain evidence-driven.
- Native cave springs supplement rather than replace authored surface hydrology.
- Generic publication/provenance recursion is closed unless a concrete production consumer reopens it.

## Known hazards

- Parallel lane merges frequently move `main`; follow `VALIDATION_POLICY.md`: inspect dependency-surface impact, preserve expensive evidence across demonstrably orthogonal movement, and rerun the cheapest synchronized gate that can falsify the remaining risk rather than chasing timestamps.
- Some other lane ledgers may lag merged history; current `main`, merged PRs, tests, and cross-lane contracts remain authoritative.
- AUTH-0083/AUTH-0084 prove review machinery, not final visual quality.
- No accepted geothermal/volcanic semantics currently exist.
- Semantic hydrology/depth/material opportunity values are not literal block coordinates, metres, reserves, or quantities.

## Verification procedure

For Authorship changes:

1. keep neutral modules free of Minecraft/NeoForge imports;
2. run targeted semantic/provenance tests and a declared evidence entry point;
3. run routine repository CI on a current integration candidate when the change affects shared code;
4. record exact workflow/artifact/digest evidence;
5. re-check `main` immediately before merge;
6. if `main` moved, classify whether the tested dependency surface changed;
7. recompose and rerun affected verification when needed, but preserve expensive evidence across
   demonstrably orthogonal movement per `VALIDATION_POLICY.md` and require synchronized cheap/current-head evidence;
8. merge with an expected-head lock and update this ledger/cross-lane contracts.

## MANUAL VERIFICATION REQUIRED

Issue #214 remains open for production morphology review, including:

- long-range family identity;
- macro/meso hierarchy;
- rim/coast transitions and underside quality;
- regional negative space/navigation/repetition;
- above/approach/below Minecraft views and representative flight routes;
- decision on whether an underside-secondary vocabulary is needed.

AUTH-0095 introduces no new independent human/manual gate; it supplies measurement evidence to the
already-open #214/#267/#283 review. Any Massif/Tableland tuning decision still requires that human
comparison against the matching SF-IMP-0083 Minecraft matrix.

AUTH-0096 introduces no new independent human/manual gate. Human judgment begins only after Content
selects concrete civilization/structure requirements and Implementation presents player-facing sites.

## Ordered next work

1. Let Content/Bootstrap consume AUTH-0096 for concrete surface-site requirement matching in #224.
2. Do not promote the working civilization/structure design documents into new Authorship semantics
   until a retained executable consumer identifies the next missing backend-neutral cause; surface
   evidence alone does not justify a regional wrapper, site class, or settlement policy.
3. Keep AUTH-0095 handed to SF-IMP-0083/#284. Current Implementation remains in targeted Tableland
   runtime recovery, so #267/#283 morphology retuning is not yet justified.
4. Return to morphology only when the synchronized Minecraft matrix plus #214 human review identifies
   a systemic family-level change; otherwise continue the next concrete Bootstrap-owned semantic gap.

