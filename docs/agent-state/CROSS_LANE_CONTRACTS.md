# Skyforge Cross-Lane Contracts

**Status:** Canonical concise coordination state  
**Updated:** 2026-09-06 (America/Chicago)  
**Repository snapshot when updated:** `335978905f5c5e235c07a114a653a3be24536c47`

## Program-wide invariants

- Backend-neutral modules remain free of Minecraft/NeoForge ontology unless a neutral abstraction is justified.
- Authorship owns world meaning; Implementation owns realization/lifecycle; Content owns game integration/experience.
- Exact three-dimensional ownership and deterministic identity remain fundamental.
- Existing mods/content are asset and capability libraries under Skyforge semantic authority.
- Do not solve poor morphology or ecological legibility by increasing content density.
- Negative space is an intentional part of the sky-world scale fantasy.

## Current lane snapshot

| Lane | Repository-visible boundary |
| --- | --- |
| Authorship | `AUTH-0085` merged on current `main` (native spring semantic admission) |
| Implementation | `SF-IMP-0079` merged before current Authorship work (post-cave vegetal routing) |
| Content / Experience | C7 shared glider/A4MC lift merged; later Bellanca B0/Portable Engine design contracts merged |

Use each lane's own state file and git history for detail. Do not infer acceptance solely from an old
architecture summary; some older runtime overview docs lag current `main`.

## Active coordination contracts

### Bootstrap Province

Issue **#224** is the organizing Content vertical slice.

Final content acceptance depends on a visually legible land-biome specimen, currently tracked by
Implementation issue **#194**. Content must not judge ecology from gravel/ocean showcase fixtures.

Required progression shape:

```text
spawn -> survival foothold -> Create workshop -> cheap glider -> shared thermals/fauna
-> first powered aircraft -> regional specialization -> freight/infrastructure
-> evidence of mature skyborne civilization
```

First powered flight should remain pre-Brass/pre-petroleum unless executable closure disproves it.

### Atmosphere and lift

Aerodynamics4MC is the leading single atmosphere authority.

- Aircraft consume it through the retained Aeronautics compatibility path.
- C6 proves the retained Fowl Play red-tailed hawk can enter/exit thermal SOAR from the same field.
- C7 proves Reliable Gliders can consume trusted vertical lift after native glider physics.
- Other lanes must not introduce a second independent wind/thermal authority without reopening this contract.

### Ecology

Authorship/environment semantics determine viable niches and population opportunity.
Content maps retained species into those niches.
Atmosphere may alter behavior (for example thermal soaring) but must not independently create population.

### Structures / civilization

Content defines gameplay roles and reuse-first asset strategy.
Authorship provides site/environment semantics.
Implementation owns realization modes and lifecycle safety.

Leading realization modes remain:

- surface-supported;
- settlement/network;
- subsurface;
- cliff/underside;
- detached;
- structure-seeded terrain.

Progression-critical structures must remain obtainable.

### Computing

Computing is a first-class capability axis but is **not** a first-flight prerequisite.

Leading reuse path:

- CC:Tweaked as computing substrate;
- existing Aeronautics/CC avionics/peripheral integrations before bespoke Skyforge peripherals;
- thin Skyforge peripherals only for genuinely Skyforge-owned semantics not already exposed.

Bootstrap computing requirements and bypass questions are tracked in **#224**.

### Bellanca / first mature utility aircraft

Merged design contracts define the Giuseppe Bellanca / B0 engineering mule.
The aircraft must be a real Sable/Create Aeronautics contraption and support useful power-off flight.

Issue **#237** tracks the proposed opt-in Portable Engine cutoff needed to conserve active fuel during
intentional engine-off soaring. This is proposed compatibility work, not accepted runtime behavior.

## Handoff discipline

When one lane changes a contract another lane relies on, update this file with:

- the changed invariant;
- the owning lane;
- the concrete issue/PR/doc;
- whether the change is accepted, in progress, or proposed.

Keep detailed history out of this file.
