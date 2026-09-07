# Portable Engine Redstone Cutoff Compatibility Contract v0.1

**Snapshot:** 2026-09-06  
**Issue:** #237  
**Status:** Proposed thin compatibility patch; implementation pending.

## Problem

Simulated Portable Engines are excellent compact mobile power sources, but current upstream behavior continuously decrements the current fuel burn timer while fuel is active.

A clutch or disconnected downstream load can stop useful mechanical work without conserving the already-active burn timer.

This creates a recurring frustration on mobile contraptions, including existing airships, and directly conflicts with the intended powered-soaring behavior of the GB-1A Giuseppe Bellanca.

## Verified upstream behavior

Current Portable Engine behavior includes:

- one internal fuel inventory with stack capacity 64;
- normal furnace-fuel acceptance;
- automated fuel insertion support;
- 32 RPM normal generator output;
- superheat support;
- comparator/display exposure of remaining burn time;
- persistent `burnTime` state.

While `burnTime > 0`, the timer is decremented each tick unless the fuel is treated as infinite.

No native fuel-pause/ignition cutoff was found in the audited source.

## Proposed behavior

Add one opt-in control mode to **Portable Engines only**:

~~~text
ALWAYS_RUN
    default
    upstream behavior preserved

REDSTONE_CUTOFF
    no cutoff signal -> RUN
    cutoff signal    -> CUT
~~~

### RUN

- normal upstream burn countdown;
- normal generator output;
- normal stress/RPM;
- normal superheat behavior.

### CUT

- generated kinetic speed becomes zero;
- active burn timer is frozen;
- inventory remains unchanged;
- no partially burned fuel is recreated/refunded;
- no new fuel efficiency is created while the engine is doing useful work.

Restart continues the same remaining burn timer.

## Scope boundary

Do not modify:

- generic Create kinetic classes;
- Steam Engines;
- Blaze Burners;
- furnaces;
- Create Crafts & Additions electrical machinery;
- Diesel Generators;
- generic fuel-time calculation;
- recipes;
- stress capacity;
- normal RPM;
- automatic load sensing.

This is an ignition/fuel-cutoff behavior for one mobile-engine block, not a global power-model change.

## Why this does not erase later progression

Portable Engines remain:

- low-output;
- manually fuelled;
- compact;
- suited to mobile/field machinery.

Later systems retain distinct advantages:

- Steam/large Create systems: large stationary mechanical power;
- petroleum: sustained heavy propulsion, fluid fuel logistics, longer operational range, larger aircraft/airships;
- electricity: distribution, storage, conversion, instrumentation, automation;
- mature Brass/control systems: coordinated logistics and control.

The patch rewards intentional shutdown rather than increasing work obtained per unit fuel while RUN.

## Compatibility strategy

Default mode must be **ALWAYS_RUN** so existing builds preserve upstream behavior.

A Portable Engine only participates in cutoff behavior after explicit configuration.

Configuration UX should be minimal, likely wrench/value-box interaction or another existing Create-style setting.

Avoid making arbitrary neighboring redstone shut existing engines down.

## Bellanca use

The GB-1A can configure both engines for cutoff and provide:

~~~text
ENGINE MASTER
    -> LEFT ENGINE CUTOFF
    -> RIGHT ENGINE CUTOFF
~~~

Later variants may support individual engine controls.

Expected flight pattern:

~~~text
takeoff -> climb -> cut -> glide/soar -> restart -> climb/divert
~~~

## Acceptance

1. Unconfigured engine is behaviorally identical to upstream.
2. RUN consumes fuel normally.
3. CUT outputs zero kinetic power.
4. CUT preserves exact current burn timer.
5. RUN resumes from that exact timer.
6. Repeated toggles neither duplicate nor lose fuel.
7. Works on assembled Sable contraptions.
8. Works with two engines sharing an aircraft kinetic network.
9. Save/reload preserves mode and cutoff state safely.
10. Comparator/display reporting remains coherent.
11. Superheat behavior remains deterministic.
12. Human play: shutdown is understandable and does not create surprising redstone interactions.

## Non-goal

Do not implement automatic load-following fuel conservation.

~~~text
BAD
machine idle -> engine magically pauses

GOOD
player/control system explicitly CUTS engine -> engine pauses
~~~

The distinction preserves the economic identity of later power systems.
