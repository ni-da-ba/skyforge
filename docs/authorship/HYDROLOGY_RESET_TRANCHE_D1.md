# Hydrology reset tranche D1 — cross-section and excavation diagnostics

**Status:** dependent evidence tranche under issue #1084  
**Depends on:** D0 / PR #1090  
**Terrain mutation:** none  
**Acceptance thresholds:** still not frozen

## Purpose

D0 records longitudinal and route diagnostics. D1 adds the lateral/cross-section measurements needed
to detect the quarry/trench failure class before any channel terrain is authored.

## Metrics

### Natural-bank containment

At each hydraulic sample, terrain is sampled one bankfull half-width to either side of the route
normal.

```text
containment = min(z_left_bank, z_right_bank) - water_surface
```

Negative values mean the candidate water surface already sits above at least one natural bank edge.
That is not automatically invalid, but it is strong evidence that the candidate would require
floodplain/bank restructuring rather than a contained ordinary channel.

### Semantic bank-recovery grade

Horizontal distance is normalized by island nominal radius:

```text
grade_semantic =
    (bank_terrain_potential - bed_potential)
    / (bankfull_half_width / nominal_radius)
```

This is an Authorship-space grade, not a literal Minecraft block slope. It allows deterministic
comparison before a backend chooses physical vertical scaling.

### Curvature-to-width ratio

Local polyline curvature is estimated from each three-point route neighborhood and multiplied by full
bankfull width:

```text
curvature_width = kappa * bankfull_width
```

Large values identify bends whose radius is small relative to the channel itself.

### Lower-bound excavation volume

D1 integrates centerline lowering over bankfull width and route distance in island-normalized
horizontal coordinates.

This is intentionally a **lower-bound proxy**, not a final cut volume: it assumes no additional
valley shoulder excavation. A later authored cross-section can only require equal or greater terrain
modification.

## Calibration discipline

These metrics are appended to the fixed D0 corpus. They are not pass/fail thresholds yet.

The first hard rejection envelope should be frozen only after:

- synthetic valid/invalid fixtures exist for each metric;
- the fixed corpus distribution is available;
- the relationship between semantic vertical potential and eventual backend block scaling is
  documented;
- any exception classes (for example deliberate gorge/cascade morphology) have explicit semantic
  ownership rather than threshold bypasses.

## Why this matters

The previous H6 line bounded absolute lowering but did not sufficiently couple vertical relief to
horizontal recovery distance. D1 makes that coupling observable before we author a new landform.
