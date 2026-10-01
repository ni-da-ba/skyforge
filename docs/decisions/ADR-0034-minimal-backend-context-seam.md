# ADR-0034: Minimal Backend Context Seam

- **Status:** Accepted
- **Date:** 2026-08-31
- **Work item:** SF-IMP-0030

## Context

SF-IMP-0029 accepted backend-neutral structural terrain semantics such as `SURFACE_MANTLE`, `EDGE_SHELL`, `UNDERSIDE_SHELL`, `SHALLOW_INTERIOR`, and `DEEP_MASS`. A concrete backend must eventually convert those meanings into concrete terrain materials.

It would be easy to respond by adding a broad Skyforge climate, biome, geology, or material-intent model. That would be premature. Minecraft and other world backends may already provide environmental systems that should remain authoritative for backend-native concepts such as biome, temperature, precipitation, vegetation, or block palettes.

The recovered Aetherial Islands / Aetherial Companion lineage reinforces the need for backend compatibility seams, but it does not require Skyforge to predeclare every environment concept Companion once had to manipulate.

## Decision

SF-IMP-0030 introduces only the smallest context seam demonstrated necessary by the first backend-policy proof:

```text
Skyforge geometry
    -> Skyforge terrain semantic
    -> adapter-visible Skyforge context
    -> backend-native environmental/material policy
    -> concrete backend material
```

Skyforge does not own a general climate simulator merely to support backend material selection.

## Ownership rule

A concept belongs in Skyforge only when it is required to express backend-independent Skyforge behavior.

Examples of intrinsically Skyforge information include:

- structural terrain semantic;
- stable world-volume identity where actually needed;
- group or archipelago identity where actually needed;
- morphology-derived information that a backend cannot infer safely;
- explicitly authored Skyforge-specific world zones or phenomena, if later required.

Examples that should normally remain backend-owned include:

- Minecraft biome identity;
- Minecraft temperature/downfall values;
- Minecraft feature and vegetation rules;
- concrete block palettes and block states;
- backend-specific decoration and replacement rules.

## No speculative climate descriptor set

SF-IMP-0030 does not introduce temperature, humidity, rainfall, continentalness, erosion, ecology, or similar generic environmental descriptors.

A Minecraft adapter may consult native biome/environment information directly and combine it with the Skyforge structural semantic.

## No speculative MaterialIntent taxonomy

SF-IMP-0030 does not introduce a backend-neutral `MaterialIntent` vocabulary.

The accepted proof works directly from:

```text
SkyIslandTerrainSemantic
+ world position
+ backend-owned environmental context
-> backend-owned material token
```

Reference material tokens live only in `skyforge-reference`; they are not Skyforge world semantics or a public material ontology.

An intermediate material-intent abstraction should be added only if concrete integration demonstrates a transformation that is genuinely shared across backends.

## First proof context shape

The accepted implementation adds `SkyIslandTerrainSampleContext` with exactly:

- world-space `x`, `y`, `z`;
- accepted `SkyIslandTerrainSemantic`.

`WorldRegionTerrain.sampleContextAt(...)` exposes this context from an accepted sampled semantic region.

The proof deliberately does not add `SkyIslandWorldVolumeId`, group role, archipelago role, climate values, biome values, suitability, or backend registry information to every sample.

Stable island/group identity remains available at world-catalog level and should be promoted into the per-sample seam only when a concrete backend behavior demonstrates that the hot path needs it.

## Suitability is separate from climate

The predecessor strongly supports introducing geometry-derived suitability before broad feature/structure integration. That does not imply a climate model.

Suitability may later be derived from authoritative Skyforge geometry and structural semantics, for example terrain thickness, distance to surface/underside, available surface, or continuous slope/exposure. A Minecraft adapter can combine this backend-neutral geometric validity with native biome, tag, feature, and structure rules.

## Dependency direction

Backend context must not reverse the accepted dependency direction:

```text
skyforge-kernel
      ^
skyforge-model
      ^
skyforge-recipes
      ^
skyforge-world
      ^
concrete backend adapter
```

`skyforge-world` may define backend-neutral contracts. It may not import Minecraft, NeoForge, Fabric, or other backend APIs.

## Reference backend proof

The first non-Minecraft proof supplies a tiny backend-owned environment with two states and maps the same `SURFACE_MANTLE` sample to two different reference-only material tokens.

The proof requires:

- backend-owned environmental input may change representation;
- the Skyforge sample context remains unchanged;
- every terrain semantic maps to a representation with the same AIR/solid occupancy;
- repeated identical inputs are deterministic.

The focused verifier passed locally on 2026-08-31, followed by a repository-wide `gradlew.bat check` PASS. SF-IMP-0030 was merged without file-content drift in PR #30.

No visual gate was required because SF-IMP-0030 changes only the adapter-context contract and reference representation policy; it does not alter accepted geometry or terrain semantics.

## Accepted criteria

SF-IMP-0030 demonstrates:

1. no Minecraft/NeoForge classes enter `skyforge-world`;
2. no generic Skyforge climate descriptor set is introduced;
3. no broad abstract material taxonomy is introduced;
4. backend-visible context contains only world position and accepted terrain semantic for the first proof;
5. invalid/non-finite sample contexts fail early;
6. sampled `WorldRegionTerrain` exposes exact world coordinates and semantic identity;
7. changing backend-native environmental input changes reference representation without changing Skyforge context;
8. the same Skyforge context is consumed by a non-Minecraft reference adapter;
9. reference material policy preserves AIR/solid occupancy for every semantic and backend environment;
10. identical Skyforge/backend inputs produce deterministic representation.

## Deferred work

This ADR does not define:

- concrete block mappings;
- persistent biome/material caches;
- custom Skyforge climate simulation;
- cross-backend material-intent taxonomy;
- per-sample world-volume/group identity;
- province/geology/ecology descriptor promotion;
- suitability fields;
- vegetation, structures, caves, ores, or fluids.

These remain evidence-driven integration work.

## Guiding principle

> Skyforge owns the concepts necessary to express Skyforge. A backend owns concepts that exist only because of that backend. Shared abstractions are introduced only after a concrete integration demonstrates that they are genuinely shared.
