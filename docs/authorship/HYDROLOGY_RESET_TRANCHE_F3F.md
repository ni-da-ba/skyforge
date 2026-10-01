# Hydrology reset tranche F3F — local cross-section head sensitivity

**Status:** diagnostic experiment under issue #1084  
**Depends on:** accepted C2 centerlines, unchanged D2 head envelopes, and F3D finite ordinary-span evidence  
**Terrain mutation:** none  
**Route authority:** none  
**Minecraft changes:** none

## Purpose

F3D shows that the key-287 route remains infeasible even after its authored CASCADE intervals are
removed from ordinary spans. The measured key-287 empty ordinary-sample gaps are approximately
0.142, 0.224, and 0.152 world units. Another span has a solved transition head outside its adjacent
D2 envelope.

Before designing a route optimizer, F3F tests a narrow diagnostic hypothesis:

> Does a small translation of a local channel cross-section, with its tangent and hydraulic
> attributes held fixed, materially reduce an empty pointwise D2 head interval?

For each failing station, the diagnostic evaluates the unchanged D2 head envelope at the accepted
centerline position and at four offsets: ±0.25 and ±0.50 bankfull half-width. It reports the offset
with the smallest non-negative envelope gap, or zero if no probe improves it.

## Fixed-corpus result

The exact-head Hydrology Reset Geometry run reports:

| Key-287 station fraction | Original gap (world units) | Best local offset (world units) | Residual gap |
| ---: | ---: | ---: | ---: |
| 0.1338234268 | 0.1419352561 | -1.1887785252 | 0.0 |
| 0.3446610118 | 0.2243063420 | +1.8687493269 | 0.0 |
| 0.3213393356 | 0.1520178924 | +4.4273231119 | 0.0 |

This supports a constrained centerline-candidate experiment, but does not qualify any shifted geometry.
Key 287 remains PHYSICAL_REJECTION: the separate 0.242424→0.333333 ordinary span still has a solved
CASCADE boundary head outside its D2 envelope, and the local probes do not solve transitions.

## Interpretation boundary

This is sensitivity evidence, not a candidate route. It does not check semantic-corridor membership,
route continuity, island interiority, neighboring station feasibility, aggregate geomorphic metrics,
or transition coupling. A reported offset must never be interpreted as permission to move the
centerline.

The probe changes no inputs to the authoritative span solve and does not change D2 limits. It grants no
head, terrain, water, voxel, or Minecraft authority. The original F3D pass/fail result remains
authoritative.

## Decision from evidence

- If small cross-section offsets consistently reduce the gap, the next bounded tranche may develop a
  centerline-candidate optimizer that evaluates the unchanged D2 envelope while enforcing existing
  semantic-corridor, continuity, terrain, and interiority constraints. Its selected result must then
  be independently requalified through F3D.
- If the local gap does not improve, do not widen D2. Inspect the active D2 constraints and other
  route/transition geometry causes before implementing an optimizer.
- A candidate remains rejected unless the complete coupled transition/ordinary chain independently
  solves and passes unchanged D2.

## Validation

The exact-head Hydrology Reset Geometry workflow runs the fixed key-287 and control corpus and emits
the sensitivity values inside the ordinary-span diagnostic. Focused tests require the probe gap to be
finite, non-negative, no worse than its unshifted baseline, and explicitly labeled as local-only.
CI provides compilation and regression validation. No local Gradle or Minecraft build is permitted.

A new Minecraft review is not warranted until a genuinely different qualified product specimen exists.
