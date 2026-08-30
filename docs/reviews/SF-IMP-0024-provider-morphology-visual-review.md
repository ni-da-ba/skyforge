# SF-IMP-0024 Provider Morphology Visual Review

- **Work item:** SF-IMP-0024
- **Evidence corpus:** `provider-morphology-suspended-volume-v1`
- **Compared against:** accepted SF-IMP-0018 built-in family endpoints at the Skyforge seed
- **Review status:** Pass for provider-extensibility claim
- **Date:** 2026-08-30

## Review objective

Determine whether a morphology implemented outside Skyforge's built-in enum is visually distinct, remains a valid suspended body, and can interpolate continuously with every accepted built-in primary family through the public provider contract.

The visual corpus contains one standalone `reference:crescent` specimen plus 25/50/75-percent built-in contributions for Massif, Tableland, Spine, Basin, and Lobed. For review, those sequences were also compared against the accepted 100-percent built-in SF-IMP-0018 Skyforge-seed endpoints.

## Review-grid invariants

Across all 16 uploaded review specimens:

- connected solid components: **1 for every specimen**;
- domain-face contacts: **0 for every specimen**;
- sampled minimum clearance: **88 to 128 world units**;
- solid samples: **27,862 to 45,166**.

The standalone custom provider has 27,862 solid samples, one component, zero face contacts, and 112 world units minimum sampled clearance.

The full-resolution provider acceptance suite remains authoritative for the three-seed topology proof and exact common-footprint checks.

## Standalone custom morphology

`reference:crescent` is clearly outside the five accepted built-in silhouettes. Its bent coordinate frame creates an asymmetric curved shoulder/tail and a displaced mass axis rather than the centered ellipse, round table/basin, straight spine, or four-shoulder Lobed planforms.

However, the current reference name is somewhat stronger than the visual result. The silhouette reads more naturally as a **bent wedge, boomerang-like body, or curved teardrop** than as a classical concave crescent. The body intentionally has no hole or deep concave cut because this proof preserves the existing simply connected topology invariant.

This is a non-blocking design limitation. SF-IMP-0024 uses the shape as an extension-contract specimen, not as a polished built-in or user-facing morphology archetype.

## Pairwise findings

### Crescent ↔ Massif — pass

The curved custom tail/shoulder progressively relaxes while the body grows broader and more centrally crowned. The 25/50/75-percent intermediates are ordered and the accepted Massif endpoint completes the progression without a visible snap.

### Crescent ↔ Tableland — pass

The custom asymmetry progressively gives way to a broad, nearly circular Tableland planform. Vertical thickness simultaneously becomes shallower. The 50-percent specimen remains recognizably intermediate rather than collapsing directly to a round body.

### Crescent ↔ Spine — strong pass

This is the clearest provider-composition axis. The bent custom body progressively straightens and elongates into the accepted narrow Spine silhouette while the underside deepens. The 25/50/75/100-percent sequence is strongly ordered in both planform and section.

### Crescent ↔ Basin — pass

The custom tail and displaced mass axis progressively disappear as the planform becomes compact and round. The signal-free primary provider atlas does not yet apply Basin's family-aware secondary depression; therefore this stage primarily proves structural primary interpolation rather than complete enriched Basin semantics.

### Crescent ↔ Lobed — pass

The custom curved wedge broadens progressively and begins acquiring the non-elliptical Lobed outline toward the endpoint. Lobed's characteristic shoulders are most obvious late in the sequence, consistent with previous built-in hybrid reviews. No abrupt shape switch is visible.

## Cross-sequence assessment

All five custom-to-built-in paths form coherent 0/25/50/75/100-percent progressions. None exhibits a disconnected lobe, rim discontinuity, obvious self-intersection, or sudden parent switch in the evidence views.

The custom provider remains visibly influential at 25 and 50 percent. At 75 percent the relevant built-in dominates, as expected, while residual custom asymmetry remains visible in several axes. This is useful behavior for future island-chain authorship because provider blends can cover a continuous morphology space rather than functioning only as endpoint selectors.

## What this proves

The visual evidence, combined with the green full-resolution acceptance suite, demonstrates that a morphology implementation outside `MorphologyFamily` can:

1. compile through the public provider SPI;
2. produce an independently valid suspended island;
3. participate in deterministic provider-neutral hybridization with every accepted built-in family;
4. produce visually continuous intermediate primary morphologies without enum-specific compiler logic.

This is sufficient visual evidence for the SF-IMP-0024 provider-extensibility claim.

## Remaining limitation

This work item does **not** yet prove enriched custom-provider hybrids. Although `reference:crescent` and built-in providers can expose `SecondaryMorphologyContribution`, the current provider-neutral hybrid recipe blends primary structure only. The next composition step should consume those secondary contributions and prove custom↔built-in organized geography without changing the accepted provider-hybrid footprint.

A later morphology-design pass may also strengthen or rename the reference crescent geometry if a genuinely concave crescent silhouette is desired. That is not required for accepting the provider ABI.

**Visual gate: passed.**
