# Skyforge Current Runtime Architecture

**Snapshot:** 2026-09-02 (America/Chicago)  
**Accepted through:** SF-IMP-0055  
**Accepted branch:** `main`  
**Snapshot main SHA:** `35fcaaed28cbf0a40fb2c682a93cf226c380169d`  
**Active implementation:** SF-IMP-0056 / PR #63 / `agent/sf-imp-0056`  
**Active snapshot SHA:** `76b2c8cd72a608ad1c15950dfc998d085008fa88`

This document is the concise current-state architecture and next-agent handoff. ADRs and milestone acceptance records remain authoritative for individual accepted contracts. Anything described under **Active SF-IMP-0056** is implemented work in progress, not an accepted runtime guarantee.

## Executive state

Skyforge is a deterministic, backend-neutral procedural world-synthesis engine whose first concrete backend is Minecraft 1.21.1 through NeoForge 21.1.

At the current accepted boundary, Skyforge can:

- compile semantic island intent into deterministic finite suspended volumes;
- compose independent islands into groups and archipelagos without collapsing them into one density field;
- query those volumes through a backend-neutral world catalog;
- classify exact three-dimensional terrain ownership and structural terrain semantics;
- realize exact Skyforge-owned solid terrain into real Minecraft chunks;
- keep ordinary BASE_WORLD generation observationally isolated from Skyforge;
- expose an exact Skyforge volume to Minecraft height/biome logic only inside an explicit island-owned operation;
- reuse live registered Minecraft biome vegetation inside vertically stacked exact volumes;
- coordinate native surface population idempotently per exact volume/chunk/phase;
- preserve native world state wherever Skyforge does not own a solid coordinate.

The current unresolved production invariant is **physical three-dimensional admission**. Generation-domain isolation prevents Skyforge from influencing ordinary base-world generation, but two independently generated domains can still plan solids at the same coordinates. SF-IMP-0056 exists to prevent Skyforge from destructively realizing a planned volume until its complete native occupancy footprint has been observed and admitted.

## Architectural ownership

Accepted module ownership remains:

- `skyforge-kernel` — coordinates, typed procedural graph representation, field contracts, canonical serialization, validation and reference evaluation.
- `skyforge-model` — backend-neutral semantic descriptors and descriptor validation.
- `skyforge-recipes` — deterministic descriptor/provider/group/archipelago compilation and planning.
- `skyforge-world` — bounded runtime catalog, exact world volumes, spatial queries, terrain semantics, ownership and backend-neutral support/composition policy.
- `skyforge-reference` — fixed-seed evidence generation, deterministic sampling, topology/morphology metrics, hashes, visual review artifacts and golden-corpus verification.
- `skyforge-neoforge-1211` — Minecraft 1.21.1 / NeoForge 21.1 adapter, live registry/state translation, lifecycle integration, exact-volume biome/population bridges, development-client proof and packaging.

Dependency direction is deliberately downstream:

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

Minecraft and NeoForge APIs are forbidden from the backend-neutral modules. The repository verification gate enforces that boundary.

Guiding rule:

> Skyforge owns concepts necessary to express Skyforge. A backend owns concepts that exist only because of that backend. Shared abstractions are introduced only after concrete integration demonstrates a genuinely shared need.

## Accepted runtime flow through SF-IMP-0055

The current accepted runtime is best understood as two generation domains with an explicit ownership seam:

```text
semantic island intent
    -> deterministic morphology/provider compilation
    -> independent finite suspended volumes
    -> group / archipelago planning
    -> SkyIslandWorldCatalog

Minecraft BASE_WORLD generation
    -> native terrain
    -> native structures
    -> native surface construction / decoration
    -> completed native chunk state

explicit Skyforge island domain
    -> exact volume query / ownership
    -> additive solid realization
    -> exact-volume scoped biome bridge
    -> admitted native VEGETAL_DECORATION population
    -> idempotent per-volume / per-chunk lifecycle completion
```

The critical accepted rule is that **BASE_WORLD does not generate against Skyforge terrain**. Skyforge is materialized only after ordinary native generation has completed for the relevant chunk. Exact island-owned operations may then opt into Skyforge-aware height, biome, support or population behavior without restoring global highest-surface competition.

### Additive realization

For normal composition:

```text
Skyforge solid -> write the resolved Skyforge BlockState
Skyforge AIR   -> preserve the existing Minecraft block
```

AIR therefore means absence of Skyforge ownership, not permission to erase native terrain.

## Terrain and spatial model

### Independent finite volumes

Each island remains independently compiled. A `SkyIslandWorldCatalog` contains one `SkyIslandWorldVolume` per island, carrying stable identity, conservative world-space query bounds and the compiled backend-neutral terrain representation.

Backends query bounded regions rather than rerunning group or archipelago planning in the chunk hot path.

The accepted hierarchy remains:

```text
archipelago
  -> child group placement / role / reservation
      -> chain or cluster group plan
          -> independently seeded island members
              -> independently compiled island volumes
```

Composition owns occurrence and relationships; morphology owns form. This preserves future control over island frequency, vertical stacking, chains/clusters, outliers/ocean islands and other world rules without encoding those choices in a giant global density function.

### Geometry

Accepted suspended volumes have explicit upper and underside surfaces plus a signed-density intersection. Built-in families are:

- MASSIF;
- TABLELAND;
- SPINE;
- BASIN;
- LOBED.

Built-in and custom morphology providers share the public provider contract. Provider-authored primary surfaces remain authoritative through canonicalization; bounded secondary enrichment composes without changing accepted footprint sign.

Final morphology/playability tuning is still deferred. Earlier in-game reviews demonstrated that geometry quality is independently observable and specifically identified oversized/heavy undersides as a future content-quality concern rather than an integration-contract failure.

### Structural terrain semantics

Backend-neutral solid terrain retains continuous structural roles:

```text
AIR
EDGE_SHELL
SURFACE_MANTLE
UNDERSIDE_SHELL
SHALLOW_INTERIOR
DEEP_MASS
```

Density remains authoritative for AIR/solid occupancy. These semantics allow the backend to interpret terrain without making Minecraft block types part of the neutral engine.

## Compatibility evolution before domain isolation

SF-IMP-0036 through SF-IMP-0050 progressively proved that Skyforge terrain can participate in Minecraft surface adaptation, multi-surface placement, suitability evaluation, early height queries and structure support/admission/accommodation.

Those milestones remain valuable because they established reusable support observations and concrete Minecraft compatibility behavior. However, SF-IMP-0052 tightened the ownership model:

- ordinary BASE_WORLD height queries delegate to vanilla;
- ordinary BASE_WORLD structures bypass Skyforge support/admission policy;
- support/admission/accommodation machinery is available only inside explicit island scope;
- native generation must not accidentally treat Skyforge as part of the global base world.

The project should not regress to a system in which every native worldgen consumer competes over one global "highest surface" containing both Minecraft and all Skyforge volumes.

## Latest accepted milestones

### SF-IMP-0052 — terrain-domain generation isolation

Acceptance record: [`docs/reviews/SF-IMP-0052-terrain-domain-isolation-acceptance.md`](../reviews/SF-IMP-0052-terrain-domain-isolation-acceptance.md)  
Decision: [`ADR-0056`](../decisions/ADR-0056-terrain-domain-generation-isolation.md)

Accepted properties include:

- no explicit Skyforge domain scope means `BASE_WORLD`;
- ordinary base-height queries delegate to vanilla in BASE_WORLD;
- ordinary base-world structures bypass Skyforge support/admission policy;
- vanilla surface construction and biome decoration complete before Skyforge realization;
- explicit island height queries inspect exactly one `SkyIslandWorldVolumeId`;
- positions not owned by the exact Skyforge realization remain unchanged.

Interactive proof recorded **63,234 protected native positions** and **35,070 Skyforge solid positions** in the proof chunk while preserving the protected native fingerprint.

### SF-IMP-0054 — exact-volume biome bridge

Acceptance record: [`docs/reviews/SF-IMP-0054-biome-bridge-acceptance.md`](../reviews/SF-IMP-0054-biome-bridge-acceptance.md)  
Decision: [`ADR-0057`](../decisions/ADR-0057-exact-volume-biome-bridge.md)

An exact `SkyIslandWorldVolumeId` can resolve to a final-registry Minecraft biome only during its owning population operation. Native biome vegetation then runs through Minecraft's own placed/configured feature machinery rather than a Skyforge reimplementation.

The accepted stacked forest/taiga fixture scanned **25 eligible chunks**. Representative final evidence included:

- forest: 225 attempted native placements, 55 successful, 933 persisted logs and 7,436 persisted leaves;
- taiga: 250 attempted native placements, 59 successful, 1,148 persisted logs and 8,352 persisted leaves.

The acceptance history intentionally retains failed proof assumptions that exposed missing biome provenance, invalid single-chunk stochastic oracles, bounding-box assumptions and the distinction between API-level success and persistent world realization.

### SF-IMP-0055 — native exact-volume surface population

Acceptance record: [`docs/reviews/SF-IMP-0055-surface-population-acceptance.md`](../reviews/SF-IMP-0055-surface-population-acceptance.md)  
Decision: [`ADR-0058`](../decisions/ADR-0058-native-surface-population-planner.md)

SF-IMP-0055 promotes the biome proof into a reusable planner/coordinator/runtime binding.

Current accepted population scope is deliberately narrow: **`VEGETAL_DECORATION` only**. Hydrology, caves, underground decoration, ores, structures and `TOP_LAYER_MODIFICATION` remain excluded.

The accepted fixture completed:

- 25 observed chunks;
- 2 exact volumes;
- 1 admitted semantic population phase;
- **50 completed lifecycle keys**;
- **0 replay-executed phases** on an immediate equivalent second request.

This establishes the current population invariant:

> For one exact Skyforge volume, chunk and admitted semantic population phase, native population executes at most once per coordinator lifecycle; repeated equivalent requests return the original result without rerunning Minecraft feature occurrence.

## Why SF-IMP-0056 is necessary

The SF-IMP-0055 interactive run revealed a separate composition warning: native chest/banner block entities existed at coordinates where a development Skyforge island later wrote solid stone. The surface-population coordinator was functioning correctly; the planned island's exact three-dimensional solid volume simply overlapped already-generated native content.

That distinction is now architectural:

```text
generation-domain isolation != physical occupancy compatibility
```

BASE_WORLD can correctly generate without seeing Skyforge and still occupy coordinates a later Skyforge plan intended to claim. Production-safe realization therefore needs a physical admission decision after native evidence exists but before destructive Skyforge writes occur.

## Active SF-IMP-0056 — physical volume admission

**Status:** in progress; not accepted  
**PR:** #63, `SF-IMP-0056: prevent physical occupancy collisions`  
**Branch:** `agent/sf-imp-0056`  
**Snapshot head:** `76b2c8cd72a608ad1c15950dfc998d085008fa88`

The active branch already contains more than the original PR foundation description. At this snapshot it implements:

- explicit physical lifecycle `PLANNED -> ADMITTED | REJECTED`;
- non-destructive exact-volume/native occupancy survey over only Skyforge-owned solid coordinates;
- conservative first policy: any pre-existing non-air native block is a conflict;
- a whole-volume admission ledger whose required evidence is the finite Minecraft-chunk footprint of the planned volume;
- immediate terminal rejection on any conflict;
- admission only after every required footprint chunk has reported clear evidence;
- terminal, non-reopenable decisions and idempotent duplicate evidence;
- fail-closed solid-write gating while any exact owner remains PLANNED;
- population gating so exact-volume population cannot run before ADMITTED;
- immutable deferred-realization records rather than retained mutable generation-region/chunk references;
- a loaded-chunk catch-up service using `ServerChunkCache#getChunkNow`, so deferred work does **not** create generation tickets or force arbitrary future chunks;
- replay of the accepted native surface-population coordinator after successful terrain catch-up;
- unit coverage for admission-ledger/stage behavior;
- a dedicated `physicalAdmissionClient` development fixture.

### Active proof fixture

The development fixture deliberately plans two tablelands across a 5x5 / 25-chunk proof footprint:

- a lower volume intersects the vanilla Overworld bedrock floor and must become REJECTED without altering the native conflict;
- an upper clear volume must remain unrealized while PLANNED, become ADMITTED only after all 25 required chunks provide clear evidence, then catch up exact terrain and taiga population through already loaded stable chunks.

The intended terminal runtime marker is:

```text
SF-IMP-0056 PHYSICAL ADMISSION PASS
```

The fixture also requires no pending catch-up work after completion, exact preservation of the rejected conflict state, actual caught-up upper terrain and completed upper population phases.

### Current CI status on the active snapshot

CI run #334 on `76b2c8cd72a608ad1c15950dfc998d085008fa88` is red, but the **build, tests and evidence generation step passed**. The failure occurred in the older branch's evidence-artifact upload step.

`main` now contains a newer compact evidence-upload workflow with fork guards and `continue-on-error: true`. The active SF-IMP-0056 branch predates those publication/CI changes. The next development agent should integrate current `main` into the feature branch before treating run #334 as an implementation regression.

## Next development agent: exact starting procedure

The next agent should continue **SF-IMP-0056**, not open a new architecture track.

1. **Integrate current `main` into `agent/sf-imp-0056` without rewriting published history.** Preserve the publication hardening and current CI workflow. A normal merge is preferred over force-updating history.
2. **Review the post-merge diff before changing behavior.** Confirm the physical-admission classes, catch-up service, write/population gates and development fixture remain intact.
3. **Run the repository gates on the integrated head.** At minimum, run the same checks/evidence tasks enforced by CI and require the NeoForge/FML test environment to remain green.
4. **Run the dedicated interactive proof in a new disposable development world:**

   ```text
   :skyforge-neoforge-1211:runPhysicalAdmissionClient
   ```

5. **Require the terminal marker `SF-IMP-0056 PHYSICAL ADMISSION PASS`.** Do not substitute compilation, unit tests or visual plausibility for the runtime invariant.
6. **Inspect the proof semantics, not just the marker.** The lower volume must reject without mutating its native conflict; the upper volume must not partially realize while PLANNED; admission must require the complete finite footprint; catch-up must use already available chunks rather than force generation; native population must begin only after ADMITTED.
7. **Fix any runtime defect at the narrowest ownership layer that actually owns it.** Do not move Minecraft concepts into the neutral engine to work around backend lifecycle behavior.
8. **Record acceptance only after the exact integrated implementation head passes.** Add a milestone acceptance record under `docs/reviews/` containing the final implementation SHA, CI run, runtime marker and observed invariants. If a dedicated ADR is necessary, confirm the next unused ADR number on the then-current `main` before creating it (at this snapshot ADR-0059 is unused).
9. **Update this document and README only after acceptance.** Change the accepted boundary from SF-IMP-0055 to SF-IMP-0056 only when the gates above are complete.
10. **Merge PR #63 only after its acceptance evidence and exact-head CI are green.**

## Non-regression rules for the next agent

Do not compromise these accepted boundaries while finishing physical admission:

- BASE_WORLD generation must remain observationally isolated from Skyforge.
- Generation-domain isolation and physical occupancy admission are distinct concerns.
- No global highest-surface competition between native terrain and all Skyforge volumes.
- An exact island operation must remain scoped to one `SkyIslandWorldVolumeId` unless a higher-level composition operation explicitly owns multiple volumes.
- PLANNED physical volumes must fail closed; they may not partially realize while evidence is incomplete.
- REJECTED volumes must not leave destructive terrain or deferred population work behind.
- Deferred catch-up must not force arbitrary future chunks to generate.
- Native population must not execute before physical ADMISSION when the admission stage is installed.
- Population replay remains idempotent.
- Mutable Minecraft generation-region or chunk references must not be retained as long-lived deferred work.
- Minecraft/NeoForge APIs stay out of `skyforge-kernel`, `skyforge-model`, `skyforge-recipes` and `skyforge-world` unless a genuinely backend-neutral abstraction is demonstrated first.
- Preserve deterministic identity, exact volume ownership and existing canonical evidence behavior.
- Preserve the repository's publication hardening, CI least privilege and visible engineering history.

## Deferred work after SF-IMP-0056

Do not broaden SF-IMP-0056 merely because these items remain valuable. They are separate follow-on decisions:

- production world-plan/config bootstrap;
- additional native population phases such as ores, hydrology, underground decoration and structures;
- final morphology/playability tuning, especially underside proportions and traversable surface form;
- production material/ecology design beyond proof palettes;
- persistent world-plan/cache serialization;
- spatial-index and evaluator/cache optimization chosen from measurements;
- benchmarks comparing live, preload and hybrid realization policies;
- broader optional-mod registry/capability compatibility;
- public preview release automation and stable release criteria;
- richer portfolio screenshots/video once the current runtime is visually representative.

## Authoritative references

For the current accepted boundary, consult in this order:

1. [`SF-IMP-0055 native surface population acceptance`](../reviews/SF-IMP-0055-surface-population-acceptance.md)
2. [`ADR-0058 native surface population planner`](../decisions/ADR-0058-native-surface-population-planner.md)
3. [`SF-IMP-0054 exact-volume biome bridge acceptance`](../reviews/SF-IMP-0054-biome-bridge-acceptance.md)
4. [`ADR-0057 exact-volume biome bridge`](../decisions/ADR-0057-exact-volume-biome-bridge.md)
5. [`SF-IMP-0052 terrain-domain isolation acceptance`](../reviews/SF-IMP-0052-terrain-domain-isolation-acceptance.md)
6. [`ADR-0056 terrain-domain generation isolation`](../decisions/ADR-0056-terrain-domain-generation-isolation.md)
7. PR #63 for the active SF-IMP-0056 implementation state.

Earlier ADRs and acceptance records remain authoritative for their narrower contracts unless a later accepted decision explicitly supersedes them.
