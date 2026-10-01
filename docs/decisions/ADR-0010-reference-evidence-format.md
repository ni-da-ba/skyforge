# ADR-0010: Reference evidence format v1

**Status:** Accepted  
**Date:** 2026-08-03  
**Ticket:** SF-IMP-0006

## Context

The first island must be judged by reproducible numerical and visual evidence rather than by an
unrecorded screenshot. Evidence generation must remain backend-neutral, preserve exact binary64
samples, expose the sampling rule, and distinguish mathematical results from their review images.
Traversal-order tests also require a canonical storage layout that is independent of evaluation
schedule.

## Decision

The reference sampler uses inclusive, uniformly spaced rectangular grids. Grid values are stored
canonically in increasing z-row and then increasing x-column order. Forward, reversed,
coprime-permuted, reverse-batched, and parallel schedules write into that same indexed layout and
must produce identical raw binary64 values.

Canonical grid binary schema v1 is big-endian and contains:

1. the eight-byte `SFGRID` schema marker;
2. schema version, width, and height as signed 32-bit integers;
3. minimum and maximum x and z bounds as raw binary64 bits;
4. every value as raw binary64 bits in canonical row-major order.

SHA-256 of those bytes is the normative grid checksum. Height slope is computed from the sampled
grid using centered finite differences in the interior and one-sided differences at boundaries.
Cross-sections are evaluated independently at the descriptor's exact center coordinates and use
hexadecimal binary64 CSV. Land means strictly positive height.

Evidence manifest schema v1 records engine, descriptor, recipe, graph, grid, statistics,
morphology metrics, canonical grid checksums, semantic provenance, and a SHA-256 hash for every
artifact other than the manifest itself. Fixed JSON member and artifact order make the manifest
byte-repeatable for identical evidence.

PNG files are deterministic review projections, not normative numerical data. They are written
without timestamps or environment metadata through the Java standard PNG encoder. Height is
clamped from sea level to the descriptor maximum, land masks are binary, slopes use the sampled
maximum, and cross-sections use a fixed 256-pixel vertical canvas. No image library dependency is
introduced.

## Consequences

- Raw evidence remains exact even if visualization palettes later change.
- Evaluation schedule cannot affect canonical layout or checksums.
- The same Skyforge and Java versions can regenerate byte-identical packages without Minecraft.
- Cross-JDK PNG identity is not promised by v0.1; raw grids and canonical JSON remain the primary
  evidence if an encoder implementation changes.
- A million-sample reference package prioritizes clarity over throughput. Performance work must
  retain differential equality with this implementation.
