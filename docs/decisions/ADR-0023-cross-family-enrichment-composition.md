# ADR-0023: Cross-Family Enrichment Composition

- **Status:** Proposed pending local numerical and visual acceptance
- **Date:** 2026-08-28
- **Work item:** SF-IMP-0019

## Context

SF-IMP-0016 accepted bounded deterministic upper/underside detail on the original suspended-volume
primary body. SF-IMP-0017 accepted structured upper-surface ridge, spur, and valley relief on that
same body. SF-IMP-0018 then established five materially different primary suspended-landform
families: Massif, Tableland, Spine, Basin, and Lobed.

The next risk is compositional rather than generative. The accepted enrichment layers were originally
implemented against node identities from the first primary recipe. Before promoting morphology-family
selection into a semantic descriptor schema, Skyforge must prove that those same enrichment contracts
can operate across every accepted primary family without erasing family identity or weakening the
suspended-volume invariants.

## Decision

SF-IMP-0019 introduces `ComposedMorphologySkyIslandVolumeRecipe`, recipe version 5. For one family and
one descriptor it:

1. compiles the accepted SF-IMP-0018 primary family with signal amplitude forced to zero;
2. applies the accepted SF-IMP-0016 bounded seeded upper and underside factors;
3. applies the accepted SF-IMP-0017 structured upper-surface factor;
4. rebuilds density as the exact positive-inside intersection of the resulting upper and underside
   surfaces.

At zero signal amplitude the composition recipe returns the exact SF-IMP-0018 primary-family artifact.
No descriptor schema or graph schema changes are introduced.

## Generic enrichment compatibility boundary

`SuspendedVolumeEnrichmentComposition` captures the already accepted graph rewrite as a transform over
any compatible primary `CompiledSkyIslandVolume` that exposes the stable structural nodes:

- `upper.offset`
- `underside.offset`
- `upper.surface`
- `underside.surface`
- `descriptor.suspension-elevation`
- `profile.along-normalized`
- `profile.across-normalized`
- `position.y`
- the accepted density constraint/intersection nodes.

The transform requires the base descriptor to equal the target descriptor with signal amplitude set
to zero. This prevents the composition layer from silently changing any other semantic control.

The generic transform is differential-tested against the accepted legacy path: when supplied the
original signal-free primary volume, it must produce a `CompiledSkyIslandVolume` exactly equal to
`SecondaryMorphologySkyIslandVolumeRecipe` for the same full-amplitude descriptor. This pins graph
node ordering, constants, seed namespaces, provenance, schema version, and arithmetic behavior.

## Preserved analytical envelopes

The SF-IMP-0016 factors remain unchanged:

```
0.85 <= upperDetailFactor <= 1.15
0.85 <= undersideDetailFactor <= 1.15
```

The SF-IMP-0017 structured upper factor remains unchanged:

```
0.76 <= secondaryUpperFactor <= 1.48
```

All factors are strictly positive. Therefore the composed upper offset has the same sign as the
primary upper offset, and the composed underside offset has the same sign as the primary underside
offset. Consequently, for every family:

- the primary horizontal footprint is preserved exactly;
- upper and underside still meet on the same primary silhouette;
- no local detail or structured relief can create a disconnected footprint island;
- the family-specific primary silhouette cannot be erased by composition.

Density remains:

```
min(composedUpper - y, y - composedUnderside)
```

## Acceptance corpus

The first composition proof reuses the accepted SF-IMP-0018 matrix:

```
5 families x 3 root seeds = 15 specimens
```

The root seeds remain `Long.MIN_VALUE`, `0`, and `0x534b59464f524745`. Every composition specimen uses
full signal amplitude `1.0`.

Numerical acceptance uses the canonical v0.2 domain and 4-unit spacing (`193 x 129 x 193`). Human
visual review uses the same world bounds at 8-unit spacing (`97 x 65 x 97`) so it can be compared
directly with the SF-IMP-0018 primary-family atlas.

## Acceptance requirements

SF-IMP-0019 must demonstrate:

1. the generic enrichment transform exactly reproduces the accepted SF-IMP-0017 compiled result when
   applied to the original signal-free primary volume;
2. zero-amplitude family composition returns the exact SF-IMP-0018 primary artifact;
3. the SF-IMP-0016 signal factors remain within `[0.85, 1.15]` on every family;
4. the SF-IMP-0017 structured upper factor remains within `[0.76, 1.48]` on every family;
5. density remains the exact positive-inside intersection of the composed upper and underside
   surfaces;
6. every canonical horizontal sample retains the same primary-family footprint sign after full
   composition;
7. all fifteen full-resolution specimens retain positive occupancy, exactly one face-connected
   solid component, zero domain-face contacts, and at least 48 world units of sampled clearance;
8. the five family masks remain numerically distinct after full composition;
9. the human atlas shows that bounded detail and structured relief add useful internal variation
   without visually erasing Massif, Tableland, Spine, Basin, or Lobed primary identity.

Requirement 9 is a human design gate. Numerical topology and mask identity are necessary but do not
prove that the composed terrain remains legible as its intended family.

## Deferred work

SF-IMP-0019 does not yet:

- add morphology family to `SkyIslandVolumeDescriptor`;
- expose independent user-facing amplitudes for local detail versus structured relief;
- hybridize primary families;
- generate multiple islands or archipelagos;
- add drainage, erosion, caves, arches, materials, climate, biomes, ecology, or Minecraft realization.

If this proof is accepted, the next architecture decision can evaluate descriptor-schema promotion
versus a separate hybridization proof. The evidence from SF-IMP-0018 and SF-IMP-0019 should determine
which family and composition controls are stable enough to become semantic API.
