# ADR-0056: Terrain-domain generation isolation

- **Status:** Accepted
- **Date:** 2026-09-01
- **Accepted:** 2026-09-02
- **Milestone:** SF-IMP-0052
- **Issue:** #54

## Context

Earlier Skyforge integration exposed floating-island terrain through global height queries and realized the island before ordinary biome decoration. The SF-IMP-0051 interactive proof showed the consequence: unrelated base terrain beneath an island could lose vegetation/structures or receive altered water-floor decoration. X/Z overlap had accidentally become generation ownership.

Skyforge instead requires vanilla base terrain and each independently compiled island to be separate terrain owners that may overlap horizontally without becoming one logical terrain column.

## Decision

Skyforge SHALL model generation through explicit terrain domains:

```text
BASE_WORLD
SKYFORGE_VOLUME(id)
```

X/Z overlap alone SHALL NOT create a relationship between domains. A generation operation resolves one owner before terrain-dependent behavior, and unrelated domains are observationally absent unless an explicit interaction contract says otherwise.

### Base-world rule

For `BASE_WORLD`, Skyforge terrain is observationally absent. Native/modded terrain, surfaces, structures, features, vegetation, fluids and height-dependent placement run with ordinary Minecraft semantics. Skyforge does not reconstruct or replace a native answer merely because another terrain body exists above it.

### Exact-volume rule

For `SKYFORGE_VOLUME(id)`, terrain queries resolve only against that exact independently compiled volume. Base terrain and stacked islands do not compete by highest, nearest or first-visible surface. An empty island column stays empty. Ambiguous ownership fails open.

## Occurrence is domain-local

Vertical independence requires independent occurrence, not merely correcting Y after one shared placement lottery.

```text
BASE_WORLD stream
    -> native/modded generation unchanged

SKYFORGE_VOLUME(A) stream
    -> native definitions reused
    -> deterministic occurrence belongs to A

SKYFORGE_VOLUME(B) stream
    -> native definitions reused
    -> deterministic occurrence belongs to B
```

Minecraft remains authoritative for registered biome/feature/structure definitions and semantic validity. Skyforge owns terrain-domain identity, geometry and occurrence. This deliberately avoids a parallel Skyforge taxonomy for every tree, ore or structure.

## Lifecycle

```text
1. BASE_WORLD generation completes with Skyforge absent
2. exact Skyforge volumes are materialized additively
3. Skyforge-owned population executes per volume through generic native registry seams
4. the composite runtime world resumes ordinary Minecraft behavior
```

Island-owned structures or other systems may require narrower generic seams, but the implementation SHALL NOT restore global `max(vanilla, Skyforge)` terrain authority or per-content compatibility tables.

## Compatibility principle

Unknown modded worldgen is base-world-owned by default. A modded feature or structure becomes island-owned only when a generic Skyforge population path deliberately invokes its native definition inside an exact volume. A completely unknown native definition should therefore have a reasonable chance of working without Skyforge-specific code.

## Superseded assumptions

The following stepping stones are superseded where they conflict with this ADR:

- global `max(vanilla, Skyforge)` early-height answers;
- treating the globally highest surface as the default placement target;
- consumer-specific repair of base-world results when lifecycle isolation can make Skyforge absent instead.

Earlier milestones remain useful evidence for individual integration seams, but not as authority for global-column composition.

## Acceptance

SF-IMP-0052 proved the domain boundary with automated tests, exact-volume height-query coverage, CI and an interactive ownership-aware fixture. On 2026-09-02 the fixture emitted `SF-IMP-0052 BASE WORLD ISOLATED` for chunk `[0, 0]`, preserving 63,234 non-Skyforge positions while realizing 35,070 Skyforge-owned solids. The forced woodland mansion remained on native terrain beneath the bare Massif, and the surrounding base terrain showed no visible generation shadow. CI #232 passed on the corrected proof head.

Acceptance of ADR-0056 establishes the lifecycle/ownership model; it does not claim that island population families are already complete.

## Follow-on

SF-IMP-0053 must prove the first independently seeded exact-volume population stream using native Minecraft/modded registry definitions. It must include a surface placed-feature/vegetation proof and vertically stacked volumes showing that A and B do not share one occurrence lottery. Ores, underground features, hydrology, caves and structures can then build on the same ownership contract without reintroducing global highest-surface competition.
