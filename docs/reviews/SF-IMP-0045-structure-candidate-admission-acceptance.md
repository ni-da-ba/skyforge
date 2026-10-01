# SF-IMP-0045 Structure Candidate Admission Acceptance

- **Status:** Accepted
- **Date:** 2026-08-31
- **Decision:** ADR-0049

## Acceptance statement

SF-IMP-0045 establishes a narrow Minecraft-native structure admission seam that consumes the accepted backend-neutral SF-IMP-0044 surface-support evaluator without copying Minecraft's structure-set selection logic or moving Minecraft semantics into backend-neutral modules.

## Verified implementation

The accepted implementation:

- widens only Minecraft 1.21.1 `ChunkGenerator.tryGenerateStructure(...)` to `protected` through `META-INF/accesstransformer.cfg`;
- overrides that method in `SkyforgeNoiseBasedChunkGenerator`;
- calls the vanilla implementation exactly once per wrapped candidate;
- snapshots and restores native structure starts when Skyforge rejects an elevated candidate;
- returns `false` after rejection so vanilla's existing weighted fallback loop remains effective;
- enriches early Skyforge height answers with independent `SkyIslandWorldVolumeId` provenance;
- records provenance only when Skyforge strictly raises the height above vanilla terrain;
- rejects ambiguous multi-volume provenance rather than fusing stacked surfaces;
- translates the real generated `StructureStart` bounding box into `SurfaceSupportRequirements`;
- delegates footprint suitability to `SkyIslandSurfaceSupportEvaluator` in `skyforge-world`;
- leaves native-ground/equal-height structures untouched by the new admission filter.

## Automated evidence

CI #112 completed successfully on implementation head `618b20d81b56c284f70a6a0e1841f9394e652ac6`.

The full repository gate passed:

- Java 25 workspace build;
- Java 21 NeoForge compilation;
- backend-independence verification;
- all unit/integration tests;
- fixed-seed evidence generation and artifact publication;
- suspended-volume evidence generation and artifact publication.

NeoForge integration tests specifically prove:

1. the Access Transformer is applied at transformed test runtime and `ChunkGenerator.tryGenerateStructure(...)` is `protected`;
2. an early Skyforge height claim carries the actual independent world-volume identity;
3. distinct claimed volumes remain distinct inside a candidate trace;
4. nested candidate traces are rejected;
5. a native Minecraft `BoundingBox` translates deterministically to the initial backend-owned neutral support policy.

The first CI attempt (#111) also proved production NeoForge compilation and Access Transformer processing; it failed only because javac `-Werror` rejected an unused try-with-resources variable in the new test. That warning was corrected without changing production behavior before successful CI #112.

## Architectural invariants confirmed

- No Minecraft or NeoForge type was added to `skyforge-world`.
- Skyforge does not reproduce `ChunkGenerator.createStructures(...)`.
- Structure placement remains vanilla-first and Minecraft-owned.
- Backend support evaluation cannot merge independent vertically stacked island volumes.
- Rejection removes the newly generated start rather than altering island geometry.
- The initial support thresholds are Minecraft adapter policy rather than world-generation constants.

## Deferred scope

SF-IMP-0045 deliberately does not flatten or accommodate terrain for structures. It also does not yet specialize support footprints by structure type or individual piece topology. Those concerns belong to later adapter policy, beginning with SF-IMP-0046 accommodation design.
