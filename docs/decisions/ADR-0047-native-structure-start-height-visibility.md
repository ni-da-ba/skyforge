# ADR-0047 — Native Structure-Start Height Visibility Proof

**Status:** Proposed

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

Because `spacing - separation == 1`, each four-chunk region has exactly one possible offset and chunk `(0,0)` is deterministically a candidate. The development datapack also appends `#c:is_overworld` to `#minecraft:has_structure/desert_pyramid`, allowing the proof to run regardless of the random native biome at the origin.

The structure itself remains entirely vanilla. Skyforge does not alter desert-pyramid pieces, templates, terrain adaptation, bounding boxes or block placement.

## Expected generation relationship

```text
STRUCTURE_STARTS
    -> vanilla desert-pyramid structure selection
    -> vanilla structure terrain-height query
    -> SkyforgeNoiseBasedChunkGenerator.getBaseHeight(...)
    -> max(vanilla height, active Skyforge height)
    -> native StructureStart anchored against elevated geometry

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

## Invariants

1. The structure proof uses vanilla `minecraft:desert_pyramid`.
2. Structure-start terrain visibility comes through the accepted generator query bridge rather than earlier Skyforge block mutation.
3. Physical Skyforge terrain remains post-surface.
4. The forced structure set and biome-tag broadening are development-only.
5. No Minecraft structure concepts enter backend-neutral modules.
6. Ordinary production structure density and biome eligibility are unchanged.
7. Retired grass/tree benchmark fixtures are not required for this proof.
8. This milestone proves height visibility and native placement, not final structure suitability.

## Acceptance criteria

ADR-0047 becomes Accepted after:

1. focused verification and repository-wide `check` pass;
2. the SF-IMP-0043 development resources load without registry/datapack errors;
3. a new development world generates the vanilla desert-pyramid candidate near chunk `(0,0)`;
4. the pyramid is visibly anchored to the elevated Massif rather than the preserved native ground below it;
5. the Massif still realizes through the accepted post-surface path without obvious seams/corruption;
6. save/reload preserves both terrain and structure cleanly; and
7. all forcing resources remain absent from the production JAR.

## Explicit non-goals

SF-IMP-0043 does not establish:

- general structure suitability for arbitrary island shapes;
- structure-aware flattening or foundation repair;
- support for villages, jigsaw structures, monuments or modded structures;
- production structure density/frequency policy;
- Skyforge-owned structure biome policy;
- stacked-island structure selection;
- final morphology/playability tuning.
