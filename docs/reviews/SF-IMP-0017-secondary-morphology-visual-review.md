# SF-IMP-0017 Secondary Morphology Visual Review

**Date:** 2026-08-28  
**Corpus:** `secondary-morphology-suspended-volume-v1`  
**Comparison baseline:** `seeded-suspended-volume-v1`  
**Decision:** Accepted as the first organized secondary-landform proof

## Review question

Does SF-IMP-0017 create coherent landscape-scale organization beyond the bounded local detail accepted in SF-IMP-0016, while preserving the suspended-volume identity contract?

## Findings

The six canonical specimens pass the visual design gate.

The isometric occupancy projection understates the change because its current lighting and viewing geometry compress upper-surface relief. Even there, the structured specimens show altered crowns while retaining the same overall suspended-mass silhouette.

The upper-surface grids and orthogonal cross-sections expose the intended morphology clearly. Across the six seeds, the added relief forms elongated coherent corridors rather than speckled or isotropic noise. The corpus exhibits:

- broad secondary ridges extending across a substantial fraction of the island interior;
- narrower oblique spur structures branching from or crossing the principal relief;
- organized negative-relief corridors that read as valleys rather than random local depressions;
- seed-dependent placement and orientation while retaining a recognizable common primary morphology.

Measured against the SF-IMP-0016 upper surfaces over the positive footprint, the maximum elevation changes are approximately 30 to 39 world units upward and up to about 11 world units downward depending on seed. Mean upper-surface change is about +3.6 to +3.7 world units, so the operator is concentrated rather than uniformly inflating the crown.

The accepted underside grid, underside render, and suspension-plane occupancy render are byte-identical to SF-IMP-0016 for all six seeds. This agrees with the analytical construction: secondary morphology changes only the upper offset, preserves the exact horizontal footprint, and does not alter the accepted underside.

## Numerical context

The canonical six-seed corpus contains 375,742 to 382,278 solid samples. Every specimen retains exactly one connected component, zero domain-face contacts, and 88 world units of minimum sampled air clearance. Relative to SF-IMP-0016, solid occupancy rises by roughly 3.2 percent, but that added volume is spatially concentrated into organized upper landforms.

## Decision

SF-IMP-0017 requirement 9 is accepted. The new layer demonstrates landscape-scale organization distinct from the bounded detail signal and is suitable to become the first secondary-morphology component in later composition work.

This does **not** establish final geological realism. The current proof still uses a small fixed vocabulary of one main ridge, one spur, and one valley basis. Multi-morphology composition, richer ridge networks, drainage-aware structure, escarpments, plateaus, and erosion remain future work.

## Evidence-system follow-up

The isometric evidence renderer should later be improved or supplemented with relief-aware shading, contours, or an explicit delta/height visualization. The present isometric view is sufficient for topology and silhouette review but is weak at communicating organized upper-surface relief.
