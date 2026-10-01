# SF-IMP-0020 Family-Aware Secondary Morphology Visual Review

- **Date:** 2026-08-28
- **Work item:** SF-IMP-0020
- **Decision:** Visual design gate accepted
- **Comparison baseline:** accepted SF-IMP-0019 generic cross-family composition
- **Review corpus:** `family-aware-morphology-suspended-volume-v1`

## Review question

Does family-aware secondary morphology materially improve the geographic identity of the accepted
Massif, Tableland, Spine, Basin, and Lobed primary families relative to the generic SF-IMP-0019
ridge/spur/valley treatment, while preserving their primary silhouette and accepted underside?

The answer is **yes** for the first family-aware proof.

## Evidence invariants visible in the corpus

The SF-IMP-0020 and SF-IMP-0019 review corpora were compared specimen-for-specimen across the same
five families and three root seeds.

- All fifteen `suspension-occupancy.png` images are byte-identical between SF-IMP-0019 and
  SF-IMP-0020.
- All fifteen `underside-surface.grid` artifacts are byte-identical between SF-IMP-0019 and
  SF-IMP-0020.
- Massif upper-surface grids are also byte-identical to SF-IMP-0019, preserving it as the explicit
  control family.
- Tableland, Spine, Basin, and Lobed all produce changed upper-surface grids, so the family-aware
  path is not a no-op.

Review-grid topology remains healthy in every specimen: one connected component and zero domain-face
contacts are preserved throughout the atlas.

## Family findings

### Massif — accepted control

Massif remains visually unchanged from SF-IMP-0019 as intended. Its crowned upper mass, deep central
underside, and generic ridge/spur/valley vocabulary remain suitable as the comparison control.

This is important because SF-IMP-0020 should demonstrate selective specialization rather than a
project-wide morphology rewrite.

### Tableland — clear improvement

Tableland is the strongest evidence that family-aware secondary morphology is necessary.

The generic SF-IMP-0019 treatment frequently drove narrow peaks through what should read as a broad
plateau interior. SF-IMP-0020 suppresses secondary relief toward the center and moves most organized
variation outward toward the margins.

In the east-west review sections, the central occupied upper-profile range changes as follows:

- `seed-min`: 40 -> 32 world units
- `seed-zero`: 64 -> 24 world units
- `seed-skyforge`: 40 -> 24 world units

The seed-zero generic specimen reached y=400 at the center; the family-aware specimen instead forms a
much broader, lower y=360 plateau. The other two seeds similarly lose the isolated generic peaks.
The result reads materially more like a tableland rather than a compact island with arbitrary
mountain ridges.

### Spine — accepted improvement

Spine retains its unmistakable elongated footprint and longitudinal keel while the upper relief is
more coherently organized around the major axis.

The generic composition could produce isolated high peaks inconsistent with the family's directional
identity. The family-aware axial-ridge/pass treatment reduces those arbitrary maxima while retaining
strong directional relief. For example, seed-zero's east-west section maximum falls from y=400 to
y=376 while the elongated silhouette and deep lower keel remain unchanged.

The family still reads as a Spine rather than an elongated Massif.

### Basin — clear improvement

Basin benefits substantially from explicitly preserving and reinforcing the center-to-ring
relationship.

The generic treatment could partially obscure the depression with a generic ridge crossing the
interior. SF-IMP-0020 retains the depressed center and adds stronger surrounding relief. In the
seed-zero east-west section, center-to-maximum contrast increases from roughly 8 world units
(center y=336, max y=344) to roughly 40 world units (center y=312, max y=352). For the Skyforge seed,
the same comparison grows from roughly 24 to 56 world units.

This makes the Basin identity far more legible in section evidence while preserving one continuous
suspended mass.

### Lobed — accepted, with room for later refinement

Lobed keeps its byte-identical four-shoulder primary silhouette while replacing the generic secondary
relief with a shoulder/saddle-aware treatment. The changed upper grids are visibly more restrained
than SF-IMP-0019 and no longer allow the same isolated generic peaks to dominate the body.

The result is a useful improvement, but Lobed remains the least mature of the five family-specific
secondary vocabularies. The current four-shoulder primary form itself is intentionally broad and
rounded, and the existing evidence renderer does not make shoulder-versus-saddle organization as
obvious as the Tableland and Basin improvements.

This is a follow-up design opportunity, not an SF-IMP-0020 blocker.

## Quantitative review-grid observations

Relative to SF-IMP-0019:

- Massif solid-sample counts are unchanged for all three seeds.
- Basin changes only slightly (-34 to -104 review-grid solid samples) while materially strengthening
  the visible depression/ring organization.
- Spine changes modestly (-189 to -243 samples) while retaining its full primary extent.
- Tableland decreases by roughly 1,191 to 1,307 samples, reflecting removal of excessive generic
  interior peaks rather than footprint loss.
- Lobed decreases by roughly 1,054 to 1,178 samples as the generic high-relief crown is replaced by
  the more restrained family-aware treatment.

Minimum review-grid clearance never regresses below the already accepted SF-IMP-0019 margins.
Tableland actually increases to 128 world units of minimum review-grid clearance in all three
specimens.

## Decision

**Accept SF-IMP-0020's visual design gate.**

Family-aware secondary morphology is a material improvement over applying one generic terrain
vocabulary to every primary family. The proof establishes that secondary organization can be
specialized by primary morphology without changing primary silhouette, underside identity, graph
schema, or descriptor schema.

The evidence also changes the architectural recommendation from SF-IMP-0019: family-aware secondary
morphology is now sufficiently concrete to inform the next semantic-API decision. Descriptor-schema
promotion should be evaluated only after the final numerical/repository acceptance checkpoint for
this work item.

## Non-blocking follow-ups

1. Lobed shoulder/saddle semantics can be strengthened in a future iteration if the primary Lobed
   family itself is expanded beyond the current rounded four-shoulder proof.
2. Relief-aware top-down shading or contours would make family-specific organization easier to review
   than the current isometric occupancy projection.
3. Future semantic controls should separate local-detail amplitude from family-aware secondary-relief
   amplitude rather than continuing to overload one `signalAmplitude` value.
