# ADR-0011: Signal-free island acceptance contract v1

**Status:** Accepted  
**Date:** 2026-08-03  
**Ticket:** SF-IMP-0007

## Context

The first island recipe and evidence writer already produce a deterministic signal-free landform,
but screenshots and unpinned measurements are not an acceptance contract. The gate must detect both
binary64 drift and semantic regressions, state every tolerance explicitly, and avoid claiming that
seeded identity has been proved before Skyforge has an accepted seed derivation or signal node.

## Decision

The canonical signal-free corpus is `signal-free-island-v1`. Its descriptor is centralized in
`SignalFreeReferenceCorpus` and uses center `(0, 0)`, nominal radius `256`, maximum elevation `96`,
coastal falloff `64`, ridge azimuth `pi / 6`, ridge strength `0.65`, zero signal amplitude, and
signal scale `32`. Its seed is the binary64-independent 64-bit pattern `0x534b59464f524745`; the
signal-free recipe must remain independent of both seed and signal scale while amplitude is zero.

Fast semantic gates use a centered `257 x 257` grid with half-width `1.5 * nominalRadius`. The odd
resolution samples the descriptor center exactly. The golden gate executes the public evidence CLI
on the mandated `1024 x 1024` grid.

### Gate envelopes

| Gate | Passing envelope |
|---|---|
| `SF-ISL-001` | Exactly one four-neighbor land component, zero boundary land samples, and land centroid within one grid spacing of the descriptor center. |
| `SF-ISL-002` | Every height is finite. Maximum height differs from `maximumElevation` by at most `1e-10`. On the standard square, the lower bound is `E * (1 - Pmax)`, where `r2max = 4.5 * stretch^2`, `stretch = 1 + 0.40 * ridgeStrength`, `q = coastalFalloff / nominalRadius`, and `Pmax = q * r2max + (1 - q) * r2max^2`; sample comparisons allow `1e-10`. |
| `SF-ISL-003` | With all dimensionless controls fixed, increasing nominal radius strictly increases measured area and both shoreline spans. Area follows the squared radius ratio and spans follow the radius ratio within `1e-10`. |
| `SF-ISL-004` | Increasing elevation leaves the canonical land mask and all footprint metrics exactly unchanged. Peak and positive-land 90th-percentile height follow the elevation ratio within `1e-10`. |
| `SF-ISL-005` | The principal axis measured from the land-mask covariance agrees with the descriptor ridge azimuth, modulo `pi`, within one degree; a controlled azimuth change rotates the measured axis by the same amount within one degree. |
| `SF-ISL-006` | At zero amplitude, changing the seed and signal scale leaves canonical height graphs, density graphs, and sampled height-grid checksums exactly unchanged. |
| `SF-ISL-008` | At sampled columns, `D(x, H(x,z), z)` is positive zero by raw binary64 bits. Symmetric vertical offsets agree with `H - y` within `1e-10`. |
| `SF-ISL-009` | Stable graph nodes expose center, elevation, derived major/minor radii, azimuth sine/cosine, coast weight, normalized coast profile, and remaining height. Seed and signal controls are explicitly neutral or deferred rather than represented by fictitious nodes. |

`SF-ISL-007`, identity preservation across signals and seeds, remains deferred. It cannot pass until
the seed derivation algorithm and first bounded signal family are accepted and implemented.

### Golden policy

The checked-in `signal-free-island-v1.sha256` file pins exact SHA-256 values for the descriptor,
canonical height and density graphs, height/mask/slope binary grids, and exact hexadecimal
cross-sections. The golden test also pins the primary manifest statistics and morphology metrics.

PNG hashes are deliberately excluded from the normative golden file. PNGs are visual review
projections, and ADR-0010 does not promise encoder identity across arbitrary JDK implementations.
They must still be present, valid, and byte-repeatable within one evidence run, as tested by the
evidence writer suite.

## Descriptor-to-evidence traceability

| Descriptor property | Graph evidence | Measured effect |
|---|---|---|
| `centerX`, `centerZ` | `descriptor.center-x`, `descriptor.center-z` | centroid and translation |
| `nominalRadius` | `ridge.major-radius`, `ridge.minor-radius` | area and shoreline spans |
| `maximumElevation` | `descriptor.maximum-elevation` | peak and positive-land percentile |
| `coastalFalloff` | `coast.quadratic-weight`, `coast.quartic-weight` | normalized coastal profile |
| `ridgeAzimuth` | `ridge.cos-azimuth`, `ridge.sin-azimuth` | principal-axis direction |
| `ridgeStrength` | derived major/minor radii | principal-axis anisotropy |
| `seed`, `signalAmplitude`, `signalScale` | absent from the signal-free graph | exact zero-amplitude neutrality; seeded effects deferred |

## Consequences

- A checksum change fails even if the new image looks plausible.
- A semantic regression fails even if golden files are mechanically updated.
- An intentional recipe change must explain both its numerical drift and its effect on every gate,
  then version or deliberately replace the affected golden corpus.
- Sprint One is not complete until the deferred seeded identity gate also passes.
