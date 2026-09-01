# SF-IMP-0042 — Early Generator Height Query Runbook

**Status:** Implementation ready for automated validation.

## Purpose

Prove that Minecraft generator height queries can observe an already-compiled Skyforge island before the island is physically written into a chunk.

This is the prerequisite seam for later native structure-start integration.

## Automated gate

From the repository root:

```bat
git fetch origin
git switch agent/sf-imp-0042
git pull --ff-only
scripts\verify-sf-imp-0042-early-height-queries.bat
gradlew.bat check
```

## Expected proof

The focused test suite establishes:

- no active runtime binding -> no Skyforge early-height answer;
- active development binding -> the origin Massif contributes its exact first-free Y;
- `WORLD_SURFACE_WG` and `OCEAN_FLOOR_WG` both recognize the current solid backend palette;
- a distant column outside the specimen contributes no Skyforge answer;
- the generator combines the answer with vanilla through `max(vanilla, skyforge)`;
- post-surface realization and supplemental feature-stage regressions remain intact.

## No client gate

There is intentionally no visual client acceptance for SF-IMP-0042. `getBaseHeight` is a non-mutating query seam; forcing a visual proxy would add diagnostic machinery that is less meaningful than the actual consumer we need.

The next milestone should use this seam in an early native consumer—preferably structure-start placement—where success can be inspected in-game.

## Limitations

The first implementation may materialize a full backend chunk to answer one column height. That is accepted for correctness proof only. A specialized/cached height-query path can be introduced after native consumers demonstrate the required call pattern and performance pressure.
