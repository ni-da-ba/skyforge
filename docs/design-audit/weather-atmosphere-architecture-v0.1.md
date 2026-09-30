# Weather and Atmosphere Architecture v0.1

**Snapshot:** 2026-09-29  
**Status:** Provisional architecture lock. Numerical solver choice, cloud renderer, shader integration, severe-weather realization, and exact gameplay balance remain acceptance-test items.

## Decision

Weather is a first-class Skyforge simulation domain.

Skyforge should own climate semantics and the backend-neutral weather contract. A Minecraft atmospheric solver may realize that contract, but gameplay systems must not depend directly on renderer state or backend-specific cloud internals.

The current leading architecture is:

~~~text
SKYFORGE WORLD MODEL
  geography
  climate priors
  seasonality
  regional weather semantics
  exceptional-phenomenon admission
            |
            v
ATMOSPHERIC BACKEND
  leading Minecraft candidate: Aerodynamics4MC
            |
            v
BACKEND-NEUTRAL WEATHER STATE
  wind
  pressure
  temperature
  humidity
  turbulence
  shear
  convection / vertical flow
  cloud state
  precipitation
  visibility
  hazards
            |
      +-----+------------------+-------------------+
      |                        |                   |
      v                        v                   v
GAMEPLAY                  INSTRUMENTATION      PRESENTATION
aircraft / gliders        radar / vane         clouds
ecology / agriculture     forecasts            precipitation
infrastructure            warnings             fog / sky / sound
~~~

## Authority split

### Skyforge owns

- regional climate identity;
- geographical and ecological climate priors;
- seasonal and long-timescale tendencies;
- exceptional weather admission;
- semantic hazard classes;
- gameplay-facing weather meaning;
- backend-neutral weather queries;
- world consequences such as agriculture, ecology, infrastructure, and civilization response.

### Atmospheric backend owns

- numerical evolution of atmospheric state;
- local and mesoscale flow;
- pressure / temperature / humidity realization;
- terrain and building interactions;
- turbulence / shear / convection;
- derived precipitation and cloud fields where supported.

### Minecraft presentation owns

- volumetric or procedural cloud geometry;
- rain/snow particles;
- fog;
- sky coloration;
- visual light attenuation;
- rendered cloud shadows;
- atmospheric sound presentation.

Presentation is downstream of weather truth and must be replaceable.

## Leading numerical backend: Aerodynamics4MC

Aerodynamics4MC is the preferred Minecraft atmospheric-solver candidate because its current architecture already contains a multi-scale atmospheric hierarchy rather than only a cosmetic wind field.

Current implementation exposes or computes substantial weather state including:

- wind vectors;
- gusts;
- pressure;
- temperature;
- humidity;
- turbulence;
- shear;
- shelter;
- boundary-layer behavior;
- terrain effects;
- thermals / local heat;
- mesoscale moisture / lift / instability;
- cloud-water potential;
- precipitation intensity;
- snow fraction;
- larger-scale weather forcing.

Its public gameplay API remains more mature for wind/air-state sampling than for clouds/precipitation, so API extension or a thin adapter may still be required.

The preferred relationship is:

> Skyforge supplies climate semantics and boundary conditions; Aerodynamics4MC realizes atmospheric physics.

Do not couple Skyforge semantics to A4MC implementation classes.

## Canonical weather sample

Skyforge should eventually expose a backend-neutral query approximately equivalent to:

~~~text
AtmosphereSample(position, time):
  windVector
  meanWind
  gustWind
  verticalVelocity
  turbulence
  shear
  pressure
  pressureTendency
  temperature
  humidity
  cloudCover
  cloudWater
  cloudBase
  cloudTop
  precipitationRate
  precipitationType
  visibility
  solarIrradiance
  icingPotential
  electricalActivity
  hazardClass
  eventIdentity
~~~

Not every field must exist in the first implementation.

Consumers should degrade gracefully when optional channels are absent.

## Gameplay consumers

The same weather truth should drive:

- Aeronautics / Sable aircraft response;
- gliders;
- soaring fauna;
- turbines;
- wind vanes / windsocks;
- weather stations;
- radar / forecasting;
- agriculture;
- ecology;
- precipitation accumulation;
- route planning;
- settlement warnings;
- environmental audio;
- visual precipitation;
- cloud presentation.

Consumers should query the canonical weather contract rather than each other.

## Clouds

Cloud physics and cloud geometry must remain separate.

The atmospheric backend may produce coarse descriptors such as:

- cloud water;
- coverage;
- base altitude;
- top altitude;
- convective character;
- precipitation;
- motion/advection.

A renderer may turn those coarse fields into much higher-frequency procedural geometry.

Exact rendered cloud voxels do not need one-to-one correspondence with atmospheric simulation cells.

This separation is required for performance, backend neutrality, and renderer replacement.

## Lighting and shading

Gameplay lighting consequences must not query the graphics renderer.

Skyforge/weather may derive a semantic solar-irradiance or atmospheric-transmittance value from:

- solar angle;
- cloud optical depth;
- cloud cover;
- atmospheric conditions.

Gameplay systems such as crops or solar generation should consume this semantic value.

Minecraft rendering may separately depict it through:

- sky darkening;
- diffuse lighting;
- fog/haze;
- cloud transmittance;
- optional GPU cloud-shadow maps.

Do not continuously mutate Minecraft block-light state merely to represent moving cloud shadows.

## Presentation strategy

Initial acceptable implementation:

- authoritative weather state;
- A4MC wind/air response;
- vanilla or modest cloud rendering;
- wind-driven precipitation;
- fog/visibility adjustment;
- ambient audio;
- broad sky/light attenuation.

Later upgrades may add:

- volumetric cloud rendering;
- cloud-specific lighting;
- procedural cloud detail;
- GPU transmittance maps;
- moving cloud shadows;
- advanced storm visuals.

The weather system must remain gameplay-complete even if the highest-end cloud renderer is disabled.

## Candidate presentation components

### Particle Rain

Strong provisional presentation candidate if its wind and precipitation presentation can be driven from canonical Skyforge/A4MC weather.

It must not own wind semantics.

### AmbientSounds

Strong keep as environmental-audio content.

### Sound Physics Remastered

Strong keep as acoustic propagation.

### Simple Clouds

A/B visual candidate only.

It should not become the authoritative localized-weather simulation unless it can be cleanly subordinated to Skyforge/A4MC atmospheric state.

### Weather2 / Expanded Weather2 Dynamics

Reserve for severe-weather R&D, presentation ideas, or specialist phenomena.

Do not run Weather2 as a parallel global wind/storm authority beside A4MC.

If selected later for tornado/destructive-storm realization, it must be subordinated to Skyforge weather semantics and should act as a phenomenon implementation rather than an independent climate model.

## Supplementaries integration

The Supplementaries Weather Vane should eventually consume canonical wind.

Target semantics:

~~~text
local wind direction -> vane orientation
local wind magnitude -> analog/redstone output
~~~

Its stock rain/thunder-derived 'wind' state should not be treated as authoritative.

## Backend-neutrality constraint

Bespoke work is justified when it belongs to weather semantics or cross-system gameplay:

- climate;
- weather state;
- atmosphere queries;
- aviation response;
- ecology;
- forecasting;
- infrastructure consequences.

Bespoke work is discouraged when it exists only to patch one Minecraft renderer:

- shader-specific cloud geometry;
- Minecraft depth-buffer hacks;
- Simple Clouds internal data structures;
- backend-specific fog pipelines.

Those belong behind presentation adapters.

## Acceptance sequence

1. Define canonical weather/atmosphere interface.
2. Map Aerodynamics4MC outputs into that interface.
3. Validate aircraft, glider, fauna, and instrument consumers.
4. Add precipitation/fog/audio presentation.
5. Validate cloud-field descriptors.
6. A/B cloud renderers.
7. Add semantic irradiance.
8. Add advanced rendered cloud lighting/shadows only after the above remains stable.
9. Prototype severe-weather phenomena only after the baseline authority model is proven.

## Design principle

> Weather truth is a world-system contract. Clouds, particles, fog, sound, and shadows are replaceable depictions of that truth.
