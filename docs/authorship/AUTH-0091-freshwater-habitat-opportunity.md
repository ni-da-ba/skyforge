# AUTH-0091 — Freshwater habitat opportunity profile

## Purpose

AUTH-0091 adds a compact island-scale planning summary above the already accepted retained-waterbody
semantics.

The existing hydrology stack can answer detailed questions such as:

- which coarse watershed cells drain where;
- which retained basins survive planning;
- which retained candidates are ponds, lakes, or wetlands;
- which connected planning cells form each inundation footprint;
- which footprint cells are shoreline;
- what normalized water-depth potential each inundated cell carries.

Later ecology, fauna, agriculture, and resource-geography consumers also need a bounded summary:

> Does this authored island retain freshwater, how much coarse horizontal planning area does it
> occupy, what source kinds create it, and what normalized depth opportunity does it expose?

AUTH-0091 answers only that summary question.

It does not select fish, livestock, crops, resources, Minecraft fluids, or population counts.

## Accepted source stack

AUTH-0091 reuses:

~~~text
AUTH-0005 SkyIslandWatershedPlanner
AUTH-0008 / AUTH-0009 retained-waterbody semantics
SkyIslandWaterbodyFootprintPlanner
~~~

No caller supplies:

- a custom watershed grid;
- a custom active threshold;
- a custom basin-retention threshold;
- a custom fill rule;
- an arbitrary footprint list;
- a custom area weight.

The public profiler surface is exactly:

~~~text
profile(SkyIslandDescriptor)
~~~

The accepted planners remain the sole source of the watershed and footprint data.

## Exact provenance retained

The profile retains the exact deterministic SkyIslandWatershedPlan and
SkyIslandWaterbodyFootprintPlan.

Both must belong to the exact authored descriptor carried by the profile.

This makes the summary auditable without recreating waterbody identity from coordinates or scalar
totals.

## Unique inundated planning cells

AUTH-0091 forms the union of all retained footprint cells by exact watershed-cell index.

If the same watershed index appears more than once with identical cell data, it is counted once.

If two retained footprints disagree about the data for one shared watershed index, profiling fails
closed.

Therefore inundatedCellCount means unique retained inundated planning cells, not a sum that can
double-count overlapping footprints.

## Coarse horizontal inundated-area estimate

The accepted AUTH-0005 watershed plan exposes one fixed grid spacing.

AUTH-0091 computes:

~~~text
cellArea = watershed.spacing ^ 2

coarseHorizontalInundatedAreaEstimate
  = inundatedCellCount * cellArea
~~~

This is intentionally named a **coarse horizontal planning estimate**.

It is useful for distinguishing, for example:

- no retained freshwater;
- a small pond/wetland opportunity;
- a materially larger retained-water footprint.

It is not:

- exact physical water surface area;
- sloped shoreline area;
- Minecraft water-block count;
- cubic water volume;
- flow discharge.

The watershed grid is a semantic planning lattice, not a block raster.

## Shoreline planning cells

AUTH-0091 exposes shorelineCellCount as the number of unique inundated planning cells marked
shoreline by the accepted footprint planner.

This is not converted into shoreline length.

Eight-neighbor coarse-grid shoreline membership is useful habitat evidence, but multiplying shoreline
cells by spacing would not constitute a proof of physical perimeter.

## Retained source kinds

Every accepted SkyIslandWaterbodyKind is represented in the source-kind count map:

~~~text
POND
LAKE
WETLAND
~~~

Counts are derived only from the exact source candidates retained by the footprint plan.

Their sum must equal sourceCandidateCount.

AUTH-0091 does not rank one waterbody kind as intrinsically better habitat.

## Normalized water-depth opportunity

Every retained footprint cell already carries waterDepthPotential in [0,1].

AUTH-0091 exposes:

- meanWaterDepthPotential;
- maxWaterDepthPotential.

These are unchanged normalized semantic potentials.

They are **not metres**, block depth, or physical water-column volume.

A later aquatic-fauna or wet-agriculture policy may use these potentials together with other
accepted causes, but must not silently relabel them as physical dimensions.

## Dry islands are valid

An authored island may legitimately retain no waterbody.

AUTH-0091 must preserve that outcome.

A dry profile has:

~~~text
footprintCount = 0
sourceCandidateCount = 0
inundatedCellCount = 0
coarseHorizontalInundatedAreaEstimate = 0
shorelineCellCount = 0
meanWaterDepthPotential = 0
maxWaterDepthPotential = 0
all source-kind counts = 0
~~~

The profiler must not fabricate a pond merely to avoid an empty profile.

The existing key-77 drainage control is used as the zero-water regression fixture.

## Scale covariance

The watershed planner uses a fixed grid count while spacing follows nominal island radius.

For a descriptor changed only by doubling nominal radius while retaining the same normalized
authorship causes, AUTH-0091 verifies:

- the watershed grid size remains unchanged;
- watershed spacing doubles;
- retained source-kind composition remains unchanged;
- unique inundated planning-cell identity remains unchanged;
- shoreline planning-cell count remains unchanged;
- normalized mean/max water-depth opportunity remains unchanged;
- coarse horizontal inundated-area estimate increases by four times.

This separates normalized freshwater semantics from habitat scale.

## Concrete downstream motivation

### Fauna

The ecology/fauna design requires water availability and water depth among the causes considered
before aquatic/riparian species are selected.

AUTH-0091 supplies:

- retained freshwater presence/absence;
- coarse horizontal inundation opportunity;
- shoreline-cell opportunity;
- normalized depth opportunity;
- pond/lake/wetland source composition.

It does **not** yet supply:

- physical water volume;
- temperature of the water column;
- open-water connectivity;
- trophic state;
- predator pressure;
- fish carrying capacity.

Therefore AUTH-0091 alone cannot justify a concrete fish, amphibian, otter, waterfowl, or other
species.

### Agriculture / resource geography

The resource design lists hydrology as one cause for:

- water;
- aquatic resources;
- wet agriculture;
- future ocean resources.

AUTH-0091 provides a retained-freshwater evidence input without deciding crop IDs, agricultural
province labels, or resource availability classes.

Those decisions still require ecology, geology, civilization/trade, progression, and other causes.

## Ownership boundaries

AUTH-0091 remains backend-neutral.

It does not inspect or emit:

- Minecraft water blocks or fluids;
- biome keys;
- entity IDs;
- spawn rules;
- population/carrying-capacity budgets;
- crop/resource IDs;
- settlement/province classes;
- save state;
- physical cubic volume;
- physical depth in blocks/metres.

Implementation remains responsible for concrete fluid realization and lifecycle.

Content remains responsible for mapping accepted habitat opportunity to retained species/resources
when sufficient semantic causes exist.

## Acceptance gate

Reject AUTH-0091 if:

- callers can select watershed resolution or thresholds;
- callers can inject arbitrary footprint data;
- provenance plans do not belong to the exact descriptor;
- overlapping retained cells are double-counted;
- conflicting duplicate cell data is silently accepted;
- dry islands are rejected or given fabricated water;
- coarse area is described as exact physical surface area;
- shoreline cells are converted into unsupported physical shoreline length;
- normalized water-depth potential is described as metres, blocks, or cubic volume;
- waterbody source-kind counts omit an accepted enum value or fail to sum to source count;
- species, carrying capacity, crop/resource eligibility, Minecraft fluid identity, or backend
  lifecycle enters the contract.

## Evidence target

The proof package should demonstrate:

- deterministic wet-island profiling;
- exact key-83 watershed/footprint provenance;
- exact one-footprint / two-source retained-water fixture;
- valid key-77 all-zero dry profile;
- unique-cell area arithmetic;
- complete source-kind counts;
- normalized mean/max depth;
- radius-doubling scale covariance;
- no physical-volume or backend-policy claim.

The evidence is semantic/planning proof, not visual-water quality or aquatic-fauna acceptance.

## Next boundary

After AUTH-0091, do not immediately assign aquatic species or wet-agriculture resources.

The next semantic step should add another genuinely missing habitat/resource cause only where an
existing consumer requires it.

Cave semantics currently expose normalized semantic depth rather than physical cave volume, so a
future cave-habitat profile must preserve that distinction rather than invent cubic volume.

Likewise, a regional resource profile should wait until ecology, freshwater/hydrology, geology,
civilization/trade, and progression requirements can be combined without letting one axis own the
decision.
