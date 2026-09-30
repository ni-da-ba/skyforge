# General Locomotion and Physical Actuation Stack v0.1

**Snapshot:** 2026-09-29  
**Status:** Provisional strong selection / integration contract. Exact recipes, balance, tuning, licensing/distribution verification, and runtime compatibility remain acceptance work.

## Decision

Skyforge should give ground vehicles and general moving machinery the same engineering depth as aircraft, weapons, and industry.

The locomotion grammar should be physical and subsystem-driven rather than a sequence of abstract vehicle tiers.

~~~text
SABLE
  -> common rigid-body / sublevel physics

AERONAUTICS SIMULATED
  -> aircraft / moving-structure assembly
  -> bearings / aerodynamic integration

AERONAUTICS OFFROAD
  -> baseline wheels / simple ground vehicles

NO HORIZON
  -> heavy wheels / tracks / suspension / oleo landing gear

TRANSMISSION & LINKAGE
  -> hinges / universal joints / hydraulics / articulated machinery

LINEAR BEARING
  -> constrained translational motion

CREATE TRAINS + STEAM 'N' RAILS
  -> fixed-route rail locomotion / heavy freight
~~~

## Selected stack

| Capability | Preferred system | Disposition |
|---|---|---|
| Common rigid-body physics | Sable | Core |
| General moving structures / aviation | Create Aeronautics / Simulated | Core |
| Baseline wheeled locomotion | Aeronautics Offroad | Core |
| Heavy ground running gear / tracks / oleos | Aeronautics: No Horizon | Strong provisional primary |
| Articulated joints / hydraulics / power linkage | Create Aeronautics: Transmission & Linkage | Strong provisional |
| Constrained linear motion | Create Linear Bearing | Strong provisional |
| Rail locomotion | Create trains | Core |
| Expanded rail infrastructure | Steam 'n' Rails | Strong provisional |
| Alternative track/suspension package | Tracks+ | Fallback / A-B |
| Original Create: Tracks | Superseded if No Horizon or Tracks+ selected | Do not stack by default |
| Wheel speed/traction extension | Aeronautics: Speed Wheels | Reserve |

## Ground-vehicle design doctrine

Ground vehicles should gain capability by combining subsystems rather than by crafting stronger vehicle tiers.

Example progression:

~~~text
EARLY VEHICLE
  simple chassis
  + baseline wheels
  + direct mechanical drive
  + simple steering

MATURE ROAD VEHICLE
  diesel / electric-mechanical power
  + tuned suspension
  + larger running gear
  + electrical distribution
  + instrumentation / controls

HEAVY CRAWLER
  heavy structure
  + tracked running gear
  + tuned damping / grip
  + high-torque power
  + hydraulic implements

ADVANCED EXPEDITION / SERVICE VEHICLE
  mature running gear
  + CC / sensors
  + articulated mechanisms
  + onboard electrical system
  + remote / assisted control
~~~

Do not create a Wood Car -> Iron Car -> Steel Car tier ladder.

## No Horizon role

No Horizon is the preferred heavy-ground extension because its current Trackwork-derived layer provides a broader physical running-gear vocabulary than a track-only addon.

Target retained roles:

- tracked running gear;
- suspension tracks;
- multiple wheel sizes;
- oleo landing gear;
- suspension controllers;
- mass differentiation across running-gear sizes;
- powered drivetrain behavior;
- bearing-mounted wheels/tracks/oleos where supported.

No Horizon's broader/junk-drawer scope is a governance risk. Only accepted locomotion/mechanical features should become progression-authoritative.

Tracks+ remains the fallback if No Horizon proves unstable, too broad, unsuitable for distribution, or inferior in runtime handling.

## Transmission & Linkage role

Transmission & Linkage is the preferred articulated-machine layer.

Target retained verbs:

- HINGE;
- UNIVERSAL JOINT;
- EXTEND / RETRACT;
- DAMP;
- RETURN;
- LIMIT ANGLE;
- TRANSMIT MOTION;
- CONVERT PHYSICS MOTION TO CREATE KINETICS.

Target applications:

- suspension linkages;
- steering mechanisms;
- cranes;
- excavators;
- retractable landing gear;
- folding wings;
- ramps;
- stabilizer outriggers;
- articulated trucks;
- deployable machinery;
- robotic / industrial mechanisms.

## Linear Bearing role

Create Linear Bearing provides translational actuation distinct from ordinary rotary bearings.

Target applications:

- landing-gear extension;
- cargo doors / ramps;
- elevators;
- cranes;
- telescoping mechanisms;
- deployable structures;
- vehicle bays;
- moving ballast;
- selected machinery / mounts.

It should use the existing Create/Sable mechanical and control vocabulary rather than define another energy system.

## Rail boundary

Create trains + Steam 'n' Rails remain the preferred fixed-route high-throughput locomotion system.

Rail should win where:

- infrastructure can be built;
- cargo is repetitive and heavy;
- routes are stable;
- high throughput matters.

Ground vehicles should win where:

- routes are flexible;
- construction is temporary;
- last-mile movement matters;
- terrain or dispersed worksites make rail impractical.

Aircraft should win where discontinuous sky geography makes surface connection impossible or urgency/flexibility outweighs transport cost.

## Physics / balance doctrine

Running gear and articulation should preserve physical consequences.

Desired behavior includes:

- vehicle mass affecting acceleration and suspension;
- center of mass affecting stability;
- high drive force causing traction or rollover problems rather than becoming free speed;
- spring stiffness and damping affecting ride/control;
- track/wheel size affecting geometry and load;
- articulated mechanisms carrying real mass/inertia;
- landing gear behaving as physical support rather than cosmetic blocks;
- drivetrain performance paying mechanical/chemical/electrical costs.

Avoid abstract speed-tier blocks that bypass the vehicle's physical design.

## Deferred compatibility / acceptance work

### LOC-COMP-1 — No Horizon vs Tracks+ A/B
Test representative:
- light tracked utility vehicle;
- heavy tracked vehicle;
- wheeled truck;
- aircraft oleo landing gear;
- bearing-mounted running gear.

Compare:
- handling;
- obstacle negotiation;
- suspension tuning;
- persistence;
- multiplayer;
- Sable sublevel behavior;
- performance;
- failure modes;
- dependency burden.

### LOC-COMP-2 — articulation
Test:
- hinge;
- universal joint;
- hydraulic actuator;
- linear bearing;
- multi-joint crane;
- retractable gear;
- folding structure.

Record:
- force limits;
- damping;
- collision behavior;
- save/reload;
- assembly/disassembly;
- nested/moving Sable behavior.

### LOC-COMP-3 — controls
Verify integration with:
- Create redstone;
- Drive By Wire;
- Tweaked Controllers;
- CC:Tweaked / Avionics where applicable.

### LOC-COMP-4 — power
Verify running gear and actuators remain coherent with:
- direct Create kinetics;
- diesel mechanical power;
- CC&A motorization;
- onboard generation.

### LOC-COMP-5 — recipes
Normalize all locomotion recipes onto retained materials and processes. Do not import a parallel ore/material tree for wheels, tracks, joints, or suspension.

### LOC-COMP-6 — performance
Characterize:
- many-wheel vehicles;
- long tracks;
- articulated machines;
- multiple moving vehicles;
- nested bearings / joints;
- multiplayer operation.

## Acceptance boundary

This document establishes the preferred locomotion and physical-actuation architecture.

Production lock still requires:
1. exact 1.21.1 dependency/version/license/distribution closure;
2. No Horizon vs Tracks+ runtime A/B;
3. joint / bearing persistence tests;
4. power/control integration;
5. multiplayer verification;
6. performance characterization;
7. recipe/balance normalization;
8. human play validation.

## Design principle

> Skyforge vehicles should be engineered machines. Their mobility should emerge from power, running gear, suspension, joints, mass, controls, and terrain—not from a generic vehicle tier or a neglected secondary subsystem.
