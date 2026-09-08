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

Still required after the stationary proof:

- save/reload in RUN and CUT states;
- assembled Sable contraption behavior;
- two-engine together/independent cutoff behavior;
- comparator/display coherence under the added mode;
- human ergonomics / neighboring-redstone shutdown clarity.

PR #392 closes the save/reload and comparator items below; assembled Sable, two-engine behavior, and
human ergonomics remain open.

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


## Accepted persistence/comparator evidence

The accepted #237 persistence specimen is a **three-boot dedicated-server proof** against one
disposable world, not an in-memory NBT round-trip.

Boot A saves configured CUT:

- burn=600;
- one queued coal;
- comparator=12;
- zero generated output;
- neighboring redstone persisted;
- cutoff mode configured and active.

Boot B reopens that exact CUT state, then removes the signal and executes one real engine tick:

- burn 600 -> 599;
- output returns to normal 32 RPM;
- comparator 12 -> 11;
- cutoff mode remains configured but becomes inactive;
- that configured RUN state is then explicitly saved.

Boot C reopens configured RUN:

- burn=599;
- one queued coal;
- comparator=11;
- cutoff mode still configured but inactive;
- normal 32 RPM output present;
- one further real tick continues burn 599 -> 598.

Exact strengthened code head `d910694922fe5f8480a4f4bb4d7b390f324a7bde` passed:

- Portable Engine Cutoff Persistence run `34193038291`;
- stationary Portable Engine Cutoff regression `34193038285`;
- retained C11 recipe regression `34193038301`;
- repository CI run `34193038286`.

Live markers:

```text
PREPARE PASS burn=600 fuel=1 comparator=12 active=true
CUT VERIFY PASS burn=600->599 fuel=1 comparator=12->11 persistedMode=true runSaved=true
RUN VERIFY PASS burn=599->598 fuel=1 comparator=11 persistedMode=true active=false
```

This accepts save/reopen in both CUT and configured-RUN states plus comparator coherence. The next
#237 gate is actual Sable sublevel assembly with two Portable Engines and together/independent
cutoff/restart behavior. Human ergonomics remains the final manual gate.


## Assembled Sable two-engine gate

The next #237 machine gate uses Simulated's own `SimAssemblyHelper.assembleFromSingleBlock` to move
an ordinary retained structure into a real Sable server sublevel.

Specimen:

- two `simulated:red_portable_engine` block entities;
- one `create:shaft` between their inward-facing shaft faces, so both engines share one aircraft
  kinetic line;
- a vanilla slime attachment floor so assembly uses normal NeoForge block stickiness;
- one redstone control block adjacent to each engine, carried into the same Sable sublevel.

The acceptance must prove, at the relocated sublevel block entities:

1. both cutoff opt-in flags and exact pre-assembly burn counters survive assembly;
2. both relocated engines and both control positions resolve to the exact returned Sable sublevel;
3. together CUT freezes both counters and produces zero output;
4. together RUN resumes exact counters and normal 32-RPM output;
5. cutting only engine A freezes A while engine B continues burning/generating;
6. restarting A resumes its exact counter without disturbing B;
7. the same independent behavior is then exercised with engine B;
8. a final together CUT/restart remains repeatable.

This is a headless compatibility gate, not the human ergonomics/flight-feel gate.


## Accepted assembled-Sable/two-engine evidence

Exact synchronized code head `d041eeee96bee538ac17a6027b6fd636fab3eb40` passed:

- Portable Engine Cutoff Sable run `34215553782`;
- Portable Engine Cutoff Persistence regression `34215553812`;
- stationary Portable Engine Cutoff regression `34215553718`;
- retained C11 recipe regression `34215553709`;
- repository CI run `34215553710`.

The live mobile marker was:

```text
PORTABLE_ENGINE_CUTOFF_SABLE_ACCEPTANCE PASS
subLevel=ServerSubLevel
virtualA=false
virtualB=false
startA=1000
startB=1200
finalA=996
finalB=1196
sharedShaft=true
independentA=true
independentB=true
together=true
```

This uses Simulated's real assembly path and relocated block entities in the exact returned Sable
server sublevel. It accepts issue #237 machine items #6 and #7 and, together with PRs #389/#392,
completes machine acceptance items #1-#9.

## Remaining human gate

Issue #237 stays open for acceptance item #10 only.

The owner/play check should answer:

1. Is sneak-use with a Redstone Torch an understandable, intentional way to opt an engine into
   redstone cutoff?
2. Is the enabled/disabled feedback clear enough during aircraft setup?
3. Once configured, is it obvious which redstone signal is commanding CUT versus ordinary nearby
   aircraft circuitry?
4. Can normal neighboring redstone arrangements create surprising engine shutdown in realistic
   aircraft packaging?
5. Does together/independent shutdown/restart feel predictable enough for power-off glide/restart?

This is a control-ergonomics gate. More stationary/mobile machine permutations do not substitute for
it unless play reveals a specific compatibility defect.
