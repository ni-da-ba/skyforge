# SF-IMP-0084 — AAL 0.6.2 pinned-stack feasibility handoff

**Status:** PROVISIONAL — CI validation specimen pending
**Issue:** #441
**Date:** 2026-09-10
**Repository head inspected:** `c5cc8cf6`
**Production dependency change:** none

## Stage 3 decision

**PROVISIONAL — pending ordinary CI validation.** Issue #441 now supplies immutable released-artifact
evidence for AAL 0.6.2, so the exact coordinate is confined to the `aalValidation` Gradle source set.
The regular `check` task must resolve the artifact, compile/link the isolated classpath, verify its
SHA-256, and confirm the reported released class surfaces. This is not a runtime boot, generated-route,
or adapter-feasibility result.

Neither **UPSTREAM HOOK REQUIRED** nor **LOCAL FORK CANDIDATE** is justified: the exact artifact has
not been inspected, so no narrow missing hook or source/API incompatibility has been established.

## Stage 1 evidence

### Exact Skyforge validation substrate

| Component | Exact checked-in pin | Evidence |
|---|---|---|
| Minecraft | `1.21.1` | `skyforge-neoforge-1211/wave-c1-mods.properties` |
| NeoForge | `21.1.249` | same |
| Create | `6.0.10+mc1.21.1` | same |
| Sable | `2.0.5+mc1.21.1` | same |
| Create Aeronautics | `1.3.2+mc1.21.1` | same |
| AAL | no checked-in coordinate, version pin, or source snapshot | repository pin search |

The retained flight substrate is validation-only in Wave C1/C9/C11/C14/C15; it is not evidence that
AAL 0.6.2 loads against it. Skyforge currently has no station, route, freight, or AAL runtime contract,
as recorded by C27.

### Local availability and seam map

The repository was searched for AAL/Automated Logistics version pins and source. The local Gradle cache
was searched for filenames containing `aal`, `automated`, or `logistics`. No candidate artifact/source
was present. No network retrieval was attempted.

Every required seam is consequently **unknown/unavailable on the exact pinned artifact**. The supplied
current-upstream observations below identify only what a supplied release must verify; they are not
callable pinned-API claims.

| Required seam | Exact AAL 0.6.2 classification | Supplied current-upstream reconnaissance (not pinned proof) |
|---|---|---|
| Station identity/binding | unknown/unavailable | `AirshipStationRegistry`, station snapshots, and block-entity state reported |
| Direct route construction | unknown/unavailable | public `Route`, `RoutePoint`, `RouteStop` construction reported |
| Route storage | unknown/unavailable | `RouteStorageService.saveRoute/loadRoute/deleteRoute` reported |
| Playback start/stop/pause/resume | unknown/unavailable | route playback services and runtime hold/resume paths reported |
| Vehicle controller binding | unknown/unavailable | `VehicleController` interface/reference reported |
| Schedule binding | unknown/unavailable | schedule execution constructs routes; binding remains unverified |
| Runtime persistence/recovery | unknown/unavailable | `AutomationRuntimeSavedData` capture/apply/save reported |
| Docking/cargo visibility | unknown/unavailable | station block entity and docking-session snapshots reported |

`RouteRecordingService` was reported as player-oriented and accepts `ServerPlayer`; direct route
construction elsewhere in current upstream source does **not** prove that the 0.6.2 release permits
generated routes without a player recording pass.

## Stage 2 disposition

Not attempted. Creating an isolated validation source set, compiling against a guessed coordinate, or
using reflection/mixins to discover state would violate #441's stop boundary. No AAL dependency,
production code, neutral route contract, fixture, generated route, or runtime evidence was added.

## Required next input

Provide locally either (1) the immutable AAL 0.6.2 Minecraft 1.21.1 NeoForge jar with SHA-256 and
release coordinate, or (2) matching release source mapped immutably to its distributable jar/checksum.
The harness must expose it only to a new isolated validation runtime/source set alongside the pins
above—not production dependencies. The next worker can then inspect released signatures and classify
all seams as public/callable, concrete-but-internal, player-recording-only, or unavailable before the
minimum deterministic two-station fixture.

If reopened, canonical Skyforge civilization/service/station/route/vehicle identity and immutable route
version remain independent of opaque AAL references. No player progression, morphology work, or
follow-on lifecycle tranche is authorized by this handoff.
