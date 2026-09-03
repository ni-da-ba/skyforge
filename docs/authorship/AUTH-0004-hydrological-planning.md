# AUTH-0004 — Hydrological Planning

## Objective

Introduce the first deterministic, backend-neutral hydrological planning layer for authored Skyforge islands.

AUTH-0004 does **not** claim to generate final rivers, lakes, erosion, groundwater, or Minecraft water blocks. It establishes local hydrological causes that later global planners can integrate into those systems.

## Inputs

Hydrology derives only from authored Skyforge semantics:

- island descriptor hydrological potential;
- permeability;
- moisture;
- exposure;
- elevation tendency;
- island interiority and edge influence.

No backend coordinates, Minecraft registry identities, block states, or placement assumptions enter this layer.

## Outputs

`SkyIslandHydrologyField` yields one `SkyIslandHydrologySample` containing:

- `runoffPotential` — available surface-water supply after moisture, hydrological character, permeability, and exposure are considered;
- `retentionPotential` — local tendency to hold water, informed by low slope and local depression character;
- `drainagePotential` — local tendency to move surface water downslope;
- `outflowPotential` — drainage that is also near enough to an island edge to plausibly become an edge discharge or waterfall;
- `flowX`, `flowZ` — normalized island-local downhill tendency.

These are planning signals, not pre-authored channels.

## Why local planning comes before river routing

A river is not a property of one point. It requires integration over a drainage domain: upstream accumulation, watershed partitioning, path continuity, basin filling, outlet selection, and conflict resolution. AUTH-0004 intentionally stops before that graph-scale problem.

The dependency direction is therefore:

```text
island descriptor
    -> semantic geography
    -> local hydrological causes
    -> future watershed / accumulation planner
    -> channels, lakes, wetlands, waterfalls
    -> backend realization
```

## Visual evidence

The deterministic `authorship-hydrology-v1` atlas selects six representative descriptors from 4,096 authored islands: hydrological, wet, impermeable, basin, exposed, and large.

Atlas encoding:

- blue emphasizes retention;
- cyan/white emphasizes drainage;
- orange emphasizes edge outflow;
- short dark ticks show local downhill tendency.

The corpus is intended to reveal whether hydrological signals respond coherently to morphology and descriptor-level causes. It is not a final river map.

## Deferred work

Later milestones should add, in order of dependency:

1. watershed and drainage-domain integration;
2. flow accumulation and channel candidate extraction;
3. basin filling and lake/wetland planning;
4. outlet and island-edge waterfall planning;
5. erosion/terrain feedback where justified;
6. backend realization.

Island-boundary naturalization is also deferred. AUTH-0004 should expose where radial first-generation island geometry constrains hydrology rather than hiding that fact.
