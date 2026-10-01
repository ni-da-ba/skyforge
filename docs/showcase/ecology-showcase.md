# SF-IMP-0080 — dedicated land-ecology showcase

This world is the human-facing ecology specimen for **SF-IMP-0080 / issue #194**. It is deliberately separate from the compact current-capability cave/interior showcase.

The existing `:skyforge-neoforge-1211:launchShowcase` remains the technical authority for stacked composed caves, post-cave interior population, generated-fluid fencing, and persisted-fluid reopen. SF-IMP-0080 does not cosmetically decorate or replace that fixture.

## What this specimen proves

The ecology showcase reuses the accepted SF-IMP-0054 / SF-IMP-0055 broad TABLELAND ecology shape and native biome identities:

- lower volume: `minecraft:forest`;
- upper volume: `minecraft:taiga`.

The pair is translated into clear high-air bands so it can exercise the current whole-volume physical-admission lifecycle instead of bypassing it. Preparation then uses the normal Minecraft-facing production seams:

```text
native/base world generation
    -> immutable native surface snapshot
    -> whole-volume physical admission
    -> deferred stable-chunk terrain catch-up
    -> exact-volume native surface population
    -> durable exact-volume biome presentation
    -> save
    -> mutation-inert actual-client reopen
```

The preparation proof requires both volumes to:

- reach terminal `ADMITTED` only after their complete finite footprint reports evidence;
- finish all deferred terrain obligations;
- finish their exact surface-population obligations;
- retain distinct forest-versus-taiga native placed-feature identity;
- contain final-world land substrate;
- contain persistent logs and leaves;
- contain persistent non-tree surface plants;
- finish durable biome presentation with no pending obligations.

The automated viewer then reopens the saved world with **only compiled terrain ownership restored**. Admission, surface population, caves, interior population, and biome-presentation mutation remain inert. The saved forest/taiga biome identities and ecology must still be present.

## Launch for human review

From the repository root:

### Windows

```powershell
.\gradlew.bat :skyforge-neoforge-1211:launchShowcaseEcology --no-configuration-cache
```

### macOS / Linux

```bash
./gradlew :skyforge-neoforge-1211:launchShowcaseEcology --no-configuration-cache
```

Preparation creates a fresh deterministic world under:

```text
skyforge-neoforge-1211/run-skyforge-showcase-ecology/saves/ecology
```

To reopen an already-prepared world without rebuilding it:

```text
:skyforge-neoforge-1211:runShowcaseEcologyClient
```

## Guided stops

Use:

```text
/skyforge_ecology
```

The review stops are:

| Stop | Command | Inspect |
| --- | --- | --- |
| Panorama | `/skyforge_ecology panorama` | Both broad stacked land surfaces and their vertical separation. |
| Lower forest | `/skyforge_ecology lower_forest` | Land substrate, forest trees/foliage, non-tree plants, attachment plausibility. |
| Upper taiga | `/skyforge_ecology upper_taiga` | Independent taiga surface, spruce/taiga character, plants, comparison with the lower forest. |

## Required human gate

Automated counters are necessary but **not sufficient** for SF-IMP-0080 acceptance.

A human reviewer must confirm in the actual Minecraft client that:

1. both exact-volume surfaces read clearly as land rather than gravel/ocean-floor technical fixtures;
2. trees and foliage are plainly visible on both surfaces;
3. non-tree surface vegetation is visibly present;
4. the lower forest and upper taiga have a meaningful visual distinction;
5. vegetation is attached plausibly rather than obviously floating or unsupported;
6. save/reopen has not produced an obvious visual persistence defect.

This is an **ecology-legibility gate**, not production-morphology acceptance. The TABLELAND geometry is intentionally a proven review carrier. Issue #214 remains the later human-eye gate for production morphology, including silhouette, rim, section, underside, and flight/orbit review.

## Acceptance boundary

SF-IMP-0080 may be merged only after:

- normal repository CI is green;
- the dedicated ecology preparation proof passes;
- the mutation-inert actual-client ecology reopen proof passes;
- existing current-capability showcase acceptance remains green;
- relevant performance/regression gates remain green;
- the human ecology gate above is explicitly recorded.

Until then, the milestone remains **IN PROGRESS**.
