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

## Rejected trials and remaining ownership decision

The integrated shared-frame proof passed in run 38830308214: 55 level sections, 9,255 admitted columns, two face-connected rivers, no missing centerline samples, and 3,928 persisted water voxels. It still reported 219 *materialized* columns clipped by the old solid-support ceiling; intended wet cells without excavation also receive no water under the retained adapter rule.

Two bounded trials are preserved in Git history, not active implementation:
- b08c5f229d75ae3ecded7ae7e16e86e9a4f41081: rigid bed/stage lowering preserved source hydraulics but failed the unchanged D2 excavation-burden limit.
- 18a923ee2b74f0926ea2c271f3635637336766c8: a conservative normal-conveyance flow candidate rejected because its 3.073930563 m headroom requirement exceeded a 2.591885475 m station depth. This does not prove that every smaller-flow candidate is impossible.

Active code retains the verified shared-frame solve and original game calibration. It does not retain either failed correction, widen excavation limits, tune residuals, or claim that the remaining ceiling distortion is repaired.

### Human contract gate

The adapter requires water to replace only pre-existing exact owner solid. This is stricter than merely preserving exact island ownership: it forbids water occupying air above the old ground even when an accepted connected water surface calls for it.

Recommend allowing a precisely bounded, connected water-volume envelope from the associated bed/stage candidate. It may extend above the old solid ceiling, but must remain explicitly assigned to that exact island, exclude foreign volume/water ownership and unrelated/player solids, and preserve removal/thickness limits. Seed connectivity from the accepted river; do not flood disconnected low terrain or grant owner-wide air access. Validate stacked isolation, save/reload and static tick fencing before a new visual review.

**No positive-air water authority is implemented or authorized by this PR.** Owner approval of the shared elevation frame is recorded; approval of this additional water-ownership contract remains pending.

## Static-water lifecycle repair

Qualified static-water tick membership now uses the exact qualified water-position set rather than the legacy planner. The prepared-world viewer restores a read-only tick mask from the exact validated fixture authorization; it does not install a terrain writer, removal binding or native surface adapter. The dedicated launch regenerates the disposable review specimen, so this is an exact-version fixture restore, not a general saved-world migration policy.

A focused NeoForge regression checks every qualified water position, absence of legacy dependence, writer-inert read-only behavior, scope cleanup and rejection of simultaneous read-only/mutation bindings. Actual long-lived/client visual evidence remains a separate human gate.

The viewer fence is scoped to the named prepared review world before level loading, only recognizes Overworld water, and closes when that server stops. Reopening the same prepared world reinstalls the read-only mask; unrelated worlds are neither fenced nor teleported by the viewer.

## Owner-approved connected water envelope — 2026-10-15

The positive-air decision is now approved in #1452. The earlier pending-decision text above is
historical: implementation now derives finite water intervals from qualified bed/stage geometry,
not the old solid ceiling. It requires connected accepted centerlines, grants no disconnected pond
or excavation authority, rejects foreign-volume/water overlap, and refuses unrelated solids in the
water destination. Legacy unassociated plans retain their original clipped replacement semantics.
Placement, persistence expectations, and static tick masks consume the same envelope.
Validation of this change is pending; no new visual acceptance is claimed.
