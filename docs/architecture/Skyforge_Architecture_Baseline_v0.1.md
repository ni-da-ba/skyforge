# Skyforge Architecture Baseline v0.1

**Document ID:** SF-BASE-0001
**Status:** Proposed baseline for implementation
**Date:** 2026-08-03
**Project phase:** Sprint Zero
**Owner:** Nicholas
**Change rule:** A decision marked *Accepted* is binding until superseded by a later decision record. A decision marked *Provisional* may be changed when implementation evidence justifies it.

## 1. Purpose

This document converts the existing Skyforge doctrine into an implementation-ready baseline. It establishes the source record, project vocabulary, architectural boundaries, first proof, measurable acceptance criteria, and immediate work order.

Skyforge v0.1 will prove one claim:

> A semantic island descriptor can compile into an inspectable procedural graph that deterministically produces a recognizable island independently of Minecraft.

This is a proof of architecture, not a content-complete world generator.

## 2. Source record and authority

### 2.1 Supplied sources

1. **Skyforge Volume I Engineering Summary** - the operative summary of established philosophy, abstractions, and engineering decisions.
2. **Skyforge Volume II Commencement Brief** - the mandate and agenda for software architecture.
3. **Skyforge Volume I - The Theory of Procedural Worlds, First Edition (Draft)** - a one-page table of contents and editorial statement for a future full manuscript.

### 2.2 Authority rule

- The Engineering Summary controls statements of established doctrine.
- The Commencement Brief controls the intended scope of Volume II and the kernel/backend relationship.
- This baseline controls the initial software contract once accepted.
- The supplied First Edition Draft identifies intended subject matter but does not contain enough prose to settle detailed engineering questions.
- Code, tests, and benchmarks supply implementation evidence; they do not silently amend doctrine. A conflicting result must be recorded as a decision or issue.

### 2.3 Maturity statement

At the start of this baseline:

- Conceptual foundation: strong.
- Architectural direction: strong but incomplete.
- Software contracts: newly proposed here.
- Implementation: not yet begun.
- Empirical validation: not yet begun.

## 3. Doctrine carried forward

The following principles are *Accepted* because they are explicit in the source record:

- Skyforge is a procedural world synthesis engine; Minecraft is its first backend, not its foundation.
- The engine evaluates procedural fields rather than generating chunks as its core abstraction.
- Procedural graphs, not fixed pipelines, are the execution model.
- Nodes are deterministic, stateless, composable, immutable, inspectable, and independently testable.
- Descriptors express meaning; recipes translate meaning into mathematical construction; graphs express evaluation.
- Primary morphology establishes identity; secondary morphology and signals enrich it.
- Signals must not replace morphological identity.
- World meaning is organized through the hierarchy: World -> Province -> Cluster -> Island -> Primary Morphology -> Secondary Morphology -> Signals -> Materials -> Blocks.
- The mathematical kernel must contain no Minecraft or NeoForge dependency.
- Optimization should primarily operate on graphs and must preserve reference results.
- Geological systems model visible consequences rather than attempting a literal geological-time simulation.
- Results and subsystems should be explainable in semantic, mathematical, and geological terms.

## 4. Vocabulary

**Backend**
A consumer that realizes Skyforge outputs in another environment. Minecraft/NeoForge is the first planned backend.

**Constraint**
A declared condition used to accept, reject, penalize, mask, blend, or select a construction. The exact constraint modes remain deferred beyond the first island.

**Descriptor**
Immutable semantic input that states what a world feature means and which meaningful parameters it possesses. It does not prescribe a generation algorithm.

**Evaluation context**
Explicit immutable data supplied during sampling, including coordinates, versioned evaluation rules, and any declared external services. Hidden global state is forbidden.

**Field**
A deterministic mapping from a coordinate domain to a typed value. v0.1 implements continuous scalar fields over 2D horizontal space and 3D space.

**Graph**
A typed, directed, acyclic structure of immutable nodes representing field evaluation.

**Identity**
The measurable large-scale form that makes a morphology recognizable across allowed signal and parameter variation.

**Kernel**
The backend-neutral mathematical runtime: coordinate types, field types, graph representation, validation, and reference evaluation.

**Morphology**
The meaningful geometric form of a landform. Primary morphology establishes its large-scale structure; secondary morphology modifies or enriches it.

**Node**
An immutable typed graph operation with explicit inputs, parameters, output type, and serialization form.

**Recipe**
A deterministic compiler from one or more semantic descriptors into a procedural graph.

**Sampler**
A consumer that evaluates a field at declared coordinates or over a declared grid.

**Signal**
Deterministic, seeded variation applied within explicit amplitude and scale limits.

**Vertical slice**
A minimal end-to-end proof crossing semantic description, recipe compilation, graph evaluation, sampling, visualization, and validation.

## 5. Scope of v0.1

### 5.1 In scope

- Java implementation using a Gradle multi-module build.
- Backend-neutral kernel.
- Immutable scalar fields over 2D and 3D coordinates.
- Typed acyclic procedural graphs.
- A deliberately simple reference evaluator.
- Graph validation and canonical serialization.
- Minimal semantic island descriptor and one island recipe.
- Neutral raster exports: height, land/sea mask, slope, and two cross-sections.
- Numerical statistics, graph inspection, and repeatability checksum.
- Automated tests for determinism, graph integrity, analytical correctness, architecture, and island morphology.

### 5.2 Explicitly deferred

- NeoForge runtime integration, chunks, blocks, biomes, and datapacks.
- Provinces and clusters beyond placeholder provenance in the first evidence package.
- Multiple island archetypes.
- Climate, ecology, materials, caves, aquifers, ores, structures, and decoration.
- Graph optimization beyond interfaces and differential-test scaffolding.
- Adaptive sampling, caching, GPU execution, and distributed execution.
- Cross-version bitwise compatibility.
- A general constraint solver.
- Runtime editing and authoring UI.

## 6. Technical baseline

### ADR-001 - Language and build

**Status:** Provisional
**Decision:** Use Java with Gradle Kotlin DSL and a checked-in Gradle wrapper. Use a Java toolchain rather than the developer machine's ambient JDK.
**Reason:** Java provides a direct path to the planned Minecraft backend while allowing the engine to remain ordinary backend-neutral Java. The exact language level will be pinned during repository scaffolding after the first backend compatibility check.

### ADR-002 - Initial module boundaries

**Status:** Provisional

| Module | Owns | May depend on |
|---|---|---|
| `skyforge-kernel` | coordinates, field interfaces, graph model, node set, validation, reference evaluator, canonical graph format | Java standard library only at first |
| `skyforge-model` | semantic descriptors and descriptor validation | Java standard library only at first |
| `skyforge-recipes` | descriptor-to-graph compilation and first island recipe | `skyforge-kernel`, `skyforge-model` |
| `skyforge-reference` | CLI, grid sampling, image/report export, fixed seed corpus | the three engine modules plus narrowly chosen output libraries |
| `skyforge-neoforge` | future Minecraft realization | engine modules and NeoForge; not present in v0.1 |

Forbidden dependencies are tested. In particular, `skyforge-kernel`, `skyforge-model`, and `skyforge-recipes` may not import Minecraft or NeoForge packages.

### ADR-003 - Coordinate contract

**Status:** Provisional

- Kernel coordinates are continuous IEEE-754 binary64 values.
- The coordinate system is right-handed: `x` and `z` span the horizontal plane and `y` is up.
- `Field2<T>` samples `(x, z)`; `Field3<T>` samples `(x, y, z)`.
- Distances use abstract world units. Backend conversion to blocks or meters is outside the kernel.
- Angles are radians unless a descriptor property explicitly states otherwise.
- Sea level for the first island is `y = 0`.
- Non-finite coordinates and descriptor values are rejected at public boundaries.
- The first height field is `H(x,z)`. Its derived solid-density field is `D(x,y,z) = H(x,z) - y`, with positive values inside solid terrain, zero on the surface, and negative values outside.

### ADR-004 - Determinism contract

**Status:** Accepted by ADR-0012 for v0.1

For the same Skyforge version, canonical descriptor, root seed, graph, and coordinate set:

1. Repeated evaluation returns identical raw binary64 output bits.
2. Sequential, shuffled, batched, and parallel sampling produce the same value for every coordinate.
3. Results do not depend on thread identity, wall-clock time, locale, unordered collection iteration, filesystem ordering, or process-global random state.
4. Every seeded operation derives its local seed from a root seed plus a stable semantic namespace. Seed derivation is centralized and versioned.
5. Canonical graph serialization produces identical bytes for structurally identical graphs.
6. A graph that is serialized and reloaded evaluates identically to its source graph.

Not promised in v0.1: identical results across Skyforge versions, arbitrary JVM implementations, CPU architectures, or changed numerical modes. Any future compatibility promise must be explicit and tested.

ADR-0008 accepts canonical graph JSON, and ADR-0012 accepts semantic seed derivation version 1 plus
the first bounded signal family. Cross-version bitwise compatibility remains outside v0.1.

### ADR-005 - Graph contract

**Status:** Provisional

- A graph is typed and acyclic.
- Every node declares a stable node-kind identifier, output type, ordered inputs, and immutable parameters.
- Node equality is structural, not based on object identity.
- Construction rejects cycles, missing inputs, type mismatches, unknown node kinds, and non-finite parameters.
- v0.1 node kinds are limited to constants, coordinates, arithmetic, minimum/maximum, clamp, remap, smooth minimum/maximum, distance primitives, affine coordinate transforms, and one versioned seeded signal family.
- The reference evaluator favors clarity and correctness over speed.
- Later compiled or optimized evaluators must pass differential tests against the reference evaluator.
- Graph serialization is versioned, human-readable, and canonical for checksum purposes.

### ADR-006 - Semantic hierarchy in v0.1

**Status:** Provisional
**Decision:** The hierarchy is a semantic provenance requirement, not a requirement to preserve every layer as a runtime object after compilation. The first evidence package records World, Province, Cluster, and Island provenance even though only the Island has meaningful generation behavior. A compiler may fuse or eliminate intermediate calculations while retaining inspectable provenance.

## 7. First island specification

### 7.1 Descriptor v0.1

The initial `IslandDescriptor` contains only values needed to test semantic control:

| Property | Meaning | Initial validation |
|---|---|---|
| `schemaVersion` | descriptor contract version | exactly the supported v0.1 value |
| `seed` | root source of controlled variation | any 64-bit pattern |
| `centerX`, `centerZ` | island center in world units | finite |
| `nominalRadius` | intended horizontal scale | finite and greater than zero |
| `maximumElevation` | intended height above sea level | finite and greater than zero |
| `coastalFalloff` | width/character of transition to sea | finite, positive, and bounded relative to radius |
| `ridgeAzimuth` | principal ridge direction | finite radians, canonicalized |
| `ridgeStrength` | semantic prominence of the principal ridge | normalized to `[0,1]` |
| `signalAmplitude` | permitted small-scale displacement | normalized and capped relative to radius/elevation |
| `signalScale` | characteristic variation scale | finite and positive |

The descriptor must not contain graph node names, noise algorithms, interpolation methods, or backend block concepts.

### 7.2 Recipe obligations

The first recipe must:

- create a closed island above sea level;
- establish form without a seeded signal;
- express radius, elevation, coastal falloff, ridge direction, and ridge strength monotonically enough to test;
- add the seeded signal only as bounded enrichment;
- emit an inspectable height graph and derived density graph;
- record recipe and graph schema versions;
- be deterministic and free of backend dependencies.

### 7.3 Standard evidence grid

Unless an acceptance test specifies otherwise:

- Sample a square whose half-width is `1.5 * nominalRadius` around the descriptor center.
- Use a `1024 x 1024` grid for golden evidence and a smaller fast grid for unit tests.
- Produce horizontal height, mask, and slope rasters.
- Produce east-west and north-south cross-sections through the descriptor center.
- Record descriptor, graph, sampling bounds, resolution, engine version, statistics, and checksums alongside outputs.

## 8. Acceptance gates

### 8.1 Kernel gate

| ID | Requirement | Passing evidence |
|---|---|---|
| SF-KER-001 | Analytical correctness | primitive and composed fields match known analytical values within declared tolerances |
| SF-KER-002 | Order-independent determinism | fixed corpus matches raw-bit results under sequential, reversed, shuffled, batched, and parallel sampling |
| SF-KER-003 | Graph integrity | cycles, type errors, missing inputs, unknown node kinds, and non-finite parameters are rejected |
| SF-KER-004 | Round-trip identity | canonical serialize/reload preserves structure and all sampled values |
| SF-KER-005 | Backend independence | automated dependency test finds no Minecraft/NeoForge dependency in engine modules |
| SF-KER-006 | Inspectability | graph dump exposes node kinds, parameters, types, inputs, schema version, and semantic provenance |

### 8.2 Island gate

| ID | Requirement | Passing evidence |
|---|---|---|
| SF-ISL-001 | Closed landform | signal-free land/sea mask contains one connected land component and no land touches the evidence-grid boundary |
| SF-ISL-002 | Bounded elevation | all sampled heights are finite and lie within the recipe's declared bounds |
| SF-ISL-003 | Scale control | increasing nominal radius increases measured land area and shoreline extent without changing center |
| SF-ISL-004 | Elevation control | increasing maximum elevation increases peak and high-percentile elevation without materially changing footprint |
| SF-ISL-005 | Ridge control | changing ridge azimuth rotates the principal elevation axis by the corresponding amount within a declared tolerance |
| SF-ISL-006 | Signal neutrality | zero signal amplitude exactly reproduces the base morphology |
| SF-ISL-007 | Identity preservation | across the fixed signal-seed suite, connectedness, centroid, area, peak range, and principal-axis metrics remain within declared envelopes |
| SF-ISL-008 | Density consistency | the zero set of `D(x,y,z)` agrees with `H(x,z)` at sampled columns within numerical tolerance |
| SF-ISL-009 | Explainable output | evidence report links every descriptor property to graph substructure and measured effects |

ADR-0011 records the passing signal-free envelopes for `SF-ISL-001` through `SF-ISL-006`,
`SF-ISL-008`, and `SF-ISL-009`. ADR-0012 executes `SF-ISL-007` across the fixed seed suite and
preserves the exact base land mask by construction.

### 8.3 Sprint One completion gate

Sprint One is complete only when all kernel and island requirements above pass for a versioned fixed corpus and the evidence package can be regenerated by one documented command from a clean checkout.

Visual attractiveness alone is not a passing condition. A valid checksum alone is not a passing condition. Both semantic behavior and mathematical repeatability are required.

## 9. Evidence and test policy

- **Unit tests:** node values, validation rules, descriptor constraints, and recipe behavior.
- **Property tests:** generated coordinates and parameter combinations checked against bounds and invariants.
- **Metamorphic tests:** predictable changes under translation, scale, elevation, azimuth, and zero-amplitude signal transformations.
- **Determinism tests:** order, batching, process repetition, and thread-count changes.
- **Differential tests:** every future optimized evaluator against the reference evaluator.
- **Golden corpus:** canonical descriptors, graphs, grids, statistics, checksums, and images for a small fixed seed suite.
- **Architecture tests:** dependency rules and package-cycle checks.
- **Benchmarks:** recorded from the first working evaluator, then used to detect regressions; initial benchmarks are observations, not arbitrary pass/fail targets.
- **Visual review:** standardized images assessed against named criteria and retained with the numerical evidence.

Every bug involving determinism or a violated invariant receives a regression test before closure.

## 10. Work register

### 10.1 Sprint Zero - bookkeeping

- [x] Inventory supplied project sources.
- [x] Distinguish operative doctrine from editorial outline.
- [x] Record maturity honestly.
- [x] Establish vocabulary.
- [x] Define v0.1 scope and explicit deferrals.
- [x] Propose module, coordinate, determinism, graph, and hierarchy contracts.
- [x] Define the first island descriptor and recipe obligations.
- [x] Define acceptance gates and evidence policy.
- [ ] Nicholas accepts or amends this baseline.
- [x] Choose repository host, visibility, ownership, and private-development license posture.
- [x] Choose the durable Java package namespace.
- [x] Pin Java and Gradle after compatibility checks.
- [x] Pin JUnit when the first test ticket requires it.
- [x] Accept the canonical graph serialization algorithm in ADR-0008; no general serialization library is required.
- [x] Resolve image output for the first evidence ticket: use the Java standard PNG encoder and
  introduce no third-party image dependency (ADR-0010).
- [x] Accept seed derivation version 1 and the first bounded signal family in ADR-0012.

### 10.2 Sprint One - implementation order

1. Create the repository and Gradle modules with dependency-enforcement tests.
2. Add continuous integration for build and test on a clean environment.
3. Implement coordinate values, scalar field interfaces, and finite-value validation.
4. Implement the minimal graph model, node types, cycle/type validation, and reference evaluator.
5. Implement canonical graph serialization and round-trip tests.
6. [x] Implement the signal-free island descriptor and recipe.
7. [x] Implement the reference sampler, raster outputs, statistics, and evidence manifest.
8. [x] Pass every currently applicable signal-free island gate and pin its golden corpus.
9. [x] Accept and implement stable seed derivation plus one signal family.
10. Pass the full fixed-seed corpus and publish the first benchmark baseline.

## 11. Remaining open decisions

Future decisions should not be guessed because they govern ownership, compatibility, or long-term
identity.

SF-OPEN-001 through SF-OPEN-004 are resolved by ADR-0007. SF-OPEN-006 is resolved for graphs by
ADR-0008. SF-OPEN-005 is resolved by ADR-0012. Descriptor serialization is implemented narrowly by
the evidence manifest; a future general descriptor codec remains deferred. A future public
distribution requires a new explicit license decision.

## 12. Risk register

| Risk | Early warning | Control |
|---|---|---|
| Architectural overreach | many abstractions exist before one island renders | require the vertical-slice completion gate before expanding domain scope |
| Noise substitutes for morphology | seed changes erase silhouette or ridge structure | implement signal-free form first and enforce identity metrics |
| Backend leakage | kernel APIs mention chunks, blocks, biomes, registries, or NeoForge classes | dependency tests and module review |
| Unspecified determinism | tests pass locally but differ by traversal or thread count | raw-bit corpus and reordered/parallel tests |
| False canon | implementation relies on concepts named but not explained in the supplied outline | source authority rule and explicit decision records |
| Premature optimization | complex caches/compiler obscure reference behavior | reference evaluator is normative; optimize only with differential tests |
| Validation by appearance | attractive screenshots hide broken semantics | numerical, property, metamorphic, and provenance gates |

## 13. Change control

New decisions use a stable identifier and record status, context, decision, consequences, and superseded decisions. Acceptance criteria may become stricter without changing a released result; any relaxation requires an explicit rationale and versioned baseline update.

The next document version should be `v0.2` only after the open identity/toolchain decisions are resolved or implementation evidence changes one of the provisional contracts.

## 14. Immediate next ticket

**Ticket:** SF-IMP-0009 - Fixed-seed evidence corpus and benchmark baseline
**Objective:** Publish canonical evidence for the accepted fixed seed suite and measure the
deliberately simple reference evaluator before optimization.
**Done when:**

- every fixed seed produces a versioned canonical evidence package with pinned descriptor, graph,
  grid, cross-section, statistics, and morphology checksums;
- corpus regeneration is one documented command and fails on any unexpected drift;
- reference evaluation wall time and throughput are recorded with environment metadata as
  observations rather than arbitrary pass/fail targets;
- Sprint One's complete fixed-corpus gate passes from a clean Java 25 checkout.
