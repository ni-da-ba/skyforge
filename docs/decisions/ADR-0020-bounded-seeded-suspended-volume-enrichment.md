# ADR-0020: Bounded Seeded Suspended-Volume Enrichment

- **Status:** Accepted by local Java 25 validation
- **Date:** 2026-08-27
- **Accepted:** 2026-08-28
- **Work item:** SF-IMP-0016

## Context

SF-IMP-0015 fixes the accepted signal-free suspended sky-island morphology and its 19-artifact identity boundary. The next step may add deterministic seeded variation only if it preserves the finite suspended-volume invariants established by SF-VOL-001 through SF-VOL-005.

Adding arbitrary noise directly to final signed density is rejected because it can independently alter the upper and lower constraints, open the rim, create disconnected solids, introduce cavities, or change the horizontal identity of the accepted morphology.

## Decision

Seeded enrichment operates on each surface's signed offset from the suspension plane, not on final density.

For a signal-free upper surface `U0`, underside surface `L0`, and suspension elevation `S`:

```
upperOffset0     = U0 - S
undersideOffset0 = S - L0

upperFactor     = 1 + 0.15 * amplitude * upperSignal
undersideFactor = 1 + 0.15 * amplitude * undersideSignal

U = S + upperOffset0 * upperFactor
L = S - undersideOffset0 * undersideFactor
```

Both planar signals are deterministic, bounded in `[-1, 1]`, and use independent semantic seed namespaces:

- `sky-island.upper-detail`
- `sky-island.underside-detail`

At full amplitude both factors therefore remain in `[0.85, 1.15]` and are strictly positive.

Density remains the exact positive-inside intersection:

```
min(U - y, y - L)
```

The zero-amplitude path returns the signal-free recipe artifact directly rather than recompiling an equivalent seeded graph.

## Consequences

Because the modulation factors are strictly positive:

1. the sign of each signal-free surface offset from the suspension plane is preserved;
2. every signal-free rim point remains exactly on the suspension plane;
3. the suspension-plane footprint and outer silhouette are unchanged;
4. upper/lower ordering is preserved inside and outside the footprint;
5. enrichment cannot by itself introduce a ground plane;
6. surface displacement is bounded to 15 percent of the corresponding signal-free offset.

Upper and underside detail may vary independently while retaining the accepted primary island identity.

## Fixed-seed acceptance suite

SF-VOL-006 reuses the six canonical v0.1 root seeds:

- `Long.MIN_VALUE`
- `-1`
- `0`
- `1`
- `0x534b59464f524745L`
- `Long.MAX_VALUE`

At the canonical `193 x 129 x 193` suspended-volume domain, every full-amplitude member must demonstrate:

- positive solid occupancy;
- zero positive-density contacts on all six domain faces;
- exactly one face-connected solid component;
- minimum domain-face air clearance of at least 80 world units;
- the accepted horizontal sampled bounds `x = [-296, 296]`, `z = [-236, 236]`;
- identical sign of upper and underside offsets relative to the signal-free morphology across the complete horizontal sampling grid;
- relative upper and underside displacement within the declared 15 percent envelope.

The existing SF-VOL-007 deterministic sampling-order contract remains separately authoritative for schedule invariance.

## Validation record

On 2026-08-28 the focused seeded recipe tests and the complete six-member SF-VOL-006 acceptance suite completed successfully under Eclipse Temurin OpenJDK 25.0.4.1 and Gradle 9.6.1 on Windows 11.

The generated full-amplitude corpus reported:

| Member | Solid samples | Components | Face contacts | Minimum clearance |
| --- | ---: | ---: | ---: | ---: |
| `seed-min` | 367,356 | 1 | 0 | 88.000 |
| `seed-negative-one` | 369,847 | 1 | 0 | 88.000 |
| `seed-zero` | 370,382 | 1 | 0 | 88.000 |
| `seed-one` | 365,864 | 1 | 0 | 88.000 |
| `seed-skyforge` | 367,732 | 1 | 0 | 88.000 |
| `seed-max` | 363,854 | 1 | 0 | 88.000 |

Every member therefore preserved the required one-component topology, zero domain-face contact, and clearance margin. The acceptance test additionally verified the exact horizontal identity/sign envelope and 15 percent relative displacement bound over the complete canonical horizontal grid.

During the same local verification run, the signal-free golden-specimen test exposed an unrelated JUnit temporary-directory lifecycle defect: `@TestInstance(PER_CLASS)` allowed a cached evidence path to outlive its `@TempDir`. The test was corrected to use the normal per-method lifecycle while retaining the canonical volume as static shared data. The formerly failing golden-specimen method subsequently passed in isolation in 51 seconds.

The six-seed visual evidence corpus then generated successfully in 5 minutes 48 seconds under `skyforge-reference/build/evidence/seeded-suspended-volume-v1`. Visual morphology review remains a human design-quality activity rather than a numerical acceptance prerequisite.

GitHub-hosted Actions remain unavailable because the repository's Actions allowance is exhausted. A final complete `gradlew.bat check` after the lifecycle fix is still required at the merge/release checkpoint, but SF-VOL-006 itself is accepted by the local Java 25 evidence above.
