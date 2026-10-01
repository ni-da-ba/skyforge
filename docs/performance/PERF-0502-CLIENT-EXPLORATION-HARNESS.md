# PERF-0502 — Actual-client exploration benchmark harness

## Purpose

Issue #502's controlled 4/2/1 headless curve established the CPU/RAM scaling baseline, but it is not a player-FPS or sustained-exploration result. The next performance authority is an actual-client, player-like benchmark that can be repeated on GitHub's software-rendered client for relative regression evidence and on a real local GPU for hardware-facing evidence.

This document fixes the benchmark boundary before implementation so performance work does not drift into invented gameplay or broad CI fan-out.

## Current executable boundary

Current Skyforge `main` has deterministic persisted showcase/morphology worlds, actual quick-play client acceptance, retained-mod validation source sets, and opt-in runtime performance metrics. It does **not** yet expose one complete executable Bootstrap Province containing every final player-loop phase in a single production world.

Therefore the first benchmark harness must report only phases it actually executes. It must not label spectator/client traversal as real glider/aircraft physics, and it must not claim final minimum/recommended hardware from GitHub's software renderer.

## Harness requirements

The harness must be:

- opt-in and development-only;
- `workflow_dispatch` for expensive GitHub characterization, consistent with issue #319;
- an actual NeoForge quick-play client under Xvfb in GitHub Actions;
- reusable unchanged for a local hardware run;
- deterministic in start position, heading, phase order, and evidence schema;
- driven by the real quick-play client's movement key state after the initial start teleport, not repeated server teleports;
- paired with server-tick and client-frame distributions rather than wall time alone;
- explicit about which product phases are real, approximated only as traversal/load pressure, or unavailable on the current executable slice.

## Evidence schema

At minimum record:

### Client

- rendered-frame interval samples and p50/p95/p99/max;
- per-phase rendered-frame distributions where phase visibility is available;
- derived average FPS over the measured interval;
- measured frame count and wall duration;
- client memory used/committed/max at completion.

### Integrated server

- server-tick duration p50/p95/p99/max during the measured route;
- per-phase server-tick distributions;
- route phase duration and tick count;
- start/end chunk, unique visited chunk count, and maximum horizontal distance;
- loaded entity count where the current API exposes it safely;
- existing opt-in Skyforge runtime metrics.

### Process / workflow

- `/usr/bin/time -v` process evidence;
- per-process JVM GC/safepoint logs;
- run-directory disk bytes before/after where practical;
- renderer/vendor/version strings when a client OpenGL context is available;
- exact benchmark source commit, workflow commit, Java version, runner OS, and benchmark mode.

## Phase model

The evidence file must distinguish these phase identities even when not all are executable yet:

1. `spawn_idle`
2. `walk_traversal`
3. `glide_traversal`
4. `fresh_terrain_flight`
5. `active_machinery_cargo`
6. `save_reload`

A phase may have one of:

- `EXECUTED_REAL` — real current gameplay/runtime capability;
- `EXECUTED_LOAD_PROXY` — actual client/server traversal used only to create chunk/render pressure; never described as real vehicle physics;
- `UNAVAILABLE_CURRENT_SLICE` — retained in schema but not fabricated.

## Initial implementation tranche

The first PR proves the harness itself with the smallest honest current-world specimen:

- reuse a deterministic prepared Skyforge world and actual quick-play client;
- collect real frame intervals from the client render loop;
- collect integrated-server tick durations;
- perform exactly one start teleport, then hold real client forward input in spectator mode to cross chunk boundaries;
- preserve all existing mutation/persistence contracts;
- write machine-readable evidence and close automatically;
- provide a manual-only GitHub workflow with software-renderer labeling;
- provide a local Gradle entry point using the same evidence code.

Do **not** add a performance threshold on software-rendered FPS in this first tranche. The first run is characterization, not a release gate.

## Follow-on widening

Once the Bootstrap Province exposes real glider, aircraft, machinery/Guild, cargo, and save/reload phases in one accepted executable slice, widen this same harness phase-by-phase rather than creating another benchmark framework.

Any next optimization must cite this benchmark (or another current measured artifact) and identify a dominant player-facing cost before code changes begin.
