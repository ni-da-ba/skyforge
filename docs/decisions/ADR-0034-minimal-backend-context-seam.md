# ADR-0034: Minimal Backend Context Seam

- **Status:** Implementation prepared; focused local validation pending
- **Date:** 2026-08-31
- **Work item:** SF-IMP-0030

## Context

SF-IMP-0029 accepts backend-neutral structural terrain semantics such as `SURFACE_MANTLE`, `EDGE_SHELL`, `UNDERSIDE_SHELL`, `SHALLOW_INTERIOR`, and `DEEP_MASS`. A concrete backend must eventually convert those meanings into concrete terrain materials.

It would be easy to respond by adding a broad Skyforge climate, biome, geology, or material-intent model. That would be premature. Minecraft and other world backends may already provide environmental systems that should remain authoritative for backend-native concepts such as biome, temperature, precipitation, vegetation, or block palettes.

Skyforge should remain backend-neutral without duplicating backend responsibilities.

The recovered Aetherial Islands / Aetherial Companion lineage reinforces the need for backend compatibility seams, but it does **not** require Skyforge to predeclare every environment concept Companion once had to manipulate. The predecessor adapted another terrain generator and therefore mixed semantic intent with biome remapping, density gating and compatibility correction in ways modern Skyforge should avoid.

## Decision

SF-IMP-0030 introduces only the smallest context seam demonstrated necessary by the first backend-policy proof.

The architectural boundary is:

```text
Skyforge geometry
    -> Skyforge terrain semantic
    -> adapter-visible Skyforge context
    -> backend-native environmental/material policy
    -> concrete backend material
```

Skyforge does **not** own a general climate simulator merely to support backend material selection.

## Ownership rule

A concept belongs in Skyforge only when it is required to express backend-independent Skyforge behavior.

Examples of intrinsically Skyforge information include:

- structural terrain semantic;
- stable world-volume identity;
- group or archipelago identity where actually needed;
- morphology-derived information that a backend cannot infer safely;
- explicitly authored Skyforge-specific world zones or phenomena, if later required.

Examples that should normally remain backend-owned include:

- Minecraft biome identity;
- Minecraft temperature/downfall values;
- Minecraft feature and vegetation rules;
- concrete block palettes and block states;
- backend-specific decoration and replacement rules.

## Reconciliation with predecessor terminology

Historical Aetherial Companion analysis proposes possible concepts such as `ProvinceDescriptor`, `Geology`, `Climate`, `Ecology`, biome bridging and suitability fields.

These should be interpreted differently according to ownership:

### Strongly supported now

- explicit island/group identity;
- semantic spatial queries;
- backend-native biome bridging/adaptation;
- geometry-derived feature/structure suitability;
- registry-aware optional compatibility;
- provenance/debug tracing.

### Plausible future Skyforge concepts, but not yet required

- province-scale semantic regions;
- explicit geology state beyond current structural terrain semantics;
- authored Skyforge-specific environment/ecology state.

### Not justified as core merely by predecessor existence

- a parallel temperature/humidity/rainfall model;
- duplicating Minecraft biome identity upstream;
- a combinatorial Skyforge biome taxonomy whose only purpose is backend block selection.

The predecessor's need to manipulate climate/biome outputs is evidence that the **adapter must participate intelligently in backend environment systems**, not proof that Skyforge core must replace them.

## No speculative climate descriptor set

SF-IMP-0030 does not introduce temperature, humidity, rainfall, continentalness, erosion, ecology, or similar generic environmental descriptors.

A Minecraft adapter may consult native biome/environment information directly and combine it with the Skyforge structural semantic.

For example:

```text
Skyforge: SURFACE_MANTLE
Minecraft: plains-like biome
Backend result: grass/soil palette

Skyforge: SURFACE_MANTLE
Minecraft: snowy biome
Backend result: snow/cold-rock palette

Skyforge: DEEP_MASS
Minecraft: any ordinary biome
Backend result: structural stone palette
```

The exact Minecraft mapping belongs in the Minecraft-facing module, not `skyforge-world`.

## No speculative MaterialIntent taxonomy

SF-IMP-0030 does not introduce a backend-neutral `MaterialIntent` vocabulary.

The first proof works directly from:

```text
SkyIslandTerrainSemantic
+ world position
+ backend-owned environmental context
-> backend-owned material token
```

The reference material tokens used by the proof live only in `skyforge-reference`. They are not Skyforge world semantics or a public material ontology.

An intermediate material-intent abstraction should be added only if a concrete integration demonstrates a transformation that is genuinely shared across backends.

## First proof context shape

The first implementation adds `SkyIslandTerrainSampleContext` with exactly:

- world-space `x`, `y`, `z`;
- accepted `SkyIslandTerrainSemantic`.

`WorldRegionTerrain.sampleContextAt(...)` exposes this context from an accepted sampled semantic region.

The first proof deliberately does **not** add `SkyIslandWorldVolumeId`, group role, archipelago role, climate values, biome values, suitability, or backend registry information to every sample.

Stable island/group identity remains available at the world-catalog level. It should be promoted into the per-sample seam only when a concrete backend behavior demonstrates that the hot path needs it.

This is a deliberate minimality decision, not a claim that identity will never be useful.

## Suitability is separate from climate

The predecessor strongly supports introducing geometry-derived suitability before broad feature/structure integration.

That does not imply a climate model. Suitability can be derived from authoritative Skyforge geometry and structural semantics, for example terrain thickness, distance to surface/underside, available surface or later continuous slope/exposure.

A Minecraft adapter can combine this backend-neutral geometric validity with native biome, tag and structure/feature rules.

Suitability is deferred beyond this context-seam proof.

## Dependency direction

Backend context must not reverse the accepted dependency direction.

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

The reference policy used for SF-IMP-0030 lives in `skyforge-reference`, downstream of `skyforge-world`.

## Reference backend proof

The first non-Minecraft proof supplies a tiny backend-owned environment with two states and maps the same `SURFACE_MANTLE` sample to two different reference-only material tokens.

The proof requires:

- backend-owned environmental input may change representation;
- the Skyforge sample context remains unchanged;
- every terrain semantic maps to a representation with the same AIR/solid occupancy;
- repeated identical inputs are deterministic.

This is sufficient to demonstrate the seam without choosing Minecraft, NeoForge, block states, biome APIs, or a material taxonomy.

## Acceptance criteria for SF-IMP-0030

The focused proof must demonstrate:

1. no Minecraft/NeoForge classes enter `skyforge-world`;
2. no generic Skyforge climate descriptor set is introduced;
3. no broad abstract material taxonomy is introduced;
4. backend-visible context contains only world position and accepted terrain semantic for the first proof;
5. invalid/non-finite sample contexts fail early;
6. a sampled `WorldRegionTerrain` exposes exact world coordinates and semantic identity;
7. changing backend-native environmental input changes reference representation without changing Skyforge context;
8. the same Skyforge context is consumed by a non-Minecraft reference adapter;
9. reference material policy preserves AIR/solid occupancy for every semantic and backend environment;
10. identical Skyforge/backend inputs produce deterministic representation.

## Deferred work

This ADR does not define:

- Minecraft version or loader selection;
- concrete block mappings;
- a persistent biome/material cache;
- custom Skyforge climate simulation;
- cross-backend material-intent taxonomy;
- per-sample world-volume/group identity;
- province/geology/ecology descriptor promotion;
- suitability fields;
- vegetation, structures, caves, ores, or fluids.

Those should be driven by concrete integration evidence.

## Guiding principle

> Skyforge owns the concepts necessary to express Skyforge. A backend owns concepts that exist only because of that backend. Shared abstractions are introduced only after a concrete integration demonstrates that they are genuinely shared.
