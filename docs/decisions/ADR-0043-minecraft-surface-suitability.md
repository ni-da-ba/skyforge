# ADR-0043 — Minecraft-Owned Supplemental Surface Suitability

**Status:** Proposed

## Context

SF-IMP-0038 proved that Minecraft configured features can consume Skyforge supplemental positions below the single-valued vanilla top heightmap. That milestone deliberately answered only **reachability**: which lower exposed positions exist and can be passed through a `PlacementModifier`.

The real-client proof also exposed why reachability cannot be treated as suitability. A permissive lower-surface index can include dry native ground, lower island tops, narrow carved shelves and cavity floors. Conversely, a submerged seabed is a meaningful surface for aquatic features but is intentionally not a dry-land target.

Skyforge must not respond by inventing its own parallel biome/climate model. Minecraft already owns blocks, fluids, biomes, configured features and their survival rules. The missing adapter responsibility is a small, local classification of the physical placement environment.

## Decision

SF-IMP-0039 preserves `skyforge:additional_surfaces` exactly as the accepted SF-IMP-0038 dry-land reachability primitive and adds a separate registered placement modifier:

```text
skyforge:suitable_surfaces
```

The modifier carries one Minecraft-owned `suitability` parameter and filters the already discovered lower surfaces accordingly.

The first supported suitability values are:

### `dry_land`

A non-fluid solid support block with air at the placement position immediately above it.

This is the accepted SF-IMP-0038 land-surface behavior and remains available for exact regression compatibility.

### `dry_open`

A strict subset of `dry_land` intended for ordinary exposed vegetation probes. The first engineering rule requires:

- at least 8 vertically contiguous air blocks beginning at the placement position; and
- at least 3 vertically contiguous non-fluid support blocks beneath the placement position.

These thresholds are deliberately local and monotonic. They reject obvious low-ceiling cavity floors and very thin carved shelves without requiring sky visibility, so preserved ground beneath a high floating island can still qualify.

They are not claimed to be final tree/structure suitability rules.

### `submerged_water_floor`

A non-fluid solid support block with a vanilla Minecraft water block immediately above it.

This creates an independently addressable underwater placement class without pretending that dry vegetation and aquatic vegetation share the same physical requirements.

## Architectural boundary

Suitability is owned by the Minecraft/NeoForge adapter because it depends on concrete Minecraft block and fluid state.

Backend-neutral Skyforge modules continue to own geometry and structural meaning only. They gain no concepts for:

- Minecraft water;
- rainfall or temperature;
- biome tags;
- tree/flower/kelp identities;
- vanilla/modded configured features.

The adapter may combine live Minecraft state with accepted Skyforge occupancy to decide whether a lower position is suitable for a particular physical placement class.

### Future biome-field ownership

The adapter-owned environmental boundary in this ADR is a **current integration strategy**, not a permanent claim that Skyforge can never own biome-scale procedural fields.

A later Skyforge milestone may legitimately introduce backend-neutral environmental or biome fields when they are required to express world-composition intent that exists independently of Minecraft. If that happens, the boundary should remain layered rather than duplicated:

```text
Skyforge biome/environment field intent
        -> backend adaptation / mapping
        -> Minecraft biome registry + block/fluid state
        -> Minecraft feature/survival behavior
```

SF-IMP-0039 intentionally does not design that future field system. Its purpose is to solve the concrete physical suitability problem using information Minecraft already supplies, while preserving room for Skyforge to become authoritative over higher-level biome distribution later.

## Separation from reachability

The distinction is explicit:

```text
Skyforge/Minecraft geometry
        -> additional lower surfaces       (reachability, SF-IMP-0038)
        -> suitability classification      (SF-IMP-0039)
        -> placed/configured feature logic (Minecraft)
```

A position may be reachable but not `dry_open`. A submerged floor may be suitable for an aquatic feature while being absent from the accepted `dry_land` view.

## Development proof

The SF-IMP-0039 ModDev resources use three development-only probes:

1. the existing vanilla `minecraft:patch_grass` copy now requests `dry_open`;
2. a sparse emerald `minecraft:simple_block` marker requests `dry_open`;
3. a sparse lapis `minecraft:simple_block` marker requests `submerged_water_floor`.

The colored markers are diagnostic fixtures only. They are intentionally sparse and are excluded from the production JAR.

This makes either a land or ocean Minecraft seed useful:

- dry lower terrain can demonstrate `dry_open` selection;
- an ocean floor can demonstrate `submerged_water_floor` selection.

## Invariants

1. SF-IMP-0038 `skyforge:additional_surfaces` dry-land behavior remains available and regression-tested.
2. Suitability remains entirely inside the Minecraft adapter for this milestone.
3. The highest vanilla-owned surface remains excluded from the supplemental index.
4. Carved-away Skyforge surfaces are not resurrected as placement targets.
5. `dry_open` is a strict subset of `dry_land`.
6. `dry_open` does not require direct sky visibility and therefore can include ground beneath a high floating island.
7. `submerged_water_floor` is not returned by the dry-land view.
8. Ordinary columns without Skyforge remain owned by vanilla and are not supplemented.
9. No entire biome-decoration step is replayed.
10. Development marker/configuration resources remain absent from distributable production artifacts.

## Explicit non-goals

SF-IMP-0039 does not yet define final suitability for:

- trees or large structures;
- flowers versus grass density;
- snow and ice;
- flowing water or non-water modded fluids;
- kelp/seagrass survival policy;
- steep-slope or horizontal-footprint metrics;
- soil/fertility categories;
- arbitrary modded feature compatibility;
- a backend-neutral Skyforge biome/environment field system.

Those can be added only when a concrete feature family or world-composition requirement demonstrates the need.

## Acceptance criteria

ADR-0043 becomes Accepted only after:

1. `skyforge:suitable_surfaces` is registered and codec-backed under NeoForge 1.21.1;
2. the existing `skyforge:additional_surfaces` registry/behavior remains green;
3. automated tests prove `dry_open` accepts sufficiently thick surfaces with adequate headroom;
4. automated tests prove `dry_open` rejects a low ceiling while `dry_land` remains reachable;
5. automated tests prove `dry_open` rejects a thin carved shelf while `dry_land` remains reachable;
6. automated tests prove a submerged water floor is addressable through `submerged_water_floor` but not through `dry_land`;
7. accepted post-surface, native-material and multi-surface regressions remain green;
8. backend-neutral independence and repository-wide `check` pass;
9. a real ModDev client loads the SF-IMP-0039 development world and visibly realizes at least one suitability-specific diagnostic when the corresponding candidate class is present;
10. no systematic marker placement occurs on the vanilla highest surface; and
11. save/reload remains clean.
