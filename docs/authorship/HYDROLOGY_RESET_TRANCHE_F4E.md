# Hydrology reset tranche F4E — qualified world-space water surface

**Status:** backend-neutral hydraulic projection evidence under issue #1084  
**Depends on:** accepted F4A hydraulic solution, F4B world-space terrain projection, F4C terrain voxel admission  
**Water voxel/Minecraft authority:** none

## Purpose

F4E projects the already-qualified F4A hydraulic water surface into the same exact compiled world
frame used by F4B.

It does not solve a new route or water grade.

For an F4A-wet sample:

```text
waterWorld =
    compiledUpperWorld
    + (F4A.waterSurfacePotential - F4A.originalTerrainPotential)
      * reliefBudget
```

Because F4B uses:

```text
terrainTargetWorld =
    compiledUpperWorld
    + (F4A.targetTerrainPotential - F4A.originalTerrainPotential)
      * reliefBudget
```

the world-space hydraulic depth is preserved exactly:

```text
waterWorld - terrainTargetWorld
    =
(F4A.waterSurfacePotential - F4A.targetTerrainPotential)
* reliefBudget
```

A dry F4A sample receives no F4E water authority.

## Why world-space head must be rechecked

The compiled realization carries physical detail independent of the placement-free semantic terrain
field. Applying the accepted semantic hydraulic offset locally preserves water depth, but it can
perturb absolute world-space water head along a reach.

F4E therefore measures, for every realized reach:

- minimum and maximum projected water head;
- count of downstream segments whose projected head rises;
- maximum world-space uphill step;
- maximum uphill grade;
- maximum absolute longitudinal grade.

This is an admission diagnostic, not a backend repair opportunity.

If the exact physical association reintroduces materially uphill or over-steep water, the next step
must solve or reject the association-specific continuous hydraulic head upstream of voxelization.
Minecraft may not deepen terrain, clamp water, or run an isotonic carrier reconciliation to hide the
failure.

## Authority mask

F4E water exists only where F4A marks the continuous cross-section `wet`.

The fixed corpus additionally evaluates water only over F4C-qualified terrain columns. A component
that fails physical terrain voxel admission therefore cannot gain downstream water authority.

Primary-287, confluence-632, and lake-609 remain dry controls because F4A grants them no realized
terrain reach.

## First fixed-corpus result

The direct depth-preserving projection is **not** admitted for the two current 8/81/77 production
components.

Across the F4C-qualified terrain envelope:

- qualified terrain components: `2`;
- directly qualified water components: `0`;
- components requiring continuous head refinement: `2`;
- authorized terrain columns: `8,969`;
- F4A-wet integer columns: `2,477`;
- projected water depth range: `0.002619649 .. 2.805327115` world units;
- projected water-surface offset from original compiled upper surface:
  `-5.419589927 .. +2.365899859` world units.

### Reach 709→559

- centerline samples: `35`, all wet;
- projected world-water head: `269.463026857 .. 282.533331955`;
- downstream uphill segments: `1`;
- maximum uphill step: `0.168878403` world units;
- maximum uphill grade: `0.084049826`;
- maximum absolute longitudinal grade: `0.210190740`;
- F4E qualification: `HEAD_REFINEMENT_REQUIRED`;
- hard violation: `UPHILL_HEAD`.

### Reach 1742→1842

- centerline samples: `24`, all wet;
- projected world-water head: `273.172341988 .. 281.340191248`;
- downstream uphill segments: `2`;
- maximum uphill step: `0.034208932` world units;
- maximum uphill grade: `0.017275969`;
- maximum absolute longitudinal grade: `0.246577288`;
- F4E qualification: `HEAD_REFINEMENT_REQUIRED`;
- hard violation: `UPHILL_HEAD`.

Both reaches remain below their existing D2 maximum longitudinal-grade envelope. The failure is
strictly the reintroduced uphill head, not excessive total grade.

Primary-287, confluence-632, and lake-609 retain zero terrain and zero water authority.

This result demonstrates why water cannot simply follow the local compiled surface offset. The next
tranche must refine association-specific continuous head while preserving upstream hydraulic/terrain
constraints. Minecraft remains prohibited from clamping or repairing the grade.

## No voxel authority

F4E defines no integer water Y, source block, propagation rule, bank fill, or connectivity repair.

A later tranche may discretize F4E only after world-space hydraulic head diagnostics pass. Discrete
water must remain contained by the already-qualified terrain voxelization and may not trigger further
terrain excavation.
