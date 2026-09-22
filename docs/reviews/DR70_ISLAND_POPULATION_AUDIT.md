# DR-70 Island Population Audit

Purpose: measure island-level authorship prevalence without Minecraft realization or seed hunting, then freeze a predetermined human-review corpus.

Corpus: world seed 0x534B59464F524745, province 8, cluster 81, island keys 0..4095. Metrics use accepted semantic planners only.

Results:
- coherent channel: 4096/4096 (100.00%)
- 20+ retained reaches: 2413/4096 (58.91%)
- retained waterbody: 23/4096 (0.56%)
- cave-bearing: 1700/4096 (41.50%)
- exterior cave exposure: 761/4096 (18.58%)
- upper-surface opening: 143/4096 (3.49%)
- underside opening: 618/4096 (15.09%)
- channel + cave: 1700/4096 (41.50%)
- channel + exterior exposure: 761/4096 (18.58%)
- channel + retained waterbody: 23/4096 (0.56%)
- channel + retained waterbody + cave: 10/4096 (0.24%)

Wet/large/high-relief diagnostic subset (moisture >= 0.65, radius >= 160, relief >= 100): 612 islands; channel 100%, retained waterbody 1.96%, cave-bearing 62.58%.

Interpretation boundary: these are measurements, not automatic acceptance thresholds. The result strongly rejects the prior implication that hydrology/caves rarely coexist. It instead flags retained standing water as unusually sparse and shows upper-surface cave exposure is much rarer than underside exposure.

Human review corpus: DR70_HUMAN_REVIEW_CORPUS.csv. The 100 keys were frozen before visual inspection: 52 deterministic unfiltered keys plus semantic strata for substantial channels, retained water, sealed/exposed caves, river+cave combinations, and wet/large/high-relief cases. Missing stratum capacity is filled deterministically. Human review must not replace failed specimens with prettier seeds.

Atlas role: this is a baseline capability audit of the ordinary Skyforge production stack before content-mod integration and specialized authored island classes. It must expose morphology, topside/edge/underside realization, hydrology, caves, authored ecology through native Minecraft surface/population representation, persistence, and cross-system coherence. Native surface rules are evaluated against the island's exact authored ecology resolver on an isolated in-memory island chunk; neither the biome beneath an island nor a hidden donor world has representation authority. Specialized later content such as ocean-bowl islands is deliberately outside this corpus.

Review traversal is coverage-first rather than purely cost-first. The first wave contains every built-in morphology family at compact, middle, and large authored radii; the next early positions guarantee each explicitly frozen semantic stress stratum appears; the remaining specimens then continue in radius-aware order. This makes early-stop human findings representative across system capability axes without altering or replacing any frozen corpus member.

## September 22 human-gate hydrology findings

The first v9 human specimens confirmed that authored ecology/native surface representation was materially improved, but the hydrology realization gate still failed. Two systemic visual defects were observed before an edge-discharge projection crash stopped the third specimen:

- literal drop deployments could create disconnected Minecraft water-source columns on island exteriors, producing physically unsupported streams into the void;
- routed channels remained too trench-like after voxelization: wet corridors were narrow relative to their carved relief and did not read as convincing river/stream landforms.

The repair contract is therefore:

- visible fluid authority comes only from connected routed channels and retained basins; cascade/waterfall/drop semantics shape terrain but never manufacture independent source columns;
- visible edge discharge must be the terminal outlet of a retained routed channel;
- Minecraft wet cells must remain laterally contained by wet neighbors or banks, except for one explicit terminal outlet breach;
- discharge/profile hierarchy continues to control width and relief, but the Minecraft-facing neutral geometry is calibrated wider and shallower while retaining the accepted AUTH-0105 maximum-lowering cap;
- a coarse intent that cannot map to a physically valid exact-volume realization must degrade conservatively instead of crashing the server or inventing water.

Retained standing water remains a separate follow-up realization gate. Its semantic footprint already carries connected inundation and a common spill-constrained water surface, but the current Minecraft adapter still samples coarse footprint cells rather than rasterizing a level fluid body. The retained-water atlas stratum must not be accepted until that backend projection is upgraded and visually reviewed.
