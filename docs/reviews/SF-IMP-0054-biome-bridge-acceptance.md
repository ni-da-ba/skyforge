# SF-IMP-0054 — Exact-volume biome bridge acceptance

**Status:** Pending interactive acceptance

## Scope

SF-IMP-0054 promotes the exact-volume population seam from one explicitly selected native placed feature to native biome-driven population.

The implementation resolves an exact `SkyIslandWorldVolumeId` to a final-registry Minecraft biome, exposes that biome only inside the owning population operation, and executes that biome's ordered `VEGETAL_DECORATION` placed features through the SF-IMP-0053 domain-local runner.

## Automated evidence

The strengthened persistent-vegetation implementation compiled and passed full repository CI as run **#286** on implementation head `11bd27ef05aafaba63ff21028856de6d0f7f0378`, including NeoForge/FML bootstrap and the established fixed-seed/suspended-volume evidence suites.

The exact final PR head must remain green before merge after documentation-only acceptance updates.

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

## Runtime marker

Successful regional proof must emit:

```text
SF-IMP-0054 BIOME POPULATION STACKED PASS
```

with:

- `scannedChunks=...`;
- `eligibleChunks=...` with at least 25 eligible chunks;
- lower biome `minecraft:forest`;
- upper biome `minecraft:taiga`;
- nonzero aggregate successful-feature counts for both domains;
- aggregate attachment-write counts;
- `logs=>0` and `leaves=>0` for **both** exact volumes;
- aggregate shared-column counts.

The marker may appear before all 81 candidate chunks have generated if the minimum eligible sample and persistent vegetation conditions are already satisfied. A `PlacedFeature` boolean success count by itself is explicitly **not** sufficient for acceptance.

## Visual acceptance

Confirm:

1. both vertically aligned tableland islands exist;
2. lower and upper islands show visibly different native forest/taiga vegetation semantics;
3. vegetation is spatially distributed rather than one hard-coded proof tree;
4. visible trees/vegetation correspond to the nonzero persistent log/leaf evidence in the runtime marker;
5. base-world terrain and decoration remain normal beneath the islands;
6. no obvious vegetation or writes jump between the two Skyforge volumes;
7. save/reload remains stable if exercised.

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

The topology-aware run emitted:

```text
SF-IMP-0054 BIOME POPULATION STACKED PASS: scannedChunks=9, eligibleChunks=9,
lower={biome=minecraft:forest, attempted=81, successful=4, attachments=9},
upper={biome=minecraft:taiga, attempted=90, successful=5, attachments=17}
```

but visual inspection found both islands completely flat and undecorated: stone/dirt with no visible vegetation.

This exposed a fourth invalid proof assumption. `PlacedFeature` returning `true` only means the configured feature reported success; it does not guarantee meaningful or persistent biome realization. A handful of ground/plant mutations can satisfy that API result while failing the visual milestone.

Correction:

- the development tablelands are enlarged so native placement receives a representative terrain sample;
- eligible chunks now require at least 75% shared terrain coverage (192/256 columns), so random X/Z placement is not dominated by void near island edges;
- the proof scans persisted post-placement world state for `BlockTags.LOGS` and `BlockTags.LEAVES`;
- PASS requires nonzero log and leaf counts on both the forest and taiga islands.

## Merge gate

Do not merge PR #59 until:

- exact-head CI is green;
- the strengthened persistent-vegetation runtime marker passes;
- the visual criteria above pass;
- acceptance evidence is recorded here and on the PR.
