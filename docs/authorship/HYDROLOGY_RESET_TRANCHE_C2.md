# Hydrology reset tranche C2 — semantic-corridor continuous centerlines

**Status:** corrective candidate geometry under issue #1084  
**Depends on:** D2 hard-safety qualification  
**Terrain mutation:** none  
**Minecraft changes:** none

## Why C1 was insufficient

C1 regularized a fine-lattice least-cost path inside a narrow tube around that path. Post-C1 D2
evidence showed that every fixed specimen still had at least one curvature/width failure.

An experiment that simply increased Chaikin smoothing inside the same tube produced no change in the
qualification manifest. The problem was therefore authority, not smoothing strength: the discrete
search path was still acting as a physical cage.

## Corrected authority

C2 now distinguishes:

```text
semantic corridor = bounded physical search authority
fine-lattice route = deterministic terrain-aware seed/evidence
continuous centerline = physical geometry candidate
```

Shared source/confluence/terminal endpoints remain exact.

Interior centerline samples may relax away from the lattice seed when all of the following hold:

- they remain inside the original semantic guidance corridor;
- they remain inside the authored island interiority domain;
- they do not climb materially above the nearest terrain sampled by the seed route.

The solver performs deterministic Laplacian relaxation and tracks the least-curved admissible
candidate. It may stop once the D2 hard-safety bend-radius condition is met.

If the semantic corridor cannot support that bend radius without violating terrain/interiority
constraints, the best bounded candidate remains rejectable by D2. The corridor is not expanded merely
to force a pass.

## Physical scale

The bend-radius request is derived from the reach's maximum downstream bankfull width. D2 currently
uses the permissive hard-safety condition:

```text
W / R <= 1
```

rather than treating it as a target natural meander geometry.

## Acceptance

C2 is successful only if the exact D2 corpus shows a reduction in `CURVATURE_TO_WIDTH` violations
without introducing worse incision, containment, excavation, or route-authority failures.

No terrain authoring is introduced here.


## Acceptance evidence — clean recomposition

The clean current-main recomposition regenerated the fixed D0/D1/E1 evidence rather than inheriting
the historical C1 measurements.

Observed effects relative to the accepted pre-C2 baseline include:

- primary key 287 maximum curvature/width: `2.978210865 -> 0.712365800`;
- control 118 reaches: `1.948486972 -> 0.827850854`,
  `2.491238567 -> 0.798181144`, and `3.240775433 -> 0.898892625`;
- stress 512 mixed/alluvial curved reaches likewise fall below approximately `0.90` where C1 had
  values above `2.4`.

The first-evidence-backed D2 acceptance controls remain accepted; primary key 287 remains rejected.
C2 therefore does not manufacture success by weakening D2.

Some already-rejected reaches change secondary diagnostics because the physical centerline and path
length have changed. In particular, control-77 reach 1140->897 increases maximum longitudinal grade
from approximately `2.696` to `3.527` while also remaining independently rejected by existing
lowering/lateral/containment pressure. This is retained as evidence, not tuned away.

Accordingly, C2 changes the diagnostic basis. Later longitudinal-profile work must regenerate D0/D1
and re-evaluate D2, exactly as required by the reset authority. The F2 global constrained profile
solve must not assume C1-era hydraulic diagnostics are current.
