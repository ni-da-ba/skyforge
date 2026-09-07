# Skyforge program charter

**Status:** canonical durable program charter  
**Updated:** 2026-09-06  
**Repository:** `ni-da-ba/skyforge`

## Objective

Skyforge's target is not a demo island. It is a Minecraft world in which a semantic floating-world model remains coherent under ordinary survival play, exploration, building, automation, destruction, persistence, multiplayer operation, and long-range travel.

The production world should increasingly express:

```text
regional intent
-> island morphology
-> geology / materials
-> hydrology
-> caves / structures
-> soils / ecology
-> Minecraft realization
-> persistent gameplay
```

Correctness is mandatory but no longer sufficient. Work should be judged by:

> Does this bring the actual production Minecraft world closer to a fast, beautiful, semantically coherent, playable Skyforge experience?

## Durable-memory policy

Repository state supersedes conversational recollection.

Authoritative development evidence is, in order:

1. merged Git history and current source;
2. automated tests / acceptance workflows and recorded review evidence;
3. canonical lane state and cross-lane contracts under `docs/handoffs/`;
4. issue/PR discussion for unresolved rationale;
5. conversation only as active-session working context.

Speculative or in-progress work must never be represented as accepted state.

## Major lanes

### Authorship

Owns backend-neutral semantic world intent and compilation contracts: morphology, hierarchy, geology, hydrology, ecology, cave intent, reservations, publication identities, and other world-model semantics.

Authorship must not import Minecraft/NeoForge implementation concepts merely to simplify the adapter.

### Implementation

Owns physical realization in Minecraft/NeoForge: exact-domain mapping, generation lifecycle, native-feature composition, persistence, performance, structure realization, safety/fencing, and player-visible runtime integration.

Implementation consumes authored contracts; it must not silently replace them with Minecraft-specific approximations.

### Content

Owns gameplay/content realization requirements: progression, aircraft, mobility, structures/civilizations, resources, quests/guidance, encounters, and bootstrap-province gameplay intent.

Implementation should expose generic physical/runtime seams instead of hard-coding one content asset when a reusable contract is possible.

### Other specialist lanes

Music, compatibility, tooling, visual review, and future specialist lanes own their domain artifacts but must publish durable cross-lane requirements when another lane depends on them.

## Long-range convergence

Near-term sequence:

1. production-quality native feature plausibility and legible ecology;
2. full authored morphology through the real Minecraft lifecycle;
3. human visual acceptance from above, rim, section, below, and flight;
4. geology/material and authored hydrology realization;
5. structure reintegration across surface, embedded, underside/cliff, detached, and structure-seeded modes;
6. deterministic Bootstrap Province as the first true game vertical slice.

Later expansion includes province-scale grammar, rare terrain, infrastructure/civilizations, mature resources and industry, Nether/End realization, multiplayer/server validation, long-duration persistence, upgrade resilience, and release configuration.

## Acceptance principle

Machine proof should reject objective invalid states. Human review remains authoritative for aesthetics, legibility, and player-facing experience where machine metrics cannot substitute for judgment.
