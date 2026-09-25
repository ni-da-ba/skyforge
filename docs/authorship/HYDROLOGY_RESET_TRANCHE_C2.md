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
