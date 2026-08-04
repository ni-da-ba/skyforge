# ADR-0012: Seed derivation and bounded planar value signal v1

**Status:** Accepted  
**Date:** 2026-08-03  
**Ticket:** SF-IMP-0008

## Context

The accepted base island establishes identity without variation. Skyforge now needs one seeded signal
that is deterministic, inspectable, bounded, and incapable of replacing that identity. The root seed
must be separated into operation-local streams by semantic meaning rather than graph declaration
order or mutable random-number-generator state. Existing signal-free graph bytes and evidence must
remain unchanged.

## Seed derivation decision

`SeedDerivation` version 1 accepts an arbitrary 64-bit root pattern and a canonical namespace
matching `[a-z0-9]+(?:[.-][a-z0-9]+)*`.

1. Hash the namespace's canonical ASCII bytes with FNV-1a-64, using offset basis
   `0xcbf29ce484222325` and prime `0x00000100000001b3`.
2. XOR the root seed, the namespace hash rotated left by 17 bits, and version domain constant
   `0x534b594645454431` (`SKYFEED1`).
3. Apply the SplitMix64 finalizer: xor-shift 30 and multiply by `0xbf58476d1ce4e5b9`, xor-shift 27
   and multiply by `0x94d049bb133111eb`, then xor-shift 31.

All arithmetic is Java 64-bit two's-complement arithmetic with intentional overflow. Namespace text
is never trimmed, case-folded, Unicode-normalized, or otherwise silently changed. The resulting
64-bit pattern is the local seed for exactly one semantic operation.

## Signal decision

`PlanarValueSignalNode` version 1 is a dependency-free graph node over the horizontal `x-z` plane.
It records the signal version, seed-derivation version, root seed, semantic namespace, and positive
binary64 scale. It may produce either a 2D or 3D scalar field; in 3D its value remains independent of
`y`.

The reference algorithm is periodic over `2^20` lattice cells in each horizontal axis. World
coordinates are divided by scale and wrapped to that lattice period. A lattice coordinate `(x,z)`
is hashed as:

`mix64(localSeed xor (x * 0x9e3779b97f4a7c15) xor (z * 0xd1b54a32d192ed03))`.

The high 53 hash bits map to `[-1,1)`. Adjacent lattice values are interpolated first along `x` and
then `z` with cubic smoothstep `t^2(3-2t)`. The final result is defensively clamped to `[-1,1]`.
Golden raw-binary64 samples pin the complete algorithm.

Canonical graph schema 2 adds this node and encodes root seeds as exactly 16 lowercase hexadecimal
digits and scales as canonical binary64 hexadecimal strings. Graphs containing only schema-1 node
kinds continue to serialize as schema 1 with byte-identical output.

## Island integration decision

The seeded recipe uses namespace `island.height-detail` and applies:

`H_seeded = H_base * (1 + 0.15 * signalAmplitude * signal)`.

Because descriptor amplitude is in `[0,1]` and signal is in `[-1,1]`, the modulation factor is in
`[0.85,1.15]`. It is therefore strictly positive: the sign and zero set of the base height are
preserved for every seed and coordinate. Land connectedness, shoreline, area, centroid, and
principal axis remain base-morphology properties, while interior elevation may vary by at most 15
percent. Density remains exactly `H_seeded - y`.

At zero amplitude, `SeededIslandRecipe` returns the original recipe-v1 artifact. Its canonical graph
bytes, evidence checksums, and recipe metadata therefore remain exactly unchanged.

## Acceptance

`SF-ISL-007` uses root seeds `Long.MIN_VALUE`, `-1`, `0`, `1`, `0x534b59464f524745`, and
`Long.MAX_VALUE` at full signal amplitude. Every member must retain the exact base land-mask hash and
morphology metrics, contain one connected component with no boundary contact, keep peak height in
`[0.85E,1.15E]`, produce a distinct height checksum, and reproduce the same raw grids under forward,
reversed, and parallel schedules.

## Consequences

- Seeded evaluation is independent of global RNG state, node declaration order, sampling order,
  batching, and thread identity.
- Stable namespaces are compatibility-bearing identifiers and must not be renamed casually.
- The first signal is deliberately modest and periodic; richer signal families remain out of v0.1.
- The 15-percent cap is a recipe contract, not a descriptor implementation detail.
- Bitwise identity across future Skyforge, seed-derivation, or signal versions is not promised.
