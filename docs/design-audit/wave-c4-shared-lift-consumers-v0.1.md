# Wave C4 Shared Lift Consumers v0.1

**Snapshot:** 2026-09-05  
**Status:** Working integration contract; implementation/runtime acceptance pending.

## Purpose

Wave C3 established a viable atmosphere-authority candidate and proved that its core runtime,
Create Aeronautics compatibility jar, and Wind Tunnel measurement stack all reach dedicated-server
ready state.

Wave C4 defines the first **consumer-side** integration:

```text
Aerodynamics4MC authoritative gameplay atmosphere
        ↓
shared lift view
        ├── Reliable Gliders
        └── soaring fauna
```

The objective is not to write another wind model.

The objective is:

> one trusted lift opportunity should mean the same thing to aircraft, a gliding player, and a
> soaring animal.

This tranche deliberately begins with **vertical lift/updraft**. Full horizontal-wind glider
aerodynamics, turbulence forcing, ridge-lift derivation, and downdraft penalties remain separate
follow-on work.

---

## 1. Source-backed seam facts

### Aerodynamics4MC

The accepted C3 candidate exposes a server gameplay sample with:

```text
effective wind
updraft m/s
turbulence
shear
shelter
confidence
authority
epoch/provenance
```

`GameplayWindSample.updraftMetersPerSecond` is derived from the clamped vertical effective wind.

Its gameplay sample identifies whether it is trusted:

```text
SERVER_AUTHORITATIVE
SERVER_AGGREGATED
    -> trusted for gameplay
```

The Minecraft-facing API also supplies direct `ServerLevel` sampling, so Skyforge does not need to
construct a parallel world/position adapter.

### Reliable Gliders

Audited source head:

```text
eb65dfe2159ffb850c631f998cd2149f7383bd47
```

Reliable Gliders currently has no ambient-atmosphere extension API.

Its player physics are injected at the tail of `Player.tick()` and directly write delta movement.

Baseline vertical behavior is:

```text
ordinary glide
    Y >= -0.05 blocks/tick

tagged block updraft
    target = updraft_strength
    default target = +0.7 blocks/tick
    smoothed with lerp factor 0.2
```

The stock tagged sources remain the C2 local-heat bootstrap:

```text
fire
campfire
lava
magma
```

### NeoForge ordering seam

NeoForge's `PlayerTickEvent.Post` is fired from inside `Player.tick()`.

That is **too early** for this compatibility layer because Reliable Gliders' tail injection runs
after the body of `Player.tick()`.

NeoForge also fires `EntityTickEvent.Post` outside the completed entity tick.

Therefore the leading compatibility seam is:

```text
Player.tick
    ↓
NeoForge PlayerTickEvent.Post
    ↓
Reliable Gliders @TAIL velocity update
    ↓
Player.tick returns
    ↓
NeoForge EntityTickEvent.Post
    ↓
Skyforge shared-lift compatibility adjustment
```

This avoids:

- forking Reliable Gliders;
- injecting into another mod's mixin;
- fragile bytecode targeting of `setDeltaMovement`;
- competing before Reliable Gliders has finished its own physics.

---

## 2. Authority rule

Only trusted A4MC gameplay lift may affect authoritative player motion.

Server rule:

```text
if not A4MC available
    -> no atmospheric adjustment

if sample !isTrustedForGameplay()
    -> no atmospheric adjustment

if player is not Reliable-Gliders gliding
    -> no atmospheric adjustment

otherwise
    -> evaluate shared lift
```

Missing/low-confidence atmosphere must degrade to the accepted C2 glider behavior, not invent a
fallback wind field.

Skyforge must not cache a second independently evolving atmosphere state.

A thin facade may cache sampled values for efficiency, but the cache is explicitly a view of A4MC
truth and retains source epoch/authority metadata.

---

## 3. Unit bridge: m/s to Minecraft motion

For player-scale movement, use the ordinary interpretation:

```text
1 block ~= 1 meter
20 ticks = 1 second
```

Therefore:

```text
vertical_air_blocks_per_tick = updraft_mps / 20
```

Reliable Gliders' accepted ordinary sink cap is:

```text
0.05 blocks/tick
    ~= 1.0 m/s sink
```

This gives a physically legible first shared-lift mapping:

```text
thermal_ground_speed_target
    = updraft_mps / 20
    - 0.05
```

Examples:

| A4MC updraft | Glider target |
|---:|---:|
| 0 m/s | -0.05 blocks/tick |
| 1 m/s | 0.00 blocks/tick |
| 2 m/s | +0.05 blocks/tick |
| 4 m/s | +0.15 blocks/tick |
| 8 m/s | +0.35 blocks/tick |
| 15 m/s | +0.70 blocks/tick |

This has a useful emergent alignment:

- a ~1 m/s thermal approximately cancels the glider's baseline sink;
- stronger thermals produce progressively useful climb;
- A4MC's current 15 m/s effective-flow gameplay cap maps to roughly +0.70 blocks/tick after sink,
  essentially the same vertical scale as Reliable Gliders' stock block-updraft target.

No arbitrary "Skyforge thermal power" scalar is required for the first prototype.

---

## 4. First glider-coupling rule

Wave C4 should begin with **positive shared lift only**.

It should not yet make Reliable Gliders a complete horizontal-wind aerodynamic model.

At `EntityTickEvent.Post`:

```text
currentY = final Reliable-Gliders Y velocity
thermalTarget = updraft_mps / 20 - 0.05

if thermalTarget <= currentY
    -> preserve currentY

if thermalTarget > currentY
    -> approach thermalTarget with a small smoothing factor
```

Leading smoothing prototype:

```text
newY = lerp(0.2, currentY, thermalTarget)
```

The 0.2 value intentionally mirrors Reliable Gliders' existing block-updraft interpolation.

This does **not** mean the value is permanently locked. It gives the first implementation a
source-consistent behavior to test rather than a second arbitrary tuning language.

### Why positive-only first

A full atmospheric glider model would eventually need to reason about:

- horizontal advection;
- sideslip;
- airspeed versus ground speed;
- sink polar;
- downdrafts;
- turbulence;
- gust response;
- stall/control behavior.

Reliable Gliders does not currently model those systems.

Adding only externally sourced **lift opportunities** is a narrow compatibility feature.

Trying to bolt full wind dynamics onto the same simple velocity model in Wave C4 would turn a clean
adapter into an accidental second flight simulator.

---

## 5. Local heat and natural lift composition

Do not sum block updraft and atmospheric updraft blindly.

Because the C4 adjustment runs after Reliable Gliders:

```text
Reliable block heat
    -> may already raise currentY

A4MC thermal target
    -> only raises Y if its target exceeds currentY
```

This naturally behaves like a **stronger-source-wins** composition in the first prototype.

It avoids:

```text
campfire updraft
+ A4MC updraft
= double-counted rocket column
```

while still allowing an atmospheric thermal to matter when the local block source is weak or absent.

Long-term, anthropogenic heat may be fed into A4MC as an environmental forcing term. If that happens,
the Reliable Gliders block-tag proxy can be reduced or removed after migration so one field owns the
full thermal result.

Until then:

> block-tag updraft = local-heat bootstrap; A4MC updraft = natural/authoritative atmosphere.

---

## 6. Client prediction

Server motion remains authoritative.

The first implementation must not use client-local L2 atmosphere as gameplay truth.

If server-only lift produces visible correction/jitter, add a prediction path that samples only
server-derived/aggregated atmosphere on the client.

Allowed prediction source:

```text
SERVER_AGGREGATED_PREFERRED
```

Disallowed for authoritative motion:

```text
CLIENT_LOCAL_PREFERRED
VISUAL_LOCAL_FIRST
DIAGNOSTIC_ALL_SOURCES
```

A client predictor may improve presentation but may not alter the server acceptance result.

Measure correction frequency/magnitude before adding prediction complexity.

---

## 7. Shared atmosphere facade

Consumers should not each learn A4MC internals independently.

A thin Skyforge-facing view is justified after C3 authority acceptance.

Conceptual record:

```text
SkyforgeLiftSample
    verticalAirMetersPerSecond
    turbulence
    shear
    shelter
    confidence
    trusted
    sourceLevel
    authority
    l1Epoch
    worldDeltaEpoch
    l2Epoch
```

The facade:

- delegates to A4MC;
- performs no independent weather evolution;
- preserves authority/provenance;
- may spatially/temporally cache identical queries;
- exposes enough information for both gameplay and ecology;
- returns an explicit unavailable/untrusted result rather than fabricated calm wind.

Do not add a generic "weather manager" beside A4MC.

---

## 8. Soaring-fauna consumer

The same lift sample should support soaring behavior.

The fauna system owns:

- whether a soaring species exists in the habitat;
- population density;
- hunger/resting/nesting;
- route intent;
- avoidance/threat behavior.

The atmosphere owns:

- whether useful lift exists at a location;
- how strong that lift is;
- turbulence/shear/shelter context.

Therefore:

```text
ecology admits soaring bird
        +
shared lift sample reports useful climb
        ↓
bird may enter SOAR / THERMAL-CIRCLE behavior
```

A thermal must **not spawn birds by itself**.

That would make weather a second population authority.

### Player-readable consequence

An adapted red-tailed hawk or other retained soaring bird circling and gaining altitude can become a
real environmental instrument:

```text
bird circles/climbs
    -> player infers lift
    -> glider can exploit same sampled opportunity
```

The cue is truthful because both consumers read the same atmospheric source.

---

## 9. Population/performance rule

Do not sample the full atmosphere independently for every soaring entity every tick.

Preferred hierarchy:

```text
shared lift cache / query cell
    ↓
multiple fauna consumers
    ↓
low-frequency route/behavior decision

player glider
    ↓
high-frequency local query only while actively gliding
```

Candidate fauna sampling cadence can be much lower than 20 Hz.

The exact cell size/cadence remains a performance-test value, but the architecture should make
query reuse possible from the start.

---

## 10. Automated acceptance

Wave C4 should not require a human eye to establish correctness.

### Pure coupling cases

Given final Reliable-Gliders velocity `currentY` and a trusted sample:

```text
NO FLOW / UNTRUSTED
    unchanged

0 m/s
    no additional lift above accepted baseline

1 m/s
    neutral-lift target ~ 0.00 b/t

2 m/s
    target ~ +0.05 b/t

4 m/s
    target ~ +0.15 b/t

15 m/s
    target ~ +0.70 b/t

EXISTING BLOCK-UPDRAFT RESULT STRONGER THAN A4MC
    preserve existing result; do not add

A4MC RESULT STRONGER
    smooth upward toward atmospheric target
```

### Ordering proof

Instrument one gliding server player and record:

```text
velocity entering Player.tick
velocity after Reliable Gliders
velocity entering Skyforge EntityTickEvent.Post
velocity after Skyforge shared-lift adjustment
```

Acceptance requires the adapter to observe the Reliable-Gliders result, not race it.

### Authority proof

Feed or select:

```text
SERVER_AUTHORITATIVE sample
SERVER_AGGREGATED sample
untrusted / NONE sample
```

Only trusted gameplay samples may affect server velocity.

### Shared-consumer proof

At one world/time/position:

```text
glider lift query
fauna lift query
```

must report the same:

- vertical-air value;
- authority;
- confidence;
- source epoch.

The consumers may interpret the value differently, but they cannot disagree about atmospheric truth.

### Local heat composition proof

A block updraft and a natural thermal must not simply add their vertical targets.

### No-atmosphere regression

With A4MC absent or unavailable, C2 Reliable Gliders behavior must remain byte-for-byte/runtime
equivalent at the compatibility seam.

---

## 11. Human-eye role

Human observation is optional for **tuning**:

- whether circling birds communicate lift clearly;
- whether thermal climb feels satisfying;
- whether smoothing feels sluggish or abrupt;
- whether visual/sound cues are sufficient.

It is not needed to verify:

- event ordering;
- sample authority;
- unit conversion;
- lift target;
- stronger-source-wins composition;
- source provenance;
- no-atmosphere fallback.

Those are machine-testable.

---

## 12. Deferred work

Wave C4 intentionally does not yet implement:

- full horizontal wind drift for Reliable Gliders;
- glider angle-of-attack/stall physics;
- atmospheric downdraft penalties;
- turbulence-induced player control disturbance;
- ridge-lift derivation;
- anthropogenic heat injection into A4MC;
- final soaring-fauna species assets;
- final circling animation;
- weather presentation;
- A4MC-driven wind instruments.

Those should follow only after shared vertical lift is accepted.

---

## 13. Implementation order

Preferred sequence:

```text
1. pure lift-coupling function + unit tests
2. A4MC-backed SkyforgeLiftSample facade
3. Reliable-Gliders detection/compat seam
4. EntityTickEvent.Post server integration
5. automated ordering/authority acceptance
6. optional client prediction only if measured correction requires it
7. fauna consumer against the same facade
8. visual/behavior tuning
```

If step 3 requires a hard Reliable Gliders dependency before the mod is finally selected, use a
development-only reflection bridge first.

Do not fork Reliable Gliders merely to prove shared lift.

## Acceptance principle

> Skyforge does not create a second thermal system. It translates one trusted atmospheric updraft
> into consumer-specific behavior, after each consumer has completed its own native baseline logic.
