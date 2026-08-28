# ADR-0024: Family-Aware Secondary Morphology

- **Status:** Accepted by local numerical validation and human visual review
- **Date:** 2026-08-28
- **Work item:** SF-IMP-0020

## Context

SF-IMP-0019 proved that the accepted SF-IMP-0016 bounded-detail layer and SF-IMP-0017 structured
ridge/spur/valley layer can be composed safely across all five accepted SF-IMP-0018 primary
morphology families. The full family matrix retained topology, exact primary footprints, and family
identity.

The SF-IMP-0019 visual review also exposed the next architectural limitation: every family receives
the same secondary ridge/spur/valley vocabulary. That is safe but not yet a mature morphology model.
Tableland becomes more corrugated than a plateau-like family should, Basin's ring/depression identity
is not explicitly reinforced by the secondary layer, and Lobed relief does not yet respond directly
to the primary lobe field.

Before morphology-family controls are promoted into a public descriptor schema, Skyforge should prove
that secondary morphology can be selected according to primary-family structure while preserving the
already accepted composition invariants.

## Decision

SF-IMP-0020 introduces `FamilyAwareMorphologySkyIslandVolumeRecipe`, recipe version 6. It uses the
accepted SF-IMP-0019 composition as an inspectable baseline and replaces only the final upper
secondary selection.

The construction order is:

1. compile the accepted SF-IMP-0018 signal-free primary family;
2. apply the accepted SF-IMP-0016 bounded upper and underside detail;
3. retain the SF-IMP-0019 generic SF-IMP-0017 secondary subgraph as an inspectable comparison
   baseline;
4. rebuild the final upper surface from `upper.offset.seeded` using a positive family-aware factor;
5. preserve the SF-IMP-0019 underside exactly;
6. rebuild density as the exact positive-inside intersection of the selected upper surface and the
   accepted underside.

At zero signal amplitude the recipe returns the exact SF-IMP-0018 primary-family artifact. No
descriptor schema or graph schema change is introduced.

## Family-aware vocabulary

### Massif — control

Massif retains the exact SF-IMP-0017 generic structured factor as the control morphology. This
provides a stable comparison against SF-IMP-0019 and prevents the first family-aware proof from
changing every family simultaneously.

Its accepted analytical factor envelope remains:

```
0.76 <= factor <= 1.48
```

### Tableland — plateau-preserving peripheral relief

Tableland uses broad rim/shoulder structure and a bounded edge-cut corridor. All secondary terms are
multiplied by:

```
r2 / (0.35 + r2)
```

where `r2` is the accepted family-normalized primary radius squared. The gate is exactly zero at the
primary center and remains below one everywhere, so family-aware secondary relief cannot corrugate
the plateau center.

The analytical factor envelope is:

```
0.93 <= factor <= 1.15
```

### Spine — axial ridge and transverse pass

Spine uses a strong narrow basis aligned with the accepted primary long axis, a weaker oblique spur,
and a bounded transverse negative corridor representing pass-like relief. Seed variation may shift
or rotate these features slightly but cannot replace the primary axial organization.

The analytical factor envelope is:

```
0.86 <= factor <= 1.32
```

### Basin — annular reinforcement and drainage-like corridor

Basin uses a smooth annular basis centered near the accepted raised interior ring plus one bounded
corridor crossing the ring. Both terms are multiplied by a center-preservation gate:

```
r2 / (0.15 + r2)
```

so the family-aware secondary factor is exactly one at the basin center. The corridor is morphological
only; SF-IMP-0020 does not claim drainage simulation or hydrological correctness.

The analytical factor envelope is:

```
0.88 <= factor <= 1.18
```

### Lobed — shoulder reinforcement and inter-lobe saddle

Lobed morphology directly reuses the accepted primary `family.lobe-directional` field. Positive
secondary relief therefore follows the broad primary shoulders rather than ignoring them. A bounded
diagonal corridor creates saddle-like separation between shoulders and a weaker local ridge provides
additional organization.

The analytical factor envelope is:

```
0.90 <= factor <= 1.24
```

## Shared invariants

Every selected family-aware factor is strictly positive. Therefore:

- the sign of the seeded upper offset is unchanged;
- the exact SF-IMP-0018 horizontal family footprint is unchanged;
- the selected upper surface still meets the underside on the accepted primary silhouette;
- no family-aware upper feature can create a separate horizontal footprint component;
- the accepted SF-IMP-0019 underside remains unchanged;
- density remains `min(upper - y, y - underside)`.

The generic SF-IMP-0019 secondary nodes remain in the graph during this proof as an inspectable
comparison subgraph. They are not on the final output path for Tableland, Spine, Basin, or Lobed.
A later cleanup may remove that baseline after family-aware behavior is accepted and no longer needs
in-graph differential inspection.

## Seed namespaces

Family-aware construction uses stable namespaces under:

```
sky-island.family-aware.tableland.*
sky-island.family-aware.spine.*
sky-island.family-aware.basin.*
sky-island.family-aware.lobed.*
```

The root seed selects bounded placement/orientation details only. The family vocabulary and its
analytical amplitude envelope are recipe-versioned constants, not semantic descriptor controls.

## Acceptance corpus

SF-IMP-0020 reuses the accepted fifteen-member family matrix:

```
5 families x 3 root seeds = 15 specimens
```

All members use signal amplitude `1.0`. Full numerical acceptance uses the canonical v0.2
`193 x 129 x 193` grid at four-unit spacing. Human visual review uses the same world bounds at
`97 x 65 x 97` / eight-unit spacing and must be compared against both the SF-IMP-0018 primary atlas
and the SF-IMP-0019 generic-composition atlas.

## Acceptance requirements

SF-IMP-0020 demonstrates:

1. zero amplitude returns the exact accepted SF-IMP-0018 primary-family artifact;
2. Massif reproduces the SF-IMP-0019 generic upper/density semantics as the control;
3. every selected family factor remains inside its declared positive analytical envelope;
4. Tableland and Basin family-aware secondary factors equal exactly one at the primary center;
5. Tableland, Spine, Basin, and Lobed compile visibly different secondary graph vocabularies;
6. the SF-IMP-0019 underside graph remains byte-identical for every family and root seed;
7. density remains the exact positive-inside intersection of selected upper and accepted underside;
8. every canonical horizontal sample retains the accepted SF-IMP-0018 primary footprint sign;
9. all fifteen full-resolution specimens retain positive occupancy, one face-connected solid
   component, zero domain-face contacts, and at least 48 world units of sampled clearance;
10. every non-control family has a measurable upper-surface departure from the generic SF-IMP-0019
    selection, while Massif remains semantically identical to the generic control;
11. human review confirms that family-specific secondary organization improves or preserves family
    legibility without introducing obvious pathological terrain structure.

Local Java 25 validation completed successfully with `scripts\verify-sf-imp-0020.bat` and a final
repository-wide `gradlew.bat check`. Human review accepted the family-aware atlas against the
SF-IMP-0019 generic-composition baseline. Tableland preserves a materially flatter interior, Basin
strengthens center/ring contrast, Spine reinforces axial organization, Massif remains the control,
and Lobed receives shoulder/saddle-aware relief.

## Deferred work

SF-IMP-0020 does not yet:

- promote family or family-aware controls into `SkyIslandVolumeDescriptor`;
- expose independent semantic amplitudes for local detail and secondary morphology;
- simulate drainage or erosion;
- hybridize primary families;
- generate multiple islands, archipelagos, provinces, climate, materials, biomes, ecology, or
  Minecraft realization.

With this proof accepted, the project now has evidence for both safe generic composition and
family-aware specialization. The next architecture decision should choose between descriptor schema
promotion and primary-family hybridization based on which controls have demonstrated stable semantic
meaning across the accepted corpus.
