# SF-IMP-0052 — Terrain-domain isolation acceptance

**Status:** Pending interactive proof  
**Issue:** #54  
**PR:** #55  
**Decision:** ADR-0056

## Scope

SF-IMP-0052 removes Skyforge from the ordinary base-world generation stream and establishes the exact-volume primitives required for later island-owned population.

This milestone deliberately does **not** repopulate the island yet. Surface vegetation, ores and structures will return through domain-local island population milestones rather than by exposing Skyforge to the base world's occurrence stream again.

## Automated evidence

The implementation now establishes:

1. no explicit Skyforge domain scope means `BASE_WORLD`;
2. ordinary `SkyforgeNoiseBasedChunkGenerator#getBaseHeight(...)` delegates directly to vanilla in `BASE_WORLD`;
3. ordinary base-world structure candidates delegate directly to vanilla and do not run Skyforge support/admission policy;
4. vanilla surface construction completes before any Skyforge block is written;
5. vanilla/modded biome decoration completes before any Skyforge block is written;
6. a pre-decoration native surface-material snapshot is retained only for later island representation and is never used as a base-world placement authority;
7. later vegetation cannot replace that captured terrain material as the island's surface representation;
8. explicit island-domain height queries inspect one exact `SkyIslandWorldVolumeId` and never fall through to vanilla terrain or another island;
9. the accepted structure admission/accommodation machinery remains available only behind an explicit island-generation scope;
10. the dedicated development fixture fingerprints the completed native world below y=160 immediately before island realization and requires that fingerprint to remain identical afterward.

CI #219 passed the full repository build/test/evidence gate on implementation head `0f6039dd9d13d0931106f33a2fbbec3de6c73ceb`.

## Interactive fixture

Run:

```cmd
cd C:\Users\nicho\Documents\skyforge
git fetch origin
git switch agent/sf-imp-0052
git pull --ff-only
gradlew.bat :skyforge-neoforge-1211:runDomainIsolationClient
```

Create a **new disposable** world using **Skyforge Development** and inspect near:

```text
/tp 8 242 8
```

The development datapack still forces one nearby woodland-mansion candidate at the origin. Under SF-IMP-0052 it is intentionally base-world-owned: it should remain on the native terrain below the floating island rather than being lifted onto or suppressed by the island.

## Interactive acceptance criteria

Acceptance requires:

1. terminal emits `SF-IMP-0052 BASE WORLD ISOLATED`;
2. one floating Skyforge Massif is present above the origin area;
3. the forced mansion is present on native terrain below the island, not on the island;
4. ordinary lower vegetation/terrain around the mansion is not replaced by a broad empty generation shadow attributable to the island;
5. no obvious island-induced water/ocean-floor anomaly is visible where such terrain is present;
6. the island itself may be comparatively bare in this milestone — that is expected because island-owned population is now deliberately separated from base-world occurrence;
7. no generation crash occurs;
8. save/reload retains both the completed base world and the later additive island.

The self-checking fingerprint makes criterion 1 a hard lower-world mutation proof for the origin chunk: if later island realization changes any block at or below y=160, the fixture throws instead of emitting the marker.

## Follow-on boundary

SF-IMP-0053 will add the first exact-volume, independently seeded island population stream using native Minecraft/modded registry definitions. It must not reintroduce global highest-surface competition.
