# ADR-0059: Exact-volume authored native surface rules

**Status:** Candidate — requires DR-70 human visual acceptance
**Scope:** Minecraft/NeoForge representation only

## Context

ADR-0041 proved that Skyforge terrain can inherit Minecraft-native surface material without
changing authored occupancy. Its original implementation sampled the already-generated BASE_WORLD
surface at the same X/Z column. Later milestones made island ecology, native population, and
persistent biome identity exact-volume-owned.

That leaves a coherence defect: an island may author forest, wetland, alpine, or another ecological
regime while its literal surface material still reflects an unrelated biome beneath the island.
A hidden donor world has the same defect unless the donor itself is driven by island authorship.

## Decision

For physically admitted production islands, Skyforge will evaluate Minecraft's native SurfaceSystem
against an isolated in-memory chunk containing only one exact island volume.

The biome source for that evaluation is the same exact-volume ecology resolver used by native
surface population and persistent biome presentation.

Therefore:

- island identity and semantic fields author ecology;
- authored ecology determines Minecraft biome carriers per surface region;
- authored freshwater/riparian context may select its accepted wet carrier;
- Minecraft's registered overworld surface rules choose concrete blocks;
- biome transitions occur only where the island's authored fields imply them;
- the biome or terrain physically beneath a suspended island has no representation authority;
- no hidden donor dimension is required;
- native rules may change material but may not change Skyforge occupancy.

The surface evaluation runs on a scratch ProtoChunk. Exact owned solids are normalized to the
backend's default overworld stone while authored hydrology AIR/fluid cells remain intact. The result
is copied back only within the authorized surface representation envelope: the accepted
SURFACE_MANTLE semantic plus a shallow profile beneath newly exposed channel surfaces.

## Ordering

```text
physical admission
-> exact terrain realization
-> authored hydrology geometry/water
-> exact-volume authored native SurfaceSystem
-> native surface population
-> composed caves
-> native interior population
-> persistent exact-volume biome presentation
```

The native surface stage reuses the population plan's biome resolver rather than defining a second
mapping. Unsupported ecology columns fail closed and do not consume the resolver's technical PLAINS
fallback.

## Compatibility

Concrete surface blocks remain Minecraft/registry-owned. Datapack or modded changes that participate
in the active overworld surface-rule registry can therefore remain representation authority without
introducing Minecraft block palettes into backend-neutral Skyforge authorship.

Specialized future island classes, including ocean-bowl islands, may add semantic policy later.
They are not required for the baseline DR-70 corpus.

## Acceptance

This candidate supersedes the BASE_WORLD-sampled surface-material path for physically admitted
production islands only after DR-70 human review confirms coherent morphology, hydrology, ecology,
topside/edge/underside presentation, caves, persistence, and cross-system composition across the
frozen corpus.
