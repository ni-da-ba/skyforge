# Skyforge Current Runtime Architecture

**Snapshot:** 2026-09-06 (America/Chicago)  
**Accepted Minecraft integration through:** **SF-IMP-0079**  
**Accepted branch:** `main`  
**Current Implementation frontier:** issue **#194** — human-legible land-biome/ecology showcase  
**Later production morphology gate:** issue **#214**  
**Parallel lanes:** backend-neutral Authorship and Content / Experience; see `docs/agent-state/`

This document is the concise current-state runtime architecture. The canonical lane state and merged Git/test history determine the current frontier; milestone acceptance records remain authoritative for individual contracts.

## Executive state

Skyforge is a deterministic, backend-neutral procedural world-synthesis engine. Minecraft 1.21.1 through NeoForge 21.1 is the first realization backend, not the foundation of the world model.

The accepted Minecraft path can now:

- compile semantic island intent into deterministic finite suspended volumes;
- compose independent exact volumes without collapsing vertically stacked X/Z regions into one global surface;
- preserve ordinary Minecraft BASE_WORLD generation as a separate generation domain;
- survey whole finite volumes for physical admission before destructive realization;
- defer realization without forcing unavailable future chunks;
- preserve stable-chunk native post-processing, lighting, and client synchronization during deferred work;
- resolve exact-volume biome context and execute native surface ecology idempotently;
- compose native and authored caves inside exact ownership;
- execute production post-cave native interior phases including lakes, local modifications, ores, underground decoration, fluid springs, and routed cave-dependent vegetation;
- persist generated-fluid provenance and fence fluid descendants to their authorized ownership/boundary policy;
- save/reload and reopen the persisted showcase through an actual client;
- keep vertically stacked exact volumes isolated through terrain, cave, population, fluid, and lifecycle work;
- run the accepted technical showcase and performance characterization fixtures.

The active Implementation problem is no longer the old SF-IMP-0057 deferred-population boundary or issue #52 structure projection. Current `main` has accepted through SF-IMP-0079. The immediate frontier is **#194**, which must expose already-proven land-biome ecology in a visually legible persistent specimen so humans can judge soil/grass/trees/plants and biome distinction. Production morphology follows; **#214** remains the later human visual/flight-quality gate.

## Architectural ownership

Module ownership remains:

- `skyforge-kernel` — coordinates, field contracts, typed procedural graphs, canonical serialization, validation, deterministic reference evaluation;
- `skyforge-model` — backend-neutral semantic descriptors and validation;
- `skyforge-recipes` — deterministic compilation from semantic/geological intent to procedural graphs;
- `skyforge-world` — exact world volumes, placement, ownership, support/composition policy, and backend-neutral authored world semantics;
- `skyforge-reference` — deterministic corpora, sampled evidence, topology/morphology analysis, hashes, and visual diagnostics;
- `skyforge-neoforge-1211` — Minecraft/NeoForge realization, registry translation, exact-volume lifecycle integration, development fixtures, and packaging.

Dependency direction remains downstream toward the backend. Minecraft and NeoForge concepts may not leak into neutral modules merely to simplify adapter implementation.

Guiding rule:

> Skyforge decides what bounded world should exist; a backend decides how to express that authored world using its own mature content and runtime machinery.

## Runtime domain model

Minecraft BASE_WORLD and Skyforge exact volumes are separate generation domains.

```text
Minecraft BASE_WORLD
    -> native terrain / structures / generation lifecycle

Skyforge exact volume
    -> finite authored ownership
    -> physical admission
    -> terrain realization
    -> native-surface adaptation
    -> exact-volume biome/population
    -> composed caves
    -> post-cave interior population
    -> generated-fluid provenance/fencing
    -> persistence / stable-chunk synchronization
```

The model supports multiple independent domains at the same X/Z:

```text
Y=400  Skyforge volume A
Y=250  Skyforge volume B
Y=70   Minecraft BASE_WORLD
```

No column-global answer may silently substitute one of those domains for another.

## Exact-volume realization and physical admission

Normal additive terrain composition remains:

```text
Skyforge solid -> write resolved Skyforge BlockState
Skyforge AIR   -> preserve existing backend state
```

AIR means absence of Skyforge-owned solid terrain, not permission to erase BASE_WORLD state.

Physical admission remains a distinct finite-volume lifecycle:

```text
PLANNED -> ADMITTED
PLANNED -> REJECTED
```

Key invariants established at SF-IMP-0056 and retained through current `main`:

- evidence is collected over Skyforge-owned solids;
- conflicting native occupancy can reject the whole planned volume without destructive mutation;
- unresolved PLANNED volumes fail closed;
- admission waits for the required finite footprint evidence;
- deferred work stores immutable descriptions rather than mutable generation regions/chunks;
- catch-up uses already-loaded/stable chunks and does not force arbitrary future generation;
- population begins only for an admitted owner.

## Deferred lifecycle and persistence

SF-IMP-0057 established the stable-chunk lifecycle seam that later milestones continue to use:

- pre-existing native post-processing work is preserved rather than consumed by Skyforge;
- Skyforge-created post-processing is isolated and drained while exact-volume scope is active;
- abnormal completion restores native state and fails closed;
- stable-chunk low-level terrain changes are submitted to lighting and client block-change synchronization;
- direct `WorldGenRegion` behavior remains generation-oriented.

Later production lifecycle work extended this same model through caves, interior features, generated fluids, persistence, save/reload, and actual-client reopen. No newer milestone redefines stable chunks as ordinary gameplay block-placement operations.

## Exact-volume biome and surface ecology

Skyforge can execute registered Minecraft biome surface content against one exact owner while preserving three-dimensional isolation and deterministic/idempotent lifecycle keys.

The accepted surface path is no longer simply “run all VEGETAL_DECORATION before caves.” SF-IMP-0079 routes cave-dependent `MultifaceGrowthFeature` entries out of the pre-cave surface pass and into post-cave execution while preserving the original biome feature-list occurrence ordinal. Ordinary vegetation remains surface ecology and is not rerolled merely because multiface growth moves later.

The current #194 work is therefore a **presentation/legibility gate**, not proof that trees/plants can execute at all. That capability is already machine-accepted; the missing claim is that a persisted human-facing specimen makes the ecology visibly judgeable.

## Cave composition

The production lifecycle supports composition between native and authored cave systems within exact-volume ownership.

The important architecture is ordering and domain ownership rather than treating a floating island as an ordinary infinite underground column:

```text
admitted exact terrain
    -> native/authored cave composition
    -> final owned cave interior/exterior shell
    -> post-cave native interior phases
```

Exact-volume operations may not borrow terrain/interior support from a vertically stacked foreign owner.

## Post-cave native interior population

The accepted production interior lifecycle includes:

- lakes;
- local modifications;
- ores;
- underground decoration;
- fluid springs;
- cave-dependent multiface vegetation routed from VEGETAL_DECORATION after cave composition.

These phases reuse Minecraft/native feature machinery where possible, but their world view is constrained by Skyforge ownership and lifecycle semantics.

The implementation does **not** claim that every vanilla/mod feature assumption is automatically valid in floating topology. New phases still require explicit read/write-domain review.

## Floating-island feature plausibility — SF-IMP-0078/0079

Human showcase review exposed two topology/lifecycle pathologies: exposed spring-derived fluids and implausible/floating glow lichen.

SF-IMP-0078 added phase-aware floating-domain behavior:

- sensitive underground reads outside exact ownership are hidden behind an inert barrier rather than arbitrary AIR;
- FLUID_SPRINGS use an interior-shell boundary policy;
- lakes retain their broader owner-domain policy;
- spring descendants are provenance-tracked and fenced from leaking through the floating-island shell;
- final multiface growth must have valid support and remain outside the reserved exterior shell.

SF-IMP-0079 identified the remaining glow-lichen defect as lifecycle ordering. `MultifaceGrowthFeature` entries registered in `VEGETAL_DECORATION` are now routed after composed caves.

Accepted stacked evidence recorded by the Implementation lane includes:

```text
final glow lichen        lower 134 / upper 80
max per chunk            lower 7   / upper 6
unsupported              0 / 0
exterior-shell lichen    0 / 0
pre-cave glow attempts   0 / 0
spring boundary cells    0 / 0
```

Issue #193 is closed. No arbitrary global lichen-density cap was required.

## Generated-fluid ownership

Generated fluids are not treated as unowned vanilla side effects once they originate from a Skyforge-owned native phase.

The retained architecture records provenance and a boundary policy so descendants can continue inside the permitted domain while failing closed at unauthorized ownership boundaries. Lakes and springs intentionally have different boundary semantics.

This state persists across save/reload and remains part of the current showcase/persistence acceptance.

## Performance convergence — SF-IMP-0070 through 0077

Performance work followed a measurement-first sequence.

The accepted tranche:

- instrumented the production stacked fixture;
- removed avoidable non-candidate terrain projection;
- bounded physical-admission surveys to exact-volume Y envelopes;
- skipped direct projection while candidate owners remained merely PLANNED;
- bounded deferred materialization to authoritative volume Y ranges;
- bounded exact-volume first-free/surface height queries;
- decomposed remaining deferred realization subphase costs.

The resulting policy is deliberate:

> Do not continue local micro-optimization merely because another hot loop can be shortened.

Reopen issue #219 or similar work only if fresh realistic-scale profiling shows surface discovery or another component is materially limiting again.

## Structure realization

Native structure admission/support/accommodation and piece-aware footprints were accepted before the current cave/interior lifecycle tranche. Structure support remains a real capability, but full reintegration of every realization mode into the newest production exact-volume population lifecycle is still future work.

Current cross-lane realization modes include surface-supported, settlement/network, subsurface, cliff/underside, detached, and structure-seeded terrain. Content owns gameplay roles, Authorship owns site/environment semantics, and Implementation owns realization/lifecycle safety.

## Authorship handoff boundaries

The Minecraft implementation must consume backend-neutral authored meaning through explicit contracts rather than inventing parallel semantics.

Current relevant contracts include:

- Authorship through **AUTH-0086**: AUTH-0085 admits supplemental native WATER springs from authored cave + aquifer evidence, while AUTH-0086 projects accepted surface channels, retained waterbodies, cascades, waterfalls, and edge discharge into exact backend-neutral visible-water intents.

Vanilla fluid springs are not equivalent to authored channels, waterfalls, retained waterbodies, or surface hydrology. Implementation must consume AUTH-0086's accepted one-for-one intent plan and preserve its provenance/dry-margin semantics rather than create a second hydrology planner.

## Current showcase role

The compact showcase is a **technical current-capability fixture**. It exists to prove exact ownership, stacked isolation, lifecycle order, persistence, reopen behavior, and representative native-content execution.

It is not the production morphology target.

Current human-facing sequence:

1. **#194:** expose persistent legible land ecology without weakening production lifecycle semantics;
2. production representative Massif/Tableland/Spine/Basin/Lobed realization;
3. **#214:** judge silhouette, top/rim/underside quality, approach readability, and actual flight/orbit-underneath views;
4. only then promote production morphology aesthetics.

## Non-regression rules

- BASE_WORLD generation remains observationally isolated from Skyforge unless an explicit operation owns a Skyforge domain.
- Exact ownership is three-dimensional, not column-global.
- PLANNED physical volumes fail closed.
- REJECTED volumes leave no destructive terrain/population work behind.
- Exact-volume operations do not borrow stacked/foreign terrain.
- Deferred catch-up never forces unavailable chunks.
- Mutable generation chunks/regions are not retained as long-lived deferred evidence.
- Population is deterministic, idempotent, and phase ordered.
- Stable-chunk catch-up preserves unrelated native post-processing and synchronizes client-visible mutations.
- Native/generated fluids retain provenance and may not cross unauthorized ownership/boundary policy.
- Cave-dependent native vegetation executes only after usable cave support exists.
- Backend-neutral modules remain free of Minecraft/NeoForge APIs unless a genuinely neutral abstraction is first established.
- Machine correctness does not replace explicit human visual/play gates where the claim is aesthetic or experiential.

## Verification authority

Current Implementation verification authorities include:

- repository CI;
- `SF-IMP-0070 Performance Characterization` for retained performance/regression evidence;
- `Skyforge Showcase Acceptance`, including persisted actual-client reopen;
- targeted unit/runtime tests for the lifecycle/domain policy being changed.

Useful local entry points recorded by the lane state include:

```text
:skyforge-neoforge-1211:sfImp0070PerformanceVerify
:skyforge-neoforge-1211:showcaseViewerVerify
:skyforge-neoforge-1211:launchShowcase
```

Use `--no-configuration-cache` where the project's ModDev showcase/dev orchestration requires it.

## Next implementation procedure

Implementation should continue from current `main`, not from this architecture document's historical predecessors.

1. complete issue #194 with the smallest production-faithful change that exposes persistent legible land-biome ecology;
2. require machine acceptance plus the explicit human ecology-legibility gate before closing #194;
3. move representative production morphology families through the same admission/cave/population/persistence pipeline;
4. trigger the first #214 Minecraft/reference/flight review tranche rather than inventing aesthetic thresholds in code;
5. then connect authored materials/geology and authored hydrology to concrete Minecraft realization;
6. reintegrate structures into the newest lifecycle and converge with Content on the deterministic Bootstrap Province.

The target is not to make the compact technical fixture attractive. The target is to carry authored, visually convincing floating worlds through the already-proven exact-volume runtime without weakening ownership, lifecycle, persistence, or Minecraft reuse contracts.
