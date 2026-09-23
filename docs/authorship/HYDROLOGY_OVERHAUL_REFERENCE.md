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


## H1 result — terrain-scored naturalization

H1 passed the focused AUTH-0105 machine contract but did not materially repair the drainage
landform. On the fixed reference diagnostics after replacing hash-selected bends:

- primary key 287: uphill-step fraction `0.344697`, ridge-crossing fraction `0.471861`;
- confluence control key 649: uphill-step fraction `0.445946`, ridge-crossing fraction `0.374517`;
- mean valley-floor advantage remained approximately zero/slightly negative.

The regenerated evidence atlas remained visually close to the pre-overhaul trench network.
Therefore bend-weight tuning is not the next action.

## H2 diagnosis — Priority-Flood ancestry is not flow direction

The accepted watershed implementation used the Priority-Flood discovery parent directly as each
cell's downstream neighbor. That conflates two different operations.

Priority-Flood is retained to establish depression fill/spill levels and prove outlet connectivity.
Its discovery tree is not a physical flow-direction model; on filled flats it is substantially a
queue traversal order. H2 therefore derives downstream edges in a separate pass:

1. consider only already outlet-connected neighbors whose flood rank guarantees an acyclic route;
2. prefer raw-terrain descent;
3. if raw descent is impossible inside a filled depression, prefer filled-surface descent;
4. use flood rank only as the final flat-resolution tie-break.

This is still a coarse 49x49 / eight-neighbor drainage graph. It is an architectural correction, not
the final fine-corridor solution. If H2 materially improves the reference metrics but leaves visible
grid/corridor artifacts, the next step is finer terrain-aware routing between semantic
junctions/basins/outlets rather than additional coarse-graph cosmetic smoothing.


## H2b macro-reach experiment

The local H2 corridor search demonstrated that treating every 49x49 watershed cell as a mandatory
visible-river vertex is too restrictive. H2b therefore partitions retained channel graphs into
macro-reaches:

- headwaters are hard geometric controls;
- confluences are hard geometric controls;
- terminals/outlets are hard geometric controls;
- degree-two intermediate watershed cells remain semantic/profile boundaries but may slide
  laterally inside the fine terrain-aware corridor.

This does not alter catchment topology, stream hierarchy, discharge provenance, or accepted
profile ownership. It changes only the physical centerline realization. Adjacent profile slices
share the same routed geometric boundary so downstream fluvial terrain can continue to consume one
path per semantic profile without reintroducing coarse-grid kinks.

The H2b acceptance question is quantitative before it is visual: route-step uphill fraction and
ridge-crossing fraction should materially improve relative to the H1 baseline, while the new
endpoint-uphill diagnostic identifies residual climbs that are inherent to coarse watershed
control points rather than the fine corridor solver.
