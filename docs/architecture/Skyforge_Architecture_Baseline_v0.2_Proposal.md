# Skyforge Architecture Baseline v0.2 Proposal

**Document ID:** SF-BASE-0002
**Status:** Accepted suspended-volume proof; secondary morphology next
**Date:** 2026-08-27
**Accepted through SF-IMP-0016:** 2026-08-28
**Owner:** Nicholas
**Supersedes:** No released contract; extends SF-BASE-0001 after acceptance

## 1. Proof claim

Skyforge v0.2 proves one new claim before broadening into secondary morphology:

> A semantic sky-island volume descriptor can compile into an inspectable procedural graph that
> deterministically produces one finite, connected geological mass suspended in air, with
> independently controllable upper and underside morphology, independently of Minecraft.

This is the minimum correction needed to make the first morphology a sky island rather than a
top-down island surface over a downward-filled terrain field.

The signal-free portion of this claim is accepted by SF-IMP-0015. SF-IMP-0016 completes the proof
boundary by accepting bounded deterministic seeded enrichment across the fixed six-seed corpus under
local Java 25 validation.

## 2. Preserved v0.1 authority

The following remain normative and unchanged:

- backend-neutral module boundaries;
- immutable typed procedural graphs and the reference evaluator;
- explicit versioned seed derivation and bounded signals;
- canonical graph schemas 1 and 2 and their byte identity;
- the complete `fixed-seed-island-v1` corpus and all v0.1 gates;
- reference-first correctness and differential identity as the optimization boundary.

The new proof must not edit the meaning or canonical serialization of the released
`IslandDescriptor`. It introduces a new descriptor and recipe version.

## 3. Semantic ownership

The accepted `SkyIslandVolumeDescriptor` owns world meaning, not construction details. Its first
schema expresses:

| Control | Semantic meaning | Required observable response |
|---|---|---|
| Horizontal center | World-space placement in X and Z | Translates the full volume without deformation |
| Suspension elevation | World-space vertical anchor | Translates the full volume in Y without deformation |
| Nominal radius | Overall horizontal scale | Changes horizontal extent predictably |
| Upper elevation | Height of the crown above the anchor | Changes the upper surface without deepening the underside |
| Underside depth | Depth below the anchor | Changes the lower surface without raising the crown |
| Coastal falloff | Upper-edge profile | Changes upper-surface approach to the silhouette |
| Primary ridge | Direction and strength of large-scale identity | Rotates or stretches the primary horizontal axis predictably |
| Underside taper | Distribution of mass from rim to nadir | Moves the lower profile between broad and concentrated forms |
| Underside asymmetry | Signed bounded departure from bilateral lower form in `[-1,1]` | Moves lower mass without breaking closure or connectedness |
| Signal controls | Bounded seeded enrichment | Varies detail without replacing the primary volume |

The descriptor must not contain graph-node names, interpolation choices, backend concepts, voxel
terms, or evidence-rendering settings.

## 4. Graph and recipe boundary

The recipe emits at least:

1. an inspectable upper-surface graph over X and Z;
2. an inspectable underside graph over X and Z;
3. a three-dimensional signed density graph whose positive set is exactly the intended solid;
4. provenance linking every semantic control to named graph substructure.

The accepted construction combines an upper constraint (`upper(x,z) - y`) and a lower constraint
(`y - lower(x,z)`) with `IntersectionNode`, an exact pointwise `Math.min` over two
three-dimensional scalar fields. ADR-0016 fixes its graph schema 3 representation and its
binary64, validation, and compatibility behavior. In particular:

- negative zero is preserved by `Math.min`, infinities follow `Math.min`, and NaN propagates;
- final evidence classification rejects every non-finite density;
- both inputs and the output are `SCALAR_FIELD_3`;
- ordinary graph validation provides reference, type, and acyclic checks;
- canonical JSON schema 3 records the ordered pair of inputs;
- graph schemas 1 and 2 retain their minimum versions and exact canonical bytes.

SF-IMP-0016 adds bounded seeded enrichment by multiplying each signal-free upper and underside
offset from the suspension plane by an independent positive factor in `[0.85, 1.15]` at full
amplitude. Because those factors never change sign, the rim, suspension-plane footprint, and
inside/outside surface ordering are preserved analytically while upper and underside detail vary
independently.

## 5. Suspended-volume acceptance gates

| Gate | Acceptance claim |
|---|---|
| `SF-VOL-001` finite closure | No positive-density sample touches any of the six faces of the declared 3D evidence domain. |
| `SF-VOL-002` suspension | A continuous negative-density air region separates the solid from the complete lower domain face; no ground plane participates. |
| `SF-VOL-003` connected mass | The positive-density grid contains exactly one face-connected component at the canonical resolution. |
| `SF-VOL-004` surface ordering | Upper density zeroes lie above lower density zeroes throughout the solid footprint and meet only on the outer silhouette within tolerance. |
| `SF-VOL-005` semantic controls | Radius, suspension elevation, crown height, underside depth, taper, ridge, and asymmetry produce declared monotonic or directional responses. |
| `SF-VOL-006` bounded enrichment | Accepted signals preserve closure, one-component topology, boundary clearance, and declared identity envelopes across the fixed seed suite. |
| `SF-VOL-007` deterministic identity | Forward, reversed, permuted, batched, and parallel 3D sampling produce identical canonical density bytes. |
| `SF-VOL-008` inspectability | Descriptor controls, upper graph, underside graph, density graph, versions, namespaces, and provenance are present in the evidence package. |
| `SF-VOL-009` legacy preservation | Every v0.1 test and all 49 canonical fixed-seed artifacts remain unchanged. |
| `SF-VOL-010` visual evidence | Two orthogonal vertical slices, an underside projection, and an isometric occupancy view agree with the canonical grid and expose the complete silhouette. |

Visual agreement never replaces the numerical gates.

SF-IMP-0015 accepts `SF-VOL-001` through `SF-VOL-005` and `SF-VOL-007` through `SF-VOL-010` for
`signal-free-suspended-volume-v1`. The accepted canonical specimen contains 366,912 solid samples,
one connected component, zero domain-face contacts, and 88 world units of minimum sampled air
clearance.

SF-IMP-0016 accepts `SF-VOL-006` across the six canonical root seeds at full amplitude. Every member
retains one connected component, zero domain-face contacts, and 88 world units of minimum sampled
air clearance while preserving the complete horizontal identity/sign envelope and the declared
15-percent relative surface-displacement bound. Solid occupancy ranges from 363,854 to 370,382
samples across the accepted corpus.

## 6. Evidence contract

ADR-0016 accepts an exact bounded volume domain of `x,z in [-384,384]`, `y in [0,512]`, and
`193 x 129 x 193` inclusive samples. Every axis has four-unit spacing. Canonical traversal advances
x first, then z, then y. The evidence package implemented in SF-IMP-0014 must contain:

- the semantic descriptor and every compiled graph;
- a canonical binary64 density grid with declared X/Y/Z traversal and bounds;
- a canonical occupancy grid derived from `density > 0`;
- volume, centroid, axis-aligned bounds, connected-component count, face-contact counts, and minimum
  air-clearance metrics;
- exact horizontal and orthogonal vertical slice data;
- deterministic upper-surface, underside, vertical-slice, and isometric review images;
- a manifest and SHA-256 listing that excludes environment-qualified timing.

The isometric image is a review projection of occupancy, not a substitute for a mesh, renderer, or
Minecraft backend.

ADR-0019 pins 19 engine-version-independent files from this package as the golden specimen. The
manifest remains inspectable but is excluded from golden identity because it records engine-version
metadata; `evidence.sha256` is excluded because it includes the manifest hash.

SF-IMP-0016 additionally defines `seeded-suspended-volume-v1`, a six-member full-amplitude review
corpus using the canonical v0.1 root-seed suite. Its corpus atlas is intentionally human-review
evidence; the numerical SF-VOL-006 acceptance test remains authoritative for topology and identity.
The visual review is recorded in `docs/reviews/SF-IMP-0016-seeded-volume-visual-review.md`: it accepts
bounded enrichment as a local-detail layer while identifying organized landscape-scale morphology
as the next limitation.

## 7. Work order

1. [x] `SF-IMP-0012` — accept the suspended-volume descriptor, signed-density semantics, graph
   intersection primitive, graph schema 3, and exact 3D evidence domain.
2. [x] `SF-IMP-0013` — implement the signal-free upper/underside recipe and provenance graphs.
3. [x] `SF-IMP-0014` — implement deterministic 3D sampling, metrics, slices, and visual evidence.
4. [x] `SF-IMP-0015` — execute the signal-free suspended-volume acceptance suite and pin a golden
   specimen.
5. [x] `SF-IMP-0016` — apply bounded seeded enrichment and execute the fixed-seed identity suite.
6. [ ] `SF-IMP-0017` — define and validate organized secondary upper-surface ridges/valleys with a
   rim-safe envelope, minimum-thickness protection, stable seeded namespaces, and visual evidence
   of landscape-scale organization larger than the SF-IMP-0016 detail signal.
7. [ ] Define multi-morphology composition only after SF-IMP-0017 preserves the v0.2 suspended-volume
   invariants.

This order follows Skyforge doctrine: correct primary geometry before enrichment, and enrichment
before backend realization.

## 8. Explicit exclusions

The accepted v0.2 proof does not include NeoForge, chunks, blocks, materials, climate, ecology,
decoration, erosion simulation, drainage, mesh extraction, level-of-detail systems, caching,
compiler optimization, multiple islands, provinces, or world-scale placement.

Those remain future consumers or extensions of a proven suspended primary volume.
