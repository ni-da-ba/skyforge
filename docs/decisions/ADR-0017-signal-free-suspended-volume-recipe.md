# ADR-0017: Signal-free suspended-volume recipe

**Status:** Accepted
**Date:** 2026-08-04
**Ticket:** SF-IMP-0013

## Context

ADR-0016 accepts the semantic descriptor, positive-inside signed-density convention, pointwise
intersection, and canonical 3D evidence domain needed for a finite sky island. The first recipe
must now define independently inspectable upper and underside surfaces without adding seeded
detail, general composition, mesh extraction, or backend behavior.

The construction must remain ordered inside the footprint and reversed outside it. Otherwise the
intersection could leave the intended rim open or create remote solid bands. Underside taper and
asymmetry also need predictable effects that cannot make their shaping factor negative.

## Decision

1. Add `SignalFreeSkyIslandVolumeRecipe` version 1 and `CompiledSkyIslandVolume`. A compilation
   contains the descriptor, recipe and graph-schema versions, two scalar-field-2 surface graphs,
   one scalar-field-3 density graph, and a semantic-control-to-node provenance map.
2. Use the same area-neutral elliptical ridge frame as the released v0.1 morphology. For
   normalized squared radius `q`, let `s = 1 - q`. Both surfaces meet the suspension elevation
   exactly where `q = 1`.
3. Define the upper surface as

   `upper = anchor + upperElevation * s * (1 + coastalShape * q)`,

   where `coastalShape = 1 - coastalFalloff / nominalRadius`. This preserves the declared crown
   height and rim while allowing the coastal approach to change.
4. Define the underside as

   `lower = anchor - undersideDepth * s / (1 + taper * q) * (1 + a*u + (a*u)^2)`,

   where `u` is the normalized coordinate along the ridge and
   `a = 0.25 * undersideAsymmetry`. The taper denominator is at least one for every `q >= 0`.
   The asymmetry factor is at least `0.75` for every finite `u`, so it cannot reverse the lower
   constraint.
5. Define final density exactly as

   `min(upper - y, y - lower)`.

   The density is positive only between the ordered surfaces. Since `s` changes sign at the rim
   while every other lower-shaping factor stays positive, upper is below lower outside the
   footprint and no exterior vertical band is solid.
6. Reject nonzero signal amplitude. Seed and signal scale remain descriptor provenance but do not
   affect signal-free graph bytes; the provenance entry for signal controls is present and empty
   to state that they are intentionally inactive.
7. Fix graph node identifiers around semantic roles (`upper.surface`, `underside.surface`,
   `density.upper-constraint`, `density.lower-constraint`, and
   `density.solid-intersection`) so evidence can trace controls without depending on node order.

## Consequences

- The canonical descriptor produces a closed, one-component suspended mass at the diagnostic
  resolution with zero contact on all six evidence-domain faces.
- Upper elevation changes only the crown; underside depth changes only the lower surface;
  suspension elevation translates both.
- Increasing taper concentrates depth toward the nadir. Signed asymmetry moves lower mass along
  the ridge while leaving the rim and crown unchanged.
- The two surface graphs require canonical graph schema 1. The compiled volume declares schema 3
  because its density graph contains the accepted intersection node.
- The recipe establishes analytical morphology and inspectable provenance. Canonical 3D grids,
  permanent metrics, deterministic review images, and golden checksums remain the responsibility
  of SF-IMP-0014 and SF-IMP-0015.

## Compatibility

No v0.1 descriptor, recipe, evaluator result, graph-schema-1/2 encoding, workflow, dependency, or
fixed-seed corpus path changes. Java 25 CI must continue to run the entire legacy suite and verify
all 49 released corpus paths.
