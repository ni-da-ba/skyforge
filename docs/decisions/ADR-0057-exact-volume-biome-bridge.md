# ADR-0057: Exact-volume biome bridge

**Status:** Proposed

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

Biome-owned native features must execute through Minecraft's biome-aware placed-feature path so vanilla `BiomeFilter` retains both pieces of provenance it expects: the top-level placed feature and the biome visible at the attempted position. Skyforge must not remove, bypass, or special-case that filter.

Native feature occurrence is evaluated per chunk. A particular chunk is allowed to yield zero successful placements because count, rarity, noise, block predicates, and other placement modifiers are stochastic or conditional. Proof and later population planning therefore evaluate deterministic finite regions rather than treating one lucky chunk as the semantic unit of biome correctness.

A deterministic regional proof must also be **terrain-topology aware**. A Skyforge volume's nominal `WorldBounds` is only a broad spatial envelope; it does not guarantee that every chunk center or every X/Z inside the bounds contains owned terrain. Regional population therefore operates on positions/chunks where the compiled exact-volume geometry actually yields a surface. For stacked-domain proofs, eligibility is based on the intersection of X/Z positions owned by both exact volumes, not on bounding-box overlap alone. A candidate chunk may be scanned yet remain ineligible when no shared terrain sample exists there.

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
- `BiomeFilter`, heightmap placement, block predicates, and modded placement modifiers execute against the owning island domain rather than the base-world column.
- BASE_WORLD remains observationally isolated during its own generation.
- Modded biome generation settings can be reused generically if they are represented through normal final registries.
- A single-chunk zero-occurrence result is not an architectural failure; finite deterministic regional evidence is required for stochastic population proofs.
- Nominal volume bounds must not be treated as proof of actual terrain occupancy; population eligibility follows compiled exact-volume geometry.
- Runtime biome projection/storage remains a separate implementation problem and may face Minecraft's finite quart-biome storage constraints.

## Acceptance boundary

This ADR may become **Accepted** when SF-IMP-0054 demonstrates, on one exact PR head:

1. full repository CI including NeoForge/Mixin bootstrap passes;
2. two vertically aligned exact volumes resolve different registered biomes at shared X/Z terrain positions;
3. a deterministic multi-chunk candidate region discovers a sufficient set of chunks containing actual shared stacked terrain and consumes each biome's native `VEGETAL_DECORATION` settings there with Minecraft's biome checks intact;
4. both domains produce successful native vegetation somewhere in the eligible region without hard-coded feature origins;
5. no cross-volume write contamination occurs;
6. BASE_WORLD decoration remains visually normal beneath the stacked islands.
