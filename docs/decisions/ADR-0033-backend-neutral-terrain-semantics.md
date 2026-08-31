# ADR-0033: Backend-Neutral Terrain Semantics

- **Status:** Accepted; focused local verification and human visual review passed
- **Date:** 2026-08-30
- **Accepted:** 2026-08-31
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
density <= 0                          -> AIR
columnThickness <= edge threshold     -> EDGE_SHELL
depthFromUpper <= mantle depth        -> SURFACE_MANTLE
depthFromUnderside <= shell depth     -> UNDERSIDE_SHELL
min(boundary depths) <= shallow depth -> SHALLOW_INTERIOR
otherwise                             -> DEEP_MASS
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

## Numerical acceptance requirements

The focused SF-IMP-0029 proof demonstrates:

1. invalid terrain profiles fail early;
2. a real compiled island produces AIR, EDGE_SHELL, SURFACE_MANTLE, UNDERSIDE_SHELL, SHALLOW_INTERIOR, and DEEP_MASS at appropriate continuous points;
3. exact compiled upper/underside boundaries remain AIR under the authoritative density test;
4. monolithic and tiled semantic regions are byte-identical;
5. irregular tile sizes and partial edge tiles do not change semantic identity;
6. semantic-region occupancy projection is byte-identical to SF-IMP-0028 density occupancy;
7. semantic bands remain unchanged across an island crossing a tile seam;
8. spatial catalog culling remains active during tiled semantic realization.

`EDGE_SHELL` existence is tested against the continuous interpreter. A coarse evidence lattice is not required to happen to hit a thin edge shell.

The local `scripts\verify-sf-imp-0029-terrain-semantics.bat` run completed successfully on 2026-08-31.

## Accepted evidence

The generated `terrain-semantics-v1` corpus contains two scales.

### Close specimen

One independently compiled island from the accepted stable-seed Hub receives a fine 4-unit lattice. The accepted specimen contains 160,295 solid samples with this exact semantic partition:

- EDGE_SHELL: 2,946
- SURFACE_MANTLE: 19,413
- UNDERSIDE_SHELL: 25,884
- SHALLOW_INTERIOR: 72,746
- DEEP_MASS: 39,306

The solid-role counts sum exactly to the sampled solid total. Its accepted semantic SHA-256 is:

`b1dd1978c41afab4c7afd09b3c288c5bc9576793145b5ef39721405f23669598`

Human review passed. The east-west and north-south sections show a coherent upper mantle, lower underside shell, shallow transition zone, substantial deep mass in thick columns, and edge-shell concentration at pinched lateral termination. The top-surface view exposes only crown-appropriate semantics rather than leaking deep or underside roles through the upper surface.

### Regional Hub

The complete accepted stable-seed SF-IMP-0027 Hub catalog receives a coarser 48-unit horizontal / 8-unit vertical semantic lattice. Its generated semantic SHA-256 is:

`ee4b1344c69e54735c6c718e8363f594c7d1ed321802bb4f3a5ae2d868469389`

Human review passed. The topmost-solid plan and isometric views preserve the accepted group/island organization across built-in, hybrid, and external-provider members. Surface mantle remains associated with island crowns, edge-shell samples remain localized to thin margins, and no visible tile seam cuts through an island or group.

At regional scale these images are organizational evidence rather than fine material-layer evidence. The fit-to-scene isometric is sparse because the accepted Hub intentionally contains large empty-sky corridors; fine vertical-band judgment remains the responsibility of the close specimen.

The detailed human review is recorded in `docs/reviews/SF-IMP-0029-terrain-semantics-visual-review.md`.

## Evidence generation

`skyforge-reference:terrainSemanticCorpus` writes:

```text
skyforge-reference/build/evidence/terrain-semantics-v1/
  index.html
  specimen/
    summary.json
    legend.png
    top-surface-semantics.png
    east-west-section.png
    north-south-section.png
    isometric-top-semantics.png
  regional-hub/
    summary.json
    legend.png
    top-surface-semantics.png
    east-west-section.png
    north-south-section.png
    isometric-top-semantics.png
```

The local verifier runs both focused test classes and then generates this corpus in one invocation.

## Backend/material boundary

Terrain semantics are deliberately upstream of backend-native environmental and material selection.

Following ADR-0034, SF-IMP-0029 does **not** imply that Skyforge must own a generic climate/ecology system or a broad backend-neutral material taxonomy. The downstream boundary is intentionally minimal:

```text
compiled Skyforge geometry
        -> backend-neutral terrain semantic
        -> minimal adapter-visible Skyforge context where demonstrated necessary
        -> backend-native biome/environment/material policy
        -> concrete backend representation
```

A Minecraft adapter may therefore combine `SURFACE_MANTLE`, `EDGE_SHELL`, or another accepted semantic directly with Minecraft-native biome/environment information when choosing block states. Another backend may map the same semantics to voxel IDs, textures, or mesh materials.

Shared climate, ecology, geology, or material-intent abstractions should be introduced only when backend-independent Skyforge behavior demonstrates a concrete need for them.

## Deferred work

SF-IMP-0029 does not yet define:

- concrete Minecraft blocks or block states;
- a parallel Skyforge climate/ecoregion system;
- vegetation, ores, caves, fluids, or structures;
- general cliff/slope orientation semantics;
- weathering or stratigraphic procedural signals;
- provider-specific material overrides;
- material blending between overlapping volumes (accepted archipelagos remain non-overlapping);
- production caching or serialization of materialized chunks.

## Consequence

Skyforge now has a deterministic, backend-neutral structural interpretation layer over accepted suspended geometry. A backend can distinguish crown, lateral termination, underside, shallow interior, deep interior, and air without changing morphology, spatial planning, catalog-query behavior, occupancy, or tile-seam identity.
