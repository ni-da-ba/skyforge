# Hydrology reset tranche C3 — resolution-consistent terrain-aware route functional

**Status:** design contract under issue #1084  
**Depends on:** C2 semantic-corridor authority; F2C hydraulic evidence remains pre-C3 until regenerated  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

C3 removes a numerical inconsistency in the terrain-aware route search before F2C results are treated
as production hydraulic evidence.

The current A* search is deterministic, but its edge objective mixes:

- one-grid-step path cost;
- unscaled local ridge/terrain/guidance/interiority penalties added once per visited node;
- positive terrain increments;
- ridge probes whose physical radius scales with search-grid spacing.

Those quantities do not yet form a demonstrated discretization of one resolution-independent route
functional. Refining the search grid can therefore change route preference merely because more samples
are counted.

C3 defines the continuous objective first, then requires each discrete search resolution to approximate
that same objective.

## 1. Continuous route functional

Let the route be a horizontal curve `gamma(s)` parameterized by world-space arc length `s`, with
planning-cell spacing `S > 0`.

Define the non-negative local penalty density

```text
phi(x) =
    w_ridge    * ridge(x)
  + w_terrain  * terrain(x)
  + w_guidance * (distanceToGuidance(x) / corridorHalfWidth)^2
  + w_exterior * max(0, interiorityThreshold - interiority(x))
```

where all sampled field terms are dimensionless authored potentials.

The dimensionless route objective is

```text
J[gamma] =
    w_length * integral(ds / S)
  + integral(phi(gamma(s)) ds / S)
  + w_ascent * TV_+(terrain o gamma)
```

with

```text
TV_+(z) = integral(max(0, dz/ds) ds)
```

interpreted as the positive variation of terrain potential along the route.

This form is intentional:

- length and local penalty terms are line integrals normalized by the fixed semantic scale `S`;
- positive terrain variation is already dimensionless and does not receive an extra grid-count factor;
- every term has a resolution-independent continuous meaning.

C3 does not claim these weights are calibrated physical erosion energetics. They are deterministic
geomorphic routing preferences.

## 2. Discrete edge quadrature

For adjacent search nodes `i -> j` separated by world distance `ds_ij`, the discrete edge cost is

```text
C_ij =
    w_length * ds_ij / S
  + 0.5 * (phi_i + phi_j) * ds_ij / S
  + w_ascent * max(0, z_j - z_i)
```

using trapezoidal quadrature for the local density.

This replaces node-count accumulation. Cardinal and diagonal moves therefore approximate the same
continuous line functional rather than charging the same local penalty per visited node.

All edge costs remain non-negative.

## 3. A* heuristic

Because local penalties and positive ascent are non-negative, an admissible deterministic heuristic is

```text
h(x) = w_length * max(0, distance(x, goalRegion) / S).
```

No unproven terrain, ridge, or guidance lower bound enters the heuristic.

Tie-breaking remains deterministic and semantically irrelevant to the objective:

1. estimated total cost;
2. accumulated cost;
3. canonical grid index.

## 4. Fixed physical probe scales

Ridge/valley diagnostics must not change their physical support merely because the search grid is
refined.

The current baseline `1.5 * (S / 4) = 0.375 S` becomes an explicit semantic-scale constant:

```text
ridgeProbeRadius = 0.375 * S
```

for every numerical search resolution unless later calibration changes that physical/planning-scale
choice.

Anchor tolerances used only to rasterize a finite anchor region may shrink with grid spacing, but an
exact zero-radius anchor must remain an exact globally aligned search node.

## 5. Search resolution is numerical, not semantic authority

C3 introduces a search-resolution parameter such as

```text
divisionsPerPlanningCell in {4, 8, 16}
```

for convergence evidence.

Changing this value may only refine the numerical lattice. It may not change:

- semantic guidance;
- corridor half-width;
- planning spacing;
- endpoint anchor centers/radii;
- terrain/interiority fields;
- objective weights;
- fixed physical probe radius.

The production default remains unchanged until convergence evidence is reviewed.

## 6. Resolution-consistent route diagnostics

Diagnostics derived from a discrete route must also approximate resolution-independent quantities.

### Path length

```text
L = sum(ds_i)
```

already has the correct world-space interpretation.

### Uphill measure

The historical fraction of uphill edges is grid-count dependent. C3 records at minimum:

```text
positiveElevationVariation = sum(max(0, z_(i+1) - z_i))
```

and may additionally expose an arc-length-weighted uphill fraction

```text
sum(ds_i * I[dz_i > 0]) / sum(ds_i).
```

Any retained legacy count fraction is diagnostic compatibility only and cannot become hard authority.

### Ridge occupancy

Use arc-length weighting against the fixed physical ridge probe:

```text
ridgeLengthFraction =
    integral(I[ridge(s) > threshold] ds) / L.
```

Discrete evidence uses segment quadrature rather than count of sampled vertices.

### Mean valley advantage

Use the line average

```text
meanValleyAdvantage =
    (1 / L) * integral(valleyAdvantage(s) ds).
```

again with arc-length quadrature.

### Maximum quantities

Maximum guidance deviation, maximum local ridge, and maximum uphill increment remain extrema, but their
convergence across numerical resolution must be measured explicitly.

## 7. Convergence evidence

Representative synthetic and generated-corpus routes must be solved at at least three resolutions,
initially 4/8/16 divisions per planning cell.

Evidence records:

- selected endpoint cells/anchor positions;
- objective `J`;
- path length;
- positive elevation variation;
- maximum guidance deviation;
- arc-length-weighted ridge fraction;
- line-mean valley advantage;
- maximum local uphill increment;
- Hausdorff or bidirectional maximum distance between route polylines after arc-length interpolation;
- downstream C2 centerline diagnostics derived from each search route.

The evidence must distinguish:

1. **objective convergence** — the discrete objective approaches a stable value;
2. **geometry convergence** — route polylines approach the same corridor solution;
3. **classification stability** — regenerated D0/D1/D2 and later F2C hydraulic classifications do not
   flip merely because the search lattice is refined.

No single exact path identity is required when several routes are numerically near-degenerate, but a
classification-changing or macroscopically different route requires investigation.

## 8. Acceptance discipline

Before freezing numeric tolerances, C3 must first publish raw 4/8/16 evidence.

Final tolerances must be:

- scale-aware;
- justified from the fixed corpus and synthetic analytic cases;
- stricter than differences large enough to change D2/E2/F2C classification;
- independent of Minecraft block quantization.

A max-iteration, search failure, or non-convergent specimen is not accepted by widening the corridor or
changing objective weights in the validation path.

## 9. Regression fixtures

C3 must include at least:

- straight monotone synthetic terrain where all resolutions recover the same straight corridor;
- synthetic ridge-with-gap case where all resolutions choose the same physical gap;
- near-degenerate symmetric alternatives proving deterministic tie behavior without objective drift;
- exact zero-radius globally aligned endpoint anchors;
- fixed generated hydrology corpus including current C2/D2 controls and F2C ordinary-solve specimens.

## 10. Downstream requalification

C3 changes the numerical seed route used by C2, so merged C3 evidence invalidates current fixed
pre-C3 numerical values even if the conceptual authority is unchanged.

After C3 implementation:

1. regenerate C2 centerlines from the resolution-consistent route seed;
2. regenerate D0/D1/D3 route/geomorphic diagnostics;
3. re-evaluate D2;
4. regenerate E1/E2 basin/channel compatibility evidence where channel terminals participate;
5. rerun F2C bounded-profile and three-resolution hydraulic evidence;
6. only then consider transition realization or expanded terrain authority.

F2C architecture remains valid, but pre-C3 specimen numbers are evidence history, not a production
freeze.

## Stop boundary

C3 does **not**:

- retune D2/E2 thresholds;
- alter semantic drainage topology;
- broaden route corridors to rescue failures;
- solve confluence/cascade/basin transitions;
- mutate terrain;
- touch Minecraft.
