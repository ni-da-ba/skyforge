# AUTH-0084 — Production regional morphology context review

## Purpose

AUTH-0084 is the second concrete authorship consumer of visual-quality issue #214.

AUTH-0083 established deterministic isolated-island review across built-in families, scales, seeds,
hybrids, and an external-provider axis.

AUTH-0084 asks the next player-facing question:

> When accepted island morphologies are composed by the existing group and archipelago planners,
> does the resulting region preserve navigable negative space, readable hierarchy, useful
> orientation, and morphological variety?

AUTH-0084 adds no new semantic-core protocol and no new placement planner.

## Context matrix

The review contains exactly five deterministic contexts at the canonical Skyforge seed.

### Sparse

One deliberately low-density five-island mixed-provider group compiled through the accepted
`SkyIslandGroupPlanner`.

The sparse scene uses:

- five distinct morphology specifications;
- full bounded detail;
- full secondary morphology;
- substantially larger center spacing and requested gap than the accepted chain/cluster corpus;
- bounded elevation jitter.

It exists to establish a negative-space control: islands should read as individual destinations
rather than a continuous ceiling.

### Chain

The exact accepted `SkyIslandGroupReferenceCorpus.chain(...)` request.

Review emphasis:

- end-to-end route readability;
- orientation consistency without mechanical repetition;
- island individuality along a corridor;
- underside readability while flying the chain.

### Cluster

The exact accepted `SkyIslandGroupReferenceCorpus.cluster(...)` request.

Review emphasis:

- local hierarchy;
- usable openings through/around the formation;
- occlusion without roofing;
- nonrepetitive neighboring silhouettes.

### Hub

The exact accepted `SkyIslandArchipelagoReferenceCorpus.hub(...)` request.

Review emphasis:

- anchor-group dominance;
- clear secondary/satellite/outlier hierarchy;
- approach readability from an outer formation toward the anchor;
- preservation of large navigable sky volumes.

### Arc

The exact accepted `SkyIslandArchipelagoReferenceCorpus.arc(...)` request.

Review emphasis:

- regional corridor legibility;
- negative-space rhythm;
- end-to-end navigation;
- variation without loss of the overall arc.

## Why existing planners are reused

Issue #214 is a visual-quality gate, not evidence that Skyforge needs another regional planning
abstraction.

Existing group and archipelago planners already provide:

- deterministic placement;
- explicit minimum reservation gaps;
- bounded elevation variation;
- provider-neutral morphology specifications;
- hierarchical group roles;
- chain/cluster/Hub/Arc grammar.

AUTH-0084 therefore exercises those accepted semantics instead of redesigning them.

The sparse case is expressed as a low-density request through the existing cluster planner rather
than by adding a new `Sparse` layout type.

## Reference evidence

The atlas generator writes one evidence directory per context.

Group contexts provide:

- planner reservation view;
- realized top-down union;
- upper elevation envelope;
- underside elevation envelope;
- east-west section;
- north-south section;
- isometric upper-surface view.

Hierarchical contexts provide:

- hierarchical planner view;
- realized top-down geometry by group;
- regional upper envelope;
- regional underside envelope;
- regional isometric view.

## Common regional diagnostics

AUTH-0084 records the same descriptive diagnostics across all five contexts:

- group count;
- island count;
- horizontal occupied-footprint fraction;
- minimum island separation normalized by the two islands' nominal-radius sum;
- coefficient of variation of nearest-neighbor spacing;
- suspension-elevation span normalized by mean island vertical scale;
- fraction of distinct morphology specifications;
- dominant morphology-specification share;
- nearest-neighbor exact-morphology repeat fraction;
- realized horizontal aspect ratio;
- dominant child-group solid share.

These measurements expose candidate visual failure modes such as:

- excessive plan coverage / weak negative space;
- mechanical spacing;
- insufficient or excessive vertical layering;
- local morphology repetition;
- lack of hierarchical dominance;
- overelongation or loss of corridor identity.

They do **not** encode aesthetic thresholds.

Thresholds may be introduced only after the measurements are correlated with human reference and
Minecraft review.

## Roofing

The accepted planners already enforce non-overlapping horizontal reservation envelopes.

AUTH-0084 therefore does not create another anti-overlap or anti-roofing semantic contract.

The human gate instead asks whether the resulting formation *looks* open and navigable from realistic
flight viewpoints despite valid reservations.

If a visually oppressive scene passes reservation correctness, the remedy should be traced to the
actual cause:

- layout density;
- member/group scale;
- elevation layering;
- morphology silhouette;
- Minecraft realization;
- or a genuine missing semantic control.

Do not introduce a new abstraction without that diagnosis.

## Minecraft handoff

The generated `minecraft-handoff.csv` defines the player-facing review for each context.

### Sparse

Views:

- high-altitude plan;
- horizon approach;
- below.

Route:

- cross the formation between destinations.

Focus:

- negative space;
- navigation;
- island individuality.

### Chain

Views:

- high-altitude plan;
- along-chain approach;
- below.

Route:

- traverse end to end.

Focus:

- route readability;
- procedural repetition;
- orientation.

### Cluster

Views:

- high-altitude plan;
- cluster approach;
- below.

Route:

- perimeter and through-route.

Focus:

- occlusion;
- local hierarchy;
- open sky.

### Hub

Views:

- high-altitude plan;
- anchor approach;
- below.

Route:

- outer group to anchor.

Focus:

- anchor dominance;
- group hierarchy;
- roofing risk.

### Arc

Views:

- high-altitude plan;
- corridor approach;
- below.

Route:

- end-to-end arc traversal.

Focus:

- corridor readability;
- negative-space rhythm;
- repetition.

Implementation should preserve the context IDs `sparse`, `chain`, `cluster`, `hub`, and
`arc` in screenshot/video evidence.

## Macro / meso / micro interpretation

Regional review remains hierarchical.

### Macro

- entire formation silhouette;
- approach identity;
- route axis;
- negative-space distribution;
- regional hierarchy.

### Meso

- neighboring-island relationships;
- openings;
- group transitions;
- local elevation layering;
- repeated family/morphology patterns.

### Micro

Individual-island local relief remains an AUTH-0083 concern.

Micro terrain should not be used to excuse weak regional composition.

## Acceptance

AUTH-0084 machinery is acceptable when:

1. the exact five-context matrix is deterministic;
2. chain, cluster, Hub, and Arc reuse accepted requests exactly;
3. sparse uses the existing group planner and mixed provider-neutral morphology intent;
4. all contexts compile through accepted production morphology/planning seams;
5. the atlas generator emits comparative reference evidence;
6. summary diagnostics are descriptive rather than prematurely thresholded;
7. the Minecraft handoff names exact views/routes for every context;
8. no Minecraft/NeoForge type enters semantic or recipe layers.

Human regional aesthetic approval is intentionally downstream of this merge.

## Next decision after regional review

AUTH-0083 + AUTH-0084 together provide the morphology evidence needed to decide:

- whether primary-family underside + bounded detail is sufficient;
- whether any explicit secondary underside grammar is justified;
- which morphology or regional parameters need tuning before Bootstrap Province adoption.

After that decision, the next major world-semantic bridge should move toward **visual hydrology and
fluid-role admissibility**, unless the morphology review identifies a concrete semantic deficiency
that must be corrected first.
