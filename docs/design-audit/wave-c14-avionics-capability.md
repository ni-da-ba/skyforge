# Wave C14 — executable avionics capability specimen

**Status:** MERGED / ACCEPTED — PR #256, merge `b4b44c87509ee70b27ddbe20468d2d287cbd79f1`  
**Issue:** #253  
**Parent computing contract:** #227  
**Parent vertical slice:** #224

## Purpose

C9 established loader/runtime coexistence for the exact retained stack:

- Create;
- Sable;
- Create Aeronautics / Simulated;
- CC:Tweaked 1.119.0;
- Create: Avionics 0.5.2.

C14 asks the next narrower question: does that existing stack already provide enough real
instrumentation and bounded control that Skyforge should continue to avoid a bespoke computer or
aircraft-control API?

## A/B specimen

C14 uses two isolated server profiles.

### Flight-only baseline

Loads the exact C9 flight substrate:

```text
Create
Sable
Create Aeronautics / Simulated
```

CC:Tweaked and Create: Avionics are deliberately absent. Reaching dedicated-server ready state proves
the retained aircraft substrate does not depend on the optional computing layer.

### Computing capability

Loads the exact accepted C9 stack and enables one Skyforge acceptance fixture.

The fixture places two real CC computers at different world altitudes. Each computer is adjacent to:

- a real Simulated altitude sensor;
- a real Simulated throttle lever.

Skyforge writes a normal `startup.lua` file and boots the actual CC computer. The Lua program uses
CraftOS's ordinary `peripheral.find` surface to discover the upstream Create: Avionics peripherals.

It then:

1. reads `altitude_sensor.getHeight()`;
2. proves two otherwise-identical fixtures separated by 40 blocks report a 40-block altitude delta;
3. reads the throttle's initial state;
4. writes signal 9;
5. writes -4 and requires upstream clamping to 0;
6. writes 99 and requires upstream clamping to 15;
7. reads the final physical redstone signal from Minecraft outside CC and requires 15.

The acceptance code has no compile-time import of CC:Tweaked, Create: Avionics, or Simulated classes.
Reflection is used only to start the already-registered real computer after its filesystem has been
seeded; peripheral discovery and method invocation happen inside the actual CC Lua runtime.

## Acceptance boundary

A passing C14 justifies:

- KEEP CC:Tweaked + Create: Avionics as the baseline programmable avionics substrate;
- KEEP computing optional for manual flight and first-flight progression;
- RESERVE thin Skyforge peripherals for genuinely Skyforge-owned semantics not exposed upstream.

It does **not** accept mature autopilot, turtle resource throughput, wireless/GPS balance, route/fleet
automation UX, or a permanent optional-mod version lock. Those remain separate #227/#224 gameplay
audits.

## Verification

Required before merge:

```text
WaveC9ComputingResourceTest
WaveC14AvionicsCapabilityResourceTest
waveC14ResolvePinnedMods
Wave C14 flight-only baseline server
Wave C14 real-computer capability server
Wave C9 computing regression
repository CI
```

The exact runtime PASS marker is emitted only after both real computers complete their Lua programs
and Minecraft confirms the bounded throttle state physically.


## Accepted evidence

Exact synchronized runtime head: `dda6d1c6a98034c12931bcdcfcaf9fe70a9d03dd`.

```text
Wave C14 classpath A/B PASS
flight-only baseline: dedicated-server ready without CC/Avionics
lowHeight=80.5
highHeight=120.5
altitudeDelta=40.0
throttle=0->9->0->15
physicalLow=15
physicalHigh=15
```

The same head passed Wave C9, C2/C3/C5/C6/C7/C10/C13 regressions, showcase acceptance,
SF-IMP performance characterization, and repository CI before merge.
