# Skyforge Atmosphere / Rendering Strategy

**Status:** future Authorship + Implementation topic; not a Bootstrap blocker  
**Scope:** semantic weather, Minecraft rendering integration, shader/Distant Horizons strategy  

## Core decision

Skyforge should eventually own a **reference atmosphere/rendering stack** rather than depending on arbitrary third-party visual combinations.

The world simulation, weather semantics, and visual realization must remain separate:

```text
Skyforge semantic weather / atmosphere
        -> client interpolation + rendering bridge
        -> Skyforge reference shader / atmosphere renderer
        -> Minecraft + Iris + Distant Horizons + retained cloud tooling where useful
```

The simulation is authoritative; rendering is a consumer.

## Semantic weather authority

The server-side weather model should be coarse, deterministic, and geography-aware rather than CFD. Candidate authoritative fields include:

- horizontal wind vector;
- vertical wind / lift / sink;
- humidity;
- temperature;
- pressure anomaly;
- cloud cover;
- lower-cloud-deck coverage;
- precipitation;
- convection;
- turbulence;
- electrical activity;
- visibility.

Climate and weather should remain distinct. Slow climatology may derive from latitude, altitude, season, Lower Sea influence, island morphology, geothermal context, and other accepted authored fields. Actual fronts, storms, convection complexes, subsidence regions, and jet cores may then move through that background state.

Unobserved regions should remain semantic. Do not continuously tick full atmospheric detail for unloaded provinces.

## Cloud hierarchy

Skyforge should eventually distinguish two major visual systems:

1. **local / inhabited-layer weather clouds** — cumulus, stratus, towers, anvils, fronts, storm volumes;
2. **persistent lower cloud deck** — broad stratiform atmospheric floor masking most of the Lower Sea.

Lower-deck openings should be tied to accepted weather state where practical rather than acting as arbitrary scenery. Strong updrafts, dry subsidence, storm complexes, and other large-scale circulation features may modulate deck coverage.

## Lower Sea realization

The Minecraft backend should not require a literal planet-sized ocean running beneath every island.

When visible through deep-cloud openings, the Lower Sea may be realized as a distant rendered/impostor surface with atmospheric attenuation, lighting, and optional low-detail silhouettes of Dark Mountains / old continental remnants.

Only a future backend or future dedicated Minecraft implementation needs full-fidelity Lower Sea geography.

## Distant Horizons

Distant Horizons is likely part of the intended Minecraft visual stack because Skyforge depends heavily on long-range island silhouettes and meaningful negative space.

DH should own **distant solid terrain**, not clouds or weather.

The atmosphere renderer must integrate with both ordinary terrain depth and DH depth so that:

- distant islands correctly occlude clouds;
- lower cloud volumes can swallow island undersides correctly;
- haze / fog applies consistently to normal and LOD terrain;
- weather does not double-fog or incorrectly overdraw DH geometry.

Moving aircraft, mobs, contraptions, and vessels should not be assumed to receive DH terrain-style LOD automatically; separate culling/silhouette strategies may be needed later.

## Shader strategy

A bespoke **Skyforge Iris shaderpack** is a viable long-range direction and may be preferable to unrestricted arbitrary-shader compatibility.

Do **not** build a new shader loader or competing terrain-render pipeline. Prefer an Iris-compatible shaderpack and a thin Skyforge client bridge that exposes accepted semantic state to the shader.

Useful shader-visible state may include:

- humidity;
- storm intensity;
- cloud coverage;
- lower-deck coverage;
- precipitation;
- visibility / extinction;
- lightning flash;
- altitude / pressure regime;
- sun occlusion;
- auroral / upper-atmosphere state.

The reference shader should own final atmospheric extinction/fog so Skyforge weather, shader fog, and Distant Horizons fog do not multiply into inconsistent visibility.

## Reuse-first shader development

Preferred progression:

```text
A. validate existing shader + Distant Horizons + candidate cloud tooling
B. adapt / derive a Skyforge reference shader from a permissively licensed foundation
C. integrate Skyforge semantic weather uniforms and atmospheric behavior
D. customize or replace cloud rendering only if retained tooling proves limiting
E. consider fully bespoke atmosphere rendering only when executable evidence justifies it
```

Do not reimplement solved generic rendering features merely to claim ownership. Existing permissively licensed shader architecture should be reused where practical, subject to license review.

## Candidate visual responsibilities

A Skyforge reference shader may eventually own or coordinate:

- altitude-dependent atmospheric scattering;
- long-range humidity / visibility extinction;
- consistent Distant Horizons haze;
- sun/moon/starlight treatment;
- storm darkening and cloud lighting;
- distant rain curtains / virga;
- aurora and high-altitude sky darkening;
- Lower Sea rendering through deep-cloud openings;
- cloud shadow / terrain-darkening integration where technically feasible;
- End-transition presentation as ordinary atmosphere gives way to extreme upper sky.

Volumetric cloud raymarching need not initially be bespoke. Existing tooling such as Simple Clouds may remain a candidate renderer if it can be cleanly driven by Skyforge state and integrated with the selected Iris/DH stack.

## Weather gameplay coupling

Visual weather and gameplay weather should describe the same authoritative phenomenon.

Aircraft / gliders may query the same semantic field used by the renderer for:

- headwind / tailwind;
- crosswind;
- updraft;
- downdraft;
- turbulence / shear;
- storm-core hazard;
- reduced visibility.

The renderer may add deterministic high-frequency visual detail, but it must not invent gameplay-significant weather independent of the authoritative state.

## Performance strategy

The intended visual stack may combine several expensive systems:

- Distant Horizons;
- an Iris shaderpack;
- volumetric clouds;
- Aeronautics/Create moving craft;
- large-distance atmospheric effects.

Therefore benchmark the **combined** stack rather than proving components independently and assuming composition will work.

Likely quality tiers should preserve one visual identity while scaling sample counts, shadow distance, cloud quality, reflections, and DH range.

Priority GPU budget should favor:

1. Distant Horizons / distant terrain readability;
2. atmosphere / visibility;
3. clouds / weather;
4. good sunlight and shadows;
5. water;
6. material polish;
7. optional expensive reflections / GI.

## Required future feasibility spike

Before committing to a production rendering implementation, benchmark a representative stack containing:

```text
Minecraft 1.21.1 / NeoForge
+ Iris-compatible shader path
+ Distant Horizons
+ candidate reference shader
+ candidate volumetric clouds
+ representative Skyforge islands
+ Create / Aeronautics craft
```

Explicit review cases should include:

- distant islands through clouds;
- island undersides intersecting the lower deck;
- DH terrain through storm haze;
- sunrise / sunset / night;
- distant rain and lightning;
- cloud shadows or fallback atmospheric darkening;
- altitude changes toward the upper atmosphere;
- FPS, frametime, and VRAM behavior;
- Aeronautics/Create rendering under the same stack.

## Lane ownership

**Authorship** owns backend-neutral atmospheric geography and semantic environmental intent where new authored causes/evidence are required.

**Implementation** owns Minecraft rendering integration, weather runtime, client synchronization, shader/DH bridges, and performance.

**Content / Experience** owns gameplay interpretation/tuning of weather hazards, aviation progression, forecasting, route incentives, and player-facing use.

A bespoke reference shader is therefore a future cross-lane consumer milestone, not a reason to reopen Bootstrap morphology or current world-generation acceptance.
