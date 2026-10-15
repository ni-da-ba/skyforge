# Windy + A4MC — parallel authoritative-airflow prototype

**Owner authorization:** 2026-10-05  
**Tracking issue:** [#1463](https://github.com/ni-da-ba/skyforge/issues/1463)  
**State:** Isolated prototype; not part of S1, S2, PT-04 or shipping alpha. No visual acceptance inferred from compilation.

## Exact sources

- Windy (NeoForge 1.21.1) upstream commit `3ba5523481d22179cdf0fa7dab0743210f136bb8`, advertised mod 1.2.0, MIT.
- A4MC accepted 0.2.2 source commit `171d8dc593651d6b34e3bfecaf6469a11b53b433`, public Minecraft-independent API.

Never edit the upstream sources in-place in this repository. CI checks out pinned sources and applies nine ordered patches followed by five assertion-checked rendering transforms:

1. `0001-authoritative-wind-provider.patch` — adds an optional external vector-field hook to Windy's existing particles and ribbons. When externally bound, the internal randomized wind is skipped, missing samples fail closed, streaks sample each location, ribbons follow 3D local velocity rather than decorative horizontal weave, and high-altitude flight may spawn nearby ribbons.
2. `0002-a4mc-client-authority-binding.patch` — binds the seam to `AeroClientWindApi.sample` with `SERVER_AGGREGATED_PREFERRED` (trusted coarse / aggregated flow, never client-only local voxel detail). A4MC remains the sole wind/pressure physics authority. The provider is registered only if A4MC is loaded.
3. `0003-external-wind-command-guard.patch` — reports the physical A4MC wind vector and gust in `/windy status` and rejects `/windy gust` or `/windy calm` when A4MC owns airflow, preventing misleading local overrides.
4. `0004-trusted-wind-and-ambient-guard.patch` — admits only server-trusted A4MC airflow and suppresses Windy's unrelated biome particle drift while physical authority is active.
5. `0005-physical-flight-cues.patch` — wisps advect with the local trusted 3D velocity at each tick, fail closed when flow disappears, and ribbons move at physical m/s ÷ 20 without their previous randomized speed factor. Uncalibrated tiny ambient wind-motes are suppressed in external-authority mode pending a bounded particle fidelity/performance trial.
6. `0006-live-trusted-flow-probe.patch` — adds opt-in, once-per-client log markers distinguishing mod binding, missing wind and genuinely server-trusted wind. The actual-client CI profile explicitly enables this probe and requires a trusted sample within its dwell interval; normal users never incur the diagnostic logging.
7. `0007-suspended-island-wind-corridors.patch` — permits physical wind wisps below island overhangs without disabling standalone sky exposure rules; anchors 3D ribbons near player altitude rather than island heightmap tops and rejects ribbon spawns inside terrain. Real Skyforge overhang captures are still a manual admission gate.
8. `0008-wind-sample-profiling.patch` — opt-in rolling 128-call sampling-window diagnostics with available-count and p50/p95/max query duration. **Startup windows may be entirely unavailable**, so they must not be treated as performance qualification.
9. `0009-trusted-burst-probe.patch` — a one-time, development-only 128-position *API microbenchmark* executed only after trusted data arrives. It records how many nearby queries remain available, p50/p95/max call cost, and is required by CI to contain at least 120 available samples. It is deliberately not real-world frame time, sustained traffic, or visual-compositing evidence.
10. `apply-0010.py` — experimental rendering fallback: replaces Windy's bespoke `POSITION_TEX_COLOR` ribbon type with Minecraft's built-in translucent entity render type and supplies its expected vertex attributes, so shader pipelines can classify the ribbons as entity translucency. This is a hypothesis to test, not accepted shader compatibility; it can change lighting/depth behavior and requires owner review.
11. `apply-0011.py` — moves the ribbon draw callback from `AFTER_TRANSLUCENT_BLOCKS` to `AFTER_ENTITIES`, keeping the built-in entity-translucent type. This tests whether submission after the shader's translucent-world composition caused the ribbons to disappear. It remains an experiment until the owner confirms the exact client result.
12. `apply-0012.py` — replaces the direct tessellator/MeshData draw with submission and flush through Minecraft's shared render buffer using the built-in entity-translucent render type. This tests whether the shader pipeline only captures standard buffer-source submissions. It is a rendering-path hypothesis, not a compatibility claim; exact shader-on client review remains required.
13. `apply-0013.py` — changes only the ribbon layer from translucent entity rendering to Minecraft's standard entity cutout pipeline. This sacrifices smooth alpha blending to determine whether Atmospheric Shaders is dropping or miscompositing only the translucent entity pass. A visible result would be a diagnostic lead, not an accepted final aesthetic.
14. `apply-0014.py` — adds an explicit JVM-property diagnostic (`skyforge.windy.shaderParticleProbe=true`) that suppresses custom ribbons and raises only the count of standard Wisp particles when nonzero, server-trusted A4MC flow is present. Particle direction and speed continue to follow sampled physical velocity. The probe is off by default and exists only to determine whether Atmospheric Shaders preserves Windy's ordinary particle render path.

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
- **Actual client:** require proof of A4MC mod binding and live server-trusted wind reception (opt-in probe), then test no-source, still air, crosswind, shear, thermals, sink, near/above/**under** floating islands, and inside sheltered flight corridors; two players at different heights and positions; reconnect/dimension switch; night/storm and sustained gliding. Verify against sampled A4MC public API traces, not just appearance.
- **Visual issues:** The owner confirmed that Windy is visible with Atmospheric Shaders disabled and invisible when enabled, with Distant Horizons and Simple Clouds loaded in the same profile. Experiments 0010 and 0011 (entity-translucent RenderType plus the AFTER_ENTITIES timing) did not resolve the shader-on failure. Experiment 0012 submits the same geometry through Minecraft's shared entity BufferSource batch; this tests shader-pipeline capture of standard buffer submissions. Experiment 0013 routes the same geometry through the standard entity cutout pipeline, testing whether the failure is isolated to translucent entity compositing; the result remains unverified. The exact S5 client must be relaunched with the resulting artifact and checked with shaders both on and off before compatibility can be claimed. Experiment 0014 provides a particle-only diagnostic: if physical wisps appear while custom ribbons are suppressed, the remaining failure is specific to the ribbon path and a particle-based fallback can be evaluated. If neither appears, the shader or its cloud compositing also affects standard particles, or the probe has no eligible airflow; logs and a clear-sky comparison help distinguish those cases.
- **Weather overlap:** The actual-client smoke explicitly blacklists `dev.fallingcloud.windy.particle.*` under A4MC's particle wind rules, which take precedence over whitelists; avoid duplicate snow/dust/rain when Particle Rain later enters the stack.
- **Particle behavior boundary:** wisps and ribbons now sample physical m/s ÷ 20 travel; other debris effects (leaves, snow and dust) still have stylized drift, gravity and inertia and must not be treated as quantitative aircraft instrumentation. Dedicated same-position tracing and sustained flight **frame-time** p95 profiling remain required before promotion. The trusted 128-query burst cost is recorded separately and must not be substituted for natural traffic or frame-time p95; a separate paired flight benchmark remains mandatory.
- **Skyforge cloud semantics:** Simple Clouds currently renders with DH in accepted S1, but mapping authored weather to Simple Clouds remains a separate unclosed integration; do not pretend wind patch solves it.
- **Authority nuance:** client-local A4MC L2 is deliberately excluded from flight-readable ribbons since server gameplay does not trust it. Future purely cosmetic local detail could be allowed if visibly distinguishable from usable lift.
- **API upstreamability:** the generic `WindStateProvider` seam is intended as an upstream-compatible proposal; the A4MC dependency belongs in a separate optional adapter rather than a permanent fork. Split or submit upstream before production promotion.
- **Completeness:** registration currently uses a small Skyforge build-only overlay to avoid altering the live S1 stack. Final provider/performance/permissions tests and packaging are not yet accepted. Manual human visual approval is mandatory.

## Stop conditions

Do **not** merge, introduce into `canonical-mod-ledger`, or change S1 production resources based only on `assemble` success. Require exact full-stack client/server smoke, unchanged glider/Sable behavior, multiplayer source consistency, particle-limit adherence, shader+cloud+Distant Horizons visual integrity, and p95 frametime bounds. Track hard failures and preserve fallbacks. All other agents retain their existing branches and file ownership.
