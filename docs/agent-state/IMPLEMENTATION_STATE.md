# Skyforge Implementation lane state

**Canonical lane:** IMPLEMENTATION  
**Updated:** 2026-09-06  
**Repository snapshot observed at migration:** `main@3f300346e598a6bc87a3a1468ec028bdee7eec0b`  
**Highest merged Implementation milestone:** **SF-IMP-0081**  
**SF-IMP-0081 merge:** PR #265, `bac972eb7e8d772a250d292602e109428d06514a`

Always verify current `main` before starting work; the snapshot above records the migration point, not a claim that other lanes stopped advancing afterward.

## MERGED / ACCEPTED

The production Minecraft/NeoForge lifecycle has accepted support for:

- adapter/runtime architecture and native surface adaptation;
- exact 3-D terrain ownership and vertically stacked isolation;
- native surface/biome population and idempotent lifecycle;
- native structure candidate admission, support/accommodation, and piece-aware footprints;
- whole-volume physical admission with fail-closed PLANNED/REJECTED behavior;
- non-forcing deferred realization from immutable evidence;
- native + authored cave composition;
- production post-cave interior population: lakes, local modifications, ores, underground decoration, fluid springs;
- generated-fluid provenance/fencing, save/reload, and actual-client reopen;
- current-capability showcase generation and viewer acceptance;
- bounded performance convergence through SF-IMP-0077;
- floating-island native-feature plausibility through SF-IMP-0078/0079.

### Recent accepted performance convergence

SF-IMP-0070 through 0077 established measurement and removed major pathological costs:

- bounded multi-quantum cave catch-up;
- non-candidate terrain fast path;
- exact-volume Y-bounded admission scans;
- admission-aware elimination of doomed direct projection;
- Y-bounded deferred materialization;
- Y-bounded exact first-free/surface queries;
- deferred realization subphase profiling.

The current policy is **do not continue local micro-optimization merely because more milliseconds are available**. Re-profile at realistic exploration scale or when a clear architectural pathology appears.

### Native feature quality: SF-IMP-0078 / 0079

SF-IMP-0078 (PR #228, merge `6e9a5ac87ac1e3599d0598afaab2ce3d530eaf65`) introduced floating-island-aware underground semantics:

- exterior-hidden reads for sensitive underground phases are virtualized as an inert barrier;
- FLUID_SPRINGS use an interior-shell policy while lakes retain broader OWNER_DOMAIN semantics;
- spring descendants cannot escape through the floating-island shell;
- glow-lichen support/exterior-shell plausibility is checked in final world state.

SF-IMP-0079 (PR #236) found the remaining glow-lichen root cause was lifecycle ordering, not density: Minecraft registers multiface growth in VEGETAL_DECORATION, which Skyforge had executed pre-cave. MultifaceGrowthFeature instances are now routed post-cave while preserving original biome feature-list occurrence ordinals.

Accepted stacked evidence recorded:

- final glow lichen lower/upper: 134 / 80;
- max per chunk: 7 / 6;
- unsupported: 0 / 0;
- exterior-shell lichen: 0 / 0;
- pre-cave glow attempts: 0 / 0;
- spring-derived boundary fluid cells: 0 / 0;
- CI, performance, stacked isolation, persistence, and actual-client reopen PASS.

Issue #193 is closed. No arbitrary lichen density cap was required.


### SF-IMP-0080 — legible persistent land-biome ecology

SF-IMP-0080 (PR #248, merge `e721b512d7aaf402d671b8d0c72db802f9bd0912`) is **MERGED / ACCEPTED** and closes issue #194.

The accepted implementation keeps the compact river/dripstone showcase as the technical cave/interior authority and adds a separate human-facing forest/taiga ecology world. The accepted path uses native-surface adaptation, whole-volume physical admission, deferred stable-chunk catch-up, exact-volume native surface population, durable biome presentation, persistence, and mutation-inert actual-client reopen.

Final machine evidence on the accepted land-backed fixture included:

- lower FOREST / upper TAIGA physical admission: **361 / 361 observed and required chunks each**, both ADMITTED;
- lower forest: **172** populated chunks, **1376** feature attempts, **339** successes, **37,684** substrate samples, **27,818** grass samples, **8,224** logs, **74,908** leaves, and **1,881** surface plants;
- upper taiga: **178** populated chunks, **1602** feature attempts, **402** successes, **39,077** substrate samples, **28,941** grass samples, **10,307** logs, **77,023** leaves, and **1,744** surface plants;
- distinct forest/taiga native feature identity: true;
- pending terrain catch-up: **0**;
- pending biome presentation: **0**;
- durable biome presentation: true;
- mutation-inert actual-client reopen: PASS;
- repository CI, existing showcase acceptance, dedicated ecology acceptance, and SF-IMP-0070 performance characterization: PASS on the synchronized integration head before merge.

The required human #194 gate explicitly passed. The reviewer reported that both ecosystems were clearly legible and visually strong, with plausible vegetation attachment, meaningful forest/taiga distinction, and correct persistence across reopen.

A separate non-blocking presentation limitation was observed: moving a short distance away from the terrain can return the client-visible biome to BASE_WORLD ambience. This matches the intentionally narrow SF-IMP-0058 immediate-surface quart contract and is tracked as **issue #261**; it does not reopen #194 or weaken SF-IMP-0080 ecology acceptance.


### SF-IMP-0081 — first exact AUTH-0083 production morphology Minecraft carrier

SF-IMP-0081 (PR #265, merge `bac972eb7e8d772a250d292602e109428d06514a`) is **MERGED / ACCEPTED** as the first issue #214 Minecraft morphology tranche.

The accepted carrier preserves exact AUTH-0083 member `builtin-massif-small-seed-skyforge`: canonical Skyforge seed, SMALL scale, built-in Massif provider, full bounded detail, full provider secondary morphology, and provider-neutral `SkyIslandMorphologySpecCompiler` compilation. Minecraft changes only suspension Y by an integer translation; the adapter proves the translated discrete support is a pure Y shift of the source specimen.

Accepted implementation evidence:

- translated suspension: **Y=216**;
- exact integer support bounds: **X -184..184, Y 96..301, Z -162..162**;
- exact support: **89,859** occupied integer X/Z columns within **281,961** certified scan columns;
- physical admission: **ADMITTED, 528 / 528 observed/required chunks**;
- pending catch-up: **0**;
- sampled top/underside claims: **1,400**;
- stored top / air above: **1,400 / 1,400**;
- stored underside / air below: **1,400 / 1,400**;
- exact height mismatches: **0**;
- land-top samples: **1,343**;
- grass-top samples: **1,014**;
- sampled top range: **Y 217..301**;
- deterministic sampled geometry digest: **11910236061391333683**;
- mutation-inert actual-client reopen reproduced the same samples and identical digest;
- exact synchronized repository build, current showcase, ecology, performance, retained Content/C16 wireless, and dedicated Massif acceptance all PASS.

The first #214 human review explicitly passed the carrier/morphology-system gate: the pure island morphology and underside looked strong and coherent in-engine. This does **not** close issue #214; the broader family/seed/scale/hybrid/regional atlas remains required.

A non-blocking gameplay-quality observation was recorded: this particular Massif felt notably lumpy, with frequent rises and falls that may not feel ideal for on-foot traversal. That is tracked separately as **issue #267** and should be evaluated across seeds/scales/families before retuning. It does not weaken SF-IMP-0081 acceptance.

## Architectural invariants

- BASE_WORLD generation and Skyforge exact ownership remain distinct domains.
- Physical admission is separate from generation-domain isolation.
- PLANNED exact volumes fail closed; REJECTED volumes do not mutate/populate.
- Exact-volume operations do not borrow stacked/foreign volume terrain.
- Deferred catch-up does not force arbitrary future chunks into generation.
- Mutable generation regions/chunks are not retained as durable deferred evidence.
- Population remains deterministic/idempotent and phase-ordered.
- Native fluids retain provenance and cannot cross unauthorized ownership/boundary policy.
- Minecraft/NeoForge types stay out of backend-neutral modules unless a genuinely neutral abstraction is established first.
- Native Minecraft behavior is reused where plausible, but floating-world topology requires explicit domain-aware read/write semantics.
- Vanilla springs are not authored waterfalls/hydrology.

## Current cross-lane dependencies

Read [CROSS_LANE_CONTRACTS.md](CROSS_LANE_CONTRACTS.md). Most important near-term dependencies:

- Authorship morphology stack must be brought through the production lifecycle rather than beautifying the compact showcase specimen.
- AUTH-0086 is now merged/accepted (PR #241, merge `a55e500c86f0baf910af809985956ac398742706`); authored Minecraft channel/waterfall realization may consume that intent contract when it reaches priority, without inventing a second planner.
- Content structure/progression requirements should use generic realization contracts.

## IN PROGRESS

### SF-IMP-0082 — remaining built-in production morphology atlas

Issue **#269** is the active next Implementation milestone.

Generalize SF-IMP-0081's accepted exact-carrier architecture across the remaining AUTH-0083 SMALL / seed-skyforge built-in members:

- `builtin-tableland-small-seed-skyforge`;
- `builtin-spine-small-seed-skyforge`;
- `builtin-basin-small-seed-skyforge`;
- `builtin-lobed-small-seed-skyforge`.

Do not copy-paste four family-specific runtimes. Prefer one reusable carrier fixture/runtime/viewer keyed by exact AUTH-0083 member identity, retaining tight integer support derivation, whole-volume admission, persistence/reopen digest proof, and guided above/approach/below/orbit review.

The #214 human gate remains required for the four-family tranche. Gameplay/traversal observations should be recorded independently from morphological correctness.

## PROPOSED / priority order

1. **Production morphology era / #214.** SF-IMP-0081 has accepted the first exact Massif carrier. SF-IMP-0082 / #269 now expands the same mechanism across Tableland, Spine, Basin, and Lobed; after that, continue to multiple seeds/scales, hybrids/provider composition, and AUTH-0084 regional contexts. Preserve reference member/context IDs in Minecraft evidence.
2. Add representative hybrids/provider composition, secondary morphology, bounded local detail, and regional/cluster composition through the current lifecycle.
3. Connect authored materials/geology and then authored hydrology to concrete Minecraft realization.
4. Reintegrate structures into the newest exact-volume lifecycle across surface, embedded, cliff/underside, detached, settlement/network, and structure-seeded modes.
5. Converge on the deterministic Bootstrap Province vertical slice.

Deprioritized performance follow-up: issue #219 (canonical nearest-first surface discovery) is valid but should only be revived if realistic-scale profiling shows surface discovery is again material.

## Known hazards / technical debt

- The compact current showcase is a technical cave/interior specimen and visually poor; do not make it the de facto ecology or production-morphology authority.
- SF-IMP-0080 established that changing the compact specimen's biome/base representation alone is insufficient for legible land ecology; its failed prototype produced soil but no grass/logs/leaves/plants. Reuse a topology that native land ecology can actually inhabit rather than increasing feature density.
- Issue #261 records a non-blocking flight/ambient-presentation limitation: exact-volume biome presentation currently ends close to the owned surface and may fall back to BASE_WORLD ambience a short distance away. Do not solve this by claiming whole native biome columns.
- Surface material mapping in technical fixtures can remain simple (for example generic dirt mantle); production geology/material roles are not yet fully realized.
- Native Minecraft feature assumptions are grounded in ordinary solid worlds; new phases must be audited for floating-domain read/write assumptions as they are introduced.
- README/current-runtime capability prose may lag the current SF-IMP boundary. This state file is canonical for lane status; architecture/review docs remain useful for the specific boundary they describe.
- Production performance has improved greatly in the torture fixture but still needs realistic exploration-scale validation later.
- Structure support was proved earlier but has not yet been fully reintegrated into the newest production exact-volume population lifecycle.
- Underside morphology is a first-class requirement; the first Massif human review passed strongly, but the remaining families must still test whether primary+detail underside vocabulary is sufficient.
- Issue #267 tracks a non-blocking traversal-quality concern from the first Massif: repeated local rises/falls may be too lumpy for comfortable on-foot exploration. Do not flatten the family from one specimen; evaluate across the broader atlas first.

## Verification procedures

Primary automated authorities:

- repository CI workflow;
- `SF-IMP-0070 Performance Characterization` workflow for regression/performance evidence;
- `Skyforge Showcase Acceptance` including persisted actual-client reopen;
- targeted unit tests for the modified lifecycle/domain policy.

Useful local Gradle entry points include:

```text
:skyforge-neoforge-1211:sfImp0070PerformanceVerify
:skyforge-neoforge-1211:showcaseViewerVerify
:skyforge-neoforge-1211:showcaseEcologyPrepareVerify
:skyforge-neoforge-1211:showcaseEcologyViewerVerify
:skyforge-neoforge-1211:launchShowcase
:skyforge-neoforge-1211:launchShowcaseEcology
```

Use `--no-configuration-cache` for the showcase/dev runs where the project marks ModDev orchestration incompatible with configuration cache.

Do not merge behavioral worldgen changes on a stale integration base: verify exact PR head/base and rerun current-main gates when `main` advances.

## MANUAL VERIFICATION REQUIRED

### #214 — production morphology visual atlas

This becomes the principal morphology-quality gate after representative production families reach Minecraft. Required review includes distant silhouette, above/top-down, rim/approach, section, below/underside, and actual flight around/beneath the island.

Do not declare production morphology aesthetically accepted before this human gate.

## Immediate recommended next work

Begin **SF-IMP-0082 / issue #269** from merged SF-IMP-0081.

Generalize the accepted first-carrier implementation rather than duplicating it. The next tranche should:

- express the remaining four AUTH-0083 SMALL / seed-skyforge built-in members through one reusable fixture/runtime abstraction;
- preserve exact member IDs, seeds, scale, provider enrichment, and pure integer-Y translation proof;
- derive tight exact integer support per member;
- prove whole-volume admission, zero catch-up, top/underside exactness, persistence, and mutation-inert actual-client reopen per family;
- expose family-selectable above / approach / below / orbit stops;
- keep machine gates objective and reserve aesthetic/family/traversal judgments for human #214 review;
- retain issue #267 as a gameplay tuning signal rather than mixing traversal comfort into morphology carrier correctness.

After the four remaining families pass, decide whether to widen the next tranche to multiple seeds/scales or address any family-specific morphology defect exposed by human review before moving to hybrids/provider axes and AUTH-0084 regional scenes.
