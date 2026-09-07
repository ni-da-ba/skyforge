# Giuseppe Bellanca B0-A Mass and Balance Note v0.1

**Snapshot:** 2026-09-06  
**Status:** Analytic estimate from the B0-A assembly manifest; live Sable measurement is authoritative.  
**Parent:** CONTENT C12 / issue #239

## Purpose

Estimate the mass and longitudinal balance of the exact B0-A assembly topology before building the live specimen.

This note is intentionally narrow. It does not attempt to predict the finished aircraft's final mass from aesthetics.

## Source-backed mass assumptions

Current Sable behavior used by this estimate:

- default block mass: **1.0 kpg**;
- `#sable:super_light`: **0.25 kpg**;
- `#sable:light`: **0.5 kpg**;
- Create windmill sails are super-light;
- planks are light;
- Create shafts/cogs/large cogs are light;
- Simulated Swivel Bearing has an explicit mass of 1.0;
- unspecified machinery remains 1.0 unless the live datapack states otherwise.

Portable Engines are 64-SU-capacity, 32-RPM generators in the audited upstream registration. This affects propulsion sizing, not their Sable mass, which is treated as the default 1.0 for this paper estimate.

## B0-A paper mass

### Lift and structure

| Group | Count | Unit mass | Mass |
| --- | ---: | ---: | ---: |
| regular main-wing sails | 66 | 0.25 | 16.5 |
| spruce carry-through | 15 | 0.5 | 7.5 |
| spruce keel | 12 | 0.5 | 6.0 |
| spruce center pylon | 2 | 0.5 | 1.0 |
| engine mounts | 2 | 0.5 | 1.0 |

Subtotal: **32.0 kpg**

### Static machinery

| Component | Count | Assumed unit mass | Mass |
| --- | ---: | ---: | ---: |
| Portable Engine | 2 | 1.0 | 2.0 |
| Rotation Speed Controller | 1 | 1.0 | 1.0 |
| large cogwheel | 1 | 0.5 | 0.5 |
| prop shafts | 2 | 0.5 | 1.0 |
| Propeller Bearing | 1 | 1.0 | 1.0 |
| Physics Assembler | 1 | 1.0 | 1.0 |

Static machinery subtotal: **6.5 kpg**

The Physics Assembler support at (0,1,+2) is already a keel block and is not counted twice.

### Nested propeller

| Component | Count | Unit mass | Mass |
| --- | ---: | ---: | ---: |
| shaft hub | 1 | 0.5 | 0.5 |
| symmetric prop sails | 8 | 0.25 | 2.0 |

Nested propeller nominal block mass: **2.5 kpg**

### Totals

~~~text
main Sable body before nested prop mass treatment: 38.5 kpg
nominal complete block set including prop child: 41.0 kpg
~~~

The live specimen must determine exactly how Sable's mass/inertia accounting treats the active nested Create bearing contraption.

Do not silently assume the 2.5-kpg prop child is or is not reflected in the main body's rigid-body mass.

## Paper centroid

Using B0-A block coordinates as integer station indices:

### MAIN_BODY only

~~~text
mass = 38.5
X centroid = 0.000
Y centroid = 3.143
Z centroid = -0.831
~~~

### MAIN_BODY + nominal prop-child block mass

~~~text
mass = 41.0
X centroid = 0.000
Y centroid = 3.134
Z centroid = -1.329
~~~

Minecraft/Sable uses block centers, so an absolute world-coordinate center receives the common +0.5 block-center offset. The **relative longitudinal separation** is unchanged and is what matters here.

The geometric center of the 66-sail main wing is at:

~~~text
Z = 0
~~~

Therefore the bare complete block-set estimate puts the mass centroid approximately:

> **1.33 blocks ahead of the main wing geometric lift center.**

This is directionally plausible for a conventional aircraft but too nose-forward to freeze as the finished balance.

## Why the current nose bias is acceptable

B0-A intentionally omits systems that will add mostly central/aft mass:

- cockpit structure and glazing;
- pilot/copilot seats;
- control hardware;
- cabin floor;
- cargo fixtures;
- Wheel Mount landing gear;
- tail fixed structure;
- Swivel Bearings and control children;
- instruments;
- later avionics.

The engine/propeller mass is already present.

Therefore the expected design evolution is naturally aftward rather than requiring artificial ballast.

## Target finished longitudinal balance

Initial target for the first controllable Bellanca:

~~~text
CG approximately 0.4 to 0.8 blocks ahead of the main-wing lift center
~~~

This is a **test target**, not an aerodynamic law.

It gives:

- positive conventional static-balance intuition;
- less trim demand than the bare 1.33-block offset;
- room for cargo near the CG without wild handling changes.

### Example mass migration

If the 41-kpg paper skeleton gains approximately 8–12 kpg of legitimate cabin/control/gear structure centered around Z≈+2:

~~~text
+8 kpg @ Z=+2  -> CG ~ Z=-0.79
+10 kpg @ Z=+2 -> CG ~ Z=-0.68
+12 kpg @ Z=+2 -> CG ~ Z=-0.58
~~~

This is close to the desired first-test envelope without adding ballast solely for balance.

Do not add dead mass simply to hit these numbers before the real component layout exists.

## Tail implication

If the final CG settles around Z=-0.6 and the fixed tail aerodynamic center is around Z=+6, the main-wing lift arm is ~0.6 blocks while the tail arm is ~6.6 blocks.

A first-order trim ratio is then approximately:

~~~text
tail downforce / main-wing lift
~ 0.6 / 6.6
~ 9%
~~~

At equal local pressure/airspeed and the same regular-sail lift coefficient, that corresponds to roughly **six main-sail-equivalent tail surfaces** against the 66-sail wing.

This suggests the Bellanca does **not** need an enormous aerodynamic tail if the cabin mass brings the CG aft as expected.

Leading later experiment:

> a modest fixed horizontal tail using regular sails oriented for downward force, plus a separate movable elevator child.

This is only a Sable-specific control hypothesis. Do not freeze its exact blocks until the main wing force sign and CG are measured.

## Vertical balance

The current bare paper centroid is high because 24.0 kpg of wing/carry-through structure sits at Y=4.

Landing gear, floor, passengers/cargo and cabin machinery below the wing should lower the final CG.

That is desirable for ground handling, but the vertical target should be learned from actual Sable taxi/roll behavior rather than imported from real-aircraft rules.

## Payload policy

Cargo should straddle the wing/CG region where possible.

Do not put the primary cargo inventory at the extreme tail merely because the cabin has visual space there.

Acceptance should include:

- empty;
- pilot only;
- pilot + passenger;
- representative cargo;
- maximum intended light cargo;

with measured CG shift for each.

## Required live evidence

The B0-A specimen must emit or record:

1. main-sublevel mass before Propeller Bearing assembly;
2. main-sublevel center of mass;
3. mass/CG after nested propeller assembly if Sable reports a change;
4. representative added cabin/gear mass;
5. eventual loaded CG cases.

If nested Create contraption mass does not participate in the main rigid-body mass the way the paper model assumes, re-balance the airframe from measured values rather than compatibility-patching Sable.

## Current decision

Do **not** move the engines aft yet.

The present forward machinery cluster:
- packages cleanly around the governor/prop shaft;
- leaves the cabin behind it;
- is expected to be counterbalanced naturally by required aircraft systems.

Only relocate heavy propulsion after a live mass/CG specimen proves the complete architecture cannot reach the desired balance without dead ballast.
