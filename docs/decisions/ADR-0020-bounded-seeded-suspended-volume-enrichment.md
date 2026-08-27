# ADR-0020: Bounded Seeded Suspended-Volume Enrichment

- **Status:** Proposed pending local acceptance validation
- **Date:** 2026-08-27
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

## Validation state

The implementation and acceptance tests are present on `agent/sf-imp-0016`, stacked on the SF-IMP-0015 acceptance commit. GitHub-hosted Actions are currently unavailable because the repository's Actions allowance is exhausted. This ADR remains **Proposed** until the relevant Gradle test suites complete successfully in a local Java 25 environment or hosted CI capacity is restored.
