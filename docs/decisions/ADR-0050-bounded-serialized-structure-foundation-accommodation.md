# ADR-0050: Bounded serialized structure foundation accommodation

- **Status:** Accepted
- **Date:** 2026-08-31
- **Milestone:** SF-IMP-0046

## Context

SF-IMP-0045 established a truthful Minecraft structure-admission seam: native candidates that depend on an elevated Skyforge height are assessed against the real generated `StructureStart` footprint, and unsuitable starts are rejected without altering procedural island geometry.

Some coherent sites are nevertheless too uneven for the initial natural-support threshold even though a conventional structure foundation could make the building physically plausible. Treating all such sites as failures would unnecessarily reduce native structure compatibility. Conversely, flattening or cutting the island to fit a structure would make backend concerns authoritative over Skyforge geometry and would erase intentional terrain.

Accommodation must therefore remain explicit, bounded, deterministic, persistent and subordinate to the compiled surface.

## Decision

Introduce a distinct fill-only accommodation outcome after natural support rejection.

The backend-neutral world layer SHALL define foundation feasibility in terms of:

1. a neutral support footprint and policy;
2. a requested foundation top Y;
3. an explicit maximum fill depth.

`SkyIslandSurfaceFoundationEvaluator` SHALL assess each independent island volume separately and accept only when:

- the entire sampled footprint is supported by that one volume;
- the support is coherent under the accommodation thresholds;
- no sampled surface lies above the requested foundation top;
- at least one sampled column requires positive fill;
- the deepest required fill is within the explicit limit.

Minecraft SHALL realize an accepted accommodation as a registered, serialized `SkyforgeFoundationPiece` attached to the native `StructureStart` while preserving the original structure identity, start chunk, reference count and vanilla pieces.

The foundation piece SHALL fill only air between existing bounded support and the foundation top. It SHALL NOT remove or replace solid terrain. At realization time it SHALL require the solid support used for each filled column to belong to the exact `SkyIslandWorldVolumeId` recorded during admission.

## Outcome ordering

The admission order is authoritative:

1. **NATURAL** — the SF-IMP-0045 natural support policy accepts the start. Preserve it unchanged.
2. **FILL_ONLY_ACCOMMODATION** — natural support rejects, but the stricter complete-footprint foundation feasibility contract accepts. Attach one serialized foundation piece.
3. **REJECT** — accommodation would require edge bridging, excavation, excessive fill, incoherent support, ambiguous multi-volume provenance, or otherwise fails the neutral contract. Restore the previous start and return `false` so vanilla fallback may continue.

Accommodation is never evaluated as a way to override a successful natural site.

## Initial Minecraft policy

Natural admission remains unchanged from ADR-0049.

The initial foundation policy requires:

- 1-block sampling across the native bounding-box footprint;
- 2-block clearance;
- 100% interior support;
- 50% clearance support;
- at most 12 blocks of evaluated surface relief;
- at most 8 world units of required fill;
- foundation top one block below the native start minimum Y.

One-block sampling is intentional: for Minecraft's integral bounding-box coordinates, accommodation checks every footprint column before accepting the start. Natural admission remains on its less expensive 4-block representative grid.

These are Minecraft adapter policy values and may be specialized later without changing the neutral contract.

## Persistence decision

Accommodation SHALL be represented as a real Minecraft `StructurePiece`, not an in-memory registry keyed by chunk or start.

This preserves normal structure serialization, partial-generation behavior and chunk-clipped placement. It also avoids generation-order dependence and process-global mutable accommodation state.

The serialized piece records the supporting `SkyIslandWorldVolumeId`, foundation top and fill bound. The island geometry itself is not serialized into or rewritten by the piece.

Because partial structure placement can continue after a save/reload, foundation realization SHALL require the active compiled Skyforge runtime binding to verify the recorded support-volume identity. Missing runtime state is an invariant failure; the backend does not fall back to attaching a foundation to arbitrary existing blocks.

## Consequences

### Positive

- Coherent uneven sites can host native structures without flattening Skyforge terrain.
- Edge gaps cannot be bridged because every footprint column must be supported before admission.
- Foundation realization cannot silently attach to vanilla ground or a different stacked island.
- The maximum fill-depth bound is exact at block placement time as well as in neutral feasibility assessment.
- Accommodation is reproducible from the native structure and accepted Skyforge geometry.
- Structure persistence uses Minecraft's existing structure-piece mechanism.
- Vanilla structure selection and fallback remain authoritative.
- Backend-neutral feasibility remains free of Minecraft types.

### Tradeoffs

- The first accommodation mode handles only fill; it deliberately cannot excavate, terrace, cut slopes or construct approach roads.
- Overall start bounding boxes remain conservative for sparse multi-piece structures.
- One-block accommodation sampling is more expensive than natural admission, but is evaluated only after natural rejection.
- Material selection for the foundation is initially simple and Minecraft-owned.
- Visual/runtime integration still requires an interactive development proof in addition to automated CI.

## Rejected alternatives

### Flatten or cut the island surface

Rejected because it would mutate authoritative procedural geometry to satisfy a backend structure.

### Permit partial footprint support and build bridges across gaps

Rejected because a structure footprint crossing an island edge is not equivalent to a foundation gap over supported terrain.

### Trust arbitrary solid blocks during foundation realization

Rejected because a serialized foundation carrying an island identity must not be allowed to attach to vanilla terrain, another structure, or a different vertically stacked island merely because those blocks happen to occupy the bounded search interval.

### Store accommodation plans in a global runtime map

Rejected because save/reload, chunk order and partial world generation would make correctness depend on transient process state.

### Copy structure blocks into a separate feature system

Rejected because the native structure should remain a native Minecraft structure with its normal identity, references and lifecycle.

## Evidence

CI #116 passed the substantive full repository build/test/evidence gate on the first SF-IMP-0046 implementation head, including NeoForge production compilation, world-layer foundation tests and both deterministic evidence artifact publications. CI #122 then passed the full repository gate with the dedicated accommodation client fixture. A final CI gate after the support-identity and exact-depth hardening is required before local interactive testing. Final milestone acceptance additionally requires the dedicated interactive accommodation specimen described in the SF-IMP-0046 architecture note.
