# WBY Biome Stack Selection v0.1

**Authority:** #1433 alpha convergence follow-on  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.249

## Decision

### Primary alpha biome vocabulary — Regions Unexplored

Select **Regions Unexplored 0.6.2 (NeoForge 1.21.1)** as the provisional alpha biome/content vocabulary.

Exact artifact:
- version: `0.6.2-neoforge-21.1`
- coordinate: `maven.modrinth:Tkikq67H:5A8LFnXX`
- Modrinth version ID: `5A8LFnXX`

Required library:
- Lithostitched **1.7.9**
- coordinate: `maven.modrinth:XaDC71GB:wiffJSbz`
- Modrinth version ID: `wiffJSbz`

### Why

- Adds a large block/flora/biome vocabulary rather than only reshaping vanilla terrain.
- Current 0.6.x line is native NeoForge 1.21.1.
- Uses Lithostitched rather than adding a separate TerraBlender authority stack.
- Individual biomes remain configurable by toggle/weight, making native placement suppressible.
- Fits the existing exact-volume policy: Skyforge chooses where a biome concept belongs; RU supplies registered biome/block/flora content.

## Authority boundary

```
Regions Unexplored registry/content vocabulary             KEEP
Regions Unexplored native Overworld/Nether placement       SUPPRESS for Skyforge-owned geography
Skyforge island/province biome selection                    SKYFORGE
Skyforge ecology/population authority                       SKYFORGE
RU biome visuals/blocks/features used by selected recipes   REUSE
```

Admission to the distributable alpha baseline requires machine evidence that the RU biome registries remain available while its autonomous biome placement is disabled or redirected.

## Alternatives

### Biomes O' Plenty — RESERVE / fallback

Strong library, broad 1.21.1 support, and configurable biome toggles, but the current 1.21.1 NeoForge backport also adds GlitchCore + TerraBlender and another broad worldgen authority surface. Keep as fallback if RU's actual visual vocabulary is rejected.

### Oh The Biomes We've Gone — RESERVE / visual comparison

Rich 50+ biome/block/mob/structure vocabulary and fine-grained worldgen configuration. It overlaps more of Skyforge's food, structure, mob, and worldgen authority than RU, so it is not the baseline choice.

### Terralith — OMIT for alpha baseline

Excellent terrain/biome datapack, but it primarily replaces terrain/biome layout using vanilla blocks. That duplicates Skyforge's own terrain/biome authorship rather than supplying unique reusable content.

## Alpha convergence note

The previous B3 pin `Lithostitched 1.6.0` was selected only for CTOV closure. The biome decision raises the alpha convergence pin to **1.7.9**, satisfying RU's required floor. Historical C5/Civilization acceptance remains evidence for earlier isolated profiles; the cumulative alpha gate owns this version convergence.
