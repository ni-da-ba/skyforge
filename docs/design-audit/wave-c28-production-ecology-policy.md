# Wave C28 — production ecology presentation and vegetation-role policy

**Issue:** #1233  
**PR:** #1234  
**Scope:** Content / Experience, Phase 2 ecology under production geography

## Purpose

C28 is the first concrete Content consumer of the newer backend-neutral ecology architecture after
AUTH #1229 / PR #1230 bound exact local hydrologic evidence into ecology provenance.

It deliberately does **not** add another Authorship layer. Instead it fixes two Content-owned choices:

1. which accepted semantic regime should be requested for surface presentation when exact AUTH-0096
   freshwater/riparian evidence is present; and
2. what broad visible structure and vegetation functional roles the five accepted community
   archetypes are intended to support.

Minecraft biome IDs, feature IDs, species, occupancy, abundance, carrying capacity, candidate spacing,
and final population density remain outside this policy.

## Surface presentation policy

The accepted AUTH-0003 regime is preserved by default.

One exact AUTH-0096 cell requests wetland presentation when any already-accepted freshwater/riparian
evidence is present:

- retained-waterbody membership;
- shoreline;
- nonzero water-depth potential;
- nonzero waterbody-margin potential;
- nonzero riparian potential;
- nonzero channel-relative discharge.

This is intentionally the existing DR-40 predicate moved from the NeoForge adapter into Content
authority. It is not a new wetness formula, interpolation scheme, or aesthetic retune.

## Community structural capacities

All values are normalized Content structure capacities in [0, 1]. They are not physical canopy
height, biomass, percent cover, or population density.

| Community | Vegetation | Canopy cover | Canopy height | Understory | Ground cover | Biomass | Patchiness | Organic surface | Deadwood |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Closed woodland | 0.90 | 0.85 | 0.80 | 0.60 | 0.50 | 0.85 | 0.45 | 0.65 | 0.55 |
| Open herbaceous | 0.75 | 0.10 | 0.10 | 0.25 | 0.90 | 0.55 | 0.40 | 0.30 | 0.15 |
| Saturated wetland | 0.85 | 0.35 | 0.30 | 0.75 | 0.85 | 0.75 | 0.55 | 0.75 | 0.50 |
| Alpine tundra | 0.45 | 0.02 | 0.03 | 0.20 | 0.70 | 0.30 | 0.50 | 0.25 | 0.10 |
| Xeric scrub | 0.50 | 0.08 | 0.12 | 0.60 | 0.50 | 0.35 | 0.55 | 0.12 | 0.18 |

The values establish broad structural ordering rather than a tuned final landscape:

- closed woodland is canopy- and biomass-dominant;
- open herbaceous terrain is ground-cover-dominant;
- saturated wetland emphasizes understory, ground cover, biomass, and organic accumulation;
- alpine tundra strongly suppresses tall canopy structure;
- xeric scrub remains low-canopy and comparatively patchy.

The accepted linear support realization transform remains responsible for scaling these capacities by
resolved community assembly support.

## Vegetation functional-role affinities

Functional groups remain ecological roles rather than species or backend features. Affinity weights
are sparse on purpose so the policy remains explainable.

| Functional group | Positive structural affinities |
| --- | --- |
| Tall canopy tree | vegetation density, canopy cover, canopy height, biomass |
| Small tree | vegetation density, canopy cover, canopy height, understory, biomass |
| Shrub | vegetation density, understory, ground cover, patchiness |
| Grass / forb | vegetation density, ground cover |
| Fern / groundcover | understory, ground cover, organic surface |
| Aquatic plant | vegetation density, ground cover, biomass |
| Decomposer / fungus | biomass, organic surface, deadwood |

The accepted weighted-mean functional-group niche transform remains responsible for calculating
structural niche support. C28 does not invent a second niche formula.

## Hydrologic requirements

Only **AQUATIC_PLANT** receives an additional C28 hydrologic requirement:

> exact accepted AUTH-0096 retained-waterbody membership must be present.

Missing hydrologic evidence fails closed for this role. No nearest-cell policy, water-distance cutoff,
depth threshold, or combined wetness scalar is introduced in Content.

Other vegetation groups have no additional C28 hydrologic gate. This does not mean hydrology can never
affect them; it means C28 does not invent a new response without a concrete need.

## Backend boundary

NeoForge still owns:

- mapping Minecraft block columns to accepted AUTH-0096 raster cells;
- registered Minecraft biome identity;
- native feature execution;
- exact-volume population lifecycle;
- persistence and reload behavior.

C28 owns only the semantic presentation request. The production resolver therefore samples Authorship
ecology and exact AUTH-0096 evidence, asks Content for the requested semantic regime, then maps that
regime to the existing Minecraft biome carrier.

## Deferred on purpose

The following should be introduced only with a concrete consumer and evidence:

- coexistence-weight policy;
- final patch scale;
- candidate lattice pitch/jitter;
- species/mod-entity mapping;
- vegetation feature palette;
- occupancy and population lifecycle;
- fauna niches and trophic relationships;
- final density/aesthetic tuning.

This keeps C28 aligned with the Phase-2 roadmap: establish coherent habitat composition first, then
tune realized population only when representative integration demonstrates the need.
