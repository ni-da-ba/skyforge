# AUTH-0089 — Island ecological opportunity profile

## Purpose

AUTH-0089 adds the missing island-scale planning view above the accepted AUTH-0003 local ecology
field.

AUTH-0003 can answer:

> what ecological regime and continuous vegetation/saturation/thermal potentials exist at this
> island-local position?

Later fauna, resource-geography, settlement, and population systems also need a different question:

> how much horizontally authored habitat does this island contain, what is its regime composition,
> and what are its mean accepted ecological potentials?

AUTH-0089 answers only that aggregate question.

It does not yet decide which animal, crop, resource, settlement, or Minecraft biome should exist.

## Scope

AUTH-0089 adds:

- `SkyIslandEcologicalOpportunityProfiler`;
- `SkyIslandEcologicalOpportunityProfile`.

The profiler consumes one placement-free `SkyIslandDescriptor` and evaluates the already accepted
current semantic domain plus AUTH-0003 ecology.

The profile exposes:

- fixed sampling resolution;
- authored-domain cell count;
- horizontal authored-habitat area estimate;
- mean AUTH-0003 vegetation potential;
- mean AUTH-0003 saturation potential;
- mean AUTH-0003 thermal suitability;
- a complete fraction for every existing `SkyIslandEcologyRegime`.

No new ecology regime is introduced.

## Fixed deterministic quadrature

The profiler uses one fixed cell-center quadrature:

~~~text
samples per axis = 128
domain bounds     = [-nominalRadius, +nominalRadius] in local X/Z
cell width        = 2 * nominalRadius / 128
ownership         = current AUTH-0021 interiority > 0
~~~

For each owned cell center, the profiler evaluates the exact current AUTH-0003
`SkyIslandEcologyField`.

The fixed resolution is part of the AUTH-0089 planning contract. Consumers do not choose their own
resolution and therefore cannot silently create incompatible island summaries.

AUTH-0089 does not add a second authored-domain threshold. It reuses the established
`interiority > 0` ownership rule.

## Horizontal area semantics

The profile reports:

~~~text
horizontalOwnedAreaEstimate = ownedCellCount * cellWidth^2
~~~

This is intentionally named a **horizontal** planning estimate.

It is useful for island-scale ecological opportunity because it distinguishes, for example, a small
productive island from a much larger island with the same normalized ecological composition.

It is not:

- physical sloped terrain surface area;
- compiled-volume area;
- Minecraft block count;
- vegetation block count;
- carrying capacity;
- spawn budget.

Those require additional physical or gameplay authority.

## Regime composition

Every existing AUTH-0003 regime appears in the profile map, including regimes with fraction 0.

Fractions are computed only over currently authored cells and must sum to one.

Therefore the profile preserves distinctions such as:

- an island dominated by woodland;
- a mixed grassland/wetland island;
- an alpine/barren island;
- a humid productive island.

The profile does not collapse those compositions into one invented quality score.

## Continuous ecological opportunity

AUTH-0089 also reports the means of the exact accepted AUTH-0003 continuous potentials:

- vegetation;
- saturation;
- thermal suitability.

It does not recompute those values and does not introduce new weights.

This is deliberate. A later fauna or resource author may need the raw composition and continuous
opportunity evidence before any domain-specific suitability policy is justified.

## Scale covariance

AUTH-0089 explicitly verifies an important semantic invariant.

If one descriptor is changed only by doubling nominal radius while all normalized authorship causes
remain identical:

- the fixed normalized quadrature visits the same semantic pattern;
- owned-cell count remains the same;
- regime fractions remain the same;
- mean vegetation/saturation/thermal potentials remain the same;
- horizontal planning area increases by exactly four times, within floating arithmetic tolerance.

This demonstrates that AUTH-0089 separates **ecological composition** from **habitat scale** rather
than allowing raster resolution to redefine the authored ecology.

## Concrete downstream consumers

### Fauna / habitat planning

The ecology design audit requires later population authority to consider factors such as surface
area, primary productivity, moisture, vegetation structure, and habitat type before selecting
concrete mod species.

AUTH-0089 supplies part of that evidence without prematurely defining species or population counts.

A later semantic milestone may combine this profile with:

- water volume/depth;
- cave volume;
- cliff/underside opportunity;
- isolation;
- disturbance;
- trophic state;

before assigning explicit roles such as `POLLINATOR`, `MEDIUM_BROWSER`, or `AERIAL_RAPTOR`.

Wave C5/C6 thermal-soaring behavior remains separate: atmospheric lift can change how an already
admitted hawk behaves, but AUTH-0089 does not make thermals spawn hawks.

### Resource geography

Ecology-sensitive resources can later use island-scale composition such as productive woodland,
wetland share, or dry/open habitat without independently rescanning backend biome blocks.

AUTH-0089 does not assign crops, timber species, loot, or mod resources.

## Ownership boundaries

AUTH-0089 remains entirely backend-neutral.

It does not inspect:

- `SkyIslandCompiledWorldPublication`;
- compiled physical terrain;
- Minecraft biome keys;
- blocks;
- entities;
- native population state;
- save data;
- registries;
- atmosphere runtime state.

AUTH-0088 remains the accepted/proposed route for projecting local ecology into one exact published
world-space realization.

AUTH-0089 instead summarizes placement-free authored ecology before backend realization.

## Acceptance gate

Reject AUTH-0089 if:

- it introduces a new ecology regime;
- it changes AUTH-0003 classification or continuous-potential formulas;
- it uses a second native-domain ownership threshold;
- consumers can select arbitrary incompatible raster resolutions;
- horizontal planning area is presented as exact physical terrain area;
- species, entity IDs, population counts, spawn budgets, resource IDs, or Minecraft biome IDs enter
  the profile;
- compiled terrain or backend lifecycle becomes an input;
- regime fractions do not cover the complete AUTH-0003 enum or fail to normalize;
- scale-only changes alter normalized ecological composition beyond numerical tolerance.

## Evidence target

The compact proof package should demonstrate:

- deterministic repeated profiling;
- complete normalized regime composition;
- normalized mean potentials;
- bounded horizontal area;
- radius-doubling scale covariance;
- materially different profiles across distinct authored climates;
- no backend identity.

The evidence is a semantic/planning proof, not a fauna-density or visual-biome acceptance review.

## Next boundary

After AUTH-0089, a later fauna milestone can define ecological roles only when the remaining required
habitat causes are available and a concrete population consumer exists.

Do not jump directly from AUTH-0089 to entity spawning.

For resource/progression work, AUTH-0089 may also become one input to regional ecological
specialization and Bootstrap Province completeness without making ecology the sole progression axis.
