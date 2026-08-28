# ADR-0027: Hybrid Family-Aware Secondary Morphology

- **Status:** Implemented; human visual acceptance passed, final local numerical/repository gate pending confirmation
- **Date:** 2026-08-28
- **Work item:** SF-IMP-0023

## Context

SF-IMP-0022 proves continuous primary-morphology hybridization across all ten unordered pairs of the five accepted built-in families. Those hybrids are finite, connected, suspended, deterministic, and visually coherent, but they remain signal-free primary bodies. A complete hybrid island also needs bounded local detail and secondary geography that reflects both parent families.

Selecting only one parent's secondary vocabulary would make a 50/50 primary hybrid semantically asymmetric. Applying the old generic ridge/spur/valley vocabulary would discard the family-aware improvements accepted in SF-IMP-0020. Re-implementing the five family-aware formulas inside the hybrid compiler would duplicate accepted mathematics and weaken regression authority.

## Decision

SF-IMP-0023 composes the accepted SF-IMP-0016 bounded-detail layer with a **blend of the two accepted SF-IMP-0020 parent secondary factors**.

For a non-endpoint hybrid:

1. compile the accepted SF-IMP-0022 signal-free hybrid primary;
2. construct the accepted full-amplitude generic enrichment carrier over that hybrid so the exact bounded-detail nodes and stable structural IDs are present;
3. compile each parent family through the accepted SF-IMP-0020 family-aware carrier at full amplitude;
4. namespace those parent carrier graphs and reference each parent's proven secondary-factor node;
5. blend the two positive secondary factors using the same canonical weights as the primary hybrid;
6. multiply the hybrid's seeded upper offset by the blended secondary factor;
7. retain the hybrid's seeded underside unchanged;
8. rebuild density as the exact intersection of the resulting upper and underside surfaces.

This deliberately blends **factors**, not two finished enriched solids. The hybrid primary remains authoritative for closure and silhouette.

## Independent enrichment controls

Descriptor schema 2 remains unchanged. Hybrid selection is still recipe-layer state, so SF-IMP-0023 does not force hybrid semantics into a premature descriptor schema 3.

A recipe-layer `HybridMorphologyEnrichment` value carries:

- the canonical `MorphologyBlend`;
- bounded local-detail amplitude in `[0,1]`;
- blended secondary-morphology amplitude in `[0,1]`.

The base descriptor for this proof remains schema 1 with zero signal amplitude. `signalScale` continues to provide the accepted local-detail spatial scale. This keeps experimental hybrid composition explicit without overloading schema-2's single built-in-family field.

## Endpoint authority

Blend endpoints delegate to the accepted descriptor-driven semantic family recipe. For any detail/secondary amplitude combination, weight `0` and weight `1` therefore reproduce the corresponding accepted built-in family's upper, underside, and density graph bytes exactly.

At zero detail and zero secondary amplitude, a non-endpoint hybrid reproduces the accepted SF-IMP-0022 primary-hybrid graph bytes exactly.

## Canonical blend-weight identity

Focused SF-IMP-0023 testing exposed a latent SF-IMP-0022 identity defect in decimal complement handling. A literal `0.30` and the reversed expression `1.0 - 0.70` are mathematically equal but can occupy adjacent IEEE-754 representations, producing different serialized graph constants after canonical pair reversal.

`MorphologyBlend` now canonicalizes weights through a stable 16-significant-digit decimal representation and computes reversed complements through decimal arithmetic before re-canonicalizing. Exact endpoint values remain exact, and the accepted SF-IMP-0022 corpus weights (`0`, `0.25`, `0.5`, `0.75`, `1`) are unchanged. Dedicated regression coverage pins literal and computed `0.30/0.70` complement cases.

This correction strengthens the originally intended canonical-request contract: `(A,B,w)` and `(B,A,1-w)` must produce identical canonical blend values and graph bytes, not merely numerically close geometry.

## Analytical safety

Every accepted parent secondary factor is strictly positive. A convex blend of two strictly positive factors is also strictly positive. Therefore applying the blended factor to the hybrid seeded upper offset cannot change the sign of the hybrid primary footprint.

The bounded local-detail factor also remains positive in `[0.85,1.15]` at full amplitude. The common hybrid rim and horizontal footprint are therefore preserved analytically through both enrichment stages. Full-resolution topology remains a hard sampled gate.

## Provider direction

The SF-IMP-0022 package-internal primary-provider seam remains in place. SF-IMP-0023 does not yet make arbitrary providers enrichment-compatible, but it exposes an important future requirement: a public morphology provider must either supply a compatible secondary-morphology factor/policy or explicitly declare how secondary geography should be composed.

This is evidence toward the eventual user/mod-defined morphology contract rather than a substitute for that contract.

## Acceptance requirements

SF-IMP-0023 must demonstrate:

1. exact accepted built-in graph identity at both blend endpoints for independent detail/secondary amplitudes;
2. exact SF-IMP-0022 primary-hybrid graph identity when both enrichment amplitudes are zero;
3. detail-only operation changes bounded local geometry without introducing organized secondary relief;
4. secondary-only operation preserves the signal-free hybrid underside while adding blended family-aware upper geography;
5. the blended secondary factor remains positive and preserves the exact hybrid primary footprint sign;
6. density remains the exact positive-inside intersection of final upper and underside surfaces;
7. canonical pair symmetry and deterministic graph identity remain intact, including decimal complement regression cases;
8. all ten unordered family pairs remain one connected component, touch no domain face, and retain at least 48 world units sampled clearance across the accepted full-resolution corpus;
9. full enrichment preserves the exact SF-IMP-0022 primary footprint sign at every canonical horizontal sample;
10. human review confirms that secondary geography transitions with the primary blend instead of visibly belonging to only one parent.

Requirements 1–7 are covered by the focused recipe/regression suite. Requirement 10 has passed human review. Requirements 8–9 remain tied to the final successful local verifier record before this ADR is marked fully Accepted.

## Initial evidence corpus

Numerical acceptance uses all ten unordered family pairs at midpoint weight `0.5` across the three established root seeds with full detail and full blended secondary morphology: 30 full-resolution canonical specimens.

Each specimen is compared directly with its accepted SF-IMP-0022 signal-free hybrid primary. In addition to topology and clearance, the full-resolution gate requires exact primary-footprint sign preservation across the canonical horizontal grid and nontrivial changes to both upper and underside surfaces.

Human review uses the stable Skyforge seed at weights `0.25`, `0.50`, and `0.75` for all ten pairings, again with full detail and secondary morphology, producing a 30-member atlas directly comparable to SF-IMP-0022.

## Human visual acceptance

The uploaded `enriched-hybrid-morphology-suspended-volume-v1` corpus was compared specimen-for-specimen against the accepted SF-IMP-0022 `hybrid-morphology-suspended-volume-v1` corpus.

Exact review-grid evidence:

- all 30 enriched `suspension-occupancy.png` files are byte-identical to their SF-IMP-0022 primary-hybrid counterparts;
- all 30 enriched review specimens contain one connected component;
- all 30 have zero face contacts;
- review-grid minimum clearance spans 64–128 world units;
- solid-sample counts increase by 268–1,818 samples versus matching primary hybrids;
- maximum sampled elevations rise by 8–40 world units.

Human review found coherent enriched interpolation across all ten pairings. In particular, Basin depression/ring behavior, Spine axial relief/keel organization, Tableland broad/shallow character, and Lobed shoulder/saddle semantics remain tied to the same blend progression as the primary morphology. No pairing shows an abrupt secondary-parent switch or an enriched silhouette drift.

Detailed review record:

- `docs/reviews/SF-IMP-0023-enriched-hybrid-morphology-visual-review.md`

**Visual gate: passed.**

## Local verifier

`scripts\verify-sf-imp-0023.bat` runs, in order:

1. the Java runtime check;
2. the blend canonicalization regression;
3. the focused enriched-hybrid differential/control suite;
4. the thirty-member full-resolution enriched midpoint acceptance corpus;
5. the thirty-member full-detail/full-secondary visual progression atlas.

Successful generation of the atlas does not by itself constitute the numerical acceptance record; the final verifier success line is retained as the authoritative local proof that the full-resolution stage completed before atlas generation.

## Deferred work

SF-IMP-0023 does not yet:

- promote hybrids into descriptor schema 3;
- expose a public custom morphology-provider ABI;
- define provider registration/discovery;
- define data-authored arbitrary morphology graphs;
- place multiple islands into chains, groups, provinces, or archipelagos;
- define biome/material interpretation or backend realization.
