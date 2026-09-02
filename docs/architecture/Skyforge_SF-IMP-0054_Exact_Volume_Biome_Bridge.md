# SF-IMP-0054 — Exact-Volume Biome Bridge

## Status

Implementation / acceptance candidate.

## Context

ADR-0056 and SF-IMP-0052 established that BASE_WORLD and each `SKYFORGE_VOLUME(id)` are independent generation domains. SF-IMP-0053 proved that one registered native Minecraft `PlacedFeature` can execute independently on vertically stacked Skyforge volumes with exact terrain reads, deterministic volume-salted randomness, bounded writes, and no base-world generation shadow.

The next problem is ecological meaning. A fixed proof feature is not sufficient for production world generation. Skyforge must be able to say that an island region is semantically forest-like, taiga-like, desert-like, wetland-like, or otherwise climate/biome appropriate, while Minecraft and installed mods remain authoritative over the concrete content attached to the resolved biome.

## Decision

Skyforge owns **where and why** a biome applies. The Minecraft backend owns **what the resolved biome means**.

The adapter therefore resolves one exact Skyforge volume/environment sample to a normal Minecraft `ResourceKey<Biome>`, then looks that key up in the live final biome registry. Population consumes the resulting `Holder<Biome>` and its final `BiomeGenerationSettings`.

```text
Skyforge volume + environment sample
                |
                v
SkyforgeExactVolumeBiomeResolver
                |
                v
Minecraft ResourceKey<Biome>
                |
                v
live final biome registry
                |
                v
Holder<Biome>
                |
                +--> runtime biome semantics
                |
                +--> final BiomeGenerationSettings
                           |
                           v
                  ordered PlacedFeature lists
                           |
                           v
                exact-volume population runner
```

## Exact-volume biome reads

During one explicit island population operation, `WorldGenRegion` biome reads are scoped to the operation's resolved `Holder<Biome>`. The first implementation intercepts `getUncachedNoiseBiome(quartX, quartY, quartZ)`, which is the low-level biome source used by ordinary worldgen biome access.

Outside an explicit Skyforge population execution, the hook is inert. BASE_WORLD generation therefore continues to observe its normal biome source and chunk biome storage.

The bridge accepts quart coordinates even though the first proof assigns one biome to each whole island. This preserves the API shape required for later within-island biome variation driven by Skyforge climate/environment fields.

## Final-registry rule

The resolver returns a biome registry **key**, not a copied or reconstructed biome. The backend resolves that key through the live `RegistryAccess` at generation time.

This is required for compatibility. NeoForge biome modifiers and datapack content may alter a biome's generation settings after initial registration. Skyforge must consume that final state rather than a stale built-in template.

Likewise, placed-feature identity is recovered from the live placed-feature registry. The population layer does not maintain a hard-coded table of vanilla or mod feature names.

## Native occurrence

For a selected generation step, `SkyforgeNativeBiomePopulationRunner` iterates the resolved biome's ordered `PlacedFeature` holders and invokes each through the SF-IMP-0053 exact-volume execution seam.

The initial proof uses `GenerationStep.Decoration.VEGETAL_DECORATION` only. The chunk origin supplied to each `PlacedFeature` matches the normal native placement contract; the placed feature's own modifiers determine count, horizontal spread, heightmap placement, rarity, biome filtering, and concrete configured feature behavior.

Skyforge therefore does not choose one tree coordinate. It chooses the generation domain and biome meaning; native placement definitions choose occurrences inside that domain.

## Randomness

Each native feature attempt retains the SF-IMP-0053 population identity:

```text
hash(
  volumeId,
  originChunk,
  generationStep,
  nativeRegistryKey,
  occurrenceIndex
)
```

Stacked islands sharing X/Z therefore do not share one population lottery. Changing one island's identity does not mutate another island's stream.

## Write ownership

Biome-driven features use the same bounded topological attachment envelope accepted in SF-IMP-0053.

- exact owner solid may be written;
- connected attachment writes such as trunks, leaves, flowers, or similar feature output may extend beyond strict density up to the configured bound;
- foreign Skyforge solid is always a hard veto;
- disconnected writes are rejected;
- BASE_WORLD does not open an island population scope.

## Initial proof fixture

The development fixture uses two vertically aligned tableland volumes in chunk `(0,0)`:

- lower volume: `minecraft:forest`;
- upper volume: `minecraft:taiga`.

Both consume only their final-registry `VEGETAL_DECORATION` lists. No individual vegetation feature or proof position is selected by Skyforge.

The runtime must emit:

```text
SF-IMP-0054 BIOME POPULATION STACKED PASS
```

only after both volumes resolve different Y surfaces, different biome identities, non-empty vegetation lists, successful native placements, and non-identical final feature lists.

## Acceptance criteria

1. Full repository CI passes on the exact implementation/documentation head, including NeoForge/FML mixin bootstrap.
2. A new disposable `runBiomePopulationClient` world produces both vertically aligned island volumes.
3. Lower and upper islands visibly exhibit different forest/taiga native vegetation behavior at naturally selected positions rather than one fixed proof coordinate.
4. The runtime emits the self-checking PASS marker with separate biome identities and Y surfaces.
5. BASE_WORLD beneath the islands remains normally decorated, with no population shadow attributable to the suspended volumes.
6. No cross-volume vegetation writes are observed.

## Non-goals for SF-IMP-0054

This milestone does not yet:

- derive biome identity from a mature backend-neutral climate field;
- support multiple biome cells inside one island in the interactive proof;
- execute every generation step, caves, carvers, ores, structures, or hydrology;
- persist independent runtime biome cells into Minecraft chunk biome storage;
- define compatibility policy for global terrain-overhaul ownership.

Those systems build on this resolver and exact-volume population boundary rather than replacing it.

## Compatibility consequence

A modded biome can participate without Skyforge-specific feature code when three conditions hold:

1. the biome exists in the final Minecraft biome registry;
2. the Skyforge-to-Minecraft resolver may select its registry key;
3. its attached generation content operates through the native worldgen interfaces constrained by the exact-volume domain view.

This is the intended meaning of **reuse Minecraft definitions, not Minecraft ownership assumptions**.
