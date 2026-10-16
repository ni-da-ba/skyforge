# Association-bound hydrology world frame

Owner approved this projection-contract change on 2026-10-15 under #1452 / PR #1490.

## Reason

The old pointwise terrain-relative bridge preserved semantic depth but introduced up to 1.410379951 world units of transverse stage variation in 55 wet cross sections of the F4K specimen. Both reviewed centerlines are straight, so the independently measured bend-interpolation defect is not the explanation for this specimen.

## Method and authority

`SkyIslandComponentFluvialTerrainCandidatePlanner.planAssociated(association)` retains F3E component admission and C2 routes, but samples the exact associated compiled upper surface as the original terrain:

```text
datum = compiledVolume.descriptor.suspensionElevation
physicalPotential = (compiledUpperWorld - datum) / authoredReliefBudget
```

The existing bounded candidate builder, game-calibrated discharge/section model, Manning source normal-depth boundary, standard-step solve, and unchanged D2 qualification run against this physical terrain. A physical elevation outside the supported normalization/edit domain rejects rather than silently rescaling or selecting another datum.

The unchanged algebraic projection formulas now cancel the physical original surface:

```text
targetUpperWorld = datum + candidateTargetPotential * reliefBudget
waterWorld       = datum + solvedStagePotential * reliefBudget
```

Thus bed and water share one fixed frame instead of inheriting a spatially varying residual. This is an association-specific continuous solve before voxelization, not a Minecraft water clamp. It does not claim that old placement-free depths remain authoritative after the physical terrain changes.

The candidate carries its exact association. Projection and quantization reject another association, and associated candidates reject an unqualified replacement/refinement field. Placement-free `plan(descriptor)` remains available for semantic authorship and legacy evidence, but is not the new F4K realization input.

## Retained limits

- Exact authored/realized association and volume ownership.
- Existing source/ordinary/edge-outlet support only; no new confluence, cascade, lake or terminal authority.
- Removal-only terrain and unchanged edit-envelope, underside and exact compiled support admission.
- Unchanged hydraulic solver equations, benchmark tolerances and D2 limits.
- Rejected components grant no terrain/water authority.
- Integer water remains restricted to authorized carved support; no backend deepening, filling or grade repair.
- Spatially seeded physical detail means each placement must be solved and admitted independently, not transplanted by translation.

F4K now opts into this path. Preparation reports `worldElevationFrame=associated-physical-v1` and verifies every permitted persisted water voxel, not just each column's lowest water voxel. This does not by itself certify connectivity or visual quality.

## Evidence and references

The initial production implementation at 4002f74772d573ddd40489ef2a46a30271798049 passes the focused hydraulic/control suite and the new 55-section level-stage, D2, exact association and voxel-admission regression. Further bed-identity and integer-connectivity measurements accompany runtime integration; final runtime evidence is recorded in the PR.

[USACE RAS Mapper](https://www.hec.usace.army.mil/confluence/rasdocs/rmum/latest/mapping-results/managing-results-maps) treats terrain and water-surface elevations in a shared spatial representation and derives depth by difference. [USACE cross-section interpolation](https://www.hec.usace.army.mil/confluence/rasdocs/rasum/6.3/entering-and-editing-geometric-data/cross-section-interpolation) provides the connected bank/invert interpolation precedent. This tranche reuses the existing 1D solvers; it does not implement HEC-RAS, triangulated bend repair, or a 2D hydrodynamic model.
