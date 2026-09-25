# Hydrology reset — continuous geomorphic solution contract

**Status:** ACTIVE DESIGN AUTHORITY — 2026-09-25  
**Tracking:** issue #1084; DR-70 / issue #754 remains the product gate  
**Historical implementation snapshot:** `archive/hydrology-h6-2026-09-25`

## Decision

Hydrology will no longer proceed as a sequence of local repairs to the H6 physical projection.

The required pipeline is:

```
coarse drainage/catchment semantics
    -> continuous terrain-aware geomorphic/hydraulic solution
    -> hard mathematical plausibility qualification
    -> voxel discretization
    -> human review
```

The key correction is an authority correction: coarse planning outputs guide the continuous solution but do not automatically become immutable physical geometry.

## 1. Authority split

### Remains authoritative at coarse scale

- catchment membership and accumulated discharge;
- drainage fate and spill/connectivity semantics;
- source, confluence, and terminal relationships;
- accepted hydrologic/geologic/ecologic causes;
- semantic profile/discharge hierarchy.

### Becomes refinable geometric guidance

Unless separately promoted by a later accepted contract:

- intermediate 49x49 watershed cells;
- intermediate coarse route positions;
- coarse retained-water boundary cells;
- planning-grid curvature and raster-aligned footprint edges.

A continuous solver may move the visible thalweg/shoreline within a bounded semantic corridor while preserving the higher-level drainage relation.

## 2. Continuous river solution

Let the pre-fluvial authored terrain be a continuous elevation field (z_0(x,z)). For each semantic reach, solve a continuous centerline (gamma(s)) inside an allowed corridor rather than forcing every coarse planning point into the final river.

A practical route objective should combine terms such as:

[
J[gamma] =
int
left(
w_u,U(s)
+ w_r,R(s)
+ w_kappa,kappa(s)^2
+ w_d,D(s)^2
ight) ds
]

where:

- (U) penalizes uphill/raw-terrain-incompatible travel;
- (R) penalizes ridge occupancy or poor valley-floor position;
- (kappa) is curvature;
- (D) is bounded deviation from semantic routing guidance.

The exact objective/solver may change, but these are constraints on a physical solution, not cosmetic bend selection.

### Hydraulic geometry

Use discharge-scaled, profile-sensitive relationships of the general form

[
w(Q)=aQ^b,qquad d(Q)=cQ^f
]

with Skyforge-scale coefficients calibrated as dimensionless game-world geometry rather than copied blindly from field units.

The longitudinal bed and free surface must be solved together. Ordinary reaches must not climb downstream; explicit drops/cascades own discontinuities.

## 3. Cross-section plausibility

Width, depth, valley envelope, and vertical relief must be coupled.

Qualification must bound at least:

- lateral grade (|partial z/partial n|);
- incision depth relative to bankfull and valley width;
- adjacent-sample/voxel terrain delta after discretization;
- local and reach-integrated excavation volume;
- curvature versus width;
- profile-specific valley recovery length.

A maximum absolute cut depth alone is not an adequate safety constraint.

## 4. Continuous retained-water solution

A retained waterbody must be derived from a continuous basin and a solved water datum (h), not from a final union of coarse wet cells.

Conceptually, for the connected depression associated with the retained semantic sink:

[
Omega_h = {(x,z): z_b(x,z) le h}_{	ext{connected to sink}}
]

and the shoreline is the contour

[
partialOmega_h : z_b(x,z)=h.
]

The semantic watershed cells constrain catchment/fate and provide basin evidence; they do not define the final shoreline polygon.

Bathymetry/littoral shape must be derived continuously from the basin/shoreline geometry and constrained by shoreline grade.

## 5. Junctions and discontinuities

Confluences, lake inlets/outlets, cascades, waterfalls, and edge discharge are explicit transition problems.

They must own transition geometry and hydraulic boundary conditions. Independent reach/basin shapes may not simply overlap and rely on voxelization or fluid propagation to repair the join.

## 6. Failure-resolution hierarchy

If the continuous solution cannot satisfy the hard constraints:

1. refine or reroute within the semantic corridor;
2. use an explicitly supported hidden-transfer / retained / drop fate where existing semantics permit it;
3. reject/fail closed and surface the missing authority.

Do **not** preserve a coarse route by increasing excavation/reconciliation until it fits.

## 7. Backend contract

Minecraft receives an already-qualified continuous landform.

The backend may:

- sample/rasterize continuous terrain and water surfaces;
- resolve ordinary one-block-scale quantization;
- choose native block/fluid/material representation under existing ownership/ecology rules;
- preserve lifecycle, persistence, exact-volume, and stacked-volume invariants.

The backend may not:

- reroute hydrology;
- invent a basin;
- manufacture major levees;
- excavate a multi-block rescue trench to make an invalid grade feasible;
- promote coarse planning cells into geometry that the continuous solution did not authorize.

Backend reconciliation must be explicitly limited to voxel-quantization scale. Any larger correction is a failed upstream solution.

## 8. Machine acceptance before Minecraft review

The continuous solution must expose quantitative diagnostics and rejection thresholds for at least:

- uphill fraction / longitudinal monotonicity;
- ridge occupancy / valley-floor advantage;
- lateral bank and valley grade;
- incision-to-width and relief-to-valley-width ratios;
- local and integrated excavation volume;
- curvature/width compatibility;
- thalweg continuity;
- confluence transition continuity;
- retained-basin connectivity and spill consistency;
- shoreline grade and contour regularity;
- river/lake datum compatibility;
- required backend reconciliation magnitude.

The exact thresholds are a design task under #1084 and must be justified from the Skyforge scale/profile model. They must not be tuned only to one screenshot.

Human review remains required after these objective gates, but it should no longer be the first mechanism capable of detecting quarry walls, bathtub basins, broad water curtains, or obvious grid-derived shoreline geometry.

## 9. Development discipline

- Use fixed deterministic proving grounds for diagnosis.
- Keep a separate representative/stress corpus for robustness.
- Do not tune against the full corpus or replace failed seeds with prettier ones.
- Separate semantic planning diagnostics, continuous geometry diagnostics, and voxelization diagnostics.
- A green topology/lifecycle suite is necessary but not evidence of geomorphic acceptance.
- No new Implementation patch tranche begins until this mathematical contract is converted into concrete accepted metrics/solver interfaces.

## Relationship to AUTH-0105

AUTH-0105 remains a merged and useful historical milestone: it established that rivers require a continuous dry channel/valley landform and a contained water-surface contract.

Its preservation boundary was intentionally conservative. The Sep-25 review demonstrates that this boundary is insufficient for future work because exact accepted route/footprint geometry can itself be the source of physical implausibility.

Therefore #1084 may supersede/refine AUTH-0105 route/footprint preservation rules while preserving the useful landform vocabulary and evidence.


## Determinism scope

Determinism is strict for Skyforge-authored causes and materially player-relevant persistent
consequences, including:

- semantic drainage topology and fate;
- source/confluence/terminal identity;
- continuous route and basin geometry;
- hydraulic datums, bed/free-surface profiles, width/depth parameters;
- geomorphic qualification metrics and accept/reject decisions;
- authored ownership/material boundaries where they affect structure, persistence, connectivity,
  traversability, or gameplay.

Minecraft-native or mod-native ecological/decorative fleshing may retain bounded nondeterminism when
that variation does not materially alter those authored contracts. A byte-for-byte population digest
mismatch in native decoration is therefore diagnostic evidence, not automatically a hydrology
architecture failure.

This distinction preserves reproducible Skyforge causes without requiring every downstream native
decorative consequence to hash identically.
