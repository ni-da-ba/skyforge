# Skyforge Architecture Baseline v0.2 Proposal

**Document ID:** SF-BASE-0002
**Status:** Proposed by SF-IMP-0011
**Date:** 2026-08-03
**Owner:** Nicholas
**Supersedes:** No released contract; extends SF-BASE-0001 after acceptance

## 1. Proof claim

Skyforge v0.2 should prove one new claim before broadening into secondary morphology:

> A semantic sky-island volume descriptor can compile into an inspectable procedural graph that
> deterministically produces one finite, connected geological mass suspended in air, with
> independently controllable upper and underside morphology, independently of Minecraft.

This is the minimum correction needed to make the first morphology a sky island rather than a
top-down island surface over a downward-filled terrain field.

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

The proposed volume descriptor owns world meaning, not construction details. Its first schema
should express:

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
| Underside asymmetry | Bounded departure from bilateral lower form | Moves lower mass without breaking closure or connectedness |
| Signal controls | Bounded seeded enrichment | Varies detail without replacing the primary volume |

The descriptor must not contain graph-node names, interpolation choices, backend concepts, voxel
terms, or evidence-rendering settings.

## 4. Graph and recipe boundary

The recipe emits at least:

1. an inspectable upper-surface graph over X and Z;
2. an inspectable underside graph over X and Z;
3. a three-dimensional signed density graph whose positive set is exactly the intended solid;
4. provenance linking every semantic control to named graph substructure.

The candidate construction combines an upper constraint (`upper(x,z) - y`) and a lower constraint
(`y - lower(x,z)`) with an exact pointwise intersection operation. The candidate is intentionally
recorded as a design direction rather than silently expanding the v0.1 graph schema. Its eventual
node contract must define:

- raw binary64 evaluation, including signed zero, infinities, and NaN rejection or handling;
- operand type and domain rules;
- acyclic validation;
- canonical JSON representation and schema version;
- round-trip and reordered/parallel evaluation identity;
- backward reading of graph schemas 1 and 2 without byte drift.

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

## 6. Evidence contract

The first implementation ticket should propose an exact bounded volume domain and a practical
canonical resolution after measuring reference-evaluator cost. The evidence package must contain:

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

## 7. Work order

1. `SF-IMP-0012` — accept the suspended-volume descriptor, signed-density semantics, graph
   intersection primitive, and exact 3D evidence domain.
2. `SF-IMP-0013` — implement the signal-free upper/underside recipe and graph-schema extension.
3. `SF-IMP-0014` — implement deterministic 3D sampling, metrics, slices, and visual evidence.
4. `SF-IMP-0015` — execute the signal-free suspended-volume acceptance suite and pin a golden
   specimen.
5. `SF-IMP-0016` — apply bounded seeded enrichment and execute the fixed-seed identity suite.
6. Only then define secondary ridges/valleys and multi-morphology composition.

This order follows Skyforge doctrine: correct primary geometry before enrichment, and enrichment
before backend realization.

## 8. Explicit exclusions

The proposed proof does not include NeoForge, chunks, blocks, materials, climate, ecology,
decoration, erosion simulation, drainage, mesh extraction, level-of-detail systems, caching,
compiler optimization, multiple islands, provinces, or world-scale placement.

Those remain future consumers or extensions of a proven suspended primary volume.
