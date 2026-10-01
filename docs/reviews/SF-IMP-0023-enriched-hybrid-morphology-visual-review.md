# SF-IMP-0023 Enriched Hybrid Morphology Visual Review

- **Work item:** SF-IMP-0023
- **Evidence corpus:** `enriched-hybrid-morphology-suspended-volume-v1`
- **Compared against:** accepted SF-IMP-0022 `hybrid-morphology-suspended-volume-v1`
- **Review status:** Pass
- **Date:** 2026-08-28

## Review objective

Determine whether full bounded local detail plus blended family-aware secondary morphology can be applied to the accepted pairwise primary-hybrid space without changing the hybrid silhouette, breaking suspended-volume identity, or making the resulting secondary geography visibly belong to only one parent.

The review compares each SF-IMP-0023 specimen directly with the matching SF-IMP-0022 primary hybrid at the same pair, root seed, and 25/50/75-percent blend weight.

## Exact evidence invariants

All 30 SF-IMP-0023 `suspension-occupancy.png` files are byte-identical to the corresponding SF-IMP-0022 files.

Therefore, on the review grid:

- full local detail does not change the accepted hybrid horizontal silhouette;
- blended family-aware secondary morphology does not change the accepted hybrid horizontal silhouette;
- the SF-IMP-0022 primary hybrid remains authoritative for closure and footprint.

As expected, all 30 upper-surface grids and all 30 underside grids differ from the signal-free SF-IMP-0022 primary-hybrid grids because SF-IMP-0023 adds upper secondary geography and bounded detail to both surfaces.

## Review-grid metrics

Across all 30 enriched review specimens:

- connected solid components: **1 for every specimen**;
- domain-face contacts: **0 for every specimen**;
- sampled minimum clearance: **64 to 128 world units**;
- solid-sample change versus the matching primary hybrid: **+268 to +1,818 samples**;
- maximum sampled elevation change versus the matching primary hybrid: **+8 to +40 world units**.

Clearance is unchanged in 18 specimens, reduced by 8 units in 7 specimens, and reduced by 16 units in 5 specimens. Every specimen remains comfortably above the 48-unit acceptance threshold on the review grid.

The full-resolution acceptance test remains authoritative for topology and exact canonical footprint preservation.

## Pairwise visual findings

### Massif ↔ Tableland — pass

The accepted compact-to-broad primary progression remains clear. Enrichment adds organized upper relief and local surface variation while Tableland influence continues to flatten and broaden the form as its weight increases. The enriched silhouettes remain exact SF-IMP-0022 silhouettes.

### Massif ↔ Spine — strong pass

This remains one of the clearest morphology axes. Increasing Spine contribution progressively strengthens elongation and the deep keel while the upper geography stays organized along the increasingly axial form. Enrichment does not turn the sequence into three similarly rough Massifs.

### Massif ↔ Basin — strong pass

The basin character continues to emerge progressively. At higher Basin weights the center depression and raised surrounding interior remain legible even after full detail is applied. The blended secondary geography reinforces rather than erases the transition.

### Massif ↔ Lobed — pass

The four-shoulder Lobed footprint remains progressively stronger toward the Lobed endpoint. Enrichment introduces useful surface structure without changing the primary outline. Lobed influence remains perceptually somewhat back-loaded, consistent with the SF-IMP-0022 review, but there is no abrupt switch.

### Tableland ↔ Spine — strong pass

The sequence moves cleanly from broad/shallow to elongated/deeper. Secondary relief increasingly aligns with the Spine-like longitudinal organization as Spine contribution increases. The Tableland end remains comparatively broad rather than becoming a generic rough ellipsoid.

### Tableland ↔ Basin — pass

As in SF-IMP-0022, planform change is intentionally subtle because both parents are compact. The meaningful progression remains vertical: a broad table-like upper interior gives way to an increasingly obvious central depression and ring-like surrounding high ground. Full detail does not destroy that distinction.

### Tableland ↔ Lobed — pass

The sequence retains a broad/shallow Tableland character at low Lobed weight and progressively acquires Lobed shoulders. This remains a quieter pairing than the Spine or Basin axes but the enriched sequence is coherent and useful.

### Spine ↔ Basin — strong pass

The transition combines two very different structural vocabularies successfully. Elongation and keel depth relax while the upper profile develops Basin-like depression/ring behavior. The 50-percent specimen reads as a genuine intermediate rather than one parent with noise from the other.

### Spine ↔ Lobed — pass

The strongly elongated Spine progressively broadens and develops shoulder structure. The family-aware upper relief follows that transition rather than remaining permanently axial. This is important evidence that the secondary-factor blend is actually responding to the same canonical hybrid weight as the primary morphology.

### Basin ↔ Lobed — pass

The sequence retains Basin-like vertical structure at low Lobed weight while increasingly adopting the Lobed planform. The combination remains continuous and connected, with no conflict between the central-depression vocabulary and the shoulder/saddle vocabulary.

## Upper-surface findings

Compared with SF-IMP-0022, every enriched upper surface gains visible structured and local variation. The effect is not a uniform roughness layer: pair-specific geometry remains legible through the enrichment.

The strongest semantic confirmations are:

- Basin-containing hybrids retain increasingly strong central-depression/ring behavior as Basin weight rises;
- Spine-containing hybrids retain increasingly axial relief and elongated structural organization as Spine weight rises;
- Tableland-containing hybrids remain comparatively broad and shallow at Tableland-heavy weights;
- Lobed-containing hybrids preserve and progressively strengthen shoulder/saddle organization rather than changing the silhouette.

## Underside findings

The underside receives only accepted bounded local detail; family-aware secondary morphology remains an upper-surface treatment. This is visible in the comparison atlas: underside depth and primary hybrid form remain coherent while deterministic local variation appears across the surface.

No reviewed underside suggests a separate component, detached spike, or loss of the accepted hybrid primary identity.

## Isometric and section findings

The isometric renderer continues to understate fine relief, but it clearly confirms that the enriched specimens remain compact suspended volumes with the accepted primary planforms.

East-west and north-south sections provide the stronger evidence. They show:

- added upper relief without common-rim drift;
- bounded underside detail;
- preserved Spine keel behavior;
- preserved Basin depression behavior;
- smooth 25/50/75 progression in overall vertical proportions.

No reviewed section shows an abrupt parent switch or a secondary morphology obviously fixed to one parent independent of the primary blend weight.

## Design conclusion

**SF-IMP-0023 passes the human visual design gate.**

The accepted composition rule is visually justified:

`SF-IMP-0022 hybrid primary → bounded local detail → convex blend of accepted parent family-aware secondary factors → exact final density intersection`

This is materially stronger than simply applying generic relief to hybrid bodies or selecting one parent's secondary vocabulary. The primary and secondary morphology spaces now interpolate coherently under one canonical family weight.

## Non-blocking follow-ups

1. Lobed influence remains somewhat perceptually back-loaded and may later benefit from a stronger primary Lobed model or a documented perceptual weighting curve.
2. The current isometric renderer remains too smooth to serve as the primary relief-review surface; top-down elevation and orthogonal sections remain more informative.
3. Hybrid semantics are still recipe-layer state. A later descriptor/API decision should decide whether pairwise hybrid composition is promoted as a first-class semantic morphology expression.
4. The provider seam now has concrete evidence that future custom providers need both primary-morphology and secondary-morphology composition contracts if they are to participate fully in hybrid islands.
5. Multi-island chain/archipelago generation should build on the accepted complete enriched-hybrid artifact rather than directly composing low-level graph nodes.
