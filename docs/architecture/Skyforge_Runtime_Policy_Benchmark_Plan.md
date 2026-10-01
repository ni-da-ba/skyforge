# Skyforge Runtime Policy Benchmark Plan

## Purpose

Skyforge's accepted world-query boundary supports live region realization, preloaded regional realization, and hybrids. This plan defines how to choose among them from measured evidence once a concrete Minecraft-like adapter exists.

No runtime policy is selected by this document.

## Common deterministic workload

Every policy must be tested against the same immutable Skyforge world plan, morphology providers, terrain profile, material/backend mapping, and query sequence.

At minimum use:

- one sparse regional scene;
- one moderately dense archipelago scene;
- one scene with multiple islands crossing chunk boundaries;
- at least three deterministic root seeds;
- both sequential and shuffled chunk request order.

The benchmark must compare identical output identity before comparing speed.

## Policy A: live realization

For each requested chunk/region:

```text
catalog query
-> relevant compiled volume lookup
-> density evaluation
-> terrain semantic evaluation
-> backend material selection
-> chunk write
```

Measure:

- cold generation latency per chunk;
- warm generation latency where evaluator objects are cached;
- p50 / p95 / maximum latency;
- candidate volume references;
- density evaluations;
- surface/semantic evaluations;
- allocation pressure where measurable;
- peak memory;
- throughput for repeated and concurrent chunk requests.

## Policy B: coarse regional preload

For each coarse Skyforge region:

```text
materialize entire region once
-> store material/semantic/occupancy representation
-> serve chunk requests from region cache
```

Measure:

- preload latency;
- peak preload memory;
- retained cache memory;
- cache size per region;
- subsequent chunk latency;
- number of chunks required to amortize preload cost;
- eviction/reload cost if tested.

## Policy C: hybrid on-demand regional cache

For the first chunk request in a coarse region:

```text
identify coarse region
-> materialize/cache region
-> answer current chunk
-> subsequent chunks read cache
```

Measure the same values as preload plus:

- first-hit penalty;
- warm-hit rate under representative movement/query sequences;
- duplicated work at region boundaries;
- cache churn under bounded memory.

## Correctness gates

Performance results are invalid unless all compared policies satisfy:

1. identical Skyforge world-plan identity;
2. identical solid/air occupancy for every sampled backend position;
3. identical terrain semantic identity before backend-specific material differences;
4. identical concrete backend material output for equivalent backend context;
5. chunk request order independence;
6. no seams at chunk or coarse-cache boundaries;
7. no false-negative catalog culling.

## Representative request patterns

The benchmark should include more than a linear chunk sweep.

### Sequential traversal

Simulates a player moving steadily through new terrain.

### Radial expansion

Simulates ordinary world generation around a spawn/player location.

### Teleport/random access

Simulates distant travel and stresses preload waste/cache locality.

### Revisit

Requests previously generated regions again to expose cache benefits.

### Concurrent neighborhood

Requests neighboring chunks in parallel or backend-equivalent scheduling to test shared evaluator/cache contention.

## Metrics that should drive the decision

The production policy should be chosen from:

- worst-case player-visible chunk latency;
- steady-state throughput;
- first-load cost;
- retained memory per active region/player;
- cache hit rate;
- total redundant density/semantic work;
- server concurrency behavior;
- implementation complexity and failure modes;
- persistence requirements;
- compatibility with world edits/regeneration.

No single metric is authoritative in isolation.

## Decision guidance

### Favor live realization when

- chunk latency is comfortably below the backend budget;
- candidate culling keeps evaluation counts low;
- memory savings are significant;
- repeated evaluation is not a practical bottleneck.

### Favor preload when

- region generation cost is high but bounded;
- memory/cache footprint is acceptable;
- most materialized chunks are likely to be consumed;
- very low subsequent chunk latency is important.

### Favor hybrid when

- live cold latency is too high;
- full preload wastes substantial work/memory;
- spatial request locality is strong enough to produce high coarse-region cache hit rates.

These are hypotheses, not acceptance criteria.

## Spatial-index follow-up

The current `SkyIslandWorldCatalog` intentionally hides its linear implementation behind `query(WorldBounds)`. If profiling shows catalog scanning is material, repeat the workload after replacing or augmenting the internal implementation with a deterministic spatial index.

The public query semantics must remain unchanged.

## Reporting

Each benchmark run should write a machine-readable result containing:

- environment/JVM/backend versions;
- world-plan identity;
- policy;
- query sequence identity;
- correctness hashes;
- latency distribution;
- evaluation/reference counts;
- memory/cache observations;
- notes on concurrency and warm/cold state.

Human-facing charts are useful but must not replace the raw measurements.
