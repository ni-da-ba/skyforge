# ADR-0042 — Supplemental Multi-Surface Feature Placement

**Status:** Proposed

## Context

SF-IMP-0037 established two facts that must coexist:

1. Minecraft's downstream world-generation systems correctly observe elevated Skyforge terrain; and
2. vanilla top-surface heightmaps are single-valued per `(x,z)`.

When native ground and one or more floating Skyforge surfaces occupy the same vertical column, final heightmap priming can expose only the highest qualifying surface. Heightmap-driven vegetal features can therefore target the upper island while lower native ground remains physically present but is no longer the column's heightmap answer.

Skyforge must not solve this by moving Minecraft biome definitions, feature registries or climate semantics into backend-neutral modules. It should also not blindly rerun every biome feature: doing so would duplicate ores and underground decoration, disturb feature ordering, and create compatibility hazards.

Minecraft's `PlacedFeature` pipeline is already compositional. Distribution and placement are expressed as ordered `PlacementModifier`s such as count/noise, horizontal scatter, heightmap selection and biome filtering. NeoForge biome modifiers can add copied placed features without replacing the owning biome definition.

## Decision

SF-IMP-0038 introduces a Minecraft-owned **supplemental additional-surface placement modifier**.

The modifier is intended to replace only the single-valued heightmap stage in a copied surface feature while preserving that feature's other placement behavior.

Conceptually:

```text
vanilla placed feature
    count / noise
    -> in-square scatter
    -> vanilla heightmap            -> highest surface only
    -> biome filter

Skyforge supplemental copy
    same count / noise
    -> same in-square scatter
    -> skyforge:additional_surfaces -> every accepted lower surface only
    -> biome filter
```

The vanilla original remains present. The supplemental copy deliberately excludes the highest surface so it does not double-place the feature where vanilla already has a valid heightmap target.

## Feature-stage scope

`SkyforgeNoiseBasedChunkGenerator.applyBiomeDecoration(...)` establishes a short-lived feature-placement scope immediately before delegating to vanilla biome decoration and clears that scope in `finally` after vanilla returns.

The scope is thread-local rather than a process-global per-chunk cache. This matches the synchronous placement-modifier call chain inside one biome-decoration invocation and avoids retaining world-generation state across chunks.

The scope is prepared **after carvers and before features** from:

- the real current `ChunkAccess` / `WorldGenLevel`; and
- the accepted deterministic Skyforge chunk materialization from the active runtime binding.

This lets the adapter distinguish Skyforge-authored solid intervals from preserved native terrain while still rejecting surfaces that carvers have actually removed.

## Additional-surface definition

For one `(x,z)` column, a placement candidate is the air block immediately above an exposed solid surface.

The first implementation can expose:

1. surviving exposed Skyforge tops below the highest world surface; and
2. the highest preserved native ground surface outside Skyforge-authored solid intervals, when it is also below the highest world surface.

The highest current world-surface placement position is excluded because the original vanilla placed feature already owns that target.

Candidates must still exist in the live world state at feature time: the supporting block must be non-air and the placement block immediately above it must be air. Fluids are not treated as ordinary land-surface vegetation targets in this first proof.

## Why a placement modifier rather than a second feature engine

This preserves Minecraft's existing worldgen abstractions:

- `PlacedFeature` remains the unit of backend-native feature definition;
- count/noise/rarity/scatter logic remains authored by the feature data;
- `BiomeFilter` remains authoritative for biome membership;
- Skyforge supplies only the missing multi-surface spatial answer.

The modifier does not know what a plains biome, tree, flower, ore or modded vegetation family means.

## Development proof

The first client proof uses development-only data that copies a simple vanilla surface-vegetation placement chain and substitutes `skyforge:additional_surfaces` for its heightmap modifier.

The proof exists to demonstrate that lower native ground and/or lower stacked Skyforge surfaces can receive a normal Minecraft configured feature while the uppermost surface remains handled by vanilla.

The diagnostic copied feature is not itself a final cross-biome production policy and remains excluded from the packaged production resources.

## Invariants

1. Backend-neutral modules gain no Minecraft biome, feature, block or placement APIs.
2. Vanilla placed features remain unmodified.
3. The supplemental path does not replay underground generation steps.
4. The highest surface is excluded from supplemental placement to avoid systematic duplication.
5. Candidate surfaces are derived from actual feature-time chunk state plus accepted Skyforge occupancy.
6. Carved-away Skyforge surfaces are not returned as placement targets.
7. Native ground remains physically untouched except by the normal configured feature being placed there.
8. Feature-stage context is scoped to one decoration call and cleared even when generation throws.
9. The mechanism supports more than one lower Skyforge surface in a vertical column.
10. Development-only copied feature data does not leak into the production JAR.

## Explicit non-goals

SF-IMP-0038 does **not** claim to:

- automatically make every vanilla or modded feature multi-surface;
- infer which arbitrary feature families are safe to replay;
- duplicate the entire `VEGETAL_DECORATION` step;
- solve structure-start timing;
- provide final tree/flower density tuning;
- create a parallel Skyforge biome or climate model;
- guarantee that every modded `PlacementModifier` composes correctly with the supplemental modifier.

## Acceptance criteria

ADR-0042 becomes Accepted only after:

1. a registered `skyforge:additional_surfaces` placement-modifier codec loads under NeoForge 1.21.1;
2. feature-stage scope is established only during biome decoration and is cleared afterward;
3. automated tests prove uppermost-surface exclusion;
4. automated tests prove preserved lower native ground can be returned as an additional target;
5. automated tests prove multiple vertically separated Skyforge surfaces can be represented;
6. carved/removed or fluid-invalid candidates are rejected from land-surface placement;
7. the original SF-IMP-0036/0037 post-surface and material-adaptation invariants remain green;
8. backend-neutral independence and repository-wide `check` pass;
9. a development-only copied surface feature demonstrates placement on an additional surface in a real client without systematic duplication on the highest surface; and
10. the development feature resources remain absent from the production JAR.
