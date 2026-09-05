# AUTH-0047 — World-Space Authored-Material Sampling

AUTH-0047 defines the backend-neutral bridge from one explicit AUTH-0046 authored-island realization association and one abstract Skyforge world-space point to the accepted native semantic material realization chain.

It still stops before concrete backend material identity and block placement.

## Dependency

~~~text
AUTH-0046 explicit authored-island <-> realized-volume association
        +
world-space Coordinate3
        +
AUTH-0042 semantic material decision provider
        ↓
AUTH-0047 world-space authored-material sampler
        ↓
associated compiled-volume center translation
        ↓
island-local x/z + physical Y
        ↓
AUTH-0027 authoritative semantic-depth transform
        ↓
AUTH-0039 material-binding request
        ↓
AUTH-0043 expression allocation
        ↓
AUTH-0044 final semantic winner
        ↓
AUTH-0045 stable application envelope/key
        ↓
future adapter-owned concrete material binding/application
~~~

AUTH-0047 introduces no new geology, cave system, material role, physical surface, or backend block vocabulary.

## Explicit association is mandatory

SkyIslandWorldAuthoredMaterialSampler is constructed from one exact SkyIslandAuthoredRealizationAssociation.

It does not:

- search SkyIslandWorldCatalog;
- choose a nearest island;
- rank overlapping volumes;
- inspect chunk ownership;
- infer identity from geometry;
- infer identity from bounds;
- infer identity from seeds.

AUTH-0046 remains authoritative for which native-authored island is represented by which compiled physical world volume.

A later multi-island world-query layer may choose which accepted association owns a world point, but that is a separate composition concern.

## World coordinate

AUTH-0047 consumes kernel Coordinate3.

Coordinate3 is an abstract Skyforge world-space coordinate:

- X: east/west;
- Y: vertical, positive upward;
- Z: north/south.

It is not:

- BlockPos;
- a Minecraft integer voxel;
- a chunk-relative coordinate;
- a dimension-qualified coordinate.

The sampler remains backend-neutral.

## Horizontal transform

The associated compiled SkyIslandVolumeDescriptor owns the physical world center:

~~~text
localX = worldX - realized.centerX
localZ = worldZ - realized.centerZ
~~~

No rotation or scale is introduced by AUTH-0047.

AUTH-0046 already requires exact nominal-radius agreement for the direct local frame.

If a future physical realization requires rotation or non-unit scale, that transformation must become an explicit reviewed contract rather than an implicit sampler heuristic.

## Vertical transform

World Y is not converted through a bounding-box interval.

The sampler constructs the associated SkyIslandCompiledVolumeColumnField and uses the accepted AUTH-0027 SkyIslandSemanticDepthRealizationTransform.

At one local horizontal position:

~~~text
world Y
    -> authoritative compiled upper/underside column
    -> semantic depthFraction
~~~

A point above the upper surface, below the underside, or in a horizontal location with no positive physical column has no semantic subsurface position.

AUTH-0047 therefore preserves local physical thickness and variable upper/underside geometry exactly.

## Three ownership states

AUTH-0047 preserves three different absence states.

### 1. Outside physical interior

No authoritative positive compiled column/depth contains the point.

The result has:

- no realized subsurface position;
- no semantic position;
- no AUTH-0044 realization;
- no AUTH-0045 application.

### 2. Physical interior but outside native authored ownership

The compiled physical volume contains the point, but the mapped semantic point lies outside the native naturalized authored domain.

The result retains the semantic mapping and non-material AUTH-0044 state, but:

- authoredOwned = false;
- materialPresent = false;
- no AUTH-0045 application exists.

AUTH-0047 does not expand native authorship merely because a legacy/compiled physical graph has solid there.

### 3. Native-authored interior

The native semantic chain owns the mapped point.

It may be:

- authored material;
- AUTH-0030 authored cave void.

Authored cave void remains non-material and produces no AUTH-0045 application.

Authored material proceeds through the full accepted semantic material chain.

## AUTH-0042 decision injection

AUTH-0044 cannot produce a semantic winner without the accepted AUTH-0042 material-resolution decision for every AUTH-0039 request used by the point.

AUTH-0047 introduces SkyIslandMaterialResolutionDecisionProvider.

The provider receives the exact backend-neutral SkyIslandMaterialBindingRequest required at the point.

It must return a SkyIslandMaterialResolutionDecision for that exact request.

The sampler rejects:

- null decisions;
- decisions for a different request;
- inconsistent decisions for one stable binding key within one sample.

The provider does not return a concrete backend material.

This keeps the separation:

~~~text
semantic capability decision
        !=
concrete backend material identity
~~~

A Minecraft adapter may later implement the provider using its accepted semantic material capability/resolution policy while retaining BlockState/registry identity outside skyforge-world.

## Final-winner authority

AUTH-0047 uses SkyIslandMaterialExpressionRealizer unchanged.

Therefore the world-space sample preserves AUTH-0044 semantics:

- one structural matrix winner;
- optional conditioned claims;
- conditioned final winner overrides structural winner when active.

The AUTH-0045 application envelope is derived from the exact final winner.

World-space placement does not alter winner selection.

## Application-key output

For authored material, SkyIslandWorldAuthoredMaterialSample exposes the AUTH-0045 application key.

That key is the exact AUTH-0038 stable binding key carried by the AUTH-0044 final winner.

AUTH-0047 does not look that key up in a concrete material table.

Therefore:

~~~text
world point
    -> stable semantic application key
~~~

is the end of the native authorship responsibility in this milestone.

## World/local floating-point contract

World coordinates are authoritative inputs.

AUTH-0047 performs ordinary binary64 translation:

~~~text
localX = worldX - centerX
localZ = worldZ - centerZ
~~~

An arbitrary binary64 semantic coordinate translated into world space and then subtracted from the center is not guaranteed to recover the original low bits exactly. Floating-point addition may already have rounded them away.

AUTH-0047 therefore does not claim a mathematically impossible bit-exact inverse for arbitrary double coordinates.

Instead:

- the recovered local coordinate is the authoritative semantic coordinate for that world query;
- semantic -> world -> semantic diagnostics must remain within normal binary64 translation error;
- the world center is not mixed into authored seeds, binding identity, or material-selection policy;
- translation contributes no semantic effect beyond the recovered coordinate itself.

For corpus round trips, horizontal error is measured against a tolerance derived from the ULPs of the world coordinate and realized center. Semantic-depth round-trip error remains bounded independently.

This is placement, not a new authorship source.

## Direct-chain equivalence

AUTH-0047 must agree exactly with direct native evaluation at the **recovered semantic point**.

For a world query:

~~~text
world point
    -> AUTH-0027 recovered semantic point
    -> AUTH-0047 material chain
~~~

the result is compared with:

~~~text
same recovered semantic point
    -> direct AUTH-0039 / 0043 / 0044 evaluation
~~~

The two paths must agree on:

- owned state;
- authored cave void;
- material-present state;
- final winner binding key;
- conditioned-winner state;
- AUTH-0045 application key.

A semantic -> world -> semantic diagnostic may also compare the recovered coordinate with its original test input, but infinitesimal binary64 input drift is not itself a material-semantic failure.

## Evidence

The AUTH-0047 corpus uses the six canonical native-authorship representatives.

For each explicit AUTH-0046 association, the evidence samples multiple semantic-depth planes, maps them through the authoritative compiled physical columns into actual world-space Coordinate3 points, and queries AUTH-0047.

The corpus records:

- physically mappable samples;
- native-owned samples;
- authored-void samples;
- material samples;
- AUTH-0045 applications;
- conditioned final winners;
- horizontal semantic round-trip error;
- semantic-depth round-trip error;
- local-frame mismatches beyond ULP-derived tolerance;
- direct-chain winner mismatches at the recovered semantic point;
- application-key mismatches;
- unique application keys.

A compact atlas shows, at semantic depth 0.52:

- DIRECT NATIVE — AUTH-0044 final winner evaluated directly in semantic coordinates;
- WORLD SAMPLE — final winner after semantic -> world -> AUTH-0047;
- EQUIVALENCE — green for exact winner/absence agreement, red for failure;
- APPLICATION KEY — stable AUTH-0045 binding-key coherence domains.

The visual evidence is diagnostic. AUTH-0047 changes no authored morphology.

## Acceptance gate

Reject AUTH-0047 if:

- the sampler discovers or infers its association instead of consuming AUTH-0046;
- world X/Z are interpreted as native local coordinates without the associated placement transform;
- world Y is normalized against bounding-box Y instead of the authoritative compiled column;
- physical air acquires native semantic/material state;
- compiled solid outside native authored ownership becomes authored material;
- AUTH-0030 authored cave void receives material/application state;
- the decision provider may substitute a decision for a different request;
- world placement is mixed into native authored identity or selection policy;
- semantic/world round-trip drift materially exceeds binary64 translation precision;
- AUTH-0047 differs from direct native evaluation at the recovered semantic point;
- conditioned final winners fall back to structural matrix;
- AUTH-0045 application key differs from AUTH-0044 final winner key;
- concrete backend material identity enters the sampler;
- Minecraft/NeoForge types enter skyforge-world.

## Parallel implementation boundary

AUTH-0047 changes no Minecraft:

- block placement;
- registry lookup;
- chunk traversal;
- mutation fence;
- carver behavior;
- persistence;
- save/reload behavior.

The existing implementation lane may continue independently.

After AUTH-0047 is accepted, the implementation lane has a stable native semantic query boundary available for a later material-placement proof:

~~~text
BlockPos converted to abstract Skyforge world Coordinate3
    + explicit accepted association
    -> AUTH-0047 application key
    -> adapter-owned concrete binding
    -> existing exact-volume placement/mutation authority
~~~

That implementation work should remain separate from authorship.

## Next milestone

If AUTH-0047 is accepted, the native material chain is complete through world-space semantic application identity.

The next Skyforge-native authorship milestone should move upward from one-island sampling to **multi-island authored realization composition/ownership**: how a world-space query selects among explicit AUTH-0046 associations when conservative compiled bounds overlap or stacked island volumes coexist.

That layer should:

- use explicit association identities;
- preserve stacked-volume isolation;
- resolve overlap deterministically;
- avoid nearest-center heuristics where physical ownership can be queried exactly;
- remain backend-neutral;
- produce at most one authoritative authored association for one world point.

It should still stop before concrete backend material placement.
