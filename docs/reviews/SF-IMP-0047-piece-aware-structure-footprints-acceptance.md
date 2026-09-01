# SF-IMP-0047 Piece-Aware Structure Footprints Acceptance

- **Status:** Accepted
- **Date:** 2026-09-01
- **Decision:** ADR-0051

## Acceptance statement

SF-IMP-0047 replaces the single enclosing structure-start support rectangle with a generic union derived from native floor-contact `StructurePiece` bounding boxes while preserving the accepted SF-IMP-0046 vertical-resolution and accommodation rules.

The milestone is accepted on deterministic automated evidence. No additional interactive Minecraft proof is required because SF-IMP-0047 changes generic footprint observation and persistence rather than the already-proven live structure lifecycle.

## Accepted evidence

1. Composite neutral footprints sample required rectangles but not empty envelope gaps.
2. Multiple intentional footprint components remain coherent when each component has coherent support.
3. Foundation feasibility ignores empty inter-piece gaps.
4. Minecraft extraction retains distinct floor-contact boxes, ignores higher superstructure and deduplicates exact horizontal duplicates.
5. Unrecognized floor geometry falls back to the conservative enclosing start box.
6. `SkyforgeFoundationPiece` serializes the admitted footprint union and refuses columns in the envelope gap.
7. Historical one-rectangle callers and SF-IMP-0046 serialized foundations retain compatible behavior.
8. The complete repository CI, test and evidence-publication gate passes on the corrected implementation head.

## CI history

CI #166 exercised the new implementation successfully through compilation and NeoForge tests, then failed two newly added neutral footprint tests. The cause was confined to the synthetic test volume: its density boundary coincided exactly with sampled footprint coordinates, where density is correctly zero and therefore unsupported. The fixture was expanded one world unit beyond the footprint; production geometry and policy thresholds were unchanged.

CI #167 passed on head `05336345728a9610eb86e3a18eaac680913cc651`, including:

- repository build and test gate;
- composite footprint and foundation regressions;
- Minecraft piece-extraction and persistence regressions;
- fixed-seed evidence and visual-atlas publication;
- suspended-volume evidence and visual-atlas publication.

This closes the substantive acceptance gate. PR #48 is eligible for squash merge once the documentation-inclusive final head also passes CI.
