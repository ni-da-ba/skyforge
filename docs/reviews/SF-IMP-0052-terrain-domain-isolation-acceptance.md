# SF-IMP-0052 — Terrain-domain isolation acceptance

**Status:** Accepted  
**Issue:** #54  
**PR:** #55  
**Decision:** ADR-0056

## Scope

SF-IMP-0052 removes Skyforge from the ordinary base-world generation stream and establishes the exact-volume primitives required for later island-owned population.

This milestone deliberately does **not** repopulate the island yet. Surface vegetation, ores and structures return through domain-local population milestones rather than by exposing Skyforge to the base world's occurrence stream.

## Automated evidence

The implementation establishes:

1. no explicit Skyforge domain scope means `BASE_WORLD`;
2. ordinary `SkyforgeNoiseBasedChunkGenerator#getBaseHeight(...)` delegates directly to vanilla in `BASE_WORLD`;
3. ordinary base-world structures bypass Skyforge support/admission policy;
4. vanilla surface construction and vanilla/modded biome decoration complete before Skyforge blocks are written;
5. the pre-decoration native surface-material snapshot is representation-only, never base-world placement authority;
6. explicit island height queries inspect exactly one `SkyIslandWorldVolumeId` and never fall through to vanilla terrain or another island;
7. accepted structure admission/accommodation machinery remains available only behind explicit island scope;
8. the development fixture fingerprints every completed native position not claimed as solid by the deterministic Skyforge materialization and requires those positions to remain unchanged after island realization.

CI #232 passed on the corrected ownership-aware proof head.

## Interactive proof — 2026-09-02

`runDomainIsolationClient` completed without a generation crash and emitted:

```text
SF-IMP-0052 BASE WORLD ISOLATED: chunk=[0, 0], protectedPositions=63234, protectedFingerprint=1271748767702749497, skyforgeSolidPositions=35070, islandSample=BlockPos{x=8, y=223, z=8}
```

Observed result:

- the floating Massif was present above the origin area;
- the forced woodland mansion remained on native terrain beneath the island rather than being lifted or suppressed;
- surrounding base-world terrain appeared otherwise unchanged, with no broad vegetation/terrain generation shadow attributable to the island;
- the comparatively bare island was expected because island-owned population is intentionally deferred.

The ownership-aware fingerprint permits physical composition only where the deterministic Skyforge materialization contributes solid terrain. Every other protected position remains byte-for-byte stable.

## Verdict

**Accepted.** SF-IMP-0052 proves the generation lifecycle/domain boundary required by ADR-0056: base-world generation can complete with Skyforge observationally absent, after which an exact island may be materialized additively without mutating unrelated native terrain.

This acceptance does not claim independent island population is complete.

## Follow-on boundary

SF-IMP-0053 adds the first exact-volume, independently seeded island population stream using native Minecraft/modded registry definitions. It must prove native surface feature/vegetation reuse and vertically stacked volumes with independent occurrence streams, without restoring global highest-surface competition.
