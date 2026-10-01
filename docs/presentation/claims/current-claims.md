# Skyforge current external claim registry

**Claim freshness checked:** 2026-09-07 (America/Chicago) against current `main`, producer lane ledgers, cross-lane contracts, accepted showcase records, and recent merged history.

This file is a Presentation-layer communication register. It does not supersede source, tests, merged producer history, lane state, or cross-lane contracts.

## Core current claims

| Claim | Status | Owning evidence | Material caveat |
| --- | --- | --- | --- |
| Skyforge is a deterministic, backend-neutral procedural world-synthesis system whose first complete game realization is Minecraft 1.21.1 / NeoForge. | ACCEPTED / PROVEN | `README.md`, `docs/agent-state/PROGRAM_CHARTER.md` | Backend-neutral architecture is proven; multiple production backends are not. |
| Skyforge starts from semantic world intent and compiles that intent through versioned recipes / procedural graphs into deterministic fields and exact world volumes. | ACCEPTED / PROVEN | `README.md`, neutral engine modules and tests | Presentation may simplify this to “starts with meaning, not random blocks,” but must not imply every downstream semantic is already physically realized. |
| The neutral engine supports five primary built-in morphology families: Massif, Tableland, Spine, Basin, and Lobed. | ACCEPTED / PROVEN | Authorship state; AUTH-0083; README | Broader seed/scale/hybrid/regional visual closure remains under issue #214. |
| Skyforge islands are finite three-dimensional owned volumes with upper and underside geometry, not merely height fields. | ACCEPTED / PROVEN | README; Implementation state; SF-IMP-0081/0082 showcase records | Do not imply every future material/hydrology/ecology semantic is already physically present inside every volume. |
| Separate exact Skyforge volumes can coexist at shared horizontal coordinates without collapsing into one global column. | ACCEPTED / PROVEN | Implementation state / stacked-volume acceptance | This is a runtime ownership property, not a player-facing feature claim by itself. |
| All five SMALL / `seed-skyforge` built-in morphology families have exact Minecraft carriers with accepted persistence/reopen evidence and human morphology viability review. | ACCEPTED / PROVEN | SF-IMP-0081/0082; issue #214 review comments; `docs/showcase/production-morphology-*.md` | Full multi-seed/multi-scale/hybrid/regional issue #214 gate remains open. |
| Skyforge currently integrates accepted Minecraft lifecycle behavior including exact-volume realization, native surface/biome population, caves, interior population, structures, generated-fluid ownership/boundaries, deferred generation, persistence, and actual-client reopen. | ACCEPTED / PROVEN | `IMPLEMENTATION_STATE.md`, README, milestone review docs | Some systems were proven in dedicated technical fixtures rather than one final production world. Structure support still requires later reintegration into the newest production lifecycle. |
| A dedicated accepted ecology fixture shows persistent, visually distinguishable forest and taiga land surfaces using normal Minecraft-facing production seams. | ACCEPTED / PROVEN | SF-IMP-0080; `docs/showcase/ecology-showcase.md` | This proves ecology legibility and lifecycle behavior, not complete authored ecology-to-Minecraft translation. |
| Skyforge produces deterministic machine-readable evidence and reproducible identities rather than relying on screenshots alone. | ACCEPTED / PROVEN | README, reference evidence tasks, Authorship/Implementation acceptance records | Human visual/listening gates remain required where qualitative judgment is the claim. |
| Authorship already exposes backend-neutral hydrology, ecology, geology/resource opportunity, regional, and local site/access evidence with explicit provenance. | ACCEPTED / PROVEN | AUTH-0085 through AUTH-0098; `AUTHORSHIP_STATE.md`; `CROSS_LANE_CONTRACTS.md` | Opportunity/evidence is not automatically a physical Minecraft feature, resource guarantee, settlement, runway, deposit, or gameplay role. |
| Content already defines and proves a substantial gameplay integration substrate including retained mobility/atmosphere, 1:1 Nether behavior, programmable avionics/network/GPS, turtle freight/mining constraints, and initial base-metal availability policy. | ACCEPTED / PROVEN | `CONTENT_STATE.md`, cross-lane contracts | Bootstrap Province is not complete; several gameplay decisions and concrete resource realization remain open. |
| Skyforge is pre-release and does not currently promise a stable public API or finished player-facing package. | ACCEPTED / PROVEN | README | Do not present development showcases as a public release. |

## Safe high-level phrasing

### One sentence

> Skyforge is a deterministic world-synthesis engine that starts from the meaning of a place, builds an exact three-dimensional procedural world from that intent, and can already realize those authored worlds inside Minecraft through a separate NeoForge backend.

### Thirty seconds

> Skyforge is a procedural world-synthesis engine. Instead of beginning with random blocks, it begins with the identity of a place - its landform and the world semantics associated with it - then compiles that intent into deterministic three-dimensional terrain with exact ownership and reproducible evidence. Minecraft is the first real backend, and the project already carries authored floating-island terrain through real game systems such as native surface population, caves, vegetation, persistence, and save/reopen. The current roadmap is to make those pieces converge into one coherent playable region.

## Claims that require status language

### IN PROGRESS

- **SF-IMP-0083:** broader built-in multi-seed/multi-scale Minecraft morphology confidence. Presentation may say “the accepted five-family SMALL set is being broadened across more seeds and scales.”
- **Issue #214:** broader morphology/regional human visual gate. Do not imply the production atlas is complete.
- **Bootstrap Province / issue #224:** central coherent gameplay vertical slice. It is a target, not a present world.

### ROADMAP / PLANNED

- full convergence of authored geology/materials into physical Minecraft realization;
- visible authored hydrology such as channels/cascades/waterfalls through the production runtime;
- structure/civilization realization over accepted site/access evidence;
- coherent regional composition that combines terrain, geology, hydrology, ecology, resources, structures, mobility, industry, and progression;
- a finished deterministic Bootstrap Province.

### ASPIRATIONAL / STRATEGIC

- multiple independent production backends proving backend neutrality empirically;
- general-purpose procedural-world middleware / commercial product positioning;
- broad public ecosystem, marketplace, or studio licensing claims.

## Must not claim yet

Do **not** state or visually imply that:

- Skyforge has multiple working production backends;
- the project is a finished Minecraft mod, finished game, or production-ready public engine;
- every Authorship semantic is already physically realized in Minecraft;
- geological opportunity equals literal ore/oil deposits, reserves, grade, or guaranteed accessibility;
- ecology opportunity equals species populations or carrying capacity;
- local site/access evidence means a village, runway, dock, settlement, or structure already exists;
- full issue #214 morphology quality is closed;
- Bootstrap Province is already generated/playable as the final intended experience;
- repeated machine evidence substitutes for human visual/listening acceptance where a human gate is explicitly required.

## Freshness triggers

Review this registry when any of the following changes materially:

- SF-IMP-0083 / issue #214 acceptance boundary;
- concrete geology/material or authored-hydrology Minecraft realization;
- structure/civilization production reintegration;
- Bootstrap Province executable closure;
- introduction of a second independent production backend;
- any producer lane reopens or weakens an external-facing accepted claim.
