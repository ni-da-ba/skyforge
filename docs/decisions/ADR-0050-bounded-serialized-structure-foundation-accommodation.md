# ADR-0050: Bounded serialized structure foundation accommodation

- **Status:** Accepted
- **Date:** 2026-08-31
- **Milestone:** SF-IMP-0046

## Context

SF-IMP-0045 established a Minecraft structure-admission seam: native candidates that genuinely resolve onto an elevated Skyforge surface may be assessed against the generated `StructureStart` footprint, and unsuitable starts may be rejected without altering procedural island geometry.

SF-IMP-0046 interactive development exposed an important native lifecycle distinction. A structure may call `ChunkGenerator.getBaseHeight(...)` while building its candidate yet retain a provisional piece Y that is adjusted later during placement. A Skyforge height query is therefore not, by itself, proof that the current `StructureStart` bounding box represents the final support plane.

Some coherently resolved surface sites are nevertheless too uneven for the initial natural-support threshold even though a conventional structure foundation could make the building physically plausible. Treating all such sites as failures would unnecessarily reduce native structure compatibility. Conversely, flattening or cutting the island to fit a structure would make backend concerns authoritative over Skyforge geometry and would erase intentional terrain.

Accommodation must therefore remain explicit, bounded, deterministic, persistent, subordinate to the compiled surface, and conservative about where in Minecraft's native structure lifecycle Skyforge is allowed to intervene.

## Decision

Introduce a distinct fill-only accommodation outcome after natural support rejection, but only for structure starts whose vertical placement is demonstrably resolved at a claimed Skyforge surface during `STRUCTURE_STARTS`.

### Height provenance is evidence, not authority

The Minecraft adapter SHALL retain the full `MinecraftSkyforgeHeightClaim` trace emitted while a native candidate is generated.

A generated start SHALL enter Skyforge admission/accommodation only when its minimum Y is coincident with a claimed Skyforge first-free height, allowing one block for adjacent occupied/free block-coordinate conventions.

If no claim resolves the actual start floor, Skyforge SHALL preserve the generated native start unchanged at this seam. This includes provisional/deferred, offset, underground, or otherwise non-coincident placement. Unknown and modded structures therefore remain vanilla-owned under uncertainty rather than being rejected speculatively.

### Backend-neutral foundation feasibility

The backend-neutral world layer SHALL define foundation feasibility in terms of:

1. a neutral support footprint and policy;
2. a requested continuous foundation fill plane;
3. a caller-authorized maximum natural surface plane;
4. an explicit maximum fill depth.

`SkyIslandSurfaceFoundationEvaluator` SHALL assess each independent island volume separately and accept only when:

- the entire sampled footprint is supported by that one volume;
- the support is coherent under the accommodation thresholds;
- no sampled surface lies above the authorized maximum natural surface plane;
- at least one sampled column lies below the foundation fill plane and requires positive fill;
- the deepest required fill, measured only to the foundation fill plane, is within the explicit limit.

The maximum natural surface plane SHALL NOT alter fill-depth measurement. This separation allows a backend to represent a native occupied/free-coordinate convention without turning tolerated existing terrain into fictitious required fill.

Minecraft SHALL realize an accepted accommodation as a registered, serialized `SkyforgeFoundationPiece` attached to the native `StructureStart` while preserving the original structure identity, start chunk, reference count and vanilla pieces.

The foundation piece SHALL fill only air between existing bounded support and the highest foundation block. It SHALL NOT remove or replace solid terrain. At realization time it SHALL require the solid support used for each filled column to belong to the exact `SkyIslandWorldVolumeId` recorded during admission.

## Outcome ordering

The authoritative order is:

0. **PRESERVE_VANILLA** — no retained Skyforge height claim resolves the actual native start floor. Do not interpret the provisional/offset start geometrically at this seam.
1. **NATURAL** — a resolved Skyforge-owned start passes the natural support policy. Preserve it unchanged.
2. **FILL_ONLY_ACCOMMODATION** — natural support rejects, but the stricter complete-footprint foundation feasibility contract accepts. Attach one serialized foundation piece.
3. **REJECT** — a resolved start would require edge bridging, excavation above the authorized native surface ceiling, excessive fill, incoherent support, ambiguous multi-volume provenance, or otherwise fails the neutral contract. Restore the previous start and return `false` so vanilla fallback may continue.

Accommodation is never evaluated as a way to override a successful natural site, and Skyforge does not manufacture a support plane for an unresolved native start.

## Continuous/discrete coordinate decision

Skyforge upper surfaces are continuous boundaries with strict occupancy (`y < upperSurface`). Minecraft height queries return first-free block coordinates.

For a resolved structure whose lowest occupied structure block is at `StructureStart.minY()`:

- neutral foundation fill plane = `StructureStart.minY()`;
- highest serialized foundation block = `StructureStart.minY() - 1`;
- maximum natural surface plane = the highest retained first-free Skyforge claim that resolves this start, clamped no lower than `StructureStart.minY()`.

Because resolved claims are admitted only within one block of the start minimum, the Minecraft surface ceiling can be at most one block above the fill plane. This is not excavation permission. It represents the native case where the top natural terrain block occupies the same discrete layer as the structure's lowest block and is replaced by normal native structure placement.

Fill depth remains measured from `StructureStart.minY()` downward. The adapter SHALL NOT use the higher first-free surface ceiling as the fill plane, because doing so would overstate required fill and weaken the exact fill-depth contract.

This two-plane model replaces the earlier single-plane assumption exposed by the first woodland-mansion proof run, where the real start resolved at `minY=223` against a first-free Skyforge surface one block higher.

## Initial Minecraft policy

Natural admission remains unchanged from ADR-0049.

The initial foundation policy requires:

- 1-block sampling across the native bounding-box footprint;
- 2-block clearance;
- 100% interior support;
- 50% clearance support;
- at most 12 blocks of evaluated surface relief;
- at most 8 world units of required fill;
- foundation fill plane at the resolved native start minimum Y;
- maximum natural surface plane derived from the retained resolved first-free claim and no more than one block above the fill plane;
- highest realized foundation block one block below the start minimum Y.

One-block sampling is intentional: for Minecraft's integral bounding-box coordinates, accommodation checks every footprint column before accepting the start. Natural admission remains on its less expensive 4-block representative grid.

These are Minecraft adapter policy values and may be specialized later without changing the neutral contract.

## Persistence decision

Accommodation SHALL be represented as a real Minecraft `StructurePiece`, not an in-memory registry keyed by chunk or start.

This preserves normal structure serialization, partial-generation behavior and chunk-clipped placement. It also avoids generation-order dependence and process-global mutable accommodation state.

The serialized piece records the supporting `SkyIslandWorldVolumeId`, highest foundation block Y and fill bound. The island geometry itself is not serialized into or rewritten by the piece.

Because partial structure placement can continue after a save/reload, foundation realization SHALL require the active compiled Skyforge runtime binding to verify the recorded support-volume identity. Missing runtime state is an invariant failure; the backend does not fall back to attaching a foundation to arbitrary existing blocks.

## Consequences

### Positive

- Coherent uneven sites can host resolved native structures without flattening Skyforge terrain.
- Structures with deferred or unusual vertical lifecycles remain compatible without per-structure special cases.
- Unknown/modded structures are preserved under uncertainty rather than blocked merely because Skyforge cannot prove their final support plane at `STRUCTURE_STARTS`.
- Native one-block occupied/free conventions no longer cause false excavation rejection.
- Edge gaps cannot be bridged because every footprint column must be supported before admission.
- Foundation realization cannot silently attach to vanilla ground or a different stacked island.
- The maximum fill-depth bound remains exact because the surface ceiling is not used to measure fill.
- Continuous Skyforge boundaries and discrete Minecraft block coordinates remain semantically explicit.
- Structure persistence uses Minecraft's existing structure-piece mechanism.
- Vanilla structure selection and fallback remain authoritative.
- Backend-neutral feasibility remains free of Minecraft types.

### Tradeoffs

- Deferred/offset structures are not accommodation candidates at this seam even if a later lifecycle stage might eventually make them analyzable.
- The first accommodation mode handles only fill; it deliberately cannot excavate, terrace, cut slopes or construct approach roads.
- Overall start bounding boxes remain conservative for sparse multi-piece structures.
- One-block accommodation sampling is more expensive than natural admission, but is evaluated only after natural rejection.
- Material selection for the foundation is initially simple and Minecraft-owned.
- Visual/runtime integration still requires an interactive development proof in addition to automated CI.

## Rejected alternatives

### Treat any Skyforge height query as proof of final structure Y

Rejected after interactive development demonstrated a native scattered-feature start whose provisional bounding box remained near sea level despite consulting an elevated Skyforge height. This would make Skyforge misinterpret Minecraft's own deferred placement lifecycle.

### Use the first-free claim as the foundation fill plane

Rejected because a native structure can resolve its lowest occupied block one layer below that claim. Using the first-free coordinate as the fill plane would overstate required fill by one unit and make the serialized placement bound less exact.

### Treat any natural surface above `StructureStart.minY()` as excavation

Rejected after the real woodland-mansion specimen resolved at `minY=223` while its first-free Skyforge claim was one block higher. That configuration is a native occupied/free-layer convention, not evidence that Skyforge must cut terrain above the structure's resolved surface.

### Translate deferred structures upward ourselves

Rejected because it would replace native structure placement semantics with a Skyforge-specific approximation and directly undermine mod compatibility.

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

CI #116 passed the substantive full repository build/test/evidence gate on the first SF-IMP-0046 implementation head. CI #122 passed with the first dedicated client fixture, and CI #130 passed after one-block footprint sampling, exact support-volume provenance and exact fill-depth hardening.

Interactive runs then exposed three fixture/seam defects that automated coverage did not previously exercise:

1. an invalid 2-D fixture graph;
2. the false assumption that every Skyforge height query implies a vertically resolved `StructureStart`;
3. the single-plane foundation assumption, exposed when the real woodland mansion resolved at `minY=223` while its retained first-free Skyforge surface was one block higher.

The branch now includes regression coverage for kernel-valid fixture construction, full height-claim retention, resolved-vs-deferred start filtering, and distinct neutral fill/surface planes. The mansion failure was not waived or hidden by enlarging the fixture; it changed the coordinate contract so native occupied/free semantics are represented explicitly without loosening fill depth or excavation bounds.

Final milestone acceptance requires a fresh exact-head CI pass plus the revised resolved-start interactive specimen described in the SF-IMP-0046 architecture note.
