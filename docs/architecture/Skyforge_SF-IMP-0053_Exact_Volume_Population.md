# Skyforge SF-IMP-0053 — Exact-Volume Native Population

## Purpose

SF-IMP-0053 is the first implementation milestone after ADR-0056 terrain-domain isolation. It introduces island-owned native population without making the base world or vertically stacked islands share one occurrence stream.

## Governing model

```text
BASE_WORLD
    -> native Minecraft/modded population unchanged

SKYFORGE_VOLUME(A)
    -> independent population operation identities
    -> native registry definitions
    -> exact-domain terrain reads/writes

SKYFORGE_VOLUME(B)
    -> independent population operation identities
    -> native registry definitions
    -> exact-domain terrain reads/writes
```

X/Z overlap does not couple occurrence, placement or terrain authority.

## Population operation identity

Every island-owned native population attempt must derive its random stream from stable semantic identity rather than iteration order or a shared chunk lottery.

The Minecraft backend currently derives a seed from:

- stable `SkyIslandWorldVolumeId` hierarchy and geometry seed;
- initiating `ChunkPos`;
- native registry definition key;
- native generation-step ordinal;
- local occurrence index.

This guarantees that two stacked islands at the same X/Z do not receive the same random stream merely because Minecraft sees one chunk column. Adding an unrelated island must not reshuffle an existing island's population stream.

## Next execution seam

Seed identity alone is not sufficient. Before invoking arbitrary `PlacedFeature` definitions, the backend must provide a domain-scoped world view with explicit read/write ownership.

The intended rule is:

```text
read/write owned terrain for A      -> allowed
explicit attachment halo for A      -> allowed by policy
BASE_WORLD or volume B mutation     -> rejected
ambiguous ownership                 -> fail open / skip operation
```

The attachment halo is necessary for legitimate feature geometry such as leaves, branches and other generated content that extends beyond the island's strict solid-density boundary. Its semantics must be generic and geometric rather than feature-specific.

## Initial native feature target

The first runtime proof should use one ordinary registered surface vegetation `PlacedFeature` because it exercises:

- native registry lookup;
- placement modifiers;
- exact island height queries;
- deterministic random occurrence;
- multi-block feature writes;
- stacked-volume independence.

The proof must not special-case a named tree, biome mod or structure family in production policy.

## Compatibility direction

Later compatibility tests will consume final Minecraft/NeoForge registry state, including definitions inserted or modified by biome/worldgen mods. Biomes O' Plenty, Terralith and Lithostitched are test references, not dependencies and not code branches.

Fundamental terrain-topology replacements remain outside automatic compatibility: Skyforge owns island topology while the Minecraft backend reuses compatible content definitions.

## Deferred systems

Structures, caves/carvers, hydrology and full biome-domain composition remain separate milestones. They must use the same exact-domain ownership and deterministic operation identity rather than introducing parallel ownership models.
