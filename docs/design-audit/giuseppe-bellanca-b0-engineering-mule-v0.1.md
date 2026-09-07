# Giuseppe Bellanca B0 Engineering Mule v0.1

**Snapshot:** 2026-09-06  
**Status:** Build specification; exact in-game measurements pending.  
**Related:** Portable Engine cutoff issue #237.

## Purpose

Define the first deliberately ugly, mechanically honest GB-1A prototype.

B0 exists to answer:

> Can the intended Bellanca architecture work as a practical Sable/Create Aeronautics aircraft before aesthetic detailing?

B0 is **not** a production structure and is not the starter wreck.

## Source-backed mechanical facts used

Current audited Sable/Simulated/Aeronautics behavior supports:

- Simulated contraptions assembled by Physics Assembler;
- regular Create sails producing lift on Simulated contraptions;
- symmetric sails producing drag and serving as turning/stabilization surfaces;
- Portable Engines generating 32 RPM and accepting automated fuel;
- Propeller Bearings generating thrust from sail area and kinetic speed;
- Steering Wheels / Analog Transmission / Torsion Springs / Swivel Bearings as control-building blocks;
- Offroad Wheel Mounts providing physical suspension, friction, braking, and steering;
- ComputerCraft peripherals for several Simulated sensors/control blocks.

Sable mass classes also make sails and many light wooden/detail blocks significantly cheaper than dense structural blocks.

## Coordinate convention

~~~text
+X = starboard/right
-X = port/left

-Z = forward/nose
+Z = aft/tail

+Y = up
~~~

Reference origin:

~~~text
(0,0,0) ~= wing carry-through / target CG neighborhood
~~~

## Dimensional target

| Quantity | B0 target |
|---|---:|
| Wingspan | 27 blocks |
| Length | 16–17 blocks |
| Wing chord | 3 blocks |
| Core fuselage width | 3 blocks |
| Local cabin max width | 5 blocks |
| Internal cabin height | 3 blocks |
| Horizontal tail span | ~9 blocks |
| Main propeller | 5-block-class diameter |
| Empty Sable mass aspiration | <=55 |
| Mass investigation threshold | >60 |

Dimensions are prototype targets, not accepted production geometry.

## Main lifting wing

Envelope:

~~~text
Y ~= +4
Z = -1..+1
X = -13..+13
~~~

Central carry-through:

~~~text
X = -2..+2
Z = -1..+1
~~~

Outboard wing should begin with regular Create Sail lift surface.

Nominal first count:

~~~text
27 x 3 footprint = 81 positions
central 5 x 3 = 15 structural positions
target regular lift sails ~= 66
~~~

Do not add decorative wing mass until the lift/mass envelope is measured.

## Fuselage stations

Provisional longitudinal zones:

~~~text
Z -8      propeller hub
Z -7..-5  engine / gearing
Z -4..-2  cockpit
Z -1..+1  wing carry-through / CG
Z  0..+4  utility cabin / cargo
Z +4..+8  tail boom
Z +7..+8  empennage / tailwheel
~~~

These are build envelopes rather than final exact block placements.

## Powerplant

Leading B0 arrangement:

- 2 x Simulated Portable Engine;
- both mechanically coupled into one aircraft powerplant;
- one Propeller Bearing;
- four-blade propeller;
- provisional 8 sail power total.

The craft reads as a single-engine aircraft even though the Create powerplant uses two Portable Engine blocks internally.

### Propeller test ladder

Further source inspection shows the Simulated Analog Transmission uses discrete redstone-derived ratios rather than an arbitrary continuous output.

With a simple 32 -> 64 RPM first gear stage, representative attainable outputs include approximately:

~~~text
128.0 RPM
146.3 RPM
170.7 RPM
204.8 RPM
256.0 RPM
~~~

For an 8-sail-power Propeller Bearing, current Aeronautics stress arithmetic gives approximately:

~~~text
128.0 RPM -> 2048 SU
146.3 RPM -> 2341 SU
170.7 RPM -> 2731 SU
204.8 RPM -> 3277 SU
256.0 RPM -> 4096 SU
~~~

Two ordinary Portable Engines provide a theoretical combined 4096 SU at their native 32 RPM under current Create stress scaling.

Therefore:
- 256 RPM is a boundary/full-capacity point and should not be assumed as normal operation;
- ~170.7 RPM is a strong first cruise candidate;
- ~204.8 RPM is a strong first takeoff/climb candidate;
- actual useful settings remain subject to flight measurement.

Record at each point:

- stress usage;
- acceleration;
- measured thrust where instrumentation permits;
- top/equilibrium speed;
- handling;
- fuel consumption.

## Engine cutoff / powered soaring

B0 must eventually consume issue #237 or an equivalent accepted mechanism.

Mandatory modes:

~~~text
RUN
    engine produces normal power
    fuel timer advances

CUT
    engine produces zero power
    fuel timer is preserved
~~~

Power-off flight is normal operation.

### Mandatory glide acceptance

At representative cruise altitude/speed:

1. cut both engines;
2. stabilize aircraft;
3. establish best observed glide;
4. measure sink rate;
5. measure horizontal distance per altitude lost;
6. verify pitch/yaw/roll controllability;
7. restart engines;
8. verify safe climb recovery.

Failure mode:

> engine cut causes near-ballistic fall or leaves too little time to choose a landing site.

If B0 fails this, adjust wing/mass/stability before adding cosmetic detail.

## Fuel

Ground loading is sufficient for B0.

Each Portable Engine already has its own fuel inventory. Do not add a central automatic fuel manifold until flight testing proves it useful.

This intentionally keeps the mule simple.

Production Bellanca may later add:

- central fuel locker near CG;
- automated split feed;
- cockpit refueling access;
- reserve;
- fuel indication.

## Tail/control surfaces

Use actual aerodynamic/control-capable blocks, not decorative fake surfaces.

### Yaw

Leading B0:

- fixed vertical stabilizer;
- movable symmetric-sail rudder;
- Swivel Bearing;
- target deflection approximately +/-25–30 degrees.

### Pitch

Leading B0:

- fixed horizontal stabilizer;
- movable symmetric-sail elevator;
- Swivel Bearing;
- target deflection approximately +/-20–25 degrees.

### Roll

Still experimental.

First test:

- paired differential symmetric-sail wingtip drag surfaces/spoilerons.

Alternate if ineffective:

- movable regular-sail lifting surfaces;
- another minimal upstream control arrangement.

Do not freeze production wing architecture until roll control is demonstrated.

## Self-centering actuation

Torsion Springs are the preferred first actuator experiment because their behavior can support return toward neutral after command removal.

Desired behavior:

~~~text
pilot command
    -> control transmission
    -> actuator
    -> surface deflects

command released
    -> surface returns toward neutral
~~~

Avoid permanent snap-to-extreme controls.

## Pilot controls

B0 prioritizes flyability over cockpit aesthetics.

Candidate prototype input:

- Linked Typewriter / Redstone Link keyed control;
- Steering Wheel for one suitable continuous axis where useful;
- Throttle Lever / Analog Transmission for delivered propeller speed.

Required control channels:

~~~text
PITCH
ROLL
YAW
THROTTLE
ENGINE CUT
BRAKE
~~~

Later cockpit architecture can convert these into a more elegant yoke/pedal/lever layout.

## Landing gear

Leading B0:

- conventional taildragger;
- 2 x Offroad Wheel Mount main gear;
- Large Tire main wheels;
- 1 x small/normal steerable tailwheel;
- main gear wide enough for rough-field stability;
- main wheels near/slightly ahead of target CG.

Test:

- taxi;
- steering;
- braking;
- suspension;
- prop clearance;
- rough surface;
- slope;
- loaded landing.

If taildragger handling is irritating rather than usefully skillful, test tricycle gear before production lock.

## Instrumentation

B0 only needs enough instrumentation to measure flight.

Prefer existing Simulated sensors before any bespoke work:

- Altitude Sensor;
- Velocity Sensor;
- Gimbal Sensor when Brass-era profile is acceptable;
- Navigation Table where useful;
- CC:Tweaked logging for test flights.

Manual flight must remain possible without computer control.

## Mass/CG acceptance

Before beauty work, record:

- exact empty Sable mass;
- lateral CG;
- longitudinal CG;
- vertical CG;
- representative loaded mass;
- loaded CG shift.

Target:

~~~text
lateral CG ~= centerline
longitudinal CG ~= wing neighborhood, slightly forward as testing requires
empty mass <=55 aspirational
>60 triggers explicit mass review
~~~

Do not solve a badly overweight craft by reflexively adding more engines/lift.

## Test matrix

### A — assembly

- Physics Assembler captures intended craft;
- no accidental world attachment;
- save/reload;
- disassemble/reassemble;
- subassemblies remain valid.

### B — mass

- empty mass;
- CG;
- representative loaded mass;
- loaded CG.

### C — propulsion

- 128/146.3/170.7/204.8/256 RPM;
- stress;
- acceleration;
- equilibrium speed;
- restart.

### D — ground

- taxi;
- steering;
- brake;
- rough-field behavior;
- prop clearance.

### E — calm-air flight

- takeoff roll;
- rotation;
- climb;
- cruise;
- low-speed handling;
- control authority;
- stall/recovery behavior;
- landing distance.

### F — power-off

- glide;
- sink rate;
- control authority;
- restart;
- diversion/landing decision window.

### G — Skyforge atmosphere

Once the authoritative atmosphere profile is ready:

- headwind;
- tailwind;
- crosswind;
- updraft;
- turbulence;
- shear;
- reduced pressure.

### H — payload

- pilot only;
- passenger;
- modest cargo;
- maximum intended light cargo.

## Human-eye gate after physics

Only after the above is credible:

- silhouette pass;
- fuselage shell;
- struts;
- glazing;
- service markings;
- interior;
- detailed avionics.

Required visual question:

> Does the craft look intentional from below as well as above?

## Exit criteria

B0 succeeds when it proves a useful, controllable, restartable, power-off-capable utility aircraft architecture.

Only then create:

1. production massing candidates;
2. intact GB-1A exterior;
3. service variants;
4. crash grammar;
5. Bootstrap Province wreck realizations.
