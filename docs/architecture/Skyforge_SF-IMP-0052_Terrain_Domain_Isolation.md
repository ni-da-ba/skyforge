# Skyforge SF-IMP-0052 — Terrain Domain Isolation

## Governing invariant

Vanilla base terrain and every independently compiled Skyforge island are separate generation domains. Horizontal overlap does not make them one logical terrain column.

```text
BASE_WORLD
    -> native terrain/surface
    -> native structures
    -> native/modded biome decoration
    -> completed base-world state

SKYFORGE_VOLUME(id)
    -> additive deterministic island realization
    -> later domain-local population
```

## What changed

The Minecraft generator no longer exposes Skyforge globally through ordinary `getBaseHeight(...)`. With no explicit island-generation scope, the method delegates directly to `NoiseBasedChunkGenerator`.

Likewise, native `tryGenerateStructure(...)` candidates in the base-world stream bypass Skyforge structure support/admission logic completely. The accepted support, accommodation and contradiction machinery is retained for later reuse only inside an explicit exact-island generation scope.

Skyforge block realization moved from immediately after `buildSurface(...)` to the tail of `applyBiomeDecoration(...)`. Vanilla and modded biome decoration therefore execute against a live chunk in which no Skyforge island exists yet.

## Surface representation without placement coupling

Skyforge still needs Minecraft's native terrain material when adapting the visible top of an island. Reading the live chunk after decoration would mistake trees or structures for terrain, so SF-IMP-0052 captures an immutable 16x16 native surface-material snapshot immediately after surface construction.

That snapshot has one purpose only: later island block representation. It is never consulted by native structure or feature placement.

## Exact island generation context

`SkyforgeGenerationDomainStage` makes island ownership explicit and thread-confined. Absence of a scope means `BASE_WORLD`.

Within an explicit island scope:

- early height queries inspect only the exact `SkyIslandWorldVolumeId`;
- an empty column remains empty;
- vanilla terrain and other stacked islands do not provide fallback surfaces;
- structure admission can reuse the existing generic physical evaluators against that same exact volume.

No production code currently opens this island scope during ordinary base-world generation. SF-IMP-0053 will consume it from an independently seeded island population pass.

## Superseded stepping stones

SF-IMP-0038 through SF-IMP-0051 proved valuable individual Minecraft seams, but two assumptions are now superseded as production architecture:

1. globally returning `max(vanilla, Skyforge)` from early height queries;
2. running vanilla decoration on a chunk that already contains Skyforge and then trying to repair individual consumers.

Their experimental evidence remains valid; their global-column composition model does not.

## Compatibility consequence

Unknown modded base-world generation now follows the safest possible default: Skyforge does not participate. A modded definition becomes island-owned only when a future generic island population pass deliberately invokes that native registry definition inside one exact Skyforge volume.

This is the compatibility model required for vertically dense worlds as well as mixed vanilla/Skyforge development worlds.
