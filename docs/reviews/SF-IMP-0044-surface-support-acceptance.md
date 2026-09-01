# SF-IMP-0044 — Surface Support Evaluation Acceptance

**Status:** Accepted  
**Date:** 2026-08-31

## Accepted scope

SF-IMP-0044 establishes a backend-neutral structure-sized surface-support evaluator in `skyforge-world`.

The accepted contract consists of:

- `SurfaceSupportRequirements` for a deterministic world-space X/Z footprint, sample spacing, clearance and caller-owned thresholds;
- `SkyIslandSurfaceSupportEvaluator` for independent evaluation of each relevant compiled island volume; and
- `SurfaceSupportAssessment` for immutable geometric diagnostics and the acceptance recommendation.

No Minecraft or NeoForge structure type enters the backend-neutral implementation.

## Geometric evidence produced

For each candidate island, the evaluator reports:

- interior sample count and supported count;
- interior support coverage;
- clearance-ring sample count and supported count;
- clearance support coverage;
- minimum and maximum supported upper-surface elevation;
- height span;
- whether the requested footprint crosses the supporting surface boundary;
- connected support-component count;
- coherent-surface status; and
- final acceptance recommendation.

Support is derived from the candidate island's authoritative compiled upper-surface and density fields rather than backend blocks or voxel-neighbor scans.

## Automated evidence

The focused deterministic test suite proves:

1. a broad flat footprint with supported clearance is accepted;
2. a footprint crossing an island edge is rejected by insufficient coverage;
3. a fully supported but excessively sloped footprint is rejected by height span without flattening the geometry;
4. disconnected support is rejected as incoherent; and
5. two vertically stacked islands sharing the same X/Z footprint remain two independent assessments rather than being fused into a fictitious foundation.

Pull-request CI run #107 completed successfully on commit `bd7f8a5197e98607c158702a6412a910b92c025c`. The repository-wide workflow reached a successful conclusion with the evaluator, focused tests, backend-independence checks and existing evidence generation present.

## Accepted architecture boundary

```text
native/backend candidate
        -> backend derives neutral footprint request
        -> Skyforge surface-support evaluator
        -> independent per-island assessments
        -> backend/policy chooses how to consume the recommendation
```

A generic early height query remains geometrically truthful. Suitability is a separate contract and does not project phantom terrain.

## Explicit limitations

This milestone does not intercept native Minecraft structure candidates, select between native ground and Skyforge surfaces, flatten or repair terrain, assign permanent structure-specific thresholds, or implement structure pieces. Those remain downstream concerns, beginning with the planned SF-IMP-0045 adapter seam.
