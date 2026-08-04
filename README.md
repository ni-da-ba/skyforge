# Project Skyforge

Skyforge is a backend-neutral procedural world synthesis engine. Its first proof is deliberately narrow: a semantic island descriptor must compile into an inspectable procedural graph and deterministically produce a recognizable island without depending on Minecraft.

## Current phase

Sprint Zero is documented and the Sprint One repository skeleton is ready. The private canonical repository is `ni-da-ba/skyforge`, and the durable Java package root is `io.github.nidaba.skyforge`.

The latest completed ticket is `SF-IMP-0005`: the validated signal-free island descriptor and
descriptor-to-graph recipe. The next planned ticket is `SF-IMP-0006`: the neutral reference
sampler, statistics, raster outputs, and evidence manifest.

## Modules

- `skyforge-kernel`: coordinates, field contracts, graph representation, validation, and reference evaluation.
- `skyforge-model`: semantic descriptors and descriptor validation.
- `skyforge-recipes`: deterministic descriptor-to-graph compilation.
- `skyforge-reference`: neutral sampling, visualization, reporting, and evidence generation.

Minecraft and NeoForge code is explicitly deferred. The first three modules may not import their APIs.

## Build

Requirements:

- A 64-bit JDK 25 installation.
- No system Gradle installation; use the checked-in wrapper.

Run:

```shell
./gradlew check
```

The wrapper is pinned to Gradle 9.6.1 and verifies the distribution checksum before use.

## Project record

The implementation contract, acceptance gates, source authority, work register, and risk register are maintained in [`docs/architecture/Skyforge_Architecture_Baseline_v0.1.md`](docs/architecture/Skyforge_Architecture_Baseline_v0.1.md).

## License

No license has been granted. This private development repository is not open source unless and until the owner adds an explicit license.
