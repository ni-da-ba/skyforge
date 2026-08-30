# ADR-0029: Provider-Aware Enrichment Composition

- **Status:** Implemented through focused recipe proof; local acceptance pending
- **Date:** 2026-08-30
- **Work item:** SF-IMP-0025

## Context

SF-IMP-0024 accepts an explicit morphology-provider SPI, a deterministic immutable provider registry, a genuine non-built-in reference morphology, and provider-neutral primary hybridization. That work proves arbitrary providers can participate in Skyforge's primary morphology space, but custom↔built-in hybrids are still signal-free.

SF-IMP-0025 must make the enrichment layer provider-neutral as well. The important constraint is that a provider's secondary morphology is exposed as a public two-dimensional positive factor graph with arbitrary provider-local node identifiers. Skyforge must therefore compose that factor into both two-dimensional surface graphs and three-dimensional density graphs without knowing those identifiers.

A second compatibility issue appears at provider endpoints. SF-IMP-0024 deliberately preserves exact provider graph bytes at blend weights 0 and 1. The accepted bounded-detail machinery, however, expects Skyforge's canonical carrier node vocabulary (`profile.*`, `upper.offset`, `underside.offset`, and related density nodes). An external provider is not required to use those names.

## Decision

SF-IMP-0025 introduces `EnrichedProviderHybridMorphologySkyIslandVolumeRecipe` with independent recipe-layer controls for:

- bounded local-detail amplitude;
- provider-aware secondary-morphology amplitude.

The primary provider blend remains `MorphologyProviderBlend` resolved through an explicit `SkyIslandMorphologyProviderRegistry`.

For a nonzero enrichment request:

1. compile the provider-neutral primary hybrid;
2. if the blend is an exact endpoint, re-express the selected provider's primary contribution through `ProviderPrimaryMorphologyCanonicalizer` using only its declared structural handles;
3. apply the accepted bounded-detail carrier machinery;
4. compile each provider's optional `SecondaryMorphologyContribution` at the requested secondary amplitude;
5. lift each provider factor graph into the target surface/density dimensionality while preserving coordinates, operators, signal identity, and graph topology;
6. blend those positive factors using the canonical provider weights, treating a missing provider secondary vocabulary as the neutral factor `1`;
7. replace generic structured relief with the blended provider factor;
8. neutralize the generic SF-IMP-0017 secondary-amplitude constant;
9. rebuild density as the exact upper/lower intersection.

The enriched provider recipe does not inspect or switch on `MorphologyFamily`.

## Exact zero-enrichment authority

When both enrichment amplitudes are zero, the recipe returns the provider-primary hybrid geometry unchanged. Exact provider graph bytes therefore remain authoritative at blend endpoints, preserving SF-IMP-0024's endpoint contract.

The canonical carrier adapter is used only when enrichment requires Skyforge's shared structural vocabulary.

## Canonical provider carrier

`ProviderPrimaryMorphologyCanonicalizer` is a provider-neutral adapter over `PrimaryMorphologyContribution`.

It namespaces the provider's original graph, aliases the provider-declared footprint and directional fields into Skyforge's canonical carrier IDs, and rebuilds upper/underside surfaces from the provider-declared upper/depth factors. The adapter must be numerically equivalent to the original provider primary geometry; full-resolution acceptance will verify that equivalence for the non-built-in reference provider.

This establishes an important SPI rule: provider-local node naming is not part of the compatibility contract. Declared structural handles are.

## Secondary factor lifting

`ProviderSecondaryMorphologyComposition` accepts provider secondary factor graphs in the public `SCALAR_FIELD_2` form and copies them under stable first/second namespaces.

When composing into the three-dimensional density graph, factor nodes are promoted to `SCALAR_FIELD_3` while preserving:

- constant values;
- coordinate axes;
- arithmetic operators and dependencies;
- planar signal version, seed derivation, namespace, root seed, and scale;
- intersection topology if present.

The promoted factor is therefore the same x/z function evaluated in the density domain rather than a provider-specific reimplementation.

## Positive envelope

Each provider secondary contribution already declares a finite strictly positive analytical envelope. Missing contributions use `[1,1]`. The blended envelope is the convex combination of the two provider envelopes and must remain finite and strictly positive.

The blended minimum and maximum are materialized as inspectable graph constants.

## Acceptance requirements

SF-IMP-0025 must demonstrate:

1. invalid provider-enrichment amplitudes fail early;
2. zero enrichment preserves exact SF-IMP-0024 provider-primary graph bytes;
3. canonical provider pair reversal preserves exact enriched graph identity;
4. detail and secondary amplitudes operate independently;
5. generic SF-IMP-0017 structured relief is neutralized when provider secondary morphology is active;
6. providers with no secondary vocabulary compose through an exact neutral factor;
7. the provider secondary envelope remains finite and strictly positive;
8. all ten built-in provider pairs compile through the new provider-neutral enriched path;
9. density remains the exact positive-inside intersection;
10. a genuinely external provider endpoint can be canonicalized and enriched without depending on its local node names;
11. the canonicalized external primary is numerically equivalent to its exact provider primary;
12. full enriched custom↔built-in specimens preserve one connected component, zero face contacts, at least 48 world units canonical clearance, and the exact accepted provider-primary footprint;
13. human visual review confirms custom provider secondary geography and built-in family-aware geography transition coherently with the same primary blend.

## Generalized morphology mixtures

The accepted provider model should remain extensible to a future normalized mixture of more than two morphology providers. Such an N-way mixture would generalize the same structural-field and positive-secondary-factor composition used here, but it also requires canonical provider ordering, normalized weight identity, endpoint/sparse-weight semantics, and more complex provenance.

SF-IMP-0025 deliberately retains pairwise blends. Pairwise composition is sufficient for the next island-chain/group layer, and generalized mixtures should be introduced when real island-variance requirements justify that additional complexity rather than preemptively expanding the public recipe surface.

## Next step

After SF-IMP-0025, morphology capability is sufficiently complete to begin the first multi-island chain/group composition layer. Group planning should consume provider IDs and provider-composition specifications rather than built-in family enums.

## Deferred work

SF-IMP-0025 does not yet:

- define provider discovery/loading;
- define data-authored morphology schemas;
- generalize pairwise provider blends to arbitrary normalized N-way morphology mixtures;
- promote provider selection into descriptor schema 3;
- place multiple islands in world space;
- define inter-island spacing, hierarchy, chain topology, or archipelago semantics;
- define biome/material interpretation or backend realization.
