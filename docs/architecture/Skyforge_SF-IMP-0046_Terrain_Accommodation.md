# Skyforge SF-IMP-0046 Terrain Accommodation Architecture

## Purpose

SF-IMP-0046 extends the accepted SF-IMP-0045 Minecraft structure-admission seam with an explicit third outcome for sites that are physically coherent but not naturally flat enough for the initial Minecraft support policy.

Accommodation is not permission to rewrite the procedural island. The compiled Skyforge surface and density fields remain authoritative. The first accommodation mode is therefore a bounded, fill-only foundation represented as an ordinary serialized Minecraft structure piece.

## Outcome model

```text
native StructureStart
        ↓
claimed Skyforge volume
        ↓
natural SF-IMP-0044 support assessment
        │
        ├─ accepted ─────────────────────────→ NATURAL
        │
        └─ rejected
              ↓
       bounded foundation assessment
              │
              ├─ accepted ───────────────────→ FILL_ONLY_ACCOMMODATION
              │                                  ↓
              │                           prepend serialized
              │                           SkyforgeFoundationPiece
              │
              └─ rejected ───────────────────→ REJECT
                                                 ↓
                                          restore previous start
                                          vanilla fallback continues
```

## Backend-neutral feasibility

`skyforge-world` owns only the geometry-independent meaning of a fill-only foundation:

- `SurfaceFoundationRequirements`
- `SurfaceFoundationAssessment`
- `SkyIslandSurfaceFoundationEvaluator`

The evaluator operates on one independently compiled island at a time and consumes the same upper-surface and density truth used by SF-IMP-0044.

A foundation is feasible only when:

1. every sampled footprint point is supported by the claimed island;
2. the underlying support assessment remains coherent under the caller-owned accommodation thresholds;
3. no sampled natural surface rises above the requested foundation top;
4. at least one sample lies below that top and therefore actually requires fill;
5. the deepest required fill does not exceed the caller-owned bound.

The neutral evaluator remains sampling-policy agnostic. Minecraft deliberately chooses one-block sampling for accommodation, so every integral block column in the native bounding-box footprint is checked rather than only a sparse representative grid.

Consequently, accommodation cannot bridge unsupported island edges, combine vertically stacked surfaces, excavate high terrain, or silently flatten the procedural field.

## Minecraft policy

The initial Minecraft backend retains SF-IMP-0045 natural admission unchanged:

- sample spacing: 4 blocks;
- clearance ring: 2 blocks;
- minimum interior support: 0.90;
- minimum clearance support: 0.50;
- maximum natural relief: 4 blocks.

The first fill-only accommodation policy uses:

- sample spacing: 1 block (every integral footprint column);
- clearance ring: 2 blocks;
- minimum interior support: 1.00;
- minimum clearance support: 0.50;
- maximum evaluated relief: 12 blocks;
- maximum fill depth: 8 world units;
- foundation top: one block below the native `StructureStart` minimum Y.

These values are adapter policy, not kernel or world constants.

## Persistent realization

An accepted accommodation becomes a `SkyforgeFoundationPiece` inserted into the native `StructureStart` before its existing vanilla pieces.

The piece is registered as `skyforge:foundation` and serializes:

- the supporting `SkyIslandWorldVolumeId` hierarchy coordinates;
- the foundation top Y;
- the bounded fill depth.

Using a real `StructurePiece` is intentional. It means accommodation participates in Minecraft's normal structure persistence and chunk-clipped placement instead of relying on a transient global cache or generation-order side channel.

During `postProcess(...)`, each chunk-local X/Z column:

1. searches downward only within the exact bounded fill depth;
2. requires the first eligible solid support in that interval to be owned by the serialized `SkyIslandWorldVolumeId`;
3. refuses the column rather than attaching to vanilla terrain, another structure, or a different stacked Skyforge volume;
4. chooses a nearby subsurface/support material when available;
5. fills only intervening air up to the foundation top;
6. never removes or replaces an existing solid block.

Foundation realization therefore requires the compiled Skyforge runtime binding whenever unfinished structure placement is occurring. A missing binding is treated as a world-generation invariant failure rather than permission to guess support provenance.

The original structure identity, start chunk, reference count and every vanilla structure piece are preserved.

## Invariants

- Compiled Skyforge geometry is never modified to make a structure fit.
- Fill-only accommodation may add support but may not cut terrain.
- Unsupported space at an island edge is not converted into a bridge.
- Every integral Minecraft footprint column is assessed before accommodation is accepted.
- Realized foundation columns may attach only to the exact admitted Skyforge volume.
- Independent vertically stacked island volumes are never combined into one foundation.
- The configured maximum fill depth is an exact upper bound on blocks added in any column.
- Natural acceptance always wins before accommodation is considered.
- Native-ground/equal-height structures remain outside Skyforge admission and accommodation.
- Vanilla structure-set selection and weighted fallback remain authoritative.
- No Minecraft or NeoForge type enters `skyforge-world`.
- Accommodation state is serialized with the structure, not held in process-global mutable worldgen state.

## Validation boundary

Automated tests and CI validate the neutral feasibility semantics, one-block Minecraft accommodation sampling, exact support-volume provenance, fill-depth bounds, custom piece registration/compilation, serialized accommodation metadata and the complete repository evidence gate.

A dedicated development specimen is additionally required to prove the final visual/runtime property: a forced native structure that fails natural relief but passes the bounded foundation assessment must generate with a visible fill-only foundation while leaving surrounding Skyforge terrain intact. Save/reload of that disposable world is the final persistence proof and remains intentionally separate from the automated architecture contract.
