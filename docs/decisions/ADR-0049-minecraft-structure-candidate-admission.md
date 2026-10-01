# ADR-0049: Minecraft structure candidate admission

- **Status:** Accepted
- **Date:** 2026-08-31
- **Milestone:** SF-IMP-0045

## Context

SF-IMP-0043 proved that native Minecraft structure generation can observe Skyforge through the early `ChunkGenerator.getBaseHeight(...)` seam. SF-IMP-0044 then established a backend-neutral evaluator for structure-sized support footprints without falsifying procedural geometry.

The remaining problem is where the Minecraft backend should apply that neutral support decision. Reimplementing `ChunkGenerator.createStructures(...)` would copy vanilla structure-set selection, weighted fallback, reference handling and future implementation details into Skyforge. Filtering only after the entire structure-start phase would also lose vanilla's per-candidate fallback semantics.

Minecraft 1.21.1 already has a narrow per-candidate method, `ChunkGenerator.tryGenerateStructure(...)`, but declares it private.

## Decision

Use NeoForge's supported Access Transformer mechanism to widen only `ChunkGenerator.tryGenerateStructure(...)` from private to protected, then override that method in `SkyforgeNoiseBasedChunkGenerator`.

The override SHALL:

1. remain inert when no Skyforge runtime binding is active;
2. snapshot the chunk's pre-candidate structure-start map;
3. open a thread-confined Skyforge height-provenance trace;
4. call the vanilla superclass implementation exactly once;
5. preserve vanilla failure unchanged;
6. preserve a successful start unchanged when no Skyforge early height strictly exceeded vanilla terrain;
7. reject rather than guess when more than one independent Skyforge volume contributed height provenance;
8. translate the real successful `StructureStart` bounding box into backend-neutral `SurfaceSupportRequirements`;
9. evaluate only the claimed independent world volume through SF-IMP-0044 diagnostics;
10. preserve the start when accepted;
11. otherwise restore the previous structure-start map and return `false`, allowing vanilla's surrounding weighted candidate loop to continue.

## Height provenance

The early Minecraft query is enriched internally from a scalar height to a `MinecraftSkyforgeHeightClaim` containing the height and the independently compiled `SkyIslandWorldVolumeId` values owning that top sample.

A claim is recorded for structure admission only when the Skyforge height is strictly greater than the vanilla height. Equal-height cases remain Minecraft-owned.

This avoids treating coincidental X/Z overlap as proof that a native structure belongs to a Skyforge island and preserves vertically stacked islands as separate surfaces.

## Initial Minecraft policy

The first backend policy translates the actual native start bounding box with:

- sample spacing: 4 blocks;
- clearance: 2 blocks;
- minimum interior support: 0.90;
- minimum clearance support: 0.50;
- maximum supported surface height span: 4 blocks.

These values belong to the Minecraft backend and are not kernel or world-model constants. Later structure-specific policies may refine footprint interpretation without changing the neutral evaluator.

## Consequences

### Positive

- Skyforge does not copy vanilla `createStructures(...)` logic.
- Native structure-set weighting and fallback remain authoritative.
- The evaluator consumes the real native start footprint rather than an invented pre-generation approximation.
- Native-ground structures remain untouched when Skyforge did not elevate their placement.
- Stacked islands cannot fuse into a phantom support surface.
- Rejection does not mutate procedural island geometry.

### Tradeoffs

- The backend intentionally depends on one Access Transformer entry tied to the Minecraft 1.21.1 method descriptor.
- The initial policy uses the overall `StructureStart` bounding box and is conservative for sparse multi-piece structures.
- Only structure placement paths that actually consume the accepted early Skyforge height seam can produce Skyforge height provenance. Broader native structure compatibility remains an adapter concern.

## Rejected alternatives

### Copy `ChunkGenerator.createStructures(...)`

Rejected because it would duplicate vanilla selection and weighted fallback machinery and increase version-coupling substantially.

### Post-filter every structure after `createStructures(...)`

Rejected because the surrounding vanilla loop would already have considered the candidate successful, preventing normal weighted fallback when Skyforge rejects the site.

### Put Minecraft structure semantics into `skyforge-world`

Rejected because structure types, native bounding boxes and NeoForge integration are backend concepts. The world layer exposes only neutral surface support semantics.

### Flatten or alter an island until the start fits

Rejected for SF-IMP-0045. Geometry accommodation is a distinct later policy problem and must not be disguised as support evaluation.

## Evidence

CI #112 passed the full repository gate on the implementation head. The NeoForge module compiled after the Access Transformer was applied, and transformed-runtime tests verified that `ChunkGenerator.tryGenerateStructure(...)` is actually protected in the ModDev test environment. The same test suite verifies independent-volume height provenance, multi-volume trace separation and native bounding-box translation.
