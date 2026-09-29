# Hydrology reset tranche F3G — D2-aware semantic-corridor centerline candidates

**Status:** implementation experiment under issue #1084  
**Depends on:** C2 semantic-corridor centerlines, unchanged D2 head envelopes, F3D ordinary-span solve, and F3F local cross-section sensitivity  
**Terrain mutation:** none  
**Hydraulic head / water authority:** none  
**Minecraft changes:** none

## Purpose

F3F found that all three key-287 empty ordinary pointwise head envelopes could be closed in a local
cross-section-only probe within at most 0.50 bankfull half-width. Those probes intentionally did not
test route continuity or semantic-corridor membership. F3G turns that bounded hypothesis into actual
continuous centerline candidates, while retaining all accepted geometric guards.

## Candidate generation and admissibility

For each interior C2 centerline point, refinement compares the existing smoothing proposal with four
small lateral candidates at ±0.25 and ±0.50 local bankfull half-width. Endpoints remain exact. A
candidate is admissible only when it:

- remains inside the authored semantic guidance corridor;
- does not rise more than the existing 0.015 terrain-potential allowance above the projected seeded
  route;
- satisfies the existing island-interiority floor.

CASCADE samples do not use the ordinary D2 head-gap objective. They retain their existing transition
ownership and are still solved by the F3 transition planners.

## Objective and authority

For reaches with one ordinary profile kind after CASCADE intervals are excluded, the candidate
scorer evaluates the exact shared D2 pointwise head envelope and that same profile's accepted limits
at the candidate centerline position, station, local tangent, discharge-derived width, and depth. It
does not alter any D2 limit. When a reach mixes ALLUVIAL and INCISED ordinary profiles, this tranche
does not synthesize a scoring envelope: it retains geometry-only C2 refinement and lets F3D apply its
existing MIXED span class.

Candidate ranking first avoids exceeding the existing curvature-to-width bound, then reduces the
maximum pointwise envelope gap and its arc-length-weighted squared integral. Existing curvature,
search-route deviation, and path-length tie-breaks remain. This is a bounded deterministic search, not
a second route authority: the original fine-lattice route and semantic corridor remain the geometric
authority.

Every resulting skeleton must still pass the existing ordinary-span bounded solve, shared D1/D2
qualification, complete-component F3E checks, and all explicit transition ownership. A better local
score alone is not a pass.

## Fixed-corpus decision

The exact-head Hydrology Reset Geometry workflow determines whether the candidate refinement changes
the key-287 result and whether controls remain stable. Interpret outcomes conservatively:

- if key 287 obtains a complete qualified chain, it may advance to the next existing non-Minecraft
  terrain/voxel-authority tranche;
- if it remains rejected, inspect the new F3D envelope and transition diagnostics. Do not widen D2;
- any regression in an already-qualified ordinary component is a failure and must be repaired before
  merging.

## Validation and stop boundary

Focused tests cover deterministic D2-directed lateral search, exact endpoints, corridor bounds, terrain
rise and interiority constraints. GitHub Actions runs CI and the exact hydrology geometry corpus; DR-40
and DR-50 remain broad integration regressions. No local or droplet project build is permitted.

F3G grants no terrain-delta, voxel, water, Minecraft, or human product acceptance. Do not repeat the
previous F4K Minecraft fixture. A new human visual review is appropriate only after a genuinely
different product specimen passes the complete continuous and transition qualification chain and a
distinct voxel-realization specimen is ready.
