# Project Skyforge

Skyforge is a backend-neutral procedural world synthesis engine. Its first proof is deliberately narrow: a semantic island descriptor must compile into an inspectable procedural graph and deterministically produce a recognizable island without depending on Minecraft.

## Current phase

Sprint One and the v0.1 architecture proof are complete. The private canonical repository is
`ni-da-ba/skyforge`, the durable Java package root is `io.github.nidaba.skyforge`, and the release
version is `0.1.0`.

The proof contains six canonical seeded surface specimens, exact regression hashes, a visual atlas, and an
observational reference-evaluator benchmark. It proves the descriptor-to-graph-to-evidence
pipeline independently of Minecraft; its released v0.1 density field still extends indefinitely
downward. The v0.2 boundary now additionally accepts a semantic suspended-volume descriptor,
exact signed-density convention, three-dimensional intersection node, graph schema 3, canonical
3D evidence domain, signal-free upper/underside recipe, deterministic 3D evidence format, and one
pinned golden suspended-volume specimen. The signal-free specimen passes `SF-VOL-001` through
`SF-VOL-005` and `SF-VOL-007` through `SF-VOL-010`: 366,912 solid samples form one connected mass,
touch no domain face, and retain at least 88 world units of sampled air clearance.

`SF-IMP-0016` is accepted by local Java 25 validation. Its bounded seeded enrichment independently
modulates upper and underside offsets by at most 15 percent while preserving the signal-free rim,
horizontal footprint, and signed surface ordering by construction. The full-amplitude six-member
`SF-VOL-006` corpus passed with one connected component, zero domain-face contacts, and 88 world
units minimum sampled clearance for every member. Solid occupancy varied from 363,854 to 370,382
samples while the accepted morphology identity envelope remained intact.

Visual review of that corpus showed that SF-IMP-0016 is a successful deterministic detail layer but
not yet a landform hierarchy: the suspension-plane silhouettes are intentionally identical and the
isometric specimens remain dominated by the same smooth primary mass.

`SF-IMP-0017` is accepted by local Java 25 numerical validation and human visual review. It adds an
organized upper-surface layer above SF-IMP-0016 using a deterministic main ridge, oblique spur, and
valley expressed entirely with existing arithmetic graph nodes. Their combined upper-offset factor
is analytically bounded to `[0.76, 1.48]`, preserving the exact rim and horizontal footprint while
creating larger-scale relief. The accepted seeded underside remains byte-identical. The six-member
structured corpus retains one connected component, zero domain-face contacts, and 88 world units of
minimum sampled clearance for every seed.

`SF-IMP-0018` is now in development on `agent/sf-imp-0018`. It tests five primary suspended-landform
families before local detail or structured relief is reintroduced: Massif, Tableland, Spine, Basin,
and Lobed. Descriptor schema 1 and graph schemas 1 through 3 remain unchanged. The first family
proof uses bounded recipe-level seed variation, a shared signed upper/underside footprint residual,
full-resolution fifteen-member topology acceptance, and a lighter fifteen-member visual atlas.

Hosted GitHub Actions validation remains temporarily unavailable because the repository's Actions
allowance is exhausted. A final complete `gradlew.bat check` remains required at each merge/release
checkpoint.

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

Linux/macOS:

```shell
./gradlew check
```

Windows:

```bat
gradlew.bat check
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
serialization. Suspended-volume intersections use schema 3 without changing either released
encoding. The seeded v0.1 recipe caps relative height modulation at 15 percent and preserves the
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
checksums. Every CI run publishes the generated directory as a downloadable workflow artifact when
hosted Actions capacity is available.

Generate the canonical signal-free suspended-volume evidence package with:

```shell
./gradlew :skyforge-reference:suspendedVolumeEvidence
```

This command evaluates 4,805,121 signed-density samples and writes canonical density and occupancy
volumes, surface grids, exact center slices, topology and air-clearance metrics, graph provenance,
six review images, an HTML guide, a manifest, and a SHA-256 listing. The images explain the exact
arrays but do not replace their numerical acceptance. The SF-IMP-0015 acceptance suite pins 19
engine-version-independent artifact hashes and exact morphology metrics; `manifest.json` and
`evidence.sha256` remain generated but are excluded from morphology identity because the manifest
contains engine-version metadata.

Generate the full-amplitude six-seed SF-IMP-0016 suspended-volume review corpus with:

```shell
./gradlew :skyforge-reference:seededSuspendedVolumeCorpus
```

On Windows use `gradlew.bat` instead of `./gradlew`. The command creates one complete evidence package
per canonical seed under `skyforge-reference/build/evidence/seeded-suspended-volume-v1`, plus a corpus
`summary.csv` and an `index.html` atlas. Each seed is rendered as isometric occupancy, upper surface,
underside, east-west and north-south sections, and suspension-plane occupancy so topology and seeded
morphology can be inspected alongside the numerical `SF-VOL-006` gate.

Generate the six-seed SF-IMP-0017 structured secondary-morphology review corpus with:

```shell
./gradlew :skyforge-reference:secondaryMorphologySuspendedVolumeCorpus
```

The output is written under
`skyforge-reference/build/evidence/secondary-morphology-suspended-volume-v1`. It intentionally uses
the same six descriptors as SF-IMP-0016 so the two atlases can be compared seed-for-seed.

Generate the first SF-IMP-0018 primary morphology-family review atlas with:

```shell
./gradlew :skyforge-reference:morphologyFamilySuspendedVolumeCorpus
```

The output is written under
`skyforge-reference/build/evidence/morphology-family-suspended-volume-v1`. It contains Massif,
Tableland, Spine, Basin, and Lobed specimens for three root seeds. The atlas uses the same world
bounds as canonical acceptance but 8-unit review spacing (`97 x 65 x 97`) to keep visual iteration
practical. The numerical family acceptance test separately samples the full canonical
`193 x 129 x 193` domain.

For Windows local development, `scripts\verify-sf-imp-0018.bat` checks Java, runs the primary-family
recipe tests, executes the fifteen-member full-resolution topology acceptance suite, and generates
the lightweight visual atlas. It does not repeat the complete repository suite; a clean
`gradlew.bat check` is reserved for the acceptance/merge checkpoint after the visual design gate
passes.

## Project record

The implementation contract, acceptance gates, source authority, work register, and risk register are maintained in [`docs/architecture/Skyforge_Architecture_Baseline_v0.1.md`](docs/architecture/Skyforge_Architecture_Baseline_v0.1.md).

The exact gate-to-test trace, corpus facts, visual-reading guide, deferrals, and release procedure
are recorded in [`docs/releases/Skyforge_v0.1.0_Release_Record.md`](docs/releases/Skyforge_v0.1.0_Release_Record.md).

The accepted implementation boundary from surface terrain to a finite mass suspended in air is defined in
[`docs/architecture/Skyforge_Architecture_Baseline_v0.2_Proposal.md`](docs/architecture/Skyforge_Architecture_Baseline_v0.2_Proposal.md).
The first signal-free suspended-volume recipe and its exact upper, underside, density, and provenance
contracts are recorded in
[`docs/decisions/ADR-0017-signal-free-suspended-volume-recipe.md`](docs/decisions/ADR-0017-signal-free-suspended-volume-recipe.md).
The canonical 3D grid, metrics, slice, checksum, and review-image formats are recorded in
[`docs/decisions/ADR-0018-suspended-volume-evidence-format.md`](docs/decisions/ADR-0018-suspended-volume-evidence-format.md).
The accepted signal-free `SF-VOL` gates, 19-artifact golden specimen, exact morphology metrics, and
`SF-VOL-006` deferral are recorded in
[`docs/decisions/ADR-0019-signal-free-suspended-volume-acceptance.md`](docs/decisions/ADR-0019-signal-free-suspended-volume-acceptance.md).
The accepted bounded seeded suspended-volume enrichment contract, six-seed acceptance corpus, and
local Java 25 validation record are recorded in
[`docs/decisions/ADR-0020-bounded-seeded-suspended-volume-enrichment.md`](docs/decisions/ADR-0020-bounded-seeded-suspended-volume-enrichment.md).
The accepted structured secondary-morphology contract and its numerical/visual acceptance boundary
are recorded in
[`docs/decisions/ADR-0021-structured-secondary-morphology.md`](docs/decisions/ADR-0021-structured-secondary-morphology.md).
The proposed five-family primary morphology proof, bounded recipe-level seed variation, and
fifteen-member acceptance/visual corpus are recorded in
[`docs/decisions/ADR-0022-primary-morphology-family-proof.md`](docs/decisions/ADR-0022-primary-morphology-family-proof.md).

## License

No license has been granted. This private development repository is not open source unless and until the owner adds an explicit license.
