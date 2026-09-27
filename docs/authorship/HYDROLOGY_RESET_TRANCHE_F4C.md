# Hydrology reset tranche F4C — one-sided voxel quantization evidence

**Status:** backend-specific read-only quantization evidence under issue #1084  
**Depends on:** F4B exact authored-realization world-surface projection  
**Minecraft mutation authority:** none

## Purpose

F4B defines a physically admitted continuous world-space upper-surface target for an exact
AUTH-0046 authored-realization association.

F4C asks only:

> What integer solid-column support results from ordinary voxel quantization of that already-qualified
> continuous target?

It does not solve another hydraulic problem and does not repair connectivity.

## Quantization rule

For one integer world X/Z column, let the existing exact compiled support be:

```text
originalMinimumY .. originalMaximumY
```

and let F4B provide `targetUpperWorld`.

F4C defines:

```text
targetCapY     = ceil(targetUpperWorld) - 1
targetMaximumY = min(originalMaximumY, targetCapY)
```

Every retained integer solid sample therefore satisfies:

```text
Y < targetUpperWorld
```

The discrete top face is:

```text
discreteTopBoundary = targetMaximumY + 1
```

and the one-sided surface error is:

```text
quantizationError = discreteTopBoundary - targetUpperWorld
0 <= quantizationError < 1 block
```

This deliberately prefers under-realizing the cut by less than one block over excavating below the
continuous target.

## Original support authority

F4C reuses the accepted exact compiled-column support calculation used by the NeoForge backend.

It may only remove solid voxels from that original exact support. It cannot:

- add solid support;
- move the compiled underside;
- synthesize a new carrier;
- change the F4B target;
- infer a different authored-realization association.

If the integer cap would erase the entire original support column, F4C fails closed.

## Explicitly forbidden H6 behavior

F4C contains no:

- isotonic carrier solve;
- channel-carrier reconciliation budget;
- bank fill;
- levee synthesis;
- water-surface clamping;
- connectivity excavation;
- route-preservation surgery;
- retained-water reconciliation;
- material or fluid placement.

In particular, the archived H6 `MAX_CHANNEL_CARRIER_RECONCILIATION_BLOCKS = 16` concept has no
analogue in F4C.

## Authority containment

A column outside F4A/F4B terrain authority must retain its original exact support unchanged.

A column inside F4A/F4B authority may remove only the number of top solid voxels implied directly by
the F4B target cap.

The quantizer is read-only and never touches live or generated chunks.

## Evidence gate

The fixed 8/81/77 AUTH-0046 association is scanned across the conservative integer envelope covering
both admitted F4A reaches.

Evidence records:

- scanned and supported columns;
- F4A/F4B-authorized columns;
- columns whose integer support actually changes;
- total and per-column removed solid voxels;
- minimum and maximum one-sided quantization error;
- the same measures grouped by semantic reach provenance.

Primary-287 remains a negative control: route probes with exact physical support must receive zero
hydrology voxel mutation because F4A grants no terrain authority.

## Next boundary

Only after F4C proves deterministic sub-block quantization may a later tranche define a **mutation
plan** for those already-known removed voxels.

That later plan must still be data-only before it is attached to chunk lifecycle. Water occupancy and
flow are separate concerns and must not be used to justify deeper terrain excavation.
