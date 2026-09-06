# Wave C3 Atmosphere Authority Prototype v0.1

**Snapshot:** 2026-09-05  
**Status:** Development scaffold; authority selection and quantitative runtime acceptance pending.

## Purpose

Wave C3 answers one narrow architectural question:

> Can Aerodynamics4MC become Skyforge's one authoritative instantaneous wind/atmosphere provider,
> with Create Aeronautics consuming true relative airflow and every later glider/ecology/navigation
> integration reading the same trusted field?

This is an **authority-selection** experiment, not a weather-content pack.

The desired dependency direction remains:

```text
Skyforge semantic climate / terrain / geography
        ↓
Aerodynamics4MC authoritative instantaneous atmosphere
        ↓
+ Create Aeronautics / Sable aircraft
+ future Skyforge glider adapter
+ future soaring-fauna behavior
+ instruments / CC / navigation
+ particles / sound / presentation
```

No second wind authority is admitted.

---

## 1. Source-backed candidate state

### Aerodynamics4MC core

Audited source:

```text
repository: MozillaFiredoge/Aerodynamics4MC-Core
source head: 62a52a584e9c65246e50226b29a1f0449e43995e
release: 0.2.1
Minecraft: 1.21.1 NeoForge
license: MIT
```

The public API exposes a gameplay sample containing:

```text
mean wind vector
pressure
gust vector
temperature
humidity
turbulence intensity
updraft m/s
wind shear magnitude / block
shelter factor
boundary-layer stability
boundary-layer mixing strength
confidence
source level
authority
epochs
```

and explicitly distinguishes trusted server-authoritative/server-aggregated gameplay samples.

That is materially stronger than a simple global wind vector.

### Create Aeronautics compatibility addon

The audited compat code samples the authoritative gameplay wind and computes:

```text
environmentWind = A4MC gameplay wind transformed into vehicle-local frame
bodyVelocity    = Sable point velocity
relativeWind    = environmentWind - bodyVelocity
airfoilWind     = relativeWind with spanwise flow removed
```

It then derives angle of attack and aerodynamic forces from that relative airflow.

This is the correct conceptual direction for Skyforge.

The addon declares runtime requirements compatible with the current retained flight stack:

```text
Sable       >= 2.0.0, < 3.0.0
Simulated   >= 1.3.0
Aeronautics >= 1.3.0
```

Current Skyforge C1 pins Sable 2.0.5 and Aeronautics 1.3.2.

### Wind Tunnel

Audited source:

```text
repository: MaxnessAWA/Wind-Tunnel
source head: 9ae386e595a1ba7c1f2ee40a12a7935c5e166a10
release: 1.1.8
Minecraft: 1.21.1 NeoForge
license: MIT
```

It supplies controlled airflow, aircraft binding/pose locking, and force/moment readback.

Wave C3 treats Wind Tunnel as **measurement infrastructure**, not atmosphere authority.

---

## 2. Artifact-layout problem and resolution

Aerodynamics4MC 0.2.1 publishes at least two required runtime jars:

```text
aerodynamics4mc-0.2.1-neoforge+1.21.1-SNAPSHOT.jar
aerodynamics4mc-compat-create-aeronautics-0.2.1-neoforge+1.21.1-SNAPSHOT.jar
```

Modrinth exposes them as separate version IDs under the same project/module identity:

```text
core:
    maven.modrinth:UnshaaiE:6Z0Z1pfP

Create Aeronautics compat:
    maven.modrinth:UnshaaiE:L1NGyZ63
```

Putting those two module coordinates directly into one Gradle configuration risks ordinary conflict
resolution selecting only one version.

Wave C3 therefore resolves them in **two independent resolvable configurations** and attaches the
resulting files to ModDevGradle run classpaths.

The C3 preflight fails if the two isolated artifact resolutions collapse onto the same file.

---

## 3. What is intentionally excluded

### No A4MC content addon

The official A4MC content jar is not part of authority selection.

Skyforge does not need to adopt:

- example/gameplay wind blocks;
- A4MC vehicles;
- A4MC presentation content;
- any duplicate world vocabulary;

merely to use the atmosphere runtime/API.

If later instrumentation is useful, select individual content roles separately.

### No second weather authority

Do not install Aeronautics: Winds & Weather, Weather2, or another full weather simulation into this
authority test.

They may remain content/presentation candidates later, but only if they consume or defer to the
selected authority.

### No glider/fauna adapter yet

Reliable Gliders does not automatically consume A4MC natural updrafts.

Do not write that adapter until C3 proves the A4MC authority + aircraft seam is viable.

The intended next adapter is thin:

```text
trusted A4MC GameplayWindSample
    ↓
updraft / local wind query
    ↓
Reliable Gliders compatibility force
    ↓
same query available to soaring fauna
```

not a second thermal simulation.

---

## 4. Development profiles

### Atmosphere core

```bash
./gradlew :skyforge-neoforge-1211:runWaveC3AtmosphereCoreClient
```

Contains A4MC core only, plus Skyforge itself.

Questions:

1. Does the 1.21.1 NeoForge core load cleanly?
2. Do native solver binaries initialize on the supported workstation/CI architectures?
3. Does a world run without A4MC official content?
4. Is the server gameplay sample available independently of client-local L2?
5. Is shutdown/reload clean?

### Aircraft wind

```bash
./gradlew :skyforge-neoforge-1211:runWaveC3AircraftWindClient
```

Adds:

```text
Create
Sable
Create Aeronautics
JEI
A4MC core
A4MC Create Aeronautics compat
```

Questions:

1. Does the compat addon recognize the current Sable/Aeronautics stack?
2. Does it sample server-authoritative gameplay wind?
3. Does relative airflow change with environment wind at fixed body velocity?
4. Does body velocity change relative airflow in the opposite direction at fixed environment wind?
5. Are lift/drag forces tied to relative airflow rather than arbitrary world force?
6. Do save/reload and reassembly preserve correct integration state?

### Wind Tunnel measurement

```bash
./gradlew :skyforge-neoforge-1211:runWaveC3WindTunnelClient
```

Adds Wind Tunnel 1.1.8 and its source-tested LDLib 2.2.6 floor.

This profile exists to turn qualitative flight claims into controlled measurements.

## 5.1 Headless loader smoke

The C3 workflow also launches three disposable dedicated-server profiles:

```text
runWaveC3AtmosphereCoreServer
runWaveC3AircraftWindServer
runWaveC3WindTunnelServer
```

Each receives an isolated game directory, EULA, and minimal server properties. CI treats the run as
accepted only after the server reaches Minecraft's ready state.

This closes several failure classes without human intervention:

- missing required mod IDs;
- loader/version incompatibilities;
- missing runtime libraries;
- server-side classloading mistakes;
- A4MC native/runtime initialization failures severe enough to prevent startup;
- incompatibility between A4MC compat and the retained Create/Sable/Aeronautics stack;
- Wind Tunnel dependency closure failures.

A successful artifact-resolution task is therefore necessary but no longer sufficient.

---

## 6. Replace human-eye physics gates with numerical cases

Human observation is useful for ergonomics and presentation. It should **not** be the primary proof
that wind physics is correct.

Wind Tunnel makes the following acceptance matrix possible:

| Case | Body state | Imposed/environment flow | Expected invariant |
|---|---|---|---|
| calm baseline | fixed | 0 | near-zero aerodynamic response |
| headwind | fixed | +V along chord | nonzero relative airflow; drag/lift according to AoA |
| equivalent motion | body -V | calm | approximately equivalent relative airflow to headwind case |
| tailwind cancellation | body +V | environment +V | relative airflow approaches zero |
| crosswind | fixed | lateral V | side-force/yaw response; no fake forward-only model |
| updraft | fixed | +Y | vertical relative-air contribution is measurable |
| doubled speed | same geometry | 2V | force scaling follows selected aerodynamic model |
| reversed flow | fixed | -V | force/angle response changes consistently |
| sheltered vs exposed | same coarse wind | different local shelter | gameplay sample/response reflects local field |

Exact tolerances belong in the later executable acceptance harness.

The core invariant is:

```text
V_relative = V_air - V_body
```

in the airfoil frame, followed by the aerodynamic model.

---

## 7. Human-eye role

A human-eye pass is **not required to establish technical correctness** for C3.

Automated or instrumented evidence should decide:

- dependency/load viability;
- authority source;
- wind-vector sampling;
- relative-airflow sign/direction;
- force/moment response;
- crosswind/updraft coupling;
- determinism within the intended model;
- performance budget;
- persistence/reload.

Human play is useful later for questions that are genuinely perceptual:

- Are gusts annoying or interesting?
- Can the player read wind without opening debug tools?
- Does turbulence feel violent enough for storms but tolerable in ordinary flight?
- Is gliding satisfying?
- Are windsocks, clouds, fauna, and sound sufficiently legible?

Those are tuning questions, not merge blockers for the authority scaffold.

---

## 8. Shared-lift architecture after authority acceptance

If A4MC passes the aircraft authority gate, the next thin integration should expose one Skyforge
atmospheric query facade rather than duplicate physics:

```text
SkyforgeAtmosphereView
    semantic caller-facing interface
        ↓
A4MC GameplayWindSample
        ↓
mean wind
effective wind
updraft
turbulence
shear
shelter
temperature/humidity where useful
confidence / authority
```

Consumers:

```text
AIRCRAFT
    existing A4MC Create Aeronautics compat

GLIDER
    thin compatibility adapter

SOARING FAUNA
    behavior planner reads same lift opportunities

INSTRUMENTS / CC
    quantitative readout / automation

PRESENTATION
    particles, flags, windsocks, sound
```

A circling adapted red-tailed hawk can then be a genuine environmental cue because its decision to
soar is driven by the same lift field available to the player's glider.

---

## 9. Authority rejection criteria

Reject A4MC as the core authority if any of these remain after reasonable configuration/integration:

1. server gameplay samples are not trustworthy/deterministic enough for gameplay;
2. native/runtime support is too fragile for the target platforms;
3. Aeronautics compat cannot coexist with current Sable/Aeronautics versions;
4. environmental wind does not materially enter true relative airflow;
5. performance cost is incompatible with Skyforge's large-world/aviation workload;
6. local/updraft/shear information cannot be consumed cleanly by other systems;
7. the API forces consumers into client-only or internal implementation details.

Do **not** reject it merely because default wind strength/turbulence tuning is imperfect.

---

## 10. Deferred work

Wave C3 does not yet:

- integrate Reliable Gliders with A4MC;
- implement soaring-fauna AI;
- author climate-to-A4MC forcing;
- select weather visuals;
- select cloud rendering;
- add CC instrumentation;
- tune storm severity;
- change Sable pressure ownership;
- add custom Skyforge aerodynamic forces;
- lock A4MC as production dependency.

## Acceptance principle

> Select one trustworthy atmosphere authority, prove relative-airflow coupling numerically, then
> make every other airborne system consume that authority through the thinnest possible adapter.
