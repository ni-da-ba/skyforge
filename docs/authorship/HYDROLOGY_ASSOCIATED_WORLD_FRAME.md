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

## Owned-water capacity and rejected placement trial

The first integrated shared-frame proof passed (run 38830308214): 55 level sections, 9,255 admitted columns, two face-connected rivers, no missing centerline samples, and 3,928 persisted water voxels. Its integer diagnostic still found 219 water columns whose requested surface exceeded the exact original solid ceiling. That intermediate proof does not certify un-clipped stage realization.

A rigid bed/stage lowering trial is archived at b08c5f229d75ae3ecded7ae7e16e86e9a4f41081. It preserved the source hydraulic solution but correctly failed the unchanged D2 excavation-burden limit. Active code does not retain that placement correction or relax the limit.

Because discharge is explicitly game-calibrated rather than an externally fixed hydrograph, the associated candidate sizes design flow to available owned headroom instead. Survey the bank-contained wet columns against their exact original solid ceiling, C = maximumSolidY + 1. The maximum positive stage excess supplies headroom d. At each existing station depth h, compute the normal-flow conveyance ratio:

```text
K(h) = A(h) * (A(h) / wettedPerimeter(h))^(2/3)
flowScale = min over stations of K(h - d) / K(h)
```

Roughness and energy slope cancel from this ratio. If a positive target depth does not exist, the candidate rejects. Scale all discharges by that common factor, preserve bed geometry, and re-run the existing source-normal-depth and standard-step solve. This is a normal-conveyance design-flow candidate, not a guarantee for a nonuniform reach: survey its actual re-solved stages again and reject any remaining support excess. There is no iterative residual tuning, per-column head clamp, or excavation increase. Final D2 and voxel admission still apply.

Associated ordinary-channel wetness is confined to its bankfull corridor; this source model does not grant disconnected low floodplain cells inundation authority. Final acceptance requires zero support clipping, one face-connected integer water body per supported ordinary reach and no missing rounded centerline samples. Sub-voxel shoreline depths remain quantified discretization loss, not authority to add terrain or water outside support.

The focused workflow retains the planner's primary-287 zero-authority control while avoiding its duplicate expensive invocation through the game-model test class. Full CI continues to run the complete test suite.
