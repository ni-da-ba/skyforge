# ADR-0009: Signal-free island recipe v1

**Status:** Accepted  
**Date:** 2026-08-03  
**Ticket:** SF-IMP-0005

## Context

Skyforge's first semantic proof requires one closed island whose shape responds predictably to a
descriptor before seeded variation exists. The recipe must remain compatible with canonical graph
JSON v1, expose its mathematical construction for inspection, and produce both a two-dimensional
height graph and a three-dimensional solid-density graph.

## Decision

`IslandDescriptor` schema v1 contains the semantic controls recorded in the architecture baseline.
It canonicalizes the bidirectional ridge azimuth to `[0, pi)`, constrains normalized controls to
`[0, 1]`, and constrains coastal falloff to `(0, nominalRadius]`. The signal-free recipe rejects a
nonzero signal amplitude rather than silently ignoring it.

Recipe v1 uses a rotated anisotropic polynomial profile. Ridge strength stretches the principal
radius by `1 + 0.40 * ridgeStrength` and contracts the perpendicular radius by the same factor, so
their geometric mean remains the nominal radius. With `q` equal to the squared normalized
elliptical distance and `f = coastalFalloff / nominalRadius`, height is:

```text
H(x,z) = maximumElevation * (1 - (f * q + (1 - f) * q^2))
```

The `q = 1` shoreline is closed and independent of elevation and coastal falloff. Increasing
coastal falloff reduces the shoreline gradient; increasing elevation scales height without moving
the shoreline; and ridge azimuth rotates the principal axis. Solid density is compiled from the
same morphology in three dimensions as `D(x,y,z) = H(x,z) - y`.

Both graphs use only canonical graph JSON v1 node kinds. Stable, human-readable node identifiers
expose descriptor, ridge, coast, height, and density substructure. The compiled result retains the
source descriptor and records descriptor, recipe, and graph schema versions.

## Consequences

- The first island is analytically testable without signals, a new graph kind, or a graph schema
  revision.
- The longest possible island radius is `1.4 * nominalRadius`, so land cannot touch the standard
  evidence square's `1.5 * nominalRadius` boundary at any ridge azimuth.
- Seed and signal scale are retained as semantic provenance but do not affect signal-free graphs;
  changing unused seed bits therefore preserves canonical graph bytes.
- The unclamped polynomial becomes negative below sea level and is not globally lower-bounded.
  The reference evidence pipeline must declare and test bounds over its finite sampling domain.
- Ridge strength currently controls whole-island anisotropy. More elaborate internal ridges remain
  future morphology work and must not alter recipe v1 silently.
