# Hydrology reset tranche F2 — coupled hydraulic transition contract

**Status:** design contract under issue #1084  
**Depends on:** F0 qualified-realization authority and F1 fail-closed fluvial realization  
**Terrain mutation:** none in F2 contract tranche  
**Minecraft changes:** none

## Purpose

F2 defines the mathematical boundary conditions and transition ownership required before deferred
confluences, cascades/drops, or river/retained-water junctions may receive terrain authority.

The governing correction is:

```text
independent reach/basin candidates
    -> explicit coupled boundary conditions
    -> globally constrained hydraulic profile
    -> node/drop/basin transition geometry
    -> hard requalification
    -> terrain authority
```

No transition may be replaced by overlap priority, deeper excavation, or backend fluid propagation.

## 1. Ordinary longitudinal profile as a global constrained solve

The current candidate hydraulic profile uses local sequential clipping. F2 replaces that authority
with a global constrained projection for every ordinary continuous subreach.

Let samples be ordered downstream by arc length `s_i`. Let `etaHat_i` be the terrain-preferred
candidate free-surface datum and let `g_min >= 0` be the minimum ordinary downstream grade.

Ordinary-flow feasibility requires:

```text
eta_i - eta_(i+1) >= g_min * (s_(i+1) - s_i)
```

Define the transformed ordinate:

```text
y_i = eta_i + g_min * s_i
```

Then the grade constraint becomes the monotonic constraint:

```text
y_i >= y_(i+1)
```

The baseline F2 solver shall minimize the global weighted squared departure from the preferred
profile:

```text
minimize  sum_i w_i * (y_i - yHat_i)^2
subject to
          y_i >= y_(i+1)
          explicit endpoint / transition boundary conditions
```

where `yHat_i = etaHat_i + g_min * s_i`.

This is a bounded weighted isotonic-regression problem. A deterministic pooled-adjacent-violators
implementation is preferred for the baseline because it is globally optimal for this objective,
order-stable, dependency-free, and independently testable.

A later smoothness term may be added only as an explicit convex extension with its own acceptance
evidence. It must not be approximated by order-dependent local clipping.

## 2. Boundary-condition ownership

Hydraulic boundary values are owned by semantic transition objects.

### Source / ordinary terminal

A source or free terminal may derive a candidate datum from qualified pre-hydrologic terrain and
hydraulic geometry, subject to the ordinary profile constraints.

### Retained-water inlet

If a semantic channel terminates at an accepted retained basin, the basin water datum is a fixed
terminal hydraulic boundary for the incident river solve.

The river may not independently choose another terminal datum and then ask E2 or Minecraft to absorb
the mismatch.

### Retained-water outlet

If an accepted retained basin feeds a visible outlet reach, the basin datum is the outlet reach's
upstream hydraulic boundary.

### Confluence

A confluence owns one transition state and bounded transition region. Incident ordinary reaches
provide boundary conditions at the edges of that region; they do not independently carve through
the node and compete by local priority.

### Cascade / drop

A cascade or explicit drop owns the allowed hydraulic discontinuity. Ordinary subreaches on either
side remain monotone continuous solves. The discontinuity magnitude, location, and downstream
recovery/plunge geometry are explicit transition data and are separately qualified.

## 3. Discharge semantics

Skyforge discharge remains the normalized accumulated watershed discharge already authored by the
semantic drainage system.

At a transition:

- discharge must not decrease downstream except where an explicit semantic loss/transfer owns it;
- an outgoing confluence state consumes the authored accumulated downstream discharge rather than
  re-summing normalized tributary values and double-counting local catchment contribution;
- incident reach boundary discharge must be compatible with the node's accumulated-discharge
  hierarchy;
- any inconsistency is diagnostic failure, not a normalization patch.

The hydraulic-geometry relationships remain of the discharge-scaled form:

```text
w(Q) = a Q^b
d(Q) = c Q^f
```

with dimensionless Skyforge coefficients calibrated from the project corpus rather than copied as
field-unit constants.

## 4. Confluence transition geometry

A confluence transition must reconcile, at minimum:

- incident centerline position and tangent;
- accumulated discharge hierarchy;
- free-surface boundary values;
- bed elevations;
- hydraulic width and depth;
- incident approach angle;
- valley/cross-section envelopes.

Exact shared endpoint coordinates and a shared datum alone are insufficient.

The transition owns a bounded spatial envelope derived from incident hydraulic widths. Inside that
envelope it emits one continuous target terrain/water solution. Outside it, each incident ordinary
reach must reproduce its qualified boundary state exactly.

The transition construction must be permutation-invariant with respect to incident-reach iteration
order. No result may depend on which tributary happens to be visited first.

HEC-style energy/momentum junction methods are an engineering reference for the fact that junctions
require additional compatibility conditions. Skyforge need not reproduce a full 1-D hydraulic
simulator, but it must not reduce a junction to coincident coordinates plus arbitrary overlap.

## 5. Drop / cascade transition geometry

A drop transition must explicitly own:

- upstream and downstream hydraulic boundary states;
- signed water-surface and bed discontinuity;
- transition/drop length;
- local channel-width evolution;
- bounded downstream recovery / plunge-pool allowance where semantically supported.

The drop is not modeled by allowing an ordinary reach to acquire an extreme longitudinal grade.

D3-style discontinuity diagnostics are admissible evidence for deciding whether an authored mixed
reach contains real cascade structure. They are not themselves permission to split or carve it.

## 6. River / retained-water coupling

E2 remains the hard admission gate for basin geometry.

Before a basin can receive terrain authority:

1. the retained-water corpus must support explicit POND/LAKE qualification policy;
2. the basin must pass that policy;
3. every exact semantic channel junction must use the basin datum as a hydraulic boundary;
4. the coupled river profile must remain feasible and pass D2;
5. the transition region must pass its own geometry diagnostics.

The current lake-609 mismatch is therefore a solver/boundary-condition problem to resolve, not a
calibration value to accept.

## 7. Required transition diagnostics

Machine evidence must expose at least:

- boundary-datum residual for every incident reach;
- downstream discharge monotonicity / semantic compatibility;
- bed-elevation residual at ordinary transition boundaries;
- width and depth change ratios across the transition;
- incident-angle geometry;
- maximum local terrain lowering;
- maximum lateral recovery grade;
- maximum longitudinal ordinary-reach grade;
- transition-integrated excavation proxy;
- transition-envelope radius relative to hydraulic width;
- realized D2 qualification of every incident ordinary reach;
- retained-basin E2 qualification where applicable.

For a coupled basin junction, channel/basin datum residual after the solve must be numerical
roundoff-scale, not merely below a visually chosen tolerance.

## 8. Failure hierarchy

If the coupled solve is infeasible:

```text
bounded route/node refinement
    -> explicit valid drop/hidden-transfer fate where semantics permit
    -> reject / fail closed
```

Forbidden:

```text
infeasible boundary conditions
    -> enlarge excavation budget
    -> relax hard qualification
    -> backend rescue
```

## 9. Evidence and recalibration rule

Changing longitudinal-profile mathematics changes the diagnostic basis.

Therefore any accepted F2 implementation that replaces the current sequential profile solve must:

- regenerate D0/D1 evidence on the fixed corpus;
- re-evaluate D2 limits without tuning to preserve formerly accepted specimens;
- regenerate E1 river/basin datum evidence;
- preserve a separate representative/stress corpus;
- record any acceptance/rejection changes explicitly.

Old thresholds may survive only if the regenerated evidence independently supports them.

## 10. Backend boundary

Minecraft remains downstream of the complete coupled continuous solution.

The backend may rasterize/sample and resolve ordinary voxel quantization. It may not:

- choose a different junction state;
- alter a basin datum;
- manufacture a plunge pool or levee;
- deepen a channel to restore connectivity;
- repair a failed confluence by fluid spread.

## Engineering references

- Leopold, L. B. & Maddock, T. (1953), USGS Professional Paper 252, hydraulic geometry of stream
  channels: width/depth/velocity as discharge-dependent power functions.
- USACE HEC-RAS stream-junction methodology: junctions require explicit energy- or momentum-based
  compatibility treatment; equal water-surface assumptions are conditional rather than universal.
- GRASS GIS `r.watershed`: terrain-aware least-cost drainage is a useful reference for separating
  drainage discovery from final continuous physical geometry.

These references constrain engineering discipline; Skyforge remains a deterministic dimensionless
procedural synthesis model, not a claim to field-scale hydraulic simulation.
