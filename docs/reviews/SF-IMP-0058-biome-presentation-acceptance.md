# SF-IMP-0058 — Exact-volume biome presentation acceptance

**Status:** Accepted

## Scope

SF-IMP-0058 closes the gap between Skyforge's execution-scoped exact-volume biome identity and Minecraft's durable/client-visible biome representation.

Before this milestone, an admitted Skyforge volume could resolve as `minecraft:taiga` while native population executed, yet the stored chunk biome data and client presentation could still reflect the vertically unrelated BASE_WORLD biome below.

## Accepted implementation

Exact accepted implementation head:

```text
7c8515a6b6794c860432c4842ccbcde49790602e
```

PR: **#90 — `SF-IMP-0058: persist client-visible exact-volume biome presentation`**

Merged to `main` as:

```text
d9dec0928f4ff3a20b53b3027c299a6d19466c52
```

Issue **#78** closed completed with the merge.

## Accepted design

The Minecraft adapter now treats biome presentation as a finite post-admission obligation for each exact volume/chunk:

1. reuse the volume's existing `SkyforgeExactVolumeBiomeResolver` rather than introducing a second Minecraft-biome mapping;
2. service only already-loaded stable `LevelChunk`s and never create chunk tickets or force unavailable chunks;
3. quantize exact Skyforge ownership to Minecraft's native 4×4×4 biome cells;
4. claim cells containing exact owned solid terrain plus the immediate surface-air cell needed for player/F3 biome reads;
5. preserve every unclaimed biome cell from the pre-existing chunk storage;
6. fail closed if two exact Skyforge volumes require incompatible identities inside the same indivisible quart cell;
7. mark changed chunks unsaved so normal Minecraft chunk persistence owns disk serialization;
8. refresh tracking clients through vanilla `ClientboundChunksBiomesPacket` rather than custom networking.

The implementation remains entirely in the NeoForge/Minecraft adapter. Backend-neutral Skyforge authorship still does not contain Minecraft biome IDs.

## Runtime evidence

The accepted interactive run used:

```text
:skyforge-neoforge-1211:runBiomePresentationClient
```

on exact head `7c8515a6b6794c860432c4842ccbcde49790602e` in a fresh disposable Skyforge Development world.

The inherited physical-admission proof passed:

```text
SF-IMP-0056 PHYSICAL ADMISSION PASS
```

with:

- lower candidate `REJECTED` on native bedrock conflict with `preserved=true`;
- upper candidate `ADMITTED`;
- `observedChunks=25`;
- `requiredChunks=25`;
- `pendingCatchup=0`;
- `originSurfaceY=248`;
- `completedPopulationPhases=21`;
- `expectedPopulationPhases=21`.

The new presentation proof emitted:

```text
SF-IMP-0058 BIOME PRESENTATION PASS
```

with the decisive values:

- upper surface biome: `minecraft:taiga`;
- upper standing-position biome: `minecraft:taiga`;
- pending biome-presentation obligations: `0`;
- same X/Z sample at Y=150: stored `minecraft:frozen_ocean`;
- native generator expectation at Y=150: `minecraft:frozen_ocean`;
- unrelated same-column BASE_WORLD cell preserved: `true`.

This proves that the accepted adapter can persist island-owned biome identity without turning the entire X/Z column into that biome.

## Visual acceptance

Manual inspection confirmed the upper island existed normally, was identified as taiga, and retained taiga vegetation while the world below remained a frozen-ocean domain.

The island still showed substantial ice, gravel, and other frozen-ocean-derived surface material. That appearance is **not** a failure of SF-IMP-0058. The current transitional surface-material adapter still borrows completed native surface material as representation guidance, while biome identity/population is now volume-owned. The result is therefore intentionally hybrid until Skyforge-authored geology/soil/surface-material fields replace that borrowed material model.

## Automated evidence

The 0058 implementation compiled and passed the Gradle test suite, backend-independence verification, NeoForge/FML initialization, and Mixin bootstrap. A later PR check failed only in the shared authorship evidence-entry-point shell assertion after parallel authorship work advanced the expected evidence corpus without updating the generation command; the actual build/test step completed successfully. The project owner explicitly authorized merge after runtime acceptance.

## Accepted invariant

> An admitted Skyforge exact volume may persist its Minecraft biome expression into the native 3-D quart-biome storage only where that volume owns the local presentation envelope, while vertically unrelated BASE_WORLD or other Skyforge domains at the same X/Z remain independently represented.

## Deferred work

SF-IMP-0058 does not yet provide:

- mature Skyforge-authored climate/ecology-to-Minecraft-biome translation;
- Skyforge-authored soil/geology/surface-material realization;
- atmospheric/open-sky biome fields;
- underground resource/geology population;
- cave/carver or hydrology realization.

The next Minecraft integration milestone is SF-IMP-0059, which investigates volume-local vertical virtualization for native underground placement before admitting `UNDERGROUND_ORES`.