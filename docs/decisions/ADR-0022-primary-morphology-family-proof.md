# ADR-0022: Primary Suspended-Landform Morphology Families

- **Status:** Accepted by local Java 25 numerical validation and human visual review
- **Date:** 2026-08-28
- **Work item:** SF-IMP-0018

## Context

SF-IMP-0015 through SF-IMP-0017 established one deterministic finite suspended volume, bounded seeded local detail, and one organized upper-surface ridge/spur/valley layer. Those layers now produce useful terrain hierarchy, but every specimen still begins from essentially one primary suspended body.

The next proof must establish a vocabulary of materially different suspended landforms before adding world-scale placement or more local detail. Family identity must exist in the primary geometry itself; it must not be an emergent consequence of increasing signal amplitude.

## Decision

SF-IMP-0018 introduces five experimental recipe-layer morphology families:

1. **Massif** — compact high mass with a pronounced crown and concentrated underside.
2. **Tableland** — broad compact footprint with a comparatively flat elevated interior and shallower lower mass.
3. **Spine** — strongly elongated footprint organized around one dominant axis with a longitudinal keel.
4. **Basin** — positive-inside suspended mass whose upper center is lower than an organized interior ring.
5. **Lobed** — star-shaped footprint with several broad connected shoulders.

The family is intentionally **not** added to `SkyIslandVolumeDescriptor` schema 1. The proof first determines whether these concepts are visually and numerically useful. Stable user-facing controls may be promoted to a later descriptor schema only after evidence review.

`MorphologyFamilySkyIslandVolumeRecipe` is recipe version 4. It requires `signalAmplitude == 0` so SF-IMP-0018 can evaluate primary-family identity without the SF-IMP-0016 local-detail signal or the SF-IMP-0017 structured-relief layer.

## No new graph primitive

A pointwise positive-inside union (`max`) was considered for Lobed morphology. It is not required for the first proof.

Instead, Lobed morphology uses a bounded positive directional divisor applied to radial distance. In normalized family coordinates `a` and `b`, let:

```
r2 = a^2 + b^2
q  = (a^2 - b^2)^2 / (1 + r2)^2
F  = 1 + L*q
R2 = r2 / F
```

where seeded recipe constant `L` remains positive and bounded. Because `F > 0`, the footprint `R2 < 1` is a continuous star-shaped deformation around the common center rather than a union of potentially disconnected pieces. This gives broad axial shoulders while retaining an analytically connected primary footprint.

Graph schemas 1 through 3 remain unchanged. Density continues to use the accepted schema-3 positive-inside intersection.

## Shared closure construction

Every family derives one signed primary residual:

```
remaining = 1 - familyRadiusSquared
```

Both the upper and underside offsets are multiplied by this same residual and by factors that remain positive everywhere. Therefore:

- upper and underside meet exactly where `remaining == 0`;
- the two surfaces share one exact horizontal footprint;
- upper offset is positive inside and negative outside;
- underside offset is negative inside and positive outside;
- local family shaping cannot create an upper/underside footprint mismatch.

Density remains:

```
min(upper - y, y - underside)
```

## Family construction

### Massif

Uses a moderately elongated elliptical primary footprint related to the accepted v0.2 body. Upper relief retains a crown-shaped profile. The underside gains a bounded center-concentrating depth factor.

### Tableland

Uses a comparatively compact, near-round footprint. Its upper factor produces a `1-r^4`-like interior profile, preserving elevation farther toward the margin than the Massif crown. The underside is deliberately shallower.

### Spine

Uses the strongest aspect ratio in the family set. Upper relief is reduced away from the longitudinal centerline by a strictly positive rational factor. The underside gains a longitudinal keel using another positive rational factor.

### Basin

Uses a broad compact footprint. Its upper factor has a positive center floor plus a radial ring term, so the center remains above suspension while an interior annulus rises materially higher. The underside remains one continuous lower surface.

### Lobed

Uses the star-shaped radial deformation above. Upper and underside shaping remain continuous across all shoulders; the family does not depend on disconnected component union.

## Stable seeded primary variation

The primary family proof may vary bounded construction parameters from the descriptor root seed even though local signal amplitude is zero. This variation is large-scale recipe construction, not local noise.

Each family uses stable namespaces under:

```
sky-island.family.<family>.radius-scale
sky-island.family.<family>.azimuth
sky-island.family.<family>.lobe-strength
```

The first proof bounds radius scale to `[0.97, 1.03]` and azimuth variation to `±0.10` radians. Lobe strength is used only by Lobed geometry but is derived for stable graph construction across all families.

## Initial corpus

The first review corpus is deliberately smaller than the final regression matrix:

```
5 families × 3 root seeds = 15 specimens
```

The root seeds are:

- `Long.MIN_VALUE` (`seed-min`)
- `0` (`seed-zero`)
- `0x534b59464f524745` (`seed-skyforge`)

Full numerical acceptance uses the canonical v0.2 domain and 4-unit spacing (`193 × 129 × 193`).

Human visual review uses the same world bounds on an 8-unit review grid (`97 × 65 × 97`). This separation is intentional: the numerical gate is authoritative for topology, while the lighter atlas exists to make primary-family iteration practical.

## Acceptance requirements

SF-IMP-0018 must demonstrate:

1. every family compiles deterministically for the same descriptor/family pair;
2. changing the root seed changes bounded recipe-level primary construction;
3. all five families preserve the exact shared upper/underside sign envelope;
4. density remains the exact positive-inside intersection of the emitted upper and underside surfaces;
5. Basin has a materially lower center than its raised interior ring;
6. Tableland retains more normalized mid-radius elevation than Massif;
7. the five families produce distinct sampled primary footprint masks for each shared root seed;
8. all fifteen full-resolution specimens contain positive occupancy, exactly one face-connected solid component, zero domain-face contacts, and at least 48 world units of sampled clearance;
9. the human atlas shows materially distinguishable family identity in primary silhouette and/or vertical profile before local detail and structured relief are added.

Requirement 9 remains a human design gate. Numerical mask inequality alone is insufficient; differences must be visually meaningful.

## Acceptance record

Local Java 25 validation on 2026-08-28 accepted requirements 1 through 8 through `scripts\verify-sf-imp-0018.bat`. The verifier completed successfully after running the focused family recipe tests, fifteen-member full-resolution topology acceptance, and the lightweight family visual corpus.

Human review of `morphology-family-suspended-volume-v1` accepted requirement 9. Massif, Tableland, Spine, Basin, and Lobed are materially distinguishable in primary silhouette and/or vertical profile before SF-IMP-0016 local detail or SF-IMP-0017 structured relief is applied. The visual decision and evidence-system follow-ups are recorded in `docs/reviews/SF-IMP-0018-primary-morphology-family-visual-review.md`.

A final repository-wide Java 25 `gradlew.bat check` completed successfully after the visual-review commit was pulled. Hosted GitHub Actions validation remains unavailable while the repository Actions allowance is exhausted.

## Deferred work

SF-IMP-0018 does not yet:

- add family semantics to `SkyIslandVolumeDescriptor`;
- apply SF-IMP-0016 local detail to every family;
- apply SF-IMP-0017 structured ridges/spurs/valleys to every family;
- hybridize families;
- generate multiple islands or archipelagos;
- add caves, arches, disconnected hanging fragments, materials, climate, biomes, ecology, or Minecraft realization.

The next work item should test composition of the accepted SF-IMP-0016 and SF-IMP-0017 enrichment layers across the accepted family matrix before any descriptor-schema promotion.
