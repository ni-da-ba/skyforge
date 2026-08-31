# ADR-0033: Backend-Neutral Terrain Semantics

- **Status:** Proposed; focused semantic and tiled-equivalence proof pending local validation
- **Date:** 2026-08-30
- **Work item:** SF-IMP-0029

## Context

SF-IMP-0028 accepts a backend-neutral runtime path from hierarchical Skyforge planning to bounded world-catalog volumes and seam-safe tiled density realization. The runtime result is still binary solid/air geometry.

A concrete backend such as Minecraft needs more than occupancy: upper terrain, exposed island edges, underside shell, shallow interior, and deep mass should be distinguishable before a backend chooses concrete blocks, voxels, textures, or materials.

Putting Minecraft block identifiers directly in `skyforge-world` would couple Skyforge terrain semantics to one game and make other voxel/rendering backends second-class. Material interpretation therefore needs an intermediate semantic layer.

## Decision

SF-IMP-0029 introduces backend-neutral terrain semantics in `skyforge-world`.

The initial semantic vocabulary is:

- `AIR` — outside authoritative compiled density;
- `EDGE_SHELL` — solid in a thin pinched column near the lateral/coastal termination of a suspended island;
- `SURFACE_MANTLE` — solid within the configured depth below the upper surface;
- `UNDERSIDE_SHELL` — solid within the configured depth above the underside surface;
- `SHALLOW_INTERIOR` — solid near either exposed vertical boundary after the explicit surface shells;
- `DEEP_MASS` — remaining interior mass.

These values describe geometric/material roles, not final materials.

## Continuous classification

`SkyIslandTerrainInterpreter` evaluates the compiled density graph as the authoritative solid/air test. For a solid point it also evaluates the compiled upper and underside surfaces and computes:

```text
depthFromUpper     = upperSurface(x,z) - y
depthFromUnderside = y - undersideSurface(x,z)
columnThickness    = upperSurface(x,z) - undersideSurface(x,z)
```

Classification order is:

```text
density <= 0                         -> AIR
columnThickness <= edge threshold    -> EDGE_SHELL
depthFromUpper <= mantle depth       -> SURFACE_MANTLE
depthFromUnderside <= shell depth    -> UNDERSIDE_SHELL
min(boundary depths) <= shallow depth -> SHALLOW_INTERIOR
otherwise                            -> DEEP_MASS
```

The classification does not inspect neighboring voxels. It is therefore independent of chunk size, sample spacing, tile order, or a specific voxel grid.

`EDGE_SHELL` intentionally means a geometrically pinched coastal/lateral shell. It is not yet a general slope/cliff normal classification. General exposed-side orientation may be added later using a continuous derivative/normal contract rather than backend-neighbor tests.

## Terrain profile

`SkyIslandTerrainProfile` supplies world-unit thresholds for:

- upper surface mantle depth;
- underside shell depth;
- maximum column thickness considered edge shell;
- shallow interior boundary depth.

The shallow-interior depth must be at least as large as both explicit surface-shell depths so the layers remain monotone.

The first reference profile uses 12 / 16 / 28 / 40 world units respectively. This profile is evidence configuration, not a permanent biome or Minecraft material policy.

## Tiled semantic realization

`ReferenceTiledSkyIslandTerrainBackend` consumes the same `SkyIslandWorldCatalog` accepted in SF-IMP-0028. It supports monolithic and independently queried tiled realization.

Each sampled lattice point receives exactly one encoded `SkyIslandTerrainSemantic`. The occupancy projection of the semantic region must remain byte-identical to the accepted density backend; material interpretation may annotate geometry but must not create or erase terrain.

## Initial acceptance requirements

The focused SF-IMP-0029 proof must demonstrate:

1. invalid terrain profiles fail early;
2. a real compiled island produces AIR, EDGE_SHELL, SURFACE_MANTLE, UNDERSIDE_SHELL, SHALLOW_INTERIOR, and DEEP_MASS at appropriate continuous points;
3. exact compiled upper/underside boundaries remain AIR under the authoritative density test;
4. monolithic and tiled semantic regions are byte-identical;
5. irregular tile sizes and partial edge tiles do not change semantic identity;
6. semantic-region occupancy projection is byte-identical to SF-IMP-0028 density occupancy;
7. semantic bands remain unchanged across an island crossing a tile seam;
8. spatial catalog culling remains active during tiled semantic realization.

## Deferred work

SF-IMP-0029 does not yet define:

- concrete Minecraft blocks or block states;
- biome/ecoregion assignment;
- vegetation, ores, caves, fluids, or structures;
- general cliff/slope orientation semantics;
- weathering or stratigraphic procedural signals;
- provider-specific material overrides;
- material blending between overlapping volumes (accepted archipelagos remain non-overlapping);
- production caching or serialization of materialized chunks.

## Consequence

The intended downstream layering becomes:

```text
compiled Skyforge geometry
        -> backend-neutral terrain semantic
        -> biome/material policy
        -> concrete backend material
```

A Minecraft adapter can therefore map semantic roles to block palettes without changing island morphology, world planning, catalog queries, or tile-seam behavior.
