# AUTH-0051 — Certified Realized Support Envelopes

AUTH-0051 separates proof-grade realized support bounds from the broader world-query reservations used for backend discovery.

The immediate purpose is to strengthen AUTH-0050 overlap admission without weakening its fail-closed policy.

## Problem

AUTH-0050 correctly refuses to treat finite sampling as proof of continuous separation.

That exposed a practical limitation for intentional stacks.

A realized island may have broad backend query bounds such as:

    suspension - 220
        through
    suspension + 220

even when the actual compiled morphology occupies only a much smaller vertical interval.

Those broad bounds are useful for backend queries, but they are too weak to prove a safe stack.

The wrong fix would be to shrink query reservations until stacking passes.

AUTH-0051 introduces a separate contract:

    backend query reservation
        !=
    proof-grade realized support envelope

The query reservation remains broad enough for discovery.

The support envelope exists only to certify where the accepted continuous realization can actually have positive physical support.

## Provider opt-in

AUTH-0051 extends SkyIslandMorphologyProvider with:

    certifiedPrimarySupportEnvelope(descriptor)

The default implementation returns empty.

This is deliberate.

A provider that cannot analytically prove a continuous support envelope does not receive one from:

- sampled extrema;
- heuristic family similarity;
- registry position;
- nominal descriptor dimensions;
- recipe-version coincidence.

Provider certification is explicit opt-in.

## Primary support envelope

PrimaryMorphologySupportEnvelope contains three positive conservative extents relative to the descriptor frame:

- maximumHorizontalRadius;
- maximumUpperOffset;
- maximumUndersideDepth.

For every positive-inside point emitted by the provider's signal-free primary realization:

    hypot(worldX-centerX, worldZ-centerZ)
        <= maximumHorizontalRadius

    suspensionElevation - maximumUndersideDepth
        <= worldY
        <= suspensionElevation + maximumUpperOffset

The envelope is proof metadata rather than rendering or sampling metadata.

## Accepted built-in primary certificate

All currently accepted built-in morphology providers share one conservative analytical certificate.

### Horizontal support

The accepted primary family recipe uses a deterministic radius scale in:

    [0.97, 1.03)

For non-lobed families, the largest major-axis multiplier is SPINE:

    majorFactor <= 1.40

Therefore:

    maximum non-lobed major radius
        < 1.03 * 1.40 * nominalRadius
        = 1.442 * nominalRadius

LOBED has a smaller base major factor but permits directional radial expansion.

Its base major factor is at most 0.96 and lobeStrength is less than 1.76.

The lobed radius transform is bounded by:

    sqrt(1 + lobeStrength) < sqrt(2.76)

Therefore:

    maximum lobed horizontal reach
        < 1.03 * 0.96 * sqrt(2.76) * nominalRadius
        < 1.65 * nominalRadius

AUTH-0051 therefore certifies:

    maximumHorizontalRadius
        = outward(1.65 * nominalRadius)

This intentionally keeps analytical margin.

### Upper support

Inside the positive primary footprint, the accepted built-in crown expressions are each bounded by one descriptor upper-elevation unit.

MASSIF, TABLELAND, SPINE, and LOBED are bounded directly by their residual/factor products.

For BASIN:

    crown(r) = (1-r) * (0.58 + 2.20r)
    r in [0,1]

The quadratic reaches its maximum below 0.88.

Therefore the common built-in certificate is:

    maximumUpperOffset
        = outward(upperElevation)

### Underside support

The accepted underside recipe has:

- tapered remaining <= 1;
- signed asymmetry amplitude <= 0.25;
- non-lobed normalized along magnitude < 1;
- lobed normalized along magnitude bounded by sqrt(2.76);
- non-lobed family depth factor <= 1.45;
- lobed family depth factor <= 1.20.

The resulting shaped-depth multiplier stays below approximately 1.91 across the accepted built-in vocabulary.

AUTH-0051 deliberately rounds outward to:

    maximumUndersideDepth
        = outward(2.0 * undersideDepth)

The certificate is conservative rather than optimized.

## Enrichment propagation

AUTH-0051 does not re-evaluate the procedural graph numerically.

It propagates already accepted analytical envelopes through already accepted bounded composition.

### Local detail

Seeded local surface detail scales each signed upper/underside offset by:

    1 + modulation

where at full amplitude:

    modulation in [-0.15, +0.15]

For detailAmplitude D:

    detailMaximumFactor
        = 1 + 0.15 * D

Horizontal footprint is unchanged.

### Secondary morphology

Provider secondary morphology already carries an accepted analytical factor envelope through SecondaryMorphologyContribution:

- minimumFactor;
- maximumFactor.

For the accepted schema-2 semantic built-in path, secondary geography only multiplies the seeded upper offset.

It does not change:

- horizontal support;
- underside support.

Therefore the final AUTH-0051 certificate is:

    maximumHorizontalRadius
        = primary.maximumHorizontalRadius

    maximumUpperOffset
        = primary.maximumUpperOffset
          * detailMaximumFactor
          * secondary.maximumFactor

    maximumUndersideDepth
        = primary.maximumUndersideDepth
          * detailMaximumFactor

No sampled graph extrema participate in certification.

## Floating-point conservatism

AUTH-0051 certificates are conservative in binary64 arithmetic as well as in the underlying real-valued analysis.

Derived positive maxima are rounded outward with `Math.nextUp`.

When an association-relative envelope becomes world-space bounds:

- minima use `Math.nextDown(center - extent)`;
- maxima use `Math.nextUp(center + extent)`.

This prevents an analytically safe bound from becoming microscopically inward because of one floating-point rounding step.

The outward margin is intentionally tiny. It is proof hygiene, not an authored geometry expansion.

## Certified compiled path

CertifiedSkyIslandSupportEnvelopeCompiler currently certifies only the accepted built-in schema-2 semantic recipe path.

A compiled volume must satisfy all of:

- recipeVersion == SemanticSkyIslandVolumeRecipe.RECIPE_VERSION;
- descriptor schema == 2;
- descriptor has an explicit semantic morphology family;
- provenance contains the matching semantic-morphology-family control;
- provenance contains detail-amplitude;
- provenance contains secondary-morphology-amplitude;
- the resolved built-in provider supplies a primary support certificate.

A matching numeric recipe version by itself is insufficient.

The resulting certificateKind is:

    semantic-built-in-v1:<family>

This provenance gate prevents arbitrary compiled graphs from inheriting support metadata merely by reusing a version number.

## Explicitly unsupported paths

AUTH-0051 intentionally leaves these paths uncertified:

- legacy schema-1 volumes;
- arbitrary third-party providers that do not opt in;
- compiled recipes not recognized by the accepted certificate compiler;
- non-endpoint provider hybrids whose continuous structural-coordinate envelope has not been separately proven.

Uncertified does not mean unsafe.

It means:

    no proof-grade support envelope is available

AUTH-0050 must therefore retain its broader fail-closed behavior for those associations.

## Authored-realization support certificate

SkyIslandAuthoredRealizationSupportCertificate binds one CertifiedSkyIslandSupportEnvelope to one exact AUTH-0046 association.

The certificate produces proof bounds centered on the realized physical descriptor:

    X = centerX +/- maximumHorizontalRadius

    Y = suspension - maximumUndersideDepth
        through
        suspension + maximumUpperOffset

    Z = centerZ +/- maximumHorizontalRadius

These bounds are independent of SkyIslandWorldVolume.bounds().

The same association may therefore have:

- broad backend query bounds;
- tighter proof-grade support bounds.

Both are valid because they serve different contracts.

## Support catalog

SkyIslandAuthoredRealizationSupportCatalog is keyed by the full AUTH-0046 canonical association identity.

It may contain only a subset of the association catalog.

Absence is preserved explicitly.

The accepted automatic builder:

    certifyAccepted(associationCatalog)

attempts the AUTH-0051 compiler for every association and includes only successful certificates.

The catalog exposes:

- certifiedCount;
- uncertifiedCount;
- exact association lookup;
- canonical certificate ordering.

A support certificate for an association outside the underlying AUTH-0046 catalog is rejected.

## AUTH-0050 integration

SkyIslandAuthoredOverlapAdmissionAuditor retains its original constructor.

Without a support catalog, AUTH-0050 behaves exactly as before.

AUTH-0051 adds a support-aware constructor.

For each association, the auditor uses:

    certified support bounds, if available
    otherwise original WorldBounds

as its conservative proof bounds.

The original WorldBounds remain authoritative for:

- candidate culling;
- deterministic overlap-witness search.

AUTH-0051 does not shrink backend discovery geometry.

### Strict separation

If the available proof bounds are disjoint, SEPARATE may now be certified even when the broader query reservations intersect.

This is safe because every proof bound remains a conservative superset of actual realized support.

The independent AUTH-0050 native horizontal-support certificate remains available unchanged.

### STACKED

STACKED still requires:

- bit-exact same center X/Z;
- positive declared minimum vertical separation.

Its vertical proof gap is now computed from the tightest available conservative proof bounds.

Thus a pair can have:

    query reservations overlap
    proof support separated

and be correctly admitted as CERTIFIED_STACKED.

If either association lacks a support certificate, its broad WorldBounds remain in the proof calculation.

AUTH-0051 never fills an uncertified gap with a guess.

## Critical before/after invariant

The canonical integration proof uses one same-X/Z pair with deliberately overlapping broad reservations.

Without AUTH-0051:

    query bounds intersect
    conservative vertical gap = 0
    STACKED -> REJECTED_STACK_REQUIREMENT

With both AUTH-0051 certificates:

    same query bounds still intersect
    certified physical support bounds separate
    proof vertical gap >= required gap
    STACKED -> CERTIFIED_STACKED

No query reservation is changed.

No finite column sample is promoted into a proof.

This is the core milestone.

## Visual evidence atlas

AUTH-0051 includes a visual atlas because the distinction is spatial and easy to misunderstand from CSV alone.

Five family panels show an X/Y slice at local Z=0.

Each panel overlays:

- the broad backend query reservation;
- the tighter AUTH-0051 certified support box;
- the actual compiled upper/underside slice.

The sixth panel shows the canonical stack proof:

- broad lower/upper reservations overlap;
- certified support boxes do not;
- the actual realized slices remain inside the certificates;
- AUTH-0050 status changes from rejected to certified only when proof metadata is supplied.

The visual atlas is diagnostic/proof evidence, not a morphology aesthetic gate.

## Evidence manifest

The family manifest records:

- query minimum/maximum Y;
- certified support minimum/maximum Y;
- sampled realized minimum/maximum Y;
- sampled-column count;
- containment violations;
- query span;
- support span.

Sampling is corroborative evidence only.

Acceptance does not infer the certificate from those samples.

The stack manifest records:

- query-bounds intersection;
- broad-only AUTH-0050 status;
- support-aware AUTH-0050 status;
- certified proof vertical gap;
- required vertical gap;
- lower support maximum Y;
- upper support minimum Y.

## Acceptance gate

Reject AUTH-0051 if:

- provider support certification defaults to an inferred envelope;
- finite sampling creates a support certificate;
- a recipe version alone creates a support certificate;
- unsupported recipes silently receive built-in bounds;
- secondary morphology changes are omitted from certified upper support;
- local detail changes are omitted from upper or underside support;
- AUTH-0051 mutates backend query bounds;
- AUTH-0050 witness search switches to the tighter support bounds and can miss query candidates;
- an uncertified association is treated as though it had tight proof bounds;
- broad-reservation STACKED rejection disappears without a proof-grade certificate;
- visual/sample containment is used as the logical source of proof;
- Minecraft or NeoForge types enter the certificate contract.

## Parallel implementation boundary

AUTH-0051 changes no Minecraft runtime behavior.

It does not alter:

- BlockPos conversion;
- chunk discovery;
- registry lookup;
- BlockState mapping;
- terrain mutation;
- runtime volume admission;
- carvers;
- persistence;
- save/reload.

The implementation lane should continue independently.

AUTH-0051 is upstream world-authorship proof metadata.

## Next milestone

AUTH-0051 certifies the native schema-2 built-in semantic realization path.

The next useful boundary is to propagate proof-grade support through the explicit provider morphology-spec world-planning path.

A likely AUTH-0052 direction is:

**provider-spec support certification and world-catalog proof bundle**

That milestone should:

- certify direct provider specs when the provider supplies a primary envelope;
- propagate accepted detail and secondary factor envelopes;
- certify endpoint blends from the selected endpoint provider;
- leave non-endpoint hybrids uncertified until their structural-coordinate support is separately proven;
- compile SkyIslandWorldCatalog together with exact per-volume support certificates;
- preserve broad world query reservations independently.

This would make the support proof available directly from the existing group/archipelago planning pipeline without weakening third-party provider semantics.
