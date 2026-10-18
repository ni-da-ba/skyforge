# Structural and Construction Engineering Stack v0.1

**Snapshot:** 2026-09-29  
**Status:** Provisional strong selection / integration contract. Runtime physics, performance, exact recipes, and final content scope remain acceptance work.

## Decision

Skyforge should use one canonical structural-shaping vocabulary for vehicles and machinery, with optional decorative/content layers around it.

~~~text
COPYCATS+
  -> canonical shaped structural geometry

COPYCATS+ AERONAUTICS WEIGHT
  -> Sable mass/physics compatibility bridge

COPYCATS+ ADDITIONS
  -> provisional compound/corner geometry extension

CREATE: INTERIORS
  -> functional vehicle/interior furnishing

BELLS & WHISTLES
  -> transportation architecture/detailing

DESIGN N' DECOR
  -> provisional industrial architectural content

FRAMEDBLOCKS
  -> reserve / experimental only
~~~

The structural stack should improve geometry and usability without creating a parallel material economy.

## Selected stack

| Capability | Preferred system | Disposition |
|---|---|---|
| Canonical structural shaping | Copycats+ | Strong keep |
| Sable mass/physics bridge | Copycats+ Aeronautics Weight | Strong keep / compatibility layer |
| Additional corner/compound geometry | Copycats+ Additions | Strong provisional |
| Functional interior seating/furnishing | Create: Interiors | Likely keep |
| Transportation detail vocabulary | Bells & Whistles | Likely keep |
| Industrial architectural detailing | Design n' Decor | Provisional content keep |
| Freeform arbitrary/deformable geometry | FramedBlocks | Reserve / experimental |
| Misc. Create utility | Create: Connected | Deferred compatibility audit |

## Structural doctrine

Structural addons should provide:

- geometry;
- collision shape;
- human-scale functionality;
- transport/industrial architectural vocabulary.

They should not define new structural-material progression merely because they expose new shapes.

Retained material authority remains with the common Skyforge material graph.

Examples:

~~~text
WOOD
IRON
COPPER
BRASS
STEEL
NETHERSTEEL
selected vanilla / world materials
~~~

A shaped or copied block should remain an expression of an existing material, not become a new material tier.

## Copycats+ role

Copycats+ is the canonical shaped-block system because it:

- is Create-native;
- covers broad structural geometry;
- already participates in the current Create/Sable ecosystem;
- is a required dependency of CBC Terminal Ballistics;
- provides a coherent base for vehicle hulls, armor geometry, interiors, machinery housings, ducts, fairings, and compact construction.

Target uses include:

- aircraft fairings;
- hull/chassis shaping;
- armor slopes;
- thin internal partitions;
- catwalks;
- machinery casings;
- ducts/pipes;
- compact vehicle interiors;
- industrial architecture.

## Physics bridge

Copycats+ Aeronautics Weight is retained as the preferred Sable compatibility layer for shaped blocks.

Required behavior:

- sensible mass values;
- occupancy-sensitive mass where supported;
- stable Sable assembly;
- correct center-of-mass contribution;
- persistence across save/reload and assembly/disassembly.

A later compatibility pass must determine whether copied appearance/material can also influence physical mass, rather than only shape occupancy.

## Copycats+ Additions

Retain provisionally for geometries missing from base Copycats+, especially:

- corner slopes;
- compound slopes;
- layered corner shapes;
- related vehicle/hull transitions.

Acceptance requires the added shapes to inherit or receive correct Sable mass/collision behavior and not create disproportionate performance cost.

## Interiors / transport detailing

Create: Interiors is favored for functional seating and human-scale interior furnishing, especially on contraptions and vehicles.

Bells & Whistles is favored as a low-risk transportation/detail layer for:

- access steps;
- grab bars;
- headlights;
- windows;
- station/platform pieces;
- carriage/vehicle details.

These do not receive progression authority.

## Design n' Decor

Design n' Decor remains a provisional content layer for industrial architecture such as:

- sheet-metal vocabulary;
- catwalks;
- industrial lighting;
- containers;
- factory-oriented blocks.

Retain only where it meaningfully expands industrial construction rather than duplicating shapes already handled by Copycats+.

## FramedBlocks boundary

FramedBlocks is not part of the canonical moving-vehicle construction language at this stage.

Reason:

- it overlaps heavily with Copycats+;
- its strongest feature is arbitrary/deformable geometry;
- equivalent dedicated Sable physics/mass integration is not currently established;
- dynamic/deformable collision creates a higher compatibility burden on moving sublevels.

FramedBlocks may be reconsidered as:

- an advanced/freeform static-construction tool;
- a specialist vehicle-shaping tool if runtime acceptance succeeds.

It should not be required for Skyforge progression or canonical vehicle designs.

## Structural physics doctrine

Where technically feasible:

~~~text
GEOMETRY OCCUPANCY
    x
MATERIAL CLASS
    ->
MASS / INERTIA CONTRIBUTION
~~~

Terminal Ballistics may separately own armor penetration/resistance semantics.

Do not assume visual camouflage alone should automatically define physics without an explicit compatibility path.

## Deferred compatibility work

### STRUCT-COMP-1 — Copycats+ Sable regression
Test:
- slopes;
- vertical steps;
- slabs;
- stacked layers;
- catwalks;
- panes/boards;
- compound shapes.

Exercise:
- assemble;
- move;
- collide;
- save/reload;
- disassemble;
- schematic reproduction;
- multiplayer sync.

### STRUCT-COMP-2 — material/mass semantics
Measure representative copied materials and determine whether:
- shape occupancy affects mass correctly;
- represented material affects mass;
- special handling is needed for structural/armor materials.

### STRUCT-COMP-3 — Terminal Ballistics
Test:
- Copycat armor;
- compound/sloped armor;
- Sable moving craft;
- CBC / Neo Warfare projectiles;
- spalling / penetration;
- save/reload.

### STRUCT-COMP-4 — FramedBlocks reserve test
Only if retained later, verify:
- arbitrary collapsible geometry;
- collision fidelity;
- Sable assembly;
- mass/COM;
- save/reload;
- multiplayer;
- ballistic interactions;
- performance.

### STRUCT-COMP-5 — performance
Benchmark:
- large Copycats-heavy aircraft;
- industrial structures;
- compound-shape hulls;
- multiple moving craft;
- client rendering and server physics cost.

## Acceptance boundary

This document establishes the preferred structural/construction architecture.

Production lock still requires:
1. exact dependency/version/license closure;
2. Copycats+ / Sable regression proof;
3. Copycats+ Additions compatibility proof;
4. representative Terminal Ballistics proof;
5. vehicle-scale performance characterization;
6. final recipe/content curation;
7. human visual/buildability review.

## Design principle

> Skyforge structural systems should expand the shapes players can engineer with existing materials. Geometry is a construction vocabulary, not a parallel progression tree.
