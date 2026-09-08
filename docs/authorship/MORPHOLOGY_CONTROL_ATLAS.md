# Skyforge Morphology Control Atlas

**Status:** canonical control/ownership contract; descriptive atlas population is incremental  
**Updated:** 2026-09-08 (America/Chicago)

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

## Acceptance principle

Skyforge production morphology is mature only when both are true:

1. the generator has sufficient expressive capacity; and
2. the project has enough documented control knowledge to intentionally select useful regions of that capacity.

Neither “the generator can make it” nor “one tuned preset looks good” is sufficient by itself.
