# Hydrology reset tranche C1 — constrained longitudinal hydraulics

**Status:** dependent implementation candidate under issue #1084  
**Depends on:** Tranche A route authority and Tranche B shared network geometry  
**Minecraft changes:** none

## Purpose

A route is not physically valid merely because it follows a valley.

Before Skyforge is allowed to carve a channel, the route must admit a longitudinal channel bed and
water surface under explicit grade, incision, and depth constraints. Tranche C1 implements that as a
feasibility problem.

The central rule is:

> If a route cannot carry an ordinary channel within the authorized geomorphic envelope, the solve
> fails. The solver never expands the incision budget to make the route work.

## Bed feasibility

At route station `i`, let:

- `z_i` be pre-fluvial terrain elevation potential;
- `b_i` be channel-bed elevation potential;
- `I_max_world` be the maximum authorized incision in physical world-height units;
- `ds_i` be horizontal distance from station `i-1` to `i`;
- `H` be the descriptor's vertical relief scale (`reliefBudget`) in world-height units;
- `S_b` be the maximum ordinary physical bed slope (vertical world units per horizontal world unit).

The solver converts physical incision to potential internally:

```text
I_max_potential = I_max_world / H
z_i - I_max_potential <= b_i <= z_i
```

Ordinary reaches additionally require:

```text
0 <= b_(i-1) - b_i <= S_b * ds_i / H
```

The solver propagates reachable intervals downstream. If the local terrain/incision interval no
longer intersects the interval reachable from upstream, the route is rejected as
`BED_GRADE_INFEASIBLE`.

This is deliberately different from H6. H6 could reconcile an incompatible physical grade by
increasing downstream excavation. C1 cannot change `I_max`.


## Scale correctness

Normalized authored elevation is not itself a physical distance or physical slope. Skyforge's physical projection converts
an elevation-potential delta `dz` to approximately `dz * descriptor.reliefBudget()` vertical world
units. Horizontal island-local units map directly to world-space distance before backend translation.

Therefore physical vertical dimensions are authored in world-height units and converted to potential
only inside the solver. A slope constraint must use:

```text
physicalSlope = (potentialDrop * reliefBudget) / horizontalDistance
```

and **must not** normalize vertical grade by `nominalRadius`. Radius and relief budget vary
independently across authored islands, so radius-normalized grade would allow the same nominal
constraint to produce materially different physical cliff steepness.

## Water-surface feasibility

After a bed is selected, free-surface elevation `h_i` must satisfy explicit minimum/maximum
physical depth. With `d_world` converted by `d_potential = d_world / H`:

```text
b_i + d_min_potential <= h_i <= min(z_i, b_i + d_max_potential)
```

and ordinary downstream grade:

```text
0 <= h_(i-1) - h_i <= S_h * ds_i / H
```

If no such surface exists, the route fails with `WATER_SURFACE_INFEASIBLE`. The bed is not
deepened to rescue water depth.

## Interval algorithm

For either bed or water surface, if the upstream feasible interval is `[L_(i-1), U_(i-1)]`, then
the values reachable at the next station under maximum fall `F_i` occupy:

```text
[L_(i-1) - F_i, U_(i-1)]
```

This interval is intersected with the station's local physical bounds. Here `F_i = S * ds_i / H`,
because one elevation-potential unit corresponds to `H` physical vertical world units. Empty
intersection means failure.

Once all stations are feasible, the solver backtracks from downstream toward upstream and chooses
the value nearest the target profile while remaining inside the propagated feasible intervals.

This gives deterministic bounded behavior without an iterative excavation feedback loop.

## What C1 proves

- ordinary bed cannot climb downstream;
- ordinary free surface cannot climb downstream;
- maximum physical bed/free-surface slope is explicit and invariant to island radius/relief scaling;
- maximum incision is explicit and immutable during the solve;
- water depth remains bounded;
- incompatible terrain fails closed;
- hydraulic feasibility is determined before terrain mutation.

## Non-goals

C1 does not yet decide the correct Skyforge values for:

- target/maximum incision calibration in physical world units;
- discharge-width/depth coefficients in physical world units;
- profile-specific physical slope limits;
- cross-section shape;
- floodplain/valley envelope;
- cascades/waterfalls;
- retained-water basins.

Those belong to C2 and later tranches. C1 supplies the mathematical mechanism those authored
parameters must satisfy.
