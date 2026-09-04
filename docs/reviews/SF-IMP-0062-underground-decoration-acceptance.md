# SF-IMP-0062 — Exact-volume underground decoration acceptance

Status: **ACCEPTED**

Issue: #110  
Pull request: #112  
Accepted feature head: `314f86a2cbc10ce23ab0f0931268305981ab95ff`  
Merge commit: `d6ec67e67f35d3d3d7214ddddcb04f4ba843c16b`

## Objective

Extend the accepted exact-volume native Minecraft population architecture into
`GenerationStep.Decoration.UNDERGROUND_DECORATION` after SF-IMP-0061 established persistent,
bounded cave topology.

The milestone had to prove that final-registry cave decoration could discover and modify real
post-carver Skyforge interiors without exposing vertically unrelated BASE_WORLD terrain or a
foreign stacked Skyforge volume.

## Accepted behavior

- `UNDERGROUND_DECORATION` is explicitly admitted to the local vertical-placement frame.
- The exact compiled owner-solid span at the already-selected X/Z column supplies the vertical
  support frame when one exists.
- Compiled occupancy remains the ownership/provenance authority while ordinary Minecraft
  `Level` reads expose actual post-carver AIR, allowing native cave predicates to see persistent
  carved surfaces.
- Native feature identity, configured-feature logic, placement ordering, and RNG remain registry
  native.
- Native height sampling completes before Skyforge maps the resulting Y coordinate.
- X/Z are never relocated to manufacture support.
- Exact-volume read/write fencing remains authoritative for owner, BASE_WORLD, and stacked-volume
  isolation.
- No placed-feature ID allowlist or copied cave-decoration definition was introduced.
- `FLUID_SPRINGS`, lakes, aquifer/water-table semantics, and Skyforge-authored cave morphology
  remain outside this milestone.

## Acceptance specimen

The final-registry `minecraft:dripstone_caves` biome was used as the discriminating specimen
because its `UNDERGROUND_DECORATION` phase exposes native cave-surface decoration.

This is an acceptance fixture choice, not production policy. Production authorization remains
generation-step and registry based.

## Runtime acceptance evidence

Two independent disposable worlds produced identical accepted evidence:

- proof chunks: 21
- native carver calls: 1,192
- carved blocks before decoration: 6,644
- attempted underground-decoration features: 42
- successful features: 33
- successful registry-native feature keys:
  - `minecraft:dripstone_cluster`
  - `minecraft:pointed_dripstone`
- height-range samples: 6,290
- mapped decoration samples outside exact volume: 0
- changed cave-neighborhood blocks: 4,362
- changed positions that were actual carved AIR: 2,031
- vertically unrelated BASE_WORLD proof columns preserved

Accepted deterministic invariants:

- carver transform digest: `10d4f06df3d8814f`
- carved-position digest: `9432af9ead2c865d`
- decoration transform digest: `1ed8887c547e0911`
- decoration digest: `ce242ec84fb8ccfc`

## Save/reload and logical-client persistence

The deterministic B run selected a real native decoration mutation at:

`BlockPos{x=-20, y=237, z=-4}`

with state:

`minecraft:pointed_dripstone[thickness=tip,vertical_direction=up,waterlogged=false]`

The server was stopped completely and the same world reopened through the automated Quick Play
client path without reinstalling Skyforge realization, carver, or population state.

Both gates passed:

- reload server observed the identical persisted block state;
- the actual logical client's `ClientLevel` independently observed the same block state.

Therefore native cave decoration is stored in ordinary Minecraft chunk persistence and reaches the
real client publication path.

## Stacked-volume isolation

The accepted stacked proof mapped one identical native sample at the same X/Z independently into
two vertically aligned Skyforge volumes:

- lower volume mapped to `Y=124`
- upper volume mapped to `Y=224`
- owner writes accepted
- foreign-volume writes rejected in both directions

The local cave-decoration frame therefore remains exact-volume scoped rather than column scoped.

## Regression gates

The final exact-head acceptance slate reproduced the prior accepted invariants:

### SF-IMP-0061 native carvers

- transform digest: `e97b5e7ee026c422`
- carve digest: `61f96a61f81c9b55`
- 1,376 persistent carved blocks
- mapped samples outside target: 0

### SF-IMP-0059 underground ores

- transform digest: `3397c516a115d6e4`
- mapped samples outside volume: 0
- BASE_WORLD proof column preserved

### SF-IMP-0060 local modifications

- transform digest: `4fe92d09d07f8002`
- mapped samples outside volume: 0
- BASE_WORLD proof columns preserved

## CI and harness repair

Exact-head normal CI and the dedicated SF-IMP-0062 automated acceptance workflow both passed.

During development, CI exposed an independent cache-contract defect in `skyforge-reference`:
reference-corpus tests create `build/evidence/**` review artifacts as side effects, but Gradle
could restore the Test task from cache without recreating those files. The later CI evidence-entry
check would then fail despite a successful cached test result.

The accepted branch disables Test-task caching for that reference evidence path, so CI now
materializes the evidence it subsequently verifies. The repaired normal CI passed on the accepted
head.

## Architectural consequence

Minecraft's native interior pipeline now reaches one important step farther down the exact Skyforge
volume stack:

`physical admission -> native carving -> persistent cave topology -> native underground decoration`

Native cave decoration can therefore operate on real carved Skyforge interiors while preserving
final-registry behavior, exact three-dimensional ownership, stacked-volume isolation, BASE_WORLD
isolation, persistence, and logical-client publication.

This does **not** yet define Skyforge-authored cave morphology or fluid-system semantics. Those
remain separate downstream concerns.
