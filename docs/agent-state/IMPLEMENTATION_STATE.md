# Skyforge Implementation lane state

**Canonical lane:** IMPLEMENTATION  
**Updated:** 2026-09-06  
**Repository snapshot observed at migration:** `main@3f300346e598a6bc87a3a1468ec028bdee7eec0b`  
**Highest merged Implementation milestone:** **SF-IMP-0080**  
**SF-IMP-0080 merge:** PR #248, `e721b512d7aaf402d671b8d0c72db802f9bd0912`

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

### SF-IMP-0081 — first AUTH-0083 production morphology Minecraft carrier

Branch **`agent/sf-imp-0081-production-morphology-massif`** is the active Implementation work for the first issue #214 tranche.

The first carrier preserves exact AUTH-0083 member **`builtin-massif-small-seed-skyforge`**: canonical Skyforge seed, SMALL scale, built-in Massif provider, full bounded detail, full provider secondary morphology, and provider-neutral `SkyIslandMorphologySpecCompiler` compilation. Minecraft changes only the suspension elevation by an integer Y translation so the discrete shape is preserved inside the 1.21.1 build range.

Implementation work in this tranche:

- derive tight exact integer-voxel runtime bounds from the compiled morphology inside its certified provider support envelope, rather than paying whole-volume admission over the broad proof envelope;
- native-surface-adapt the exact carrier onto the accepted land-backed deterministic base;
- require whole-volume physical admission and zero deferred catch-up;
- intentionally omit caves/ecology/interior mutation so the first #214 review sees unobscured morphology;
- sample both top and underside boundaries against compiled exact-volume expectations;
- persist the world and require an ownership-only actual-client reopen with an identical sampled geometry digest;
- expose `above`, `approach`, `below`, and `orbit` guided stops for the first human #214 review.

No aesthetic thresholds are encoded. Automated acceptance is objective carrier correctness only; issue #214 remains a human visual/design gate.

## PROPOSED / priority order

1. **Production morphology era / #214.** Begin with one exact AUTH-0083 built-in specimen in Minecraft, then expand across Massif, Tableland, Spine, Basin, Lobed, scales/seeds, hybrids/provider composition, and AUTH-0084 regional contexts. Preserve reference member/context IDs in Minecraft evidence.
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
- Underside morphology is a first-class requirement; current family-aware secondary geography is more developed topside than underside.

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

Begin **SF-IMP-0081** as the first issue #214 Minecraft production-morphology tranche.

Use the exact AUTH-0083 member identity `builtin-massif-small-seed-skyforge` as the first executable specimen. Preserve its canonical seed, physical scale, full bounded detail, full secondary morphology, and provider-neutral `SkyIslandMorphologySpecCompiler` path; only translate the suspension elevation into Minecraft's build range.

The first tranche should:

- derive tight Minecraft integer-voxel support bounds from the compiled production morphology instead of paying physical-admission cost over a broad generic envelope;
- realize the specimen through native-surface adaptation, whole-volume physical admission, deferred stable-chunk catch-up, persistence, and a mutation-inert actual-client reopen;
- preserve a stable AUTH-0083 member ID in all evidence;
- provide guided **above / horizon-approach / below / orbit-underneath** player views for the first #214 human review;
- use machine gates only for objective realization failures (clipping, missing terrain, persistence mismatch, lifecycle violation). Do not invent aesthetic thresholds before correlating Minecraft views with AUTH-0083 diagnostics.

After that carrier proof is green, expand the same mechanism across the other four built-in families before moving to hybrids/provider axes and AUTH-0084 regional scenes.
