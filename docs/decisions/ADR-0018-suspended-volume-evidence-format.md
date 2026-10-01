# ADR-0018: Suspended-volume evidence format

**Status:** Accepted
**Date:** 2026-08-04
**Ticket:** SF-IMP-0014

## Context

ADR-0017 defines an analytical suspended-volume recipe, but its diagnostic image is not a
permanent acceptance artifact. Skyforge needs a backend-neutral evidence path that samples the
three-dimensional density graph, derives topology from exactly those samples, and produces
review images without introducing a mesh, voxel backend, or second geometric implementation.

The format must make finite closure, suspension, connectedness, morphology, and provenance
inspectable. It must also distinguish permanent deterministic evidence from the golden specimen
hashes that SF-IMP-0015 will accept.

## Decision

1. Add `ScalarVolumeGrid` binary schema 1. Its magic is `SFVOL\0\0\1`; it records the three sample
   counts, six binary64 bounds, and every finite density as a big-endian raw binary64 value.
   Storage advances x first, then z, then y.
2. Add `OccupancyVolumeGrid` binary schema 1. Its magic is `SFOCC\0\0\1`; it uses the same header
   and stores one canonical byte per sample. A byte is one exactly when finite density is strictly
   greater than zero. Both signed zeros are therefore non-solid surface samples.
3. Add schedule-independent 3D sampling for forward, reversed, permuted, batched, and parallel
   evaluation. Every schedule writes into the same canonical linear index.
4. Derive one face-connected-component count, positive-sample volume and centroid, sampled solid
   bounds, per-face contact counts, and per-face air clearances solely from the occupancy grid.
5. Store the complete density and occupancy volumes, upper and underside surface grids, the
   suspension-plane density grid, and hexadecimal signed-density CSV for two orthogonal vertical
   center slices.
6. Store the descriptor, all three compiled graphs, and semantic-control provenance separately.
   A manifest records versions, sampling, metrics, canonical grid checksums, and every nonmanifest
   artifact hash. A separate sorted SHA-256 listing includes the manifest and all prior artifacts
   but cannot include itself.
7. Render six deterministic review views from the exact evidence arrays: upper surface, underside
   depth, suspension-plane occupancy, east-west occupancy, north-south occupancy, and an isometric
   projection of boundary occupancy. The vertical slices show the suspension elevation in orange,
   solid geology in dark gray, and air in pale gray.
8. Treat images and the HTML gallery as explanations. Numerical grids, metrics, and hashes remain
   authoritative. The isometric projection is not a mesh, material render, or Minecraft preview.
9. Generate and upload this complete package on every Java 25 CI run alongside the frozen v0.1
   corpus. Do not check any new volume checksum into source control in this ticket.

## Consequences

- The canonical domain produces 4,805,121 density samples. The density payload is 38,440,968 bytes
  before its 72-byte header; occupancy adds one byte per sample plus the same-size header.
- Human reviewers can see both the top surface and the finite underside, while exact vertical data
  remains available for numerical inspection.
- Topology and clearance are derived from sampled occupancy. They describe the declared canonical
  resolution and do not claim continuous analytical topology between samples.
- PNG encoding is deterministic within the accepted Java reference environment and is included in
  the evidence checksums. Any renderer change must be deliberate and versioned.
- SF-IMP-0015 can now execute the named signal-free `SF-VOL` gates and accept one golden specimen
  without inventing new evidence formats.

## Compatibility

No v0.1 grid format, descriptor, recipe, graph encoding, corpus path, or checksum changes. The
existing 49-path corpus remains regenerated and verified before the suspended-volume artifact is
published.
