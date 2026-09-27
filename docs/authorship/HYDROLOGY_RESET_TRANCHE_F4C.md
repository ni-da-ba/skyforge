# Hydrology reset tranche F4C — removal-only voxel quantization

**Status:** backend-neutral integer-column discretization evidence under issue #1084  
**Depends on:** F4B compiled world-surface projection  
**Minecraft mutation authority:** none

## Purpose

F4B defines an already-qualified continuous world-space upper-surface target. F4C asks only how to
represent that target on an integer voxel lattice without reintroducing terrain reconciliation.

The quantizer is deliberately one-sided:

```text
quantizedUpperBoundary = ceil(F4B.targetUpperWorld)
targetMaximumSolidY    = quantizedUpperBoundary - 1
```

This choice guarantees:

```text
F4B.targetUpperWorld
    <= quantizedUpperBoundary
    < F4B.targetUpperWorld + 1
```

The backend may therefore leave less than one block of qualified cut unrealized, but it may never
excavate below the qualified continuous target.

## Exact compiled support

F4C measures the exact original integer solid interval for every F4A-authorized X/Z column using the
same strict-between-surfaces occupancy convention as current compiled terrain materialization.

A quantized target is admissible only when:

- the original compiled column contains exact solid support;
- the target maximum solid Y remains at or above the exact minimum solid Y;
- the target maximum never exceeds the original maximum;
- every Y in the measured support interval remains continuously solid.

Failure rejects the quantization plan. F4C does not add blocks, lower the underside, move the water
datum, or search for a nearby carrier.

## Authority mask

Only columns whose F4A terrain sample is not `UNAFFECTED` enter the F4C plan.

Rejected or deferred F3E/F4A components therefore produce exactly zero authorized and zero mutated
columns. A column inside a conservative scan envelope but outside F4A terrain authority is ignored.

## No reconciliation excavation

For each admitted column:

- removed blocks are exactly `originalMaximumSolidY - targetMaximumSolidY`;
- `removedSolidBlocks >= 0`;
- the discrete upper boundary is never below the continuous F4B target;
- quantization residual is in `[0, 1)` world units.

There is no additional carrier cut, isotonic backend solve, bank fill, or geometry rescue budget.

The archived H6 allowance of up to 16 blocks of carrier reconciliation has no analogue in F4C.

## First evidence target

The accepted 8/81/77 F4B envelope contains 8,969 authorized integer columns. F4C must:

- retain those same 8,969 columns as its authority envelope;
- mutate only a subset that actually crosses an integer surface boundary;
- report the total and per-column removal counts;
- prove maximum residual is strictly below one block;
- prove extra excavation below the qualified continuous target is exactly zero;
- keep primary-287, confluence-632, and lake-609 at zero voxel authority.

## Failure semantics

A physically unsupported authorized column rejects the plan. It is not omitted locally.

The next refinement may represent admission explicitly per complete F3E/F4A terminal component so one
unsupported component need not suppress an unrelated qualified component. That refinement must
preserve the same no-partial-realization rule within each component.

## Next boundary

Once F4C fixed evidence is green, the implementation lane may consume its quantized removal plan in
the Minecraft adapter.

That adapter must be a literal consumer of the F4C column plan. It may not recompute hydraulic grade,
deepen a channel for connectivity, fill banks, reroute, or otherwise repair the accepted geometry.

A human visual gate becomes meaningful only after that literal consumer exists.
