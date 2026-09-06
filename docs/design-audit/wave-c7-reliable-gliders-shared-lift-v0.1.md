# Wave C7 Reliable Gliders Shared Lift v0.1

**Snapshot:** 2026-09-06  
**Status:** Executable development compatibility slice.

## Purpose

C6 proved that Fowl Play's existing red-tailed hawk can consume the authoritative A4MC lift field.
C7 closes the corresponding player-facing loop:

```text
                 A4MC trusted updraft
                         │
              ┌──────────┴──────────┐
              ↓                     ↓
     Reliable Gliders player    Fowl Play hawk
          vertical lift          SOAR choice
```

The consumers interpret the same atmosphere differently, but atmospheric truth has one owner.

## Ordering

Reliable Gliders injects its native glider physics at the tail of `Player.tick()`.

NeoForge fires `EntityTickEvent.Post` after the completed entity tick.

C7 therefore observes the **final Reliable Gliders velocity** and may only raise its Y component when
trusted atmospheric lift implies a stronger target.

No Reliable Gliders mixin is patched or forked.

## Physical mapping

The retained C4 mapping is now executable:

```text
targetY = updraft_mps / 20 - 0.05
newY    = lerp(0.20, currentY, targetY) only when targetY > currentY
```

Examples:

| Updraft | Target |
|---:|---:|
| 1 m/s | 0.00 b/t |
| 2 m/s | +0.05 b/t |
| 4 m/s | +0.15 b/t |
| 15 m/s | +0.70 b/t |

A 4 m/s thermal applied to Reliable Gliders' stock `-0.05 b/t` sink yields `-0.01 b/t` after the
first smoothing step.

## Stronger-source-wins

C7 does not add natural lift on top of an already stronger Reliable Gliders block updraft.

If the completed native result is `+0.70 b/t` and the A4MC target is `+0.15 b/t`, C7 leaves the
native result unchanged.

This prevents a campfire/lava proxy plus natural A4MC thermal from becoming a double-counted rocket
column.

## Authority

Only trusted A4MC gameplay samples may modify server motion.

Untrusted, non-finite, zero, or negative atmospheric updraft is inert in this first vertical-only
adapter.

The adapter is server-authoritative. Client prediction remains deferred until measured correction
shows it is necessary.

## Optional-mod boundary

Production Skyforge can load with neither Reliable Gliders nor A4MC.

C7:

- checks NeoForge mod IDs first;
- binds both optional public APIs reflectively after server start;
- keeps optional jars on an isolated development run source set;
- fails closed to stock Reliable Gliders behavior if reflection later becomes incompatible.

## Headless acceptance

NeoForge's `FakePlayerFactory` plus public `EntityTickEvent.Post` make a client unnecessary.

The deterministic acceptance:

1. obtains the exact registered `reliable_gliders:glider`;
2. equips an airborne/falling NeoForge FakePlayer;
3. asks Reliable Gliders' own public `GlidingState.isGliding(Player)` to admit the state;
4. passes that player through the actual C7 post-tick handler;
5. injects a trusted 4 m/s thermal and verifies `-0.05 -> -0.01 b/t`;
6. verifies an existing `+0.70 b/t` native updraft is preserved;
7. verifies untrusted atmosphere changes nothing;
8. removes the glider and verifies trusted lift changes nothing.

No human-eye test is required for those correctness claims.

## Deferred

C7 does not yet implement:

- horizontal wind advection;
- sink polar / airspeed model;
- downdraft penalties;
- turbulence control disturbance;
- ridge lift;
- client prediction;
- thermal visualization;
- final tuning of smoothing.

## Acceptance principle

> Reliable Gliders owns glider mechanics. A4MC owns the air. Skyforge only translates trusted lift
> into the smallest post-native adjustment needed for both systems to describe the same world.
