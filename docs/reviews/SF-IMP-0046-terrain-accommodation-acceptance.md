# SF-IMP-0046 Terrain Accommodation Acceptance

- **Status:** Accepted
- **Date:** 2026-09-01
- **Decision:** ADR-0050

## Acceptance statement

SF-IMP-0046 is milestone-complete. The implementation establishes a deterministic fill-only accommodation path for native Minecraft structures that preserves authoritative Skyforge geometry, vanilla structure identity and vanilla structure-set fallback semantics, while conservatively preserving vertically deferred native structures outside the start-time accommodation seam.

The final `runAccommodationClient` specimen was generated and inspected on a real Minecraft client on 2026-09-01. The user confirmed that the cleaned fixture produced one bounded local Skyforge landform with a woodland mansion on the island rather than the earlier mansion grid/cuboid, that the accommodation result was visually acceptable, and that save/reload persistence remained correct. The runtime self-check had already required the real mansion start to resolve at the claimed Skyforge surface, reject natural support, and accept bounded fill-only accommodation before the world could complete generation.

## Automated implementation evidence

The implementation proves:

- a Skyforge height query is retained as provenance evidence rather than treated automatically as final structure placement;
- full height claims survive the candidate trace so the resulting native start can be checked for vertical coincidence;
- starts whose minimum Y is not resolved at a claimed Skyforge first-free height remain vanilla-owned and are not rejected speculatively;
- natural structure admission remains unchanged for resolved starts from SF-IMP-0045;
- accommodation is considered only after natural support rejection;
- the Minecraft accommodation footprint is sampled at one-block resolution, covering every integral column in the native bounding box;
- 100% interior support is required from one independently compiled Skyforge volume;
- the neutral foundation fill plane is distinct from the caller-authorized maximum natural surface plane;
- the Minecraft surface ceiling is derived from the retained resolved first-free claim and can be at most one block above the structure floor boundary;
- tolerated native occupied/free-layer overlap does not inflate required fill depth;
- terrain above the authorized native surface ceiling is still rejected as excavation;
- required fill depth is explicitly bounded;
- the realized foundation's block-placement range cannot exceed that admitted depth;
- `SkyforgeFoundationPiece` is a registered serializable Minecraft structure piece;
- the piece persists the exact supporting `SkyIslandWorldVolumeId`, foundation top block and fill bound;
- foundation realization verifies support against that exact volume rather than attaching to arbitrary vanilla terrain, another structure or another stacked island;
- original vanilla structure pieces, start chunk and references are preserved;
- rejected resolved accommodation restores the prior structure-start state and leaves vanilla fallback authoritative;
- no Minecraft or NeoForge type enters `skyforge-world`.

CI #116 passed the initial implementation gate, CI #122 passed after the first self-checking client fixture, CI #130 passed after one-block sampling, exact support-volume provenance and exact-depth hardening, CI #159 passed the two-plane occupied/free-coordinate correction, and CI #163 passed the isolated radial specimen and sparse forced-structure fixture on exact head `68599931cef52c8753b404bc8915b6fa7e48482b` before final interactive acceptance.

## Interactive findings retained as design evidence

Four interactive failures were treated as evidence rather than waived:

1. the first fixture used a 3-D-only graph node in a 2-D surface graph;
2. the desert-pyramid specimen demonstrated that a native structure can consult elevated Skyforge height while retaining a provisional Y until a later placement phase;
3. the first woodland-mansion specimen resolved its real start at `BoundingBox{minY=223}` while the retained Skyforge first-free surface was one block higher, exposing that the original single-plane foundation contract incorrectly treated Minecraft's native occupied/free-block adjacency as excavation;
4. the first visually successful mansion-oriented fixture used a development structure-set spacing of only five chunks and axis-aligned density walls, producing a grid of forced mansions on an enormous floating cuboid rather than one inspectable specimen.

These findings produced, respectively, fixture graph validation coverage, explicit resolved-vs-deferred structure filtering, the two-plane foundation contract, and an isolated radial development specimen with one nearby deterministic mansion candidate. The final fixture uses `spacing=512` / `separation=511`, and a regression verifies that the specimen is solid near its center but absent at the old square-corner region.

## Final interactive acceptance evidence

The accepted client run confirmed all required properties:

1. one bounded floating Skyforge landform is present locally, not the earlier cuboid or mansion grid;
2. the native woodland mansion exists at the elevated Skyforge site;
3. fill-only accommodation supports the intended uneven site without mass structure spawning;
4. surrounding Skyforge terrain remains intact apart from normal native structure placement semantics;
5. no unsupported island-edge bridge was observed;
6. save/quit and reload preserve the structure and accommodation correctly.

SF-IMP-0046 is therefore **Accepted** and PR #47 is eligible for the explicit merge gate.