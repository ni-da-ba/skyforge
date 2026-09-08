# Exceptional Regional Phenomena v0.1

**Snapshot:** 2026-09-07  
**Status:** Working design direction; records an existing project decision. Not an accepted Authorship or Implementation milestone.

## Purpose

Skyforge may occasionally author large-scale world phenomena that exceed ordinary island-local morphology, ecology, hydrology, or structure grammar.

These phenomena exist to create memorable regional identity and wonder without making exceptional spectacle the ordinary background texture.

This document records the existing project framework so future work does not invent a competing ad hoc system.

## Rarity vocabulary

### Regional

Common within the appropriate province or regional grammar, but absent where that grammar does not apply.

A Regional feature may strongly characterize one province without being globally rare.

### Unusual

Infrequent enough to become a destination, memorable flight, or recognizable landmark.

Unusual features should remain subordinate to ordinary regional geography rather than saturating it.

### Exceptional

Very rare and story-worthy. An Exceptional feature should feel like a genuine discovery rather than a standard checklist item.

Exceptional does not mean visually noisy, mechanically arbitrary, or exempt from provenance and deterministic authorship.

## Existing candidate vocabulary

Examples already discussed for the framework include:

- thermal columns;
- cloud seas or persistent cloud layers;
- aurora / high-altitude luminous activity;
- meteor showers or rare impacts;
- severe electrical-storm regions;
- airborne biological migrations;
- whale migrations;
- debris or wreck belts;
- persistent atmospheric vortices;
- undefined legendary anomalies.

A **floating river or other open-sky watercourse** is an appropriate candidate instance of this framework. It may cross between islands/clusters or exist as a higher-order regional object. It must not be retrofitted by pretending one island-local watershed owns a genuinely cross-island phenomenon.

Other future geological, atmospheric, ecological, hydrologic, or civilization-scale phenomena may enter the same framework when a concrete design consumer exists.

## Hierarchical placement

Exceptional phenomena should respect Skyforge's accepted world hierarchy:

```text
World
-> Province / Region
-> Cluster / Group
-> Island
-> local morphology / ecology / hydrology / geology
```

An individual island remains an exact authored semantic environment.

A phenomenon that genuinely spans islands belongs at the lowest higher-order scope that owns the relationship. Participating islands may expose explicit local interfaces or consequences without losing their independent identity.

## Ownership

- **Authorship** owns backend-neutral phenomenon meaning, participating-world provenance, deterministic relationships, and any required semantic fields.
- **Content / Experience** owns rarity/frequency in the playable game, discovery value, progression consequences, rewards, hazards, and whether a phenomenon belongs in the shipping Wild Blue Yonder experience.
- **Implementation** owns concrete Minecraft/NeoForge realization, rendering, particles, fluids, entity/runtime behavior, persistence, synchronization, and performance.
- **Music / Audio** may consume accepted phenomenon state for presentation but does not define the world truth.

Do not create a duplicate generic anomaly/landmark authority in another lane.

## Presentation constraint

Core phenomenon identity must remain readable without shaders.

Shaders, advanced particles, volumetrics, post-processing, or other enhanced presentation may improve spectacle, but the underlying world feature must remain legible in ordinary supported rendering.

## Design guardrails

- Ordinary world geography establishes the visual rules; rare phenomena may selectively bend them.
- Negative space remains intentional.
- Do not use exceptional features to compensate for weak ordinary morphology or content density.
- Large-scale phenomena should have coherent causes/relationships and exact provenance, even when fantastical.
- Mechanical usefulness is welcome but not mandatory; a phenomenon may primarily be environmental or navigational.
- When gameplay consequences exist, they should emerge from the phenomenon rather than from unrelated reward injection.
- Frequency must preserve surprise. "Exceptional" cannot become a biome checklist.
- New phenomena should reuse the existing hierarchy and world contracts before introducing new generic framework layers.

## Floating-river interpretation

A literal river suspended through open sky is feasible as a future **regional hydrologic phenomenon**, but it is not already covered by AUTH-0086 island-coherent visible hydrology.

A future concrete consumer should define only the missing higher-order contract, potentially including:

- exact phenomenon identity;
- source/participating-island provenance;
- regional path or trajectory;
- entry/exit relationships with participating islands;
- flow semantics;
- bounded exceptional-support semantics;
- deterministic realization handoff.

Do not reinterpret normalized island-local hydrologic potentials as a cross-island physical river path.

## Acceptance principle

> Exceptional phenomena should make the player occasionally discover that Skyforge's world is stranger than its ordinary geography suggests, while remaining deterministic, coherent, sparse, and compatible with the same authorship hierarchy as everything else.
