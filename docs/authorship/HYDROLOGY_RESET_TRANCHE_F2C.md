# Hydrology reset tranche F2C — ordinary bounded-profile assembly

**Status:** design/assembly tranche under issue #1084  
**Depends on:** accepted F2 contract, proven F2A bounded-QP primitive, and accepted F2B head-independent geometry skeleton  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

F2C is the first consumer of the F2 numerical primitive. It replaces the historical topological
water-surface clipping logic for **transition-free ordinary reaches** with the coupled bounded
world-space profile solve.

It does not yet grant new terrain authority. Confluences, cascades/drops, and retained-water
junctions remain explicitly transition-owned and are withheld until their transition mathematics
exists.

## 1. State and units

For each C2 centerline sample, define world-space free-surface head

```text
H_i = reliefBudget * waterSurfacePotential_i
```

relative to the island authored vertical datum.

Hydraulic depth is

```text
d_i = reliefBudget * waterDepthPotential(Q_i)
B_i = H_i - d_i
```

where `B_i` is solved bed elevation.

All longitudinal grades are dimensionless world rise/run ratios:

```text
grade_i = (H_i - H_(i+1)) / ds_i
```

No normalized potential may be combined directly with horizontal world length.

## 2. Preferred head objective

The preferred unqualified surface follows the current pre-hydrologic terrain with the existing
freeboard relation:

```text
Hhat_i = reliefBudget * (z0_i - freeboard_i)

freeboard_i =
    BASE_FREEBOARD_POTENTIAL
  + DEPTH_FREEBOARD_FRACTION * waterDepthPotential(Q_i)
```

clamped only to the authored vertical domain before conversion to world units.

The discrete quadratic objective must approximate a continuous line integral rather than count
samples equally. Each centerline sample receives trapezoidal/Voronoi arc-length weight:

```text
ell_0       = 0.5 * ds_0
ell_i       = 0.5 * (ds_(i-1) + ds_i)
ell_(n-1)   = 0.5 * ds_(n-2)

w_i = ell_i * semanticWeight_i
```

with `semanticWeight_i > 0`. The initial F2C policy uses `semanticWeight_i = 1` unless later
evidence justifies another physical weighting.

This arc-length weighting is mandatory for sampling-refinement convergence.

## 3. Hard pointwise head bounds from D2

For each ordinary sample, F2C derives an admissible interval `L_i <= H_i <= U_i` from the same
accepted D2 policy. It does not invent a parallel safety envelope.

Let:

- `z0_i` be pre-hydrologic centerline terrain elevation in world units;
- `zL_i, zR_i` be the pre-hydrologic terrain at the left/right valley probes;
- `zBankL_i, zBankR_i` be terrain at the bankfull probes;
- `d_i` be hydraulic depth in world units;
- `r_b` be bankfull half-width;
- `r_v` be the profile-sensitive valley half-width;
- `W_v = 2 r_v`;
- `m_cut` be D2 maximum centerline lowering in world units;
- `g_lat` be D2 maximum lateral recovery grade;
- `rho_v` be D2 maximum relief/valley-width ratio;
- `c_bank` be D2 maximum bank-containment deficit in world units.

Because `B_i = H_i - d_i`, D2 implies the lower head constraints

```text
H_i >= z0_i - m_cut + d_i

H_i >= zL_i - g_lat * r_v + d_i
H_i >= zR_i - g_lat * r_v + d_i

H_i >= zL_i - rho_v * W_v + d_i
H_i >= zR_i - rho_v * W_v + d_i
```

and the bank-containment upper constraint

```text
H_i <= min(zBankL_i, zBankR_i) + c_bank.
```

The authored vertical domain contributes the explicit physical bounds

```text
d_i <= H_i <= reliefBudget
```

so the solved bed `B_i = H_i - d_i` cannot fall below the authored vertical datum.

Therefore

```text
L_i = max(d_i, all D2-derived lower bounds)
U_i = min(reliefBudget, all D2-derived upper bounds)
```

and `L_i > U_i` is immediate hydraulic infeasibility.

Depth/width, curvature/width, ridge occupancy, and other geometry-only gates remain independent
preconditions. Aggregate excavation burden remains a post-solve D1/D2 diagnostic rather than being
converted into an arbitrary local bound.

## 4. Ordinary longitudinal grade bounds

Without calibrated physical discharge units, roughness, or velocity state, Skyforge has no justified
positive minimum hydraulic slope. Therefore the initial ordinary lower-grade authority is

```text
g_min = 0.
```

This enforces non-climbing ordinary free surface without fabricating a Manning-equation calibration.

The upper grade is inherited from the exact D2 qualification class of the semantic reach:

```text
0 <= H_i - H_(i+1) <= g_max(D2_class(reach)) * ds_i.
```

F2C does not create a new finer-grained longitudinal-grade policy merely because local profile labels
are available. Any future profile-local grade authority must be independently calibrated and accepted.

CASCADE segments are never forced through this ordinary constraint.

## 5. Variable ownership

F2C only assembles transition-free ordinary components.

- interior samples receive one head variable each;
- an ordinary shared semantic endpoint may use one shared variable only when no confluence, cascade,
  basin, or other transition owns that location;
- confluence incident reaches terminate at transition boundaries and remain deferred;
- cascade/drop boundaries remain separate upstream/downstream hydraulic states;
- channel terminal fate is resolved by following the authoritative watershed graph from the semantic
  channel terminal to its watershed terminal; cell-index or coordinate coincidence is not a basin
  linkage rule;
- only an explicit `EDGE_OUTLET` fate may use the ordinary free-terminal solve;
- `RETAINED_OPEN_WATER`, `RETAINED_WETLAND`, and unresolved terminal fate remain deferred to
  explicit transition ownership;
- exact accepted basin datums become fixed equality variables only after retained-water production
  qualification is adequately calibrated.

No coincident-coordinate heuristic may merge variables or assign terminal fate.

## 6. Solve and reconstruction

For each independently proven uncoupled ordinary component:

1. assemble canonical variables and constraints;
2. solve with the accepted F2A primitive;
3. reject on `INFEASIBLE` or `NUMERICAL_FAILURE`;
4. reconstruct `H_i`, `B_i = H_i - d_i`, and hydraulic samples;
5. regenerate D0/D1 diagnostics on the new profile;
6. re-run D2 without threshold weakening.

A solved QP is necessary but not sufficient geomorphic acceptance.

## 7. Discretization convergence

Fixed representative ordinary fixtures must be solved at **three resolutions**: the native C2
hydraulic collocation, one deterministic midpoint refinement, and a second midpoint refinement along
the same accepted C2 piecewise-continuous centerline. Two resolutions alone are insufficient to
distinguish convergence from incidental pairwise agreement.

Refinement is numerical sampling, not new geometry authority. Inserting an interpolation sample may
not change the C2 route, semantic corridor, endpoints, or geometry-only diagnostics such as
ridge occupancy and curvature/width. In particular, the existing discrete curvature estimator is
defined on the accepted C2 centerline nodes; blindly inserting collinear nodes and recomputing that
estimator would change its numerical value without changing the physical polyline and is forbidden.

Refined collocation samples interpolate the accepted centerline by arc length, interpolate
discharge/width/depth semantics consistently, and resample the pre-hydrologic terrain/cross-section
probes at the interpolated physical position. Geometry-only D2 gates remain those of the unchanged C2
candidate. Head-dependent gates are recomputed at the refined collocation.

Before numeric tolerances are frozen, all three levels must remain solvable and preserve the same
head-dependent D2 pass/fail classification. The evidence corpus records coarse/medium/fine values for
the following quantities so a scale-aware Cauchy/grid-convergence criterion can be frozen from the
fixed corpus rather than invented in advance:

- endpoint/free-terminal heads;
- integrated objective per unit length;
- maximum ordinary longitudinal grade;
- integrated excavation proxy;
- maximum lowering.

Final refinement acceptance requires convergence of:

- endpoint/free-terminal heads;
- integrated objective per unit length;
- maximum ordinary longitudinal grade;
- integrated excavation proxy;
- maximum lowering;
- every head-dependent D2 pass/fail result, while geometry-only D2 results remain invariant by
  construction.

If a finer collocation reveals a hard hydraulic/cross-section violation, the coarse result was
under-resolved and is retired. If convergence cannot be established without changing C2 geometry,
the required work belongs to a later centerline-representation tranche rather than being hidden in
the hydraulic solver.

## 8. Determinism and provenance

Variable ordering is canonical by semantic reach identity and downstream station. Constraint ids
derive from stable semantic identity plus station pair, not list insertion order.

Every solved sample retains exact semantic reach/profile provenance and the original C2 centerline
identity. F2C must not create a second routing authority.

## 9. Stop boundary

F2C does **not**:

- mutate terrain;
- resolve confluences;
- resolve cascades/drops;
- realize retained basins;
- modify E2 calibration;
- change D2 thresholds;
- touch Minecraft.

The next transition tranche may proceed only after F2A is accepted and F2C proves that the bounded
profile assembly changes hydraulic evidence in a controlled, convergent way.

## 10. Downstream authority correction identified during F2C audit

F2C explicitly resolves semantic terminal fate through downstream watershed topology. A channel
terminal may lie several planning cells upstream of the retained sink; therefore endpoint/sink
cell-index equality is neither necessary nor sufficient for basin ownership. Only an explicit edge
outlet is an ordinary free terminal. POND/LAKE, wetland, and unresolved terminal fates are deferred.

The earlier F1 terrain-authority implementation predates this explicit guard and currently names only
confluence and cascade deferrals.

Before any later tranche broadens terrain authority or reconnects Authorship to Minecraft, F1 must be
hardened so a reach touching an unresolved retained-open-water junction also contributes zero terrain
delta. That correction is intentionally not folded into F2C because F2C itself changes no terrain
authority.
