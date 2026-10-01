# SF-IMP-0018 Primary Morphology-Family Visual Review

**Date:** 2026-08-28  
**Corpus:** `morphology-family-suspended-volume-v1`  
**Families:** Massif, Tableland, Spine, Basin, Lobed  
**Seeds per family:** `seed-min`, `seed-zero`, `seed-skyforge`  
**Decision:** Accepted as the first primary morphology-family proof

## Review question

Does SF-IMP-0018 establish materially different suspended-landform families in primary geometry itself, before bounded local detail or structured ridge/spur/valley relief are applied?

## Findings

The 15-member atlas passes the human design gate in ADR-0022.

### Massif

Massif reads as the deepest and most conventionally mountain-like suspended body in the set: a moderately elongated crowned upper mass paired with a concentrated deep underside. Across the three review specimens, the silhouette varies modestly while retaining the same family identity.

### Tableland

Tableland is visibly shallower than Massif in both orthogonal vertical sections and retains elevation farther toward mid-radius before dropping at the margin. Its footprint is compact and close to round. The existing isometric renderer understates the flatter upper profile, but the upper-elevation image and sections distinguish the family clearly.

### Spine

Spine is the strongest silhouette discriminator in the corpus. All three specimens are strongly elongated, with a narrow transverse profile and a deep longitudinal keel. Seed variation changes orientation/aspect while preserving the same directional family identity.

### Basin

Basin intentionally remains compact in outer silhouette, so it overlaps Tableland/Massif more strongly in a pure footprint comparison. Its vertical-profile identity is nevertheless unambiguous: both orthogonal sections show a materially lowered center between raised upper shoulders, and the upper-elevation maps expose the annular high region. This satisfies ADR-0022 requirement 9, which permits family identity in silhouette and/or vertical profile.

Across the review grids, Basin's center upper offset averages about 59.6 world units while the interior ring averages about 81.4, a roughly 21.8-unit center depression.

### Lobed

Lobed is clearly non-elliptical. Its silhouette forms a rounded four-shoulder / squarish star-shaped body across all three seeds, while remaining one continuous mass. This is materially distinct from the compact ellipses and the Spine family.

The first proof's lobes are deliberately broad and smooth; they do not yet create deep bays or narrow promontories. Stronger coastline articulation can be explored later without changing the conclusion that a connected non-elliptical primary family is viable.

## Cross-family separability

For the review-grid `seed-zero` masks, Spine has low overlap with the compact families (roughly 0.56-0.62 IoU), while Lobed remains visibly identifiable by its four-shoulder outline. Tableland and Basin have high outer-mask overlap, as expected, but are strongly separated by vertical profile. Within-family mask overlap remains high across the three seeds (approximately 0.83-1.00 depending on family), indicating bounded variation rather than family collapse.

The primary identities are therefore not merely numerical mask inequalities:

- Massif: crowned, deep suspended mass;
- Tableland: compact shallow table-like mass;
- Spine: elongated ridge/keel body;
- Basin: compact body with central upper depression and raised ring;
- Lobed: connected non-elliptical four-shoulder footprint.

## Review-grid topology context

The lightweight 8-unit atlas reports one connected component and zero domain-face contacts for all fifteen specimens. Minimum sampled clearance ranges from 64 world units for the most expansive Spine member to 128 world units for several compact families. These review-grid observations complement, but do not replace, the full-resolution numerical acceptance test executed earlier in the SF-IMP-0018 verifier.

## Decision

ADR-0022 requirement 9 is accepted. The five-family vocabulary is sufficiently distinct and useful to justify advancing SF-IMP-0018 to the final repository-wide validation checkpoint.

## Follow-up observations

Two visual-system/design improvements are explicitly non-blocking:

1. improve or supplement the isometric renderer so Tableland flatness and upper-profile differences read more clearly;
2. later explore a stronger Lobed variant with deeper bays/promontories while retaining the current analytical connectedness guarantees.

The next technical step after SF-IMP-0018 acceptance should be compatibility/composition: apply the accepted SF-IMP-0016 bounded detail and SF-IMP-0017 structured relief across the family matrix before promoting family semantics into a new descriptor schema.
