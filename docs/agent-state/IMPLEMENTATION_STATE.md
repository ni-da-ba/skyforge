# Skyforge Implementation lane state

**Canonical lane:** IMPLEMENTATION  
**Updated:** 2026-09-08
**Repository snapshot observed for current SF-IMP-0083 recomposition:** `main@13b453284dca429da3e78bb3d5d9fe3be9dee627`  
**Highest merged Implementation milestone:** **SF-IMP-0082**  
**SF-IMP-0082 merge:** PR #273, `3c48828924b0cf4ac7f1184c493d60ef605bf84a`

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


### SF-IMP-0082 — remaining built-in production morphology atlas

SF-IMP-0082 (PR #273, merge `3c48828924b0cf4ac7f1184c493d60ef605bf84a`) is **MERGED / ACCEPTED** and closes issue #269.

The accepted implementation generalizes the SF-IMP-0081 exact Minecraft carrier across the remaining AUTH-0083 SMALL / seed-skyforge built-ins:

- `builtin-tableland-small-seed-skyforge`;
- `builtin-spine-small-seed-skyforge`;
- `builtin-basin-small-seed-skyforge`;
- `builtin-lobed-small-seed-skyforge`.

The carrier preserves canonical member IDs, seed, SMALL scale, built-in provider identity, full bounded detail, full provider secondary morphology, and provider-neutral production compilation. Minecraft relocation is a proven pure integer-Y translation.

Accepted exact support / persistence evidence:

- Tableland: **440 / 440** admitted chunks, **1,244** sampled claims, **1,244 / 1,244** stored top/underside boundaries before and after actual-client reopen, zero height mismatches, digest **3828864144689447643**;
- Spine: **442 / 442** admitted chunks, **972** sampled claims, identical top/underside persistence, zero height mismatches, digest **724610389118588620**;
- Basin: **440 / 440** admitted chunks, **1,226** sampled claims, identical top/underside persistence, zero height mismatches, digest **8434138988482785295**;
- Lobed: **484 / 484** admitted chunks, **1,291** sampled claims, identical top/underside persistence, zero height mismatches, digest **12532817239739983832**;
- pending catch-up: **0** for every carrier;
- explicit acceptance warmup footprint equals each exact physical-admission footprint;
- repository build, performance characterization, technical showcase, ecology showcase, retained Massif, and retained current Content regressions all PASS on the synchronized merge head.

The tranche also exposed and fixed one concrete backend representation defect. A one-voxel Tableland fringe at **(-163, 160, -19)** could inherit an unsupported falling native surface block and disappear by reopen. Native surface adaptation now copies a `FallingBlock` only when the exact Skyforge materialization has solid support immediately below; otherwise the stable original Skyforge surface representation is retained. This preserves ADR-0041's invariant that native surface adaptation must not erase authoritative Skyforge occupancy without defining a broader beach/shore policy.

The required human #214 review passed. All four new families were coherent and visibly distinguishable in Minecraft; no silhouette or underside blocker was identified.

Two non-blocking tuning questions remain deliberately separate from carrier correctness:

- **#267:** the reviewed Massif has frequent rises/falls and may be too locally lumpy for comfortable traversal;
- **#283:** the reviewed Tableland remains closer to Massif than ideal because both SMALL / seed-skyforge specimens are highly hilly and vertically variant.

Do not tune from one specimen. The next accepted direction is to complete the AUTH-0083 built-in multi-seed/multi-scale matrix first.

### SF-IMP-0083 runtime-recovery support boundary — MERGED

PR #338 merged as `3f6e27bdef4d85a7b167db460e630a6ed12e28cc` without consuming a
milestone number or claiming SF-IMP-0083 acceptance. It extracted the reusable runtime delta from
superseded draft #285 onto current-main history:

- exact-column native occupancy survey preserving historical first-conflict identity;
- exact-footprint admission skipping for bounds-only chunks;
- bounded one-volume deferred catch-up with terrain-before-population ordering;
- admitted exact deferred-write fast path with conservative overlap fallback;
- neutral column classification/materialization hot-path reductions;
- focused unit/regression coverage.

Synchronized normal CI run 34179592334 passed before merge. Draft PR #285 was then closed unmerged as
superseded rather than carrying its 53-commit synchronization history forward.

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

### SF-IMP-0083 — AUTH-0083 built-in seed/scale Minecraft matrix

**Machine status:** PASS — human #214/#267/#283 review required before acceptance/merge.

Issue **#284** remains the active Implementation milestone. The original four-member-per-family
carrier draft #285 is retired. Its Tableland characterization run 34171538516 enabled 1,886 exact
warmup chunks in a 1,616-block-high carrier and then emitted repeated `OutOfMemoryError` failures
before producing `prepare.properties`. This is classified as a carrier-resource failure, not a
morphology correctness failure.

The recomposed branch is `impl/sf-imp-0083-representative-carriers`.

Tier 0 remains exhaustive for all **20** remaining AUTH-0083 built-ins: exact member/family/seed/scale
identity, deterministic provider-neutral compilation, tight support, occupied chunk footprint,
translation/build-range feasibility, and cheap diagnostics.

Tier 2 lifecycle evidence is representative under the canonical validation policy. The initial seven
risk representatives are:

- Massif MEDIUM / seed-skyforge;
- Tableland MEDIUM / seed-skyforge;
- Spine MEDIUM / seed-min;
- Basin MEDIUM / seed-zero;
- Lobed MEDIUM / seed-skyforge;
- Massif LARGE / seed-skyforge;
- Spine LARGE / seed-skyforge.

The replacement Minecraft carrier realizes **one exact member per world**, uses min Y 320 / height
544, translates exact support to begin at Y=336, warms exact occupied chunks only, and uses
nonblocking explicit warmup rather than serial synchronous `getChunk` forcing. Cave/ecology/interior
mutation remains absent so the carrier isolates morphology realization.

Tableland MEDIUM / seed-skyforge is the first deliberate full-runtime case because it reproduces the
family/context that exposed the retired carrier's memory pathology. Diagnostic run 34184041079
reached complete sampled geometry (3,192/3,192 top/underside boundaries, matching exterior air,
zero height mismatches) before an inherited grass>0 predicate stopped the case. After that predicate
was correctly narrowed to stable land substrate + exact geometry, run 34185431565 exposed a later
real resource failure: Java heap exhaustion during admitted deferred exact-solid realization before
the 862-chunk radius-3 warmup completed. The representative failure therefore widened the carrier
resource class as required.

The next bounded correction does not add heap or weaken lifecycle evidence. The acceptance harness
now permits an explicit ticket radius while retaining radius 3 as the historical default; SF-IMP-0083
uses radius 0 because its proof needs target chunks held at FULL status for getChunkNow/deferred
mutation, not an entity-ticking halo around every one of 862 targets. Run 34186680014 then passed
Tableland preparation under radius 0 with the exact 862-chunk footprint and no heap failure. Its
actual-client reopen did not start the integrated server: QuickPlay stalled while the shared
development resource set parsed the C1 Create Addition recipe even though Create Addition was absent
from this stripped morphology client. The development-only recipe is now guarded by NeoForge's
mod-loaded condition. Its retained-stack behavior is unchanged when Create Addition is present, and
the isolated morphology client can ignore the unrelated compatibility override.

Widened representative run 34188436241 then separated two independent remaining carrier
problems. Basin MEDIUM / seed-zero and Spine LARGE / seed-skyforge exhausted heap while
`ThreadedLevelLightEngine.checkBlock` work accumulated during slow deferred stable-chunk catch-up;
the authoritative exact geometry was not rejected. The accepted SF-IMP-0056 side effects remain
required. Follow-up Basin evidence on run 34189778665 showed that one end-of-chunk kick was
insufficient and, critically, the surviving OOM stack moved inside
`LevelChunk.setBlockState -> ThreadedLevelLightEngine.checkBlock`. Stable `LevelChunk#setBlockState`
already owns the required light-engine notification in Minecraft 1.21.1, so the deferred lifecycle
now removes its redundant second `checkBlock`, retains the explicit `blockChanged` broadcast, and
calls `tryScheduleUpdate()` every 256 changed blocks plus once at scope close. This preserves one
native lighting notification per relevant stable-chunk write while preventing a single slow chunk
from starving the asynchronous light executor.

The same widened run showed Massif MEDIUM, Tableland MEDIUM, Spine MEDIUM, and Lobed MEDIUM
preparing successfully but stopping on the same 360-second pre-integrated-server QuickPlay boundary.
The development-only 544-high dimension is an experimental world configuration, so actual-client
acceptance now recognizes only Minecraft's `BackupConfirmScreen` and presses the exact
`selectWorld.backupJoinSkipButton` ("I Know What I'm Doing!") action before continuing the unchanged
ownership-only persistence proof. The client records whether this expected warning was acknowledged
and reports its last screen class on timeout.

Post-fix branch evidence on `b3195c1e554b39f3595bec22122831525aae5238` closes both diagnosed
machine defects before final-main recomposition. Normal CI 34190278699 and retained C21 A/B
34190278674 passed. Basin MEDIUM / seed-zero run 34191320102 passed preparation plus actual-client
reopen with 888 / 888 exact footprint chunks, 3,331 / 3,331 sampled top and underside boundaries,
zero height mismatches, and stable digest 13330754608028663149. Massif MEDIUM / seed-skyforge run
34191688813 passed with 969 / 969 chunks, 3,591 / 3,591 boundaries, zero mismatches, and stable digest
12718455444228866863. Massif LARGE / seed-skyforge run 34191720235 passed with 2,124 / 2,124 chunks,
8,088 / 8,088 boundaries, zero mismatches, and stable digest 1731929346833003588. None reproduced
the earlier heap failure; all actual-client viewers acknowledged the expected experimental-world
warning and reproduced the persisted geometry. Final synchronized candidate `05bd8cd9c9a1b26e7f196c633e07a93a151d79e5` passed ordinary CI
34214944601 plus the current-main C11, C21, Portable Engine cutoff, and Portable Engine persistence
regressions. Final representative run 34215626713 then passed **all seven** approved Tier 2 cases
through preparation and persisted actual-client reopen: Massif MEDIUM / seed-skyforge, Tableland
MEDIUM / seed-skyforge, Spine MEDIUM / seed-min, Basin MEDIUM / seed-zero, Lobed MEDIUM /
seed-skyforge, Massif LARGE / seed-skyforge, and Spine LARGE / seed-skyforge.

Main subsequently advanced only through the accepted Sable Portable Engine cutoff proof. Its overlap
with this PR is limited to one additional ModDev run block in `build.gradle.kts` and one opt-in
bootstrap hook in `SkyforgeNeoForge1211Mod`; no SF-IMP-0083 carrier, generator, mutation, viewer,
contract, or dependency changed. Under the canonical evidence-portability rule, run 34215626713
remains merge-ready expensive evidence after recomposition onto `main@13b453284...`.

Final code head `fb37ad80cad6f562aa7679d3f39c73ed7559a1f2` passed repository CI
**34218264488**, C11 First Flight Recipe Runtime **34218264640**, C21 Create Resource Authority
**34218264473**, Portable Engine Cutoff **34218264496**, Portable Engine Cutoff Persistence
**34218264510**, and Sable Portable Engine Cutoff **34218264658**. The automated acceptance boundary
is therefore closed. SF-IMP-0083 is **machine-ready but not yet accepted**; the only remaining
milestone gate is human #214/#267/#283 morphology review.

Preserve exact AUTH-0083 IDs/providers and objective admission/persistence/digest gates. Human #214,
#267, and #283 remain qualitative gates; no machine diagnostic is an aesthetic pass/fail threshold.

## PROPOSED / priority order

1. **Production morphology era / #214.** SF-IMP-0081/0082 have accepted all five SMALL / seed-skyforge built-in carriers. SF-IMP-0083 / #284 now completes the remaining 20 built-in AUTH-0083 seed/scale specimens before hybrids/provider composition and AUTH-0084 regional contexts. Preserve reference member/context IDs in Minecraft evidence.
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
- Underside morphology is a first-class requirement; the SMALL / seed-skyforge review passed across all five built-in families, but multiple seeds/scales must still test whether primary+detail underside vocabulary remains sufficient.
- Issue #267 tracks a non-blocking Massif traversal-quality concern: repeated local rises/falls may be too lumpy for comfortable on-foot exploration. Issue #283 separately tracks Tableland-vs-Massif family separation. Do not retune either family from one specimen; SF-IMP-0083 provides the required multi-seed/multi-scale evidence.
- Retired SF-IMP-0083 draft #285 demonstrated that four large vertically stacked morphology volumes in one 1,616-high carrier can exhaust GitHub-runner memory. Do not rerun that packaging. Use one exact member per short review world and widen only on information-bearing failures.

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
:skyforge-neoforge-1211:productionMorphologySeedScaleTablelandPrepareVerify -PskyforgeProductionMorphologyVariant=medium-seed-skyforge
:skyforge-neoforge-1211:productionMorphologySeedScaleTablelandViewerVerify -PskyforgeProductionMorphologyVariant=medium-seed-skyforge
```

Use `--no-configuration-cache` for the showcase/dev runs where the project marks ModDev orchestration incompatible with configuration cache.

Do not merge behavioral worldgen changes on a stale integration base: verify exact PR head/base and rerun current-main gates when `main` advances.

## MANUAL VERIFICATION REQUIRED

### #214 — production morphology visual atlas

This becomes the principal morphology-quality gate after representative production families reach Minecraft. Required review includes distant silhouette, above/top-down, rim/approach, section, below/underside, and actual flight around/beneath the island.

Do not declare production morphology aesthetically accepted before this human gate.

## Immediate recommended next work

Continue **SF-IMP-0083 / issue #284** on the recomposed single-member carrier.

Next acceptance sequence:

1. perform the human #214 review on the seven representative Minecraft carriers;
2. explicitly classify #267 Massif traversal cadence across MEDIUM and LARGE;
3. explicitly classify #283 Massif-vs-Tableland family separation at MEDIUM;
4. if the review passes without evidence-backed tuning changes, record SF-IMP-0083 acceptance and
   merge PR #358;
5. if review exposes a concrete morphology defect, keep the machine carrier boundary accepted as
   evidence and open/continue the narrow tuning issue rather than reopening solved runtime hazards.

After carrier/lifecycle risk and the human morphology gate are retired, prefer the next major
production-world integration over exhaustive equivalent lifecycle repetition. Current cross-lane
state also hands C25/C26 petroleum source/depletion/pumpjack integration to Implementation; that
handoff is queued behind the active SF-IMP-0083 human gate rather than folded into this morphology PR.
