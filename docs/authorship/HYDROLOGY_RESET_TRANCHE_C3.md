# Hydrology reset tranche C3 — resolution-invariant terrain-aware route quadrature

**Status:** staged design contract under issue #1084  
**Depends on:** accepted C2 semantic-corridor centerline authority  
**Hydraulic/terrain authority:** none  
**Minecraft changes:** none

## Purpose

The current terrain-aware A* route is deterministic, but its discrete cost is not a
resolution-invariant quadrature of one fixed continuous route functional.

At the current numerical resolution:

- base length is accumulated as `ds / h`, where `h = planningSpacing / N`;
- ridge/terrain/guidance/interiority cost is added once per visited node;
- positive terrain ascent is accumulated as elevation variation.

Therefore changing the purely numerical fine-grid division count `N` rescales the first two terms
relative to ascent. Ridge probe radius and several route diagnostics also depend on `h`.

C3 makes numerical resolution a discretization choice rather than an implicit geomorphic parameter.

## 1. Fixed physical scale

Let

```text
L_p = planningSpacing
N_ref = 4
```

where `N_ref` is a **calibration reference only**, preserving the approximate magnitude of the
existing 4-division route objective. It does not define numerical resolution.

For any search grid with `N` divisions per planning cell,

```text
h = L_p / N.
```

Physical corridor width, node search radii, and terrain probe radii are fractions of `L_p`, never of
`h`.

The initial ridge/valley probe radius is the current physical value expressed explicitly:

```text
r_probe = 0.375 L_p
```

because the historical value `1.5 h` at `N=4` equals `0.375 L_p`.

## 2. Continuous route functional

Define the non-negative local dimensionless cost density

```text
phi(x) =
    w_ridge    * ridgePotential(x)
  + w_terrain  * terrainPotential(x)
  + w_guidance * (guidanceDeviation(x) / corridorHalfWidth)^2
  + w_exterior * exteriorPenalty(x).
```

The route functional is

```text
J[gamma] =
    N_ref * integral_gamma [ w_length + phi(x) ] ds / L_p
  + w_ascent * TV_plus[z o gamma].
```

Here `TV_plus` is positive variation of terrain potential along the directed route:

```text
TV_plus = sum over infinitesimal directed rises max(0, dz).
```

The line-integral term and positive-variation term are both dimensionless. Their relative weights are
fixed by authored calibration and do not depend on search-grid spacing.

The factor `N_ref` preserves the approximate scale of the existing four-divisions-per-planning-cell
objective without making four divisions authoritative geometry.

## 3. Edge quadrature

For one search edge `i -> j` with world-space length `ds`:

```text
Delta J_line =
    N_ref * (ds / L_p)
    * [ w_length + 0.5 * (phi_i + phi_j) ]

Delta J_ascent =
    w_ascent * max(0, z_j - z_i)

Delta J = Delta J_line + Delta J_ascent.
```

The local density uses trapezoidal endpoint quadrature. It must not be added once per visited node
without an edge-length factor.

All terms remain non-negative.

## 4. A* heuristic

Because `phi >= 0` and positive ascent is non-negative, a lower bound on remaining route cost is
pure base length:

```text
H(x) =
    N_ref * w_length
    * max(0, distance(x, goalCenter) - goalRadius)
    / L_p.
```

This heuristic is admissible for every numerical grid resolution and remains deterministic.

No heuristic may include terrain/guidance/ascent optimism that is not mathematically guaranteed.

## 5. Node selection

SOURCE / CONFLUENCE / TERMINAL semantic search radii remain fixed fractions of `L_p`.

Candidate nodes may be sampled on the numerical grid, so selected coordinates may converge with
resolution. Their **cost function and physical probe support** must not change merely because `N`
changes.

The surrounding-terrain probe radius is fixed at `0.375 L_p` unless later evidence deliberately
recalibrates that physical scale.

TERMINAL selection retains its existing semantic-neighborhood preservation rule until explicit
terminal-fate transition geometry owns the endpoint.

## 6. Resolution-consistent route diagnostics

Diagnostics that represent spatial occupancy or mean properties are arc-length weighted.

For segment lengths `ds_k`:

```text
ridgeOccupancy =
    [sum ds_k * 0.5 * (I_ridge,k + I_ridge,k+1)]
    / pathLength

meanValleyAdvantage =
    [sum ds_k * 0.5 * (v_k + v_k+1)]
    / pathLength

uphillOccupancy =
    [sum_(Delta z_k > 0) ds_k]
    / pathLength.
```

Positive ascent is reported separately:

```text
positiveAscentPotential = sum max(0, Delta z_k).
```

This has units of normalized terrain potential and approximates positive total variation. It must not
be relabeled as a spatial fraction.

A maximum single-step rise may remain diagnostic only if clearly labeled discretization-sensitive; it
must not become a hard physical criterion. Prefer maximum uphill **grade** for local physical
steepness.

The D2 ridge-occupancy input must use the arc-length-weighted definition after C3.

## 7. Resolution study

The route solver must expose a package-private/test-only numerical-resolution parameter while the
production default remains the current `N=4` until evidence supports changing it.

Synthetic fixtures must be solved on at least:

```text
N = 4, 8, 16
```

with the same physical terrain, guidance, corridor, anchor radii, probe radii, and weights.

Evidence must report:

- route functional `J`;
- path length;
- endpoint displacement from semantic centers where nonzero anchor regions are used;
- maximum guidance deviation;
- ridge occupancy;
- mean valley advantage;
- uphill occupancy;
- positive ascent potential;
- continuous C2 centerline deviation after the candidate route is consumed downstream.

Do not require exact lattice-vertex equality across resolutions.

## 8. Convergence discipline

Three nested resolutions permit an apparent-order/grid-convergence check only for scalar quantities
that show a suitable monotonic/asymptotic sequence.

For each scalar `f_4, f_8, f_16`:

- always publish coarse/medium/fine values and successive differences;
- if the sequence is monotonic and the fine/medium difference contracts, an apparent order may be
  estimated from the constant refinement ratio;
- if the sequence is oscillatory, flat at numerical tolerance, or not contracting, do **not** invent
  a Richardson extrapolate or formal order;
- hard acceptance must use explicit absolute+relative tolerances appropriate to the quantity and must
  not be chosen merely to preserve an existing specimen.

The route objective and diagnostics must demonstrate that refinement changes numerical approximation,
not the underlying geomorphic calibration.

## 9. Regeneration boundary

C3 changes upstream candidate-route numerics. Therefore after C3 is accepted:

1. regenerate D0 route/hydraulic diagnostics;
2. regenerate D1 cross-section/excavation diagnostics;
3. re-evaluate D2 qualification with unchanged limits unless evidence supports an independently
   reviewed recalibration;
4. regenerate E1 retained-basin diagnostics where channel compatibility depends on route state;
5. rerun F2C bounded-profile/convergence evidence.

Current pre-C3 F2C specimen values must not be promoted to production hydraulic or terrain authority.

## 10. Stop boundary

C3 does **not**:

- broaden semantic route corridors;
- tune route weights to make key 287 pass;
- change drainage topology or accumulated discharge;
- change D2/E2 limits;
- solve confluence/cascade/basin transition geometry;
- mutate terrain;
- touch Minecraft.

C3 is numerical verification and discretization hygiene for the existing terrain-aware route
functional.
