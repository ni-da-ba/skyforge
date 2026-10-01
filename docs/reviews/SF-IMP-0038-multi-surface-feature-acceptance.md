# SF-IMP-0038 — Supplemental Multi-Surface Feature Acceptance

**Status:** Accepted  
**Date:** 2026-08-31

## Accepted capability

SF-IMP-0038 establishes the first Minecraft-native multi-surface feature-placement primitive for Skyforge.

The accepted path is:

```text
post-carver live chunk
    + accepted Skyforge occupancy
    -> additional-surface index
    -> skyforge:additional_surfaces placement modifier
    -> ordinary Minecraft PlacedFeature chain
    -> ordinary Minecraft ConfiguredFeature
    -> live chunk mutation on a lower surface
```

Vanilla continues to own its ordinary highest-surface feature placement. Skyforge supplements only lower valid surfaces that vanilla's single-valued heightmap cannot simultaneously target.

## Automated evidence

The focused verifier and repository-wide `gradlew.bat check` passed on 2026-08-31.

Automated coverage proves:

- the placement-modifier codec is registered under NeoForge 1.21.1;
- the feature-stage scope exists only during biome decoration and is cleared afterward;
- the vanilla highest surface is excluded from supplemental placement;
- preserved lower native ground can be represented as an additional target;
- multiple vertically separated Skyforge surfaces can be represented;
- carved-away surfaces are rejected;
- fluid-covered surfaces are rejected from the first dry-land suitability rule;
- accepted SF-IMP-0036/0037 post-surface and native-surface behavior remains green;
- development-only feature fixtures are excluded from the production JAR.

## Real-client evidence

The first instrumented client run showed nonzero additional-surface candidates, modifier queries and emitted positions in chunks around the origin. The test world happened to place the Massif over ocean, which also demonstrated that submerged seabed is not treated as an ordinary dry-land placement target.

A follow-up deterministic development fixture used vanilla `minecraft:simple_block` with a gold-block provider through the same `skyforge:additional_surfaces` modifier. The client log then reported nonzero `markerBlocksAtEmittedPositions` across many origin-area chunks. Representative origin chunk `[0,0]` reported:

```text
availableAdditionalPositions=205
modifierQueries=26
emittedPositions=21
markerBlocksAtEmittedPositions=11
```

Several neighboring chunks realized 15–16 marker blocks. The gold markers were also visibly observed on supplemental lower surfaces in-game.

Save/reload remained clean and the world persisted without observed codec, registry, chunk or placement corruption.

## Architectural conclusion

The question **"can Minecraft features be placed on more than one independently valid vertical surface?"** is answered yes for the first supported mechanism.

The remaining problem is suitability rather than reachability. The geometric index deliberately exposes many lower air-facing surfaces. A production feature system must decide which of those surfaces are appropriate for dry vegetation, submerged vegetation, snow/ice, trees, structures or other feature families.

That suitability policy should remain backend-owned wherever it depends on Minecraft blocks, fluids, biomes, tags or feature definitions. Backend-neutral Skyforge should expose only geometry-derived facts when a concrete need justifies them.

## Non-claims

SF-IMP-0038 does not claim:

- automatic replay of every vanilla/modded feature;
- production vegetation density or aesthetics;
- underwater vegetation support;
- final stacked-island feature policy;
- structure-start awareness;
- that every exposed lower ledge is a suitable vegetation surface.

The development gold-marker fixture is acceptance instrumentation only and is not proposed gameplay behavior.
