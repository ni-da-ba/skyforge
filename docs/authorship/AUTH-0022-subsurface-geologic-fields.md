# AUTH-0022 — Subsurface Geological Fields

AUTH-0022 begins the geological/interior authorship front.

It promotes the existing island-scale geological priors established in AUTH-0001 into coherent backend-neutral subsurface meaning without introducing named rock types, block materials, or cave geometry.

## Dependency

~~~text
SkyIslandDescriptor
    -> rock competence / permeability / hydrological potential / erosion maturity
    -> current naturalized island domain
    -> current surface semantic fields
    -> AUTH-0022 subsurface geological fields
        -> bulk competence
        -> fracture intensity
        -> connected permeability
        -> groundwater potential
        -> void-formation suitability
    -> future geological regions / strata / cave-network authorship
    -> future materials
    -> backend realization
~~~

## Subsurface coordinate

AUTH-0022 introduces SkyIslandSubsurfacePosition.

Horizontal coordinates remain island-local Skyforge world units.

Vertical position is represented by semantic depthFraction:

- 0 — immediately beneath the authored upper surface;
- 1 — deep interior / underside zone.

This is intentionally not a backend Y coordinate.

The cave-carving implementation lane may later establish a realization transform between semantic depth and physical exact-volume coordinates, but the authorship layer does not depend on that mechanism.

## Continuous geology rather than taxonomy

AUTH-0001 deliberately represented geological character with continuous physical tendencies instead of prematurely selecting a rock taxonomy.

AUTH-0022 preserves that decision.

The first five subsurface fields are:

### Bulk competence

Local tendency for the island material to remain mechanically coherent.

It is driven primarily by descriptor rock competence, with coherent structural variation, depth strengthening, and local fracture weakening.

### Fracture intensity

Local tendency toward joints, cracks, and structurally weakened corridors.

It responds to:

- inverse rock competence;
- relief-associated structural stress;
- erosion maturity;
- surface/edge weathering near shallow depths;
- coherent three-dimensional structural variation.

### Connected permeability

Local tendency for void/fracture space to form connected flow pathways.

It derives from descriptor permeability, fracture intensity, and coherent connectivity structure.

### Groundwater potential

Local tendency for subsurface water presence or sustained flow.

It derives from:

- island hydrological potential;
- current surface moisture;
- connected permeability;
- semantic depth;
- coherent groundwater structure;
- exposure and edge losses.

This is an authored hydrogeological tendency, not a literal fluid simulation.

### Void-formation potential

Local suitability for persistent internal voids/caverns.

It is deliberately derived from geology rather than generated as an independent cave noise mask.

Contributing causes are:

- fracture intensity;
- connected permeability;
- groundwater potential;
- a moderate stable-rock competence band;
- a broad mid-depth preference.

The result answers where cave formation is semantically plausible. It does not decide exact cave topology, tunnel radius, Minecraft carvers, or blocks.

## Ownership

Geological samples exist only beneath the current AUTH-0020/AUTH-0021 naturalized island domain.

A point horizontally outside current semantic ownership returns an explicit unowned zero sample.

This prevents geological meaning from silently extending into the old nominal circular envelope.

## Coherence

The first geological fields use deterministic smooth three-dimensional lattice variation as bounded internal structure.

Noise remains subordinate to meaning.

The causal order is:

~~~text
island identity
    -> descriptor geological priors
    -> current surface/domain semantics
    -> coherent subsurface variation
    -> geological field meaning
~~~

not:

~~~text
3D noise
    -> caves
    -> attempt to infer geology afterward
~~~

## Evidence

The authorship-subsurface-geology-v1 corpus selects deterministic representatives spanning:

- high rock competence;
- low rock competence;
- high permeability;
- high hydrological potential;
- high erosion maturity;
- a strong SPINE morphology representative.

Each specimen contains:

- SHALLOW COMPETENCE — plan view at depth 0.15;
- FRACTURE SECTION — x/depth cross-section through z=0;
- GROUNDWATER SECTION — x/depth cross-section through z=0;
- VOID SUITABILITY — x/depth cross-section through z=0.

manifest.csv records descriptor geological priors and field statistics.

## Acceptance gate

Reject AUTH-0022 if:

- geological samples exist outside current naturalized ownership;
- fields resemble high-frequency unstructured static;
- descriptor rock competence/permeability cease to matter;
- groundwater is spatially unrelated to hydrology/moisture;
- void suitability becomes a standalone random cave mask;
- every island produces nearly identical subsurface structure;
- the layer depends on Minecraft, NeoForge, block states, carver APIs, or backend Y coordinates.

## Parallel implementation boundary

SF-IMP-0061 is allowed to establish exact-volume native cave-carving mechanics independently.

AUTH-0022 does not instruct that implementation which exact cells to carve.

A later integration milestone may map authored void/cave semantics into a backend-neutral cave topology or density field after both sides are mature enough to compose cleanly.

## Next milestone

If AUTH-0022 is accepted, the next geological milestone should give these scalar fields mesoscale structure: geological regions, coherent fracture corridors, aquifer bodies, and/or authored cave-network topology.

Named material/rock realization should remain downstream until the semantic geological structure is sufficiently expressive.
