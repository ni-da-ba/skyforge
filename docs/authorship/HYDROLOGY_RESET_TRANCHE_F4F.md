# Hydrology reset tranche F4F — depth-preserving world-head refinement evidence

**Status:** backend-neutral centerline evidence under issue #1084  
**Depends on:** F4E qualified world-space water projection  
**Terrain / voxel / Minecraft authority:** none

## Problem

F4E proves that direct projection of accepted semantic hydraulic depth onto independently compiled
physical terrain can reintroduce small downstream water-head rises.

The fixed 8/81/77 association exhibits this on both accepted reaches.

Minecraft may not repair that by clamping water, rerouting, or excavating extra terrain.

## Refinement rule

F4F first evidence solves only the centerline hydraulic datum.

At every centerline sample:

- direct F4E head is the objective target;
- direct F4E head is also a hard lower bound;
- the paired terrain target is raised by exactly the same amount as head;
- accepted hydraulic depth therefore remains bit-for-bit unchanged within numeric tolerance;
- the raise may not exceed the remaining F4B carve margin back to the original compiled upper surface;
- the refined water head may not exceed physical bank containment:
  `min(leftBankTarget, rightBankTarget) + accepted D2 containment deficit`.

The solve reuses the accepted bounded hydraulic QP and D2 longitudinal-grade limit:

```text
0 <= H_i - H_(i+1) <= maxLongitudinalGrade * ds
```

Thus refined head is non-climbing downstream.

## One-sided authority

F4F is deliberately upward-only.

It may reduce previously authorized excavation, because the continuous bed and water surface can move
up together.

It may not:

- deepen the F4B terrain target;
- lower water head below F4E;
- change water depth;
- raise bed above the original compiled surface;
- exceed the accepted bank-containment deficit;
- change the centerline route or bankfull width.

If those restrictions make the QP infeasible, the component remains water-deferred.

## Scope of first evidence

The first tranche supports one realized ordinary reach per terminal component.

Confluences, cascades, retained basins, and multi-reach transition-coupled components remain outside
F4F refinement authority until their corresponding continuous joint solve is defined.

This limitation is explicit and fail-closed.

## Why centerline evidence is not terrain authority

A solved centerline correction does not yet define how the reduced excavation should taper laterally
through the full bank/valley cross section.

Therefore F4F does not modify F4B, F4C, or F4D output.

A later tranche must extend a solved correction into a continuous cross-section field, re-run physical
and D2 qualification, and only then regenerate voxel removal authority.

## Acceptance evidence

The fixed corpus reports, per component:

- solve status;
- maximum and mean terrain/head raise;
- post-refinement uphill-segment count;
- maximum residual upclimb;
- maximum absolute longitudinal grade;
- QP primal residual.

Numerical failure is never accepted as a physical rejection.

## Next boundary

If the 8/81/77 components solve cleanly with small upward corrections, extend those corrections
laterally with the same cross-section ownership/taper rules used by the accepted F4A terrain field.
That extension must reduce or preserve excavation everywhere; it may never create a deeper cut than
F4B.
