# SF-IMP-0022 Primary Morphology Hybridization Visual Review

- **Date:** 2026-08-28
- **Work item:** SF-IMP-0022
- **Evidence:** `hybrid-morphology-suspended-volume-v1`
- **Comparison baseline:** accepted `morphology-family-suspended-volume-v1` Skyforge-seed parent specimens
- **Visual gate:** Accepted

## Review method

The SF-IMP-0022 review atlas contains all ten unordered pairs of the five accepted built-in primary morphology families. Each pair is rendered at second-family weights `0.25`, `0.50`, and `0.75` on the 8-unit review grid. Review additionally places the accepted SF-IMP-0018 Skyforge-seed parent specimens at weights `0.00` and `1.00`, producing a five-step progression for each pair.

The review inspects suspension-plane silhouette, upper-surface projection, underside projection, isometric occupancy, and east-west/north-south vertical sections. The question is not whether interpolation is merely detectable, but whether the five-step sequence behaves like a coherent continuous morphology space while retaining suspended-volume identity.

The uploaded review-grid summary reports one component and zero face contacts for all 30 visual specimens. Review-grid minimum clearance ranges from 72 to 128 world units, and solid sample count ranges from 34,809 to 53,709. These are descriptive review-grid facts; canonical full-resolution acceptance remains the separate numerical gate.

## Overall finding

**The primary hybridization visual gate passes.**

All ten family pairs show coherent intermediate landforms. No progression exhibits abrupt parent switching, detached lobes, pinched-through rims, obvious self-intersection, or a visual topology failure. The suspension-plane silhouette changes continuously from the first accepted parent toward the second. Upper and underside projections change consistently with that silhouette, while the orthogonal sections show continuous changes in crown, basin, keel, and underside-depth character.

The result is materially more useful than a discrete five-family selector: the built-ins now span a continuous primary-morphology space that can support varied island populations without adding a new named family for every intermediate form.

## Pair-by-pair review

### Massif → Tableland

Accepted. The elongated Massif footprint contracts and rounds continuously. The underside becomes progressively shallower and less concentrated. The vertical sections move from the deeper compact Massif body toward the thinner Tableland envelope without a discontinuity. The upper difference is intentionally subtler than some other pairings because both parents retain a broad elevated crown.

### Massif → Spine

Accepted strongly. This is one of the clearest interpolation families. The footprint aspect ratio increases continuously, the transverse width narrows, and the underside develops the Spine-like longitudinal keel. The 25/50/75 specimens all read as useful intermediate suspended landforms rather than scaled copies of either endpoint.

### Massif → Basin

Accepted strongly. The Massif crown progressively gives way to a central upper depression and raised surrounding interior. The basin is already legible by the midpoint and strengthens toward the Basin endpoint. The underside simultaneously becomes shallower and more radially distributed. This pair demonstrates that hybridization can interpolate a vertical-profile semantic, not just a planform silhouette.

### Massif → Lobed

Accepted. The Massif ellipse progressively acquires the Lobed family’s broad shoulder/promontory structure. The transition is continuous in silhouette and upper surface. Lobed character becomes visually strongest in the latter half of the progression; this is a useful non-blocking observation rather than a failure.

### Tableland → Spine

Accepted strongly. The broad compact Tableland transitions continuously into an elongated narrow Spine. The upper surface and orthogonal sections show the expected loss of broad plateau-like extent and development of the Spine’s deeper/narrower body. This is another high-value hybrid axis for generating controlled variation.

### Tableland → Basin

Accepted. The outer silhouette changes only modestly, which is appropriate because these parents have similarly compact footprints. The meaningful interpolation occurs vertically: a central depression progressively develops while the body retains a comparatively shallow compact envelope. This pair is important evidence that the hybrid system does not require large silhouette change to create a distinct intermediate morphology.

### Tableland → Lobed

Accepted. The compact near-round footprint gradually develops the Lobed rounded-square/four-shoulder structure. Vertical-profile changes are more restrained than in Spine or Basin pairings, but the 25/50/75 sequence remains coherent and useful.

### Spine → Basin

Accepted strongly. The highly elongated Spine broadens and rounds continuously while the Basin center depression emerges. Both directional anisotropy and vertical-profile identity change across the blend. The midpoint remains a plausible single suspended mass rather than appearing as two incompatible shapes averaged together.

### Spine → Lobed

Accepted. Elongation relaxes while broad lobed shoulders emerge. As with other Lobed pairings, the Lobed signature becomes visually dominant later than the Spine signature recedes, but the progression remains continuous and contains useful intermediate forms.

### Basin → Lobed

Accepted. The Basin’s central depression fades continuously while the outer silhouette develops Lobed shoulders. This pairing provides a useful transition between two non-Massif compact families and confirms that the hybrid space is not organized solely around Massif as a neutral center.

## Structural observations

### 1. Continuous primary morphology space is visually credible

Across all ten unordered pairs, the 25/50/75 intermediates are visually ordered between the accepted endpoints. The strongest axes are Massif↔Spine, Massif↔Basin, Tableland↔Spine, and Spine↔Basin. More similar parents such as Tableland↔Basin primarily interpolate vertical profile rather than silhouette, which is desirable rather than a weakness.

### 2. Lobed influence is somewhat back-loaded

Lobed pairings interpolate safely, but the distinctive rounded-square/four-shoulder silhouette tends to become unmistakable closer to the Lobed end of the blend. A future weighting curve or a stronger Lobed primary model could make perceptual influence more linear if desired. The current behavior remains coherent and does not block acceptance.

### 3. Primary-only scope remains appropriate

The atlas intentionally contains signal-free primary morphology. Family-aware secondary geography is not yet blended across two parents. This means the current hybrids prove primary geometry and provider composition, not the final enriched appearance of hybrid islands. A later work item should define how secondary-morphology policies combine or select across hybrid parents before hybrids are promoted as a complete enriched semantic descriptor.

### 4. Provider seam is justified by the result

The successful blend construction supports retaining the internal primary-morphology provider abstraction. Composition code can consume provider-produced primary structural graphs rather than depending directly on the built-in family switch. This is the correct direction for eventual registered/custom morphology providers, provided a future public contract defines deterministic identity, structural capabilities, closure/rim guarantees, and validation requirements.

## Decision

SF-IMP-0022 passes the human visual gate for primary morphology hybridization.

Acceptance of the work item still requires the dedicated full-resolution numerical verifier and final repository-wide `gradlew.bat check` on the exact merge candidate. No public custom-provider ABI, descriptor schema 3 hybrid representation, secondary-morphology blending, or island-chain placement is accepted by this visual review.

## Non-blocking follow-ups

1. Evaluate perceptual blend curves for Lobed influence after the primary contract is accepted.
2. Define secondary-morphology composition for hybrids before exposing enriched hybrids through a new semantic descriptor schema.
3. Use the internal provider seam as evidence for a later explicit custom-provider contract, but do not publish it unchanged without capability/invariant design.
4. Once hybrid/enriched single-island semantics stabilize, begin island-chain/group placement as a separate composition layer rather than baking inter-island relationships into the single-island morphology descriptor.
