# SF-IMP-0019 Cross-Family Composition Numerical Acceptance

**Date:** 2026-08-28  
**Status:** Numerically accepted; human visual review pending  
**Environment:** Eclipse Temurin OpenJDK 25.0.4.1 LTS on Windows

## Scope

This checkpoint validates the SF-IMP-0019 generic enrichment composition and the full-amplitude composition of the accepted SF-IMP-0016 bounded detail and SF-IMP-0017 structured relief layers across the fifteen-member SF-IMP-0018 primary-family matrix.

## Focused compatibility gate

The focused recipe suite completed successfully:

```text
gradlew.bat :skyforge-recipes:test --tests "io.github.nidaba.skyforge.recipes.skyisland.ComposedMorphologySkyIslandVolumeRecipeTest"
BUILD SUCCESSFUL
```

This suite includes differential identity between the generic composition transform and the accepted legacy SF-IMP-0017 path when both are applied to the original primary suspended volume. It also validates zero-amplitude family identity, factor envelopes, exact density intersection semantics, deterministic graph bytes, and family footprint preservation at representative samples.

## Full SF-IMP-0019 verifier

`scripts\verify-sf-imp-0019.bat` completed successfully through all four stages:

1. Java runtime check;
2. generic composition compatibility and family recipe tests;
3. fifteen-member full-resolution composed-family acceptance;
4. fifteen-member lightweight composed-family visual-atlas generation.

The full-resolution acceptance stage uses the canonical `193 x 129 x 193` domain and passed all fifteen family/seed specimens. The acceptance contract requires positive occupancy, exactly one face-connected solid component, zero domain-face contacts, at least 48 world units sampled clearance, exact primary-family footprint-sign preservation at every canonical horizontal sample, and five distinct family masks per shared seed.

## Review-grid corpus observations

The 8-unit visual-review corpus (`97 x 65 x 97`) also reports one component and zero face contacts for every specimen. Its observed minimum-clearance range is 56 to 128 world units:

| Member | Solid samples | Components | Face contacts | Minimum clearance |
|---|---:|---:|---:|---:|
| massif-seed-min | 58,339 | 1 | 0 | 80 |
| massif-seed-zero | 56,854 | 1 | 0 | 72 |
| massif-seed-skyforge | 59,634 | 1 | 0 | 72 |
| tableland-seed-min | 43,965 | 1 | 0 | 120 |
| tableland-seed-zero | 42,976 | 1 | 0 | 112 |
| tableland-seed-skyforge | 42,597 | 1 | 0 | 120 |
| spine-seed-min | 35,170 | 1 | 0 | 72 |
| spine-seed-zero | 36,923 | 1 | 0 | 56 |
| spine-seed-skyforge | 38,280 | 1 | 0 | 56 |
| basin-seed-min | 45,293 | 1 | 0 | 120 |
| basin-seed-zero | 47,181 | 1 | 0 | 120 |
| basin-seed-skyforge | 44,234 | 1 | 0 | 128 |
| lobed-seed-min | 43,422 | 1 | 0 | 120 |
| lobed-seed-zero | 46,345 | 1 | 0 | 112 |
| lobed-seed-skyforge | 43,153 | 1 | 0 | 112 |

These review-grid metrics are descriptive visual-corpus observations, not substitutes for the full-resolution acceptance stage.

## Decision

SF-IMP-0019 requirements 1 through 8 are accepted locally. Requirement 9 remains the human visual gate: the composed atlas must show that the accepted detail and structured-relief layers add useful terrain organization without erasing Massif, Tableland, Spine, Basin, or Lobed primary identity.

Hosted GitHub Actions are not used as evidence at this checkpoint because the repository Actions allowance remains exhausted.
