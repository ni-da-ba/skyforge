# SF-IMP-0049 Native-Piece Underside Separation Acceptance

- **Status:** Accepted
- **Date:** 2026-09-01
- **Decision:** ADR-0053

## Acceptance statement

SF-IMP-0049 introduces proof-grade evidence that one complete native `StructurePiece` integer bounding box lies at or below one exact Skyforge island underside. It does not yet interpret that evidence as permission to reject a structure.

The milestone is accepted because the implementation head passed the full repository CI/evidence gate and demonstrated conservative no-proof behavior for ambiguous or unbounded cases.

## Required evidence

1. Proof-grade native bounding-box translation preserves all X/Y/Z extents and uses sample spacing `1.0`.
2. An observation whose every integer sample is at/below the exact supporting volume underside produces positive separation evidence.
3. A mixed observation produces no evidence.
4. An observation containing `openBetweenSurfaces` produces no evidence.
5. An observation from a different `SkyIslandWorldVolumeId` cannot be promoted into evidence for the requested volume.
6. A piece whose exact proof would exceed one million integer coordinates fails open before runtime observation.
7. The active Minecraft development runtime can prove a small integer bounding box wholly below the exact development island underside.
8. Existing descriptive SF-IMP-0048 observation remains at four-block spacing.
9. No SF-IMP-0049 code path changes `StructureStart` admission, fallback, placement, accommodation, or terrain.
10. Full repository tests and both standard evidence-publication stages pass on the exact head.

CI #179 satisfied all ten automated requirements on implementation head `ebcb201781d11ab55fcef24873e81c1dbbdaa18d`. The documentation-inclusive head must pass the ordinary final CI gate before merge.

## Manual-proof boundary

No interactive Minecraft proof is required for SF-IMP-0049 because it introduces no generation behavior. The first future milestone that consumes this evidence to alter native structure admission will require a dedicated live specimen and interactive acceptance proof before merge.

The user asked to be notified when manual interaction is required again; this milestone remains fully automated.
