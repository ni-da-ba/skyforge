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
