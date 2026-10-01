# ADR-0015: Suspended volume before composition

**Status:** Accepted by SF-IMP-0012
**Date:** 2026-08-03
**Ticket:** SF-IMP-0011

## Context

The v0.1 proof established deterministic semantic description, graph compilation, sampling,
evidence generation, and regression identity. Its two-dimensional height field makes a closed
island silhouette when viewed from above. Its derived density field is
`D(x,y,z) = H(x,z) - y`, however, so positive density continues indefinitely downward.

That construction is a valid terrain-kernel specimen, but it does not yet express the defining
identity of a sky island: a finite geological mass suspended in air. Adding secondary ridges,
valleys, or composition to the half-space density would enrich the wrong primary volume.

## Decision

1. Treat the v0.1 corpus as a frozen architectural and numerical proof, not as the final sky-island
   morphology.
2. Make a finite suspended solid the first v0.2 implementation milestone. Secondary morphology
   and multi-feature composition follow only after that milestone passes.
3. Preserve the v0.1 `IslandDescriptor`, recipe versions, graph schemas, and golden corpus. The
   suspended-volume work uses a new semantic descriptor and new versioned recipe rather than
   changing released bytes in place.
4. Represent the volume through an inspectable three-dimensional signed density field with
   positive values only inside the solid, zero on its upper or lower boundary, and negative values
   throughout surrounding air.
5. Model the upper surface and underside as separately inspectable morphology. Their shared
   intersection determines the outer silhouette; neither a ground plane nor an implicit
   downward-filled half-space may participate in the result.
6. Add only the smallest graph-composition primitive needed to combine upper and lower constraints.
   ADR-0016 accepts a three-dimensional positive-inside intersection evaluated as exact pointwise
   `Math.min`, together with its binary64, validation, canonical-serialization, and compatibility
   behavior.
7. Require three-dimensional numerical evidence and views that reveal the underside. A top-down
   height map remains useful, but it cannot by itself satisfy a suspended-volume gate.

## Consequences

- The next proof emphasizes primary morphology correction rather than decorative sophistication.
- The descriptor must name upper elevation, underside depth, underside taper, and bounded
  asymmetry in semantic terms without naming graph nodes or algorithms.
- Sampling and evidence expand from a two-dimensional surface grid to a bounded three-dimensional
  volume grid.
- Exact v0.1 regression checks remain in every v0.2 build, so the new proof adds capability without
  silently changing the released one.
- Composition, secondary ridges and valleys, materials, climate, ecology, decoration, NeoForge,
  and optimization remain deferred until the suspended-volume prerequisite passes.

## Visual interpretation

The decisive images are orthogonal vertical slices, a deterministic isometric occupancy render,
and an underside projection. They must visibly show air above, below, and on every side of one
finite connected mass. The images explain the claim; canonical density grids, metrics, and hashes
decide it.
