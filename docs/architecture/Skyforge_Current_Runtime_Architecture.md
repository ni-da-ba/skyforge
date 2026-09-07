# Skyforge Current Runtime Architecture

**Snapshot:** 2026-09-06 (America/Chicago)  
**Accepted Minecraft integration through:** SF-IMP-0079  
**Accepted branch:** `main`  
**Accepted integration merge:** `91da8370a9c8078758b355a7ca4bd5f66531df6b`  
**Current Minecraft follow-on:** SF-IMP-0080 / issue #194 — human-legible land-biome ecology showcase (draft; manual gate required)  
**Parallel lanes:** backend-neutral Authorship through AUTH-0085 with AUTH-0086 in progress; Content / Experience through C10

This document is the concise current-state runtime architecture and implementation handoff. Milestone acceptance records and ADRs remain authoritative for individual contracts.

## Executive state

Skyforge is a deterministic, backend-neutral procedural world-synthesis engine. Minecraft 1.21.1 through NeoForge 21.1 is the first realization backend, not the foundation of the world model.

The accepted Minecraft path can now:

- compile semantic island intent into deterministic finite suspended volumes;
- compose independent islands without collapsing vertically stacked X/Z regions into one global surface;
- query exact three-dimensional ownership through the backend-neutral world catalog;
- preserve ordinary Minecraft BASE_WORLD generation as a separate generation domain;
- realize Skyforge-owned solid terrain additively into real chunks;
- scope height and biome behavior to one exact island-owned operation;
- reuse live registered Minecraft biome vegetation inside exact volumes;
- coordinate native surface population idempotently per volume/chunk/phase;
- survey an entire finite Skyforge volume for native occupancy before destructive realization;
- reject conflicting volumes atomically without mutating the native conflict;
- admit clear volumes only after complete finite footprint evidence exists;
- defer catch-up until target chunks are already stable and loaded, without forcing future generation;
- preserve Minecraft native post-processing work while deferred population runs on stable chunks;
- synchronize deferred stable-chunk terrain mutations to lighting and tracking clients;
- persist client-visible exact-volume biome presentation;
- virtualize native underground ores, local modifications, carvers, underground decoration, fluid springs, and lakes into exact floating volumes;
- compose registry-native cave variation with authored required cave topology, native first and authored last;
- execute composed caves and post-cave native interior population as production exact-volume obligations;
- preserve generated-fluid provenance and exact-volume propagation fences across save/reload and actual-client reopen;
- bound scheduler and full-height pathologies through measured performance work from SF-IMP-0070 through SF-IMP-0077;
- apply floating-island-aware native interior placement policy and route cave-dependent multiface vegetation after composed caves.

The accepted runtime frontier is SF-IMP-0079. The immediate Implementation problem is now **player-legible realization rather than another ownership primitive**: issue #194 / draft SF-IMP-0080 must expose persistent forest/taiga land ecology in the showcase and pass a human visual gate without weakening the accepted production lifecycle. Production morphology integration, authored material/hydrology realization, and structure reintegration into the newest exact-volume lifecycle remain downstream work.

## Architectural ownership

Module ownership remains:

- `skyforge-kernel` — coordinates, field contracts, typed procedural graphs, canonical serialization, validation, deterministic reference evaluation.
- `skyforge-model` — backend-neutral semantic descriptors and validation.
- `skyforge-recipes` — deterministic compilation from semantic/geological intent to procedural graphs.
- `skyforge-world` — exact world volumes, placement, ownership, support/composition policy, and backend-neutral authored world semantics.
- `skyforge-reference` — deterministic corpora, sampled evidence, topology/morphology analysis, hashes and visual diagnostics.
- `skyforge-neoforge-1211` — Minecraft/NeoForge realization, registry translation, exact-volume lifecycle integration, development fixtures and packaging.

Dependency direction is strictly downstream:

```text
skyforge-kernel
      ^
skyforge-model
      ^
skyforge-recipes
      ^
skyforge-world
      ^
skyforge-neoforge-1211
```

Minecraft and NeoForge concepts may not leak upward into the neutral engine merely to simplify an adapter problem.

Guiding rule:

> Skyforge decides what bounded world should exist; a backend decides how to express that authored world using its own mature content and runtime machinery.

## Runtime domain model

Minecraft and Skyforge are treated as separate generation domains.

```text
Minecraft BASE_WORLD
    -> native terrain
    -> native structures
    -> native generation lifecycle

Skyforge island domain
    -> exact finite volume
    -> physical admission
    -> additive terrain realization
    -> exact-volume biome/population execution
    -> deferred stable-chunk lifecycle adaptation when necessary
```

BASE_WORLD generation does not globally see all Skyforge terrain as another highest surface. An explicit Skyforge operation may opt into one exact island domain.

The mature model must continue to support multiple independent domains at identical X/Z positions, for example:

```text
Y=400  Skyforge island A
Y=250  Skyforge island B
Y=70   Minecraft BASE_WORLD
```

No global column-level answer may collapse those domains together.

## Exact-volume realization

Normal additive composition uses:

```text
Skyforge solid -> write resolved Skyforge BlockState
Skyforge AIR   -> preserve existing backend state
```

AIR means absence of Skyforge ownership, not permission to erase Minecraft terrain.

A planned volume may not write until physical admission reaches a terminal result.

## SF-IMP-0056 — physical volume admission

Acceptance record: [`SF-IMP-0056-physical-admission-acceptance.md`](../reviews/SF-IMP-0056-physical-admission-acceptance.md)

SF-IMP-0056 separated two previously conflated concerns:

```text
generation-domain isolation != physical occupancy compatibility
```

The accepted lifecycle is:

```text
PLANNED -> ADMITTED
PLANNED -> REJECTED
```

Properties:

- evidence is collected only for Skyforge-owned solid coordinates;
- any detected native occupancy conflict rejects the whole planned volume;
- rejection is terminal and leaves the native conflict unchanged;
- admission occurs only after every required chunk in the finite footprint has reported evidence;
- unresolved volumes fail closed and may not partially realize;
- deferred realization stores immutable work descriptions rather than retaining mutable generation chunks/regions;
- catch-up uses `ServerChunkCache#getChunkNow` and therefore does not create generation tickets for unavailable chunks;
- native surface population begins only after physical admission.

Accepted runtime proof:

- lower volume rejected on native bedrock and preserved the conflict;
- upper volume admitted after 25 / 25 required chunks;
- pending catch-up reached 0;
- upper terrain materialized;
- 21 / 21 actual surface-bearing chunks completed native population.

The 25 required admission chunks and 21 surface-population chunks are intentionally different sets: footprint corner chunks may be required for complete occupancy evidence while containing no exact island surface.

## SF-IMP-0057 — deferred native lifecycle semantics

Acceptance record: [`SF-IMP-0057-deferred-post-processing-acceptance.md`](../reviews/SF-IMP-0057-deferred-post-processing-acceptance.md)

SF-IMP-0057 repaired two lifecycle differences exposed by stable loaded-chunk catch-up.

### Native post-processing preservation

A promoted `LevelChunk` may legitimately retain native post-processing marks. Therefore deferred Skyforge population must not assume the queue is empty and must not consume unrelated Minecraft work.

Accepted behavior:

1. snapshot/detach pre-existing native post-processing marks for the touched chunk;
2. run one exact Skyforge population operation with an isolated live queue;
3. resolve Skyforge-created marks through `LevelChunk#postProcessGeneration()` while exact-volume scope remains active;
4. require the isolated Skyforge queue to drain;
5. restore the native snapshot unchanged;
6. on abnormal scope completion, clear unflushed Skyforge marks and restore native state before failing closed.

Direct `WorldGenRegion` population remains unchanged.

### Stable-chunk mutation synchronization

Low-level generation-style chunk writes are sufficient before a chunk becomes client-visible but are not lifecycle-equivalent after promotion to a stable loaded chunk.

Deferred catch-up therefore enters a narrow stable-chunk mutation scope that submits actual changed positions to Minecraft lighting and block-change synchronization. It does **not** replace the generation writer with ordinary gameplay-style block updates or introduce broad neighbor-update side effects.

This repaired the observed state in which authoritative server collision existed while a client rendered part of the island as stale/invisible/dark.

## Native population scope

Native population is now split by lifecycle role rather than limited to one surface phase.

- pre-cave surface ecology executes ordinary biome `VEGETAL_DECORATION` features;
- post-cave interior population executes `LAKES -> LOCAL_MODIFICATIONS -> UNDERGROUND_ORES -> UNDERGROUND_DECORATION -> FLUID_SPRINGS`;
- cave-dependent multiface vegetation is routed from the biome's original `VEGETAL_DECORATION` list into a final post-cave pass while preserving its original occurrence ordinal.

Skyforge resolves each exact-volume operation to a final-registry Minecraft biome and invokes registered native content through Minecraft machinery; phase-specific read/write policies adapt that content to floating exact volumes without copying feature definitions.

The original SF-IMP-0057 surface-only boundary has since expanded materially:

- SF-IMP-0058 persisted client-visible exact-volume biome presentation;
- SF-IMP-0059/0060 virtualized native underground placement and local modifications;
- SF-IMP-0061/0062 added exact-volume native carvers and underground decoration;
- SF-IMP-0063/0064 admitted fluid springs and whole-footprint native lakes with exact-volume fencing;
- SF-IMP-0065/0066 realized sealed and exterior-connected authored cave volumes;
- SF-IMP-0067 established native-first/authored-last cave union semantics;
- SF-IMP-0068 productionized composed caves as admitted-volume obligations;
- SF-IMP-0069 productionized ordered post-cave native interior population;
- SF-IMP-0070 through 0077 measured and removed scheduler/full-height pathologies without changing world meaning;
- SF-IMP-0078/0079 constrained floating-island interior plausibility and routed cave-dependent multiface vegetation after caves.

Still downstream are concrete authored hydrology/material mappings, representative production morphology through the newest lifecycle, and full structure/civilization reintegration into that lifecycle.

## Biome semantics and future authorship

The current Minecraft bridge is transitional: it can make an exact island population operation execute against a selected registered biome, but Minecraft biome identity is not yet the upstream semantic source of an island.

The intended direction is:

```text
Skyforge island identity
    -> authored environmental fields
    -> local ecological classification
    -> backend biome expression
    -> Minecraft registered biome / native content
```

A sufficiently large island should ultimately behave as a bounded miniature world and may contain several real vanilla/modded biome expressions, structures, caves, water systems and other local-world content where spatial support permits.

The parallel `auth/*` lane is developing those upstream backend-neutral semantics independently. The Minecraft implementation lane should consume them later through an explicit contract rather than duplicating or constraining them with Minecraft-specific ontology.

SF-IMP-0058 has since accepted persistent client-visible exact-volume biome presentation. Authorship remains the upstream source of meaning: AUTH-0085 is merged, and AUTH-0086 is currently defining backend-neutral visible-water realization intent that Implementation should consume rather than duplicate.

## Current Implementation boundary — issue #194 / SF-IMP-0080

Issue #52 is closed and is no longer the active frontier. Earlier structure-support and terrain-accommodation work remains accepted, but structures still need deliberate reintegration into the newest admission/cave/population lifecycle before they are treated as part of the production composition path.

The immediate human-facing dependency is issue #194:

> A reviewer must be able to see persistent land-biome ecology—soil/grass, trees, foliage, surface plants, and a meaningful biome distinction—in the integrated saved-world showcase.

Draft SF-IMP-0080 / PR #248 is the current implementation vehicle. Its intended scope is narrow:

- preserve the accepted stacked SF-IMP-0069 production fixture and exact-volume lifecycle;
- use a development-only land-bearing showcase preset rather than arbitrary ocean/gravel base presentation;
- present lower forest and upper taiga exact volumes;
- require machine evidence for grass/soil/logs/leaves/surface plants in both volumes;
- retain cave, interior-population, fluid-fencing, persistence, and actual-client reopen gates;
- require a human-eye review before #194 is accepted.

AUDIT-0002 observed that #248 was opened before the canonical agent-state consolidation and still targets the removed historical path `docs/handoffs/IMPLEMENTATION_STATE.md`. It must synchronize with current `main` and write lane state under `docs/agent-state/IMPLEMENTATION_STATE.md` before acceptance.

Passing #194 does **not** accept production morphology aesthetics. Issue #214 remains the later visual atlas gate for family identity, rim/approach views, underside quality, and actual flight around/beneath production islands.

## Non-regression rules

- BASE_WORLD generation remains observationally isolated from Skyforge unless an explicit operation owns a Skyforge domain.
- Exact ownership is three-dimensional, not column-global.
- PLANNED physical volumes fail closed.
- REJECTED volumes leave no destructive terrain/population work behind.
- ADMITTED deferred catch-up never forces unavailable chunks.
- Native population never begins before admission when the physical-admission stage is installed.
- Deferred population preserves unrelated native post-processing state.
- Stable-chunk deferred writes synchronize lighting/client state without changing normal generation writer semantics.
- Population replay remains idempotent.
- Final cave topology preserves native variation while guaranteeing authored AUTH-0030-positive AIR.
- Native interior population waits for whole-volume composed-cave completion and preserves its authorized phase order.
- Cave-dependent multiface vegetation is routed post-cave without reseeding unrelated biome features.
- Generated fluids retain provenance and may not cross their accepted owner-domain/interior-shell policy after reload.
- Performance optimizations may change work partitioning or bounded scan domains only when deterministic ownership/lifecycle evidence remains equivalent.
- Mutable generation chunks/regions are not retained as long-lived deferred work.
- Backend-neutral modules remain free of Minecraft/NeoForge APIs.
- Deterministic identity, exact volume ownership and canonical evidence behavior remain stable unless an explicit versioned contract changes them.

## Next implementation procedure

The Implementation lane should continue from current accepted `main`, not from this document's historical snapshots.

1. resynchronize the active SF-IMP-0080 branch with current `main` and the canonical `docs/agent-state/` namespace;
2. repair the current showcase-acceptance failure on that synchronized head without weakening the existing stacked/persistence gates;
3. rerun exact-head CI, SF-IMP-0070 characterization, showcase acceptance, and any focused SF-IMP-0080 ecology checks;
4. run the required human-eye `launchShowcase` review for lower forest / upper taiga land-biome legibility;
5. only then accept/merge SF-IMP-0080 and close #194;
6. after #194, move representative production morphology families through the same exact-volume lifecycle and expose the first #214 visual-atlas tranche;
7. consume AUTH-0086 visible-water intent before inventing authored Minecraft waterfall/channel placement;
8. reintegrate accepted structure realization modes into the newest production lifecycle as progression/civilization work requires them.

The architectural objective is no longer to prove that exact-volume lifecycle machinery exists. It is to carry richer authored world meaning through that machinery without sacrificing exact ownership, determinism, persistence, native-content reuse, or the floating-world plausibility constraints established through SF-IMP-0079.
