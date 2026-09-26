# Hydrology reset tranche F2D — terminal-fate terrain-authority guard

**Status:** staged follow-on; do not merge before F2C acceptance  
**Depends on:** accepted F2C topology-backed channel terminal fate  
**Hydraulic geometry changes:** none  
**D2/E2 threshold changes:** none  
**Minecraft changes:** none

## Purpose

F1 predates F2C's explicit channel-terminal fate semantics. Its terrain-authority guard currently
fails closed for D2 rejection, confluences, and CASCADE transitions, but it can still authorize an
otherwise accepted reach whose semantic channel terminal drains through additional watershed cells
into retained water.

F2D closes that authority gap without broadening realization.

## Authority rule

Every semantic channel terminal is resolved through the accepted watershed graph.

Only:

```text
EDGE_OUTLET
```

may retain ordinary F1 terrain authority.

These fates are transition-owned and contribute zero fluvial terrain delta until their dedicated
transition mathematics exists:

```text
RETAINED_OPEN_WATER -> RETAINED_WATER_TRANSITION_REQUIRED
RETAINED_WETLAND    -> WETLAND_TRANSITION_REQUIRED
UNRESOLVED          -> UNRESOLVED_TERMINAL_FATE
```

The guard uses the same `SkyIslandChannelTerminalFatePlanner` accepted by F2C. It does not infer basin
ownership from coordinate coincidence or channel-terminal/sink cell equality.

## Failure-closed ordering

F1/F2D classification remains:

1. D2 rejection -> rejected, zero terrain delta;
2. D2 acceptance + any unresolved transition ownership -> deferred, zero terrain delta;
3. only D2 acceptance + no transition deferral -> eligible for continuous fluvial terrain field;
4. realized geometry is remeasured/requalified before publication as terrain authority.

F2D does not turn a rejected basin into an edge outlet and does not invent a hidden transfer.

## Evidence

Transition-guard tests use a deliberately permissive **test-only** geomorphic policy so they test
authority ownership independently from the current D2 calibration:

- confluence proving ground: key 632;
- CASCADE proving ground: key 512;
- retained-open-water proving ground: key 83.

Each must show the relevant accepted reach in `deferredQualifications` and absent from
`terrainField.acceptedReaches`.

Production behavior continues to use the unchanged evidence-backed D2 policy.

## Stop boundary

F2D does not:

- replace the historical F1 hydraulic profile with F2C;
- solve confluence, cascade, basin, wetland, or hidden-transfer geometry;
- calibrate POND/LAKE acceptance;
- change terrain cross-section formulas;
- change D2/E2 limits;
- touch Minecraft.

It is a safety correction only: unresolved terminal ownership cannot receive terrain authority.
