# Aircraft pitch/roll control semantics v0.1

**Authority:** AIRCRAFT-DESIGN-001 / issue #687
**Scope:** first-principles semantic contract and discrete feasibility only
**Production status:** design evidence; no pitch/roll lowering or runtime qualification

## Coordinate and moment convention

The production blockspace coordinate system is authoritative:

- `+x`: nose to tail; aircraft forward is local `-x`;
- `+y`: up;
- `+z`: starboard.

Commands are defined by physical effect, never by guessed target-actuator sign:

| Semantic command | Physical effect | Desired moment sign |
|---|---|---:|
| `PITCH_NOSE_UP` | nose rises; tail incremental force is downward | `-Mz` |
| `PITCH_NOSE_DOWN` | nose lowers | `+Mz` |
| `ROLL_STARBOARD_DOWN` | starboard wing descends; port wing rises | `+Mx` |
| `ROLL_PORT_DOWN` | port wing descends; starboard wing rises | `-Mx` |

Pitch requires symmetric left/right elevator response. Roll requires antisymmetric port/starboard aileron response. A future target adapter must derive concrete actuator sign from inspected exact-stack behavior; the semantic contract must not encode a guessed Swivel/Steering Wheel sign.

## Accepted Guild utility lattice

The accepted analytical geometry is transcribed at 2 blocks/m. The deterministic current blockspace contains:

- wing: 64 aerodynamic surface cells at `y=4`, spanning `z=-10..10`, with taper producing `x=7..10`;
- horizontal tail: 19 aerodynamic surface cells at `y=3`, spanning `z=-3..3`, with taper producing `x=15..17`;
- corrected yaw lineage reserves parent Swivel position `[18,3,0]` and the vertical rudder child above it.

A naive full-span elevator placed on the aft x=18 row would collide with the corrected yaw bearing at `[18,3,0]`. Pitch lowering therefore cannot simply mirror the rudder pattern across the entire horizontal-tail span.

## Split-elevator feasibility candidate

This candidate is evidence that a collision-free symmetric pitch partition exists. It is not yet a production lowering prescription.

Proposed aerodynamic child cells:

- port elevator: `[18,3,-2]`, `[18,3,-1]`;
- starboard elevator: `[18,3,1]`, `[18,3,2]`.

Corresponding former trailing cells `[17,3,-2]`, `[17,3,-1]`, `[17,3,1]`, `[17,3,2]` become a one-cell parent/child air gap. The centerline cell `[17,3,0]` remains parent-owned so the accepted yaw bearing at `[18,3,0]` retains its mount and semantic partition.

A future target lowering can place one Z-axis hinge controller per elevator half outside the active span at `z=-3` and `z=+3`, seeded inward. Exact resource identity, mount details, and actuator sign remain target-adapter work.

The candidate preserves the horizontal-tail aerodynamic cell count: four parent trailing cells are replaced by four child aerodynamic cells one block aft. The whole-tail aerodynamic x-centroid therefore moves aft by `4/19` block, or approximately `0.1053 m` at 2 blocks/m. This is a target-realization delta that must be reported explicitly; it must not be folded back into the analytical design as a fitted correction.

## Differential-aileron feasibility candidate

A mirrored outboard candidate exists without consuming the wing tips:

- starboard aileron: `[10,4,4]` through `[10,4,8]`;
- port aileron: `[10,4,-4]` through `[10,4,-8]`.

The former trailing cells at `[9,4,4..8]` and `[9,4,-4..-8]` become one-cell air gaps. Candidate Z-axis hinge controllers sit at `[10,4,3]` and `[10,4,-3]`, seeded outward from parent-supported inboard positions.

Each aileron contains five aerodynamic cells. Their mean absolute span station is `|z|=6` blocks = `3.0 m`, providing substantial roll moment arm while leaving the extreme tips unmodified. Ten parent trailing cells are replaced by ten child aerodynamic cells one block aft, preserving the 64-cell wing aerodynamic count. The whole-wing aerodynamic x-centroid moves aft by `10/64` block = `0.078125 m`.

For `ROLL_STARBOARD_DOWN`, the target realization must produce a downward incremental force on the starboard side and an upward incremental force on the port side, yielding `+Mx`. The concrete deflection/actuator signs are intentionally unresolved until exact target behavior is qualified.

## Required invariants for future compiler work

Any later pitch/roll lowering must:

1. preserve target-neutral analytical geometry, mass, and stability authority; target realization deltas are separate diagnostics;
2. preserve mirror symmetry of elevator halves and aileron geometry;
3. preserve corrected yaw centerline ownership and all accepted propeller/powertrain partitions;
4. keep every dynamic child disjoint from parent-main and from other dynamic children except through its declared controller;
5. prove one-cell isolation or another independently qualified causal separation mechanism rather than relying on ordinary adjacency;
6. compute emitted control-surface cell count, area fraction, span station, centroid shift, and moment arm deterministically;
7. reject collisions with then-current powertrain, cockpit route, glue, yaw, propeller, and child domains;
8. use deterministic candidate ordering if more than one legal hinge/layout exists;
9. expose semantic command direction separately from target actuator sign;
10. keep pitch, roll, multi-axis control, stability, handling, and flight readiness false until their own runtime gates pass.

## What this audit does not establish

This audit does not select a Minecraft/Simulated mechanism, prove that a candidate Swivel arrangement assembles, prove aerodynamic force magnitude, establish stability derivatives, tune a controller, or qualify pitch/roll flight behavior. The layouts above establish only that current accepted geometry admits plausible, symmetric, collision-aware discrete control-surface partitions suitable for later production lowering and exact-stack falsification.
