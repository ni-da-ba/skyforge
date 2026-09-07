# Wave C1 Loader Dependency Closure v0.1

**Snapshot:** 2026-09-05  
**Status:** Static dependency graph closed; Gradle resolution and Minecraft startup remain runtime gates.

## Purpose

The Wave C1 run profiles deliberately use immutable top-level mod coordinates from Modrinth.

Modrinth's Maven endpoint does not guarantee transitive dependency metadata, so the specimen cannot assume that Gradle will discover every required Minecraft mod from POM metadata.

Instead, this audit classifies every runtime dependency as one of:

~~~text
DIRECT PIN
    explicit Wave C1 runtime-classpath artifact

NESTED / BUNDLED
    intentionally supplied inside an upstream release jar

OPTIONAL
    not required for the focused Wave C1 acceptance question

PLATFORM
    Minecraft / NeoForge
~~~

The objective is a closed loader graph without loose duplicate copies of libraries already supplied by Jar-in-Jar packaging.

# Platform

~~~text
Minecraft 1.21.1
NeoForge 21.1.249
~~~

The selected Create/Sable/Aeronautics/CBC/Metallurgy release lines target NeoForge 21.1.228-era 1.21.1 runtimes or compatible ranges, so the adapter's 21.1.249 runtime is above their known minimums while remaining in the same 1.21.1 loader line.

Classification:

~~~text
Minecraft
    PLATFORM

NeoForge
    PLATFORM
~~~

# Create 6.0.10

Direct Wave C1 pin:

~~~text
Create 6.0.10+mc1.21.1
~~~

Create's NeoForge build Jar-in-Jars its runtime libraries rather than requiring the pack to install loose copies.

Current 1.21.1 source/build data identifies:

~~~text
Ponder 1.0.82
Flywheel 1.0.6
Registrate MC1.21-1.3.0+67
~~~

as Create's matching runtime libraries.

The Create changelog also explicitly states that Ponder ships with the Create jar.

Classification:

~~~text
Create
    DIRECT PIN

Ponder
Flywheel
Registrate
    NESTED / BUNDLED WITH CREATE
~~~

Do not add top-level Ponder/Flywheel/Registrate pins merely because Modrinth's Maven POM does not expose them transitively.

# Create Big Cannons 5.11.7

Published required content:

~~~text
Create
Ritchie's Projectile Library
~~~

Both are direct Wave C1 pins.

CBC's 1.21.1 source line is built against:

~~~text
Create 6.0.10
RPL 2.1.2
NeoForge 21.1.228
~~~

which matches the specimen.

Classification:

~~~text
Create Big Cannons
Ritchie's Projectile Library
Create
    DIRECT PINS
~~~

No additional hard CBC library is identified by the published required-content graph.

# Ritchie's Projectile Library 2.1.2

RPL is the explicit CBC library dependency and is pinned directly.

Its 2.1.2 NeoForge artifact targets Minecraft 1.21.1.

Classification:

~~~text
RPL
    DIRECT PIN
~~~

# Create Crafts & Additions 1.6.0

The current 1.21.1 metadata requires:

~~~text
Create [6.0.7, 6.1.0)
~~~

and marks integrations such as JEI and ComputerCraft optional.

Wave C1 supplies:

~~~text
Create 6.0.10
JEI 19.50.0.414
~~~

ComputerCraft is not required for the Silver-free accumulator acceptance question.

Classification:

~~~text
CC&A
Create
JEI
    DIRECT PINS

ComputerCraft
    OPTIONAL
~~~

# Create: Metallurgy 1.0.3

The selected release is explicitly published for Create 6.0.10.

The current 1.21.1 metadata line requires Create and treats JEI, Jade, KubeJS, and CreateJS as optional integrations.

Wave C1 supplies Create and JEI.

Classification:

~~~text
Create: Metallurgy
Create
JEI
    DIRECT PINS

Jade
KubeJS
CreateJS
    OPTIONAL
~~~

The foundry A/B does not require optional scripting/UI integrations to answer its material-economy question.

# Sable 2.0.5

Sable is pinned directly.

Its 2.0.5 NeoForge build explicitly Jar-in-Jars:

~~~text
Sable Companion 1.6.0
Veil
Sable Rapier runtime
~~~

and its published release identifies Veil as included content.

Create is optional to Sable itself, but Create is already directly present for this specimen.

Classification:

~~~text
Sable
    DIRECT PIN

Sable Companion 1.6.0
Veil
Sable Rapier
    NESTED / BUNDLED WITH SABLE
~~~

This matters because Propulsion declares `sablecompanion` as a required loader mod. That requirement is satisfied by Sable's nested Companion.

Adding a second loose Sable Companion is therefore unnecessary and may create Jar-in-Jar version-selection noise.

# Create Aeronautics 1.3.2

The published artifact is:

~~~text
create-aeronautics-bundled-1.21.1-1.3.2.jar
~~~

The bundled build packages the project's three runtime modules:

~~~text
Simulated
Aeronautics
Offroad
~~~

Published external required content is only:

~~~text
Create
Sable
~~~

Both are direct Wave C1 pins.

Classification:

~~~text
Create Aeronautics bundled distribution
    DIRECT PIN

Simulated
Aeronautics module
Offroad
    NESTED / BUNDLED WITH CREATE AERONAUTICS

Create
Sable
    DIRECT PINS
~~~

Do not also pin loose Simulated or Offroad jars.

# Create Propulsion: Simulated 1.1.5

Propulsion is pinned directly.

Its release line targets Minecraft 1.21.1, requires Sable 2.0.3+ behavior, and was built against the Sable/Aeronautics ecosystem.

Its loader metadata includes a required `sablecompanion` relationship.

Wave C1 closes that dependency through Sable 2.0.5's nested Sable Companion 1.6.0.

Propulsion's source/release line also references Simulated/Aeronautics/Offroad. Wave C1 does not rely on Propulsion to provide those modules: it already loads the official Create Aeronautics 1.3.2 bundled distribution explicitly.

Therefore:

~~~text
Create Propulsion
Create Aeronautics bundled
Sable
Create
    DIRECT PINS

Sable Companion
    NESTED WITH SABLE

Simulated / Aeronautics / Offroad
    NESTED WITH CREATE AERONAUTICS
~~~

This is intentionally redundant at the *capability* level but not at the jar/mod-id level: the top-level Aeronautics bundle is the authoritative physics/content substrate and Propulsion is the addon under evaluation.

# JEI 19.50.0.414

JEI is a direct development pin for recipe closure inspection.

It is not a production Skyforge dependency.

The selected engineering mods' JEI relationships are optional or use older compatible 19.x development baselines.

Classification:

~~~text
JEI
    DIRECT DEVELOPMENT PIN
~~~

# Closed graph

The expected Wave C1 external runtime graph is now:

~~~text
Minecraft 1.21.1
└── NeoForge 21.1.249
    ├── Create 6.0.10
    │   ├── [nested] Ponder
    │   ├── [nested] Flywheel
    │   └── [nested] Registrate
    ├── RPL 2.1.2
    ├── CBC 5.11.7
    ├── CC&A 1.6.0
    ├── JEI 19.50.0.414
    ├── Create: Metallurgy 1.0.3        [focused/integrated profiles]
    ├── Sable 2.0.5                      [propulsion/integrated profiles]
    │   ├── [nested] Sable Companion 1.6.0
    │   ├── [nested] Veil
    │   └── [nested] Rapier runtime
    ├── Create Aeronautics 1.3.2 bundled [propulsion/integrated profiles]
    │   ├── [nested] Simulated
    │   ├── [nested] Aeronautics
    │   └── [nested] Offroad
    └── Create Propulsion 1.1.5           [propulsion/integrated profiles]
~~~

No known loader-required top-level dependency is absent from the current specimen.

# What remains unproven

Static closure is not startup proof.

The following still require the actual artifact-resolution / launch gate:

1. Modrinth Maven resolves every immutable top-level version ID from the development environment.
2. NeoForge Jar-in-Jar selection accepts the exact nested versions together.
3. Propulsion 1.1.5 initializes against Sable 2.0.5 and Aeronautics 1.3.2 without binary-linkage errors.
4. JEI 19.50.0.414 initializes all selected plugin integrations.
5. The integrated profile has no duplicate mod-id or mixin conflict.

The first command remains:

~~~bash
./gradlew :skyforge-neoforge-1211:waveC1ResolvePinnedMods
~~~

followed by the focused client launches.

## Acceptance principle

> Pin top-level capabilities; trust upstream nested packaging only where source and release packaging agree; never add duplicate libraries merely because a Maven proxy omits transitive metadata.
