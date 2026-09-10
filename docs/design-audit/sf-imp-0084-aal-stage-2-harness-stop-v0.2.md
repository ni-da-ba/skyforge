# SF-IMP-0084 — AAL 0.6.2 Stage 2 local-harness stop

**Status:** STOPPED — local exact-artifact/build-harness unavailable
**Issue:** #441
**Date:** 2026-09-10
**Repository head inspected:** `76d42f59`
**Production dependency change:** none

## Stage 3 decision

**STOP — AAL not worth adapting yet in this worker environment.** This is a local acquisition/build-harness
stop, not a design rejection. Trusted issue evidence `5611855826` identifies the exact immutable
released artifact, and directive `5611896156` authorizes Stage 2:

| Field | Trusted value |
|---|---|
| Modrinth project/version | `73ZXeRfx` / `EgPi4wq9` |
| Artifact | `create_aeronautics_automated_logistics-0.6.2.jar` |
| Minecraft / loader | `1.21.1` / NeoForge |
| SHA-256 | `b966fe666212da694ef19b292516643081b402c0af18549f94152dd387cc268e` |
| Maven coordinate | `maven.modrinth:73ZXeRfx:EgPi4wq9` |

That evidence does not materialize the jar into this network-disabled worker's local Gradle cache.
An exhaustive local cache filename search found no matching project ID, version ID, or jar filename.
The required local Gradle command also cannot start: `./gradlew --offline
:skyforge-neoforge-1211:dependencies --configuration runtimeClasspath` fails before project evaluation
because neither `JAVA_HOME` nor `java` is available. Therefore no compatible runtime, link check, or
generated-route fixture can be honestly run here.

Neither **GO**, **UPSTREAM HOOK REQUIRED**, nor **LOCAL FORK CANDIDATE** is justified. The trusted
released signatures remain portable artifact evidence, but this worker did not execute the real
pinned stack or construct/bind a station/controller at the public seam.

## Preserved Stage 2 boundary

No AAL production dependency, production adapter, neutral route contract, reflection, mixin, or
internal access was added. In particular, the existing AAL artifact-evidence workflow is a remote
identity/signature bridge, not a local runtime artifact provisioner; treating it as one would guess
at the build harness.

The retained stack remains exactly Minecraft `1.21.1`, NeoForge `21.1.249`, Create
`6.0.10+mc1.21.1`, Sable `2.0.5+mc1.21.1`, and Create Aeronautics `1.3.2+mc1.21.1`, as pinned in
`skyforge-neoforge-1211/wave-c1-mods.properties`.

## Required next input

Provide both of the following to a fresh isolated validation worker:

1. the exact trusted AAL jar above in a locally reachable build/cache location (or a local immutable
   artifact repository containing that coordinate); and
2. a Java 21 runtime available to the Gradle wrapper.

Only then add a run-scoped validation source set/runtime and prove boot/link compatibility before
attempting the minimum two-station route, independent Skyforge identity, idempotent binding,
persistence, playback, and missing-capability assertions. This handoff does not authorize the
follow-on lifecycle tranches or any morphology work.
