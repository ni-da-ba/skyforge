# Wave C5 Soaring Fauna Prototype v0.1

**Snapshot:** 2026-09-05  
**Status:** Reuse-first runtime and behavior contract; thermal behavior implementation pending.

## Purpose

Wave C5 decides whether Skyforge needs a bespoke thermal-soaring bird.

Current answer:

> **No bespoke hawk entity is justified.**

Fowl Play 1.2.3 for Minecraft 1.21.1 already provides the desired red-tailed-hawk asset,
flight/navigation substrate, raptor ecology behavior, and SmartBrainLib-backed activity system.

Skyforge should add only the missing **atmosphere-aware soaring behavior**.

---

## 1. Retained substrate

Pinned runtime specimen:

```text
Fowl Play       1.2.3+1.21.1-neoforge
SmartBrainLib   1.16.11
YACL            3.6.5+1.21.1-neoforge
A4MC core       reused from Wave C3
```

Fowl Play's current NeoForge 1.21.1 release is client/server capable and specifically includes a
dedicated-server crash fix.

The audited 1.21.1 source head is:

```text
22eb0dfe639f709a6e91972009911c47ed7f9e60
```

The exact SmartBrainLib 1.21 branch used by Fowl Play reports version 1.16.11 and source head:

```text
1e091c69477f49972d8f434a595a3eb7d115346b
```

---

## 2. Why the stock hawk is the right asset

Fowl Play's hawk already supplies:

- a raptor-specific entity;
- high-altitude flight preference;
- flight navigation;
- flight/glide animation state;
- hunting;
- avoidance;
- perching/resting;
- food/trust behavior;
- raptor day schedule;
- SmartBrainLib integration.

The project description and entity semantics treat this hawk as the red-tailed-hawk realization.

Therefore the earlier Skyforge placeholder:

```text
THERMAL_SOARER -> adapted red-tailed hawk
```

can become:

```text
THERMAL_SOARER -> Fowl Play hawk + thin Skyforge atmosphere behavior
```

No new model, texture, sounds, egg, loot table, breeding system, or entity registration is needed.

---

## 3. Existing SOAR seam

Fowl Play's common `BirdBrain` already registers a first-class `SOAR` activity and places it in
the normal activity-priority list.

Other Fowl Play birds already populate `soarActivity()`.

The hawk currently does not.

Its raptor schedule is:

```text
0      IDLE
1000   HUNT
6000   IDLE
8000   HUNT
11000  IDLE
13000  REST
23000  IDLE
```

So adding a SOAR activity alone is insufficient: the stock raptor schedule never selects it.

---

## 4. Post-construction activity injection is supported

SmartBrainLib 1.16.11 exposes:

```text
BrainUtils.addActivity(Brain, BrainActivityGroup)
SmartBrain.addActivity(BrainActivityGroup)
SmartBrain.setSchedule(SmartBrainSchedule)
```

This means Skyforge does not need to:

- subclass `HawkEntity`;
- replace Fowl Play's registered entity type;
- fork Fowl Play;
- inject into `HawkEntity.soarActivity()`;
- patch its bytecode merely to add an activity.

Leading setup hook:

```text
first server-side observation of Fowl Play hawk
    ↓
confirm brain instanceof SmartBrain
    ↓
install one Skyforge SOAR activity if not already installed
    ↓
retain stock raptor schedule until useful lift exists
```

The exact entity-lifecycle event can be chosen by implementation based on the earliest point at which
the brain is guaranteed initialized.

---

## 5. Thermal schedule switching

Do not globally replace normal hawk behavior with endless soaring.

Use two schedules.

### Stock

```text
RAPTOR
0 IDLE
1000 HUNT
6000 IDLE
8000 HUNT
11000 IDLE
13000 REST
23000 IDLE
```

### Thermal-capable raptor

```text
SKYFORGE_THERMAL_RAPTOR
0 IDLE
1000 SOAR
6000 IDLE
8000 SOAR
11000 IDLE
13000 REST
23000 IDLE
```

Runtime rule:

```text
useful trusted lift present
    -> SKYFORGE_THERMAL_RAPTOR

no useful trusted lift
    -> stock Fowl Play RAPTOR
```

This preserves:

- resting windows;
- idle windows;
- combat/avoidance interruptions;
- normal hunting whenever thermals are not worth exploiting.

Skyforge is not authoring a second circadian schedule. It is substituting a lower-energy raptor
movement mode into the same active windows when the atmosphere supports it.

---

## 6. Thermal admission

The atmosphere does not spawn hawks.

A hawk may consider thermal soaring only if:

1. the entity already exists under Skyforge/Fowl Play ecological population authority;
2. the hawk is not in a higher-priority emergency/interaction state;
3. the A4MC sample is trusted for gameplay;
4. lift exceeds the behavior threshold;
5. the opportunity remains useful long enough to avoid activity thrash.

Leading initial thresholds are deliberately conservative:

```text
ENTER_SOAR   >= 1.5 m/s updraft
EXIT_SOAR    <= 0.75 m/s updraft
MIN_HOLD     ~ several seconds
```

These are prototype hysteresis values, not canon.

The 1.5 m/s entry point is above the ~1 m/s neutral-lift point from Wave C4, so the bird should
prefer thermals that can produce actual climb rather than merely cancel sink.

---

## 7. Thermal-directed target behavior

Do not make SOAR equal random flight.

The first Skyforge SOAR behavior should:

1. query the shared A4MC lift view at low cadence;
2. search a small local neighborhood for stronger trusted lift;
3. choose a target around rather than directly through the lift maximum;
4. bias yaw tangentially to produce a broad orbit;
5. allow the existing Fowl Play flight navigation to execute the movement;
6. periodically re-sample and shift the orbit center as the thermal moves/changes;
7. abandon SOAR when lift falls below the exit threshold or a higher-priority behavior wins.

The target selector is a behavior planner, not a new flight physics engine.

Fowl Play continues to own movement/navigation mechanics.

---

## 8. Shared truth with player gliding

Wave C4 requires the hawk and the player glider to consume the same lift source.

For a shared world/time/position:

```text
glider query
hawk query
    ↓
same A4MC vertical-air value
same authority
same confidence
same source epochs
```

The consumers differ only in interpretation:

```text
player glider
    -> modifies vertical movement target

hawk
    -> selects SOAR activity and orbit target
```

This makes circling/climbing raptors a truthful environmental instrument.

---

## 9. Sampling and performance

Do not perform a wide A4MC neighborhood scan for every bird every tick.

Recommended first architecture:

```text
Skyforge shared lift cache
    cell/time bucket
        ↓
hawk behavior samples at low frequency
glider samples locally at high frequency only while gliding
```

For fauna, a cadence on the order of seconds is acceptable for route choice.

Exact cell size and cadence must be benchmarked.

---

## 10. Automated acceptance

Human-eye review is not a correctness gate.

Machine evidence should cover:

### Load
- Fowl Play + SmartBrainLib + YACL + A4MC reaches dedicated-server ready state.

### Activity injection
- stock hawk begins without Skyforge activity;
- compat setup adds exactly one SOAR group;
- repeated setup is idempotent.

### Schedule preservation
- no lift -> stock raptor schedule;
- useful lift -> thermal raptor schedule;
- loss of lift -> stock schedule restored.

### Priority preservation
- avoid/fight/pickup or other higher-priority state interrupts SOAR;
- SOAR never prevents REST during the normal rest window.

### Authority
- untrusted A4MC sample cannot trigger SOAR;
- trusted sample above entry threshold can;
- hysteresis prevents rapid toggling near threshold.

### Shared truth
- hawk and glider query the same cached sample/provenance.

### Population
- thermal appearance creates zero new hawks by itself.

### No-Fowl-Play fallback
- Skyforge boots normally when Fowl Play is absent;
- no Fowl Play classes are resolved on the absent-mod path.

---

## 11. Human-eye tuning

Human play remains useful for:

- whether the orbit radius looks natural;
- whether birds visibly gain altitude enough to teach the player;
- whether animation still reads as soaring rather than frantic flapping;
- whether the cue is too common or too rare;
- whether flock density becomes visually noisy.

These are presentation/ecology tuning questions.

They do not decide whether the architecture is correct.

---

## 12. Bespoke budget result

Before this audit:

```text
possible bespoke species:
    cliff raptor / adapted red-tailed hawk
```

After this audit:

```text
bespoke hawk entity:
    REJECT FOR NOW

Fowl Play hawk:
    RETAIN

Skyforge work:
    thin optional atmosphere/brain compatibility
```

Only reopen a bespoke hawk entity if runtime testing proves the external SmartBrain activity/schedule
seam cannot produce acceptable behavior.

## Acceptance principle

> Reuse the existing red-tailed hawk and its flight brain. Skyforge should contribute knowledge of
> where useful lift exists, not rebuild a bird merely to make it circle that lift.
