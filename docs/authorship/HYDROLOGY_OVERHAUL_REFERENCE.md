# Hydrology Overhaul — Reference Island Contract

Status: active proving-ground work; **not** DR-70 acceptance evidence.

## Purpose

The 100-island DR-70 atlas is a robustness gate. It is no longer the primary development loop for
hydrology. Hydrology changes must first prove themselves on a fixed, feature-rich production
specimen with explicit diagnostics, then return to the frozen atlas.

## Fixed proving ground

Primary island identity:

- world seed: `0x534B59464F524745`
- province: `8`
- cluster: `81`
- island key: **287**
- current accepted semantics at the pre-overhaul baseline: one coherent component, 33 retained
  reaches, one retained waterbody, three interior drop events, and one authored edge fall.

Temporary confluence control:

- same world/province/cluster
- island key: **649**
- current baseline: one coherent component, 37 retained reaches, stream order 2.

The second specimen exists only because the current primary island does not contain a true
confluence. It should disappear once the reference scenario itself exercises tributary joining.

## Development rule

The production stack remains authoritative. The fixture may pin island identity and later add
explicit reference-scenario semantic inputs, but it must not hand-place a Minecraft river or use a
special renderer that bypasses production hydrology.

The fast loop is:

1. generate reference diagnostics;
2. run focused world/reference tests;
3. only when semantic geometry improves, run the Minecraft reference fixture;
4. obtain human-eye acceptance;
5. freeze the accepted behavior as regression evidence;
6. return to the 100-island atlas.

Do not run the full atlas to tune individual formulas.

## Required hydrologic behaviors

The accepted reference island must eventually demonstrate:

- small headwaters;
- at least one tributary confluence;
- discharge-scaled downstream growth;
- a low-gradient alluvial reach;
- a confined/incised reach;
- a cascade or waterfall;
- retained water with a credible basin and spill path;
- riparian transition;
- one deliberate edge discharge toward the Lower Sea.

Surface drainage fate is an authored concept. Edge discharge is valid, but not every drainage path
must expose an off-island outlet. Retained and eventual subsurface-loss fates remain valid.

## Geometry acceptance

The channel network must be legible with water hidden. A route must not be made plausible merely by
excavating an arbitrary trench through otherwise incompatible terrain.

Reference diagnostics therefore report, at minimum:

- fraction of sub-grid route steps that climb the pre-channel terrain;
- fraction of interior centerline samples that sit above their lateral neighborhood (ridge crossing);
- mean lateral valley-floor advantage;
- network hierarchy/confluence counts;
- retained-water and drop-event counts;
- path-length ratios.

These metrics diagnose failures; they are not by themselves visual acceptance.

## First overhaul increment

The old naturalization stage chose lateral bend sign and amplitude from a deterministic seed hash.
That produces reproducible curvature but no geomorphic justification.

The first replacement selects from bounded candidate curves using pre-channel authored terrain:
downstream climbs and ridge crossings are penalized, local valley-floor alignment is rewarded, and
exterior escape is penalized. This is intentionally a low-cost first increment. It does **not**
declare the 49x49 watershed routing solved; the next milestone evaluates whether coarse graph
routing itself must be replaced by a finer terrain-aware drainage corridor solver.
