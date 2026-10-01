# AUTH-0024 — Cave-System Topology

AUTH-0024 converts the mesoscale geological regions accepted in AUTH-0023 into a small number of explainable semantic cave-system graphs.

It does not author carved cells, tunnel splines, Minecraft carvers, underground features, or block materials.

## Dependency

~~~text
SkyIslandDescriptor
    -> AUTH-0022 continuous subsurface geology
    -> AUTH-0023 connected geologic regions
        -> fracture corridors
        -> aquifer bodies
        -> void-prone domains
    -> AUTH-0024 semantic cave-system topology
        -> chamber anchors
        -> continuity links
        -> geologically supported inter-domain bridges
    -> future cave geometry / passages / entrances
    -> backend realization
~~~

## Void region is not cave system

AUTH-0023 identifies connected volumes where persistent natural voids are plausible.

AUTH-0024 deliberately does not equate one void-prone region with one cave.

Large void regions are sampled into a small number of chamber-scale anchors:

- fewer than 18 planning cells -> one anchor;
- 18–59 cells -> two anchors;
- 60 or more cells -> three anchors.

Three is the first-generation maximum.

The first anchor is the strongest void-prone cell. Additional anchors balance geological membership with spatial separation so a large domain receives several meaningful chamber centers rather than several nearly coincident points.

## Node meaning

A SkyIslandCaveNode records:

- stable node identifier;
- source AUTH-0023 void-region identifier;
- island-local semantic subsurface position;
- chamber potential inherited from void-region membership;
- AUTH-0022 groundwater potential.

A node is a topological chamber-scale anchor, not a sphere to carve.

## Link meaning

### Void continuity

Nodes originating inside the same connected AUTH-0023 void region are connected by a deterministic minimum spanning tree.

This states that the geological domain can support one connected cave system without pretending that the straight line between two evidence points is the eventual passage geometry.

### Geological bridges

Separate void-prone regions may be joined only when:

1. the candidate endpoints are within a bounded normalized semantic distance;
2. the sampled bridge remains horizontally inside current naturalized island ownership;
3. the intervening coarse geological plan supplies enough expressed fracture and/or aquifer support.

Bridge kinds are:

- FRACTURE_BRIDGE;
- AQUIFER_BRIDGE;
- MIXED_GEOLOGIC_BRIDGE.

Below-threshold geological tendencies do not justify a bridge.

## Sparse first topology

AUTH-0024 uses Kruskal-style bridge selection after internal void-region continuity trees are established.

The result is a forest of connected cave-system trees.

This is intentional.

Loops, alternate passages, shafts, and secondary branches are downstream detail. The first accepted topology should establish the minimum geological connectivity that explains the cave system.

## System identity

Each connected graph component becomes one SkyIslandCaveSystem.

System ordering is deterministic by minimum node identifier.

A cave system can span multiple AUTH-0023 void regions only through accepted geological bridges.

Systems also expose whether groundwater/aquifer support makes them water-influenced.

This is semantic cave-system character, not literal flooded-block state.

## Evidence

The authorship-cave-system-topology-v1 corpus uses the same geological representatives accepted in AUTH-0023:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders:

- VOID DOMAINS — AUTH-0023 x/depth void-region projection;
- CAVE TOPOLOGY — chamber anchors and semantic links over that projection;
- TOP-DOWN GRAPH — x/z graph view inside current naturalized ownership;
- HYDRO INFLUENCE — cave graph over AUTH-0023 aquifer support.

The graph lines are schematic evidence of topology. They are not future tunnel centerlines.

manifest.csv records void-region counts, cave-system counts, nodes, links, cross-region links, water-influenced systems, maximum nodes per system, and maximum source void regions per system.

## Acceptance gate

Reject AUTH-0024 if:

- cave nodes occur outside accepted AUTH-0023 void-prone regions;
- islands with no void-prone regions receive invented cave systems;
- large void domains explode into many chamber nodes;
- separate void regions connect merely because they are nearby;
- inter-region bridges lack expressed fracture/aquifer support;
- every void region is mechanically forced into one island-wide cave system;
- topology becomes visually tangled or maze-like at this semantic stage;
- the graph is mistaken for literal passage geometry;
- backend-specific coordinates, blocks, carvers, or placed features enter the model.

## Parallel implementation boundary

SF-IMP-0063 contains native fluid springs inside exact carved Skyforge volumes.

AUTH-0024 does not invoke springs, fluid features, native decoration, or carving.

Its groundwater character may later become one semantic input to backend or Skyforge-native cave hydrology, but current runtime mechanics remain independent.

## Next milestone

If AUTH-0024 is accepted, the next authorship step should be cave-system geometry.

That milestone can convert semantic topology into broad passage/chamber geometry while preserving:

- node/edge identity;
- geological support;
- semantic depth;
- naturalized island ownership;
- separation from backend carving.

Entrance/underside breakthrough policy can then be authored as a consequence of system geometry rather than guessed from raw noise.
