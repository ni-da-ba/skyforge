# Hydrology reset tranche D0 — geomorphic diagnostic characterization

**Status:** dependent evidence tranche under issue #1084  
**Depends on:** Tranche C / PR #1089  
**Terrain mutation:** none  
**Acceptance thresholds:** deliberately not yet frozen

## Purpose

The reset requires hard machine rejection for implausible hydromorphology, but choosing those limits
from one failed screenshot would repeat the same overfitting mistake in a different form.

D0 therefore persists a deterministic diagnostic corpus before introducing rejection thresholds.

The first manifest records, per fixed specimen:

- route/node/confluence counts;
- maximum and mean centerline lowering implied by the hydraulic candidate;
- maximum longitudinal water-surface slope;
- mean raw-terrain uphill-step fraction;
- maximum route ridge-sample fraction;
- mean valley-floor advantage;
- maximum deviation from coarse semantic guidance;
- maximum bankfull half-width;
- maximum water-depth potential.

## Fixed corpus

The evidence includes:

- key 287 — primary proving ground;
- key 632 — current confluence control;
- key 649 — historical confluence/control specimen;
- key 811 — stress specimen;
- key 83 — retained-basin control;
- keys 77 / 118 / 512 — prior deterministic hydrology controls/stress members.

The corpus can grow when a distinct physical failure mode requires it. It must not be replaced merely
because a specimen is inconvenient.

## Calibration rule

A later hard threshold must satisfy all of the following:

1. it corresponds to a physical/geomorphic failure mode rather than screenshot aesthetics;
2. its dimensional or normalized meaning is documented;
3. synthetic tests demonstrate both a valid and invalid side of the boundary;
4. the fixed corpus is reported before and after the threshold;
5. the threshold is not selected solely to make all existing specimens pass.

Some existing specimens are expected to fail future qualification. That is the point of the reset.

## Next diagnostic additions

Before final qualification, D1 must add cross-section-aware metrics:

- lateral recovery grade;
- water containment relative to natural banks;
- depth/width or relief/valley-width compatibility in a documented normalized coordinate system;
- approximate cut volume;
- curvature relative to bankfull width.

Only after those exist should #1084 freeze a first rejection envelope.
