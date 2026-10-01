# Skyforge Morphology Control Atlas

**Status:** canonical control/ownership contract; descriptive atlas population is incremental  
**Updated:** 2026-09-10 (America/Chicago)

## Purpose

Skyforge's morphology system must preserve broad expressive capacity **and** remain deliberately controllable.

A generator that can create many terrain forms but whose users do not understand how to request those forms is not production-ready. Conversely, production tuning must not erase valid rugged, lumpy, awkward, hostile, or otherwise role-inappropriate terrain merely because ordinary traversal prefers quieter geography.

This document defines the durable Authorship artifact for separating:

```text
generator capacity
    -> semantic control knowledge
    -> gameplay/world-role selection
    -> backend realization
```

It does not itself choose production terrain for Wild Blue Yonder and does not define Minecraft block-space traversal rules.

## Ownership

### Authorship

Authorship owns:

- the backend-neutral expressive morphology space;
- stable semantic control axes and named terrain recipes;
- descriptive control-response evidence;
- interactions among controls;
- representative extremes and deterministic examples;
- exact provenance sufficient to reproduce a requested neutral terrain character;
- evidence that materially different terrain characters are intentionally reachable.

Authorship does **not** own:

- whether a terrain character is good or bad for a gameplay role;
- starting-area suitability;
- Minecraft step/slope/walkability thresholds;
- concrete block discretization;
- physical runtime lifecycle.

### Content / Experience

Content owns:

- terrain requirements implied by gameplay/world roles;
- suitability and production distribution;
- which Authorship recipe is chosen for starting, settled, remote, hostile, exceptional, or other contexts;
- progression consequences of where terrain characters appear.

Content should ask for semantic recipes/requirements rather than hardcoding incidental internal generator parameters.

### Implementation

Implementation owns:

- faithful translation of the requested neutral terrain into the concrete backend;
- block-space support, slope/step/traversal consequences, structure clearance, persistence, mutation, and lifecycle;
- detection of backend-induced distortion or discretization artifacts.

Implementation does not silently smooth or retune neutral morphology to solve a Content suitability problem.

### Human review

Human review owns qualitative product judgments such as beauty, family identity, traversal feel, repetition, silhouette quality, and whether a technically valid terrain recipe belongs in a particular production context.

## Atlas entry schema

Each meaningful control axis or named recipe should eventually record:

1. **semantic name** — stable backend-neutral identifier;
2. **intent** — what terrain character the control/recipe is intended to express;
3. **control definition** — accepted neutral parameters or composition rule;
4. **descriptive response** — observed effect on morphology without good/bad ranking;
5. **interactions** — other controls that materially change the response;
6. **representative low/mid/high or alternative examples**;
7. **diagnostics** — metrics that distinguish the response where useful;
8. **canonical deterministic examples** — seed/spec/provenance sufficient for reproduction;
9. **invariants** — what should remain unchanged when varying the control;
10. **known backend caveats** — only as downstream evidence, never as hidden Minecraft policy;
11. **consumer notes** — which Content requirements may plausibly consume the recipe, without assigning the final gameplay role.

A control is not considered well-understood merely because changing it visibly changes terrain. The atlas should make the direction and meaningful interactions reproducible enough that a future author can deliberately request a target terrain character.

## Required distinction: capacity versus production selection

Examples:

```text
RUGGED / LUMPY MASSIF
    valid expressive capacity
    may be appropriate for remote or hostile terrain
    may be rejected for an ordinary starting foothold

QUIETER / BROAD-BENCHED MASSIF
    separately valid expressive capacity
    may be preferred where route continuity matters
```

The correct production fix for a role mismatch is normally to change **recipe selection/distribution**, not to remove one of these from the generator.

Likewise, Tableland should be controllable across meaningful variation while preserving a recognizably different neutral family identity from Massif. “Make every Tableland flat” is not an acceptable substitute for understanding its control space.

## Fault triage

Before changing morphology, classify the defect:

```text
1. Neutral shape/control response is wrong,
   or desired terrain cannot be deliberately reproduced.
       -> AUTHORSHIP

2. Neutral shape is intentional and reproducible,
   but inappropriate for its assigned gameplay/world role.
       -> CONTENT / EXPERIENCE

3. Neutral shape is correct,
   but Minecraft/backend realization distorts it or creates artifacts.
       -> IMPLEMENTATION

4. Technical behavior is correct,
   but preferred look/feel/distribution is unresolved.
       -> HUMAN PRODUCT GATE
```

Do not allow one lane to solve another lane's failure by obscuring the boundary.

## Relationship to current morphology review

AUTH-0095 and AUTH-0101 already provide threshold-free descriptive morphology evidence. They do **not** yet constitute a complete control atlas.

The #214 / #267 / #283 human review should therefore answer two different questions explicitly:

1. **production judgment:** which realized terrain characters are desirable for ordinary production roles?
2. **control sufficiency:** does current evidence suggest that Authorship can deliberately produce both the accepted and rejected terrain characters without collapsing generator capacity?

If a human review identifies “too lumpy for this role,” do not immediately retune the entire family. First determine whether:

- the current Authorship recipe itself is wrong;
- the control space already contains a quieter suitable recipe;
- a new stable semantic control/recipe is needed;
- Content simply selected the wrong valid recipe;
- Minecraft introduced the perceived defect.

## Initial control-atlas work after the current human gate

If the current #214/#267/#283 review identifies a concrete need, the next Authorship work should be the smallest evidence needed to characterize the relevant control relationship, for example:

- local-relief amplitude versus short-distance surface variation;
- characteristic secondary length scale versus broad/short-period terrain features;
- Tableland broad-upper-surface persistence versus bounded detail;
- Massif macro drama versus local route continuity;
- primary/detail contributions to underside character.

Do not create arbitrary aesthetic thresholds. Use descriptive neutral evidence plus matched Minecraft/human observations, and add a new semantic control only when existing controls cannot deliberately reach the needed terrain character.

## AUTH-0102 — reviewed Tableland/Lobed recipe refinement — IN PROGRESS

The #442 project-owner evidence bundle (comment `5622909247`) identifies two authored-intent defects
in the exact accepted review specimens: `builtin-tableland-medium-seed-skyforge` lacks persistent
broad elevated benches under its full-detail recipe, and `builtin-lobed-medium-seed-skyforge` has
weak lobed silhouette legibility. The matching Minecraft carrier/coherence review passed; this is
not a carrier, traversal, role-selection, underside, or meso-scale change. The reviewed
`builtin-massif-medium-seed-skyforge` and `builtin-massif-large-seed-skyforge` remain explicit
negative controls and are not retuned.

Two existing recipe relationships are sufficient; no descriptor schema, generator primitive, random
namespace, support envelope, or new semantic axis is required.

### `tableland.quiet-outer-detail`

- **Intent:** retain a broad elevated primary crown and dramatic rim while reducing the full-detail
  secondary contribution that breaks its upper-surface cadence.
- **Control definition:** the existing Tableland outer-gated ridge / shoulder / edge-cut composition
  keeps its shapes and seed namespaces, with strengths changed from `0.10 / 0.05 / 0.07` to
  `0.06 / 0.03 / 0.04`.
- **Deterministic before/after control evidence:** its analytical full-amplitude factor envelope
  contracts from `[0.93, 1.15]` to `[0.96, 1.09]`. The neutral primary crown, rim gate, descriptor
  fields, and seed derivation are unchanged.
- **Canonical specimen and diagnostics:** compare the exact reviewed member before/after with the
  existing AUTH-0095 threshold-free upper-relief, gradient, curvature, fixed-lag, and local-window
  distributions; retain the AUTH-0101 Massif MEDIUM -> Tableland MEDIUM seed-skyforge row as
  descriptive context only.

### `lobed.minimum-footprint-modulation`

- **Intent:** make the already-authored Lobed footprint read as lobed more consistently without
  changing its five-family vocabulary, carrier, or support contract.
- **Control definition:** the existing seeded `family.lobe-strength` relation changes from
  `[1.44, 1.76)` to `[1.60, 1.76)`. It uses the same seed namespace and leaves the certified
  upper support maximum unchanged.
- **Deterministic before/after control evidence:** each seed's lobed directional radial factor is
  increased or unchanged, while the prior maximum remains the certified bound. The exact reviewed
  member is `builtin-lobed-medium-seed-skyforge`; use the existing AUTH-0095 diagnostics alongside
  its reproducible visual evidence, without introducing a lobe score or aesthetic threshold.

### Acceptance boundary

The change is deliberately limited to those two family recipes. It does not assert that the result
is beautiful, sufficiently plateau-like, or sufficiently lobed; downstream human qualitative
re-review owns that acceptance. Implementation must realize these exact authored recipes faithfully,
and Content still owns any production role selection.

## Acceptance principle

Skyforge production morphology is mature only when both are true:

1. the generator has sufficient expressive capacity; and
2. the project has enough documented control knowledge to intentionally select useful regions of that capacity.

Neither “the generator can make it” nor “one tuned preset looks good” is sufficient by itself.
