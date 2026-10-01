# SF-IMP-0054 — Exact-volume biome bridge acceptance

**Status:** Accepted

## Scope

SF-IMP-0054 promotes the exact-volume population seam from one explicitly selected native placed feature to native biome-driven population.

The implementation resolves an exact `SkyIslandWorldVolumeId` to a final-registry Minecraft biome, exposes that biome only inside the owning population operation, and executes that biome's ordered `VEGETAL_DECORATION` placed features through the SF-IMP-0053 domain-local runner.

## Automated evidence

Final implementation head before acceptance documentation: `83d65aa01f60084c29cb294534ab6d3649089000`.

CI run **#298** passed on that exact implementation head, including NeoForge/FML mixin bootstrap and the established fixed-seed and suspended-volume evidence suites.

The final acceptance-documentation head is required to remain green before merge.

## Interactive fixture

Run:

```text
:skyforge-neoforge-1211:runBiomePopulationClient
```

in a **new disposable Skyforge Development world**.

The fixture stacks two development-only tableland volumes in the same X/Z region:

- lower volume: `minecraft:forest`;
- upper volume: `minecraft:taiga`.

The development tablelands are deliberately broad enough to provide a statistically meaningful native population sample. A deterministic 9×9 candidate chunk patch centered on the origin is scanned. A chunk is eligible only when at least 192 of its 256 X/Z columns contain an owned surface in **both** compiled exact volumes. This deliberately uses actual compiled terrain topology and substantial shared coverage rather than nominal `WorldBounds`, a single chunk center, or one surviving terrain column.

Each eligible chunk consumes the corresponding final biome's native `VEGETAL_DECORATION` list. Native placement modifiers choose count, rarity, X/Z positions, heightmap positions, biome eligibility, and configured-feature placement.

A single eligible chunk may legitimately yield zero successful placements. The fixture requires at least 25 eligible chunks and continues scanning the deterministic candidate region until both biome domains have produced persistent tree evidence or the region is exhausted.

## Runtime acceptance result

The accepted run emitted:

```text
SF-IMP-0054 BIOME POPULATION STACKED PASS: scannedChunks=25, eligibleChunks=25,
lower={volume=6000564149924409428/sf-imp-0054-biomes/0/0/6000500054823363328, biome=minecraft:forest, attempted=225, successful=55, attachments=9726, logs=933, leaves=7436, sharedColumns=6400},
upper={volume=6000564149924409428/sf-imp-0054-biomes/0/1/6000563925533947669, biome=minecraft:taiga, attempted=250, successful=59, attachments=10482, logs=1148, leaves=8352, sharedColumns=6400}
```

The prerequisite diagnostics reported `expectedBiome=true` for both `minecraft:trees_birch_and_oak` and `minecraft:trees_taiga` once the active exact-volume biome was exposed through `BiomeManager#getBiome(BlockPos)` as well as the existing quart/noise-biome seam.

Representative successful tree calls included attachment-write counts in the hundreds per chunk, confirming that the native tree configured features were materially realizing world state rather than only returning API-level success.

## Visual acceptance

Interactive inspection confirmed:

1. both vertically aligned tableland islands exist;
2. the lower island has obvious forest-style vegetation;
3. the upper island has obvious taiga-style vegetation;
4. trees are spatially distributed rather than one hard-coded proof tree;
5. base-world terrain and decoration remain normal beneath the islands;
6. no obvious vegetation or writes jump between the two Skyforge volumes;
7. a bee was observed on the forest island, consistent with native forest tree/bee configured-feature behavior and further evidence that the live biome generation semantics are being reused rather than replaced with a Skyforge-specific decoration implementation.

## Failed interactive attempts retained as evidence

### Attempt 1 — missing biome feature provenance

The first interactive run crashed inside vanilla `BiomeFilter` with:

```text
Tried to biome check an unregistered feature, or a feature that should not restrict the biome
```

Cause: biome-owned features were invoked through `PlacedFeature.place(...)`, which does not identify the top-level placed feature to `BiomeFilter`.

Correction: biome-owned population now uses Minecraft's `placeWithBiomeCheck(...)`; explicit standalone SF-IMP-0053 feature execution continues to use `place(...)`. The vanilla biome filter remains enabled.

### Attempt 2 — invalid single-chunk success oracle

The corrected biome-aware path passed `BiomeFilter`, but the origin chunk's upper taiga stream happened to produce zero successful placements. The development fixture then aborted with:

```text
upper proof biome produced no successful native vegetation placements
```

This was an invalid proof oracle: native placed-feature occurrence is stochastic/conditional per chunk. Zero occurrence in one chunk is legal.

Correction: acceptance moved to a deterministic multi-chunk regional aggregate.

### Attempt 3 — invalid bounding-box / chunk-center occupancy oracle

The first regional fixture assumed every chunk center inside the candidate patch had a surface in both tablelands. The run reached chunk `[2,2]` and aborted with:

```text
stacked biome volume has no proof-chunk surface
```

This was another invalid proof oracle. `WorldBounds` encloses a procedural volume but does not guarantee terrain at every enclosed X/Z, especially near morphology edges.

Correction: candidate chunks are evaluated against actual compiled exact-volume terrain rather than bounding-box or chunk-center assumptions.

### Attempt 4 — API success without visible biome realization

The topology-aware run emitted a nominal PASS with a few successful feature calls and tiny attachment counts, but visual inspection found both islands completely flat and undecorated.

This exposed a fourth invalid proof assumption. `PlacedFeature` returning `true` only means the configured feature reported success; it does not guarantee meaningful or persistent biome realization.

Correction:

- the development tablelands were enlarged;
- eligible chunks require at least 75% shared terrain coverage (192/256 columns);
- persisted `BlockTags.LOGS` and `BlockTags.LEAVES` are counted;
- PASS requires nonzero log and leaf counts on both the forest and taiga islands.

### Attempt 5 — biome holder carried but ordinary biome lookup not scoped

The strengthened tree diagnostic found clean dirt-backed exact-volume surfaces with viable oak, birch, and spruce saplings, but every tree prerequisite reported:

```text
expectedBiome=false
```

and the corresponding tree placed features returned `placed=false, attachments=0`.

Cause: the exact-volume biome was exposed through `WorldGenRegion#getUncachedNoiseBiome(...)`, but Minecraft's ordinary `level.getBiome(BlockPos)` path used by placement predicates routes through `BiomeManager#getBiome(BlockPos)`. The operation carried the correct biome, but native placement did not observe it.

Correction: add a narrowly scoped `BiomeManager#getBiome(BlockPos)` mixin that returns the active exact-volume biome only while an island population operation is active. BASE_WORLD and ordinary runtime biome behavior remain unchanged outside that scope.

The next interactive run flipped all representative tree prerequisite checks to `expectedBiome=true`, native forest and taiga tree placed features began succeeding with substantial attachment counts, and the final persistent log/leaf marker passed.

## Merge gate

All SF-IMP-0054 acceptance gates are satisfied subject to the final documentation-head CI recheck:

- implementation-head CI green (#298);
- strengthened persistent-vegetation runtime marker passed;
- visual forest-vs-taiga differentiation passed;
- base-world isolation remained visually intact;
- no visible cross-volume contamination;
- acceptance evidence recorded in this document and ADR-0057.
