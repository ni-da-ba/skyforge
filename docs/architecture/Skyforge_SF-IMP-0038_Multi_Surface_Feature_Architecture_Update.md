# Skyforge — SF-IMP-0038 Multi-Surface Feature Architecture Update

**Snapshot:** 2026-08-31  
**Accepted through:** SF-IMP-0038

This delta advances the accepted Minecraft integration architecture through the first supplemental multi-surface feature-placement proof. ADR-0042 remains authoritative for the decision itself.

## Accepted feature path

```text
semantic island geometry
    -> post-surface realization
    -> native Minecraft surface adaptation
    -> vanilla carvers
    -> feature-stage live chunk
    -> Skyforge additional-surface index
    -> skyforge:additional_surfaces PlacementModifier
    -> ordinary Minecraft PlacedFeature chain
    -> ordinary Minecraft ConfiguredFeature
    -> lower-surface live chunk mutation
```

Vanilla's original feature remains responsible for the ordinary highest-surface heightmap target. Skyforge supplements only lower independently valid surfaces.

## What is now empirically established

- the custom placement modifier participates in real NeoForge/Minecraft biome decoration;
- preserved lower native ground can be represented below a floating island;
- lower Skyforge surfaces can be represented without replacing the highest vanilla-owned surface;
- the modifier emits actual positions in a real client;
- a vanilla configured `simple_block` feature consumed those positions and wrote visible blocks into the chunk;
- save/reload remained stable;
- development-only diagnostic resources remain outside the production JAR.

This closes the basic reachability problem exposed by SF-IMP-0037's single-valued-heightmap boundary.

## New boundary: reachability versus suitability

SF-IMP-0038 also shows that **an exposed geometric surface is not automatically a suitable target for every feature family**.

The first additional-surface index intentionally uses a minimal dry-land rule: solid support with air immediately above it. That is enough to prove the placement primitive, but it can expose many lower ledges and cavity floors. It also intentionally excludes submerged seabeds because water above the support is not air.

The next layer should therefore classify or filter candidate surfaces according to concrete feature requirements rather than broadening the primitive indiscriminately.

Examples include:

- dry-land vegetation surfaces;
- submerged floors for kelp/seagrass-like features;
- snow/ice-capable surfaces;
- larger stable surfaces for trees or structures;
- minimum clearance or local thickness where a feature requires it.

Minecraft remains authoritative for block, fluid, biome, registry and feature semantics. Backend-neutral Skyforge should expose only geometry-derived suitability facts that prove necessary.

## Near-term priorities

1. define the smallest useful surface-suitability contract for the Minecraft adapter;
2. separate dry, submerged and other environment-specific placement rules without creating a parallel climate model;
3. test one real feature family against each justified suitability class;
4. preserve vanilla/modded feature definitions and placement composition wherever feasible;
5. keep structure-start timing as a separate integration problem;
6. return to morphology/playability refinement once these representation and placement boundaries are stable.

The SF-IMP-0038 gold-marker fixture is acceptance instrumentation only and must not be interpreted as intended world appearance.
