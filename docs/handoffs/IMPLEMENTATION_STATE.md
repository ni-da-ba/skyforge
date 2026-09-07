# Skyforge Implementation lane state

**Canonical lane:** IMPLEMENTATION  
**Updated:** 2026-09-06  
**Repository snapshot observed at migration:** `main@3f300346e598a6bc87a3a1468ec028bdee7eec0b`  
**Highest merged Implementation milestone:** **SF-IMP-0079**  
**SF-IMP-0079 merge:** PR #236, `91da8370a9c8078758b355a7ca4bd5f66531df6b`

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
- AUTH-0086 visible-hydrology intent should precede authored Minecraft waterfall/channel realization.
- Content structure/progression requirements should use generic realization contracts.

## IN PROGRESS

### SF-IMP-0080 — issue #194 legible showcase ecology

Active branch: `agent/sf-imp-0080-showcase-ecology`  
Current integration base at tranche start: `main@01bde4fb96860d8ccfd17b4e6f9d9459c0e827fc`

Goal: make accepted exact-volume native ecology visible to a human reviewer without turning the compact technical showcase into fake production morphology.

Current implementation direction:

- development-only `skyforge:showcase_land` world preset keeps the same `skyforge:noise_overlay` generator but uses fixed plains as the base native surface-representation source;
- showcase mode maps the lower exact volume to forest and the upper exact volume to taiga;
- ordinary SF-IMP-0069/performance fixtures remain on their historical development preset and biome routing;
- showcase preparation must end with nonzero grass, soil, logs, leaves, and surface plants on both exact volumes;
- full admission/cave/interior/fluid/persistence/client-reopen gates remain authoritative.

Status is **IN PROGRESS**, not accepted. Automated evidence must pass and the human ecology gate below is still required.

## PROPOSED / priority order

1. Complete and human-review **SF-IMP-0080 / #194**.
2. **Production morphology era.** Route representative Massif, Tableland, Spine, Basin, Lobed instances through the same physical-admission, caves, population, persistence, and performance pipeline.
3. Add representative hybrids/provider composition, secondary morphology, bounded local detail, and regional/cluster composition.
4. Connect authored materials/geology and then authored hydrology to concrete Minecraft realization.
5. Reintegrate structures into the newest exact-volume lifecycle across surface, embedded, cliff/underside, detached, settlement/network, and structure-seeded modes.
6. Converge on the deterministic Bootstrap Province vertical slice.

Deprioritized performance follow-up: issue #219 (canonical nearest-first surface discovery) is valid but should only be revived if realistic-scale profiling shows surface discovery is again material.

## Known hazards / technical debt

- The compact current showcase is a technical specimen and visually poor; do not make it the de facto production morphology.
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
:skyforge-neoforge-1211:launchShowcase
```

Use `--no-configuration-cache` for the showcase/dev runs where the project marks ModDev orchestration incompatible with configuration cache.

Do not merge behavioral worldgen changes on a stale integration base: verify exact PR head/base and rerun current-main gates when `main` advances.

## MANUAL VERIFICATION REQUIRED

### SF-IMP-0080 / #194 — ecology legibility

After automated showcase preparation and actual-client reopen pass, run `:skyforge-neoforge-1211:launchShowcase` and inspect `lower_surface` and `upper_surface`.

Human reviewer must visibly confirm:

- grass/soil on both exact volumes;
- persistent trees/foliage and surface plants on both;
- a meaningful lower-forest versus upper-taiga distinction;
- no obvious floating/unsupported vegetation or new persistence defect.

Machine success counters are insufficient. **Do not merge/accept SF-IMP-0080 before this gate.**

### #214 — production morphology visual atlas

This becomes the principal morphology-quality gate after representative production families reach Minecraft. Required review includes distant silhouette, above/top-down, rim/approach, section, below/underside, and actual flight around/beneath the island.

Do not declare production morphology aesthetically accepted before this human gate.

## Immediate recommended next work

Finish SF-IMP-0080 automated CI/showcase acceptance, then request the #194 human-eye ecology review. If accepted, merge SF-IMP-0080, update this state to the new merged milestone, and begin the production morphology integration era. Explicitly flag the first #214 human-eye morphology review tranche when representative production families are Minecraft-visible.
