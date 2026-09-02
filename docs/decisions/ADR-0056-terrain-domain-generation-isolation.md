# ADR-0056: Terrain-domain generation isolation

- **Status:** Proposed
- **Date:** 2026-09-01
- **Milestone:** SF-IMP-0052
- **Issue:** #54

## Context

Skyforge previously composed a floating island into the same live Minecraft chunk before ordinary biome decoration, and its generator-level early height query returned the higher of native terrain and Skyforge terrain. Those seams were useful integration proofs, but they also made unrelated terrain bodies compete inside one X/Z column.

The SF-IMP-0051 interactive proof exposed the resulting cross-domain effects. Ordinary terrain directly beneath a floating island showed reduced/absent vegetation and structures plus suspicious water-floor decoration. Its log also showed a terrain-projection bridge changing many ordinary low-terrain results rather than intervening only at a proven cross-domain conflict.

The desired production model is stronger: vanilla base terrain and each independently compiled Skyforge island are separate terrain owners that may overlap in X/Z without becoming one logical terrain column.

## Decision

Skyforge SHALL model generation as explicit terrain domains:

```text
BASE_WORLD
SKYFORGE_VOLUME(id)
```

X/Z overlap alone SHALL NOT create a relationship between domains.

A generation operation SHALL resolve its owning domain before terrain-dependent behavior. Once an owner is resolved, unrelated domains are observationally absent unless an explicit cross-domain interaction contract exists.

### Base-world rule

For `BASE_WORLD` generation, Skyforge terrain SHALL be observationally absent.

Ordinary vanilla/modded terrain, surface generation, structure occurrence, placed/configured features, vegetation, fluid/water-floor decoration and height-dependent placement must behave as they would have behaved without a Skyforge island above the same X/Z.

Skyforge SHALL NOT compute a replacement native result merely because it can see additional terrain.

### Skyforge-volume rule

For `SKYFORGE_VOLUME(id)` generation, terrain queries SHALL resolve only against that exact independently compiled volume. Base-world terrain and other stacked Skyforge volumes SHALL NOT compete by highest, nearest or first-visible surface.

An empty column in an island domain remains empty. Ambiguous ownership SHALL fail open rather than inventing intent.

## Occurrence is domain-local

True vertical independence requires more than choosing the correct Y after one shared 2-D placement decision.

A single column-oriented occurrence stream cannot simultaneously represent independent base terrain and multiple stacked island volumes without making them compete for feature and structure decisions.

Therefore occurrence intended to populate a Skyforge island SHALL be domain-local and deterministically seeded to that exact volume while reusing native Minecraft/modded registries and definitions wherever possible.

```text
BASE_WORLD placement stream
    -> vanilla/modded worldgen unchanged

SKYFORGE_VOLUME(A) placement stream
    -> native definitions reused
    -> occurrence belongs to A

SKYFORGE_VOLUME(B) placement stream
    -> native definitions reused
    -> occurrence belongs to B
```

This does not require a parallel Skyforge biome, feature or structure taxonomy. Minecraft remains authoritative for registry definitions and semantic validity; Skyforge owns only terrain-domain occurrence and geometry.

## Preferred lifecycle

```text
1. BASE_WORLD native generation completes with Skyforge observationally absent
2. exact Skyforge volumes are materialized additively
3. Skyforge-owned adaptation/population runs per volume through generic native registry seams
4. lighting/heightmap state is finalized for the composite runtime world
```

If island-owned structures require a narrower post-overlay native seam, that seam may be introduced generically. The implementation SHALL NOT restore global `max(vanilla, Skyforge)` terrain authority or per-structure compatibility tables.

## Compatibility principle

Unknown modded worldgen is base-world-owned by default and should work unchanged without Skyforge-specific knowledge.

A modded feature or structure becomes Skyforge-volume-owned only when the generic island adaptation path deliberately invokes its native definition inside an exact island domain.

> A completely unknown structure or feature from another mod should have a reasonable chance of working correctly without Skyforge ever having heard of it.

## Superseded stepping-stone assumptions

The following accepted experimental assumptions are superseded as production architecture where they conflict with this ADR:

- a global `max(vanilla, Skyforge)` early height answer;
- treating the globally highest Minecraft surface as the ordinary placement target while merely supplementing lower surfaces;
- consumer-specific base-world reconstruction when lifecycle isolation can make Skyforge absent instead.

Earlier acceptance records remain valid as experiments demonstrating individual seams; they do not override this stronger production boundary.

## Staged validation

The decision is intentionally validated in two milestones so base-world isolation is proved independently from island repopulation.

### SF-IMP-0052 — base-world isolation and exact-volume primitives

SF-IMP-0052 must prove:

1. ordinary base-world early height queries bypass Skyforge;
2. ordinary native/modded structure occurrence bypasses Skyforge admission policy;
3. vanilla/modded biome decoration completes before any Skyforge block is realized;
4. later additive island realization does not mutate the already-completed lower base world except at explicit physical intersection;
5. native surface material may be captured read-only for later island representation but cannot become a placement authority;
6. an explicit island-domain height query sees exactly one `SkyIslandWorldVolumeId` and never falls through to base terrain or another island;
7. full repository CI and fixed-seed/suspended-volume evidence remain green;
8. an interactive Minecraft specimen demonstrates the removal of the generation shadow and preservation of native lower structures/features.

ADR-0056 may become **Accepted** when SF-IMP-0052 proves this lifecycle/domain boundary. Acceptance of the architectural decision does not claim that all island population families are already implemented.

### SF-IMP-0053 — first domain-local island population

The immediate follow-on must prove the first independently seeded population stream for one exact island volume using native Minecraft/modded registry definitions. It must include at least surface vegetation/placed-feature behavior and a vertically stacked-volume regression showing that A and B do not share one occurrence lottery.

Additional feature families such as ores, underground features and structures may follow as separate slices where their native vertical semantics require distinct adaptation, but none may reintroduce a shared highest-surface model.

## Consequences

This architecture applies even to a future all-island world. Vertically dense regions still require independent occurrence and terrain ownership for stacked islands sharing the same X/Z footprint.

It also prevents compatibility work from degenerating into a sequence of feature-specific symptom patches: isolation is the default, and cross-domain interaction must be explicit.
