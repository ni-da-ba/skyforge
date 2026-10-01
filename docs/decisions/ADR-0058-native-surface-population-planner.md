# ADR-0058: Native exact-volume surface population planner

**Status:** Accepted

## Context

ADR-0057 proved that an exact Skyforge terrain volume can expose one final-registry Minecraft biome to native placed-feature execution while preserving independent vertical ownership and BASE_WORLD isolation.

That proof was intentionally fixture-driven: the development runtime selected a biome, iterated one biome generation step, and accumulated acceptance evidence. The next architectural question is how that behavior becomes a reusable runtime system without turning Skyforge into a replay of Minecraft's entire chunk decoration pipeline.

Minecraft's biome generation steps mix several different semantic systems: surface ecology, lakes and fluid behavior, underground decoration, ores, structures, and top-layer thermal effects. Treating all of them as one generic "biome population" operation would recreate the same global-world-column coupling that exact-volume generation was introduced to eliminate.

## Decision

Skyforge introduces a production-facing **native surface population planner and coordinator**.

The planner identifies:

- one exact `SkyIslandWorldVolumeId`;
- one exact-volume biome/environment resolver;
- an ordered list of semantically admitted native generation phases;
- one bounded attachment policy.

The coordinator executes each admitted `(volume, chunk, phase)` at most once during the chunk-generation lifecycle and caches the native result. Repeated requests for the same lifecycle key return the cached result without re-running placed features.

The first admitted phase is:

```text
VEGETAL_DECORATION
```

The following are explicitly **not** admitted by ADR-0058:

```text
LAKES / fluid-hydrology phases
UNDERGROUND_ORES
UNDERGROUND_DECORATION
structure phases
TOP_LAYER_MODIFICATION
```

`TOP_LAYER_MODIFICATION` is deferred even though it occurs at the surface because its snow/ice behavior crosses the later thermal/hydrology boundary. Future ADRs may admit it once those semantics are modeled explicitly.

## Phase semantics, not feature IDs

Admission occurs at semantic generation-step granularity. Skyforge does not maintain a whitelist of vanilla or modded placed-feature IDs.

Once a phase is admitted, the Minecraft adapter consumes the selected biome's **live final-registry** feature list for that phase. Datapacks and NeoForge/mod biome modifications therefore remain eligible without bespoke feature mappings.

This preserves the compatibility principle established in ADR-0057:

> reuse Minecraft definitions, not Minecraft ownership assumptions.

## Lifecycle idempotency

One exact population lifecycle key is:

```text
(volumeId, chunkPos, semanticPhase)
```

It is intentionally coarser than individual placed-feature identity. The biome's native feature list is one ordered phase payload and must not be replayed because a neighboring chunk, adapter callback, or development observer asks for the same volume/chunk phase twice.

The coordinator therefore:

1. resolves one actual owned surface sample inside the target chunk;
2. evaluates biome intent at that owned sample rather than blindly using the chunk center;
3. executes the native phase once;
4. caches the phase result;
5. returns a no-op replay result on subsequent requests.

If the surface sample, biome assignment, or attachment policy changes after the phase has completed, the replay is rejected as an invalid generation-lifecycle mutation instead of silently redecorating under different semantics.

Minecraft chunk status already prevents normal decoration from being replayed on ordinary save/reload. The coordinator guard protects Skyforge's own orchestration from duplicate execution while a chunk is being generated.

## Runtime binding

`SkyforgeNativeSurfacePopulationStage` is supplied by a higher-level plan resolver. The Minecraft chunk generator does not query Skyforge's catalog directly to decide which plans exist.

This preserves dependency direction:

```text
Skyforge world/environment runtime
        -> surface population plans
        -> Minecraft native surface population stage
        -> exact-volume native feature runner
```

The stage is inert when no resolver is installed.

The existing accepted post-realization callback is reused rather than adding a new generator override for every population milestone.

## Exact biome sample

ADR-0057's fixture initially resolved biome intent using chunk-center X/Z plus a known surface Y. ADR-0058 promotes an actual exact-volume owned surface coordinate to the biome resolver.

This is necessary for future within-island climate/ecology fields: a chunk may intersect an island only near one edge, and an empty chunk center must not become the semantic biome sample.

## Consequences

- Surface ecology now has a reusable lifecycle coordinator rather than a fixture-specific loop.
- Duplicate surface-population calls cannot duplicate native vegetation for the same exact volume/chunk/phase.
- Stacked volumes at one X/Z retain independent phase ledgers and biome semantics.
- Biome mods can continue contributing vegetation through final-registry generation settings.
- Hydrology, caves, ores, structures, and top-layer thermal effects remain explicitly outside this scheduler until separately admitted.
- The plan resolver becomes the later connection point from backend-neutral environment/climate intent to Minecraft biome semantics.

## Acceptance evidence

SF-IMP-0055 was accepted on exact runtime head `6e6ba3e39e0b8f576535892a6971fbed22bf8e4a`.

Automated evidence:

- CI run **#302** passed on implementation head `d10a586f771786b89ebdf771e71b453543986c03`;
- CI run **#304** passed on pre-interactive documentation head `6e6ba3e39e0b8f576535892a6971fbed22bf8e4a`.

Interactive evidence:

```text
SF-IMP-0055 SURFACE POPULATION COORDINATED PASS:
observedChunks=25,
completedPhases=50,
replayExecutedPhases=0,
admittedPhases=[VEGETAL_DECORATION],
lower={biome=minecraft:forest, chunks=25, attempted=225, successful=55, attachments=9736, logs=933, leaves=7435},
upper={biome=minecraft:taiga, chunks=25, attempted=250, successful=60, attachments=10504, logs=1145, leaves=8443}
```

Visual inspection confirmed good forest-vs-taiga differentiation, distributed native-looking vegetation, normal-looking ordinary terrain beneath the islands, and no obvious cross-volume ecology contamination.

The same development run also exposed a separate physical 3-D collision between the low fixture island and an already-generated native structure, visible through chest/banner block-entity warnings after Skyforge stone replaced their blocks. That is **not** a surface-population replay failure. It is a distinct world-composition/occupancy policy problem: generation domains can be observationally isolated yet still physically intersect when realized. That defect is tracked separately and does not expand the set of phases admitted by this ADR.

The accepted invariant is therefore:

> Surface ecology is scheduled semantically and idempotently per exact volume/chunk/phase. Minecraft supplies the live native content for admitted phases, while Skyforge preserves domain identity, lifecycle ownership, and explicit exclusion of unrelated worldgen systems.
