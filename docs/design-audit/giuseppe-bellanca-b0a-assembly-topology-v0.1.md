# Giuseppe Bellanca B0-A Assembly Topology v0.1

**Snapshot:** 2026-09-06  
**Status:** Source-constrained construction topology; live Sable specimen pending.  
**Parent:** CONTENT C12 / issue #239

## Purpose

Convert the GB-1A *Giuseppe Bellanca* from an aircraft concept into an assembly architecture that respects the actual Create 6.0.10 / Sable 2.0.5 / Create Aeronautics 1.3.2 design model used by Skyforge.

This record distinguishes:

1. the **main Sable rigid body**;
2. the **nested Create propeller contraption**;
3. later **child Sable control-surface sublevels**;
4. Sable actor blocks such as Wheel Mounts.

The design must work through those upstream mechanisms rather than pretending the entire aircraft is one ordinary Create contraption.

---

## 1. Upstream assembly model

### 1.1 Main aircraft

A Physics Assembler calls Simulated's single-block assembly search from the block on its sticky face.

The search:
- traverses supported/sticky/attached blocks;
- honors Super Glue and Honey Glue;
- includes diagonal attachment candidates used by Simulated;
- rejects unmovable blocks and blocks with destroy speed -1;
- is bounded by the upstream default of 128,000 moved blocks;
- moves collected glue into the resulting sublevel.

The Bellanca is far below the block-count limit. The real constraints are **connectivity, dynamic-boundary discipline, and component lifecycle**, not size.

### 1.2 Nested Create contraptions

Before blocks are moved into a Sable sublevel, Simulated explicitly finds controlled Create contraptions whose controller blocks belong to the assembly, disassembles them, and folds their blocks into the moving set.

This matters for the Propeller Bearing:

> A running propeller may be flattened during main aircraft assembly, then reassembled as a nested Create bearing contraption after the Propeller Bearing's kinetic network becomes active again.

Do not treat the propeller blades as permanently part of the main rigid body during operation.

### 1.3 Swivel Bearings

Simulated Swivel Bearings are designed to live on a Sable sublevel and then create/attach another Sable sublevel through a rotary constraint.

During the main Physics Assembler search, Simulated has a special Swivel Bearing case that explicitly includes the block on the bearing's FACING side.

Therefore the intended aircraft lifecycle is:

~~~text
STATIC WORLD BUILD
        |
        v
PHYSICS ASSEMBLER
        |
        v
one main Sable aircraft body
        |
        +--> Propeller Bearing reassembles nested Create propeller
        |
        +--> Swivel Bearing splits rudder/elevator/ailerons
             into constrained child Sable sublevels
~~~

This is the canonical topology unless live testing disproves it.

### 1.4 Wheel Mounts

Offroad Wheel Mounts are Sable BlockEntitySubLevelActor blocks. They remain blocks on the main aircraft sublevel and apply suspension/friction/braking/steering forces directly to that body.

They are **not** separate wheel contraptions.

---

## 2. Hard assembly rules

### A. No glue across a dynamic hinge

A control-surface cluster may be internally glued.

The main airframe may be internally glued.

But there must be **no Super Glue/Honey Glue volume bridging the Swivel Bearing separation plane**.

Otherwise the child assembly search can capture unintended tail/wing structure.

### B. No broad glue through the propeller plane

The Propeller Bearing controls the block cluster on its front/FACING side as a standard Create bearing contraption.

Do not place a broad glue sheet that spans from the static nose through the bearing into the blade assembly.

Internally glue the propeller blade cluster if required, but the bearing remains its controller boundary.

### C. Kinetic connection is not structural proof

Shaft/cog adjacency establishes Create rotational topology; it does not substitute for proving that the whole aircraft belongs to the Physics Assembler's moved block set.

Every static machinery cluster must also be structurally captured through an accepted support/stick/glue path.

### D. The Physics Assembler must remain supported before assembly

The Physics Assembler is a face-attached block and requires a supporting block face.

For B0-A, reserve a full structural floor block beneath the assembler rather than relying on a thin slab support.

### E. Mid-air disassembly is not an operating feature

Upstream default disallowMidAirDisassembly=true.

The Physics Assembler also attempts grid alignment and enforces velocity/angular-velocity limits during disassembly.

The Bellanca should therefore be treated as:

> assemble on the ground -> fly as a Sable body -> land before ordinary disassembly.

This is desirable and should not be compatibility-patched away for the aircraft.

---

## 3. Coordinate convention

~~~text
+X = starboard/right
-X = port/left
-Z = nose/forward
+Z = tail/aft
+Y = up
~~~

Reference neighborhood:

~~~text
(0,0,0) ~= desired wing/CG region
~~~

Current total structural envelope:

~~~text
X: -13 .. +13   main wing
Y: approximately 0 .. 5 before gear deflection
Z: -9 .. +7     propeller plane through tail
~~~

---

## 4. B0-A main wing

### 4.1 Lift surface

~~~text
Y = 4
Z = -1..+1
port:      X = -13..-3
starboard: X = +3..+13
~~~

This yields 66 regular Create Sail blocks.

First orientation hypothesis:

~~~text
create:white_sail[facing=up]
~~~

This is **not production-accepted until the live force sign is measured**.

Current Sable source predicts this orientation should generate upward body impulse under forward relative motion because Create Sail normal is FACING.opposite and Sable subtracts the calculated force.

### 4.2 Carry-through

Leading B0-A center section:

~~~text
Y = 4
Z = -1..+1
X = -2..+2
~~~

15 full light wooden structural blocks.

Initial material: minecraft:spruce_planks.

This deliberately favors assembly certainty over minimum mass. Replace some with slabs/stairs only after mass/assembly evidence says doing so is worthwhile.

---

## 5. B0-A fuselage skeleton

This is a **test skeleton**, not the production cabin shell.

### 5.1 Keel

~~~text
X = 0
Y = 1
Z = -5..+6
~~~

12 spruce-plank stations.

Functions:
- ties nose machinery to wing and tail;
- gives the Physics Assembler a continuous main-body path;
- provides a measurable longitudinal structural baseline;
- provides future cargo/seat attachment points near the CG.

### 5.2 Light floor rails

Provisional low-mass cabin floor support:

~~~text
X = -1,+1
Y = 1
Z = -4..+3
~~~

Use wooden slabs where no full support face is required.

These are optional for the first assembly-only proof but included in the packaging envelope.

### 5.3 Wing pylon

Connect keel to carry-through through the center:

~~~text
(0,2,0) minecraft:spruce_planks
(0,3,0) minecraft:spruce_planks
(0,4,0) <- existing carry-through
~~~

These two full planks are now part of the B0-A manifest. This intentionally favors deterministic structural capture over shaving 0.5 kpg before the first measured specimen.

---

## 6. Powerplant topology

### 6.1 Engines

Two Portable Engines meet on one X-axis source network:

~~~text
(-1,2,-5) Portable Engine, HORIZONTAL_FACING=EAST
(+1,2,-5) Portable Engine, HORIZONTAL_FACING=WEST
~~~

Each engine exposes its only kinetic shaft inward toward the center.

Structural mounts:

~~~text
(-1,1,-5)
(+1,1,-5)
~~~

tie both engines to the keel.

### 6.2 Prop governor

~~~text
(0,2,-5)
create:rotation_speed_controller
HORIZONTAL_AXIS=X
~~~

A dedicated large cogwheel must sit directly above:

~~~text
(0,3,-5)
create:large_cogwheel
AXIS=Z
~~~

This is required by the upstream Rotation Speed Controller topology.

### 6.3 Prop shaft

~~~text
(0,3,-6) create:shaft[axis=z]
(0,3,-7) create:shaft[axis=z]
~~~

### 6.4 Propeller Bearing

~~~text
(0,3,-8)
aeronautics:propeller_bearing
FACING=NORTH
~~~

For a BearingBlock facing NORTH:
- the kinetic shaft enters from SOUTH;
- the moved propeller assembly lies NORTH/front of the bearing.

This matches the proposed shaft train.

---

## 7. Nested propeller B0-A

### 7.1 Hub

First moved block:

~~~text
(0,3,-9)
~~~

Use a lightweight central propeller hub block compatible with standard BearingContraption assembly. A Z-axis Create shaft is the first candidate.

### 7.2 Blades

Use Simulated **Symmetric Sails**, not regular lifting sails.

Reason:
- they remain tagged as Create windmill sails, so the Propeller Bearing counts them as sail power;
- Simulated explicitly sets their Sable lift scalar to zero;
- they avoid adding unintended rotating lifting surfaces inside the propeller contraption.

Leading 8-sail cross:

~~~text
(+1,3,-9) (+2,3,-9)
(-1,3,-9) (-2,3,-9)

(0,4,-9) (0,5,-9)
(0,2,-9) (0,1,-9)
~~~

State:

~~~text
simulated:white_symmetric_sail[axis=z]
~~~

Nominal: 4 blades, 2 sail blocks/blade, 8 sail power.

### 7.3 Propeller preassembly requirement

The PROP_CHILD must exist as an actual controlled Create BearingContraption **before the main Physics Assembler fires**.

Reason:

- MAIN_BODY glue correctly stops at the Propeller Bearing;
- a static, unassembled blade cluster at Z=-9 is therefore not guaranteed to belong to the main Sable moved set;
- Simulated explicitly flattens controlled Create contraptions whose controller blocks are already included in the main assembly.

Ground assembly procedure:

~~~text
1. build/glue PROP_CHILD
2. supply the Propeller Bearing any small nonzero governed speed
3. confirm the bearing is running / blades are a Create contraption
4. activate the main Physics Assembler
5. Simulated flattens PROP_CHILD into the Sable moved block set
6. after main assembly, allow the Propeller Bearing to reassemble PROP_CHILD
~~~

A governor target as low as ~1 RPM is a leading assembly-mode candidate; exact minimum usable value is a live UX matter rather than a flight-performance setting.

### 7.4 Why four blades are assembly-robust

Create's current `StructureTransform` constructor rounds a controlled contraption's arbitrary bearing angle to the nearest 90-degree block rotation when converting it back to block positions.

The proposed four-blade cross is invariant under a 90-degree rotation.

Therefore an 8-sail four-blade prop can be flattened during main Sable assembly without requiring the player to stop it at an exact visual angle.

This is a strong upstream-derived reason to prefer the four-blade cross over a two- or three-blade asymmetric propeller for GB-1A.

### 7.5 Propeller glue boundary

Allowed:
- hub <-> blade internal glue;
- blade <-> blade internal glue where useful.

Forbidden:

~~~text
static nose ===GLUE=== moved blade cluster
~~~

The Propeller Bearing itself is the controller boundary.

---

## 8. Governor operating envelope

With 8 propeller sails and current Aeronautics/Create stress arithmetic:

| Propeller target | Approx. stress |
| ---: | ---: |
| 128 RPM | 2048 SU |
| 160 RPM | 2560 SU |
| 192 RPM | 3072 SU |
| 224 RPM | 3584 SU |
| 256 RPM | 4096 SU |

Two ordinary Portable Engines provide a theoretical combined 4096 SU under the current normal-speed stress scaling.

Interpretation:
- **128:** first safe propulsion proof;
- **160–192:** cruise-search region;
- **192–224:** climb/takeoff-search region;
- **256:** boundary/full theoretical capacity, not a normal accepted operating point.

The Rotation Speed Controller is the leading intact-GB-1A governor because the Propeller Bearing is already Brass-era.

The starter wreck must not yield either component intact.

---

## 9. Physics Assembler placement

Leading accessible cabin location:

~~~text
support block:     (0,1,+2) full spruce plank
Physics Assembler: (0,2,+2), floor-mounted
~~~

For a floor-mounted Physics Assembler, the sticky assembly direction is DOWN.

Thus the support block below becomes the initial moved block and must belong to the main keel/floor structure.

This location keeps the assembler inside the future cabin and close to the aircraft center.

Exact FACING is an ergonomic choice; FACE=FLOOR is the important assembly state.

---

## 10. Control-surface assembly architecture

B0-A does **not** add all controls before proving the main body. The architecture is nevertheless reserved now.

### 10.1 Rudder

Required hinge axis: vertical Y.

Use a Swivel Bearing facing UP or DOWN.

Leading tail-root candidate:

~~~text
bearing around: (0,3,+6)
FACING=UP
~~~

Child control-surface blocks begin immediately on the bearing's FACING side.

The rudder child cluster should use symmetric sails and remain internally glued without bridging back into the fixed fin/tail boom.

### 10.2 Elevator

Required hinge axis: X.

A more natural Minecraft realization is likely **two synchronized half-elevators**, one port and one starboard, each on its own outward-facing Swivel Bearing.

This requires live validation before coordinates are frozen.

### 10.3 Roll

Do not freeze differential-drag spoilerons merely because they are easy to describe.

The most promising next experiment is:

> movable regular-sail trailing-edge aileron child sublevels.

Under Sable's simple lift model, differential rotation of regular lifting sails may provide cleaner roll authority than drag-only spoilerons.

This is a test hypothesis, not an accepted production mechanism.

---

## 11. Input/control routing reservation

Simulated's Linked Typewriter already captures momentary W/A/S/D/Q/E/arrow/space input and broadcasts it through ordinary Create Redstone Link frequencies.

Therefore no bespoke flight-key input layer is justified.

Reserve cabin/control-system volume for:

~~~text
Linked Typewriter
    -> Redstone Link receiver/control logic
    -> low-speed actuator network
    -> Torsion Spring / Swivel Bearing
    -> aerodynamic control surface
~~~

Torsion Spring is promising because upstream behavior returns its output toward zero when its input stops, giving a reuse-first path to self-centering controls.

Do not add this full routing before one-axis control is proven.

---

## 12. Landing gear constraints

Wheel Mount behavior constrains packaging:
- Wheel Mount is an actor on the main Sable sublevel;
- the wheel occupies the mount's HORIZONTAL_FACING side;
- suspension reaches downward;
- braking reads redstone from the block directly above the Wheel Mount;
- steering reads differential redstone from the two lateral sides;
- an unpowered Wheel Mount can be used as a free-rolling landing wheel.

Leading main-gear geometry:

~~~text
port mount:      x ~= -2, y ~= 1, facing WEST
starboard mount: x ~= +2, y ~= 1, facing EAST
visible wheel center ~= x +/-3
large tire radius = 1.25
~~~

Do not install this until basic airborne control is understood.

### Propeller-clearance warning

With the propeller center at Y=3 and a two-block blade extension downward, the lowest prop sail reaches Y=1.

That may leave inadequate rough-field ground clearance once tire radius/suspension are realized.

Therefore **propeller center height is not frozen**.

Likely remedies, in order:
1. raise the powerplant/prop center one block;
2. increase main-gear stance/height;
3. reduce prop radius only if propulsion remains adequate.

---

## 13. Glue-domain map

Treat glue as part of aircraft architecture.

### 13.0 Upstream Super Glue limitation

NeoForge's default block `canStickTo` behavior only makes genuinely sticky blocks such as slime/honey stick by default. Ordinary adjacent planks, sails and machinery are **not** a sufficient aircraft assembly contract.

Create 6.0.10's Super Glue selection UI also limits the distance between its two selected block positions to less than 24 blocks.

The 27-block Bellanca wing therefore cannot safely be treated as one manually selected tip-to-tip glue region.

B0-A must use **multiple overlapping glue domains**.

Leading generated/manual-equivalent glue volumes:

~~~text
G-WING-PORT
  X -13..+2
  Y 4
  Z -1..+1

G-WING-STARBOARD
  X -2..+13
  Y 4
  Z -1..+1

G-FUSELAGE
  X -1..+1
  Y 1..4
  Z -8..+5

G-TAIL-ROOT
  X 0
  Y 1..3
  Z +4..+6

G-PROP
  X -2..+2
  Y 1..5
  Z -9
~~~

The two wing domains overlap the center carry-through. G-FUSELAGE overlaps the carry-through/pylon and G-TAIL-ROOT, producing one connected MAIN_BODY glue graph.

G-PROP is deliberately isolated on Z=-9 and cannot include the Propeller Bearing at Z=-8.

When control children are added, their own glue volumes must remain entirely outside the corresponding main-body hinge plane.

For automated fixture generation, these may be instantiated directly as equivalent SuperGlueEntity bounding boxes rather than requiring manual selection clicks.



### MAIN_BODY

May be mutually glued as necessary:
- keel;
- engine mounts;
- static powerplant;
- controller/cog/shaft support;
- fixed wing carry-through;
- fixed wing lift surface;
- future fixed cabin/fuselage;
- fixed tail boom/stabilizer.

### PROP_CHILD

Internally glued only:
- prop hub;
- symmetric-sail blades.

Boundary:

~~~text
MAIN_BODY | Propeller Bearing || PROP_CHILD
~~~

### CONTROL_CHILD_n

Internally glued only:
- rudder child;
- left/right elevator child;
- future left/right aileron child.

Boundary:

~~~text
MAIN_BODY | Swivel Bearing || CONTROL_CHILD
~~~

No glue envelope may cross the double-bar controller boundary.

---

## 14. Assembly sequence

### Phase 0 — static world build

Build:
- complete fixed main body;
- fixed wing;
- engines/governor;
- Propeller Bearing;
- propeller blade cluster;
- Swivel Bearing bases and intended child blocks when those axes are being tested;
- Physics Assembler;
- deliberate glue domains.

Keep the aircraft stationary and grid-aligned.

### Phase 0.5 — preassemble the propeller

Before the main Physics Assembler:

1. fuel/energize at least one valid power source;
2. command a small nonzero propeller RPM;
3. confirm Propeller Bearing has assembled PROP_CHILD as a ControlledContraptionEntity.

Do not main-assemble with PROP_CHILD merely sitting as static loose blocks in front of an unpowered bearing.

### Phase 1 — main Physics Assembler

Activate the Physics Assembler while the Propeller Bearing owns its controlled propeller contraption.

Expected result:
- all MAIN_BODY blocks move into one Sable sublevel;
- glue moves with the craft;
- existing controlled Create bearing contraptions owned by included controller blocks are flattened safely into the moved block set.

Record:
- exact moved block count;
- exact Sable mass;
- center of mass;
- bounding box;
- any unintentionally omitted/included blocks.

### Phase 2 — restore dynamic children

Once the main Sable aircraft exists:
1. allow/command Propeller Bearing to assemble its PROP_CHILD Create contraption;
2. separately assemble each tested Swivel Bearing CONTROL_CHILD into its child Sable sublevel.

Verify that no child captures fixed airframe blocks.

### Phase 3 — operation

Only after topology is correct:
- light Portable Engines;
- establish governed prop RPM;
- measure lift/thrust;
- test controls;
- add Wheel Mounts;
- test ground handling.

---

## 15. Initial mass budget

Source-backed nominal block masses:
- regular/symmetric sails: 0.25;
- planks: 0.5;
- wooden slabs/stairs/fences: 0.25;
- Create shafts/cogs/large cogs: 0.5;
- most unspecified machinery: default 1.0 unless its datapack says otherwise;
- Simulated Swivel Bearing explicitly: 1.0.

For the proposed B0-A wing + simple skeleton + powerplant, including the now-explicit two-block pylon, the first paper estimate remains roughly **41–45 kpg before full controls, landing gear, cabin shell, seats, instruments and cargo**.

This is only a planning estimate.

The first Sable assembly must replace it with actual mass/CG evidence.

The accepted rule remains:

~~~text
<=55 kpg   aspirational empty target
55-60      review but plausible
>60        explicit mass-reduction review before adding more power/lift
~~~

Do not make the aircraft fly by simply hiding ever more engines in an overweight shell.

---

## 16. First live acceptance gates

### B0-A1 — assembly integrity

Prove:
- Physics Assembler captures all intended MAIN_BODY blocks;
- no PROP_CHILD/control-child leakage;
- no unmovable block failure;
- main aircraft persists as one Sable body.

### B0-A2 — mass/CG

Record exact mass and X/Y/Z center of mass.

Correct heavy-component placement before adding cosmetic shell.

### B0-A3 — prop lifecycle

Prove:
- an unassembled static prop is correctly rejected as an unsafe assembly procedure or otherwise characterized;
- low-RPM preassembly creates the expected ControlledContraptionEntity;
- fourfold prop geometry survives Simulated's quarter-turn StructureTransform flattening;
- prop blade cluster survives main assembly lifecycle;
- Propeller Bearing can create its nested Create contraption on the Sable sublevel;
- 128 RPM operation works;
- shutdown/disassembly/reassembly does not capture the nose.

### B0-A4 — lift sign

At controlled forward velocity, prove whether create:white_sail[facing=up] supplies the intended upward body force.

If the sign hypothesis is wrong, fix block orientation, not Sable physics.

### B0-A4b — dual-engine cooperation

Because the two Portable Engines face inward from opposite sides of the same X-axis network, prove them in sequence:

1. left engine alone;
2. right engine alone;
3. both engines together.

Record:
- network direction;
- generated speed;
- stress capacity;
- whether upstream movement-direction auto-correction changes either engine's setting.

Do not assume two 64-SU generators sum cleanly until the live kinetic network says they do.

### B0-A5 — engine cutoff

After #240 is accepted into the relevant runtime:
- cut both engines on an assembled Sable aircraft;
- verify zero power;
- preserve fuel;
- restart.

This is the assembled-Sable gate still missing from #240.

---

## 17. What remains intentionally unresolved

Do not freeze yet:
- exact finished fuselage shell;
- final propeller height;
- final gear geometry;
- exact cargo bay;
- roll mechanism;
- exact elevator split;
- final control-key mapping;
- final cruise RPM;
- exact wing orientation until live lift proof;
- autopilot.

The next evidence should be **a machine-built/live B0-A specimen**, not more stylistic elaboration.
