# ADR-0045 — Supplemental Multi-Block Vegetation Proof

**Status:** Accepted

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

## Accepted evidence

The focused SF-IMP-0041 verifier and repository-wide `check` were used as the automated gate for this implementation slice. In the real development client, normal Minecraft trees generated naturally on compatible lower supplemental dry terrain beneath the elevated Massif. This demonstrated successful multi-block configured-feature realization through the accepted supplemental-surface path rather than a marker-only write.

The obsolete emerald/lapis/diamond marker fixtures used by earlier suitability milestones were removed after the tree proof. New SF-IMP-0041 development worlds therefore retain the real grass/tree probes without the colored marker clutter.

## Architectural boundary

Skyforge selects a physically suitable supplemental origin. Minecraft's configured tree feature remains responsible for tree shape, trunk and foliage realization, block replacement and feature-specific viability.

This milestone does not introduce a backend-neutral tree taxonomy, species model, biome field or vegetation-density field. Those remain legitimate future Skyforge systems when world composition/environmental intent requires them.

The current proof also does not claim that adding `minecraft:trees_plains` to every Overworld biome is final species policy. That broad biome modifier exists only in development resources so a native tree feature can be exercised without creating a second feature engine.

## Accepted invariants

1. Vanilla retains the highest surface in each `(x,z)` column.
2. The accepted `dry_open` suitability contract is reused unchanged.
3. The supplemental tree proof does not rerun the entire biome decoration step.
4. Underground ores, structures and unrelated feature families are not duplicated.
5. Tree realization remains Minecraft-owned.
6. All SF-IMP-0041 tree resources are development-only and absent from the production JAR.
7. No Minecraft tree/biome concept enters backend-neutral modules.
8. Future Skyforge-owned climate/biome/ecology fields remain compatible with this mechanism.
9. Colored marker fixtures from SF-IMP-0039/0040 are no longer part of the active development proof.

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
