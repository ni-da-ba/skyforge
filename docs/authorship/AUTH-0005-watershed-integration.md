# AUTH-0005 — Watershed integration and flow accumulation

AUTH-0005 converts AUTH-0004 local hydrological signals into coarse, backend-neutral drainage topology.

## Purpose

Local runoff and downhill tendency do not by themselves define rivers. A coherent water network requires upstream integration: many cells must contribute to shared drainage paths, sinks, and outlets. This milestone introduces that graph without claiming voxel-scale terrain, erosion, or final river geometry.

## Model

`SkyIslandWatershedPlanner` samples one authored island on a deterministic 49×49 island-local lattice. Active cells are defined by semantic interiority. Each active cell receives:

- authored surface potential derived primarily from elevation tendency;
- local runoff from `SkyIslandHydrologyField`;
- a downstream neighbor chosen from lower surrounding surface potential;
- accumulated upstream runoff;
- terminal classification as retained sink, edge outlet, or unresolved interior terminal.

The routing graph is acyclic because water is only routed to lower authored surface potential.

## Deliberate limits

AUTH-0005 does **not** yet implement:

- terrain carving or erosion;
- final river width or depth;
- depression filling or lake level solving;
- stream-order taxonomy;
- waterfalls as realized geometry;
- Minecraft blocks, chunks, biome IDs, coordinates, or placement APIs.

The coarse lattice is semantic planning resolution only. Later realizers may interpolate or refine it independently.

## Visual acceptance corpus

`AuthorshipWatershedCorpusCli` deterministically searches 4,096 authored islands and selects representatives emphasizing hydrology, moisture, basin morphology, spine morphology, lobed morphology, and scale.

The atlas visualizes accumulated upstream runoff using a logarithmic intensity scale. Retained sinks and edge outlets are marked separately. This evidence should expose, rather than hide, current radial-domain limitations inherited from the first-generation interiority field.

## Acceptance criteria

- repeated planning is deterministic;
- downstream references remain inside the active graph;
- drainage topology is acyclic;
- accumulated flow exceeds local runoff where tributaries converge;
- distinct morphology/climate cases produce materially distinct drainage patterns;
- CI publishes the watershed corpus for visual inspection before merge.
