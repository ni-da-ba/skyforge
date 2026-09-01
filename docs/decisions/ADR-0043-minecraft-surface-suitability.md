# ADR-0043 — Minecraft-Owned Supplemental Surface Suitability

**Status:** Accepted

## Context

SF-IMP-0038 proved that Minecraft configured features can consume Skyforge supplemental positions below the single-valued vanilla top heightmap. That milestone deliberately answered only **reachability**: which lower exposed positions exist and can be passed through a `PlacementModifier`.

The real-client proof also exposed why reachability cannot be treated as suitability. A permissive lower-surface index can include dry native ground, lower island tops, narrow carved shelves and cavity floors. Conversely, a submerged seabed is a meaningful surface for aquatic features but is intentionally not a dry-land target.

Skyforge must not respond by inventing its own parallel biome/climate model prematurely. Minecraft already owns blocks, fluids, biomes, configured features and their survival rules. The missing adapter responsibility for this milestone is a small, local classification of the physical placement environment.

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

## Acceptance evidence

SF-IMP-0039 was accepted on 2026-08-31 after all automated and manual gates passed.

Automated evidence:

- `scripts\verify-sf-imp-0039-surface-suitability.bat` passed;
- repository-wide `gradlew.bat check` passed;
- tests proved `dry_open` acceptance, low-ceiling rejection, thin-support rejection and submerged-water-floor classification;
- accepted SF-IMP-0036 through SF-IMP-0038 integration regressions remained green;
- development-only suitability fixtures remained excluded from the production JAR.

Real-client evidence used an ocean seed. Across the origin-area chunks the diagnostic consistently reported:

```text
dryLand=0
dryOpen=0
submergedWaterFloor=256
dryOpenQueries=14
dryOpenEmitted=0
submergedQueries=4
submergedEmitted=4
```

This proved that dry probes could execute yet correctly emit nothing when no dry class existed, while the submerged selector exposed and emitted water-floor targets. Sparse lapis markers were visibly realized on the seabed below the Massif. No corresponding dry markers appeared underwater.

The same world then saved, closed and reloaded cleanly. The Massif and markers persisted, with no placement-modifier codec/registry error, chunk corruption or obvious duplicate generation.

The observed occasional submerged-cave marker remains within the accepted `submerged_water_floor` definition. SF-IMP-0039 does not claim an open-ocean-versus-flooded-cavity distinction.
