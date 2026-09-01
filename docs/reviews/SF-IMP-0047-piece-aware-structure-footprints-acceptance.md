# SF-IMP-0047 Piece-Aware Structure Footprints Acceptance

- **Status:** Pending automated proof
- **Date:** 2026-09-01
- **Decision:** ADR-0051

## Acceptance statement

SF-IMP-0047 replaces the single enclosing structure-start support rectangle with a generic union derived from native floor-contact `StructurePiece` bounding boxes while preserving the accepted SF-IMP-0046 vertical-resolution and accommodation rules.

Milestone acceptance is withheld until the exact PR head passes the repository CI/evidence gate and the regressions below are green.

## Required evidence

1. Composite neutral footprints sample required rectangles but not empty envelope gaps.
2. Multiple intentional footprint components can remain coherent when each component has coherent support.
3. Foundation feasibility ignores empty inter-piece gaps.
4. Minecraft extraction retains distinct floor-contact boxes, ignores higher superstructure and deduplicates exact horizontal duplicates.
5. Unrecognized floor geometry falls back to the conservative enclosing start box.
6. `SkyforgeFoundationPiece` serializes the admitted footprint union and refuses columns in the envelope gap.
7. Historical one-rectangle callers retain equivalent behavior.
8. Exact-head CI, tests and evidence publication complete successfully.

No additional interactive Minecraft proof is required unless automated or review evidence exposes behavior that cannot be established from deterministic geometry tests. The accepted SF-IMP-0046 client fixture remains the integration proof for the underlying structure-admission/accommodation lifecycle.
