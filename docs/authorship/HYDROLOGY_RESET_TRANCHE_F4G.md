# Hydrology reset tranche F4G — lateral shallower-only refinement evidence

**Status:** association-specific continuous evidence under issue #1084  
**Depends on:** solved F4F centerline head/bed refinement  
**Voxel / Minecraft authority:** none

## Purpose

F4F solves only centerline head and bed. F4G extends solved corrections laterally using the exact
cross-section field already accepted by F4A.

No new channel shape or taper is introduced.

## Construction

For each F4F-solved reach sample:

```text
raisePotential = F4F.terrainRaiseWorld / reliefBudget
refinedWaterPotential = F4A.waterPotential + raisePotential
refinedBedPotential   = F4A.bedPotential   + raisePotential
```

All other hydraulic geometry remains unchanged:

- centerline;
- discharge;
- bankfull width;
- water depth;
- profile family.

The existing F4A `SkyIslandQualifiedFluvialTerrainField` then reconstructs channel bed, bankfull
corridor, and valley recovery from those refined samples.

A water-blocked F4F component is not modified. Its accepted F4A terrain remains exactly unchanged.

## Hard invariants

F4G must prove:

- water depth is unchanged at every refined hydraulic sample;
- required centerline lowering never increases;
- the combined refined/unrefined field re-passes D2 for every realized reach;
- on the accepted F4C integer X/Z authority envelope:
  - refined terrain is never below F4A terrain;
  - refined terrain never rises above pre-hydrologic terrain;
  - at least one point is shallower when a non-zero F4F correction exists.

Because world realization applies semantic terrain delta to the compiled upper surface, this
pointwise semantic inequality also means F4G can never require a deeper world-space cut than F4B.

## Fixed component policy

For the 8/81/77 fixture:

- terminal 559 / reach 709→559 consumes the solved F4F correction;
- terminal 1842 / reach 1742→1842 remains unchanged because F4F is bank-containment-infeasible.

This is component-level refinement. No partial repair is introduced inside the blocked component.

## Why this is still evidence

F4G has not regenerated F4C voxel authority.

The accepted F4C/F4D removal plan remains the current discrete evidence, and a later tranche must
re-quantize the F4G field before any runtime mutation can consume the shallower terrain target.

## Next boundary

If F4G remains D2-clean and shallower-only, re-quantize the refined continuous field. The new
discrete plan may remove fewer blocks than F4C, never more. Only the solved water component may then
advance toward water voxelization; the bank-blocked component remains water-deferred.
