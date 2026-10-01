# Skyforge SF-IMP-0046 Terrain Accommodation Architecture

## Purpose

SF-IMP-0046 extends the accepted SF-IMP-0045 Minecraft structure-admission seam with an explicit third outcome for sites that are physically coherent but not naturally flat enough for the initial Minecraft support policy.

Accommodation is not permission to rewrite the procedural island. The compiled Skyforge surface and density fields remain authoritative. The first accommodation mode is therefore a bounded, fill-only foundation represented as an ordinary serialized Minecraft structure piece.

## Outcome model

```text
native StructureStart
        ↓
Skyforge height claims observed during native generation
        ↓
does the actual start floor resolve at a claimed Skyforge surface?
        │
        ├─ no ─────────────────────────────────→ PRESERVE VANILLA
        │                                           deferred/independent Y remains native-owned
        │
        └─ yes
              ↓
       one unambiguous claimed Skyforge volume
              ↓
       natural SF-IMP-0044 support assessment
              │
              ├─ accepted ─────────────────────→ NATURAL
              │
              └─ rejected
                    ↓
             bounded foundation assessment
                    │
                    ├─ accepted ────────────────→ FILL_ONLY_ACCOMMODATION
                    │                               ↓
                    │                        prepend serialized
                    │                        SkyforgeFoundationPiece
                    │
                    └─ rejected ────────────────→ REJECT
                                                    ↓
                                             restore previous start
                                             vanilla fallback continues
```

## Native vertical-resolution boundary

A call to `ChunkGenerator.getBaseHeight(...)` during native structure generation is provenance evidence only. It is not proof that the resulting `StructureStart` has adopted that Y coordinate.

Some vanilla structure families create pieces at a provisional Y and move them later during `postProcess(...)`. `ScatteredFeaturePiece`, for example, exposes terrain-height adjustment methods used by structures that align themselves during placement. Skyforge must not reinterpret such a provisional bounding box as a final support plane.

SF-IMP-0046 therefore retains the full `MinecraftSkyforgeHeightClaim` trace and applies admission/accommodation only when the actual start minimum Y is coincident with a claimed first-free Skyforge height, allowing one block for Minecraft's adjacent occupied/free coordinate conventions. If no claim resolves the start floor, the generated start is preserved unchanged and the remainder of its vanilla lifecycle stays authoritative.

This is deliberately conservative. An unknown or modded structure that consults Skyforge height but uses deferred, underground, offset, or otherwise non-coincident placement is not rejected merely because Skyforge cannot prove its support relationship at `STRUCTURE_STARTS`.

## Backend-neutral feasibility

`skyforge-world` owns only the geometry-independent meaning of a fill-only foundation:

- `SurfaceFoundationRequirements`
- `SurfaceFoundationAssessment`
- `SkyIslandSurfaceFoundationEvaluator`

The evaluator operates on one independently compiled island at a time and consumes the same upper-surface and density truth used by SF-IMP-0044.

A foundation request carries two independent vertical planes:

- **foundation fill plane** — the continuous boundary up to which added support may be required;
- **maximum natural surface plane** — the highest existing surface the caller can accept without excavation.

A foundation is feasible only when:

1. every sampled footprint point is supported by the claimed island;
2. the underlying support assessment remains coherent under the caller-owned accommodation thresholds;
3. no sampled natural surface rises above the caller-authorized maximum natural surface plane;
4. at least one sample lies below the foundation fill plane and therefore actually requires fill;
5. the deepest required fill, measured only to the foundation fill plane, does not exceed the caller-owned bound.

Separating the two planes is important: a backend may legitimately tolerate an existing natural surface slightly above the fill plane because of its native occupied/free-coordinate convention without pretending that extra height is required foundation fill.

The neutral evaluator remains sampling-policy agnostic. Minecraft deliberately chooses one-block sampling for accommodation, so every integral block column in the native bounding-box footprint is checked rather than only a sparse representative grid.

Consequently, accommodation cannot bridge unsupported island edges, combine vertically stacked surfaces, excavate terrain above the authorized native surface ceiling, or silently flatten the procedural field.

## Continuous/discrete coordinate contract

Skyforge upper surfaces are continuous boundaries and terrain occupancy is strict (`y < upperSurface`). Minecraft height queries return a first-free block coordinate.

For a native structure whose lowest occupied structure block is at `StructureStart.minY()`:

- neutral foundation fill plane = `StructureStart.minY()`;
- highest serialized foundation block = `StructureStart.minY() - 1`;
- maximum natural surface plane = the highest retained first-free Skyforge claim that resolves this start, never lower than `StructureStart.minY()` and never more than one block above it.

This distinction is required by real vanilla behavior. A woodland mansion can resolve with `StructureStart.minY()` one block below the first-free height it queried. In that case the top natural terrain block occupies the mansion's lowest structure layer, which vanilla structure placement can replace as part of its normal lifecycle; it does **not** imply that Skyforge must excavate terrain above the native resolved surface.

Fill depth remains measured from `StructureStart.minY()` downward. The adjacent first-free plane is only an excavation ceiling, so it cannot inflate the admitted fill depth or the serialized foundation's placement bound.

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
- neutral foundation fill plane: the native resolved `StructureStart` minimum Y;
- maximum natural surface plane: derived from the retained resolved first-free height claim, clamped no lower than the fill plane;
- highest serialized foundation block: one block below the structure minimum Y.

These values are adapter policy, not kernel or world constants.

## Persistent realization

An accepted accommodation becomes a `SkyforgeFoundationPiece` inserted into the native `StructureStart` before its existing vanilla pieces.

The piece is registered as `skyforge:foundation` and serializes:

- the supporting `SkyIslandWorldVolumeId` hierarchy coordinates;
- the highest foundation block Y;
- the bounded fill depth.

Using a real `StructurePiece` is intentional. It means accommodation participates in Minecraft's normal structure persistence and chunk-clipped placement instead of relying on a transient global cache or generation-order side channel.

During `postProcess(...)`, each chunk-local X/Z column:

1. searches downward only within the exact bounded fill depth;
2. requires the first eligible solid support in that interval to be owned by the serialized `SkyIslandWorldVolumeId`;
3. refuses the column rather than attaching to vanilla terrain, another structure, or a different stacked Skyforge volume;
4. chooses a nearby subsurface/support material when available;
5. fills only intervening air up to the highest foundation block;
6. never removes or replaces an existing solid block.

Foundation realization therefore requires the compiled Skyforge runtime binding whenever unfinished structure placement is occurring. A missing binding is treated as a world-generation invariant failure rather than permission to guess support provenance.

The original structure identity, start chunk, reference count and every vanilla structure piece are preserved.

## Invariants

- Compiled Skyforge geometry is never modified to make a structure fit.
- A Skyforge height query alone never authorizes structure admission or accommodation.
- Vertically unresolved/deferred native starts remain vanilla-owned at this seam.
- Unknown/modded structures are preserved under uncertainty rather than rejected speculatively.
- Fill-only accommodation may add support but may not cut terrain above the resolved native surface ceiling.
- Existing terrain tolerated by the native occupied/free convention does not count as required foundation fill.
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

Automated tests and CI validate the neutral feasibility semantics, distinct fill/surface planes, one-block Minecraft accommodation sampling, resolved-vs-deferred height-claim filtering, exact support-volume provenance, fill-depth bounds, custom piece registration/compilation, serialized accommodation metadata and the complete repository evidence gate.

The dedicated interactive specimen uses a forced woodland mansion because its generated pieces resolve from a terrain-derived start position during `STRUCTURE_STARTS`. The specimen plateau contains bounded shallow depressions away from the origin terrain-query neighborhood so the real resolved start can fail natural relief while remaining eligible for fill-only accommodation.

The first mansion interactive run exposed the occupied/free-plane distinction directly: the real start resolved at `minY=223` while the retained Skyforge first-free surface was one block higher. That failure was retained as contract evidence and led to the explicit two-plane neutral requirement rather than a fixture-specific exception.

Interactive acceptance still requires a visible fill-only foundation, intact surrounding Skyforge terrain, the `SF-IMP-0046 FOUNDATION ATTACHED` marker, and save/reload persistence in a disposable development world.
