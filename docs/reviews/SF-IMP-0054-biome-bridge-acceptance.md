# SF-IMP-0054 — Exact-volume biome bridge acceptance

**Status:** Pending interactive acceptance

## Scope

SF-IMP-0054 promotes the exact-volume population seam from one explicitly selected native placed feature to native biome-driven population.

The implementation resolves an exact `SkyIslandWorldVolumeId` to a final-registry Minecraft biome, exposes that biome only inside the owning population operation, and executes that biome's ordered `VEGETAL_DECORATION` placed features through the SF-IMP-0053 domain-local runner.

## Automated evidence

Required before merge:

- full repository CI green on the exact final PR head;
- NeoForge/FML unit-test bootstrap green with the `WorldGenRegion` mixin active;
- existing fixed-seed and suspended-volume evidence suites remain green;
- SF-IMP-0053 exact-volume population regression tests remain green.

## Interactive fixture

Run:

```text
:skyforge-neoforge-1211:runBiomePopulationClient
```

in a **new disposable Skyforge Development world**.

The fixture stacks two tableland volumes in the same X/Z region:

- lower volume: `minecraft:forest`;
- upper volume: `minecraft:taiga`.

A deterministic 5×5 candidate chunk patch centered on the origin is scanned. A chunk is eligible only when Skyforge can find at least one shared X/Z position inside that chunk where **both** compiled exact volumes have an owned surface. This deliberately uses actual compiled terrain topology rather than nominal `WorldBounds` or chunk-center assumptions.

Each eligible chunk consumes the corresponding final biome's native `VEGETAL_DECORATION` list. Native placement modifiers choose count, rarity, X/Z positions, heightmap positions, biome eligibility, and configured-feature placement.

A single eligible chunk may legitimately yield zero successful placements. The fixture passes after at least nine eligible shared-terrain chunks have been observed and both biome domains have produced successful native vegetation. It fails only after exhausting all 25 candidate chunks if there is insufficient shared terrain or either biome still has zero successful placements.

## Runtime marker

Successful regional proof must emit:

```text
SF-IMP-0054 BIOME POPULATION STACKED PASS
```

with:

- `scannedChunks=...`;
- `eligibleChunks=...` with at least 9 eligible chunks;
- lower biome `minecraft:forest`;
- upper biome `minecraft:taiga`;
- nonzero aggregate successful-feature counts for both domains;
- aggregate attachment-write counts.

## Visual acceptance

Confirm:

1. both vertically aligned tableland islands exist;
2. lower and upper islands show visibly different native forest/taiga vegetation semantics;
3. vegetation is spatially distributed rather than one hard-coded proof tree;
4. base-world terrain and decoration remain normal beneath the islands;
5. no obvious vegetation or writes jump between the two Skyforge volumes;
6. save/reload remains stable if exercised.

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

The first regional fixture assumed every chunk center inside the 5×5 candidate patch had a surface in both tablelands. The run reached chunk `[2,2]` and aborted with:

```text
stacked biome volume has no proof-chunk surface
```

This was another invalid proof oracle. `WorldBounds` encloses a procedural volume but does not guarantee terrain at every enclosed X/Z, especially near morphology edges.

Correction: each candidate chunk now searches its actual compiled field for a shared X/Z position where both exact volumes have surfaces. Only those shared-terrain chunks are eligible. The proof requires a minimum deterministic eligible sample count and successful native vegetation for both owners.

## Merge gate

Do not merge PR #59 until:

- exact-head CI is green;
- the corrected shared-terrain regional runtime marker passes;
- the visual criteria above pass;
- acceptance evidence is recorded here and on the PR.
