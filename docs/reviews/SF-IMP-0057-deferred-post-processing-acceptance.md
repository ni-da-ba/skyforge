# SF-IMP-0057 — Deferred native post-processing acceptance

**Status:** Accepted

## Scope

SF-IMP-0057 closes the lifecycle gap exposed when SF-IMP-0056 deferred exact-volume realization and native surface population onto already-loaded stable Minecraft `LevelChunk`s.

Ordinary Minecraft world generation records some feature post-processing through proto-chunk lifecycle semantics. Replaying those features later against stable chunks originally fell through to an unsupported `ChunkAccess.markPosForPostprocessing` path. The accepted implementation preserves that native work while keeping exact-volume ownership and deferred stable-chunk realization intact.

The final runtime also exposed a second stable-world distinction: low-level writes into client-visible chunks require lighting and block-change synchronization that ordinary generation-time writes do not. SF-IMP-0057 therefore owns both deferred lifecycle seams without changing the generation-time writer contract.

## Accepted implementation

Exact accepted implementation head:

```text
0411d0c41f81c8453684c8396adcbd45b952bf08
```

PR: **#77 — `SF-IMP-0057: preserve deferred native post-processing semantics`**

Merged to `main` as:

```text
9b93ebd25a7917b7b934e89144480c1f026c286c
```

Issue **#64** was closed completed after merge.

## Accepted design

The deferred native-population path now:

1. opens an exact Skyforge population scope on one stable `LevelChunk`;
2. snapshots and temporarily detaches any pre-existing native packed post-processing marks for that chunk;
3. allows the deferred Skyforge feature to create only its own live post-processing marks;
4. resolves those Skyforge-created marks through `LevelChunk#postProcessGeneration()` while exact-volume execution remains active;
5. requires the isolated Skyforge queue to drain;
6. restores the pre-existing native snapshot unchanged for Minecraft's own later lifecycle;
7. fails closed on abnormal scope completion, clearing unflushed Skyforge work and restoring native state.

Direct `WorldGenRegion` population remains unchanged.

Deferred exact terrain catch-up additionally opens a stable-chunk mutation lifecycle. Actual changed positions are submitted to Minecraft's light engine and block-change broadcaster so authoritative server terrain and tracking clients converge after low-level chunk writes. This runtime-only synchronization scope closes before native population begins and is never entered by ordinary generation-time realization.

## Runtime discoveries retained in the engineering record

The first repaired runtime head, `56f617adfd9847e7424f4bc47d6bae675aa83186`, proved that the interception seam worked but crashed on an invalid assumption that a promoted stable `LevelChunk` must have an empty native post-processing queue. Chunk `[-2, 0]` legitimately retained native pending work. The final design therefore preserves rather than rejects pre-existing native marks.

A later runtime on `4e29cff6de431304d5fefbae494207a3d144a993` passed server-side admission and population but visually exposed stale client chunk geometry/lighting: part of the admitted island was invisible or dark while server collision remained present. This demonstrated that generation-time and stable-world low-level writes are not lifecycle-equivalent even when authoritative server block state is correct. The deferred mutation lifecycle was added narrowly to repair that distinction.

## Automated evidence

The final PR merge-result CI run compiled successfully and completed the repository Gradle build/test suite, backend-independence verification, NeoForge/FML initialization, and Mixin bootstrap.

Its only failing step was the shared `Verify evidence review entry points` shell assertion after parallel authorship work added hydrologic evidence paths to the workflow without adding the corresponding corpus generation task to the build command. The implementation lane did not patch that unrelated authorship/CI wiring defect. The project owner explicitly authorized merge after confirming the red condition was outside SF-IMP-0057.

## Runtime fixture

The accepted interactive run used:

```text
:skyforge-neoforge-1211:runPhysicalAdmissionClient
```

in a new disposable Skyforge Development world on exact head `0411d0c41f81c8453684c8396adcbd45b952bf08`.

The run emitted the inherited terminal marker:

```text
SF-IMP-0056 PHYSICAL ADMISSION PASS
```

with the accepted semantic values:

- lower candidate: terminal `REJECTED` on native bedrock conflict with `preserved=true`;
- upper candidate: terminal `ADMITTED`;
- `observedChunks=25`;
- `requiredChunks=25`;
- `pendingCatchup=0`;
- `originSurfaceY=248`;
- `completedPopulationPhases=21`;
- `expectedPopulationPhases=21`.

The prior unsupported post-processing warning was absent, the retained-native-queue crash did not recur, and no unavailable chunk generation was required.

## Visual acceptance

Manual inspection confirmed:

1. the upper floating island rendered continuously rather than exhibiting the earlier invisible/dark stable-chunk slice;
2. native vegetation remained present and persistent;
3. the deliberately conflicting lower Skyforge candidate remained absent;
4. native Minecraft content below, including the woodland mansion in the proof world, remained present;
5. no obvious cross-domain terrain destruction was observed.

The visible grass/sand split across the island was accepted as transitional behavior of the current native-surface adaptation model rather than a post-processing defect. Client-visible island-owned biome authorship remains deferred.

No ores were expected from this proof because the currently admitted native population scope remains surface-oriented; complete bounded underground decoration, ore generation, caves, and geology are later miniature-world capabilities.

## Accepted invariant

> Deferred Skyforge realization on a stable loaded Minecraft chunk must preserve Minecraft's pre-existing native post-processing work, resolve only Skyforge-created post-processing inside the owning exact-volume scope, synchronize actual stable-world terrain changes to lighting and tracking clients, and leave ordinary generation-time worldgen semantics unchanged.

This invariant supplements SF-IMP-0056 physical admission. Together, the accepted path can reject conflicting exact volumes without mutation, admit clear finite volumes atomically, catch up only through already-loaded stable chunks, and replay native surface population without losing Minecraft post-processing semantics or leaving client-visible terrain stale.

## Deferred work

Separate follow-on concerns remain intentionally outside this milestone:

- persistent client-visible island-owned biome identity and tint presentation (#78 / SF-IMP-0058, deliberately deferred until upstream biome authorship matures);
- cross-volume native structure terrain projection (#52 / SF-IMP-0051);
- bounded caves, carvers, ores, underground hydrology, and deeper native worldgen phases;
- full Skyforge-authored environmental fields and their Minecraft realization.
