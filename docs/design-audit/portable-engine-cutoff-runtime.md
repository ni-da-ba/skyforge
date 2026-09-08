# Portable Engine redstone cutoff — current-main runtime recomposition

**Status:** STATIONARY PROOF ACCEPTED MERGE CANDIDATE  
**Issue:** #237  
**Historical draft:** PR #240, closed unmerged as reserved historical work  
**Dependency:** accepted C11 / PR #386 flight-only runtime  
**Downstream:** C12 / issue #239

## Purpose

Issue #237 needs an opt-in off switch for Simulated Portable Engines so powered-soaring aircraft can
shut down without wasting the already-consumed fuel item's remaining burn time.

Historical PR #240 proved the stationary concept but was closed by AUDIT dormant-branch cleanup as
reserved historical work, not rejected.

This branch recomposes only that bounded compatibility seam from current `main`.

## Current upstream target

The current retained Simulated 1.2.1 Portable Engine still:

- computes `isLit = burnTime > 0` at the start of its own tick;
- decrements an active finite `burnTime`;
- loads the next inventory fuel item only when `burnTime <= 0`;
- writes `generatedSpeed = isLit ? 32 : 0`;
- exposes public burn-time accessors and a public inventory field;
- exposes `PortableEngineBlock.useItemOn(...)` for its ordinary item interaction.

The mixins remain `@Pseudo` and name only the two Simulated Portable Engine classes. They do not
target generic Create kinetic classes.

## Compatibility contract

Per Portable Engine:

```text
ALWAYS_RUN (default)
    neighboring redstone does not alter upstream behavior

REDSTONE_CUTOFF (explicit opt-in)
    no signal -> RUN
    signal    -> CUT
```

While CUT:

- generated speed becomes zero through Simulated's own `isLit` path;
- current burn timer is preserved exactly;
- a queued fuel item is not consumed;
- no fuel is refunded or recreated.

When signal is removed:

- a cold engine consumes one queued fuel item using upstream ignition behavior;
- Simulated's ordinary one-tick cold-start delay is preserved;
- a warm engine resumes from the exact preserved timer.

## Implementation technique

The block-entity mixin temporarily masks the private `burnTime` with an infinite sentinel only
during the upstream tick, forces the audited first stored `isLit` local false, then restores the exact
timer at tick return.

The injection uses `require = 1` for that local. If upstream bytecode changes, compatibility should
fail loudly rather than silently burn fuel or produce power.

The opt-in flag is persisted in the Portable Engine block entity NBT.

Sneak-use of a Redstone Torch toggles the opt-in flag. The torch is not consumed. This is an initial
minimal UX only; issue #237's human ergonomics gate remains open.

## Runtime isolation improvement over historical PR #240

Historical PR #240 reused C9, which also carried CC:Tweaked and Create: Avionics.

The current recomposition uses **accepted C11 `waveC11Runtime`** instead:

```text
Create
Sable
Create Aeronautics / bundled Simulated
```

No computing/avionics dependency is needed to prove engine cutoff behavior.

## Stationary acceptance scope

The initial live proof requires:

1. unconfigured engine + redstone still burns normally and produces normal 32 RPM;
2. opted-in active engine + redstone preserves the exact burn timer and produces zero output;
3. a cut cold engine does not consume queued coal;
4. removing signal starts from the same queued fuel;
5. upstream cold-start one-tick delay is preserved;
6. a re-cut warm engine preserves the exact running timer;
7. removing signal resumes decrement and normal output.

## Not accepted by this stationary proof

Issue #237 must remain open after the initial stationary proof.

Still required:

- save/reload in RUN and CUT states;
- assembled Sable contraption behavior;
- two-engine together/independent cutoff behavior;
- comparator/display coherence under the added mode;
- human ergonomics / neighboring-redstone shutdown clarity.

C12 may consume the cutoff seam for engineering-mule work, but final powered-soaring/restart
acceptance must not be declared closed until the relevant mobile/persistence gates pass.

## Human gate

No human gate is required for the stationary compatibility proof.

A human ergonomics gate remains required before final #237 acceptance.


## Accepted stationary evidence

Exact code head `42aadf006e641168954076a6a8af72ce60d755f9` passed:

- Portable Engine Cutoff run `34190573454`;
- retained Wave C11 First Flight Recipe Runtime regression `34190573367`;
- repository CI run `34190573402`.

The live retained engine reported:

```text
defaultBurn=99
cutoffBurn=100
queuedFuelPreserved=2
ignitionBurn=1600
runningBurn=1599
resumedBurn=1598
runtime=C11_FLIGHT_ONLY
```

This accepts only the stationary compatibility seam. Issue #237 remains open for persistence,
assembled-Sable, two-engine, comparator/display, and human ergonomics gates.


## Persistence/comparator follow-on

The next #237 slice is intentionally a separate two-boot dedicated-server specimen rather than an
in-memory NBT unit test.

Prepare boot:

- place an opted-in Portable Engine;
- power the cutoff with a persisted redstone block;
- set exact active burn time to 600 ticks;
- queue one coal item;
- verify zero output and comparator level 12;
- force a world save.

Verify boot reopens the same disposable world and requires:

- the Portable Engine and redstone block still exist;
- cutoff opt-in flag is restored;
- cutoff is active;
- exact burn time remains 600;
- queued coal count remains one;
- generated output remains zero;
- comparator remains 12.

Then the verify boot removes the signal and ticks once:

- burn time becomes 599;
- output returns to normal 32 RPM;
- comparator falls coherently from 12 to 11.

This proof remains stationary. Assembled-Sable and two-engine behavior stay separate mobile gates.
