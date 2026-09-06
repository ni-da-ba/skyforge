# Wave C1 Runtime Specimen v0.1

**Snapshot:** 2026-09-05  
**Status:** Reproducible launch scaffold complete; runtime/human-eye acceptance remains pending.

## Purpose

Turn the Wave C1 recipe/worldgen normalization scaffold into a launchable specimen without making any selected engineering addon a production Skyforge dependency.

The runtime specimen is intentionally development-only.

It exists to answer evidence questions, not to commit the pack to retaining every loaded mod.

## Version lock

The machine-readable lock is:

~~~text
skyforge-neoforge-1211/wave-c1-mods.properties
~~~

Current pins:

| Component | Pinned version | Role |
|---|---|---|
| Minecraft | 1.21.1 | target game |
| NeoForge | 21.1.249 | existing Skyforge adapter runtime |
| Create | 6.0.10+mc1.21.1 | common engineering substrate |
| Ritchie's Projectile Library | 2.1.2 | CBC required dependency |
| Create: Big Cannons | 5.11.7 | baseline heavy-industry spine |
| Create Crafts & Additions | 1.6.0 | baseline electrical bridge |
| Create: Metallurgy | 1.0.3-1.21.1 | optional foundry A/B |
| Sable | 2.0.5+mc1.21.1 | moving-sublevel substrate for propulsion specimen |
| Create Aeronautics | 1.3.2+mc1.21.1 | propulsion/aeronautics compatibility substrate |
| Create Propulsion: Simulated | 1.1.5 | optional advanced propulsion branch |
| JEI | 19.50.0.414 | recipe-closure inspection |

The Gradle coordinates use immutable Modrinth version IDs rather than floating version ranges.

## Repository isolation

These jars are not ordinary `runtimeOnly` dependencies.

Each specimen uses ModDevGradle's run-specific additional runtime classpath so optional content cannot leak into:

- normal Skyforge client runs;
- SF-IMP acceptance runs;
- production packaging;
- backend-neutral modules.

The only workspace-wide addition is the Modrinth Maven repository, restricted to the `maven.modrinth` group.

## Development runs

### Baseline

~~~bash
./gradlew :skyforge-neoforge-1211:runWaveC1BaselineClient
~~~

Loads:

~~~text
Create
RPL
Create: Big Cannons
Create Crafts & Additions
JEI
Skyforge development resources
~~~

Use for:

- CBC material closure;
- Silver-free Modular Accumulator;
- baseline factory timing/readability;
- rejected-material search without optional Metallurgy/Propulsion noise.

### Metallurgy A/B specimen

~~~bash
./gradlew :skyforge-neoforge-1211:runWaveC1MetallurgyClient
~~~

Adds:

~~~text
Create: Metallurgy
~~~

Use for:

- Wolframite suppression;
- Steel/Bronze collision inventory;
- molten Steel/Bronze interoperability;
- Factory A versus Factory B foundry comparison.

### Propulsion specimen

~~~bash
./gradlew :skyforge-neoforge-1211:runWaveC1PropulsionClient
~~~

Adds:

~~~text
Sable
Create Aeronautics
Create Propulsion: Simulated
~~~

Use for:

- Platinum suppression;
- hidden Platinum recipe dependency search;
- advanced propulsion recipe-survival audit;
- Gold/Electrum electrical-throughput observations in the moving-structure stack.

### Integrated collision specimen

~~~bash
./gradlew :skyforge-neoforge-1211:runWaveC1IntegratedClient
~~~

Loads both optional branches.

Use only after the focused runs load cleanly.

Its purpose is to expose:

- cross-addon recipe collisions;
- duplicate Steel/Bronze identities;
- hidden rejected-material leaks;
- JEI clutter that does not appear in isolated tests.

## Artifact-resolution preflight

Before launching Minecraft:

~~~bash
./gradlew :skyforge-neoforge-1211:waveC1ResolvePinnedMods
~~~

This resolves the exact pinned artifacts for every C1 profile and prints the resulting jar filenames.

It is deliberately not wired into normal CI: optional-mod availability must not become a production build prerequisite.

## Datapack precedence

The development source set continues to mirror the C1 overrides as mod resources.

The authoritative fallback remains:

~~~text
skyforge-neoforge-1211/src/development/wave-c1-datapack
~~~

If a focused run proves that ordinary mod-resource ordering does not win, install that standalone datapack into the disposable test world at higher priority and repeat the proof.

Do not add Java solely to force Platinum/Wolframite suppression.

## Runtime gate sequence

Run in this order:

~~~text
1. waveC1ResolvePinnedMods
2. waveC1BaselineClient
3. waveC1MetallurgyClient
4. waveC1PropulsionClient
5. waveC1IntegratedClient
~~~

Stop on the first loader/dependency failure and record the exact missing/incompatible mod rather than changing multiple pins at once.

## Evidence required

For each focused client:

~~~text
RUN PROFILE
PIN MANIFEST COMMIT
LOADED MOD LIST
DATAPACK LIST / PRIORITY
JEI SEARCH RESULTS
RELEVANT RECIPE SCREENSHOT
WORLDGEN OBSERVATION
PASS / FAIL
NOTES
~~~

The runtime specimen becomes accepted only when the existing Wave C1 acceptance specification's human-eye/runtime gates are recorded.

## Current boundary

This commit does **not** claim:

- Platinum suppression has worked in a live world;
- Wolframite suppression has worked in a live world;
- Gold wiring is sufficient at mature scale;
- Metallurgy improves the factory;
- Steel/Bronze identities are already normalized.

It removes assembly ambiguity so those questions can now be answered on one exact mod stack.


Static recipe closure evidence that narrows those questions is recorded at:

~~~text
docs/design-audit/wave-c1-static-recipe-closure-audit-v0.1.md
~~~

In particular, the development specimen now includes a retained-material Industrial Crucible override, while Propulsion Platinum recipe rebasing remains intentionally deferred because stock Propulsion provides a rare Platinum-byproduct route from washed Crushed Raw Gold.

## Acceptance principle

> Reproduce the specimen exactly, change one variable at a time, and retain only the integrations whose gameplay value survives direct comparison.
