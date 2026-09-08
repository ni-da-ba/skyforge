# Skyforge Cross-Lane Contracts

**Status:** Canonical concise coordination state  
**Updated:** 2026-09-07 (America/Chicago)  
**Repository snapshot after AUTH-0096 acceptance:** `3fa34d5c30d7d89f48179cdcde6d6b4fb1740d40`

Detailed milestone evidence belongs in lane ledgers, milestone docs, tests, workflows, and merged PRs.
This file records only coordination boundaries another lane must preserve.

## Program-wide invariants

- Backend-neutral modules remain free of Minecraft/NeoForge ontology unless a neutral abstraction is justified.
- Authorship owns world meaning/evidence; Implementation owns physical realization/lifecycle; Content owns game integration/experience/progression; Music / Audio owns score/audio authorship and source identity.
- Exact three-dimensional ownership, explicit provenance, and deterministic identity remain fundamental.
- Existing mods/content are asset/capability libraries under Skyforge semantic authority.
- Do not compensate for morphology/ecology problems by increasing world/content density; negative space is intentional.

## Current lane snapshot

| Lane | Repository-visible boundary |
| --- | --- |
| Authorship | `AUTH-0096` accepted: exact local surface-site capability evidence over AUTH-0046 provenance; AUTH-0095 remains the morphology-diagnostic handoff |
| Implementation | `SF-IMP-0082` accepted; SF-IMP-0083 / #284 owns remaining AUTH-0083 built-in seed/scale Minecraft matrix |
| Content / Experience | `C20` merged on current history: Iron/Copper/Zinc availability/guarantee policy over AUTH-0093; C11/C12 remain separately in progress/reserved |
| Music / Audio | `MUS-0002` accepted in `30b5202fec3ae07b0720b42eb8d7770c3c463843`: canonical soundtrack source integrity is machine-gated in normal CI; human listening/CWP recovery gates remain explicit |
| AUDIT | `AUDIT-0008` accepted: repository-visible process/convergence health remains supervisory responsibility |

If a lane ledger lags merged history, current `main`, merged PRs, tests, and this coordination file take precedence until its owner repairs the ledger.

## Authorship realization contracts

### AUTH-0085 — native spring admission — ACCEPTED

Natural water springs require authored cave interior plus accepted aquifer support. Molten candidates fail closed without geothermal/volcanic semantics. Physical fluid realization/lifecycle remains Implementation-owned.

### AUTH-0086 — visible authored hydrology — ACCEPTED

Implementation may consume exact channel, retained-waterbody, cascade/waterfall, and edge-discharge intents. It owns rasterization, block/fluid identity, mutation, scheduling, persistence, and lifecycle. Normalized semantic potentials are not literal world Y.

### AUTH-0087 — published authored-realization binding — ACCEPTED

Publication/authorship association exists only through the explicit exact AUTH-0046 catalog/root/count/coverage contract. No seed/order/spatial inference may replace it.

### AUTH-0088 / 0089 / 0090 — ecology seams — ACCEPTED

- AUTH-0088: exact published-volume/world-XZ local ecology query.
- AUTH-0089: fixed island-scale authored-habitat/ecology opportunity summary.
- AUTH-0090: exact-region area-weighted ecology summary with canonical island provenance.

These assign no species, spawn counts, carrying capacity, resources, province class, Minecraft biome, or backend lifecycle.

### AUTH-0091 — freshwater habitat opportunity — ACCEPTED

Retained-waterbody planning semantics expose exact source provenance, coarse inundated planning area, shoreline-cell count, source-kind counts, and normalized depth opportunity. These are not physical cubic water volume or fauna/agriculture/resource eligibility.

### AUTH-0092 — regional island isolation evidence — ACCEPTED

One exact AUTH-0087 region yields canonical nearest-peer provenance, horizontal center distance, and nominal radial gap. These are raw planning distances, not physical terrain-edge distance, route cost, gameplay range, or an ecological-isolation class.

### AUTH-0093 — base-metal geological opportunity — ACCEPTED

One authored island exposes normalized `IRON`, `COPPER`, and `ZINC` opportunity over exact AUTH-0033 mineral-bearing structural provenance. Every nonzero opportunity requires and is bounded by accepted mineral-bearing support.

AUTH-0093 provides no ore id, grade, reserves, deposit count/volume, availability class, guarantee, trade/salvage, or placement policy.

### AUTH-0094 — regional base-metal opportunity inventory — ACCEPTED

AUTH-0094 / PR #307 merged as `2e9618ce170a107e4e9fe88d80a26e5b537209ab` from exact-head candidate `07d931eaf98dd27be454fb16af8b819df6c9e0ee`.

One exact AUTH-0087 published region now inventories every canonical AUTH-0093 island profile with exact association provenance. It exposes:

- raw geological eligibility exactly at the already-accepted AUTH-0093 zero/nonzero boundary (`peakOpportunity > 0`);
- eligible-island count/list by Iron/Copper/Zinc;
- descriptive descending ranking by unchanged AUTH-0093 mean opportunity, preserving canonical order on exact ties.

AUTH-0094 does **not** satisfy C20 guarantees, select a mine/site, infer reserves/grade/volume/accessibility, or realize Minecraft resources.

Ownership after AUTH-0094:

- **Content C20:** availability class, first-flight criticality, guarantee scope, bounded trade/salvage, and scope selection/replanning;
- **Authorship:** exact geology/opportunity/provenance inventory;
- **Implementation:** ore/block/tag identity, deposit geometry/count/volume/grade/accessibility, placement/worldgen, persistence, and lifecycle.

### AUTH-0095 — morphology surface-character diagnostics — ACCEPTED

AUTH-0095 / PR #313 merged as `0185274ade7a07e890cbaba9d4601404748e89c0` from exact-head candidate `1b1e1fc259b49dced2e8245bfdee803e5bd3440c`.

For the exact 25 built-in AUTH-0083 seed/scale specimens, Authorship now exposes threshold-free,
scale-normalized upper-surface character evidence: global relief/statistical spread, gradient and
curvature quantiles, fixed normalized-lag relief, and complete local-window relief distributions.
The evidence reuses the exact AUTH-0083 generation pass and member provenance.

Cross-lane consequences:

- SF-IMP-0083 / #284 may consume these metrics alongside the matching Minecraft carriers;
- #267/#283 human review may use them to distinguish systemic short-period relief / plateau-identity problems from specimen-specific observations;
- AUTH-0095 does **not** define walkability, plateau/bench membership, lumpiness classes, or aesthetic pass/fail thresholds;
- Authorship must not retune Massif/Tableland until the matching Minecraft matrix and human review justify a family-level change;
- Implementation still owns block-space traversal/carrier/persistence/runtime evidence.

### AUTH-0096 — local surface-site capability evidence — ACCEPTED

AUTH-0096 / PR #318 merged as `3fa34d5c30d7d89f48179cdcde6d6b4fb1740d40`.

For one exact AUTH-0046 authored-realization association, downstream planners may now inspect every
canonical accepted watershed anchor with:

- exact-realization physical surface presence and normalized upper offset;
- complete 3x3 / 5x5 / 9x9 physical support and normalized local relief;
- mean cardinal grade;
- authored interiority and normalized flow accumulation;
- retained-waterbody, shoreline/depth, waterbody-margin, riparian, coherent-channel, and hydrologic
  terrain-response context.

The profile preserves exact watershed identity/order and introduces no site-classification threshold.

Cross-lane consequences:

- **Content / Experience** may define neutral structure/civilization requirements, thresholds, and
  selection policy over this evidence;
- **Implementation** must still prove each concrete Minecraft/mod structure's exact geometry,
  orientation, support/clearance, terrain accommodation, mutation, persistence, and lifecycle;
- AUTH-0096 does **not** mean an island/anchor supports a village, airfield, dungeon, dock, or any
  other named role;
- exact realized physical evidence must not be treated as translation-invariant because accepted
  planar detail signals sample world X/Z;
- no concrete structure roll, progression guarantee, settlement density, faction state, or loot
  policy is created by AUTH-0096.

## Morphology / Minecraft realization

### SF-IMP-0080 — visible land ecology — ACCEPTED

Legible forest/taiga Minecraft ecology passed persistence/reopen and project-owner visual review. It proves visible land ecology, not a complete AUTH-0003-to-Minecraft-biome translation. Issue #261 remains non-blocking presentation follow-up.

### SF-IMP-0081 / 0082 / issue #214 — built-in SMALL morphology carriers — ACCEPTED

All five SMALL / seed-skyforge built-in AUTH-0083 families have exact Minecraft carrier/persistence evidence. SF-IMP-0083 / #284 owns the remaining built-in seed/scale matrix.

Issue #214 remains the broader human morphology/regional gate. #267 tracks Massif traversal lumpiness; #283 tracks Tableland-vs-Massif separation. AUTH-0095 now supplies the backend-neutral distribution/spatial-scale diagnostics for that comparison, but it does not replace the Minecraft/human gate or authorize tuning by itself.

## Active Content / Bootstrap contracts

### Bootstrap Province — issue #224

Organizing experience spine:

```text
spawn -> survival foothold -> Create workshop -> cheap glider -> shared thermals/fauna
-> first powered aircraft -> regional specialization -> freight/infrastructure
-> mature skyborne civilization evidence
```

First powered flight remains pre-Brass/pre-petroleum unless executable recipe closure disproves it.

### C20 — base-metal availability and guarantees — ACCEPTED

Verified merged policy on current history (`0b76038a0b0a98628b6b3ec0e39b5cb0cd76a8d9`):

- **IRON:** `COMMON_REGIONAL`, first-flight critical, `STARTING_CLUSTER` guarantee;
- **COPPER:** `COMMON_REGIONAL`, not first-flight critical, `POST_FLIGHT_PROVINCE` guarantee;
- **ZINC:** `COMMON_REGIONAL`, not first-flight critical, `POST_FLIGHT_PROVINCE` guarantee.

A metal is geologically eligible iff AUTH-0093 has nonzero opportunity. Mean opportunity may rank already-eligible candidates only. Availability/guarantee semantics do not vary with magnitude.

Hard guarantees must select/re-plan an eligible authored scope rather than inject a metal into zero-opportunity geology. Trade/salvage can satisfy a hard guarantee only where the world recipe itself guarantees that access path.

AUTH-0094 is now the exact regional inventory seam C20 may consume for that selection/replanning work.

## Atmosphere / mobility

Aerodynamics4MC remains the single accepted atmosphere authority.

- aircraft consume retained Aeronautics compatibility;
- C6 proves retained Fowl Play hawk thermal SOAR behavior;
- C7 proves Reliable Gliders consume trusted shared lift;
- C8 closes Phantom-gated glider maintenance;
- C13 removes Elytra rocket propulsion while preserving fall-flying and ordinary fireworks.

No lane may introduce a second wind/thermal authority without reopening this contract.

## Ecology / fauna

Authorship/environment semantics determine niches/opportunity; Content maps retained species/population policy. Atmosphere may alter behavior but does not independently create population.

AUTH-0088/0089/0090/0091/0092 are evidence inputs only. SF-IMP-0080 is the current visible Minecraft ecology seam. Do not conflate opportunity with spawn/carrying-capacity/resource policy.

## Nether / dimension routing

- C10 proves live Nether `coordinate_scale=1.0`.
- C15 proves ordinary portal search/linking and missing-target creation consume the live 1:1 scale at non-origin coordinates.

Remaining work includes assembled contraption/passenger/cargo transfer, authored portal foothold safety, human terminal usability, and eventual permanent cosmology decision.

## Structures / civilization

Content defines gameplay role/reuse/history/service semantics; Authorship supplies site/environment evidence; Implementation owns realization modes and lifecycle. AUTH-0096 is the current accepted local surface-site evidence seam, not site selection or concrete structure proof. Progression-critical structures must remain obtainable and cannot rely on lucky structure rolls unless the world recipe guarantees them.

## Computing / automation

Computing is first-class infrastructure, **not** a first-flight prerequisite.

Accepted reuse boundaries:

- C9: CC:Tweaked + Create: Avionics substrate coexists with retained Create/Sable/Aeronautics;
- C14: real CraftOS reads retained avionics sensors and drives the upstream bounded throttle;
- C16: ordinary Wireless Modems are bounded infrastructure; Ender Modems are mature range/dimension bypasses;
- C17: ordinary GPS requires physical non-collinear hosts inside radio range; Ender-backed GPS inherits mature-bypass status;
- C18: stock turtle freight is retained; turtles require physical movement/fuel and do not supply arbitrary route ticking;
- C19: stock mining-turtle extraction is retained; physical deployment, fuel, ticking/loading, and cargo handling remain real constraints.

Do not build duplicate generic Skyforge networking, GPS, computer, or turtle systems while retained capabilities suffice.

Still Content-owned: farming/quarry-scale throughput, dedicated chunk-loader progression, Ender-storage combinations, GPS tower placement, autopilot progression, ore abundance, mature fleet automation, and final aircraft-vs-turtle economics.

## Music / Audio — MUS-0002 — ACCEPTED

MUS-0002 / PR #304 merged as `30b5202fec3ae07b0720b42eb8d7770c3c463843`, superseding MUS-0001 as the highest accepted Music / Audio repository-health boundary.

- Music / Audio owns composition, motifs/cues, orchestration, exact source/library identity, renders/masters, and listening gates;
- Music source integrity is now a Tier 0 deterministic gate in normal CI for relevant Music source/verifier changes; it does not replace human BBCSO listening or original-CWP/plugin-state recovery.
- Authorship owns world/environment semantics that may motivate musical state;
- Content owns gameplay/experience meaning that may request cue changes;
- Implementation owns Minecraft playback, transitions, adaptive runtime, persistence, and client integration.

No adaptive-music runtime/state-machine contract is currently accepted. Large WAV masters remain outside ordinary Git pending an explicit large-artifact strategy.

## Bellanca / mature utility aircraft

The Bellanca B0 must be a real Sable/Create Aeronautics contraption with useful power-off flight. Issue #237 / draft PR #240 tracks Portable Engine cutoff work. Assembled-Sable, save/reload, two-engine behavior, and human ergonomics remain unaccepted; C12 remains reserved for the executable B0.

## Handoff discipline

When one lane changes a contract another lane relies on, update this file with:

- changed invariant;
- owning lane;
- concrete issue/PR/doc;
- accepted / in-progress / proposed status.

Keep detailed history in lane-specific state/docs rather than expanding this file with logs.
