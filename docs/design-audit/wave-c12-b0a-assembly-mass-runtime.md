# Wave C12 B0-A1/A2 — live Bellanca assembly and mass

**Status:** IN PROGRESS  
**Parent:** CONTENT C12 / issue #239  
**Historical input:** PR #242 (closed unmerged; source-constrained proposal only)  
**Runtime:** accepted C11 flight-only Create + Sable + Aeronautics/Simulated stack

## Purpose

The first executable C12 tranche intentionally proves only:

- **B0-A1 assembly integrity** for the historical MAIN_BODY proposal;
- **B0-A2 live mass and center of mass** from Sable itself.

It does not yet prove the propeller child lifecycle, lift sign, propulsion, control surfaces, landing
gear, ground handling, or flight.

## Recomposition rule

PR #242 is not an accepted aircraft implementation.

Its topology/manifest are reused because they encode a source-audited assembly hypothesis. The live
fixture is authoritative and may disprove them.

The paper values:

```text
MAIN_BODY mass ~= 38.5 kpg
MAIN_BODY integer-coordinate centroid ~= (0.000, 3.143, -0.831)
```

are diagnostics only. Sable's live `MassData` decides the actual result.

## Exact B0-A1 MAIN_BODY

The fixture builds 105 distinct blocks:

```text
66  create:white_sail main-wing lift blocks
15  spruce carry-through blocks
12  spruce keel blocks
 2  spruce center-pylon blocks
 2  spruce engine mounts
 2  simulated:red_portable_engine
 1  create:rotation_speed_controller
 1  create:large_cogwheel
 2  create:shaft
 1  aeronautics:propeller_bearing
 1  simulated:physics_assembler
```

The assembler support at relative `(0,1,+2)` is already one of the keel blocks and is not counted
twice.

PROP_CHILD at Z=-9 is deliberately absent from this tranche.

## Structural authority

Ordinary adjacency is not treated as an aircraft assembly guarantee.

The fixture materializes real Create `SuperGlueEntity` volumes equivalent to PR #242:

```text
G-WING-PORT       (-13,4,-1) -> ( 2,4, 1)
G-WING-STARBOARD ( -2,4,-1) -> (13,4, 1)
G-FUSELAGE        ( -1,1,-8) -> ( 1,4, 5)
G-TAIL-ROOT       (  0,1, 4) -> ( 0,3, 6)
```

It then activates the actual moved `simulated:physics_assembler` block entity. No fake assembly
helper bypass is used for B0-A1.

## B0-A1 acceptance

The run must prove:

1. exactly one new Sable `ServerSubLevel` is created;
2. the moved Physics Assembler is the sublevel's primary assembler;
3. all 105 intended source positions become air;
4. every intended block appears at the exact common integer translation in the sublevel;
5. every moved BlockState equals the placed source BlockState;
6. the translated MAIN_BODY bounding box contains exactly 105 non-air blocks;
7. no PROP_CHILD block is required for this result.

## B0-A2 evidence

The fixture reads the new `ServerSubLevel.getMassTracker()` and records:

- live merged mass in kpg;
- live center of mass in Sable plot coordinates;
- center of mass translated back into B0-A local coordinates;
- diagnostic deltas from the old paper estimate.

Classification:

```text
mass <= 55       ASPIRATIONAL_RANGE
55 < mass <= 60  REVIEW_RANGE
mass > 60        MASS_REDUCTION_REQUIRED
```

The workflow does not falsify Sable by forcing the paper estimate. If live mass exceeds 60, the
evidence is still valid but C12 must perform explicit mass-reduction review before adding more
systems.

## Downstream

After B0-A1/A2 is characterized:

1. B0-A3 nested propeller lifecycle;
2. B0-A4 live main-wing lift sign;
3. B0-A4b left-engine / right-engine / dual-engine kinetic cooperation;
4. 128-RPM prop gate and later governor ladder;
5. one control axis at a time;
6. landing gear;
7. flight/power-off acceptance.

The #237 human redstone ergonomics gate should be evaluated on the eventual real C12 control
packaging; it does not block this engineering tranche.
