# SF-IMP-0048 Native-Piece Terrain Observation Acceptance

- **Status:** Accepted
- **Date:** 2026-09-01
- **Decision:** ADR-0052

## Acceptance statement

SF-IMP-0048 introduces read-only, backend-neutral sampled 3-D terrain observation for native structure-piece geometry. It intentionally does not add any new structure veto or accommodation behavior.

The milestone is accepted because the implementation head passed the full repository CI/evidence gate and proves that sampled evidence remains descriptive rather than prescriptive.

## Required evidence

1. A box wholly inside one compiled island reports every sampled coordinate as solid.
2. A box wholly at or above the upper surface reports only upper-side air.
3. A box wholly at or below the underside reports only underside-side air.
4. Density-negative samples between upper and underside surfaces remain `openBetweenSurfaces` rather than being mislabeled as exterior or invalid.
5. A box crossing a surface reports mixed evidence rather than a binary decision.
6. Vertically stacked volumes are observed independently under their exact `SkyIslandWorldVolumeId` identities.
7. Minecraft native `StructurePiece` bounding boxes translate all X/Y/Z extents faithfully into neutral `WorldBounds` with backend-owned sampling policy.
8. The active Minecraft runtime can observe one exact claimed volume without merging or substituting another island.
9. No SF-IMP-0048 code path changes `StructureStart` admission, fallback, placement or terrain geometry.
10. Full repository tests and both standard evidence-publication stages pass on the exact implementation head.

## Evidence result

CI #175 passed the complete repository build/test suite and both standard evidence-publication stages on implementation head `be4e845c93a2a037ec0075d0dd5dca61763a3d18`.

Two earlier CI failures were fixture/harness defects and were corrected without weakening production invariants:

- CI #173: strict `-Werror` rejected an intentionally unused `AutoCloseable` test resource variable;
- CI #174: synthetic world-volume fixtures used a `SkyIslandWorldVolumeId.geometrySeed` that differed from the compiled descriptor seed, correctly triggering the existing identity invariant.

The corrected tests preserve the intended exact-volume identity contract and all required observation semantics pass.

## Merge boundary

No additional interactive Minecraft proof is required for this milestone because the new behavior is read-only observation and does not alter generation results. Any future use of these observations for rejection or adaptation requires a separate milestone, separate decision record and explicit acceptance gate.

PR #49 is accepted for merge once the documentation-inclusive exact head passes the ordinary final CI gate. The user explicitly authorized PR #49 with “Merge and proceed” on 2026-09-01.
