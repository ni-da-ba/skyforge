# SF-IMP-0037 — Native Surface Adaptation Acceptance

**Accepted:** 2026-08-31  
**Branch:** `agent/sf-imp-0037`  
**Base:** SF-IMP-0036 merge `cc4522e75de51318ec14910719d5eaa66702f8af`

## Accepted change

SF-IMP-0037 adds the first Minecraft-native surface material adaptation for Skyforge terrain without introducing Minecraft biome/climate concepts into backend-neutral modules.

After vanilla surface construction and before the accepted Skyforge additive write, the NeoForge adapter snapshots the already-built native surface-top block in each chunk column. Exposed Skyforge top samples above that native surface may inherit the native top block, while Skyforge occupancy, geometry, interior structural semantics and AIR behavior remain unchanged.

## Automated evidence

The final branch passed:

```bat
scripts\verify-sf-imp-0037-native-surface-adaptation.bat
gradlew.bat check
```

The focused verifier establishes:

- backend-neutral module independence;
- NeoForge adapter and development-resource compilation;
- native surface sampling skips air and fluids;
- elevated exposed Skyforge segments inherit the native top material;
- non-top material remains unchanged;
- occupancy and candidate-volume metadata are preserved;
- terrain intersections are not incorrectly treated as floating exposed tops;
- the development runtime uses the native-surface-adapted post-surface binding rather than the legacy load-event path;
- a preserved lower ground surface remains present while Minecraft's final single-valued world-surface heightmap selects the elevated Skyforge surface.

The final heightmap diagnostic initially triggered only a Java `-Werror` warning because its try-with-resources handle was unused. The test was corrected to reference/assert the handle, after which both the focused verifier and repository-wide check passed.

## Real-client evidence

A new `Skyforge Development (SF-IMP-0037)` ModDev world generated the accepted Massif through the post-surface generator path.

Manual inspection established:

- the Massif remained present and geometrically coherent;
- native terrain beneath and around the island remained intact where Skyforge contributes AIR;
- the exposed Massif top appeared remarkably like the surrounding native Minecraft terrain;
- copied native surface representation remained shallow rather than replacing the entire Massif interior;
- downstream world-generation and lighting behavior remained functional;
- save/reload preserved the adapted result without observed duplication or corruption.

## Newly discovered integration boundary

The ordinary ground directly beneath the Massif appeared comparatively sparse in trees/vegetation.

This does not invalidate the material adapter. The accepted diagnostic demonstrates the relevant Minecraft limitation: normal top-surface heightmaps are single-valued per `(x,z)`. Once an elevated Skyforge surface occupies a column, vanilla heightmap-driven placement can target the upper island rather than the preserved lower ground.

Consequently, ground plus one or more vertically stacked islands cannot all be represented as independent top surfaces by one vanilla heightmap. If Skyforge wants native-style vegetation/features on every suitable stacked surface, that requires an explicit multi-surface suitability/placement strategy.

## Accepted invariants

- Minecraft/NeoForge representation remains adapter-owned.
- Backend-neutral geometry and structural terrain semantics remain authoritative for occupancy.
- Surface adaptation never converts Skyforge AIR into solid material.
- Surface adaptation never removes a Skyforge solid.
- No Skyforge climate/biome ontology is introduced.
- The post-surface worldgen insertion accepted in SF-IMP-0036 remains unchanged.
- Development-only validation resources remain non-production configuration.

## Deferred

- native filler/subsurface adaptation;
- snow/shore/ocean-floor/falling-block policy;
- explicit multi-surface feature suitability and placement;
- structure-start awareness;
- production user-facing world configuration;
- Massif morphology/playability refinement;
- broader mod compatibility and performance validation.
