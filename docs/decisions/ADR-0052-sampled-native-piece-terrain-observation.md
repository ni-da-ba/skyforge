# ADR-0052: Sampled native-piece terrain observation

- **Status:** Accepted
- **Date:** 2026-09-01
- **Milestone:** SF-IMP-0048

## Context

SF-IMP-0046 established conservative native structure admission/accommodation at a resolved Skyforge surface. SF-IMP-0047 then replaced the enclosing start rectangle with generic floor-contact piece footprints.

The next compatibility question is three-dimensional: native and modded `StructurePiece` geometry can extend upward, downward or through open space relative to one independently compiled Skyforge island. Skyforge needs a way to observe those relationships before it can responsibly decide whether any of them constitute a physical contradiction.

A structure taxonomy would undermine Minecraft naturalization, while immediately turning sparse samples into rejection policy would overstate what the geometry proves.

## Decision

Skyforge SHALL add a backend-neutral, read-only sampled observation seam for finite 3-D world-space boxes.

The existing `SkyIslandTerrainInterpreter` SHALL remain authoritative. It exposes the compiled upper surface, underside surface and signed density as continuous world-space queries; no parallel terrain model is introduced.

For each sampled coordinate relative to one independently compiled island volume, the observer SHALL report exactly one of four factual categories:

1. solid compiled Skyforge terrain;
2. air at or above the compiled upper surface;
3. air at or below the compiled underside surface;
4. air between the two surfaces.

Air between surfaces SHALL deliberately remain semantically unresolved. It may represent a cave, lateral exterior, concavity, opening or other valid geometry. The observation layer SHALL NOT guess which.

## Sampling contract

`TerrainBoxObservationRequirements` carries finite closed `WorldBounds` plus caller-owned sample spacing.

`TerrainBoxObservation` reports category counts and convenience predicates such as all-observed-solid or all-observed-below-underside. It SHALL NOT contain an accepted/rejected result.

The reference observer SHALL cap one request at one million samples so malformed or unexpectedly large native geometry cannot cause unbounded work at this seam.

Sparse observation describes only sampled coordinates. Unobserved space SHALL NOT be interpreted as proven solid, proven air or proven incompatibility.

## Minecraft translation

NeoForge SHALL translate ordinary native `StructurePiece` bounding boxes directly to neutral `WorldBounds` and use a backend-owned four-block sampling interval for the first observation seam.

Every native piece is treated identically. Skyforge does not inspect structure type, registry identity or semantic role.

The active runtime SHALL support observing a box against one exact `SkyIslandWorldVolumeId` without spatially prefiltering the requested box. This is necessary because useful evidence may consist precisely of native geometry lying above or below the volume's conservative bounds.

`MinecraftStructurePieceTerrainObserver` is read-only in SF-IMP-0048. The chunk generator SHALL NOT consume these observations to reject, translate, excavate or otherwise modify structures in this milestone.

## Consequences

### Positive

- Unknown/modded structures automatically expose the same generic 3-D physical evidence.
- Existing upper/underside/density semantics are reused instead of duplicated.
- Vertically stacked island volumes remain independent because each observation names one exact volume.
- Future policy can distinguish positive evidence from uncertainty without structure-specific adapters.
- No new terrain mutation or accommodation behavior is introduced.

### Tradeoffs

- Sampling is evidence, not an exact occupancy proof.
- Piece bounding boxes still over-approximate actual placed blocks.
- Open air between surfaces remains intentionally ambiguous.
- A later milestone must define which observations, if any, are strong enough to justify a veto.

## Rejected alternatives

### Add immediate underground-structure rejection

Rejected because the first task is to prove observation semantics. Minecraft naturally permits caves, exposed mineshafts and other open underground geometry; a blanket enclosure rule would be incompatible.

### Classify structures by type

Rejected because physical observation should automatically benefit unknown modded structures.

### Infer lateral exterior from density-negative samples between surfaces

Rejected because the same signal can describe legitimate internal caves or concavities.

### Inspect actual placed blocks

Rejected at this seam because it couples observation to later structure placement and generation order.

## Validation

SF-IMP-0048 requires automated evidence for:

- wholly solid sampled boxes;
- wholly at/above-crown boxes;
- wholly at/below-underside boxes;
- open-between-surfaces samples remaining explicitly ambiguous;
- mixed boundary observations remaining mixed rather than collapsing to policy;
- independent observations of vertically stacked volumes;
- faithful Minecraft `BoundingBox` to neutral 3-D bounds translation;
- complete repository CI and evidence publication.

CI #175 passed the complete repository build/test suite and both standard evidence-publication stages on implementation head `be4e845c93a2a037ec0075d0dd5dca61763a3d18`. The acceptance record is therefore **Accepted**; the documentation-inclusive PR head remains subject to the ordinary final exact-head CI gate before merge.
