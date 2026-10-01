# AUTH-0025 — Cave-System Geometry

AUTH-0025 converts the semantic cave-system graphs accepted in AUTH-0024 into broad backend-neutral chamber and passage geometry.

It remains an authorship layer. It does not emit Minecraft coordinates, carved blocks, NeoForge carvers, placed features, or exact backend voxel masks.

## Dependency

~~~text
SkyIslandDescriptor
    -> AUTH-0022 continuous subsurface geology
    -> AUTH-0023 mesoscale geological regions
    -> AUTH-0024 semantic cave-system topology
        -> chamber anchors
        -> topological links
    -> AUTH-0025 cave-system geometry
        -> chamber ellipsoids
        -> geology-steered curved passage corridors
    -> future cave volume/density compilation
    -> backend realization
~~~

## Coordinate discipline

AUTH-0022 established that island-local horizontal coordinates and semantic depth have different units:

- x/z use island-local world units;
- depthFraction is dimensionless semantic depth from upper surface toward the deep interior/underside zone.

AUTH-0025 preserves that distinction.

Chambers therefore store:

- horizontalRadius in island-local world units;
- depthRadius in semantic depth-fraction units.

Passage points likewise store independent horizontal and depth radii.

The authorship layer does not invent an implicit conversion between those axes. A later compiler may map semantic depth to physical exact-volume height using an explicit realization transform.

## Chambers

Every AUTH-0024 cave node becomes exactly one SkyIslandCaveChamberGeometry.

Chamber scale derives from:

- AUTH-0024 chamber potential;
- descriptor rock competence;
- descriptor erosion maturity.

Weaker rock and stronger void potential permit broader chambers, but chamber count remains topology-controlled.

Chambers remain bounded inside semantic depth [0, 1].

AUTH-0025 does not yet define high-frequency chamber wall detail. The irregularity value records a later-detail tendency rather than deforming the first-generation evidence silhouette arbitrarily.

## Passages

Every AUTH-0024 topological link becomes exactly one SkyIslandCavePassageGeometry.

A passage is represented by 13 sampled semantic centerline points with local horizontal/depth thickness.

The sampled centerline is meaningful geometry, but it is not a backend carve path contract.

## Geological steering

AUTH-0025 does not simply draw the straight evidence line from AUTH-0024.

For each topological link, the planner tests a small deterministic family of gentle quadratic bends around the link midpoint.

Candidate curves are rejected if any sampled point:

- leaves current AUTH-0020/AUTH-0021 naturalized horizontal ownership;
- reaches or crosses semantic depth boundaries.

Remaining curves are scored against current geology.

### VOID_CONTINUITY

Routes favor:

- AUTH-0022 void-formation potential;
- expressed AUTH-0023 fracture support;
- expressed AUTH-0023 aquifer support.

### FRACTURE_BRIDGE

Routes weight expressed fracture support most strongly while still requiring cave-forming geology.

### AQUIFER_BRIDGE

Routes weight expressed aquifer support most strongly while still requiring cave-forming geology.

### MIXED_GEOLOGIC_BRIDGE

Routes balance fracture, aquifer, and void support.

A straight or nearly straight route remains valid when geology supports it best.

A small bounded preference for nonzero curvature prevents numerically equivalent routes from systematically collapsing back to ruler-straight passages without allowing decorative curvature to override geological support.

## Passage thickness

Passage thickness is subordinate to its endpoint chambers.

Horizontal radius derives from the smaller endpoint chamber plus geological steering/link support.

Depth thickness derives from endpoint chamber depth radii and is clamped to remain inside semantic depth bounds.

Passages widen mildly toward their middle rather than becoming constant-radius cylinders.

This is first-generation corridor geometry, not final cave-wall morphology.

## Topology preservation

AUTH-0025 preserves AUTH-0024 graph cardinality exactly:

- one chamber per topology node;
- one passage per topology link;
- one geometry system per semantic cave system.

It may curve and size the geometry, but it may not create or remove semantic cave connectivity.

## Evidence

The `authorship-cave-system-geometry-v1` corpus reuses the accepted AUTH-0024 representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic massif key 2211;
- eroded tableland key 1439;
- spine key 3670.

Each specimen renders:

- TOPOLOGY — AUTH-0024 schematic graph;
- SECTION GEOMETRY — x/depth chambers and curved passage corridors;
- TOP-DOWN GEOMETRY — x/z chamber/passage geometry inside naturalized ownership;
- GEOLOGIC SUPPORT — final geometry over AUTH-0023 fracture/aquifer support.

The geometry panels show authored corridor extent. They still do not represent Minecraft blocks.

`manifest.csv` records systems, chambers, passages, mean chamber scale, mean passage scale, mean steering support, and maximum centerline deviation from the corresponding straight topology link.

## Acceptance gate

Reject AUTH-0025 if:

- cave-free AUTH-0024 controls receive geometry;
- chamber/passage counts differ from topology;
- chamber or passage thickness crosses semantic depth bounds;
- passage centerlines leave naturalized island ownership;
- passages remain uniformly ruler-straight despite viable geology-supported bends;
- curvature becomes decorative and unrelated to geological support;
- passage thickness becomes comparable to whole-island scale;
- competent and weak islands receive indistinguishable chamber scale;
- the evidence begins to look like backend voxels rather than semantic geometry;
- Minecraft/NeoForge-specific concepts enter the world layer.

## Parallel implementation boundary

SF-IMP-0063 contains native fluid springs inside exact carved volumes.

AUTH-0025 does not consume spring placement or runtime carving behavior.

Later integration may use authored aquifer/cave geometry as policy input, but this milestone remains independent of implementation mechanics.

## Next milestone

If AUTH-0025 is accepted, the next cave milestone should compile chamber and passage geometry into a continuous backend-neutral cave-volume/density field.

That compilation should answer whether an arbitrary semantic subsurface point lies inside, near, or outside an authored cave volume while preserving topology and geometry identity.

Only after that field exists should exact-volume backend integration become an authorship concern.
