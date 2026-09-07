# Skyforge cross-lane contracts

**Status:** canonical concise cross-lane state  
**Updated:** 2026-09-06

This file records only dependencies/contracts another lane must know. Detailed lane histories belong in lane state or linked design/acceptance documents.

## MERGED / ACCEPTED contracts

### Authorship -> Implementation

- Exact `SkyIslandWorldVolumeId` identity and finite 3-D ownership are authoritative; stacked volumes sharing X/Z remain independent.
- Backend-neutral compiled morphology/world contracts remain outside Minecraft/NeoForge modules.
- Implementation may optimize evaluation using conservative authored bounds only when exact output/ownership semantics are preserved.
- Existing morphology architecture includes Massif, Tableland, Spine, Basin, Lobed, hybrids/provider composition, secondary geography, bounded detail, and regional grouping. The current technical showcase specimen is not the production morphology target.
- Authored caves and native Minecraft caves compose under explicit precedence/order rather than competing global terrain ownership.

### Implementation -> Authorship / Content

- Minecraft realization now supports whole-volume physical admission, deferred exact realization, native surface population, composed caves, post-cave interior population, lakes, ores, underground decoration, fluid springs, persistence/reopen, stacked isolation, and actual-client showcase verification.
- Native spring fluids are not an authored hydrology substitute. Accidental vanilla spring escape must remain fenced; future waterfalls/outlets should come from authored hydrologic intent.
- Production interior features use exact-domain and lifecycle-aware admission; feature return counts alone are not sufficient evidence of player-visible quality.
- Performance optimization must preserve deterministic identity, ownership, admission, persistence, and no-replay behavior.

### Content -> Implementation

- Structure realization should follow generic realization classes: surface-supported, settlement/network, subsurface-embedded, cliff/underside-attached, detached, and structure-seeded terrain. See `docs/design-audit/structure-realization-contract-v0.1.md`.
- Progression-critical structures require guaranteed admissible realization or an approved equivalent fallback; ordinary structures may reject.
- Bootstrap/first-flight content should consume generic world/runtime capabilities rather than force bespoke terrain hacks.

## IN PROGRESS dependencies

- Authorship PR #241 / AUTH-0086 is developing visible hydrologic realization intent. Implementation should consume that contract before making authored waterfalls/channels a Minecraft runtime feature.
- Content has active first-flight/aircraft work (including PRs #233 and #242). Implementation should preserve useful runtime seams for these systems without coupling core worldgen to a single aircraft.

## PROPOSED / future coordination

- If current primary morphology + bounded detail is insufficient underneath, Authorship and Implementation should define an explicit underside-secondary vocabulary before production morphology acceptance.
- Structure reservations may need to become visible to morphology/cave/hydrology planning before concrete Minecraft structure realization.
- A deterministic Bootstrap Province will require authored world intent, implementation realization, and content/progression contracts to converge in one persistent specimen.

## MANUAL VERIFICATION REQUIRED

- Production morphology aesthetics are governed by issue #214. No lane should treat technical topology proof as aesthetic acceptance.
- Legible biome/ecology realization is governed by issue #194; machine population evidence alone does not close the human-facing gate.
