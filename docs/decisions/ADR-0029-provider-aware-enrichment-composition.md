# ADR-0029: Provider-Aware Enrichment Composition

- **Status:** Focused recipe proof and human visual acceptance passed; final full verifier completion pending confirmation
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
2. if the blend is an exact endpoint, re-express the selected provider's primary contribution through `ProviderPrimaryMorphologyCanonicalizer`;
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

The provider's authored upper and underside surfaces are authoritative. The adapter namespaces those source graphs, derives canonical `upper.offset` and `underside.offset` directly from the actual provider surfaces relative to suspension elevation, and aliases the provider-declared footprint/directional fields into Skyforge's canonical carrier IDs. Provider-declared upper/depth factors remain available as inspectable semantic handles, but the adapter does **not** reconstruct the provider surfaces from Skyforge's built-in crown/underside formula.

For density composition, the provider's two-dimensional upper and underside graphs are independently promoted into the three-dimensional carrier before canonical offsets and the exact intersection are rebuilt. This avoids requiring a provider density graph to expose any particular local surface-node names.

This rule was tightened after the first full-resolution SF-IMP-0025 run correctly failed the three-seed canonical-equivalence gate. The original adapter had reapplied Skyforge's generic underside-asymmetry formula; `reference:crescent` does not use that term in its accepted primary underside, so canonicalization changed the external provider geometry. The acceptance gate was retained unchanged and the adapter was corrected instead.

A second focused regression then exposed that a canonical surface carrier must reference only the handles guaranteed for that surface by the public SPI: `upperFactor` is guaranteed in upper/density graphs, while `undersideDepthFactor` is guaranteed in underside/density graphs. The adapter now obeys that contract while density carries both.

This establishes two important SPI rules:

- provider-local node naming is not part of the compatibility contract; declared structural handles are;
- provider-authored primary surfaces remain authoritative unless a provider explicitly opts into a future stronger reconstruction contract.

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

Requirements 1–10 are covered by the focused recipe suite, which completed successfully locally after correcting a test helper that had queried the upper graph for an underside-only amplitude node and after tightening the canonical carrier to obey per-surface SPI handle guarantees. Requirement 11 passed the targeted three-seed canonical-carrier equivalence regression after provider-authored surfaces were made authoritative. Requirement 13 passed human review of the uploaded visual corpus. Requirement 12 is exercised by the full-resolution reference acceptance stage of the fail-fast local verifier; final verifier completion remains to be explicitly confirmed before the ADR is promoted to `Accepted`.

## Full-resolution acceptance corpus

The SF-IMP-0025 numerical corpus contains 18 fully enriched canonical specimens:

- the `reference:crescent` endpoint at all three established seeds, exercising provider endpoint canonicalization with full detail and full custom secondary morphology;
- `reference:crescent` midpoint-hybridized with each of the five accepted built-in providers at all three established seeds, again with full detail and full provider-aware secondary morphology.

For each specimen the gate requires positive occupancy, exactly one connected component, zero domain-face contacts, at least 48 world units sampled clearance, exact provider-primary footprint-sign preservation across the canonical horizontal grid, and nontrivial changes to both upper and underside surfaces.

A separate three-seed canonical-carrier check wraps the same external crescent primary with no secondary vocabulary. A nonzero secondary request forces canonicalization while composing an exact neutral factor, allowing the canonical carrier's upper and underside surfaces to be compared numerically against the external provider's original surfaces across the full canonical horizontal grid.

## Human-review corpus

The visual corpus uses the stable Skyforge seed and contains 16 fully enriched review specimens:

- one enriched standalone `reference:crescent` endpoint;
- 25/50/75-percent built-in contributions for each of Massif, Tableland, Spine, Basin, and Lobed.

Every member uses detail amplitude `1` and provider-aware secondary amplitude `1`. Review compares these artifacts against the accepted SF-IMP-0024 signal-free provider atlas.

Across the uploaded review corpus:

- connected components are **1 for all 16 members**;
- face contacts are **0 for all 16 members**;
- review-grid minimum clearance is **72 to 128 world units**;
- **16/16 suspension-plane occupancy images are byte-identical** to the corresponding accepted SF-IMP-0024 images;
- all 16 upper-surface and underside artifacts change nontrivially;
- solid-sample increases relative to the signal-free baseline range from **981 to 2,826**;
- maximum sampled elevation increases by **16 to 40 world units**.

The lowest review-grid clearance is 72 world units for the 75-percent Spine specimen, still above the 48-unit gate.

Human review finds coherent enriched progressions on all five axes. Massif and Spine provide the strongest vertical/organized-relief transitions; Basin retains its broad lower-center/ring tendency; Tableland and Lobed are visually quieter but remain ordered and do not exhibit a parent snap or generic morphology collapse. The isometric renderer continues to understate fine relief, so upper-surface and orthogonal-section artifacts carry most of the visual acceptance weight.

Detailed review record:

- `docs/reviews/SF-IMP-0025-enriched-provider-morphology-visual-review.md`

**Human visual gate: passed.**

## Generalized morphology mixtures

The accepted provider model should remain extensible to a future normalized mixture of more than two morphology providers. Such an N-way mixture would generalize the same structural-field and positive-secondary-factor composition used here, but it also requires canonical provider ordering, normalized weight identity, endpoint/sparse-weight semantics, and more complex provenance.

SF-IMP-0025 deliberately retains pairwise blends. Pairwise composition is sufficient for the next island-chain/group layer, and generalized mixtures should be introduced when real island-variance requirements justify that additional complexity rather than preemptively expanding the public recipe surface.

## Local verifier

`scripts\verify-sf-imp-0025.bat` runs, in order:

1. the Java runtime check;
2. the focused provider-aware enrichment recipe suite;
3. the 18-member full-resolution external-provider enrichment acceptance suite;
4. the 16-member enriched custom-provider visual atlas.

The atlas is written to `skyforge-reference\build\evidence\enriched-provider-morphology-suspended-volume-v1`.

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