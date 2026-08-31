# Skyforge — SF-IMP-0037 Surface Adaptation Architecture Update

**Snapshot:** 2026-08-31  
**Accepted through:** SF-IMP-0037

This delta advances the accepted Minecraft integration architecture through the first native surface material adaptation. ADR-0041 remains authoritative for the decision itself.

## Accepted realization path

```text
semantic island intent
    -> morphology / compiled island volume
    -> world catalog query
    -> structural terrain semantics
    -> Minecraft engineering block projection
    -> registered skyforge:noise_overlay generator
    -> vanilla noise
    -> vanilla surface construction
    -> snapshot native Minecraft surface-top material per column
    -> adapt exposed Skyforge top representation
    -> additive Skyforge ChunkAccess write
    -> vanilla carvers
    -> final Minecraft heightmap priming
    -> vanilla feature / biome decoration
    -> lighting
    -> persistent Minecraft chunk/world
```

The new adaptation does not move Minecraft biome or block concepts upstream. Skyforge still controls geometry, occupancy and structural meaning; the Minecraft backend controls concrete representation.

## What is now empirically established

In addition to the SF-IMP-0036 worldgen participation proof:

- exposed Skyforge tops can inherit Minecraft's already-built native surface material;
- the adaptation remains shallow and does not replace the island interior globally;
- Skyforge AIR remains non-destructive;
- the Massif remains persistent and geometrically coherent in a real client;
- downstream worldgen and lighting continue to function after adaptation;
- no parallel Skyforge climate or biome table is required for this first native representation step.

## Newly established heightmap boundary

A vanilla top-surface heightmap is single-valued per `(x,z)`.

When ordinary ground and an elevated Skyforge island coexist in one column, final heightmap priming selects the upper Skyforge surface as the world-surface target while the lower ground remains physically present. This is now captured by an automated regression test and is consistent with the observed sparse vegetation directly below the development Massif.

This has an important architectural consequence for future vertically stacked islands: vanilla heightmaps alone cannot describe all independently decoratable surfaces in a column.

Skyforge should not solve that by moving Minecraft feature definitions or climate logic upstream. Instead, any future solution should expose only the backend-neutral geometric/suitability facts actually required, then let the Minecraft adapter decide how to place or re-run appropriate backend-native features across multiple valid surfaces.

## Near-term priorities

1. Design a minimal multi-surface suitability/placement boundary for vegetation and other heightmap-driven features.
2. Preserve compatibility with vanilla/modded feature systems rather than cloning their environmental semantics upstream.
3. Investigate structure-start awareness separately; structure timing still precedes the accepted post-surface insertion.
4. Refine morphology/playability after integration invariants are stable, including the observed heavy/oversized Massif underside.
5. Measure generation performance and caching behavior as test worlds expand beyond the finite engineering specimen.

No speculative material ontology, parallel climate model, or assumption that every surface must receive every vanilla feature is implied by this update.
