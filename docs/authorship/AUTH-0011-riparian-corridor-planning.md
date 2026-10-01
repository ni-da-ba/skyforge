# AUTH-0011 — Semantic Riparian Corridor Planning

AUTH-0011 adds dry-land riparian transition semantics around the accepted AUTH-0007 routed channel network.

## Dependency

```text
semantic geography
    -> local ecology / hydrology
    -> watershed topology
    -> selected channel network + hierarchy
    -> standing-water footprints and margins
    -> riparian corridor planning
    -> later ecology / terrain / backend realization
```

## Purpose

A routed channel centerline is not itself a complete river environment. Later realization needs a semantic signal describing the dry land immediately influenced by flowing water: wetter vegetation, flood-prone ground, softer terrain transitions, and other riparian effects.

AUTH-0011 provides that signal without choosing blocks, Minecraft biome IDs, literal bank widths, or carved river geometry.

## Planning policy

The planner evaluates active watershed cells one or two coarse lattice steps from accepted channel segments.

Channel influence combines:

- AUTH-0007 corridor scale;
- relative discharge;
- normalized stream order.

Local riparian suitability additionally samples:

- AUTH-0003 ecological saturation potential;
- AUTH-0004 hydrological retention potential;
- distance from the channel centerline.

Cells below the semantic corridor threshold remain ordinary authored terrain.

Accepted cells are classified as:

- `RIPARIAN_TRANSITION` — channel-adjacent dry land with meaningful flowing-water influence;
- `SATURATED_RIPARIAN` — stronger locally wet/retentive riparian ground.

Major/high-influence segments may support a two-cell coarse corridor. Smaller segments remain limited to the immediate adjacent planning ring.

## Ownership precedence

AUTH-0011 does not compete with standing-water semantics.

The following cells are reserved before channel-corridor planning:

1. AUTH-0009 retained-waterbody footprint cells;
2. AUTH-0010 waterbody-margin cells;
3. accepted channel centerline cells themselves.

A remaining dry cell may be claimed by at most one channel segment. Competing claims resolve by stronger riparian potential, then stronger channel influence, then deterministic channel-cell identity.

This produces a stable semantic precedence:

```text
standing water
    > standing-water margin
    > routed channel centerline
    > riparian dry-land transition
    > ordinary authored terrain
```

## Output semantics

`SkyIslandRiparianCell` records:

- watershed-cell identity and island-local position;
- riparian kind;
- owning channel source/downstream cell identities;
- channel role and stream order;
- coarse channel distance;
- channel influence;
- saturation and retention potentials;
- final riparian potential.

`SkyIslandRiparianCorridorPlan` contains the unique accepted dry riparian cells for one island.

## Evidence

The deterministic `authorship-riparian-corridors-v1` corpus reuses the six AUTH-0007 representative islands: keys 77, 118, 241, 512, 811, and 83.

The atlas renders:

- accepted standing water in pale blue;
- AUTH-0010 shoreline margins in pale teal;
- `RIPARIAN_TRANSITION` cells in gold;
- `SATURATED_RIPARIAN` cells in green;
- accepted channel centerlines in gray.

`manifest.csv` summarizes corridor counts and distances. `cells.csv` records per-cell channel provenance and semantic scores.

The visual gate should reject the milestone if corridors become island-scale blankets, detach from routed channels, overwrite standing-water semantics, or collapse into an indiscriminate fixed-width buffer that ignores channel hierarchy.

## Deferred

- literal river-channel width/depth;
- terrain carving and bank geometry;
- floodplain geomorphology;
- meanders and sub-grid channel naturalization;
- erosion/deposition feedback;
- Minecraft biome IDs and vegetation placement;
- seasonal flooding;
- block/fluid realization;
- irregular island-domain naturalization.
