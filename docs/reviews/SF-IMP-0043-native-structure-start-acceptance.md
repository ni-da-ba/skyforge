# SF-IMP-0043 — Native Structure-Start Height Visibility Acceptance

**Status:** Accepted
**Date:** 2026-08-31

## Accepted scope

SF-IMP-0043 proves that Minecraft's native structure-start path can consume Skyforge-aware early generator height queries before the floating island is physically realized in the chunk.

The proof uses vanilla `minecraft:desert_pyramid`; Skyforge does not own the structure pieces, templates, bounding boxes, or concrete block placement.

## Automated evidence

The focused verifier and repository-wide `gradlew.bat check` passed after the Windows PowerShell verifier fix. The verifier also proves that all structure-forcing data remains development-only and absent from the production JAR.

## Client evidence

The development world generated several forced vanilla desert pyramids. One candidate outside the Massif remained on ordinary native terrain, while several candidates overlapping the Massif were vertically associated with elevated Skyforge geometry and clipped into the island at varying depths.

This proves the early height bridge is visible to a real native structure path. It does **not** prove that arbitrary native structures are suitable for arbitrary island footprints.

The same world was saved, closed, reopened, and confirmed clean. The Massif and structure results persisted without codec/registry errors or duplicate regeneration.

## Newly exposed boundary

Structure visibility and structure suitability are separate contracts. Future work must reason explicitly about footprint coverage, coherent target surface, slope tolerance, edge clearance, and eventually stacked-island choice. Generic `getBaseHeight(...)` must remain geometrically truthful rather than projecting phantom support.

The same client run also confirmed that aquatic vegetation such as kelp is still not replayed beneath an elevated island; that remains a separate multi-surface ecology gap.
