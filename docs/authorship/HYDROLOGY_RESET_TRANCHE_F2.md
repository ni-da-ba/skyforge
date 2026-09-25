# Hydrology reset tranche F2 — coupled bounded-profile transition contract

**Status:** design contract under issue #1084  
**Depends on:** merged C2 geometry authority, F0 strict realization authority, clean F1 fail-closed realization  
**Terrain mutation:** none in this contract tranche  
**Minecraft changes:** none

## Purpose

F2 defines the mathematical boundary conditions required before deferred confluences, cascades/drops,
or river/retained-water junctions may receive terrain authority.

The governing order is:

```text
semantic drainage and accumulated discharge
    -> C2 continuous centerline geometry
    -> coupled bounded longitudinal hydraulic solve
    -> explicit node/drop/basin transition geometry
    -> D2/E2 + transition requalification
    -> one continuous terrain/water target field
    -> backend discretization
```

No transition may be replaced by overlap priority, larger excavation budgets, or backend fluid
propagation.

## 1. Units and state variables

All longitudinal constraints are solved in **world units**.

Let:

- `s_i` be downstream arc length in world units;
- `H_i` be solved free-surface elevation in world units relative to the island's authored vertical
  datum;
- `Hhat_i` be the terrain/hydraulic preferred free-surface elevation in world units;
- `B_i` be solved bed elevation in world units;
- `Q_i` be the existing dimensionless accumulated-discharge semantic quantity.

Normalized terrain potentials may remain storage/interchange values, but they must be converted through
the descriptor relief/vertical scale before being combined with lengths or grades. A dimensionless
potential may not be subtracted from a world-space length inside one constraint.

## 2. Ordinary longitudinal profile as a network-global bounded convex solve

The authoritative solve is global over the currently coupled ordinary network, not a sequence of
independent reach solves after locally choosing node datums.

For every ordinary continuous subreach, samples are ordered downstream by `s_i`. Shared semantic
nodes introduce shared head variables (or explicit transition-boundary variables when a transition
owns a discontinuity). Reach endpoints reference those variables through equality constraints.

The admissible ordinary water-surface grade is bounded:

```text
g_min * ds_i <= H_i - H_(i+1) <= g_max * ds_i
ds_i = s_(i+1) - s_i > 0
```

where `g_min >= 0` and `g_max >= g_min` are explicit profile-class qualification values.

Each sample also has an admissible elevation interval:

```text
L_i <= H_i <= U_i
```

derived from transition boundary conditions, pre-hydrologic terrain, bed/freeboard relationships, and
the same hard excavation/containment policy used by D2. The solver may not create feasibility by
silently widening these bounds.

The baseline objective is the strictly convex weighted projection over all free sample and node
heads:

```text
minimize  sum_(r,i) w_(r,i) * (H_(r,i) - Hhat_(r,i))^2
        + sum_n      w_n     * (N_n     - Nhat_n)^2

subject to
          g_min(r,i) * ds_(r,i)
              <= H_(r,i) - H_(r,i+1)
              <= g_max(r,i) * ds_(r,i)

          L_(r,i) <= H_(r,i) <= U_(r,i)

          H_(r,0)   = boundary_upstream(r)
          H_(r,last)= boundary_downstream(r)

          shared ordinary node boundaries reference the same N_n
          transition-owned boundaries obey the transition's explicit equalities/relations
```

with every free-variable weight strictly positive.

This formulation has a unique optimum whenever the feasible set is nonempty. It is independent of
reach iteration and topological visitation order, globally defined, and exposes infeasibility
directly. The existing topological node-surface clipping is therefore staging code, not F2 authority.

### Solver requirement

A one-sided weighted isotonic/PAV solve is valid only for a decoupled chain in the special case where
the upper-grade, box, and shared-node/transition constraints are inactive.

The general F2 solver must implement the full sparse convex problem deterministically. Acceptable
implementations include a small dependency-free active-set solver specialized to the network's sparse
difference/equality constraint matrix or another deterministic convex projection method with
independently proven KKT/primal-feasibility checks.

Do not emulate the global solve with sequential clipping.

## 3. Bed and free-surface coupling

The free surface is not solved independently of channel geometry.

For each ordinary sample:

```text
B_i = H_i - d(Q_i, profile_i)
```

where the dimensionless Skyforge hydraulic depth relation remains discharge-scaled:

```text
w(Q) = a Q^b
d(Q) = c Q^f
```

after conversion to world-space geometry through the island scale.

The solved bed must still satisfy terrain-lowering, lateral-recovery, containment, depth/width,
relief/valley-width, curvature/width, ridge-occupancy, and excavation gates. A longitudinally feasible
profile is not sufficient by itself.

## 4. Boundary-condition ownership

Hydraulic boundary values are owned by semantic transition objects.

### Source / free terminal

A source or free terminal may derive a preferred datum and admissible interval from qualified
pre-hydrologic terrain and hydraulic geometry.

### Retained-water inlet

If a semantic channel terminates at an accepted retained basin, the basin water datum is an **exact
terminal equality constraint** for the incident river solve.

The river may not choose a different terminal datum and ask E2 or Minecraft to absorb the mismatch.

### Retained-water outlet

If an accepted retained basin feeds a visible outlet reach, the basin datum is the outlet reach's
exact upstream hydraulic boundary.

### Confluence

A confluence owns one coupled transition state and one bounded transition region. Incident ordinary
reaches supply boundary states at the transition boundary; they do not independently carve through the
node.

### Cascade / drop

A cascade/drop owns the permitted hydraulic discontinuity. Ordinary subreaches on either side are
individually continuous bounded-profile solves.

## 5. Discharge semantics

Skyforge discharge remains the normalized **accumulated watershed discharge already authored by the
semantic drainage system**.

At a transition:

- discharge must not decrease downstream unless an explicit semantic loss/transfer owns the change;
- an outgoing confluence consumes the authored accumulated downstream discharge;
- normalized tributary values must not be naively re-summed if that would double-count locally
  accumulated catchment contribution;
- any inconsistency is a semantic/diagnostic failure, not a normalization repair.

## 6. Confluence transition mathematics

A confluence transition must reconcile at least:

- incident centerline positions and tangents;
- accumulated-discharge hierarchy;
- free-surface boundary elevations;
- bed elevations;
- hydraulic width and depth;
- incident approach angles;
- valley/cross-section envelopes.

The transition owns a bounded spatial envelope derived from incident hydraulic widths. Inside that
envelope it emits one terrain/water target. At the envelope boundary it must reproduce every incident
ordinary reach state to numerical tolerance.

The construction must be permutation-invariant with respect to incident-reach iteration order.

USACE HEC-RAS is an engineering reference for the principle that junctions require explicit
energy/momentum compatibility and that equal water-surface assumptions are conditional. Skyforge does
not claim to reproduce a field-scale 1-D hydraulic simulator, but it must preserve the same modeling
discipline: a junction is a coupled boundary-value problem, not coincident coordinates plus priority.

## 7. Drop / cascade transition mathematics

A drop transition explicitly owns:

- upstream and downstream hydraulic boundary states;
- signed free-surface and bed discontinuity;
- transition/drop length;
- width evolution;
- bounded downstream recovery/plunge geometry where semantically supported.

Ordinary subreaches may not acquire extreme grade merely to absorb a discontinuity.

D3 discontinuity diagnostics are evidence for deciding whether a mixed reach contains authored
drop structure. They do not themselves authorize splitting or terrain mutation.

## 8. River / retained-water coupling

E2 remains the hard admission gate for open-water basin geometry.

Before basin terrain authority exists:

1. the retained-water corpus must support explicit POND/LAKE qualification limits;
2. the basin must pass those limits;
3. every exact semantic channel junction must use the basin datum as an exact hydraulic boundary;
4. the coupled river profile must be feasible under the bounded solve and re-pass D2;
5. the transition region must pass its own geometry diagnostics.

A datum mismatch is a coupled-solver/boundary-condition failure, not a threshold to tune around.

## 9. Required machine diagnostics

Machine evidence must expose at least:

- primal feasibility residual for every longitudinal inequality and box constraint;
- fixed-boundary residual;
- KKT/optimality residual appropriate to the chosen solver;
- downstream discharge semantic compatibility;
- bed/free-surface consistency residual;
- boundary-state residual for every incident transition reach;
- width/depth change ratios through transitions;
- incident-angle geometry;
- maximum local terrain lowering;
- maximum lateral recovery grade;
- maximum ordinary longitudinal grade;
- transition-integrated excavation proxy;
- transition-envelope radius relative to hydraulic width;
- post-realization D2 qualification for every incident ordinary reach;
- E2 qualification for any coupled retained basin.

For an exact basin junction, channel/basin datum residual after the solve must be numerical
roundoff-scale.

## 10. Failure hierarchy

If the coupled problem is infeasible:

```text
bounded route/node refinement
    -> explicit valid drop/hidden-transfer fate where existing semantics permit
    -> reject / fail closed
```

Forbidden:

```text
infeasible constraints
    -> widen excavation/grade bounds
    -> relax D2/E2
    -> backend rescue
```

## 11. Evidence invalidation and recalibration

Changing centerline or longitudinal-profile mathematics changes the diagnostic basis.

Any accepted F2 implementation must therefore:

- regenerate D0/D1 on the fixed corpus;
- regenerate D3 discontinuity evidence where relevant;
- re-evaluate D2 without tuning to preserve previous passes;
- regenerate E1 river/basin datum evidence;
- re-evaluate E2 once an adequate basin corpus exists;
- preserve separate representative and stress corpora;
- record every acceptance/rejection change explicitly.

Historical thresholds survive only if regenerated evidence independently supports them.

## 12. Backend boundary

Minecraft receives one already-qualified continuous target field.

Allowed backend correction is limited to ordinary voxel quantization and mathematically equivalent
one-block-scale cleanup.

Forbidden backend behavior includes:

- rerouting;
- changing a transition state;
- changing a basin datum;
- manufacturing a plunge pool or levee;
- deepening a channel to restore connectivity;
- resolving a confluence through fluid spread.

## Engineering references

- Leopold, L. B. & Maddock, T. (1953), USGS Professional Paper 252: hydraulic width, depth, and
  velocity exhibit discharge-dependent power-law behavior.
- USACE HEC-RAS stream-junction documentation: junctions require explicit hydraulic compatibility;
  energy and momentum approaches are distinct and approach angle can matter.
- GRASS GIS `r.watershed`: least-cost/MFD drainage discovery is a useful reference for keeping raster
  drainage inference separate from final continuous physical geometry.
- Tarboton (1997) D-infinity drainage: continuous directional drainage over raster facets is useful
  evidence that routing semantics need not imply grid-aligned final geometry.

These references constrain engineering practice. Skyforge remains a deterministic, dimensionless
procedural synthesis system rather than a field-scale hydraulic forecast model.
