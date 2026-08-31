# ADR-0034: Minimal Backend Context Seam

- **Status:** Proposed; implementation deferred until SF-IMP-0029 acceptance
- **Date:** 2026-08-30
- **Next work item:** SF-IMP-0030

## Context

SF-IMP-0029 introduces backend-neutral structural terrain semantics such as `SURFACE_MANTLE`, `EDGE_SHELL`, `UNDERSIDE_SHELL`, `SHALLOW_INTERIOR`, and `DEEP_MASS`. A concrete backend must eventually convert those meanings into concrete terrain materials.

It would be easy to respond by adding a broad Skyforge climate, biome, geology, or material-intent model. That would be premature. Minecraft and other world backends may already provide environmental systems that should remain authoritative for backend-native concepts such as biome, temperature, precipitation, vegetation, or block palettes.

Skyforge should remain backend-neutral without duplicating backend responsibilities.

The recovered Aetherial Islands / Aetherial Companion lineage reinforces the need for backend compatibility seams, but it does **not** require Skyforge to predeclare every environment concept Companion once had to manipulate. The predecessor adapted another terrain generator and therefore mixed semantic intent with biome remapping, density gating and compatibility correction in ways modern Skyforge should avoid.

## Decision

SF-IMP-0030 will introduce only the smallest context seam demonstrated necessary by a concrete adapter.

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

SF-IMP-0030 must not introduce temperature, humidity, rainfall, continentalness, erosion, ecology, or similar generic environmental descriptors unless a backend-neutral Skyforge behavior demonstrates a concrete need for them.

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

SF-IMP-0030 also must not introduce a large backend-neutral `MaterialIntent` vocabulary merely because multiple future backends are imaginable.

The first adapter should attempt to work directly from:

```text
SkyIslandTerrainSemantic
+ stable Skyforge identity/context only where needed
+ backend-native environmental context
-> concrete backend material
```

An intermediate material-intent abstraction should be added only if at least one concrete integration demonstrates a transformation that is genuinely shared across backends.

## Minimal context shape

The first implementation may expose a very small immutable context object or equivalent adapter call boundary. Candidate information is:

- world position;
- `SkyIslandTerrainSemantic`;
- `SkyIslandWorldVolumeId`;
- group role or other hierarchy metadata only if a demonstrated backend behavior requires it.

Not every candidate field must be included. The implementation should start with the minimum needed by the first adapter proof.

## Suitability is separate from climate

The predecessor strongly supports introducing geometry-derived suitability before broad feature/structure integration.

That does not imply a climate model. Suitability can be derived from authoritative Skyforge geometry and structural semantics, for example terrain thickness, distance to surface/underside, available surface or later continuous slope/exposure.

A Minecraft adapter can combine this backend-neutral geometric validity with native biome, tag and structure/feature rules.

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

## First concrete adapter implication

The first Minecraft-like adapter should be allowed to combine:

1. Skyforge terrain semantic;
2. the sampled world position and stable Skyforge identity if needed;
3. native Minecraft biome/environment information;
4. backend-specific block palette rules.

The adapter may therefore participate in Minecraft's existing biome system instead of replacing it.

## Acceptance criteria for SF-IMP-0030

The first proof should remain deliberately small and demonstrate:

1. no Minecraft/NeoForge classes enter `skyforge-world`;
2. no generic Skyforge climate descriptor set is introduced;
3. no broad abstract material taxonomy is introduced without demonstrated need;
4. a backend adapter can receive the structural semantic and enough stable Skyforge context to make a material decision;
5. changing backend-native environmental input may change material selection without changing Skyforge geometry or terrain semantic identity;
6. the same Skyforge semantic/context can be consumed by a non-Minecraft reference adapter;
7. backend material policy cannot create or erase Skyforge occupancy unless a later explicit world-edit contract permits it.

## Deferred work

This ADR does not yet define:

- a final public `TerrainMaterialContext` API;
- Minecraft version or loader selection;
- concrete block mappings;
- a persistent biome/material cache;
- custom Skyforge climate simulation;
- cross-backend material-intent taxonomy;
- province/geology/ecology descriptor promotion;
- vegetation, structures, caves, ores, or fluids.

Those should be driven by concrete integration evidence.

## Guiding principle

> Skyforge owns the concepts necessary to express Skyforge. A backend owns concepts that exist only because of that backend. Shared abstractions are introduced only after a concrete integration demonstrates that they are genuinely shared.
