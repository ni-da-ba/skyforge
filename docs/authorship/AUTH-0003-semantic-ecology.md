# AUTH-0003 — Semantic ecology field

## Purpose

AUTH-0003 converts continuous island-local semantic geography into broad ecological regimes without introducing Minecraft biome identifiers into Skyforge's authorship kernel.

Dependency direction remains:

`SkyIslandDescriptor -> SkyIslandSemanticFieldSet -> SkyIslandEcologyField -> backend realization`

## Ecological outputs

The field samples broad semantic regimes:

- cold barren;
- alpine;
- boreal woodland;
- temperate woodland;
- humid woodland;
- open grassland;
- dry scrub;
- wetland.

These are not intended to be a final biome taxonomy. They are a compact ecological vocabulary sufficient to test whether authored climate, terrain, exposure, hydrology, and ecological potential produce coherent region structure. Minecraft adapters may later map these semantics to one or more registered vanilla or modded biomes.

Each sample also exposes continuous vegetation, saturation, and thermal-suitability potentials so later milestones can evolve beyond hard regime boundaries without changing the causal inputs.

## Causal inputs

Ecology depends on AUTH-0002 fields and AUTH-0001 descriptor state. Region identity therefore follows from temperature, moisture, elevation tendency, exposure, interiority, ecological potential, and hydrological potential. No independent biome RNG is introduced.

## Visual acceptance corpus

The fixed AUTH-0003 atlas searches a deterministic 4096-island candidate corpus and selects six distinct representatives by authored extrema: cold, warm, wet, dry, hydrological potential, and scale. The resulting atlas is intended to reveal whether semantic ecology remains diverse across very different authored island conditions rather than proving one hand-picked seed.

Acceptance requires deterministic output, normalized continuous ecology potentials, broad regime diversity over representative generated islands, bounded visual masks, complete repository verification, and an inspectable CI artifact.
