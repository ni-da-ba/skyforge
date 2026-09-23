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


## H2 validation note

Historical seed-specific expectations are not hydrology contracts. After the watershed flow-direction
rewrite, regression tests assert topology/coherence invariants while the proving-ground diagnostics
select the next fixed confluence specimen from the new production behavior.


## H3 hydraulic geometry

Channel width is now authored from a monotonic discharge power law and physical bankfull width is
scaled directly from island radius rather than the watershed lattice spacing. Stream-power
potential couples discharge and gradient; rock competence and erosion maturity then control
incision. This preserves topology authority while preventing a planning-resolution change from
silently resizing rivers.


## H2.5 visible transport separation

Priority-Flood remains authoritative for catchment transport and accumulation, including transport
across filled depressions. A filled-depression ascent is no longer automatically authored as a
visible channel reach. Visible channel candidates must descend or remain level on the raw authored
terrain; downstream accumulation still includes hidden transport.

Reference evidence reports the fraction of watershed transport edges that are raw-terrain uphill.
If suppressing those edges fragments visible drainage excessively, the next authorship step is an
explicit retained-basin or subsurface-transfer fate rather than restoring uphill trench carving.

## H4 terrain-conditioned valley shaping

Fluvial geometry now measures lateral terrain confinement along each accepted fine corridor.
Unconfined alluvial reaches receive broader valley envelopes; confined/incised/cascade reaches
remain narrower and gain stronger dry-landform relief. Longitudinal bed shaping uses the local
centerline terrain plus a bounded profile-sensitive grade ceiling instead of imposing one straight
start-to-end trench grade.

Evidence now records mean confinement and the fraction of active samples that saturate the global
fluvial-lowering cap. The cap-saturation fraction is a diagnostic failure signal even when the
maximum lowering remains formally bounded.


H3 also enforces a small-channel voxel-quantization floor. This is not a return to lattice-sized
rivers: the floor exists only so an accepted headwater cannot vanish when continuous geometry is
rasterized into one-unit blocks; discharge scaling controls width above that floor.


## H4 valley morphology

Fluvial geometry now carries terrain-derived lateral asymmetry and explicit confluence expansion.
Alluvial reaches may widen asymmetrically according to surrounding relief; incised reaches use a
smaller asymmetry response and cascades remain effectively symmetric. Reaches immediately
downstream of two or more accepted incoming channels receive bounded bankfull and valley widening.
These are geometry consequences of accepted topology and terrain, not backend-invented river paths.


## H5 lifecycle ordering

Final surface representation is now downstream of composed-cave topology for the same exact chunk,
while surface population waits for completed cave topology across the whole exact volume. The cave
realizer consumes the already-authored biome resolver directly and no longer waits for vegetation or
persistent biome presentation. This prevents a later cave mutation from removing support beneath
already-placed mushrooms, grass, trees, or other surface features without globally stalling material
surfacing on unrelated chunks.


## H6 focused visual reference runtime

The hydrology development loop now has a dedicated production-stack visual harness for island key
287. Run:

`gradlew.bat :skyforge-neoforge-1211:hydrologyReferenceReview`

The task deletes and recreates `run-hydrology-reference-review-v1` before bootstrapping the world,
so topology changes cannot inherit stale chunks. Once preparation completes, use:

- `/skyforge_hydrology_ref status`
- `/skyforge_hydrology_ref info`
- `/skyforge_hydrology_ref above`
- `/skyforge_hydrology_ref approach`
- `/skyforge_hydrology_ref river`
- `/skyforge_hydrology_ref lake`
- `/skyforge_hydrology_ref outlet`
- `/skyforge_hydrology_ref below`

This is the next human-eye gate. The 100-island DR-70 atlas remains frozen and is not the tuning
loop.


The visible-network closure contract treats a raw-DEM climb as an explicit hidden-transport
boundary. That hidden interval may begin on the edge entering the first non-visible watershed cell
or on that cell's outgoing edge; visible tributaries must resolve to a trunk, terminal fate, or one
of those explicit climb boundaries rather than terminate because of selection-budget tie breaking.


Current H2 reference evidence after terrain-aware routing and downstream closure:

- primary key 287: 3.33% sub-grid uphill steps, 19.09% ridge-crossing samples, zero
  endpoint-uphill visible reaches;
- confluence control key 632: 0% sub-grid uphill steps, 34.55% ridge-crossing samples, zero
  endpoint-uphill visible reaches.

Conservative regression thresholds are now enforced around these gains. The remaining ridge-crossing
rate is diagnostic pressure for later refinement, not permission to restore the old coarse-routing
behavior.
