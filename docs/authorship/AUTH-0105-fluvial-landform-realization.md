# AUTH-0105 — Fluvial landform realization

## Purpose

DR-70 human review exposed a boundary in the first island-local hydrology tranche. Skyforge could
author watershed topology, channel hierarchy, profile class, naturalized centerlines, terrain-response
potentials, retained water, and discrete drops, yet the Minecraft realization still read as a narrow
trench carrying water across otherwise unrelated terrain.

AUTH-0105 adds the missing neutral landform contract. An accepted channel is now also a continuous
channel-and-valley terrain primitive. The primary acceptance criterion is intentionally stronger than
"water is present": **with visible water hidden, the terrain must still contain a legible drainage
landform.**

## Dependency

~~~text
AUTH-0019 coherent hydrology
    -> accepted naturalized centerline + profile/discharge hierarchy
    -> AUTH-0105 fluvial reach geometry
    -> continuous dry channel/valley terrain field
    -> authored wet-corridor surface potential
    -> later backend terrain projection
    -> later material/ecology/water realization
~~~

AUTH-0105 does not alter watershed routing or coherent reach selection.

## Reach geometry

Each accepted naturalized reach produces one SkyIslandFluvialReachGeometry.

Horizontal scale is expressed in island-local world units relative to the accepted watershed planning
spacing. Vertical scale remains normalized authored elevation potential.

The geometry exposes:

- bankfullHalfWidth — channel-scale dry terrain corridor;
- wetHalfWidth — contained visible-water corridor strictly inside the banks;
- valleyHalfWidth — wider landform influence;
- bedDepthPotential — total centerline lowering;
- waterDepthPotential — water-surface offset above the bed;
- bankReliefPotential — dry-bank relief above the bed;
- crossSectionExponent — profile-sensitive channel cross-section shape.

Width responds continuously to accepted bankfull-width potential, relative discharge, and corridor
scale. It is not collapsed to a fixed three- or five-block backend trench.

## Profile-sensitive valleys

AUTH-0012 profile identity remains authoritative.

- ALLUVIAL reaches receive the broadest valley/floodplain corridor and a gentler cross section.
- INCISED reaches receive a narrower corridor with stronger confinement.
- CASCADE reaches remain the most confined and direct.

The field never invents another route. Every corridor is centered on the exact accepted AUTH-0017
naturalized path.

## Longitudinal bed

The accepted AUTH-0019 continuous hydrologic terrain supplies the reference grade at each reach
endpoint. AUTH-0105 interpolates that grade along the naturalized path and requires a small
profile-scaled downstream fall before applying the accepted reach's bed lowering.

This creates one coherent longitudinal bed target for the reach instead of sampling and digging the
local terrain independently at each backend voxel.

At overlaps/confluences the lower compatible terrain surface wins. For visible channel water, the
lower compatible authored water surface wins. This prevents an overlapping reach from creating an
uphill pool.

## Cross-section and valley blending

Inside the bankfull corridor the dry terrain rises continuously from the authored bed toward the bank
using a profile-sensitive cross-section exponent.

Outside the bankfull corridor, the field blends a wider valley shoulder back into the accepted
AUTH-0019 surface with smootherstep falloff. The adjustment is therefore local to accepted drainage
rather than an island-wide morphology retune.

This is a procedural landform primitive, not a hydraulic or sediment-transport simulation.

## Wet-corridor contract

SkyIslandFluvialTerrainField.waterSurfacePotential(position) exposes water support only inside the
accepted contained wet corridor. Water depth is always below authored bank relief.

Retained standing-water bodies remain governed by AUTH-0009/AUTH-0086. AUTH-0105 does not replace
their footprint or water-level semantics.

## Evidence

authorship-fluvial-landforms-v1 renders three directly comparable views:

1. BASE — accepted AUTH-0019 continuous hydrologic terrain;
2. DRY FLUVIAL — AUTH-0105 terrain with all water hidden;
3. WATER OVERLAY — the exact AUTH-0105 wet-channel corridor over the same dry terrain.

The atlas includes the previous DR-70 specimen plus accepted representative hydrology controls,
including the retained-basin key and high-relief stress cases.

The human gate should reject the milestone if DRY FLUVIAL is visually indistinguishable from BASE,
if channels read as arbitrary narrow scratches rather than drainage landforms, if alluvial reaches
are not materially broader than confined reaches, if valley influence becomes island-wide, or if the
wet corridor visibly escapes its banks.

## Preservation boundary

AUTH-0105 does not:

- change watershed routing, Priority-Flood spill routing, channel promotion, stream order, or coherent
  component selection;
- add or delete a retained waterbody;
- move accepted naturalized channel endpoints;
- author Minecraft blocks, fluids, Y coordinates, chunks, ticks, or material palettes;
- perform iterative erosion or sediment transport;
- retune general island morphology to make one screenshot attractive.

If the new dry-landform atlas demonstrates that accepted routing itself is the remaining defect, that
must become a later explicit hydrology requirement rather than being hidden inside this realization
layer.

## Implementation handoff

Implementation should project the AUTH-0105 dry terrain field before visible channel water and before
riverbed/riparian material dressing. It should derive wet channel occupancy from the AUTH-0105
water-surface contract rather than selecting an independent fixed trench depth.

A backend may discretize the continuous field, but it must not replace the authored landform with an
unrelated local carve.
