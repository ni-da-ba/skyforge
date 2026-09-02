# ADR-0056: Terrain-domain generation isolation

- **Status:** Proposed
- **Date:** 2026-09-01
- **Milestone:** SF-IMP-0052
- **Issue:** #54

## Context

Skyforge currently composes a floating island into the same live Minecraft chunk before ordinary biome decoration, and its generator-level early height query returns the higher of native terrain and Skyforge terrain. These seams were useful proofs for making suspended surfaces visible to native systems, but they also make unrelated terrain bodies compete inside one X/Z column.

The SF-IMP-0051 interactive proof exposed the resulting cross-domain effects. Ordinary terrain directly beneath a floating island showed reduced/absent vegetation and structures plus suspicious water-floor decoration. The log also showed a terrain-projection bridge changing many ordinary low-terrain results rather than intervening only at a proven cross-domain conflict.

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

This means ordinary vanilla/modded:

- terrain and surface generation;
- structure occurrence and placement;
- placed/configured features;
- vegetation;
- fluid and water-floor decoration;
- height-dependent placement;

must behave as they would have behaved without a Skyforge island above the same X/Z.

Skyforge SHALL NOT compute a replacement native result merely because it can see additional terrain.

### Skyforge-volume rule

For `SKYFORGE_VOLUME(id)` generation, terrain queries SHALL resolve only against that exact independently compiled volume. Base-world terrain and other stacked Skyforge volumes SHALL NOT compete by highest, nearest, or first-visible surface.

Ambiguous ownership SHALL fail open rather than inventing intent.

## Occurrence is domain-local

True vertical independence requires more than choosing the correct Y after a 2-D placement decision.

A single Minecraft column-oriented occurrence stream cannot simultaneously represent independent base terrain and multiple stacked island volumes without making them compete for the same feature/structure decisions.

Therefore occurrence/placement that is intended to populate a Skyforge island SHALL be domain-local and deterministically seeded to that exact volume while reusing native Minecraft/modded registries and definitions wherever possible.

Conceptually:

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

This does not require a parallel Skyforge biome, feature, or structure taxonomy. Minecraft remains authoritative for registry definitions and semantic validity; Skyforge owns only terrain-domain occurrence and geometry.

## Preferred lifecycle

The implementation SHOULD prefer phase/lifecycle isolation over a growing collection of consumer-specific patches.

The leading design is:

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

This preserves the project invariant:

> A completely unknown structure or feature from another mod should have a reasonable chance of working correctly without Skyforge ever having heard of it.

## Superseded stepping-stone assumptions

The following accepted experimental assumptions are not suitable as final production architecture and must be revised where they conflict with this ADR:

- a global `max(vanilla, Skyforge)` early height answer;
- treating the globally highest Minecraft surface as the ordinary placement target while merely supplementing lower surfaces;
- consumer-specific base-world reconstruction when lifecycle isolation can make Skyforge absent instead.

Earlier acceptance records remain valid as experiments demonstrating individual seams; they do not override this stronger production boundary.

## Validation

SF-IMP-0052 requires a side-by-side interactive control fixture with identical base-world generation coordinates/seeds, once without an overlapping island and once with one.

Acceptance requires:

1. base terrain blocks below the island remain unchanged except for explicit physical overlap;
2. base-world vegetation and placed features show no island-induced suppression or relocation;
3. base-world structures retain their native occurrence/placement behavior;
4. water/ocean-floor decoration shows no island-induced anomaly;
5. island-owned population can still target an exact Skyforge volume;
6. vertically stacked islands use independent domain-local occurrence streams;
7. unknown/modded native definitions remain registry-driven rather than hard-coded;
8. full CI and fixed-seed/suspended-volume evidence remain green.

ADR-0056 becomes **Accepted** only after the automated and interactive proofs establish observational isolation.