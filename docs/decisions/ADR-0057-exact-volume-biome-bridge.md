# ADR-0057: Exact-volume biome bridge

**Status:** Accepted

## Context

ADR-0056 established that BASE_WORLD generation completes with Skyforge observationally absent and that exact Skyforge volumes are later realized additively. SF-IMP-0053 then proved that one exact `SkyIslandWorldVolumeId` can run a registered native `PlacedFeature` with terrain-local height/block access, deterministic volume-salted population randomness, bounded attachment writes, and a hard veto on foreign Skyforge solids.

The next compatibility boundary is biome semantics. Minecraft and NeoForge biomes are registry objects whose final generation settings can include vanilla, datapack, and mod-injected placed features. Skyforge must be able to assign those semantics to one suspended island without changing the base world's biome column or reintroducing one highest-surface interpretation for a shared X/Z coordinate.

## Decision

Skyforge owns the semantic decision of **which biome applies to an exact island volume at a position**. The Minecraft adapter resolves that decision to an ordinary final-registry `Holder<Biome>` / biome registry key. Minecraft and NeoForge remain authoritative for what that biome means.

The bridge is:

```text
Skyforge exact-volume environmental intent
    -> Minecraft adapter biome resolver
    -> final registered Holder<Biome>
    -> final BiomeGenerationSettings
    -> ordered native PlacedFeature definitions
    -> SF-IMP-0053 exact-volume execution scope
```

During one explicit island population operation, ordinary world-generation biome reads resolve to that operation's exact-volume biome. Outside that operation, the hook is inert. BASE_WORLD generation and its stored biome data are not rewritten.

Biome visibility requires both world-generation biome seams used by Minecraft 1.21.1: quart/noise-biome access through `WorldGenRegion#getUncachedNoiseBiome(...)` and ordinary block-position biome access through `BiomeManager#getBiome(BlockPos)`. The latter is the path used by native biome-aware placement and was proven necessary by the SF-IMP-0054 tree diagnostics. Both hooks are scoped to the active exact-volume population operation and are inert for BASE_WORLD generation.

Biome-owned native features must execute through Minecraft's biome-aware placed-feature path so vanilla `BiomeFilter` retains both pieces of provenance it expects: the top-level placed feature and the biome visible at the attempted position. Skyforge must not remove, bypass, or special-case that filter.

Native feature occurrence is evaluated per chunk. A particular chunk is allowed to yield zero successful placements because count, rarity, noise, block predicates, and other placement modifiers are stochastic or conditional. Proof and later population planning therefore evaluate deterministic finite regions rather than treating one lucky chunk as the semantic unit of biome correctness.

A deterministic regional proof must also be **terrain-topology aware**. A Skyforge volume's nominal `WorldBounds` is only a broad spatial envelope; it does not guarantee that every chunk center or every X/Z inside the bounds contains owned terrain. Regional population therefore operates on positions/chunks where the compiled exact-volume geometry actually yields a surface. For stacked-domain proofs, eligibility is based on the intersection of X/Z positions owned by both exact volumes, not on bounding-box overlap alone.

For stochastic native occurrence, a single surviving shared terrain column is also not a representative chunk sample. Placement modifiers may choose any X/Z inside the chunk. Validation regions therefore require substantial shared terrain coverage before treating a chunk as eligible; otherwise feature failure may merely reflect random placement into void near a morphology edge.

Finally, a `PlacedFeature` boolean return is **not sufficient evidence of biome realization**. A configured feature may report success after only a tiny ground or plant mutation that is not representative of the biome semantics being validated. Acceptance must inspect persistent post-placement world state. The SF-IMP-0054 forest/taiga proof therefore requires persistent log and leaf blocks on both exact volumes in addition to native feature success. This is a validation criterion, not a production feature-ID compatibility table.

## Runtime biome identity versus generation settings

The same exact-volume resolver has two distinct consumers:

1. **Generation semantics**: while populating an island, native placement modifiers and biome generation settings see the island's biome.
2. **Runtime semantics**: later work may project the same resolver into runtime biome queries/storage for weather, colors, ambient behavior, temperature, and spawning.

SF-IMP-0054 proves the first use. It does not require mutating BASE_WORLD biome storage to do so.

## Registry compatibility

The resolver returns registry identity rather than copying biome objects. Population resolves that identity from the live final registry before reading `BiomeGenerationSettings`. This preserves post-bootstrap NeoForge biome modifications and allows later modded/datapack biome content to traverse the same seam without a Skyforge compatibility table of feature IDs.

## Ordering and randomness

Feature lists retain Minecraft's generation-step ordering. Exact-volume population randomness remains salted by stable volume identity, chunk, native feature key, generation step, and occurrence identity so stacked volumes at the same X/Z remain deterministic but independent.

Skyforge does not promise byte-identical decoration to a vanilla ground biome, because the terrain domain and RNG ownership are deliberately different. It promises reuse of the final registered biome semantics and native placement algorithms inside the correct exact terrain owner.

## Consequences

- Two islands stacked at the same X/Z can expose different Minecraft biomes during their independent population streams.
- `BiomeFilter`, heightmap placement, block predicates, ordinary biome-manager reads, and modded placement modifiers execute against the owning island domain rather than the base-world column.
- BASE_WORLD remains observationally isolated during its own generation.
- Modded biome generation settings can be reused generically if they are represented through normal final registries.
- A single-chunk zero-occurrence result is not an architectural failure; finite deterministic regional evidence is required for stochastic population proofs.
- Nominal volume bounds must not be treated as proof of actual terrain occupancy; population eligibility follows compiled exact-volume geometry.
- Regional validation must account for terrain coverage inside each chunk, because native placement modifiers choose positions independently of Skyforge's proof sample.
- Native feature API success alone cannot certify meaningful biome realization; acceptance must validate persistent resulting world state.
- Runtime biome projection/storage remains a separate implementation problem and may face Minecraft's finite quart-biome storage constraints.

## Acceptance evidence

SF-IMP-0054 was accepted on PR head `83d65aa01f60084c29cb294534ab6d3649089000`.

Automated CI run **#298** passed on that exact implementation head, including NeoForge/FML mixin bootstrap and the established fixed-seed and suspended-volume evidence suites.

Interactive validation on a new disposable Skyforge Development world produced:

```text
SF-IMP-0054 BIOME POPULATION STACKED PASS:
scannedChunks=25,
eligibleChunks=25,
lower={biome=minecraft:forest, attempted=225, successful=55, attachments=9726, logs=933, leaves=7436, sharedColumns=6400},
upper={biome=minecraft:taiga, attempted=250, successful=59, attachments=10482, logs=1148, leaves=8352, sharedColumns=6400}
```

Every diagnostic sample reported `expectedBiome=true` once the `BiomeManager` read seam was scoped correctly. The forest and taiga tree features then produced persistent trees with substantial attachment-write counts. Visual inspection confirmed obvious forest-vs-taiga differentiation on the two vertically aligned islands, normal base-world terrain beneath them, and no visible cross-volume contamination. A bee was also observed on the forest island, providing additional evidence that native biome-configured feature behavior—not merely Skyforge-authored tree placement—was executing inside the exact-volume domain.

The accepted invariant is therefore:

> Skyforge chooses the exact-volume biome identity; the Minecraft adapter resolves that identity through the live final registry, and native biome generation executes against the owning island's scoped terrain and biome view without rewriting or borrowing BASE_WORLD semantics.
