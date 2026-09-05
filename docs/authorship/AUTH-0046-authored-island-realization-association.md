# AUTH-0046 — Authored-Island Realization Association Contract

AUTH-0046 defines the explicit backend-neutral association between one placement-free native-authored Skyforge island and one independently compiled physical world volume.

It exists because the repository currently has two intentionally distinct identity hierarchies that must not be conflated implicitly.

## Dependency

~~~text
native authorship
World -> Province -> Cluster -> Island
        -> SkyIslandIdentity
        -> SkyIslandDescriptor
        -> AUTH-0002 ... AUTH-0045

compiled realization
archipelago -> group -> member
        -> SkyIslandWorldVolumeId
        -> SkyIslandWorldVolume
        -> compiled upper / underside / density graphs

AUTH-0046
        -> explicit authored-island <-> realized-volume association
        -> shared local-frame compatibility
        -> one-to-one association catalog
        -> future world-space authored-material sampling
~~~

AUTH-0046 does not create a new placement planner and does not change either existing hierarchy.

## Why an explicit association is required

AUTH-0001 intentionally made SkyIslandIdentity placement-free.

The older world-volume hierarchy predates the native authorship lane and carries independently compiled placement and geometry identity.

No accepted invariant currently permits Skyforge to infer that one native-authored island corresponds to one world volume from:

- matching or similar seeds;
- morphology family;
- nominal radius;
- list order;
- group/member ordinal;
- nearest center;
- overlapping bounds;
- chunk ownership;
- backend encounter order.

Those properties may be useful diagnostics, but they do not constitute identity.

AUTH-0046 therefore makes the relationship a first-class immutable datum.

## Association schema

SkyIslandAuthoredRealizationAssociation contains:

- schema version;
- the exact SkyIslandDescriptor;
- the exact SkyIslandWorldVolume.

It exposes:

- the native SkyIslandIdentity;
- the realized SkyIslandWorldVolumeId;
- a stable association token containing both identities.

The association does not derive either side from the other.

## Independent root domains

Native authorship identifies a world by:

~~~text
SkyIslandIdentity.worldSeed
~~~

The compiled realization hierarchy identifies an archipelago realization root by:

~~~text
SkyIslandWorldVolumeId.archipelagoRootSeed
~~~

AUTH-0046 does not assume these values are numerically equal.

A future world-composition policy may choose to:

- reuse one root seed;
- derive a realization root from the authored world seed;
- restore a realization root from persisted planning state;
- use another explicit deterministic mapping.

That policy is outside AUTH-0046.

SkyIslandAuthoredRealizationCatalog therefore stores both roots explicitly and validates each side against its own domain.

## Direct local-frame compatibility

The current native authored subsurface chain is evaluated in island-local horizontal coordinates.

The established compiled-volume column adapter also exposes physical columns through island-local horizontal coordinates.

AUTH-0046 permits a direct association only when the authored and realized nominal radii are bit-exact equal.

This follows the accepted AUTH-0027 cave-realization rule and prevents a downstream sampler from silently stretching or shrinking native semantic fields to fit a different physical volume.

AUTH-0046 does not introduce a scale transform.

If scaled authored-to-realized mappings are ever required, they must be separately authored and reviewed.

## Morphology preservation

A schema-2 SkyIslandVolumeDescriptor explicitly declares a semantic morphology family.

When that information exists, AUTH-0046 requires:

~~~text
authored morphology family
    =
realized declared morphology family
~~~

A mismatch is rejected because a realized volume that declares a different primary morphology cannot honestly represent the same authored island without an explicit intervening transformation.

Legacy schema-1 physical descriptors contain no morphology-family field.

AUTH-0046 does not invent one.

A legacy volume may therefore be explicitly associated when the direct local scale matches, but the association makes no claim about an absent morphology declaration.

This is compatibility, not inference.

## One-to-one catalog

SkyIslandAuthoredRealizationCatalog represents one authored world identity domain and one realization-root identity domain.

It enforces:

- every association belongs to the declared authored world seed;
- every realized volume belongs to the declared realization root seed;
- one native SkyIslandIdentity appears at most once;
- one SkyIslandWorldVolumeId appears at most once.

Therefore the catalog forbids:

~~~text
one authored island -> multiple realized volumes
multiple authored islands -> one realized volume
~~~

within one accepted catalog.

Partial catalogs are allowed.

AUTH-0046 does not require every legacy world volume to have native-authored semantics.

## Canonical ordering

Caller list order has no semantic meaning.

The catalog sorts associations by their stable association token before freezing them.

The same association set therefore produces the same catalog order regardless of construction order.

Backends must not use encounter order as identity.

## Stable association token

The canonical token contains both complete identity domains:

- authored world seed;
- Province key;
- Cluster key;
- island key;
- realization root seed;
- realized group identifier;
- group ordinal;
- member ordinal;
- geometry seed.

The token deliberately does not contain:

- world center X/Z;
- suspension elevation;
- chunk coordinate;
- backend registry data;
- Minecraft dimension id;
- concrete material identity.

Placement may change without redefining which authored island and realized-volume identity are associated.

## What AUTH-0046 does not prove

AUTH-0046 does not claim that an arbitrary explicitly associated legacy volume is a perfect geometric realization of all native-authored semantic fields.

It establishes only the identity and direct-frame invariants required before such fields may be sampled against that volume.

In particular, it does not:

- regenerate physical geometry from SkyIslandDescriptor;
- compare detailed shorelines;
- reconcile legacy and native signal algorithms;
- rewrite naturalized ownership;
- change cave geometry;
- choose Minecraft blocks;
- place materials.

Those remain separate milestones.

## Evidence

The AUTH-0046 reference corpus uses the six canonical native authorship representatives:

- competent massif key 2332;
- weak basin key 653;
- permeable lobed key 1051;
- hydrologic representative key 2211;
- eroded tableland key 1439;
- spine key 3670.

For each representative, the corpus creates a schema-2 reference physical volume with:

- the same nominal radius;
- the same declared morphology family;
- independent geometry seed;
- explicit world placement;
- a realization root seed deliberately different from the authored world seed.

Each specimen renders:

- AUTHORED DOMAIN — the native naturalized island-local ownership domain;
- REALIZED DOMAIN — positive physical compiled columns in the same local frame;
- FRAME OVERLAP — overlap and disagreement diagnostics between those two independently defined domains.

The overlap panel is diagnostic.

AUTH-0046 does not require the two independently authored shapes to be pixel-identical; the milestone establishes identity and compatible coordinate scale, not a new geometry compiler.

manifest.csv records both identity domains, both semantic scale/morphology declarations, placement diagnostics, overlap counts, and the stable association token.

## Acceptance gate

Reject AUTH-0046 if:

- an association is inferred rather than supplied explicitly;
- authored and realized nominal radii differ but are silently rescaled;
- a schema-2 realized morphology differs from native authored morphology;
- missing legacy morphology is fabricated;
- the contract requires native world seed to equal realization root seed;
- one authored identity can map to multiple realized-volume identities in one catalog;
- multiple authored identities can map to one realized-volume identity in one catalog;
- association order depends on caller encounter order;
- placement coordinates become part of stable association identity;
- Minecraft, NeoForge, block, registry, or chunk concepts enter the world-layer contract.

## Parallel implementation boundary

AUTH-0046 changes no existing Minecraft or NeoForge behavior.

It does not alter:

- world-volume planning;
- cave/carver execution;
- mutation fences;
- persistence;
- chunk writing;
- material registries;
- block placement.

The implementation lane may continue independently.

## Next milestone

If AUTH-0046 is accepted, AUTH-0047 should define the **world-space authored-material sampling bridge**.

That bridge may consume one explicit AUTH-0046 association and perform:

~~~text
world point
    -> realized volume world center
    -> island-local horizontal position
    -> authoritative compiled vertical column
    -> semantic depth
    -> native authored material request/allocation/realization
    -> AUTH-0044 final semantic winner
    -> AUTH-0045 stable application key
~~~

It must preserve:

- naturalized authored ownership;
- AUTH-0030 authored cave void;
- exact local-frame scale;
- association identity;
- backend independence.

Concrete backend material identity and block placement remain downstream of AUTH-0047.
