# ADR-0016: Suspended density, intersection, and evidence domain

**Status:** Accepted
**Date:** 2026-08-04
**Ticket:** SF-IMP-0012

## Context

ADR-0015 requires a finite positive-inside density volume before Skyforge adds secondary
morphology. The v0.1 kernel already supports three-dimensional scalar fields and arithmetic, but it
does not yet own an exact solid/air classification, a composition node for the simultaneous upper
and lower constraints, a semantic suspended-volume descriptor, or a canonical 3D evidence domain.

Those contracts must be fixed before the first volume recipe is written. Otherwise the recipe
would silently choose architecture while appearing to implement morphology.

## Decision

1. Add `SkyIslandVolumeDescriptor` schema 1 with semantic controls for horizontal center,
   suspension elevation, nominal radius, upper elevation, underside depth, coastal falloff, primary
   ridge, underside taper, signed underside asymmetry, and bounded signal enrichment. Ridge
   azimuth is an unoriented axis canonicalized to `[0, pi)`. Strength, taper, and signal amplitude
   lie in `[0,1]`; underside asymmetry lies in `[-1,1]`.
2. Define finite signed density exactly: values greater than zero are solid, either signed zero is
   surface, and values below zero are air. Evidence classification rejects NaN and both infinities.
3. Add one graph primitive, `IntersectionNode`, restricted to two
   `SCALAR_FIELD_3` inputs. It evaluates as Java `Math.min(left, right)`. This preserves negative
   zero, handles infinities according to `Math.min`, and propagates NaN. Evidence consumers must
   reject a non-finite final density rather than assigning it to a spatial region.
4. Add canonical graph schema 3 for intersection nodes. Its encoding is an ordered two-input node
   with kind `intersection` and output type `scalar-field-3`. Schemas 1 and 2 retain their exact
   minimum-version rule and canonical bytes.
5. Accept the first signal-free reference descriptor at center `(0,0)`, suspension elevation 256,
   radius 256, upper elevation 96, underside depth 128, coastal falloff 64, ridge azimuth `pi/6`,
   ridge strength 0.65, underside taper 0.60, underside asymmetry 0.25, and zero signal amplitude.
6. Accept an inclusive evidence domain of `x,z in [-384,384]` and `y in [0,512]` at
   `193 x 129 x 193` samples. Spacing is exactly four world units on every axis. Canonical storage
   increments x first, then z, then y, for 4,805,121 density samples. One raw binary64 density grid
   therefore occupies 38,440,968 bytes before container metadata.

## Consequences

- The first recipe can construct `min(upper(x,z)-y, y-lower(x,z))` without introducing a general
  blend, Boolean algebra, mesh, or constraint system.
- The descriptor owns meaning while the recipe remains free to choose the first analytical upper
  and lower functions.
- The standard domain leaves declared analytical margins around the descriptor's nominal primary
  envelope: 128 units on each horizontal side, 128 below, and 160 above.
- Schema-3 graphs may contain schema-1 arithmetic and schema-2 signal nodes, but the presence of an
  intersection fixes the minimum canonical schema to 3.
- The 3D grid is large enough to expose closure and suspension while remaining practical for the
  deliberately simple reference evaluator. Timing remains observational and cannot accept or
  reject a result.
- SF-IMP-0013 now owns the signal-free upper/underside recipe and provenance. Deterministic 3D
  sampling, metrics, permanent review images, and golden gates remain in SF-IMP-0014 and later.

## Compatibility

No v0.1 descriptor, recipe, node, evaluator result, graph-schema-1/2 encoding, or fixed-seed corpus
path is changed. Java 25 CI must continue to run the complete v0.1 suite and verify all 49 released
corpus paths for every v0.2 ticket.
