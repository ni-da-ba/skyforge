# ADR-0021: Structured Secondary Sky-Island Morphology

- **Status:** Accepted by local Java 25 validation and visual review
- **Date:** 2026-08-28
- **Work item:** SF-IMP-0017

## Context

SF-IMP-0016 proved that bounded seeded enrichment can vary a suspended sky-island volume without
changing its analytical footprint or violating the v0.2 topology contract. The six-seed visual
corpus also showed the limitation of that mechanism: it produces real vertical variation, but the
specimens still read as the same smooth primary body because the signal layer has no organized
landform hierarchy.

Increasing the SF-IMP-0016 displacement bound is rejected. It would ask an unstructured detail
signal to perform the role of secondary morphology and would trade away safety without introducing
coherent ridges, spurs, passes, or valleys.

## Decision

SF-IMP-0017 introduces a new recipe layer,
`SecondaryMorphologySkyIslandVolumeRecipe`, above the accepted seeded recipe. It changes only the
upper-surface offset from the suspension plane. The accepted seeded underside is retained exactly.

The first proof deliberately does **not** extend `SkyIslandVolumeDescriptor` schema 1. The operator
uses the existing root seed and `signalAmplitude` as an overall seeded-morphology amplitude while
its internal secondary-landform parameters remain recipe-versioned construction choices. Semantic
controls will be promoted into a later descriptor schema only after visual evidence demonstrates
which controls are stable and useful.

### Directional basis

Each organized landform is represented using existing arithmetic graph nodes. After rotating,
translating, and normalizing the accepted primary-ridge coordinate frame, a directional basis is:

```
B(along, across) = 1 / (1 + across^2 + along^4)
```

The basis is strictly in `(0, 1]`. The fourth-power longitudinal term produces an elongated core
with smooth decay, while the squared transverse term controls corridor width. No new kernel node or
graph schema is required.

Three deterministic bases are compiled at this stage:

1. a broad secondary ridge approximately aligned with the primary landform;
2. a narrower oblique spur;
3. an organized valley corridor.

Their angle and center offsets are derived from stable semantic seed namespaces through
`SeedDerivation.VERSION`. Length and width bounds are recipe constants for this proof.

### Bounded upper-surface factor

For accepted seeded upper surface `Us`, suspension elevation `S`, descriptor amplitude `A`, and the
three directional bases `Br`, `Bs`, and `Bv`:

```
F = 1 + A * (0.30 * Br + 0.18 * Bs - 0.24 * Bv)
U = S + (Us - S) * F
```

Because `A` and every basis lie in `[0, 1]` or `(0, 1]`, the factor has the analytical envelope:

```
0.76 <= F <= 1.48
```

The factor is therefore strictly positive. Consequently:

- the sign of the accepted upper offset is unchanged everywhere;
- every accepted rim point remains exactly on the suspension plane;
- the complete horizontal footprint is unchanged;
- exterior upper-surface ordering remains unchanged;
- the structured layer cannot perforate the volume through the suspension plane;
- the accepted seeded underside remains byte-identical.

Density remains the exact positive-inside intersection:

```
min(U - y, y - acceptedUnderside)
```

## Deterministic parameter namespaces

The proof derives compile-time landform parameters from these stable namespaces:

- `sky-island.secondary.main-angle`
- `sky-island.secondary.main-along`
- `sky-island.secondary.main-across`
- `sky-island.secondary.spur-side`
- `sky-island.secondary.spur-angle`
- `sky-island.secondary.spur-along`
- `sky-island.secondary.spur-across`
- `sky-island.secondary.valley-angle`
- `sky-island.secondary.valley-along`
- `sky-island.secondary.valley-across`

The same root seed must compile byte-identical graphs. Different canonical seeds must alter the
structured upper graph.

## Acceptance requirements

The SF-IMP-0017 local acceptance checkpoint requires:

1. zero amplitude returns the accepted signal-free artifact exactly;
2. nonzero structured compilation preserves the accepted seeded underside exactly;
3. the upper-offset factor remains in `[0.76, 1.48]` across the sampled domain;
4. upper sign, rim closure, and surface ordering are preserved;
5. density remains the exact intersection of structured upper and accepted underside surfaces;
6. the six canonical full-amplitude seeds each retain positive occupancy, zero domain-face
   contacts, exactly one face-connected solid component, and at least 80 world units of sampled air
   clearance;
7. sampled horizontal bounds remain `x = [-296, 296]`, `z = [-236, 236]`;
8. every canonical seed produces at least eight world units of structured upper-surface relief
   relative to SF-IMP-0016 somewhere on the canonical horizontal grid;
9. the generated `secondary-morphology-suspended-volume-v1` atlas visibly exhibits organized
   landform structure at a larger spatial scale than the SF-IMP-0016 detail layer.

Requirement 9 is a human design gate. Numerical correctness alone does not establish that the
secondary morphology is useful.

## Local acceptance record

Local Java 25 validation on 2026-08-28 accepted requirements 1 through 8. The canonical six-member
full-amplitude corpus reported:

| Member | Solid samples | Components | Face contacts | Minimum clearance |
|---|---:|---:|---:|---:|
| `seed-min` | 378,935 | 1 | 0 | 88 |
| `seed-negative-one` | 381,784 | 1 | 0 | 88 |
| `seed-zero` | 382,278 | 1 | 0 | 88 |
| `seed-one` | 377,445 | 1 | 0 | 88 |
| `seed-skyforge` | 379,341 | 1 | 0 | 88 |
| `seed-max` | 375,742 | 1 | 0 | 88 |

The numerical acceptance test completed successfully after the optimized evaluator/evidence path
was introduced. The complete six-seed visual corpus then generated successfully in 31 seconds.

An earlier sequential run exposed a reference-harness scalability defect rather than a morphology
defect: graph-node lookup was linear in graph size for every recursive evaluation, and the 3D
evidence path redundantly reevaluated X/Z-only surface structure across every Y sample. The accepted
performance correction compiles graph node references to indexed evaluator plans and, for canonical
forward suspended-volume evidence, samples upper and underside surfaces once per horizontal cell
before filling density with the exact intersection equation. A regression test compares that factored
forward path against direct full-density graph evaluation byte-for-byte on a smaller 3D grid. Seed
specimens are additionally executed with bounded two-way concurrency.

Visual review of the generated corpus accepted requirement 9. Compared directly with the
SF-IMP-0016 corpus, the structured layer creates elongated ridge corridors, oblique spur structures,
and coherent valley depressions at scales materially larger than the local detail signal. Across the
positive footprint, maximum upper-surface changes are approximately 30 to 39 world units upward and
up to about 11 world units downward depending on seed, while mean change remains only about +3.6 to
+3.7 world units. This concentration shows organized relief rather than uniform crown inflation.
The accepted underside grid/render and suspension-plane occupancy render are byte-identical to
SF-IMP-0016 for all six seeds.

The detailed review is recorded in
`docs/reviews/SF-IMP-0017-secondary-morphology-visual-review.md`. The current isometric occupancy
renderer is retained for topology and silhouette review but should later be supplemented with
relief-aware shading, contours, or explicit height/delta visualization because it visually
understates upper-surface organization.

Hosted GitHub Actions validation remains unavailable while the repository Actions allowance is
exhausted. A final complete local `gradlew.bat check` remains required before merge.

## Consequences

This design separates three levels that were previously conflated:

- primary morphology defines the overall suspended mass and silhouette;
- seeded bounded detail provides small-scale deterministic variation;
- structured secondary morphology creates coherent internal landform organization.

Because all three remain explicit graph structure, later Minecraft realization can evaluate the
same field incrementally without changing the reference definition.

SF-IMP-0017 is accepted. Multi-morphology composition is the next morphology milestone, subject to
the final repository-wide local merge check for the stacked v0.2 implementation branches.
