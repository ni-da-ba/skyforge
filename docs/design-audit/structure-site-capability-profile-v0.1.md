# Structure Site Capability Profile v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design proposal. Not yet an accepted ADR.

## Purpose

Give semantic structure placement a backend-neutral way to compare candidate islands/sites without:

- knowing Minecraft structure identities;
- fully probing every block for every candidate;
- mistaking broad support bounds for exact usable structure space.

The profile answers:

> What kinds of structures could this authored site plausibly support?

It does **not** answer:

> Is this concrete Minecraft structure definitely valid here?

That remains an exact compatibility/admission problem.

## Evidence hierarchy

Structure placement should use three evidence levels.

### Level 1 — Proof-backed coarse bounds

Existing or future analytical evidence such as:

- certified horizontal support envelope;
- certified upper support;
- certified underside depth;
- exact descriptor/provider support capabilities.

These can prove that some candidates are impossible or that broad outer bounds are adequate.

They cannot by themselves prove:

- connected interior structure volume;
- local flatness;
- a specific cliff patch;
- a cavern;
- waterbody shape;
- a concrete structure footprint.

### Level 2 — Authored semantic capability

Derived from already-planned Skyforge systems:

- surface morphology;
- local relief;
- hydrology;
- geology/aquifer state;
- cave topology;
- waterbody planning;
- settlement/ecology context.

This provides meaningful site-ranking information.

It may be exact relative to semantic planning cells, but should not automatically be treated as concrete Minecraft structure proof.

### Level 3 — Exact realized compatibility

After a specific structure/site pairing exists, evaluate the actual requirement against the compiled exact Skyforge volume and concrete structure geometry.

This remains authoritative for realization.

## Candidate neutral model

Conceptually:

~~~text
StructureSiteCapabilityProfile {
    siteIdentity
    ownerIdentity
    scaleCapacity
    surfaceCapabilities
    interiorCapabilities
    cliffCapabilities
    waterCapabilities
    hydrologicContext
    geologicContext
    caveContext
    accessContext
    compositionContext
    proofMetadata
}
~~~

The exact API should remain smaller than this first conceptual inventory.

## Scale capacity

Possible neutral information:

- conservative horizontal support radius;
- upper support capacity;
- underside/depth capacity;
- approximate owned surface-area class;
- approximate connected-mass class.

Important:

> Conservative outer support bounds are not equivalent to connected usable volume.

A 300-block support envelope can still contain a shape unsuitable for a large embedded structure.

## Surface capabilities

Useful site-level observations:

~~~text
broad low-relief patch
moderate-relief buildable patch
ridge/shoulder patch
basin-floor patch
edge/overlook patch
agricultural surface potential
landing/airfield approach potential
~~~

These should be represented as capability candidates with location/orientation/extent, not one global island Boolean.

A large Massif might simultaneously contain:

- one buildable shoulder;
- one steep unusable face;
- one cliff-dock candidate;
- one high lookout.

## Interior capabilities

Useful neutral candidates:

~~~text
connected solid-host span
available cover depth
embedded occupancy candidate
large cavern candidate
deep semantic region
low-exterior-exposure region
~~~

For structure fitting, the important quantity is not total island volume but whether one coherent local region can host the requirement.

### Coarse versus exact interior tests

Coarse planners may identify candidate interior regions from:

- descriptor/support scale;
- semantic depth transform;
- cave/geology planning;
- occupancy samples.

Final proof should use exact compiled ownership/structure geometry.

## Cliff capabilities

Cliff/underside structures require local oriented features.

Candidate record:

~~~text
CliffCapability {
    anchor
    outwardDirection
    verticalSpan
    horizontalSpan
    rockContactQuality
    approachClearance
    nearbySurfaceAccess
}
~~~

This capability should emerge from morphology.

Do not infer global "cliff-capable morphology family" and then assume a usable site exists.

## Water capabilities

Future ocean/large-water structures need local water-volume evidence.

Useful attributes:

~~~text
surface footprint
depth
open-water fraction
seafloor/support
shoreline/cliff adjacency
submerged clearance
connection to broader marine habitat
~~~

A biome label is insufficient.

## Hydrologic context

Current Skyforge hydrology can eventually supply structure site preferences such as:

- retained standing water;
- coherent channel proximity;
- edge outflow/waterfall;
- riparian context;
- dry/interior surface;
- drainage conflict.

Structures should consume those semantics rather than erase them.

Examples:

- settlement scores positively near useful water but negatively inside a channel/standing-water footprint;
- watermill/bridge requires channel adjacency;
- cliff settlement may value an edge fall;
- airfield penalizes active channel crossing.

## Geologic context

AUTH geology/material systems can provide:

- competence/weakness;
- fracture support;
- aquifer/groundwater potential;
- void-prone regions;
- mineral-bearing structural units;
- lithologic assemblage/contact context.

Structure roles then select what they need.

Examples:

- mineshaft prefers mineral-bearing geology;
- dry dungeon penalizes strong aquifer;
- cave shrine may prefer fracture/cave support;
- Ancient City prefers deep/anomalous cavern context.

The structure should not create the geology merely to justify itself unless an explicitly exceptional semantic rule says otherwise.

## Cave context

Useful site capabilities:

~~~text
near cave system
inside large cave-compatible region
sealed deep region
cave breach opportunity
surface-connected cave opportunity
water-influenced cave context
~~~

These are relationships to AUTH cave semantics.

They should not be hard-coded from Minecraft cave blocks.

## Access context

A structure's play value depends on reaching it.

Candidate access types:

~~~text
surface walk
ridge route
cave route
shaft
cliff entrance
dock
air approach
water approach
underside approach
~~~

A site may satisfy physical occupancy but be rejected or scored lower because access would be incoherent.

## Composition context

World-scale composition should remain part of site ranking.

Possible factors:

- distance from other major landmarks;
- horizon clutter;
- island stacking/sky exposure;
- route significance;
- settlement/faction geography;
- province destination rhythm.

This prevents every technically suitable large island from accumulating several unrelated major structures.

## Requirements versus capabilities

A neutral structure requirement and a site capability profile should meet through generic matching.

Example:

~~~text
StrongholdRequirement:
    interior span >= LARGE
    cover >= MODERATE
    exterior contradiction = forbidden

Candidate Site A:
    interior span = SMALL
    -> reject cheaply

Candidate Site B:
    interior span = LARGE_CANDIDATE
    cover = HIGH
    -> exact pairing worth testing
~~~

Minecraft's concrete Stronghold identity remains below the neutral adapter boundary.

## Candidate site features, not island labels

Avoid reducing a complex island to:

~~~text
supportsVillage = true
supportsDungeon = true
~~~

Prefer local candidates:

~~~text
surfacePatch #4
interiorRegion #2
cliffPatch #7
waterVolume #1
~~~

This permits several structures to use different parts of one large island and enables conflict detection.

## Capability synthesis timing

A single profile cannot be produced entirely before world planning.

Use staged synthesis.

### Stage A — descriptor/provider preflight

Available before graph compilation when exact descriptors/seeds are known.

Useful for:

- support-envelope bounds;
- obviously insufficient scale;
- provider certification/capability declarations.

This is compatible with the direction demonstrated by AUTH-0053 support-reservation preflight.

### Stage B — semantic authored profile

After enough morphology/geology/hydrology planning exists.

Useful for:

- surface patches;
- cave/geology relationships;
- aquifer/water context;
- local cliff/water candidates.

### Stage C — exact pairing proof

After specific concrete structure geometry/site pairing.

No earlier profile substitutes for this.

## Stronghold example

A Tier-3 Stronghold signal needs a host near its topology anchor.

Search:

1. Level-1 filter rejects islands whose support/scale is obviously too small.
2. Level-2 profile ranks candidate interior regions on nearby islands.
3. If no island has a credible interior candidate, request conditioned structure-required terrain.
4. The new island is generated from ordinary compatible morphology distribution.
5. Level-2 profile identifies an interior region.
6. Concrete native/modded Stronghold is generated/probed.
7. Level-3 exact admission proves occupancy/support/cover.

If exact proof fails, Tier 3 requires deterministic re-plan, not silent loss.

## Trial Chamber example

Trial Chamber can search a much wider regional candidate set.

If several islands qualify:

- prefer good interior capacity;
- prefer acceptable cave relationship;
- penalize strong aquifer conflict;
- preserve regional structure spacing.

If none qualifies, ordinary candidate may disappear unless regional frequency policy promotes one intent.

## Village example

Village matching should use multiple surface candidates rather than one island-wide flatness metric.

A settlement planner may combine:

- several buildable patches;
- road connectivity;
- water access;
- dock/air access;
- agriculture.

This allows non-Tableland settlements without flattening whole islands.

## Ancient City example

Buried Ancient City requires a rare high-capacity profile:

- enormous interior/cavern candidate;
- Deep Dark semantic depth;
- low ordinary cave/ecology conflict;
- adequate surrounding support.

If selected as a Tier-2 landmark and no candidate exists, conditioned terrain authors a new large island while preserving morphology diversity.

## Failure handling

Capability matching is advisory until exact proof.

A false positive is acceptable if:

- exact proof catches it;
- fallback/re-plan is deterministic;
- no partial world mutation occurs.

A false negative is more damaging because it reduces valid morphology diversity.

Therefore coarse capability filters should be conservative about rejection unless backed by proof.

## Telemetry/evidence

Future structure-planning evidence should distinguish:

~~~text
candidates considered
coarse rejected
semantic rejected
selected site
exact proof passed/failed
fallback attempts
terrain-required replan
final morphology family/provider
~~~

This is especially important for detecting morphology collapse, e.g. all Strongholds accidentally selecting Massif hosts.

## Acceptance principle

> Structure site capability is staged evidence about **what a place can support**. It is never permission to replace exact geometry proof with a heuristic.
