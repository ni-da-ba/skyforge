# ADR-0044 — Open-Water Supplemental Surface Exposure

**Status:** Accepted

## Context

SF-IMP-0039 accepted the first Minecraft-owned suitability classes for supplemental lower surfaces. Its real-client ocean proof also exposed the next concrete distinction: `submerged_water_floor` correctly includes any qualifying floor with water above it, but aquatic feature families may need to distinguish a vertically open water column from an enclosed flooded cavity.

This is a physical exposure problem, not yet a biome problem. Solving it does not require Skyforge climate fields, ocean biome ownership, sea-level inference, or a second feature engine.

## Decision

SF-IMP-0040 adds one new Minecraft-owned suitability value to the existing `skyforge:suitable_surfaces` modifier:

```text
open_water_floor
```

`open_water_floor` is a strict subset of `submerged_water_floor`.

A candidate qualifies when:

1. its support is non-air and non-fluid;
2. the placement cell immediately above the support is Minecraft water;
3. the support has the accepted local thickness requirement used by open-placement engineering rules; and
4. scanning vertically upward from the placement cell encounters only water until the first non-water cell, and that first non-water cell is air.

If a solid or other non-water block is encountered before air, the candidate remains `submerged_water_floor` but is not `open_water_floor`.

## Why vertical exposure rather than sky visibility

Direct sky visibility would incorrectly reject ordinary ocean floor underneath a floating Skyforge island.

The criterion instead asks whether the local water column itself reaches an air surface before a ceiling:

```text
floating Skyforge island
██████████████████

air gap             <- enough to establish open water
~~~~~~~~~~~~~~~~~~   water surface
~~~~~~~~~~~~~~~~~~
~~~~~~~~~~~~~~~~~~
seabed               <- open_water_floor
██████████████████
```

An enclosed flooded cavity behaves differently:

```text
solid ceiling
██████████████████
~~~~~~~~~~~~~~~~~~
~~~~~~~~~~~~~~~~~~
flooded floor         <- submerged_water_floor only
██████████████████
```

A vertical shaft or cave opening that genuinely connects the water column to open air may still qualify. This milestone does not claim a full three-dimensional cave-enclosure metric.

## Architectural boundary

This remains Minecraft/NeoForge adapter logic because it reads concrete Minecraft block/water state.

No new backend-neutral climate, biome, ocean, water, or feature identity concepts are introduced. Future Skyforge-owned biome/environment fields remain compatible with this design and may later control where oceanic intent occurs; this adapter rule would still determine concrete Minecraft placement suitability.

## Development proof

The SF-IMP-0040 development world retains the accepted probes and adds a sparse diamond marker for `open_water_floor`.

- emerald: `dry_open`;
- lapis: `submerged_water_floor`;
- diamond: `open_water_floor`.

The diagnostic log reports total and emitted counts for both submerged classes.

The expected relationship is:

```text
openWaterFloor <= submergedWaterFloor
```

In an ordinary ocean column both classes may be present. In a flooded column capped by a solid ceiling, only `submerged_water_floor` should remain.

## Invariants

1. Accepted SF-IMP-0038 reachability remains unchanged.
2. Accepted SF-IMP-0039 suitability names and behavior remain available.
3. `open_water_floor` is a strict subset of `submerged_water_floor`.
4. A solid ceiling before the water column reaches air rejects `open_water_floor`.
5. A floating island above an ocean air gap does not reject `open_water_floor`.
6. Highest-surface ownership remains vanilla.
7. No neighbor chunk loads or horizontal world scans are introduced.
8. Development-only marker data remains absent from production artifacts.

## Explicit non-goals

SF-IMP-0040 does not yet claim:

- final kelp/seagrass suitability;
- ocean biome detection;
- sea-level semantics;
- horizontal cave enclosure or exposure;
- current/flowing-water policy;
- modded fluid support;
- final water-depth thresholds;
- Skyforge biome-field ownership.

## Acceptance evidence

Automated validation covers the positive and negative exposure semantics: an upward water column reaching air qualifies as `open_water_floor`, while a solid ceiling rejects `open_water_floor` without removing `submerged_water_floor`. Prior dry/submerged suitability regressions, backend independence and development-resource packaging boundaries remain part of the focused SF-IMP-0040 gate.

The accepted real-client specimen generated over dry desert terrain rather than ocean. Origin-area diagnostics consistently reported dry candidates and no aquatic candidates. The `open_water_floor` development feature was nevertheless loaded and queried, and correctly emitted zero positions. This is accepted as the live false-positive/runtime-loading proof for this milestone rather than requiring repeated world creation until an ocean seed appears.

A live positive diamond-marker realization was therefore not observed in the SF-IMP-0040 client specimen. That limitation is explicit. The positive classifier behavior is established by automated tests, while SF-IMP-0039 already established the generic live supplemental configured-feature realization path on submerged Minecraft terrain.

## Acceptance criteria

ADR-0044 is accepted because:

1. automated tests prove an upward water column reaching air qualifies as `open_water_floor`;
2. automated tests prove a solid ceiling rejects `open_water_floor` while preserving `submerged_water_floor`;
3. prior dry and submerged suitability regressions remain green;
4. backend independence and repository-wide validation pass;
5. the development resources load in a real client;
6. the dry desert client specimen reports no aquatic candidates or emissions, demonstrating correct false-positive suppression;
7. the parameterized open-water probe is queried successfully in the live feature pipeline;
8. no systematic supplemental marker appears on the highest Massif surface;
9. the world saves and shuts down cleanly; and
10. development-only SF-IMP-0040 fixtures remain excluded from the production JAR.

The milestone does not claim a real-client positive `open_water_floor` placement observation; that remains a useful future regression when an ocean specimen is naturally available.