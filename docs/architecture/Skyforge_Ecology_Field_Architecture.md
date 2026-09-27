# Skyforge ecology field architecture

**Status:** first integration architecture; compatibility-preserving  
**Governing issues:** #1192, #1194, #1196, #1198, #1200, #1202, #1204  
**Scope:** backend-neutral Authorship ecology

## Purpose

Skyforge ecology must describe ecological meaning independently of any one game backend while remaining
composable with morphology, geology, atmosphere, hydrology, caves, and later disturbance/civilization
systems.

The accepted ecology foundation already provides useful semantics:

- AUTH-0003: broad island-local ecological regimes plus vegetation, saturation, and thermal-suitability potentials;
- AUTH-0088 / AUTH-0103: exact authored-realization world-X/Z surface projection;
- AUTH-0089 / AUTH-0090: deterministic island/regional ecological opportunity summaries;
- AUTH-0091: retained-freshwater habitat opportunity.

Those contracts remain valid. This document defines how ecology grows beyond that prototype without
turning Minecraft biome identity, mod species, or subsystem implementation APIs into ecological truth.

## Governing rule

> Ecology consumes semantic fields and produces semantic fields. Backends consume ecology; they do not define it.

A hydrology solver, atmosphere solver, geology system, or backend may provide fields used by ecology,
but ecology must not call into those subsystems as imperative services.

The intended dependency shape is:

~~~text
authored identity / descriptors
          |
          v
physical and environmental source fields
(morphology, geology, atmosphere, hydrology, caves, disturbance)
          |
          v
ecological response fields
          |
          v
community / habitat suitability fields
          |
          v
community assembly state
(dispersal/connectivity, disturbance/succession, biotic filters)
          |
          v
ecological realization fields
          |
          +--------------------+
          |                    |
          v                    v
backend projection       planning/analysis
(Minecraft, mods,        (fauna, resources,
other clients)            settlements, evidence)
~~~

All arrows are downstream for the initial architecture. Ecology does not feed back into atmosphere or
hydrology until a future milestone explicitly introduces and solves a coupled model.

## 1. Current compatibility input seam

Issue #1192 introduces `SkyIslandEcologyInputFieldSet` as the explicit input boundary for the accepted
AUTH-0003 implementation.

The compatibility fields are:

| Input | Current semantic meaning | Range |
| --- | --- | --- |
| `interiority` | current authored naturalized-domain membership/strength | [0,1] |
| `temperature` | normalized AUTH-0002 thermal condition | [0,1] |
| `moisture` | normalized AUTH-0002 moisture condition | [0,1] |
| `elevationTendency` | normalized local topographic tendency | [0,1] |
| `exposure` | normalized environmental exposure tendency | [0,1] |
| `hydrologicalPotentialPrior` | descriptor-level AUTH-0001 hydrological prior | [0,1] |
| `ecologicalPotentialPrior` | descriptor-level AUTH-0001 ecological prior | [0,1] |

These names describe the accepted semantic quantities. In particular, `temperature` is not Kelvin and
`moisture` is not a water-mass or soil-water SI quantity.

`SkyIslandEcologyInputFieldSet.fromCurrentSemantics(descriptor)` reconstructs the exact accepted
AUTH-0003 inputs. `SkyIslandEcologyField.create(descriptor)` remains the compatibility factory and must
produce exactly the same samples as before this seam existed.

The seam exists so later graph-backed producers can provide accepted equivalent environmental semantics
without ecology acquiring calls such as `atmosphere.getTemperature()` or `hydrology.getWater()`.

## 2. Environmental source layer

The long-term environmental source layer owns physical or semantically primary conditions, not
ecological interpretation.

Likely source families include:

- authored-domain / physical support;
- terrain elevation, slope, aspect, curvature, landform position, and shelter/exposure;
- substrate, permeability, regolith/soil opportunity, and geologic material;
- air temperature and radiative/thermal conditions;
- precipitation and atmospheric moisture;
- wind and exposure;
- surface/soil water availability, drainage, saturation, retained water, and riparian proximity;
- cave/light/depth and underside/cliff opportunity;
- disturbance or civilization influence when explicitly authored.

This is a vocabulary direction, not authorization to manufacture fields before their producers and
units are accepted.

### Units rule

Where a producer owns a meaningful physical unit, preserve that unit in the producer/source layer.
Normalization belongs in an explicit ecological response transform.

Do not relabel normalized legacy potentials as physical quantities merely to make the API look more
scientific.

## 3. Ecological response layer

Ecological responses answer:

> Given environmental state, how favorable or stressful is this location along one ecological axis?

Responses should generally be continuous and independently inspectable.

Issue #1194 implements this separation for the accepted AUTH-0003 continuous responses through
`SkyIslandEcologyResponseFieldSet` and `SkyIslandEcologyResponseSample`. Downstream consumers may now
sample the response bundle or the individual normalized response fields without consuming a categorical
`SkyIslandEcologyRegime`.

The accepted AUTH-0003 responses remain:

- `thermalSuitability`;
- `saturationPotential`;
- `vegetationPotential`.

Likely later response families, once their source fields exist, include:

- biologically available water;
- productivity potential;
- exposure stress;
- rooting/soil support;
- substrate suitability;
- light availability;
- disturbance stress;
- aquatic/riparian opportunity;
- cliff/cave/underside opportunity.

Do not multiply species-specific versions of every source quantity. General environmental responses
should be reusable inputs to many communities and later niche policies.

## 4. Community suitability layer

A mature ecology must not reduce the world to one categorical biome label too early.

For community (c) at position (x):

~~~text
S_c(x) = f_c(response_1(x), response_2(x), ..., authored context)
~~~

with suitability normally represented as a bounded continuous value.

Several communities may therefore be simultaneously suitable at one position:

~~~text
temperate woodland   0.78
montane woodland     0.46
open grassland       0.31
wetland              0.07
~~~

This overlap is intentional. It provides ecotones and lets downstream clients choose how much
categorical simplification they need.

Issue #1196 implements the first overlapping habitat-opportunity layer through
`SkyIslandHabitatOpportunityFieldSet`. The initial opportunities are woodland, open vegetation,
saturated lowland, alpine/exposed, and xeric/exposed. They are continuous environmental opportunity
envelopes, not biome IDs, occupancy claims, or mutually exclusive communities.

Issue #1198 adds the first structural community-suitability layer through
`SkyIslandCommunitySuitabilityFieldSet`. The initial archetypes are closed woodland, open herbaceous,
saturated wetland, alpine tundra, and xeric scrub. These are overlapping suitability envelopes derived
from accepted habitat opportunity and ecological response; they remain explicitly separate from
occupancy and assembly history.

The current `SkyIslandEcologyRegime` enum remains an accepted compact summary/compatibility vocabulary.
It is **not** promoted to the final community taxonomy, and issue #1192 does not change its thresholds.

## 5. Community assembly layer

Environmental suitability is not occupancy.

A location can be physically suitable for a community or functional group without that community
being present. Later ecology may therefore apply explicit assembly filters such as:

- regional availability / biogeographic pool;
- island isolation and inter-island connectivity;
- dispersal opportunity;
- disturbance history and recurrence;
- successional state;
- competition, predation, grazing, or other accepted biotic interactions;
- civilization/land-use influence where explicitly authored.

The separation is deliberate:

~~~text
environmental response
    -> potential habitat / fundamental suitability
    -> assembly filters and history
    -> realized community state
~~~

AUTH-0092-style isolation evidence is therefore potentially relevant to assembly, but raw isolation
must not be converted into occupancy through an implicit threshold. Likewise, disturbance is not merely
another climate scalar: discrete or historical disturbance can change realized community state even
where the underlying abiotic environment remains suitable.

Issue #1200 establishes the first assembly integration seam through
`SkyIslandCommunityAssemblyEvidence` and `SkyIslandCommunityAssemblyEvidenceBinder`. The seam binds an
exact #1198 community-suitability field set to an exact AUTH-0046 authored-realization association and
its raw AUTH-0092/AUTH-0103 nearest-neighbor isolation evidence. It deliberately performs no distance
normalization, dispersal transform, colonization inference, or occupancy decision.

Singleton catalogs preserve `no neighbor evidence` as an empty optional value rather than fabricating
infinite isolation. Likewise, nominal radial gap remains geometric evidence based on nominal radii; it
is not promoted to physical terrain-edge distance.

Issue #1202 adds a parameterized dispersal-response seam. `SkyIslandCommunityDispersalProfile` interprets
raw #1200 isolation evidence for one explicit dispersal strategy, and
`SkyIslandCommunityAssemblyAccessibility` binds that exact profile/evidence pair to an optional normalized
accessibility result. The first generic implementation, `SkyIslandExponentialDispersalProfile`, applies
`exp(-gap / characteristicGap)` to nearest nominal radial gap using a caller-supplied positive distance
scale. This deliberately avoids a universal isolation score or hard-coded organism category.

Missing neighbor evidence remains unresolved accessibility rather than being converted to 0 or 1.

Issue #1204 adds an explicit disturbance/succession seam. `SkyIslandCommunityDisturbanceEvidence` binds
one exact #1200 assembly context to an optional latest disturbance event carrying normalized severity
and non-negative elapsed ecological time. `SkyIslandCommunitySuccessionProfile` interprets that evidence
without changing local suitability or dispersal accessibility. The initial generic
`SkyIslandExponentialSuccessionProfile` produces residual-disturbance and recovery-progress signals from
a caller-supplied characteristic recovery time.

No disturbance mechanism is inferred, and missing disturbance evidence remains unresolved rather than
being treated as pristine habitat. Recovery progress is likewise not equivalent to late-successional
occupancy; individual communities may later interpret the same assembly state differently.

This layer should remain parsimonious. Add an assembly filter only when it explains a concrete
ecological distinction or downstream consumer. It must not become a hidden simulation of every
population interaction.

## 6. Ecological realization layer

Suitability says what the environment can support; assembly constrains what establishes and persists.
Realization describes the resulting authored ecological structure.

Candidate backend-neutral realization fields include:

- vegetation density;
- canopy cover;
- canopy height;
- understory density;
- ground-cover density;
- biomass/productivity proxies;
- patchiness;
- organic-surface accumulation;
- deadwood/disturbance structure;
- functional-group mixture.

These fields are semantic outputs. A realization layer may use deterministic signals for controlled
variation, but random variation may not create ecological identity independently of environmental
causes.

## 7. Functional groups before concrete species

Core ecological mechanics should prefer reusable functional groups or niches over backend entity IDs.

Examples:

- tall canopy tree;
- small tree;
- shrub;
- grass/forb;
- fern/groundcover;
- aquatic plant;
- decomposer/fungus;
- pollinator;
- medium browser;
- scavenger;
- aerial raptor;
- aquatic predator.

Concrete vanilla/modded species are downstream content/backend choices constrained by these semantics.

A rich installed mod catalogue is therefore compatible with a sparse authored island: the ecological
model constrains which roles are actually realized.

## 8. Backend boundary

The ecology kernel/world layer may expose:

- environmental-response fields;
- community-suitability fields;
- realization/structure fields;
- ecology provenance and authored identity;
- aggregate opportunity summaries.

A Minecraft or modded adapter may map those outputs to:

- registered biome identities;
- configured/placed features;
- blocks, soils, foliage, particles, ambience;
- concrete entities/species;
- backend-specific persistence and population lifecycle.

Those mappings remain downstream. They may not leak registry keys, entity IDs, block IDs, quart-cell
policy, or backend lifecycle into neutral ecology.

The same ecology result should be consumable by a Minecraft adapter, a mod integration, a reference
visualizer, or another game/backend without changing its authored meaning.

## 9. Graph integration and provenance

Ecological dependencies must remain visible as field dependencies.

Prefer:

~~~text
temperature field ----+
water field ----------+--> ecological response --> community suitability
exposure field -------+
substrate field ------+
~~~

over imperative subsystem queries.

This preserves:

- deterministic evaluation;
- dependency inspection;
- independent testing;
- caching/lazy evaluation opportunities;
- adaptive-resolution evaluation;
- provenance;
- backend independence.

An ecology result must retain enough authored identity/provenance that later consumers do not
rediscover an island from seed, coordinate proximity, encounter order, or backend state.

## 10. Resolution and dimensionality

Not every ecological quantity needs the same spatial dimensionality or sampling scale.

Examples:

- surface plant-community suitability is naturally a 2-D surface/local-XZ field;
- cave, underwater, canopy-volume, or atmospheric fauna habitat may require 3-D fields;
- island/regional opportunity profiles are deterministic aggregates over accepted local fields.

Do not force every ecological concept into one global raster resolution. Reuse Skyforge's existing
principle: evaluate at the spatial scale required by the consumer while preserving deterministic
semantics and explicit aggregation rules.

## 11. Hierarchy and descriptors

World/province/cluster/island descriptors may bias ecological priors and composition, but they do not
paint backend biomes directly.

The hierarchy is:

~~~text
descriptor/context -> environmental causes -> response -> suitability -> assembly -> realization
~~~

not:

~~~text
descriptor -> biome ID
~~~

A semantic descriptor such as lush, dry, exposed, ancient, or disturbed may alter accepted priors or
response parameters only through an explicit contract. Environmental feasibility still constrains the
result.

## 12. Initial acyclic rule

Real ecosystems feed back into hydrology and atmosphere through interception, evapotranspiration,
roughness, infiltration, erosion, and albedo.

Skyforge does not model those feedbacks implicitly.

Initial authority remains acyclic:

~~~text
morphology/geology/atmosphere/hydrology
                 -> ecology
~~~

Any future coupled iteration requires a separately specified convergence/authority model. No ecology
implementation may quietly mutate an upstream field.

## 13. Compatibility and migration policy

AUTH-0003 and its downstream projections/profiles are already accepted project contracts.

Therefore:

1. the current AUTH-0003 formula/classification remains available through the compatibility factory;
2. issue #1192 changes dependency injection, not ecological output;
3. issue #1194 moves the accepted continuous response formulas into a reusable field layer without changing their values;
4. issue #1196 adds overlapping habitat-opportunity fields without replacing the AUTH-0003 categorical compatibility projection;
5. issue #1198 adds overlapping structural community suitability without asserting occupancy;
6. issue #1200 binds suitability to raw regional assembly evidence without applying a universal isolation penalty;
7. issue #1202 permits explicit profile-specific dispersal accessibility while preserving missing-neighbor uncertainty;
8. issue #1204 permits explicit profile-specific disturbance/recovery state while preserving missing-history uncertainty;
9. later replacement response/community models must be versioned or explicitly migrated;
10. AUTH-0088/0089/0090/0103 consumers must not silently observe changed semantics merely because a
   richer model exists;
11. new ecology authority should be earned through deterministic reference evidence before a backend
   adopts it.

## Immediate next work after #1204

The compatibility input seam and reusable AUTH-0003 response layer are now structurally defined. The
next ecology tranche should still be selected from actual available upstream semantics rather than
invented in advance.

A prudent sequence is:

1. keep the AUTH-0003 response set as the stable compatibility baseline;
2. keep #1196 habitat opportunity as an overlapping environmental envelope rather than a final biome taxonomy;
3. inventory accepted atmosphere/hydrology/geology/morphology fields that have stable semantics;
4. introduce richer or more physical ecological responses only from those real neutral producers and
   version/migrate them explicitly rather than silently changing AUTH-0003;
5. keep #1198 community suitability as a potential-establishment layer rather than an occupancy result;
6. keep #1200 isolation evidence raw and #1202 dispersal accessibility explicitly profile-specific;
7. keep #1204 disturbance history explicit and its recovery interpretation profile-specific;
8. introduce functional-group assembly profiles only when dispersal and disturbance responses are
   explicit and testable rather than assigning one universal filter;
9. add realization/functional-group fields before any concrete species/population policy.

This keeps ecology integrated with the rest of Skyforge without allowing any backend or one subsystem
to become the owner of ecological meaning.

## Ecological modelling rationale

This layering intentionally mirrors established ecosystem/community modelling distinctions rather than
treating biome labels as causes. Abiotic resources and ambient environment constrain feasibility;
disturbance, dispersal, and biotic interactions additionally filter community assembly; ecosystem
structure and function are resulting properties rather than interchangeable labels. Functional groups
are useful abstractions, but continuous trait/response variation should remain possible instead of
forcing all vegetation into a small fixed type list.

For Skyforge this is an architectural discipline, not a claim to reproduce Earth ecology in full.
Complexity should be added only when it creates visible, gameplay-relevant, or analytically useful
world differences while preserving deterministic provenance and backend neutrality.
