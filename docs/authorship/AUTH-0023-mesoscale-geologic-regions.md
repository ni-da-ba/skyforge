# AUTH-0023 — Mesoscale Geological Regions

AUTH-0023 converts the continuous subsurface tendencies accepted in AUTH-0022 into connected mesoscale geological systems.

The milestone remains backend-neutral and does not author exact cave tubes, block materials, Minecraft features, or placement instructions.

## Dependency

~~~text
SkyIslandDescriptor
    -> AUTH-0022 continuous subsurface geology
        -> bulk competence
        -> fracture intensity
        -> connected permeability
        -> groundwater potential
        -> void-formation potential
    -> AUTH-0023 mesoscale geological systems
        -> fracture corridors
        -> aquifer bodies
        -> void-prone domains
    -> future cave topology / geological materials
    -> backend realization
~~~

## Why a region layer exists

AUTH-0022 answers what a point in the island interior tends to be like. That is necessary but insufficient for authored underground geography.

Caves, groundwater systems, mineralization, strata disruption, and underground ecology eventually need mesoscale objects that can be reasoned about as systems rather than isolated scalar samples.

AUTH-0023 therefore introduces connected region plans.

## Coarse three-dimensional planning domain

The first planner evaluates a deterministic 25 x 13 x 25 island-local lattice.

The horizontal domain spans nominalRadius but only positions inside current AUTH-0020/AUTH-0021 naturalized ownership are active.

The vertical axis uses AUTH-0022 semantic depthFraction.

The lattice is a planning representation, not a voxelization contract.

## Overlapping geological systems

Geological region kinds are intentionally non-exclusive.

A cell may participate in a fracture corridor, aquifer body, and void-prone domain simultaneously.

This is important because the most interesting cave-forming conditions often occur precisely where geological systems overlap.

### Fracture corridors

AUTH-0023 introduces a small deterministic family of oblique structural corridors.

Corridor count is controlled by erosion maturity and inverse rock competence.

The corridors are broad planes with deterministic strike, offset, depth drift, and width.

They do not become fracture regions by geometry alone. Actual fracture-region membership blends corridor support with AUTH-0022 fracture intensity, so a nominal structure is only expressed where the island geology supports it.

### Aquifer bodies

AUTH-0023 gives potential aquifers a small deterministic family of broad oblique hydrogeological lenses with authored horizontal extent and semantic-depth thickness.

Lens geometry alone is insufficient. Aquifer membership derives jointly from lens support, AUTH-0022 groundwater potential, and connected permeability.

High hydrological potential without permeability is therefore not sufficient by itself, and high permeability without water does not guarantee a saturated body. This localization prevents a generally wet or permeable island from becoming one undifferentiated island-wide aquifer.

### Void-prone domains

Void-prone regions derive from AUTH-0022 void-formation potential, expressed fracture membership, aquifer membership, and a broad mid-depth preference.

This is still not cave topology.

It identifies connected parts of the island interior where persistent natural void systems are geologically plausible.

## Connected-component filtering

Each geological kind is independently flood-filled through face-adjacent planning cells.

Components smaller than five cells are discarded.

This prevents threshold noise from becoming authored geological objects.

Region identifiers are deterministic within each geological kind.

## Evidence

The authorship-mesoscale-geology-v1 corpus selects representatives for high rock competence, low rock competence, high permeability, high hydrological potential, high erosion maturity, and strong SPINE morphology.

Each specimen renders orthographic maximum-membership projections:

- FRACTURE SYSTEM — x/z projection through all depths;
- AQUIFER SYSTEM — x/z projection through all depths;
- VOID DOMAINS — x/depth projection through all z;
- COMPOSITE — x/depth projection showing overlap between the three systems.

The projections are evidence views only. They are not the stored representation.

manifest.csv records structural-corridor count, region counts, total participating cells, and largest connected-region size for every geological kind.

## Acceptance gate

Reject AUTH-0023 if regions occur outside current naturalized ownership, fracture systems reduce to scattered threshold specks, every island receives the same number or geometry of structural corridors, aquifer bodies ignore groundwater/permeability common causes, void-prone regions become independent random cave masks, connected components are tiny or overwhelmingly fill the whole interior, SPINE ownership is lost in plan projections, obvious grid-aligned rectangular geology reappears, or the region model depends on Minecraft, NeoForge, carvers, features, or block materials.

## Parallel implementation boundary

SF-IMP-0062 virtualizes native underground decoration inside exact Skyforge volumes.

AUTH-0023 does not select Minecraft decoration, invoke placed features, or depend on that runtime.

Later work may let backend adapters condition native or Skyforge-authored underground features on these geological systems.

## Next milestone

If AUTH-0023 is accepted, the next geological step should be cave-system topology, not more scalar tuning.

A future planner should be able to select and connect void-prone domains through fracture/aquifer structure into a small number of explainable cave systems while preserving the distinction between semantic cave topology and backend carving.
