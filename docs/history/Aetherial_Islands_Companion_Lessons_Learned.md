# Aetherial Islands / Aetherial Companion Lessons Learned

## Purpose

This document records the engineering inheritance from Skyforge's direct predecessor lineage:

```text
Aetherial Islands
    -> Aetherial Companion / Aetherial Control
    -> Skyforge
```

It is intended specifically to inform the Minecraft/NeoForge backend adapter and later compatibility work. It is **not** a plan to port Aetherial Companion wholesale.

The deepest inheritance rule is:

> Keep Companion's questions. Keep its compatibility lessons. Keep its diagnostic instincts. Do not keep its need to fight the terrain generator.

Skyforge should arrive at a concrete backend with the world already semantically and geometrically correct. The adapter's job is to realize that world faithfully, efficiently, and compatibly.

## Recovered artifact provenance

The recovered artifacts are present in Google Drive under the old Stab City test modlists.

### Stab City Test 1 Modlist

The smaller Test 1 set is strongly worldgen-focused and contains:

- `aetherial-islands-2.0.2.jar`
- `aetherial-islands-clusters-edit-1.2.0.jar`
- `aetherial_control-1.0.0-prototype.jar`
- Lithostitched
- Terralith
- Biomes O' Plenty
- TerraBlender
- Diabolical Islands

The Test 1 Companion artifact is 494,356 bytes with SHA-256:

`5724d2d8e793b1831ea0c2b2094cebca0b1412556d138ed45332c7ecbd0b2913`

### Stab City Test 2 Modlist

The larger Test 2 set contains the same three predecessor artifacts alongside the broader NeoForge 1.21.1 server ecosystem, including Create and numerous Create addons, Terralith, Biomes O' Plenty, Lithostitched, Chunky, Distant Horizons, and other runtime/compatibility targets.

The Test 2 Companion artifact is 498,811 bytes with SHA-256:

`22cd1a499223a7e007093a6dcb622e5572785927cad2ec95ce6f8342886ab347`

The Test 2 JAR contains every Test 1 archive entry plus 15 Create compatibility resources; no Test 1 entries are absent. Those added resources are Create compatibility notes, guarded zinc configured/placed features and biome modifier, an empty biome tag, and datapack overrides for Create's native zinc/striated ore biome modifiers.

Therefore the two files sharing the filename `aetherial_control-1.0.0-prototype.jar` must be treated as **distinct recovered prototype builds**, not as a unique binary version.

The recovered upstream artifacts inspected from Test 2 are:

- `aetherial-islands-2.0.2.jar` — 1,567,711 bytes; SHA-256 `6c1bc752b632f233b28cfd5137da2e6fb1d56fb308c8f760bc321b37038bc26f`
- `aetherial-islands-clusters-edit-1.2.0.jar` — 866,344 bytes; SHA-256 `9d62d2c68418aace996849a27c19f25e1cc8f0ce6af8518d3550795775711e48`

## What Aetherial Islands actually represented

The recovered `aetherial-islands-2.0.2.jar` is heavily data-driven through Minecraft density functions. It contains five vertical island layers, each with:

- `noise.json`
- `depth_top.json`
- `depth_bottom.json`
- `offset_top.json`
- `offset_bottom.json`

The five layer noise graphs all sample the same `aetherial_islands:islands` shifted-noise field with `xz_scale = 0.85`, `y_scale = 0`, and a `-0.425` offset. Their coordinate shifts are:

- layer 1: `0`
- layer 2: `+4000`
- layer 3: `-4000`
- layer 4: `+8000`
- layer 5: `-8000`

This is elegant as an implicit procedural generator. It is poorly suited to authoring semantic relationships such as:

- these islands belong to one cluster;
- these islands must not stack;
- this region has one dominant island and several satellites;
- oceanic islands are sparse but deliberately distributed;
- this structure requires a sufficiently thick island beneath it.

The architectural lesson is not that noise is bad. The lesson is that **noise should supply controlled variation rather than carry semantic identity or composition**.

## Companion's conceptual advance

The recovered Companion prototype identifies itself in NeoForge metadata as:

> Prototype companion mod for descriptor-driven Aetherial Islands control.

Its compiled classes confirm that it was already moving toward explicit semantic world objects.

`ClusterDescriptor` carries:

- cluster ID;
- cell coordinates;
- center;
- radius;
- ocean-cluster flag.

`IslandDescriptor` carries:

- island ID;
- cluster ID;
- center X/Y/Z;
- X/Z radii;
- vertical band;
- type;
- size class;
- archetype;
- placement fallback state and note.

`IslandField` exposes random-access semantic queries:

```text
generateAround(seed, x, z, radius)
nearest(seed, x, y, z)
contains(seed, x, y, z)
nearby(seed, x, y, z, radius)
presenceAt(seed, x, y, z)
```

This idea survives directly in modern Skyforge's world catalog/query boundary:

```text
coordinate / region
    -> spatial lookup
    -> relevant semantic world objects
    -> evaluate only required procedural fields
```

## What should be preserved strongly

### Explicit island and group identity

If the designer cares about an object or relationship semantically, represent it explicitly upstream.

Modern equivalents include:

- independently identified islands;
- explicit group/archipelago membership;
- deterministic nested identity;
- morphology/provider identity;
- composition roles and reservations.

The backend must consume these identities rather than rediscover them from density or blocks.

### Deterministic random-access world queries

Companion's `IslandField` and caches established the right operational question: **what semantic object exists here?**

Skyforge's `SkyIslandWorldCatalog.query(WorldBounds)` is the mature continuation of that instinct.

### Simple authoring controls over deeper parameters

`CompanionWorldSettings` exposed higher-level controls such as preset, world shape, stacking protection, ocean mode, structure safety, island density, cluster size/spacing, island-size bias, ocean frequency, anti-stacking strength, starter search radius, and debug mode. A resolver translated those into lower-level runtime values.

Preserve the pattern:

```text
AUTHOR INTENT
    -> friendly semantic controls
    -> resolver/compiler
    -> advanced internal parameters
```

Do not require ordinary users to tune procedural mathematics directly.

### Diagnostic culture

The prototype contains a broad suite of audit/report/validation tools, including native density audits, authority reports, configuration sweeps, control-volume reports, surface-region mapping, staged terrain validation, starter search, and headless batch execution.

Skyforge should preserve this instinct. Long-term backend diagnostics should make it possible to trace:

```text
Minecraft output
    -> backend decision
    -> terrain semantic / suitability
    -> world volume
    -> compiled field / recipe
    -> descriptor
    -> semantic ancestor
```

## What should be preserved as a requirement but redesigned

### Vertical stacking

Companion correctly treated stacking as a first-class problem, with controls for vertical bands, vertical clearance, horizontal overlap, anti-stacking attempts/strength, and stack allowance above a clearance threshold.

The problem was the mechanism: candidate generation followed by violation checks, rejection, retry, and fallback. As density and constraints interact, public controls become nonlinear and coupled.

Skyforge replacement:

> Composition determines where islands may exist. Morphology determines what those islands look like.

Stacking should be a composition relationship/policy, not a density correction and not primarily retry-driven placement.

### Island/cluster density and topology

The predecessor's candidate/retry machinery made parameters such as density, spacing, overlap and clearance interact nonlinearly.

Skyforge public controls should be monotonic where appropriate, locally influential where possible, and statistically testable over deterministic corpora.

Examples of future authoring tests:

```text
requested mean membership -> observed mean within tolerance
requested stacking rate   -> observed stacking rate within tolerance
requested ocean occupancy -> observed occupancy within tolerance
```

Implementation mechanics such as retry counts must not become de facto world-design parameters.

### Ocean prevalence

Companion recognized both ocean-cluster and ocean-island prevalence as meaningful controls, but represented them through multiple probabilities, filters and later biome remapping.

Skyforge should treat deliberately sparse/coherent oceanic occurrence as a composition problem if and when Skyforge itself needs to own it. Ordinary Minecraft biome/climate state remains backend-owned unless a backend-independent Skyforge behavior requires more.

### Structure safety

The predecessor had explicit structure-safety policy, special handling for mineshafts, trial chambers, ancient cities and strongholds, and Lithostitched modifiers for surface/ocean/progression-critical structures.

The requirement survives. The mechanism should be rebuilt around actual Skyforge geometry rather than corrective control volumes imposed on another generator.

## Suitability is a direct inheritance

`IslandAuthority` and `IslandFeatureEligibility` are among the most useful predecessor concepts.

A valid Minecraft biome does not imply that a floating geological volume can safely host a feature or structure.

Skyforge should eventually provide backend-neutral **suitability** queries derived from authoritative geometry, such as:

- terrain thickness;
- distance to upper surface;
- distance to underside;
- surface availability;
- slope/exposure where continuous derivative support exists;
- soil/surface-mantle depth where defined;
- stable island/group identity;
- structure- or feature-specific suitability.

This is intentionally not part of SF-IMP-0029's first structural-semantic proof. It is a high-priority backend concern once feature/structure integration begins.

The old term `Authority` does not need to survive. The useful abstraction is **eligibility/suitability**.

## Preserve Minecraft contracts where practical

Companion's `OceanBiomeRemapBiomeSource` wrapped Minecraft's `BiomeSource` instead of replacing the whole biome ecosystem. Its public API includes the wrapped source, possible biomes and `getNoiseBiome(...)`.

This supports the modern rule:

> Preserve backend-native contracts wherever they can express the desired Skyforge result without compromising upstream world meaning.

Skyforge should not reinvent basic vegetation placement, ordinary biome APIs, mob spawning, every decorative feature, or every ore system by default.

The backend should use native Minecraft/NeoForge systems where compatible and intervene only where floating-island geometry changes the validity assumptions.

## Compatibility evidence from Create

The later Test 2 Companion build adds explicit Create compatibility resources.

Bundled notes state that Create native worldgen was expected to be disabled, or its native ore biome modifiers overridden to no-op, and Companion then re-added zinc through guarded configured/placed features and a biome modifier.

The reason is instructive: Create's ordinary worldgen assumptions were not appropriate for the Aetherial floating-island environment.

Modern rule:

> Preserve native mod behavior when valid. When invalid, adapt the integration boundary rather than patch arbitrary internals.

Do **not** automatically reproduce the Create workaround in Skyforge. First test whether Skyforge's geometry/material pipeline actually creates the same problem.

## Registry probing is a backend requirement

The recovered `environment_backend_notes.txt` records a concrete failure mode: static optional Terralith/Biomes O' Plenty cleanup was deliberately not shipped because registry IDs differed from an earlier audit, and stale optional JSON could crash world creation with unbound placed-feature/carver IDs even when the mod itself was installed.

This becomes an explicit Skyforge backend rule:

```text
mod presence
    -> inspect active registry/tags/capabilities
    -> validate target key
    -> enable optional compatibility behavior
```

Not:

```text
modLoaded(X)
    -> assume old registry key Y still exists
```

Optional integration must fail gracefully.

## Correct lesson from biome adaptation

Companion used biome remapping partly because it lacked authority over the upstream terrain generator. Skyforge should not use biome suppression/remapping to manufacture geography.

The backend may still bridge/remap biome identity as a **representation** decision.

This is compatible with ADR-0034's minimal-context rule:

- Skyforge does not predeclare a broad parallel climate/ecology model;
- native Minecraft biome/climate information remains authoritative where appropriate;
- genuinely Skyforge-specific environment semantics can be added later if a backend-independent behavior requires them.

Therefore the predecessor recommendation `Skyforge climate/ecology -> Minecraft biome` is retained only as a possible future shape, **not** as a current required architecture.

## Patterns explicitly rejected as foundations

### Noise as the primary semantic authoring interface

If a designer cares about a cluster, island, stacking relationship, province, ridge, valley, geology object, or other world concept, that concept should normally exist explicitly.

### Retry count as a design parameter

Retries may exist internally. Changing retry limits must not materially redefine intended world composition.

### Post-hoc semantic repair

Reject patterns such as:

```text
too much stacking -> distort/mask density
wrong regional frequency -> suppress terrain afterward
wrong cluster distribution -> repeatedly reject candidates
wrong vertical layer -> add corrective backend density gate
```

Fix a semantic failure at the highest semantic layer that actually owns it.

### Backend-owned world meaning

The concrete backend does not decide what constitutes an island, group, morphology family, ridge, or other upstream Skyforge identity.

### Combinatorial Minecraft biome explosion

Do not create a Minecraft biome for every combination of richer Skyforge state. Preserve independently expressible geology/morphology/terrain semantics and project them onto a manageable backend biome vocabulary.

### Static third-party registry assumptions

Never assume optional registry IDs merely from mod presence.

### Corrective machinery in core

Companion needed masks, reducers, presence gates, layer gates and permission fields because it adapted another terrain generator. Skyforge itself is the terrain authority and should not reproduce this architecture internally.

## Backend implications

The current Minecraft adapter proposal should inherit the following responsibilities when their integration stages arrive:

```text
MINECRAFT / NEOFORGE ADAPTER
├─ density / geometry bridge
├─ terrain semantic + material bridge
├─ native biome bridge where useful
├─ feature suitability bridge
├─ structure suitability bridge
├─ active-registry compatibility layer
├─ optional-mod adapters
└─ debug / provenance tools
```

Not all of these belong in the first chunk-writing proof. They are staged responsibilities, not requirements to overbuild the initial adapter.

## Modern Skyforge inheritance principle

The predecessor lessons can be summarized as:

> World rules control occurrence and relationships.  
> Descriptors control identity.  
> Recipes control form.  
> Fields control realization.  
> The Minecraft backend controls representation.

Examples:

```text
"May these islands stack?"           -> composition
"How many islands form this group?"  -> composition
"What object is this?"               -> descriptor / identity
"What shape does it have?"           -> morphology recipe
"How rough is this local surface?"   -> field/signal
"Which block represents it?"         -> backend material bridge
"Which biome communicates it?"       -> backend biome bridge
"Can a village fit here?"            -> suitability + structure bridge
```

Aetherial Companion is best treated as an experimental record of where an implicit floating-island generator became difficult to control. Skyforge's job is to make those desired controls native upstream while retaining the compatibility knowledge and diagnostics learned downstream.
