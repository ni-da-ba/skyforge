# SF-IMP-0055 — Native exact-volume surface population acceptance

**Status:** Pending interactive acceptance

## Scope

SF-IMP-0055 promotes the accepted SF-IMP-0054 biome-population proof into a reusable native surface-population planner, coordinator, and runtime binding.

This milestone admits `VEGETAL_DECORATION` only. Hydrology, caves/underground decoration, ores, structures, and `TOP_LAYER_MODIFICATION` are intentionally excluded.

## Automated evidence

Initial implementation head `d10a586f771786b89ebdf771e71b453543986c03` passed full repository CI as run **#302**, including NeoForge/FML bootstrap, unit tests, fixed-seed evidence, and suspended-volume evidence.

Documentation commits move the PR head; the final exact head must remain green before interactive acceptance and merge.

## Runtime fixture

Run:

```text
:skyforge-neoforge-1211:runSurfacePopulationClient
```

in a **new disposable Skyforge Development world**.

The fixture reuses the accepted broad vertically stacked tablelands from SF-IMP-0054:

- lower exact volume -> `minecraft:forest`;
- upper exact volume -> `minecraft:taiga`.

Unlike SF-IMP-0054, the fixture itself does not iterate biome features. It installs a `SkyforgeNativeSurfacePopulationStage` plan resolver. The generator's existing post-realization population callback invokes the stage, which resolves actual owned terrain samples and routes each plan through `SkyforgeNativeSurfacePopulationCoordinator`.

Immediately afterward, the development observer invokes the same stage a second time for the same chunk. Every populated phase in that second call must report `executedNow=false`.

## Hard runtime marker

Acceptance requires:

```text
SF-IMP-0055 SURFACE POPULATION COORDINATED PASS
```

with:

- `observedChunks=25`;
- `replayExecutedPhases=0`;
- `admittedPhases=[VEGETAL_DECORATION]`;
- a completed-phase ledger equal to the number of terrain-owning lower + upper chunk phase keys;
- lower biome `minecraft:forest`;
- upper biome `minecraft:taiga`;
- nonzero native successful-feature counts for both volumes;
- nonzero persistent `logs` and `leaves` for both volumes.

A duplicate replay that executes a native phase, a phase-policy leak, a changed biome/surface/policy after execution, or an inconsistent ledger is a hard failure.

## Visual acceptance

Confirm:

1. the two vertically stacked broad tablelands exist;
2. the lower island remains visibly forest-like;
3. the upper island remains visibly taiga-like;
4. vegetation is spatially distributed and native-looking;
5. BASE_WORLD terrain and decoration remain visually normal beneath them;
6. no obvious vegetation crosses from one vertical owner into the other;
7. no lakes, ore passes, underground decoration, structure population, or top-layer phase has been introduced by this milestone.

## Regression significance

The accepted SF-IMP-0054 run demonstrated that exact-volume biome semantics can drive ordinary Minecraft forest and taiga content, including bee-bearing forest behavior. SF-IMP-0055 is not expected to make that ecology more visually sophisticated. Its new proof is lifecycle-oriented: the same visible result must now come through the reusable population coordinator and remain unchanged under an immediate duplicate request.

## Merge gate

Do not merge PR #61 until:

- final exact-head CI is green;
- the hard runtime marker passes;
- the visual criteria above pass;
- ADR-0058 is promoted to Accepted and acceptance evidence is recorded.
