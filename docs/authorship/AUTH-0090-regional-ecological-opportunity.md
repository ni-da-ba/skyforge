# AUTH-0090 — Regional ecological opportunity aggregation

## Purpose

AUTH-0090 lifts the accepted AUTH-0089 island-scale ecological opportunity profile to one accepted
regional Skyforge publication.

It answers:

> Across this exact accepted regional publication, how much horizontally authored habitat exists,
> what is its AUTH-0003 ecological composition, and what are its area-weighted continuous ecological
> opportunities?

It does **not** answer:

- which species spawn;
- how many animals the region carries;
- which crops/timber/resources are eligible;
- whether the region is an "agricultural province";
- which Minecraft biomes represent the region.

Those remain later policies requiring additional causes.

## Canonical regional boundary

AUTH-0090 does not invent a region identifier.

Its input is exactly:

~~~text
SkyIslandPublishedAuthoredRealizationBinding
~~~

That binding already proves:

- one accepted AUTH-0058 regional publication;
- one explicit AUTH-0046 association for every published volume;
- exact publication root/count/volume coverage;
- exact authored descriptor ↔ realized volume association;
- canonical association order.

Therefore AUTH-0090 treats the accepted AUTH-0058 publication as the regional aggregation boundary.

The public profiler surface is intentionally:

~~~text
profile(SkyIslandPublishedAuthoredRealizationBinding)
~~~

There is no overload accepting:

- arbitrary descriptor lists;
- arbitrary AUTH-0089 profile lists;
- a caller-selected island subset;
- a caller-selected raster resolution;
- custom weights.

## Island source

For every canonical AUTH-0087 association, AUTH-0090 invokes the accepted AUTH-0089 profiler on the
exact associated authored descriptor.

Each regional entry retains:

~~~text
AUTH-0046 association
AUTH-0089 island ecological opportunity profile
~~~

The entry rejects a profile whose descriptor differs from the exact associated authored descriptor.

Regional entries must preserve the AUTH-0087 association catalog's canonical order and exact count.

Missing, reordered, substituted, or foreign entries fail closed.

## Regional area

AUTH-0090 sums AUTH-0089:

~~~text
horizontalOwnedAreaEstimate
~~~

over every associated island.

The result is:

~~~text
totalHorizontalOwnedAreaEstimate
~~~

This has the same semantics as AUTH-0089:

- horizontal authored-habitat planning area;
- not sloped physical surface area;
- not compiled terrain area;
- not Minecraft block count;
- not carrying capacity.

## Area-weighted continuous opportunity

Regional continuous opportunity uses AUTH-0089 horizontal authored-habitat area as the only weight.

For one potential p_i and island area A_i:

~~~text
regionalMean = sum(A_i * p_i) / sum(A_i)
~~~

AUTH-0090 exposes area-weighted means for the unchanged AUTH-0003-derived:

- vegetation potential;
- saturation potential;
- thermal suitability.

No new coefficients or thresholds are introduced.

The area weighting matters because a tiny productive island and a very large productive island are
not equivalent regional habitat opportunities even if their normalized local ecology is identical.

## Area-weighted regime composition

For every existing AUTH-0003 regime r:

~~~text
regionalFraction(r)
  = sum(A_i * islandFraction_i(r)) / sum(A_i)
~~~

Every AUTH-0003 regime remains represented, including a zero fraction.

Fractions must sum to one within floating arithmetic tolerance.

AUTH-0090 does not derive a new region class from those fractions.

## Provenance

A regional profile exposes:

- exact AUTH-0087 binding;
- exact AUTH-0058 publication identity;
- authored world seed;
- canonical island-entry list;
- exact association per entry;
- exact AUTH-0089 profile per associated descriptor.

This lets a later consumer explain regional ecology without reverse-discovering islands from
coordinates, seeds, geometry, or order.

## Concrete consumers

### Bootstrap / resource geography

The resource and progression audit already requires:

- "enough ecological diversity for normal play" at regional scale;
- ecological/climate causes for timber, crops, livestock, food/medicinal ingredients, and special
  plants;
- productive climate/hydrology/surface terrain as a cause of agricultural provinces;
- resource concentrations to correlate with authored geology/ecology/civilization.

AUTH-0090 supplies regional ecological evidence for those later decisions.

It does **not** implement:

- ResourceAvailabilityClass;
- RegionalResourceProfile;
- BootstrapCompletenessRequirement;
- resource-to-ecology thresholds;
- crop/timber/livestock IDs.

Those policies need geology, hydrology, trade/salvage, civilization, and progression state as well.

### Fauna

The ecology/fauna audit requires surface area, productivity, climate, water, vegetation structure,
cliff/cave opportunity, isolation, disturbance, predator pressure, and other causes before concrete
niche or species selection.

AUTH-0090 supplies only the regional surface-ecology/area portion.

Do not convert one regional woodland fraction into "spawn deer" or one thermal mean into
THERMAL_SOARER.

Atmosphere remains behavior authority for thermal soaring; it does not create population.

## Ownership boundaries

AUTH-0090 remains backend-neutral.

It does not inspect or emit:

- Minecraft biome keys;
- blocks;
- entities;
- spawn rules;
- population budgets;
- native biome population;
- resource blocks/items/tags;
- structures/settlements;
- save state;
- atmosphere runtime values;
- physical terrain surface area.

The publication and association objects are consumed only as accepted identity/provenance carriers.

## Acceptance gate

Reject AUTH-0090 if:

- callers can aggregate an arbitrary island list instead of an AUTH-0087 binding;
- an island profile can be paired with a different authored association;
- any published association can be omitted;
- regional entry order differs from canonical AUTH-0087 order;
- caller-selected weights or raster resolution enter the API;
- regional means are simple island-count means rather than horizontal-area weighted;
- regime fractions are not area-weighted or fail to normalize;
- AUTH-0003/AUTH-0089 formulas are recomputed with new thresholds;
- species, carrying capacity, spawn counts, resource eligibility, province labels, Minecraft biome
  identity, or backend lifecycle enters the contract.

## Evidence target

The compact proof package should demonstrate:

- genuine multi-volume accepted AUTH-0058 publication;
- exact AUTH-0087 association coverage;
- deterministic AUTH-0089 profile production for every associated island;
- canonical provenance order;
- exact area sum;
- exact area-weighted vegetation/saturation/thermal means;
- complete normalized area-weighted regime composition;
- rejection of missing/reordered/substituted entries;
- no species/resource/backend policy.

The atlas is semantic/provenance evidence, not a visual ecology or production-density review.

## Next boundary

After AUTH-0090, do not immediately assign regional resource or fauna roles.

A prudent next step must demonstrate which additional accepted causes are sufficient for one concrete
consumer.

For resource geography that likely means combining regional ecology with accepted geology,
hydrology, civilization/trade, and progression requirements.

For fauna it likely means authoring missing habitat causes such as cliff/cave opportunity, water
volume/depth, isolation, disturbance, and trophic state before role suitability.
