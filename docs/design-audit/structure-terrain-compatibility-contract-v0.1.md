# Structure-to-Terrain Compatibility Contract v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design decision. Clarifies the structure reservation/relocation policy; not yet an accepted ADR.

## Core correction

Structure-seeded terrain does **not** normally mean:

~~~text
structure shape
-> generate terrain around that shape
~~~

The preferred model is:

~~~text
structure intent
-> declare terrain requirements
-> select/generate any Skyforge island descriptor satisfying those requirements
-> let normal morphology authorship produce the island
-> verify realized support/occupancy compatibility
-> realize the structure
~~~

A structure usually constrains the **admissible terrain space**, not the visible island shape.

This preserves Skyforge morphology diversity and prevents islands from visibly looking authored around Minecraft structures.

## Relationship to accepted morphology work

Skyforge already has accepted primary morphology families:

- Massif;
- Tableland;
- Spine;
- Basin;
- Lobed;

and accepted hybridization between primary families.

Those families define materially different suspended landforms. Structure compatibility should therefore operate as a filter over descriptor/provider outcomes rather than introduce structure-specific morphology recipes by default.

Similarly, AUTH-0051 separates broad backend query reservations from proof-grade realized support envelopes. Structure requirements should consume proof/capability information of that kind where useful rather than assume nominal descriptor dimensions exactly equal realized geometry.

## Two separate questions

For every structure, distinguish:

### 1. What terrain must be physically possible?

Examples:

- minimum horizontal support span;
- minimum usable interior volume;
- minimum cover above an embedded structure;
- minimum underside depth;
- maximum local relief under a surface structure;
- sufficient cliff face;
- sufficient water depth;
- required open-air clearance.

These are **hard compatibility constraints**.

### 2. What terrain would make the structure compositionally appropriate?

Examples:

- defensible terrain for an illager fort;
- broad agricultural area for a settlement;
- dramatic basin for an exposed Ancient City;
- isolated massif for a monastery;
- mineral-rich geology for a mineshaft.

These are **semantic preferences**, not necessarily hard geometry constraints.

Do not collapse the two.

## Structure terrain requirement

Candidate neutral concept:

~~~text
StructureTerrainRequirement {
    minimumHorizontalSpan
    minimumInteriorSpan
    minimumInteriorHeight
    minimumCoverDepth
    minimumSupportDepth
    maximumSurfaceRelief
    requiredSurfaceArea
    allowedExposure
    requiredAccessKinds
    clearanceRequirement
    waterRequirement
    cliffRequirement
}
~~~

This is intentionally descriptive rather than morphology-specific.

A Stronghold requirement should not say:

~~~text
morphology = MASSIF
~~~

It should say something closer to:

~~~text
minimum interior dimensions = ...
minimum cover = ...
minimum surrounding support = ...
~~~

Then Massif, Tableland, Spine, Basin, Lobed, or a hybrid may all qualify if their realized geometry satisfies the requirement.

## Morphology compatibility filtering

Structure-seeded terrain should conceptually work as:

~~~text
structure requirement
        |
        v
candidate island descriptor distribution
        |
        v
reject descriptors/providers that cannot satisfy coarse requirements
        |
        v
compile candidate morphology normally
        |
        v
evaluate proof-grade support / occupancy compatibility
        |
        +--> pass -> candidate remains eligible
        |
        +--> fail -> try another descriptor/site
        |
        v
normal Skyforge composition ranking
        |
        v
selected island
~~~

The structure narrows the search space without replacing the morphology generator.

## Constraint strength

Constraints should be as weak as correctness permits.

Bad requirement:

~~~text
Stronghold island must be 220 x 220 x 120 blocks.
~~~

Better:

~~~text
Stronghold reservation requires:
- enough connected interior support for its generated occupancy envelope;
- required cover above critical pieces;
- no exterior/void contradiction;
- a valid access relationship;
- sufficient safety margin for structure variation.
~~~

This lets different morphology families solve the same physical problem differently.

## Stronghold example

Stronghold is Tier 3 / progression-critical.

Its structure intent may require a large interior envelope, but should remain morphology-agnostic.

Potential compatible outcomes:

### Massif

A thick compact massif naturally provides deep central volume.

### Tableland

A broad tableland may contain a Stronghold beneath its plateau.

### Spine

A sufficiently large spine can contain an elongated Stronghold, perhaps with the structure rotated/oriented to maximize fit.

### Basin

A broad basin island can contain the Stronghold below the basin floor or within deeper rim mass.

### Lobed / hybrid

A large connected lobed landform may contain the Stronghold in one central/major shoulder volume.

The requirement is:

> contain the Stronghold coherently.

It is not:

> look like the Stronghold.

If no existing/planned island qualifies, structure-seeded terrain samples from the ordinary permitted morphology vocabulary under stronger minimum-size/support constraints.

## Trial Chamber example

Trial Chambers should usually impose less authority.

Requirement may include:

- sufficient connected interior volume;
- moderate cover;
- room for critical chamber pieces;
- no void exposure except approved access intersections.

Many morphology families can satisfy this.

A thin/small island simply fails compatibility.

The planner may relocate the Trial Chamber to another island rather than changing the morphology of the first island.

Only a promoted regional-frequency intent should cause the planner to request a new qualifying island.

## Buried Ancient City example

An Ancient City requires much more than raw structure occupancy.

Hard requirements:

- enormous usable interior/cavern span;
- surrounding support;
- Deep Dark-compatible semantic depth;
- sufficient vertical/horizontal clearance.

Semantic preferences may include:

- very large mature landform;
- low ordinary settlement pressure;
- deep/anomalous geology;
- sparse ordinary cave ecology.

Even here, the city should not dictate one visible morphology family.

Possible hosts could include:

- a massive Massif;
- a huge Tableland;
- a broad Basin;
- sufficiently large hybrid forms.

The city/cavern constraint narrows scale and internal capacity, not necessarily silhouette.

## Exposed Ancient City exception

The exposed Ancient City is intentionally different.

Its world role includes being visible as a horizon-scale landmark. Therefore it may legitimately add visible composition preferences such as:

- broad exposed support;
- basin/caldera tendency;
- controlled approach/landing geometry;
- partial embedding around city edges.

Even then, prefer **biasing/selecting compatible morphology families and parameters** over constructing a literal city-shaped island.

This is one of the minority cases where structure semantics may influence visible morphology.

## Village example

A village should normally require:

- enough usable surface area;
- tolerable local relief;
- water/agricultural or import viability;
- access to docks/landing routes where appropriate.

It should not require a perfectly flat rectangular plateau.

Compatible results may include:

- broad Tableland settlement;
- terraced Massif shoulder settlement;
- Basin-rim settlement;
- elongated Spine settlement connected along the ridge;
- multi-lobed settlement distributed across shoulders.

The settlement planner should adapt its network/layout to terrain within limits.

Only major Tier-2 settlement hubs may bias island generation toward broader buildable surfaces.

## Illager fort example

A fort may prefer:

- defensibility;
- commanding visibility;
- sufficient surface footprint;
- approach control.

Those are semantic/functional constraints.

They do not require one "fort island" morphology.

A fort can occupy:

- a Massif summit;
- a Tableland plateau;
- a Spine ridge;
- one Lobed shoulder.

This helps hostile civilization feel opportunistic and geographically adaptive rather than procedurally stamped.

## Mineshaft example

Mineshaft compatibility should be driven primarily by:

- mineral/geologic value;
- connected rock volume;
- historical extraction semantics;
- enough interior support.

Morphology family is secondary.

A Spine may produce a long linear mine; a Massif a deep central network; a Lobed island several extraction branches.

This is desirable variation.

## Cliff/underside structures

Cliff docks, hanging warehouses, and underside structures are more locally geometric.

Even here the structure should ask for a **local feature**, not a whole-island morphology:

~~~text
need cliff patch:
- minimum height
- minimum width
- suitable local normal
- rock contact
- free approach volume
~~~

Any island morphology that produces such a local cliff can qualify.

The structure should not request "generate a Spine island" merely because Spine often produces useful cliffs.

## Coarse prefilter versus exact proof

Use two stages.

### Coarse semantic/capability filter

Cheaply reject clearly unsuitable island descriptors before expensive realization.

Possible inputs:

- nominal radius/scale;
- certified support envelope;
- morphology-provider capabilities;
- estimated surface-area class;
- semantic depth class;
- water/cliff capability.

This stage may be conservative.

### Exact realized compatibility

After a candidate island is compiled/planned, test the actual structure reservation against:

- exact-volume ownership;
- realized support;
- occupancy;
- cover;
- clearance;
- local relief;
- exterior contradictions.

This stage decides whether the concrete site is genuinely usable.

The coarse stage should never be promoted into proof merely because it is convenient.

## Structure-conditioned sampling

When a Tier-2 or Tier-3 structure must create terrain, do not generate arbitrary islands until one happens to fit.

Instead sample from a **conditioned ordinary island distribution**.

Conceptually:

~~~text
normal island descriptor distribution
        |
        + structure requirements
        v
conditioned candidate distribution
        |
        v
ordinary morphology provider / hybrids / enrichment
~~~

Examples of conditioning:

- raise minimum nominal radius;
- raise minimum underside-depth capacity;
- require certified support capability;
- require sufficient waterbody capacity;
- require cliff-capable local geometry;
- restrict obviously incompatible scale classes.

Do **not** normally:

- select a unique morphology family;
- flatten terrain to exact structure footprint;
- copy structure bounding boxes into island shape;
- create cuboid support volumes.

## Diversity requirement

A structure-bearing island population should retain morphological diversity.

For common structure categories, future acceptance should verify that multiple compatible morphology families can host the same semantic structure class when their constraints allow it.

For example:

> A Stronghold-bearing island corpus should not collapse to 100% Massif merely because Massif is the easiest initial implementation.

Likewise:

> Trial Chamber-bearing islands should not become a visually recognizable special island species unless intentionally designed that way.

## Exceptional visible coupling

Direct visible structure-to-morphology coupling should require explicit design justification.

Candidate cases:

- exposed Ancient City;
- giant crater/boss site whose landform is part of the encounter;
- purpose-built airfield island;
- monumental faction capital;
- rare engineered/artificial islands if Skyforge later supports them.

Even these should usually specify semantic morphological tendencies rather than exact geometry.

## Revised interpretation of terrain authority

The earlier levels remain useful if interpreted correctly:

### A — Fit only
Use already-authored terrain.

### B — Accommodation
Minor local support adaptation.

### C — Conditioned morphology
Structure constraints participate in descriptor/site selection and may require local terrain features, but normal morphology authorship remains authoritative.

### D — Structure-required terrain
A new island/waterbody/landform must exist because the structure is required, but the landform is still sampled/authored from the normal compatible morphology vocabulary.

Only explicit exceptional content should cross from Level D into direct visible landform authorship.

## Cross-agent requirement

The authorship lane should eventually be able to consume a backend-neutral terrain requirement without knowing the concrete Minecraft structure ID.

The implementation lane should translate Minecraft/mod structure behavior into that requirement and later verify the realized site.

Desired boundary:

~~~text
Minecraft/mod structure
        |
        v
adapter-derived neutral requirement
        |
        v
Skyforge semantic planner
        |
        v
compatible island descriptor / reservation
        |
        v
normal Skyforge morphology authorship
        |
        v
exact-volume compatibility proof
        |
        v
native/modded structure realization
~~~

## Acceptance principle

The desired invariant is:

> Structures constrain what terrain is acceptable; they do not normally prescribe what the terrain looks like.

A progression-critical structure may force **an island** to exist, but should not ordinarily force **one island morphology** to exist.
