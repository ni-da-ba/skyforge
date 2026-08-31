# ADR-0040 — Post-Surface Worldgen Insertion

**Status:** Proposed

## Context

SF-IMP-0033 through SF-IMP-0035 proved that Skyforge can realize deterministic floating terrain into real Minecraft chunks, display it in an interactive client, persist it, and ship as a normal NeoForge artifact. The accepted lifecycle callback is `ChunkEvent.Load(isNewChunk=true)`, which is deliberately provisional because it occurs after important world-generation systems may already have run.

SF-IMP-0036 audits Minecraft/NeoForge 1.21.1 for the earliest practical supported insertion point that improves native world-generation participation without introducing a mixin/coremod or moving Minecraft concepts upstream.

## Source audit

Minecraft 1.21.1 generation proceeds through the relevant chunk statuses in this order:

```text
STRUCTURE_STARTS
BIOMES
NOISE
SURFACE
CARVERS
FEATURES
INITIALIZE_LIGHT
LIGHT
SPAWN
FULL
```

`ChunkStatusTasks` delegates these stages to the active `ChunkGenerator`. In particular:

```text
NOISE    -> ChunkGenerator.fillFromNoise(...)
SURFACE  -> ChunkGenerator.buildSurface(...)
CARVERS  -> ChunkGenerator.applyCarvers(...)
FEATURES -> final heightmap priming, then ChunkGenerator.applyBiomeDecoration(...)
```

The NeoForge 1.21.1 patch to `ChunkStatusTasks` does not expose a general base-terrain event between these stages. Its added worldgen hook concerns post-worldgen spawners, not terrain realization.

`ChunkEvent.Load` is explicitly documented by NeoForge as a load/lifecycle event and may fire before promotion to FULL, but it is still downstream of the generation stages above. It therefore remains unsuitable as the final terrain-generation seam.

Minecraft's supported generator architecture is codec/registry based. `ChunkGenerator` is dispatched through the chunk-generator registry, and `NoiseBasedChunkGenerator` is designed to be subclassed by custom generator types.

## Decision

The first earlier Skyforge insertion uses a registered `NoiseBasedChunkGenerator` subtype.

The subtype preserves vanilla noise generation and vanilla surface construction, then applies the accepted Skyforge additive chunk overlay immediately after `super.buildSurface(...)` returns.

Conceptually:

```text
vanilla BIOMES
    -> vanilla NOISE
    -> vanilla SURFACE
    -> Skyforge additive realization
    -> vanilla CARVERS
    -> final heightmap priming
    -> vanilla FEATURES
    -> vanilla lighting initialization
    -> vanilla LIGHT
```

The generator remains inert when no Skyforge worldgen runtime binding is installed. Selecting the generator and supplying a Skyforge world plan remain backend/configuration concerns.

For the interactive SF-IMP-0036 proof, a selectable `skyforge:development` world preset is provided through a separate Gradle `development` source set. ModDev loads that resource set locally, but the production jar continues to package only `main`, so this temporary validation preset and its world-selector tag are not shipped as user-facing configuration.

## Why post-surface rather than post-noise

Injecting immediately after `fillFromNoise` would be earlier, but it creates two avoidable problems for this first proof:

1. `fillFromNoise` is asynchronous, making ordering and failure propagation more complex.
2. vanilla `SurfaceSystem` uses the noise generator's own preliminary-surface estimates. A separately inserted elevated Skyforge volume is not represented by those estimates, so allowing vanilla surface construction to reinterpret the new island at this point would create an unproven coupling between two different surface models.

Post-surface insertion keeps the accepted Skyforge structural semantics/material projection authoritative while still moving terrain ahead of carvers, final heightmap priming, biome features, and lighting.

## Why not another event

No audited public NeoForge 1.21.1 event provides the required base-terrain insertion between SURFACE and CARVERS. Inventing an event-like polling scheme around `ChunkEvent.Load` would not change its timing.

## Why not a mixin/coremod

A mixin into `ChunkStatusTasks` could place code at almost any stage, but it would bind Skyforge to Minecraft internals unnecessarily when the supported `ChunkGenerator` abstraction already owns these stages. A mixin remains a fallback only if a later requirement cannot be expressed through supported generator APIs.

## Invariants

1. Kernel/model/recipes/world remain free of Minecraft and NeoForge APIs.
2. The generator subtype lives entirely in the NeoForge adapter.
3. Vanilla terrain generation remains authoritative before the Skyforge overlay.
4. Skyforge AIR remains additive/non-destructive.
5. The Skyforge planner is not run in the per-chunk hot path; the generator consumes an already-installed runtime catalog/adapter binding.
6. The registered generator is inert without an explicit runtime binding.
7. Normal worlds that do not select the Skyforge generator remain unchanged.
8. Existing SF-IMP-0033 load-event realization remains available only as the accepted legacy/provisional lifecycle proof until superseded deliberately.
9. Development-only preset/tag resources used for this proof are excluded from distributable artifacts.

## What this seam should improve

Because Skyforge blocks exist before the later stages, this seam is intended to make them visible to:

- vanilla carvers;
- final heightmap priming;
- heightmap-driven placed features;
- biome decoration/features;
- lighting initialization and propagation.

SF-IMP-0036 must prove at least the final-heightmap consequence automatically and the real generator path interactively before this ADR can be Accepted.

## Explicit limitations

This first seam does **not** claim that:

- `STRUCTURE_STARTS` sees Skyforge terrain; that stage is earlier;
- vanilla `SurfaceSystem` applies biome-native surface rules to Skyforge terrain; it has already run;
- all vanilla/modded features are automatically suitable for floating terrain;
- carvers necessarily produce desirable island caves;
- the generator is yet exposed as a polished user-facing world preset.

Structure-aware height queries and biome-surface adaptation are separate integration problems and should be solved explicitly rather than hidden inside this milestone.

## Acceptance criteria

ADR-0040 becomes Accepted only after:

1. a Skyforge `NoiseBasedChunkGenerator` subtype is registered through the normal chunk-generator registry;
2. its `buildSurface` path invokes Skyforge only after vanilla surface generation;
3. no active Skyforge runtime binding leaves the generator behavior inert;
4. an active binding performs the accepted additive realization into a real `ChunkAccess`;
5. Skyforge AIR preserves backend-native blocks;
6. Minecraft final heightmap priming performed after realization observes the elevated Skyforge solid;
7. backend-neutral independence remains green;
8. focused NeoForge tests and repository-wide validation pass;
9. a development-only world preset selects the registered generator without entering the production jar;
10. a real ModDev client can create a new world using that preset, observe the expected floating specimen, retain native terrain, show no obvious chunk-ownership seam, light the specimen through later vanilla stages, and preserve it across save/reload;
11. the interactive proof uses only the post-surface runtime binding and does not depend on the legacy `ChunkEvent.Load` binding.
