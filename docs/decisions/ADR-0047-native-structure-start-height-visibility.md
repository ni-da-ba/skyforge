# ADR-0047 — Native Structure-Start Height Visibility Proof

**Status:** Accepted

## Context

SF-IMP-0042 established a non-mutating `ChunkGenerator.getBaseHeight(...)` bridge that exposes the already-compiled Skyforge runtime before the island is physically written into a chunk. The remaining question is whether Minecraft's real structure-start machinery can consume that bridge early enough to place a native structure relative to an elevated Skyforge island.

A synthetic marker or Skyforge-authored structure would not answer this as strongly as a vanilla structure whose generation code already relies on `ChunkGenerator` terrain-height queries.

## Decision

SF-IMP-0043 uses the vanilla `minecraft:desert_pyramid` structure as the first native structure consumer proof.

Development-only data adds a separate structure set:

```text
skyforge:sf_imp_0043_desert_pyramids
```

with random-spread placement:

```text
spacing = 4
separation = 3
salt = 430043
```

Because `spacing - separation == 1`, each four-chunk placement region has exactly one possible offset and chunk `(0,0)` is deterministically a candidate. Neighboring generated placement regions may therefore contain additional pyramids; the fixture intentionally increases observation density rather than promising one structure total.

The development datapack also appends `#c:is_overworld` to `#minecraft:has_structure/desert_pyramid`, allowing the proof to run regardless of the random native biome at the origin.

The structure itself remains entirely vanilla. Skyforge does not alter desert-pyramid pieces, templates, terrain adaptation, bounding boxes or block placement.

## Expected generation relationship

```text
STRUCTURE_STARTS
    -> vanilla desert-pyramid structure selection
    -> vanilla structure terrain-height query
    -> SkyforgeNoiseBasedChunkGenerator.getBaseHeight(...)
    -> max(vanilla height, active Skyforge height)
    -> native StructureStart can react to elevated geometry

later:

SURFACE
    -> vanilla surface
    -> Skyforge physical realization

FEATURES / surface_structures
    -> vanilla desert-pyramid pieces placed into the now-realized chunk
```

## Architectural boundary

The forced structure set and broadened biome tag are test fixtures only. They are not production structure density or biome policy.

The accepted production mechanism under test is the early generator query bridge from SF-IMP-0042. Minecraft continues to own concrete structure type, pieces, templates, bounding boxes and placement behavior.

Future Skyforge structure suitability may contribute semantic/geometry-derived constraints, but this milestone does not create a parallel structure engine or make every vanilla structure appropriate for floating islands.

## Client finding: visibility is not suitability

The accepted client proof generated several development-forced vanilla desert pyramids. A candidate completely outside the Massif generated on ordinary native ground, while several candidates intersecting the Massif were vertically associated with the elevated island and clipped into it at varying depths.

This establishes:

```text
structure can see Skyforge height
                !=
structure footprint is appropriate for that surface
```

A sloped footprint or a footprint crossing an island edge can yield an anchor that embeds the structure even when individual height answers are locally truthful.

The correct future response is **not** to extend or flatten `getBaseHeight(...)` beyond real Skyforge geometry. Doing so would expose phantom terrain to every early generator consumer. Structure placement needs a separate suitability/support policy capable of reasoning about footprint coverage, coherent target surfaces, slope, edge clearance, and eventually stacked-island choice.

The same run did not show kelp beneath the Massif. This is not an SF-IMP-0043 regression; supplemental aquatic vegetation has not yet been generalized into production ecology replay.

## Invariants

1. The structure proof uses vanilla `minecraft:desert_pyramid`.
2. Structure-start terrain visibility comes through the accepted generator query bridge rather than earlier Skyforge block mutation.
3. Physical Skyforge terrain remains post-surface.
4. The forced structure set and biome-tag broadening are development-only.
5. No Minecraft structure concepts enter backend-neutral modules.
6. Ordinary production structure density and biome eligibility are unchanged.
7. Retired grass/tree benchmark fixtures are not required for this proof.
8. This milestone proves height visibility and native placement, not final structure suitability.
9. Early height queries remain geometrically truthful; structure-specific footprint accommodation must be solved separately.

## Acceptance evidence

SF-IMP-0043 was accepted after:

1. focused verification and repository-wide `check` passed;
2. development resources loaded without registry/datapack errors;
3. development-forced vanilla desert pyramids generated in the expected regions;
4. multiple Massif-overlapping pyramids were visibly vertically associated with elevated Skyforge terrain;
5. the Massif remained stable through the accepted post-surface realization path;
6. save/reload was confirmed clean; and
7. verifier coverage confirmed development forcing resources remain absent from the production JAR.

## Explicit non-goals

SF-IMP-0043 does not establish:

- general structure suitability for arbitrary island shapes;
- structure-aware flattening or foundation repair;
- support for villages, jigsaw structures, monuments or modded structures;
- production structure density/frequency policy;
- Skyforge-owned structure biome policy;
- stacked-island structure selection;
- aquatic vegetation replay such as kelp beneath an elevated island;
- final morphology/playability tuning.
