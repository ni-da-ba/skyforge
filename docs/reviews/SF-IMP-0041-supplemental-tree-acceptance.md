# SF-IMP-0041 — Supplemental Tree Acceptance

**Status:** Accepted
**Date:** 2026-08-31

## Accepted scope

SF-IMP-0041 proves that the accepted supplemental lower-surface path can originate a real multi-block Minecraft vegetation feature.

The development proof uses `minecraft:trees_plains` with `skyforge:suitable_surfaces { suitability: dry_open }`, retaining Minecraft's own sapling-survival predicate and configured tree realization.

## Client evidence

In the SF-IMP-0041 development client, normal Minecraft trees generated naturally on compatible lower terrain beneath the elevated Massif. This is a stronger proof than the earlier diagnostic blocks: trunk, foliage, local replacement and feature-specific viability all completed through Minecraft's normal configured-feature implementation.

No wholesale replay of biome decoration was introduced, and vanilla remains responsible for the highest surface in each `(x,z)` column.

## Diagnostic cleanup

The emerald, lapis and diamond marker fixtures used for SF-IMP-0039/0040 validation were removed from the active development resources after the real tree proof. The underlying suitability classes remain available; only the obsolete visualization fixtures were deleted.

## Accepted boundary

Skyforge supplies supplemental physical placement origins. Minecraft owns concrete tree realization. Species choice, vegetation density, biome/ecology intent and future Skyforge-owned environmental fields remain separate later concerns.

## Explicit limitations

- the development biome modifier is not production species policy;
- no generic native-biome vegetation replay exists yet;
- no modded tree-family compatibility is claimed;
- no final vegetation-density policy is claimed;
- no Skyforge biome/climate ownership is introduced by this milestone.
