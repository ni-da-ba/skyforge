# AUTH-0099 — Cave-System Site Capability Evidence

## Purpose

Bootstrap Province #224 requires at least one cave/resource-learning opportunity in the early
starting region. The bootstrap recipe also identifies cave / mine-capable geology as a neutral
CAPABILITY_SITE concept.

Skyforge already authors rich cave causes:

- AUTH-0024 cave topology;
- AUTH-0025 cave geometry;
- AUTH-0028 sparse exterior-exposure intent;
- AUTH-0022 geology;
- AUTH-0033 host-material families.

Those causes are currently exposed as separate APIs. A downstream Bootstrap/structure planner would
need to understand all of them to ask a simple question such as:

> What cave/access/geologic evidence does each authored cave system provide?

AUTH-0099 adds that Stage-B evidence composition.

It does **not** decide whether a cave is useful, mine-capable, a dungeon, a progression site, or a
valid concrete Minecraft structure host.

## Exact source chain

AUTH-0099 consumes one authored `SkyIslandDescriptor` and internally reuses the existing accepted
producers:

```text
AUTH-0024 topology
    -> AUTH-0025 geometry
        -> AUTH-0028 sparse exterior exposure

AUTH-0022 geology
AUTH-0033 host-material plan
```

The profile retains the exact source objects.

No second cave topology, chamber geometry, exposure threshold, geology field, material-family grid,
or cave entrance policy is created.

## One output per cave system

Every exact AUTH-0024 cave system receives one
`SkyIslandCaveSiteCapabilitySystem` in canonical source-system order.

Cave-free descriptors remain cave-free. AUTH-0099 may not create synthetic systems merely because a
downstream bootstrap recipe wants a cave.

## Node evidence

Each exact source cave node retains:

- the exact `SkyIslandCaveNode`;
- the exact AUTH-0022 geology sample at that node;
- the nearest active AUTH-0033 host-planning cell;
- normalized 3D planning distance to that host cell.

### Why nearest host rather than material at the cave center?

AUTH-0031 correctly reports authored void inside exterior-connected cave volume. A cave center is
therefore not automatically solid host rock.

AUTH-0099 does not pretend otherwise.

Instead, nearest AUTH-0033 host evidence answers the coarser question:

> What accepted host-material context lies closest to this cave node on the existing planning
> lattice?

The nearest host relationship is deterministic in normalized x/z/depth distance. Exact distance ties
use lower canonical AUTH-0033 cell index.

This is planning evidence, not literal wall-block distance or proof that a resource deposit intersects
the cave.

## Per-system summaries

The system profile exposes raw descriptive summaries of exact source evidence:

### Topology / geometry

- node count;
- link count;
- chamber count;
- passage count;
- minimum / mean / maximum semantic node depth;
- maximum node chamber potential;
- mean / maximum chamber horizontal radius divided by nominal island radius;
- maximum semantic chamber depth radius.

### Hydrology / geology

From exact cave nodes:

- mean groundwater potential;
- mean / peak fracture intensity;
- mean / peak void-formation potential;
- mean / peak connected permeability.

### Nearby host context

From each node's nearest exact AUTH-0033 host cell:

- mean / peak mineral-bearing structural-host support;
- mean / maximum normalized host distance.

These values are intentionally not converted to classes such as dry cave, wet cave, rich mine, large
cavern, or deep dungeon.

## Exterior exposure

If AUTH-0028 accepted one sparse exterior-exposure intent for a cave system, AUTH-0099 retains that
exact intent including:

- source system and primitive provenance;
- exposure side;
- cave/boundary anchors;
- semantic gap;
- accepted exposure score/support components.

If AUTH-0028 kept a system sealed, AUTH-0099 retains empty exposure evidence.

No synthetic entrance is created to satisfy Bootstrap.

## Scale semantics

Cave chamber horizontal radius is physical authored extent and is normalized by nominal island
radius in the capability profile.

Node depth, chamber depth radius, geology values, host-family memberships, and nearest-host distance
already use semantic or normalized coordinates.

A radius-only descriptor scaling must preserve:

- cave-system identity/order;
- normalized cave geometry;
- nearest AUTH-0033 host-cell identity;
- normalized host distance;
- geology/host summary values.

## Ownership

### Authorship

Owns:

- exact cave-system provenance;
- cave topology/geometry/exposure meaning;
- backend-neutral geology/host relationship evidence;
- deterministic capability composition.

### Content / Experience

Owns:

- what counts as a useful cave/resource-learning site;
- cave/mineshaft/dungeon/structure-role requirements;
- thresholds/ranking/site selection;
- progression/teaching value;
- resource guarantees;
- loot/mob/service meaning.

### Implementation

Owns:

- concrete Minecraft carve output;
- exact physical entrance/clearance/occupancy;
- concrete mine/dungeon/structure identity and geometry;
- block/resource realization;
- mutation/persistence/lifecycle.

## Explicit non-contract

AUTH-0099 defines no:

- `usefulCave`;
- `mineCapable`;
- `dungeonCapable`;
- Ancient-City/Trial-Chamber/Stronghold eligibility;
- dry/wet cave class;
- resource/deposit eligibility, grade, abundance, count, or guarantee;
- structure tier;
- progression or teaching score;
- loot or mob policy;
- Minecraft carver/structure behavior.

## Evidence

Evidence identity:

```text
authorship-cave-site-capability-v1
```

Files:

- `index.html`;
- `atlas.png`;
- `manifest.csv`;
- `systems.csv`;
- `nodes.csv`.

Proof panels:

- `EXACT_CAVE_SOURCES`;
- `NEAREST_AUTH0033_HOST`;
- `EXACT_EXPOSURE`;
- `DETERMINISTIC`;
- `SCALE_COVARIANT`;
- `NO_ROLE_POLICY`.

## Acceptance

Reject AUTH-0099 if:

- exact AUTH-0024/0025/0028 source identity is lost;
- a second cave/material planning lattice is introduced;
- a cave-free source gains synthetic cave systems or exposure;
- node geology differs from exact AUTH-0022 sampling;
- nearest AUTH-0033 host evidence does not reconstruct from the source plan;
- an AUTH-0028-sealed system gains a synthetic entrance;
- radius-only scaling changes normalized capability identity;
- useful/mine/dungeon/progression/resource-guarantee policy enters Authorship.

## Downstream handoff

AUTH-0099 is sufficient for Bootstrap/Content to define a concrete cave/resource-learning
requirement without importing low-level cave planner internals.

Do not add an Authorship-owned usefulness threshold. Do not add a concrete mine/dungeon placement.
Exact physical validation remains downstream.
