# ADR-0045 — Supplemental Multi-Block Vegetation Proof

**Status:** Proposed

## Context

SF-IMP-0038 proved that Minecraft placed features can consume supplemental lower surfaces in columns whose vanilla heightmap is owned by an elevated Skyforge island. SF-IMP-0039 separated reachability from physical suitability, and SF-IMP-0040 refined aquatic exposure.

The remaining practical vegetation question is stronger than grass placement: can an ordinary multi-block Minecraft tree configured feature originate from a supplemental lower dry surface and complete its own trunk/foliage/ground validation without Skyforge implementing a second tree system?

This directly addresses the earlier in-game observation that ordinary ground beneath a floating island can lose vanilla surface vegetation because a single-valued heightmap points feature placement at the upper island.

## Decision

SF-IMP-0041 adds a development-only placed feature named:

```text
skyforge:additional_surface_trees_plains
```

It uses Minecraft's existing `minecraft:trees_plains` configured feature and mirrors the relevant vanilla plains-tree placement structure while replacing the single-valued surface selection with:

```text
skyforge:suitable_surfaces { suitability: dry_open }
```

The chain retains a Minecraft `would_survive` oak-sapling predicate and biome filter. The development count is intentionally boosted to make the proof observable in a small specimen; it is not a production density recommendation.

## Architectural boundary

Skyforge selects a physically suitable supplemental origin. Minecraft's configured tree feature remains responsible for tree shape, trunk and foliage realization, block replacement and feature-specific viability.

This milestone does not introduce a backend-neutral tree taxonomy, species model, biome field or vegetation-density field. Those remain legitimate future Skyforge systems when world composition/environmental intent requires them.

The current proof also does not claim that adding `minecraft:trees_plains` to every Overworld biome is final species policy. That broad biome modifier exists only in development resources so a native tree feature can be exercised without creating a second feature engine.

## Invariants

1. Vanilla retains the highest surface in each `(x,z)` column.
2. The accepted `dry_open` suitability contract is reused unchanged.
3. The supplemental tree proof does not rerun the entire biome decoration step.
4. Underground ores, structures and unrelated feature families are not duplicated.
5. Tree realization remains Minecraft-owned.
6. All SF-IMP-0041 tree resources are development-only and absent from the production JAR.
7. No Minecraft tree/biome concept enters backend-neutral modules.
8. Future Skyforge-owned climate/biome/ecology fields remain compatible with this mechanism.

## Explicit non-goals

SF-IMP-0041 does not yet provide:

- production biome-aware species selection;
- final vegetation density;
- tree-scale horizontal footprint classification in Skyforge;
- a generic replay of every biome vegetation feature;
- modded tree-family adaptation;
- cave-versus-open-land horizontal enclosure;
- final morphology/playability tuning;
- Skyforge-owned climate or biome fields.

## Acceptance criteria

ADR-0045 becomes Accepted only after:

1. the focused verifier and repository-wide `check` pass;
2. development resources load in a real Minecraft client;
3. on a specimen with qualifying dry ground beneath the Massif, at least one real Minecraft tree is visibly realized from a supplemental lower surface;
4. the upper Massif surface is not systematically double-populated by the supplemental tree proof;
5. obvious wholesale biome-decoration duplication does not occur;
6. save/reload is clean; and
7. the SF-IMP-0041 development tree resources remain absent from the production JAR.

If a random specimen contains no tree-compatible lower substrate (for example ocean or desert sand), the absence of trees is not by itself a failure; another disposable seed may be required for the positive client proof.
