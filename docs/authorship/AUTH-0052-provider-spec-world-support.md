# AUTH-0052 — Provider-Spec World Support Proof

AUTH-0052 propagates AUTH-0051 proof-grade realized support through Skyforge's existing explicit provider-spec and world-catalog compilation path.

It does not invent authored identity and it does not weaken support certification for provider hybrids.

## Motivation

AUTH-0051 proved the built-in schema-2 semantic recipe path and introduced a crucial distinction:

    backend query reservation
        !=
    proof-grade realized support envelope

The existing archipelago/world-catalog path predates schema-2 semantic compilation.

It uses:

- schema-1 descriptors;
- ProviderMorphologySpec;
- ProviderBlendMorphologySpec;
- SkyIslandMorphologySpecCompiler;
- SkyIslandWorldCatalogCompiler.

AUTH-0052 brings proof metadata into that path directly.

## Provider-spec compilation result

SkyIslandMorphologySpecCompilation contains:

- the exact CompiledSkyIslandVolume;
- Optional<CertifiedSkyIslandSupportEnvelope>.

Absence is meaningful.

The ordinary SkyIslandMorphologySpecCompiler.compile method remains unchanged.

Support-aware callers explicitly use:

    compileWithSupport(...)

This prevents support certification from becoming an accidental prerequisite or side effect of legacy compilation.

## Direct provider specs

For ProviderMorphologySpec:

1. resolve the exact provider identity from the registry;
2. request certifiedPrimarySupportEnvelope;
3. if absent, return an uncertified compilation;
4. propagate the accepted bounded detail envelope;
5. propagate the provider's accepted secondary-morphology maximum factor;
6. emit a CertifiedSkyIslandSupportEnvelope.

The certificate kind is:

    provider-spec-v1:<provider-id>

No built-in family switch is used by the provider-spec certifier.

The provider itself owns the primary analytical support claim.

## Detail propagation

AUTH-0052 uses the same accepted bounded detail proof as AUTH-0051.

For detail amplitude D:

    detailMaximum
        = outward(1 + 0.15 * D)

The primary upper and underside offsets are multiplied by this conservative factor.

Horizontal support is unchanged by local detail.

## Secondary morphology propagation

The selected provider's SecondaryMorphologyContribution already supplies:

- minimumFactor;
- maximumFactor.

For secondary amplitude S, the provider is asked for its contribution at S.

The final upper support uses the outward-rounded maximum factor.

Underside and horizontal support remain unchanged by the accepted secondary composition path.

## Exact blend endpoints

MorphologyProviderBlend canonicalizes its pair and exact weight.

AUTH-0052 certifies only exact endpoints:

    secondWeight == 0
        -> first provider

    secondWeight == 1
        -> second provider

An exact endpoint is semantically the selected provider under the accepted hybrid recipe.

AUTH-0052 therefore propagates only that selected provider's:

- primary support certificate;
- secondary support factor.

The certificate kind is:

    provider-blend-endpoint-v1:<pair>@<selected-provider>

## True interior blends remain uncertified

For:

    0 < secondWeight < 1

AUTH-0052 returns no support certificate.

This is deliberate.

The accepted provider-hybrid recipe blends structural coordinates such as:

- footprint residual;
- normalized along coordinate;
- normalized across coordinate;
- family upper factor;
- underside depth factor.

Endpoint support certificates alone do not yet prove a continuous conservative support envelope for those blended structural fields.

AUTH-0052 therefore does not infer an envelope from:

- endpoint union;
- maximum endpoint radius;
- finite sampling;
- weighted endpoint support extents;
- built-in provider identity.

The volume still compiles normally.

It is simply marked uncertified.

## Floating-point conservatism

AUTH-0052 follows AUTH-0051 proof hygiene.

Derived maxima use outward rounding with Math.nextUp.

World-space support bounds use:

- Math.nextDown for minima;
- Math.nextUp for maxima.

The extra ulp is a proof margin, not authored geometry.

## World-volume support certificate

SkyIslandWorldVolumeSupportCertificate binds one support envelope to one exact SkyIslandWorldVolume.

It exposes:

- volumeId;
- supportBounds;
- queryBoundsContainSupport.

The support bounds are centered on the compiled descriptor's exact world center and suspension elevation.

They do not replace the volume's WorldBounds.

## World-catalog support bundle

SkyIslandWorldCatalogSupportBundle contains:

- the unchanged SkyIslandWorldCatalog;
- zero or more exact world-volume support certificates.

Certificates must:

- refer to an ID present in the catalog;
- bind the exact catalog volume, not merely the same ID;
- be unique by world-volume identity;
- fit completely inside that volume's backend query bounds.

The bundle exposes:

- certifiedCount;
- uncertifiedCount;
- fullyCertified;
- exact lookup by volume or volume ID;
- canonical plan-order certificate view.

A partial bundle is valid.

For example:

    5 world volumes
    4 certified direct/endpoint specs
    1 uncertified interior blend

is represented as exactly that, not rounded up to a fully certified world.

## Support-aware world compilation

SkyIslandWorldCatalogCompiler retains its ordinary compile method unchanged.

AUTH-0052 adds:

    compileWithSupport(plan, registry, verticalReservation)

For every planned member it:

1. compiles the exact provider-spec volume;
2. attempts analytical support certification;
3. constructs the same world-volume ID;
4. constructs the same backend query WorldBounds;
5. binds support proof when present;
6. validates the final support bundle.

For identical inputs:

    ordinaryCompile.catalog volumes
        ==
    supportAwareCompile.catalog volumes

The support path must not change:

- graph geometry;
- world-volume identity;
- plan order;
- query reservations.

## Query reservations must contain certified support

AUTH-0052 introduces a new safety check.

If a volume is analytically certified, its backend query bounds must contain the full certified support bounds.

Otherwise support-aware compilation fails.

This is important because backend query bounds are used to discover potentially relevant world volumes.

A query reservation smaller than certified physical support could cause a backend query to cull real authored geometry.

AUTH-0052 therefore rejects:

    support extends outside query bounds

rather than publishing a misleading bundle.

## Existing reservation compatibility

The older world-catalog example uses roughly:

    horizontal reservation = 256
    below suspension = 180
    above suspension = 140

For the fully enriched built-in provider specimens used by AUTH-0052, the new analytical proof may exceed those values.

That does not invalidate ordinary legacy compilation.

It means the old reservation is not sufficient for a proof-backed catalog under the new analytical worst-case guarantee.

AUTH-0052 preserves both facts:

- ordinary compile still works exactly as before;
- support-aware compile rejects the undersized reservation.

A larger explicit reservation such as the accepted AUTH-0052 corpus fixture:

    horizontal = 360
    below = 260
    above = 160

contains the certified support and succeeds.

No reservation is silently expanded after planning.

## Why no silent auto-expansion

Horizontal member reservation participates in group and archipelago spacing.

Silently increasing it at world-catalog compilation time could invalidate already accepted placement separation.

AUTH-0052 therefore validates rather than mutates.

A later planning preflight may calculate or enforce support-driven minimum reservation before placement.

## Closed bounds containment

WorldBounds now supports closed-bounds containment.

A query bound contains a support bound only when every support minimum and maximum lies inside or on the corresponding query axis.

Boundary equality is accepted.

This matches the existing closed query/intersection semantics.

## Explicit AUTH-0046 bridge

AUTH-0052 does not infer native authored identity from world-volume identity.

SkyIslandAuthoredRealizationSupportCatalog.fromWorldSupport requires:

- an already explicit AUTH-0046 association catalog;
- an AUTH-0052 world support bundle.

For every association:

1. realizationRootSeed must equal world catalog rootSeed;
2. the association's realized volume ID must exist in the world catalog;
3. the world volume must equal the exact association realized volume;
4. if that exact volume has support proof, the envelope is rebound to the existing association.

No association is created by AUTH-0052.

No authored world/province/cluster/island identity is inferred from:

- archipelago root seed;
- group identifier;
- member ordinal;
- geometry seed;
- placement.

This preserves AUTH-0046's identity boundary.

## Evidence atlas

AUTH-0052 includes a visual atlas because provider-spec certification and query containment are spatial contracts.

The canonical panels are:

- DIRECT_MASSIF;
- DIRECT_SPINE;
- ENDPOINT_FIRST;
- ENDPOINT_SECOND;
- INTERIOR_BLEND;
- RESERVATION_GATE.

Each provider-spec panel shows:

- unchanged backend query reservation;
- actual compiled X/Y slice at local Z=0;
- proof-grade support bounds when certified.

The interior blend intentionally has no support box.

The reservation-gate panel overlays:

- an adequate query reservation;
- the certified support envelope;
- the older smaller query reservation;
- the actual compiled slice.

It makes the support-aware rejection visible rather than reducing it to a CSV boolean.

## Evidence manifests

manifest.csv records per provider-spec scenario:

- spec kind;
- certificate presence;
- certificate kind;
- whether query bounds contain support;
- sampled-column count;
- sampled query-containment violations;
- sampled support-containment violations;
- query horizontal/vertical extents;
- support horizontal/vertical extents.

Sampling is corroborative only.

It does not create support proof.

bundle.csv records:

- volume count;
- certified count;
- uncertified count;
- fullyCertified;
- equality with ordinary world catalog;
- rejection of the legacy undersized reservation.

Canonical acceptance expects:

    volumeCount = 5
    certifiedCount = 4
    uncertifiedCount = 1
    fullyCertified = false
    ordinaryCatalogEqual = true
    legacyReservationRejected = true

## Acceptance gate

Reject AUTH-0052 if:

- ordinary provider-spec compilation changes;
- support-aware compilation changes graph geometry;
- support-aware compilation changes world-volume IDs;
- support-aware compilation changes query bounds;
- direct provider support is inferred when the provider did not opt in;
- non-endpoint blends receive inferred support;
- exact blend endpoints use the unselected provider's support;
- detail or secondary factors are omitted from support propagation;
- certified support may extend outside query bounds;
- undersized query reservations are silently expanded after planning;
- partial certification is reported as fully certified;
- world support creates or infers AUTH-0046 authored identities;
- same world-volume ID is accepted with different volume content;
- finite visual/sample evidence is treated as the source of proof;
- Minecraft or NeoForge types enter the proof contract.

## Parallel implementation boundary

AUTH-0052 changes no Minecraft runtime behavior.

It does not alter:

- BlockPos conversion;
- chunk traversal;
- registry lookup;
- BlockState mapping;
- terrain mutation;
- carvers;
- structures;
- persistence;
- save/reload;
- client state.

The implementation lane remains independent.

## Next milestone

AUTH-0052 intentionally fails late when a planned provider spec has a certificate larger than its reserved query envelope.

The next useful authorship boundary is to move that knowledge earlier.

A likely AUTH-0053 direction is:

**provider-support reservation preflight / planning admission**

It should evaluate certifiable morphology specs before group/archipelago placement and report:

- minimum required horizontal reservation;
- minimum required below-suspension reservation;
- minimum required above-suspension reservation;
- uncertified member/spec identities;
- whether a proposed group template and world vertical reservation can produce a fully proof-backed catalog.

It must not silently enlarge already planned spacing.

True non-endpoint hybrid certification should remain a separate analytical milestone unless a proof-grade structural-coordinate envelope is established.
