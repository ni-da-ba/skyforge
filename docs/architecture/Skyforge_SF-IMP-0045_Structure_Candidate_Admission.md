# SF-IMP-0045 — Native Structure Candidate Admission

## Purpose

SF-IMP-0045 lets Minecraft keep its native structure-set selection and `StructureStart` realization while allowing Skyforge to reject a candidate that depended on an elevated Skyforge surface but lacks coherent backend-neutral support.

## Boundary

The Minecraft adapter owns interception. `skyforge-world` remains unaware of Minecraft structures, chunks, bounding boxes, NeoForge, or Access Transformers.

The accepted flow is:

1. Minecraft chooses a native structure candidate.
2. Skyforge opens a thread-confined candidate trace.
3. Vanilla `tryGenerateStructure(...)` runs unchanged.
4. Skyforge records a height provenance claim only where its early height answer is strictly above vanilla terrain.
5. If vanilla creates no start, Skyforge returns vanilla's result.
6. If no Skyforge height contributed, the native start is preserved without additional filtering.
7. If exactly one independent Skyforge volume contributed, the real `StructureStart` bounding box is translated to `SurfaceSupportRequirements` and evaluated by `SkyIslandSurfaceSupportEvaluator`.
8. A supported start remains installed.
9. An unsupported or multi-volume start restores the chunk's previous structure-start map and returns `false`, allowing vanilla's existing weighted fallback loop to continue.

## Access Transformer

Minecraft 1.21.1 keeps `ChunkGenerator.tryGenerateStructure(...)` private. NeoForge's supported Access Transformer mechanism widens only that method to `protected`, enabling the Skyforge `NoiseBasedChunkGenerator` subtype to wrap one candidate without copying `ChunkGenerator.createStructures(...)`.

This is intentionally narrower than reproducing vanilla structure selection or using a general-purpose mixin.

## Initial backend policy

The first Minecraft policy uses the actual native start bounding box with:

- 4-block support sampling;
- 2-block clearance ring;
- 90% minimum interior support;
- 50% minimum clearance support;
- 4-block maximum supported surface relief.

These values are backend policy, not kernel or world-model constants. SF-IMP-0046 may introduce explicit accommodation rather than relaxing geometry truthfulness.

## Invariants

- Vanilla structures not elevated by Skyforge remain Minecraft-owned and unfiltered.
- Vertically stacked Skyforge volumes never fuse into one support claim.
- Multi-volume provenance is rejected rather than guessed.
- Rejected candidates do not leave a `StructureStart` installed.
- Vanilla weighted fallback behavior remains authoritative.
- Skyforge procedural geometry is not flattened or falsified.
- No Minecraft or NeoForge type enters `skyforge-world`.
