# AUTH-0085 — Native spring semantic admission

## Purpose

AUTH-0085 is a concrete world-semantics bridge from accepted Skyforge geology/cave authorship to the
Minecraft native `FLUID_SPRINGS` integration accepted by SF-IMP-0063.

The production failure mode is explicit:

> native Minecraft spring placement can currently create water or lava inside an exact Skyforge
> volume without any authorship-level reason that the island contains a spring at that location.

The technically correct SF-IMP-0063 containment system prevents generated fluid from escaping its
owner volume. It does **not** establish that the fluid belongs there semantically.

AUTH-0085 closes that policy gap for native springs.

## Scope

AUTH-0085 applies only to candidate **subsurface native spring placement**.

It does not define:

- Minecraft feature keys;
- block/fluid registry identities;
- native `LAKES` admission;
- literal channel blocks;
- retained-waterbody filling;
- waterfall block realization;
- fluid propagation/fencing;
- geothermal generation;
- new groundwater simulation.

Those remain separate concerns.

## Existing authorship reused

AUTH-0085 introduces no new groundwater threshold.

It consumes:

- AUTH-0022 continuous geology;
- AUTH-0023 connected geological regions;
- the accepted `AQUIFER_BODY` classification;
- AUTH-0030 exterior-connected authored cave volume.

The accepted AUTH-0023 planner already decides which coarse geological cells form aquifer bodies
using groundwater, connected permeability, and deterministic hydrogeological lenses.

AUTH-0085 therefore asks only:

1. is the candidate position owned by the authored island geology?
2. is the candidate inside authored cave volume?
3. for water, does the nearest accepted geological grid cell belong to an `AQUIFER_BODY`?
4. for molten fluid, does Skyforge currently possess geothermal/volcanic authorship semantics?

## Fluid classes

`SkyIslandNativeSpringFluidKind` contains two backend-neutral material classes:

- `WATER`;
- `MOLTEN`.

The Minecraft adapter may map concrete backend fluids to these classes.

For the current vanilla integration, the obvious downstream mapping is:

- `minecraft:spring_water` -> `WATER`;
- `minecraft:spring_lava` -> `MOLTEN`.

That mapping does not belong in `skyforge-world`.

## Admission statuses

`SkyIslandNativeSpringAdmissionStatus` is explicit and fail-closed:

- `ADMITTED_AQUIFER_CAVE_WATER`;
- `OUTSIDE_AUTHORED_ISLAND`;
- `NOT_AUTHORED_CAVE_INTERIOR`;
- `NO_AQUIFER_SUPPORT`;
- `MISSING_GEOTHERMAL_SEMANTICS`.

Only `ADMITTED_AQUIFER_CAVE_WATER` is currently admitted.

## Water rule

Native water spring placement is semantically admissible only when:

- AUTH-0022 geology owns the candidate position;
- AUTH-0030 says the exact semantic position is inside authored cave volume;
- the nearest accepted AUTH-0023 geological grid cell belongs to an accepted `AQUIFER_BODY`
  region.

The aquifer decision is **not recomputed** with a new threshold.

AUTH-0085 consumes the discrete accepted region plan exactly.

## Molten-fluid rule

Current Skyforge geology has:

- fracture corridors;
- aquifer bodies;
- void-prone domains;
- lithologic/material semantics.

It does not currently contain a geothermal/volcanic semantic system.

Therefore native molten spring placement is not semantically admissible merely because a backend
selected a lava spring feature.

An owned authored cave position still returns:

`MISSING_GEOTHERMAL_SEMANTICS`.

This is intentional fail-closed behavior, not a claim that Skyforge can never contain volcanic or
geothermal islands.

A later geothermal/volcanic authorship ticket may open that capability if world design requires it.

## Exact provenance

`SkyIslandNativeSpringAdmission` records:

- exact semantic candidate position;
- backend-neutral fluid class;
- admission status;
- AUTH-0030 cave source kind;
- cave system ID when present;
- AUTH-0023 aquifer region ID when present;
- exact aquifer grid-cell index when present;
- accepted aquifer membership when present.

An admitted result cannot be constructed without both cave and aquifer evidence.

## Evaluation order

`SkyIslandNativeSpringAdmissionPolicy.evaluate(...)` is deliberately ordered:

1. validate geology/cave descriptor identity;
2. reject positions outside authored island geology;
3. reject positions outside authored cave volume;
4. reject molten candidates because geothermal semantics do not exist;
5. map water to the nearest accepted geological grid cell;
6. reject water without accepted aquifer support;
7. admit aquifer-supported authored cave water.

This produces explainable rejection reasons without backend guesses.

## Concrete downstream integration

The named consumer is the Minecraft/NeoForge `FLUID_SPRINGS` population path accepted in
SF-IMP-0063.

A downstream implementation should:

1. receive the native spring candidate in owner-local/physical coordinates;
2. map its physical Y into existing semantic subsurface depth using the accepted realization
   transform;
3. classify the backend spring fluid as `WATER` or `MOLTEN`;
4. evaluate AUTH-0085;
5. execute the native placed feature only if the semantic decision is admitted;
6. retain the existing SF-IMP-0063 exact-volume propagation provenance/fence for any admitted fluid.

AUTH-0085 does not alter asynchronous fluid containment.

It determines whether the native spring should be semantically admitted in the first place.

## Relationship to visible authored hydrology

AUTH-0085 is not the final Minecraft hydrology bridge.

Existing authorship already distinguishes:

- visible channel water;
- retained waterbodies;
- cascades;
- waterfalls;
- edge discharge;
- riparian geography.

Those should receive explicit player-visible realization separately.

Native cave springs are supplemental geological/hydrological features, not substitutes for the
AUTH-0019 coherent surface hydrology stack.

## Acceptance gate

Reject AUTH-0085 if:

- it invents a second groundwater/aquifer threshold;
- water can be admitted outside authored cave volume;
- water can be admitted without an accepted AUTH-0023 aquifer cell;
- molten fluid is admitted without geothermal/volcanic authorship;
- geology and cave semantics from different islands can be combined;
- an admitted decision can omit cave or aquifer provenance;
- Minecraft/NeoForge classes enter `skyforge-world`;
- the policy performs block placement, storage, fluid propagation, or registry lookup.

## Evidence

The AUTH-0085 proof corpus should demonstrate:

- actual representative aquifer/cave water admission;
- the same site rejecting molten fluid;
- owned non-cave water rejection;
- outside-island rejection;
- exact cave/aquifer provenance;
- backend-neutral policy surface.

## Next hydrology boundary

After native spring admission, the highest-value authorship bridge is **visible hydrologic
realization intent** for:

- naturalized channels;
- retained waterbodies;
- cascades/waterfalls;
- edge discharge;
- riparian relationships.

That work should be driven by concrete Minecraft realization requirements rather than by extending
hydrology ontology for its own sake.
