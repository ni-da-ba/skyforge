# Skyforge — SF-IMP-0036 Worldgen Architecture Update

**Snapshot:** 2026-08-31  
**Accepted through:** SF-IMP-0036

This delta advances `Skyforge_Current_Runtime_Architecture.md` through the accepted post-surface world-generation insertion. ADR-0040 remains authoritative for the decision itself.

## New accepted Minecraft path

SF-IMP-0036 supersedes the late `ChunkEvent.Load(isNewChunk=true)` seam as the primary world-generation realization path.

```text
semantic island intent
    -> morphology / compiled island volume
    -> world catalog query
    -> structural terrain semantics
    -> Minecraft block projection
    -> registered skyforge:noise_overlay NoiseBasedChunkGenerator
    -> vanilla noise
    -> vanilla surface construction
    -> Skyforge additive ChunkAccess realization
    -> vanilla carvers
    -> final Minecraft heightmap priming
    -> vanilla feature / biome decoration
    -> lighting
    -> persistent Minecraft chunk/world
```

The old load-event seam remains useful as an accepted historical lifecycle proof, but new terrain-generation work should target the registered generator path.

## What is now empirically established

In addition to the earlier adapter, live-BlockState, client and packaging proofs, Minecraft now demonstrably observes Skyforge terrain during downstream world generation:

- automated final-heightmap priming recognizes elevated Skyforge solid terrain;
- a real development world generated caves through the Massif;
- ores appeared in the elevated terrain;
- trees and other vegetation behavior appeared on the island;
- lighting did not exhibit a systematic late-insertion failure;
- native terrain remained additive rather than being erased by Skyforge AIR;
- the generated island survived save/reload without observed duplication or corruption.

These observations establish participation in the post-surface pipeline. They do not imply that every downstream vanilla system is suitable for floating terrain.

## Remaining timing boundary

`STRUCTURE_STARTS` still occurs before the accepted Skyforge insertion. Structure-aware integration therefore remains a distinct problem; SF-IMP-0036 does not claim that structure location, terrain fitting or start generation understands Skyforge islands.

Vanilla `SurfaceSystem` also runs before Skyforge realization. Biome-native surface appearance must therefore be handled deliberately rather than inferred from the vanilla surface pass.

## Near-term architecture priorities

The most valuable next work is now interpretation and suitability rather than another generic insertion rewrite:

1. backend-native biome/surface material adaptation while preserving backend ownership of biome and registry concepts;
2. geometry-derived suitability facts only where concrete features or structures require them;
3. explicit strategy for structure-start awareness or fitting;
4. morphology/playability refinement informed by real in-game inspection, including the oversized/heavy Massif underside;
5. performance/cache measurement once real-world generation scope expands beyond the finite engineering specimen.

No parallel Skyforge climate model or speculative backend-neutral material taxonomy is implied by this update.
