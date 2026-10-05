# Windy + A4MC — parallel authoritative-airflow prototype

**Owner authorization:** 2026-10-05  
**Tracking issue:** [#1463](https://github.com/ni-da-ba/skyforge/issues/1463)  
**State:** Isolated prototype; not part of S1, S2, PT-04 or shipping alpha. No visual acceptance inferred from compilation.

## Exact sources

- Windy (NeoForge 1.21.1) upstream commit `3ba5523481d22179cdf0fa7dab0743210f136bb8`, advertised mod 1.2.0, MIT.
- A4MC accepted 0.2.2 source commit `171d8dc593651d6b34e3bfecaf6469a11b53b433`, public Minecraft-independent API.

Never edit the upstream sources in-place in this repository. CI checks out pinned sources and applies the two intentionally narrow patches in order:

1. `0001-authoritative-wind-provider.patch` — adds an optional external vector-field hook to Windy's existing particles and ribbons. When externally bound, the internal randomized wind is skipped, missing samples fail closed, streaks sample each location, ribbons follow 3D local velocity rather than decorative horizontal weave, and high-altitude flight may spawn nearby ribbons.
2. `0002-a4mc-client-authority-binding.patch` — binds the seam to `AeroClientWindApi.sample` with `SERVER_AGGREGATED_PREFERRED` (trusted coarse / aggregated flow, never client-only local voxel detail). A4MC remains the sole wind/pressure physics authority. The provider is registered only if A4MC is loaded.
3. `0003-external-wind-command-guard.patch` — reports the physical A4MC wind vector and gust in `/windy status` and rejects `/windy gust` or `/windy calm` when A4MC owns airflow, preventing misleading local overrides.

## Authority boundary

```
Skyforge climate/weather meanings -> A4MC L0/L1 (+ eligible server L2)
    -> replicated public 3D effective wind / gust
        -> Windy only as client visual effects
Skyforge / A4MC gameplay consumers remain untouched.
```

**Velocity units:** A4MC returns m/s; ribbons advance at `velocity / 20` blocks/tick (Minecraft nominal 20 TPS). Particle density and alpha scale with wind speed, which is only presentation normalization. Cosmetic noise may never independently select airflow direction.

**Unavailable data:** never resurrect Windy's random gusts. The patched Windy remains standalone-capable only when *not* bound to A4MC.

## Current boundaries / known TODOs

- **Proof stage 1:** deterministic pinned-source build and bytecode presence through `wby-windy-a4mc-prototype.yml`; must be PASS before any staging claim.
- **Actual client:** test no-source, still air, crosswind, shear, thermals, sink, near/above/between floating islands, two players at different heights and positions, reconnect/dimension switch, night/storm and sustained gliding. Verify against sampled A4MC public API traces, not just appearance.
- **Visual issues:** WindRibbons still draws at `AFTER_TRANSLUCENT_BLOCKS` with a custom render type, so Iris/Atmospheric Shaders + DH/SSRD + Simple Clouds depth ordering remains unknown until a real client captures the overlap. Ordinary wisps may be retained independently if ribbons fail.
- **Weather overlap:** A4MC particle wind selection may also affect Windy particles. Validate class blacklist/exclusion, and avoid duplicate snow/dust/rain when Particle Rain later enters the stack.
- **Skyforge cloud semantics:** Simple Clouds currently renders with DH in accepted S1, but mapping authored weather to Simple Clouds remains a separate unclosed integration; do not pretend wind patch solves it.
- **Authority nuance:** client-local A4MC L2 is deliberately excluded from flight-readable ribbons since server gameplay does not trust it. Future purely cosmetic local detail could be allowed if visibly distinguishable from usable lift.
- **API upstreamability:** the generic `WindStateProvider` seam is intended as an upstream-compatible proposal; the A4MC dependency belongs in a separate optional adapter rather than a permanent fork. Split or submit upstream before production promotion.
- **Completeness:** registration currently uses a small Skyforge build-only overlay to avoid altering the live S1 stack. Final provider/performance/permissions tests and packaging are not yet accepted. Manual human visual approval is mandatory.

## Stop conditions

Do **not** merge, introduce into `canonical-mod-ledger`, or change S1 production resources based only on `assemble` success. Require exact full-stack client/server smoke, unchanged glider/Sable behavior, multiplayer source consistency, particle-limit adherence, shader+cloud+Distant Horizons visual integrity, and p95 frametime bounds. Track hard failures and preserve fallbacks. All other agents retain their existing branches and file ownership.
