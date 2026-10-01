# SF-IMP-0019 Cross-Family Enrichment Composition Visual Review

- **Work item:** SF-IMP-0019
- **Date:** 2026-08-28
- **Corpus:** `composed-morphology-family-suspended-volume-v1`
- **Reference corpus:** `morphology-family-suspended-volume-v1`
- **Decision:** Accepted as the first cross-family enrichment-composition proof
- **Final repository-wide merge validation:** Pending

## Review question

Does composition of the accepted SF-IMP-0016 bounded detail layer and SF-IMP-0017 structured ridge/spur/valley layer add useful internal variation to every accepted SF-IMP-0018 primary family without visually erasing Massif, Tableland, Spine, Basin, or Lobed identity?

## Numerical prerequisite

The local Java 25 verifier completed successfully before this visual review. The focused composition compatibility suite passed, including exact differential identity with the accepted legacy SF-IMP-0017 path. The fifteen-member full-resolution canonical acceptance suite also passed.

Therefore every composed specimen already satisfies the machine gate: positive occupancy, one face-connected solid component, zero domain-face contacts, at least 48 world units of sampled clearance, exact positive-inside density intersection, and canonical horizontal footprint-sign preservation relative to its SF-IMP-0018 primary family.

The lightweight review corpus independently reports one component and zero face contacts for all fifteen members. Its minimum sampled clearance ranges from 56 to 128 world units. These review-grid values are descriptive only; the full-resolution acceptance suite remains authoritative.

## Exact footprint comparison

The primary and composed `suspension-occupancy.png` artifacts are byte-identical for all fifteen corresponding specimens.

This is stronger visual evidence than merely similar outlines: the review-grid suspension-plane masks do not change at all under composition. It agrees with the analytical positive-factor construction and the full-resolution sign-preservation test.

## Family-by-family review

### Massif

Accepted. The compact crowned body remains visually dominant in isometric and orthogonal sections. Composition adds pronounced upper ridges and local underside variation without changing the broad deep-mass profile. The composed upper extent rises substantially, as intended for a ridge-dominant body, but the specimen does not become Tableland-, Spine-, Basin-, or Lobed-like.

### Tableland

Accepted with a design caveat. The broad compact footprint, shallow underside, and low overall vertical aspect remain distinct from Massif. The accepted structured-relief layer introduces visible ridges and corrugation across the formerly smooth plateau, so the upper surface is less literally flat after composition. Nevertheless the family still reads as a broad shallow tableland envelope rather than a crowned massif.

This is evidence that future secondary morphology should become family-aware: a mature Tableland treatment should likely preserve larger coherent planar or gently rolling regions instead of applying exactly the same ridge vocabulary used by Massif.

### Spine

Accepted strongly. The elongated footprint and longitudinal lower keel remain unmistakable in the isometric and section views. Structured relief adds localized high points along the narrow body without disrupting its dominant directional organization. Spine remains the most visually separable family after full composition.

### Basin

Accepted with a visualization/design caveat. The central depression remains clearly legible in both orthogonal sections after enrichment, so the defining vertical-profile identity survives composition. In the upper-elevation and current isometric renderer, local ridge/detail variation partially obscures the depression compared with the primary specimen.

This does not fail the composition proof, but it argues for basin-aware secondary structure and improved relief-aware evidence rendering before Basin controls become public semantic API.

### Lobed

Accepted. The broad four-shoulder connected footprint remains exact, and the composed isometric/upper views retain the non-elliptical primary outline. Local detail and secondary relief add internal structure without collapsing the family back toward a generic ellipse. As in SF-IMP-0018, the current lobe vocabulary remains rounded rather than deeply embayed; that is a future morphology-expansion question, not a composition failure.

## Review-grid magnitude

Relative to the SF-IMP-0018 primary atlas, composed review-grid solid occupancy increases modestly rather than explosively: approximately 2.48% to 5.17% across the fifteen specimens.

The sampled top bound rises by 24 to 56 world units depending on family and seed. The sampled lower bound moves downward by 0 to 16 world units. Basin shows the smallest upper-bound increase (24 units for all three review seeds), while Massif and Tableland show the largest typical upper increases. These are review-grid observations, not new compatibility constants.

## Cross-family conclusion

The visual gate passes.

The important result is not that every family receives the same surface appearance. It is that the accepted enrichment operators can be composed across all five primary bodies while their coarse identity remains legible in silhouette and/or vertical profile. The composition therefore validates the architectural separation between primary morphology and positive-factor enrichment.

The review also identifies the next design pressure: the current secondary layer is generic. It preserves family identity, but it does not yet express family-specific terrain semantics. Tableland would benefit from plateau-preserving relief rules, Basin from depression/rim-aware structure, Spine from explicitly longitudinal ridge logic, and Lobed from shoulder-aware organization.

Accordingly, family-aware secondary morphology should be evaluated before freezing a descriptor schema that would imply the present generic composition is the final semantic model.

## Follow-up

Before merge, run a complete repository-wide Java 25 `gradlew.bat check` after pulling this review commit. If green, SF-IMP-0019 is ready to be recorded as accepted and merged.
