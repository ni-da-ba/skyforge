# SF-IMP-0025 Enriched Provider Morphology Visual Review

- **Work item:** SF-IMP-0025
- **Evidence corpus:** `enriched-provider-morphology-suspended-volume-v1`
- **Baseline corpus:** accepted SF-IMP-0024 `provider-morphology-suspended-volume-v1`
- **Review result:** **PASS**

## Review objective

Confirm that provider-aware bounded detail and secondary morphology can be applied to the accepted non-built-in `reference:crescent` provider and to all five custom-to-built-in blend axes without changing accepted primary silhouettes, breaking suspended-volume topology, or causing the secondary geography to snap to one parent.

The review treats the SF-IMP-0024 signal-free provider atlas as the primary-morphology authority. SF-IMP-0025 is accepted visually only if enrichment adds vertical/surface structure while preserving that primary planform progression.

## Corpus

The human-review corpus contains 16 members at the stable Skyforge seed:

- enriched standalone `reference:crescent`;
- `reference:crescent` → `skyforge:massif` at 25/50/75% built-in contribution;
- `reference:crescent` → `skyforge:tableland` at 25/50/75%;
- `reference:crescent` → `skyforge:spine` at 25/50/75%;
- `reference:crescent` → `skyforge:basin` at 25/50/75%;
- `reference:crescent` → `skyforge:lobed` at 25/50/75%.

Every member uses detail amplitude `1` and provider-aware secondary amplitude `1`.

## Quantitative review-grid evidence

Across all 16 enriched review members:

- connected solid components: **1 for every member**;
- domain-face contacts: **0 for every member**;
- sampled minimum clearance: **72 to 128 world units**;
- solid samples: **30,688 to 47,307**.

Compared with the corresponding accepted SF-IMP-0024 signal-free members:

- **16/16 `suspension-occupancy.png` artifacts are byte-identical**;
- **0/16 upper-surface artifacts are byte-identical**, confirming nontrivial upper enrichment;
- **0/16 underside artifacts are byte-identical**, confirming nontrivial underside detail;
- review-grid solid-sample count increases by **981 to 2,826** samples;
- minimum clearance is unchanged for 5 members, reduced by 8 units for 5 members, and reduced by 16 units for 6 members;
- all enriched members remain above the 48-unit acceptance floor;
- maximum sampled elevation rises by **16 to 40 world units** relative to the signal-free baseline.

The lowest review-grid clearance is **72 world units** at `crescent-to-spine-enriched-built-in-75`.

## Visual evidence interpretation

### Suspension-plane silhouette

The suspension-plane occupancy image is the strongest planform-invariance artifact. It encodes occupancy at the common suspension plane and therefore exposes changes to the accepted primary footprint directly.

All 16 enriched images are byte-identical to the accepted SF-IMP-0024 images. This proves, at review-grid resolution, that provider-aware enrichment changes vertical morphology rather than rewriting the accepted custom-to-built-in silhouette progression.

### Upper surface

The upper-surface view encodes sampled surface elevation. Relative to SF-IMP-0024, every member acquires visible bounded local structure and organized relief while retaining the same footprint boundary.

The external crescent endpoint remains recognizably the same curved wedge/boomerang-like proof morphology. The enrichment adds an interior high region and localized relief without straightening or circularizing the custom planform.

### Underside

The underside view shows bounded seeded underside detail over the provider-authored lower surface. Every member retains the accepted primary footprint while gaining clearly nontrivial underside modulation. No detached lower mass, inverted pocket, or visible footprint expansion appears.

### Orthogonal sections

East-west and north-south sections provide the clearest evidence that enrichment is genuinely vertical. They show increased upper relief and bounded lower detail while preserving one contiguous suspended body and the same suspension-plane rim.

The sections also make the family progression clearer than the isometric renderer: Massif becomes vertically heavier, Spine becomes increasingly elongated/deep, Tableland remains comparatively broad and shallow, Basin retains its lower-center/ring tendency, and Lobed retains its shoulder-driven planform while gaining secondary relief.

### Isometric occupancy

The isometric view confirms one suspended body and the primary planform progression, but the current renderer visually smooths or understates fine relief. It is therefore supporting evidence rather than the principal secondary-morphology review surface.

## Pairwise visual review

### reference:crescent → Massif — PASS

The progression remains ordered from the custom curved body toward the compact Massif. Enrichment makes the body vertically heavier and raises the crown without erasing the crescent-derived shoulder at lower Massif weights. No secondary-parent snap is visible.

### reference:crescent → Tableland — PASS

The primary transition toward the broader, shallower Tableland remains intact. Secondary geography is quieter than on Massif or Spine, which is consistent with the Tableland vocabulary. Added relief does not destroy the comparatively broad/table-like interior.

### reference:crescent → Spine — STRONG PASS

This remains the clearest interpolation axis. Increasing Spine weight produces a longer, narrower planform and deeper vertical section while enriched upper relief becomes progressively more axial. The 75% specimen reaches the corpus minimum clearance of 72 units but remains comfortably above the 48-unit gate.

### reference:crescent → Basin — STRONG PASS

The curved custom planform progressively relaxes while the Basin vertical vocabulary becomes more legible. The higher Basin-weight specimens preserve the broad lower-center/raised-surrounding-interior tendency rather than collapsing into generic roughness. Clearance remains 112–120 units across the progression.

### reference:crescent → Lobed — PASS

The transition remains coherent and topologically stable. Shoulder/lobe influence strengthens with built-in weight while custom curvature remains visible at lower weights. The secondary relief is comparatively subtle but does not remain fixed to the custom parent or switch abruptly to the Lobed parent.

## Acceptance findings

Observed across the corpus:

- no primary-silhouette drift;
- no disconnected mass;
- no detached lobe;
- no pinched-through rim;
- no visible parent switch in secondary geography;
- no evidence that the custom provider's secondary contribution is ignored;
- no evidence that built-in secondary geography overwrites the custom contribution wholesale;
- no topology regression caused by the canonical provider carrier.

The provider-aware enrichment claim is therefore visually supported.

## Limitations

- The current `reference:crescent` remains a proof morphology rather than a polished archetype; its silhouette reads more like a bent wedge/boomerang or curved teardrop than a classical deeply concave crescent.
- The current isometric renderer is too smooth to be the primary inspection surface for fine secondary relief.
- Tableland and Lobed secondary differences are visually quieter than Massif, Spine, and Basin at this review resolution. This is not blocking because the transitions remain coherent and the numerical/focused gates independently prove composition and control isolation.
- This review proves pairwise provider-aware enrichment. Arbitrary normalized N-way morphology mixtures remain intentionally deferred.

## Verdict

**Human visual gate: PASS.**

SF-IMP-0025 demonstrates that a genuinely non-built-in provider can retain authority over its primary geometry while participating in Skyforge's bounded-detail and provider-aware secondary-morphology system, including continuous blends with all five accepted built-in providers.
