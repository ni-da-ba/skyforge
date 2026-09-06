# Wave C6 Hawk Thermal Compatibility v0.1

**Snapshot:** 2026-09-05  
**Status:** Executable development compatibility slice; runtime acceptance in progress.

## Scope

Wave C6 implements the smallest non-forking bridge implied by Waves C4-C5:

```text
A4MC trusted updraft
    ↓
pure hysteresis policy
    ↓
Fowl Play hawk schedule substitution
    ↓
ordinary Fowl Play flight navigation
```

The bridge is activated only by `skyforge.dev.waveC6SoaringFauna=true`. Production Skyforge
remains inert.

## Implementation

### No hard optional dependencies

No Fowl Play, SmartBrainLib or A4MC type appears in a public/production class signature.

The development controller checks NeoForge's loaded mod list and initializes reflection handles only
when both `fowlplay` and `aerodynamics4mc` are present.

### Brain adaptation

For entity id `fowlplay:hawk`:

1. obtain the existing vanilla `Brain`;
2. verify its concrete implementation is SmartBrainLib `SmartBrain`;
3. remember the existing stock raptor schedule;
4. add one empty Fowl Play `SOAR` activity marker through `BrainUtils.addActivity`;
5. construct the thermal raptor schedule from the same IDLE/SOAR/REST windows documented in C5;
6. switch schedules only on soaring-state transitions.

No entity replacement, subclass or Fowl Play mixin is used.

### Hysteresis

```text
ENTER >= 1.50 m/s trusted updraft
EXIT  <= 0.75 m/s trusted updraft
MIN HOLD = 100 ticks
UNTRUSTED = immediate fail-closed exit
```

### Route planner

While the thermal schedule is active *and* the day is in one of the stock raptor HUNT windows:

- sample current location plus four cardinal points at 12-block radius;
- choose the strongest trusted updraft;
- generate a moving 10-block-radius orbital target;
- call Fowl Play's public `startFlying()` reflectively;
- hand the target to the hawk's ordinary Minecraft/Fowl Play `PathNavigation`.

The planner does not apply aerodynamic forces and does not create another atmosphere field.

### Sampling budget

```text
admission sample: 20 ticks
target refresh:   40 ticks
neighborhood:     center + 4 cardinal candidates
```

This is intentionally bounded for the first implementation. A later shared cell cache can replace
the repeated A4MC calls without changing consumer semantics.

## Automated policy proof

Unit tests cover:

- trusted-only entry;
- 1.50 m/s enter threshold;
- 0.75 m/s exit threshold;
- 100-tick minimum hold;
- immediate exit on loss of authority;
- exact two stock raptor HUNT windows including day wrap.

## Runtime proof

The Wave C6 CI profile must:

1. compile/test without optional mods on the ordinary test classpath;
2. resolve the already-pinned C5 bird stack;
3. launch the combined Fowl Play + SBL + YACL + A4MC dedicated server;
4. prove the reflection bridge initialized against those exact runtime APIs;
5. reach Minecraft server-ready state.

The next acceptance increment after this loader/API proof is a self-spawned hawk specimen that
records successful brain adaptation and schedule transitions under injected test lift samples.

## Non-decisions

This pass does not lock:

- final thermal thresholds;
- final orbit radius;
- final search radius;
- final sampling cadence;
- final shared-lift cache geometry;
- client visual prediction;
- whether every hawk should thermally soar at the same propensity.

## Acceptance principle

> Make the existing bird choose the existing flight system differently when the authoritative
> atmosphere makes soaring worthwhile. Do not turn a compatibility feature into a second bird or a
> second physics engine.
