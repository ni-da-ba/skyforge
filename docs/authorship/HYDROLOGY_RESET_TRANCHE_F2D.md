# Hydrology reset tranche F2D — shared transition-ownership terrain guard

**Status:** staged follow-on; merge only after F2C  
**Depends on:** F2C terminal-fate semantics and shared fail-closed policy  
**Hydraulic geometry changes:** none  
**D2/E2 threshold changes:** none  
**Minecraft changes:** none

## Purpose

F1 terrain realization predates F2C's explicit watershed-backed terminal-fate semantics. It already
fails closed on D2 rejection, confluences, and CASCADE reaches, but terminal ownership must use the
same semantics as the bounded hydraulic candidate layer.

F2D makes transition ownership a single shared contract consumed by both F2C and F1.

## Shared transition classifier

`SkyIslandFluvialTransitionOwnership` classifies one semantic reach from:

- shared geomorphic node kinds;
- semantic profile kinds;
- the explicit channel-terminal fate map produced by
  `SkyIslandChannelTerminalFatePlanner`;
- the single `SkyIslandChannelTerminalFatePolicy`.

The resulting reasons are:

```text
confluence endpoint      -> CONFLUENCE_TRANSITION_REQUIRED
any CASCADE profile      -> CASCADE_TRANSITION_REQUIRED
retained open water      -> RETAINED_WATER_TRANSITION_REQUIRED
retained wetland         -> WETLAND_TRANSITION_REQUIRED
unresolved terminal fate -> UNRESOLVED_TERMINAL_FATE
edge outlet              -> no terminal-fate deferral
```

A reach may carry multiple reasons. Classification is deterministic and order-stable.

## Terrain-authority rule

F1 remains:

1. D2 rejected -> rejected; zero terrain delta.
2. D2 accepted but shared transition classifier returns any reason -> deferred; zero terrain delta.
3. D2 accepted and no transition reason -> eligible for ordinary continuous fluvial terrain field.
4. Realized geometry is remeasured/requalified before terrain authority is published.

Thus a transition-owned reach cannot become ordinary merely because another layer omitted a newer
terminal-fate check.

## Evidence

The transition classifier is tested independently from D2 thresholds:

- key 632 proves confluence ownership;
- key 512 (6/61) proves CASCADE and unresolved-terminal ownership;
- 8/81 and 6/61 terminal fixtures prove that a terminal is free only when its explicit fate is
  `EDGE_OUTLET`.

Existing F1 tests continue to prove that deferred reaches are absent from
`terrainField.acceptedReaches`.

The shared terminal-fate class mapping itself remains exhaustively tested in F2C.

## Stop boundary

F2D does **not**:

- promote F2C profiles into F1 terrain realization;
- solve any transition geometry;
- alter route geometry;
- alter D2/E2 thresholds;
- calibrate POND/LAKE limits;
- touch Minecraft.

It is a consistency/safety tranche only.
