# SF-IMP-0055 — Native exact-volume surface population acceptance

**Status:** Accepted

## Scope

SF-IMP-0055 promotes the accepted SF-IMP-0054 biome-population proof into a reusable native surface-population planner, coordinator, and runtime binding.

This milestone admits `VEGETAL_DECORATION` only. Hydrology, caves/underground decoration, ores, structures, and `TOP_LAYER_MODIFICATION` are intentionally excluded.

## Automated evidence

Initial implementation head `d10a586f771786b89ebdf771e71b453543986c03` passed full repository CI as run **#302**, including NeoForge/FML bootstrap, unit tests, fixed-seed evidence, and suspended-volume evidence.

Final pre-interactive documentation head `6e6ba3e39e0b8f576535892a6971fbed22bf8e4a` passed full repository CI as run **#304**.

The final acceptance-documentation head must also remain green before merge.

## Runtime fixture

The accepted interactive run used:

```text
:skyforge-neoforge-1211:runSurfacePopulationClient
```

in a new disposable Skyforge Development world on exact head:

```text
6e6ba3e39e0b8f576535892a6971fbed22bf8e4a
```

The fixture reused the accepted broad vertically stacked tablelands from SF-IMP-0054:

- lower exact volume -> `minecraft:forest`;
- upper exact volume -> `minecraft:taiga`.

Unlike SF-IMP-0054, the fixture itself did not iterate biome features. It installed a `SkyforgeNativeSurfacePopulationStage` plan resolver. The generator's existing post-realization population callback invoked the stage, which resolved actual owned terrain samples and routed each plan through `SkyforgeNativeSurfacePopulationCoordinator`.

Immediately afterward, the development observer invoked the same stage a second time for the same chunk. Every populated phase in that second call reported `executedNow=false`.

## Accepted runtime evidence

The run emitted:

```text
SF-IMP-0055 SURFACE POPULATION COORDINATED PASS:
observedChunks=25,
completedPhases=50,
replayExecutedPhases=0,
admittedPhases=[VEGETAL_DECORATION],
lower={biome=minecraft:forest, chunks=25, attempted=225, successful=55, attachments=9736, logs=933, leaves=7435},
upper={biome=minecraft:taiga, chunks=25, attempted=250, successful=60, attachments=10504, logs=1145, leaves=8443}
```

This proves:

- 25 observed chunks × 2 exact volumes × 1 admitted phase produced the expected 50 completed lifecycle keys;
- the immediate replay executed **zero** native phases;
- only `VEGETAL_DECORATION` was admitted;
- both exact domains retained substantial persistent native tree ecology through the reusable coordinator.

## Visual acceptance

Interactive inspection reported that the visuals were good:

1. both vertically stacked broad tablelands existed;
2. the lower island remained visibly forest-like;
3. the upper island remained visibly taiga-like;
4. vegetation remained spatially distributed and native-looking;
5. ordinary terrain beneath the islands appeared visually normal;
6. no obvious vegetation crossed from one vertical owner into the other.

## Separate composition warning observed

The same run emitted Minecraft warnings for chest/banner block entities around Y=128 whose corresponding native structure blocks had become Skyforge stone. This does **not** implicate the 0055 surface-population coordinator: the warning occurs because the development lower island's solid 3-D volume physically overlaps an already-generated native structure.

That is a distinct world-composition invariant that must be addressed before Skyforge treats arbitrary planned island placement as production-safe. It is not a reason to replay or broaden the 0055 population phase. The follow-up architecture must distinguish generation-domain isolation (already accepted) from physical 3-D occupancy collision between independently generated domains.

## Regression significance

The accepted SF-IMP-0054 run demonstrated that exact-volume biome semantics can drive ordinary Minecraft forest and taiga content, including bee-bearing forest behavior. SF-IMP-0055 proves that the same result now flows through a reusable lifecycle coordinator and remains idempotent under duplicate orchestration requests.

The accepted lifecycle invariant is:

> For one exact Skyforge volume, chunk, and admitted semantic population phase, native population executes at most once per coordinator lifecycle; repeated equivalent requests return the original result without re-running Minecraft feature occurrence.

## Merge gate

SF-IMP-0055 is accepted once:

- the final acceptance-documentation head is green;
- ADR-0058 is marked Accepted;
- PR #61 records this evidence.
