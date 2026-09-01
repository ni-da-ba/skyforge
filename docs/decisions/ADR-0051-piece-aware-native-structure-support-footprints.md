# ADR-0051: Piece-aware native structure support footprints

- **Status:** Accepted
- **Date:** 2026-09-01
- **Milestone:** SF-IMP-0047

## Context

ADR-0049 introduced native structure support admission and ADR-0050 added conservative fill-only accommodation. Both deliberately used the enclosing `StructureStart` bounding box as the first generic approximation of structure footprint.

That approximation is safe but can be materially larger than the native pieces that actually contact terrain. Sparse structures, multi-building starts, courtyards and modded layouts can therefore be rejected because terrain is missing in empty space where the structure itself places nothing. The same envelope can also cause fill-only accommodation to place foundation beneath those empty gaps.

A structure-specific compatibility table would solve individual cases at the cost of Minecraft naturalization. Skyforge instead needs a generic representation that consumes geometry already exposed by native `StructurePiece` objects.

## Decision

For vertically resolved starts admitted to the existing SF-IMP-0046 seam, Skyforge SHALL derive horizontal support geometry from native pieces whose bounding-box minimum Y equals the resolved `StructureStart` minimum Y.

Exact duplicate horizontal rectangles SHALL be removed. Pieces above that plane SHALL NOT enlarge support geometry merely because they project over the same X/Z area.

If no native piece touches the resolved start minimum Y, Skyforge SHALL retain the enclosing `StructureStart` bounding box as a conservative fallback.

### Neutral footprint union

`skyforge-world` SHALL represent a support footprint as a non-empty union of axis-aligned world-space X/Z rectangles.

Surface support and foundation evaluators SHALL sample only points contained by that union. Empty space inside the union's bounding envelope SHALL NOT count toward coverage, relief, fill depth, excavation checks or required foundation columns.

Clearance SHALL be sampled from the union of individually expanded footprint rectangles minus the footprint itself.

### Coherence

A footprint may contain multiple intentional connected components. Surface coherence SHALL therefore be evaluated relative to required footprint components: each sampled required component must retain exactly one supported component.

A disconnected native footprint is not invalid merely because its buildings are separated. Unexpected fragmentation within one required component remains invalid.

### Minecraft persistence

`SkyforgeFoundationPiece` SHALL retain one Minecraft bounding envelope for normal structure-piece lifecycle and chunk clipping, while serializing the admitted horizontal footprint rectangles separately.

Foundation realization SHALL skip every column outside that serialized union even when the column lies inside the piece's enclosing Minecraft bounding box.

Serialized SF-IMP-0046 foundation pieces without explicit footprint data SHALL remain readable and SHALL fall back to their historical bounding envelope.

## Consequences

### Positive

- Unknown vanilla/modded multi-piece structures benefit automatically without Skyforge knowing their type.
- Empty courtyards and gaps no longer require terrain support merely because they lie inside the overall start envelope.
- Fill-only accommodation no longer creates foundation beneath those gaps.
- Higher structure pieces no longer enlarge ground-support requirements.
- Existing rectangular neutral callers retain a convenience constructor and unchanged semantics.
- The proven SF-IMP-0046 vertical lifecycle rules remain intact.

### Tradeoffs

- Piece bounding boxes remain an approximation; they can include empty cells inside an individual piece rectangle.
- Structures with multiple independently resolved support elevations are still outside this milestone.
- The serialized foundation stores additional footprint metadata.
- Composite-footprint sampling and component analysis are somewhat more expensive than one rectangle, though still bounded by structure-sized areas and only used at the structure seam.

## Rejected alternatives

### Continue using only the enclosing `StructureStart` box

Rejected because it creates false terrain requirements and false foundation fill in empty inter-piece space.

### Maintain per-structure footprint rules

Rejected because it would make unknown/modded structures require Skyforge-specific compatibility knowledge.

### Inspect actual placed blocks

Rejected because it would couple admission to later placement phases, introduce generation-order problems, and require deeper knowledge of structure implementation details.

### Treat every `StructurePiece` projection as ground support

Rejected because upper floors and roof pieces are not terrain-contact requirements.

### Remove the conservative fallback

Rejected because an unfamiliar structure that exposes unusual piece geometry should not be rejected merely because Skyforge cannot identify a floor-contact piece.

## Validation

SF-IMP-0047 acceptance required automated evidence that:

1. a two-rectangle neutral footprint samples the rectangles but not the envelope gap;
2. intentionally separated footprint components remain coherent when each is independently supported;
3. foundation fill counts only footprint columns;
4. Minecraft extraction retains distinct floor-contact boxes, ignores higher pieces and deduplicates exact horizontal duplicates;
5. extraction falls back to the enclosing start box when no piece touches the resolved floor;
6. serialized foundations retain multiple footprint rectangles and refuse inter-piece gap columns;
7. the complete repository CI/evidence gate passes on the exact PR head.

CI #166 reached the new implementation and failed only two new neutral regression assertions because their synthetic density volume ended exactly on the sampled footprint boundary. Skyforge correctly treats zero density at that mathematical boundary as unsupported. The fixture was expanded one world unit beyond the tested footprint without weakening production support, coherence, accommodation, or edge-rejection rules.

CI #167 then passed on corrected implementation head `05336345728a9610eb86e3a18eaac680913cc651`, including the complete build/test gate, fixed-seed evidence publication and suspended-volume evidence publication. This satisfies the substantive automated acceptance criteria. A final documentation-inclusive exact-head CI remains the merge gate for PR #48.
