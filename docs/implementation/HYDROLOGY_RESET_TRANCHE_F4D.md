# Hydrology reset tranche F4D — literal NeoForge removal projection

**Status:** non-mutating implementation evidence under issue #1084  
**Depends on:** F4C component-level removal-only voxel quantization  
**Chunk mutation authority:** none

## Purpose

F4D is the first Minecraft-type consumer in the reset chain.

It does not reinterpret hydrology. It translates an accepted F4C quantization plan into exact
`BlockPos` removals.

For every F4C-qualified terminal component and every qualified column:

```text
remove Y = targetMaximumSolidY + 1 .. originalMaximumSolidY
```

No other positions are emitted.

## Authority rules

F4D may consume only F4C output.

It may not:

- route or reroute a channel;
- solve a new water grade;
- widen or deepen a cross section;
- reconcile a carrier;
- fill a bank;
- search neighboring columns;
- place water;
- alter a rejected F4C component;
- invent positions from legacy AUTH-0086 visible-water intent.

A rejected F4C component has zero F4D removal positions.

## Component identity

Each removal projection retains:

- the exact realized `SkyIslandWorldVolumeId`;
- the F3E/F4A terminal cell identity;
- an immutable ordered list of exact Minecraft block positions.

Cross-component duplicate removal positions fail closed.

The aggregate projected position count must equal the F4C accepted `totalRemovedSolidBlocks`
exactly.

## Non-mutation boundary

F4D deliberately exposes no chunk-apply method.

This tranche proves only that accepted backend-neutral voxel authority can be translated into a
Minecraft coordinate representation without changing its geometry or cardinality.

The next tranche must decide how those removals participate in the existing physical-admission,
chunk lifecycle, mutation-ledger, save/reload, and lighting contracts.

That runtime consumer must remain literal. It cannot use chunk state as permission to enlarge the
removal plan.

## Human review

No visual run is required for F4D because no world state changes yet.

A human visual gate becomes meaningful only after the literal removal projection is actually applied
through the accepted Minecraft lifecycle and water placement is derived from the same qualified
hydraulic solution.
