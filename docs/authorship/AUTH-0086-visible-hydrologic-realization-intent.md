# AUTH-0086 — Visible hydrologic realization intent

## Purpose

AUTH-0086 is the concrete authorship bridge from Skyforge's accepted surface-hydrology semantics to
a downstream Minecraft-visible water realization.

The production failure mode is specific: Skyforge already authors coherent channels, retained
waterbodies, cascades, waterfalls, edge discharge, riparian corridors, and waterbody margins, but a
backend adapter has no single exact contract saying which accepted semantic objects must become
visible water.

Without that contract, implementation can accidentally substitute native fluid features, omit
accepted authored water, flatten distinct hydrologic classes into one generic water pass, or lose
the dry transition semantics that make channels and waterbodies read as part of the terrain.

AUTH-0086 closes that projection gap.

## Scope

AUTH-0086 defines backend-neutral visible-hydrology realization intent.

It does not define Minecraft block/fluid registry identities, literal block-space channel widths or
depths, exact world-space water Y values, fluid tick behavior, waterlogging, chunk scheduling,
native feature admission, or a new watershed/channel/waterbody/waterfall planner.

## Existing authorship reused

AUTH-0086 introduces no new hydrologic threshold. It consumes accepted source objects directly:

- AUTH-0018/AUTH-0019 coherent visible channel topology and downstream hydrologic realization;
- AUTH-0017 naturalized sub-grid channel centerlines;
- AUTH-0011 riparian dry-land relationships;
- AUTH-0009 connected retained-waterbody inundation footprints;
- AUTH-0010 retained-waterbody dry margins;
- AUTH-0013 discrete CASCADE_STEP, WATERFALL, and EDGE_FALL events.

The projection retains those exact objects rather than re-deriving equivalent values.

## Realization classes

SkyIslandVisibleHydrologicRealizationKind contains five downstream-facing semantic classes:

- CHANNEL_WATER;
- RETAINED_WATER;
- CASCADE;
- WATERFALL;
- EDGE_DISCHARGE.

The mapping is structural: every accepted naturalized channel path becomes CHANNEL_WATER, every
accepted retained-waterbody footprint becomes RETAINED_WATER, and accepted drop kinds map directly
CASCADE_STEP -> CASCADE, WATERFALL -> WATERFALL, EDGE_FALL -> EDGE_DISCHARGE.

No new score or threshold chooses these classes.

## Exact channel provenance

SkyIslandVisibleChannelWaterIntent retains the exact accepted SkyIslandNaturalizedChannelPath and
the exact accepted riparian cells associated with that source segment. The source path already
retains channel hierarchy role, stream order, relative discharge, hydraulic profile, width/depth
potentials, incision potential, and naturalized sub-grid centerline.

A riparian cell carried by a channel intent must reference the exact same source/downstream channel
cell identity.

## Exact retained-water provenance

SkyIslandVisibleRetainedWaterIntent retains the exact SkyIslandWaterbodyFootprint and exact
SkyIslandWaterbodyMargin associated with it. The footprint retains source candidates, connected
inundation cells, water-surface/depth potentials, shoreline flags, and spill-surface potential.

The margin remains dry semantic terrain. AUTH-0086 does not convert it into water.

## Exact drop provenance

SkyIslandVisibleDropWaterIntent retains the exact accepted SkyIslandChannelDrop. Its visible class
is a direct enum mapping from the accepted source drop kind.

EDGE_FALL becomes EDGE_DISCHARGE while retaining downstreamCellIndex = -1, so the backend can treat
the event as water leaving the island domain rather than inventing an interior target.

AUTH-0086 does not calculate literal falling-fluid geometry.

## One-for-one projection invariant

SkyIslandVisibleHydrologicRealizationPlan is deliberately strict. A valid plan must contain, in
accepted deterministic order:

- exactly one channel intent for every coherent naturalized channel path;
- exactly one retained-water intent for every retained-waterbody footprint and corresponding margin;
- exactly one drop intent for every coherent channel-drop event.

The plan rejects missing accepted sources, reordered/substituted source objects, cross-island source
composition, waterbody footprint/margin mismatch, and channel/riparian identity mismatch.

A downstream implementation may choose how to realize an intent, but it cannot silently change what
Skyforge authored.

## Concrete downstream integration

The named consumer is a future Minecraft/NeoForge authored-water realization stage. It should:

1. obtain one exact SkyIslandVisibleHydrologicRealizationPlan;
2. map island-local positions and normalized hydraulic potentials through the accepted physical
   terrain realization transform;
3. rasterize CHANNEL_WATER from retained naturalized centerline/profile semantics;
4. rasterize RETAINED_WATER from connected footprint/water-depth semantics;
5. realize CASCADE, WATERFALL, and EDGE_DISCHARGE from the retained exact drop event;
6. preserve associated dry riparian and waterbody-margin semantics nearby;
7. keep native cave-spring admission governed separately by AUTH-0085.

AUTH-0086 intentionally does not put Minecraft types in skyforge-world.

## Relationship to AUTH-0085

AUTH-0085 asks whether a backend-native subsurface spring may exist at all.

AUTH-0086 asks which already-authored surface-hydrology objects must become player-visible water.

A native cave spring is supplemental subsurface hydrology. It is never a substitute for a channel,
retained waterbody, waterfall, or edge discharge authored by the surface hydrology stack.

## Acceptance gate

Reject AUTH-0086 if it adds a new hydrologic threshold, recomputes source semantics, permits an
accepted source to disappear or be substituted, allows unrelated riparian or margin provenance,
combines different island descriptors, introduces Minecraft/NeoForge classes into skyforge-world,
or performs placement, scheduling, fluid updates, storage, or I/O.

## Evidence target

The compact proof corpus should show exact coherent naturalized channels projected as visible
channel-water intents; retained waterbody footprints with dry margins preserved; accepted
cascade/waterfall/edge-fall events mapped without new thresholds; one-for-one source/intent counts;
a provenance table of exact source cell identities; and an explicit no-Minecraft/no-placement
boundary.

This is an architecture/provenance proof atlas, not a final aesthetic Minecraft screenshot.

## Next boundary

After AUTH-0086, the highest-value work becomes implementation-facing: define the smallest adapter
contract that maps these intents into physical terrain/block space, preserve exact
ownership/provenance through rasterization, then expose a live Minecraft proof where authored
channels, standing water, and edge discharge are visibly distinguishable from incidental native
fluid.
