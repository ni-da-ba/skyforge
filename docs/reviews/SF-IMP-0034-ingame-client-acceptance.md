# SF-IMP-0034 — In-Game Development Client Acceptance

**Status:** Accepted  
**Date:** 2026-08-31

## Scope

SF-IMP-0034 closes the first interactive Minecraft validation loop for Skyforge. It accepts the development-client realization path only; it does not accept final morphology quality, final world-generation timing, packaged-mod distribution, or production terrain presentation.

## Automated gates

The following local gates passed on the accepted branch:

- `scripts\verify-sf-imp-0034-ingame-preflight.bat`;
- repository-wide `gradlew.bat check`;
- backend-neutral independence verification;
- NeoForge/FML-aware unit and lifecycle tests;
- deterministic development-specimen materialization.

Hosted GitHub Actions remain unavailable, so local validation is authoritative for this milestone.

## Manual in-client gate

A real ModDevGradle Minecraft 1.21.1 client was launched with the development specimen enabled. A new disposable Creative Overworld was created and inspected near `x=0, y=300, z=0`.

Observed result:

- the deterministic Massif was clearly present;
- it appeared coherent across the visible multi-chunk footprint with no obvious ownership seam;
- surrounding native Minecraft terrain remained present rather than being globally erased by Skyforge AIR;
- the generated blocks persisted after save/quit and re-entry;
- the specimen therefore demonstrated the complete interactive path from backend-neutral Skyforge geometry through NeoForge lifecycle delivery to visible persistent Minecraft chunk storage.

## Design observations retained for later work

Manual inspection also found that the current Massif is visibly preliminary:

- the underside is oversized/heavy relative to the desired suspended-landform proportion;
- the overall shape is not yet judged production-playable;
- the engineering dirt/stone/deepslate palette is intentionally crude.

These findings are morphology/playability evidence, not failures of the integration milestone. They should be addressed when morphology tuning is revisited deliberately rather than by distorting the integration specimen solely to improve this smoke test.

## Accepted conclusion

The following path is now empirically proven in a real Minecraft client:

```text
backend-neutral Skyforge island
    -> bounded world catalog query
    -> terrain semantics
    -> Minecraft block-key projection
    -> live BlockState resolution
    -> additive ChunkAccess mutation
    -> NeoForge newly-generated-chunk lifecycle
    -> visible in-game terrain
    -> normal save/reload persistence
```

## Deferred

SF-IMP-0034 does not claim acceptance of:

- the final earlier world-generation insertion point;
- native heightmap/lighting/feature/structure participation;
- packaged JAR / CurseForge installation;
- user-facing world/config bootstrap;
- final material palette or biome adaptation;
- final morphology/playability quality;
- production caching or realization strategy.

The next packaging gate should prove that a self-contained Skyforge JAR behaves as an ordinary NeoForge 1.21.1 mod in a clean CurseForge profile. The subsequent world-generation integration work should then move realization earlier so native Minecraft systems can reason about Skyforge terrain during generation.