# Project Skyforge

Skyforge is a backend-neutral procedural world synthesis engine. Its first proof is deliberately narrow: a semantic island descriptor must compile into an inspectable procedural graph and deterministically produce a recognizable island without depending on Minecraft.

## Current phase

Sprint One and the v0.1 architecture proof are complete. The private canonical repository is
`ni-da-ba/skyforge`, the durable Java package root is `io.github.nidaba.skyforge`, and the release
version is `0.1.0`.

The proof contains six canonical seeded islands, exact regression hashes, a visual atlas, and an
observational reference-evaluator benchmark. It proves the descriptor-to-graph-to-evidence
pipeline independently of Minecraft; it is not a content-complete world generator. The next
project action is to define the v0.2 composition and secondary-morphology boundary.

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

Generate the standard 1024 by 1024 signal-free evidence package with:

```shell
./gradlew :skyforge-reference:run --args='build/evidence/signal-free-island-v1'
```

The package contains canonical raw grids and hexadecimal cross-section data alongside PNG review
images. Its manifest records versions, bounds, statistics, morphology metrics, and SHA-256 hashes.
The signal-free acceptance suite executes the named `SF-ISL` gates and pins the normative standard
package hashes; run it with the ordinary `./gradlew check` command.
Seeded graphs use canonical graph schema 2 while signal-free graphs retain byte-identical schema-1
serialization. The seeded recipe caps relative height modulation at 15 percent and preserves the
base land mask exactly.

Regenerate and verify the complete fixed-seed corpus with:

```shell
./gradlew :skyforge-reference:fixedSeedCorpus
```

The command produces six complete 1024 by 1024 evidence packages under
`skyforge-reference/build/evidence/fixed-seed-island-v1`, verifies 49 canonical artifacts against
the checked-in golden corpus, writes environment-qualified benchmark observations, and creates an
`index.html` atlas for side-by-side review of height, land-mask, slope, and cross-section images.
Benchmark timings are observations, not pass/fail thresholds, and are excluded from canonical
checksums. Every CI run publishes the generated directory as a downloadable workflow artifact.

## Project record

The implementation contract, acceptance gates, source authority, work register, and risk register are maintained in [`docs/architecture/Skyforge_Architecture_Baseline_v0.1.md`](docs/architecture/Skyforge_Architecture_Baseline_v0.1.md).

The exact gate-to-test trace, corpus facts, visual-reading guide, deferrals, and release procedure
are recorded in [`docs/releases/Skyforge_v0.1.0_Release_Record.md`](docs/releases/Skyforge_v0.1.0_Release_Record.md).

## License

No license has been granted. This private development repository is not open source unless and until the owner adds an explicit license.
