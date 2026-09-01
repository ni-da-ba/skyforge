# SF-IMP-0046 Terrain Accommodation Acceptance

- **Status:** Pending interactive proof
- **Date:** 2026-09-01
- **Decision:** ADR-0050

## Acceptance statement

SF-IMP-0046 is not yet milestone-complete. The implementation establishes a deterministic fill-only accommodation path for native Minecraft structures that preserves authoritative Skyforge geometry, vanilla structure identity and vanilla structure-set fallback semantics, while conservatively preserving vertically deferred native structures outside the start-time accommodation seam.

Final acceptance is intentionally withheld until the revised `runAccommodationClient` specimen is generated, inspected, saved and reloaded on a real Minecraft client.

## Automated implementation evidence

The implementation now proves:

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

CI #116 passed the initial implementation gate, CI #122 passed after the first self-checking client fixture, and CI #130 passed after one-block sampling, exact support-volume provenance and exact-depth hardening.

Three interactive failures have been correctly treated as evidence rather than waived:

1. the first fixture used a 3-D-only graph node in a 2-D surface graph;
2. the desert-pyramid specimen demonstrated that a native structure can consult elevated Skyforge height while retaining a provisional Y until a later placement phase;
3. the first woodland-mansion specimen resolved its real start at `BoundingBox{minY=223}` while the retained Skyforge first-free surface was one block higher, exposing that the original single-plane foundation contract incorrectly treated Minecraft's native occupied/free-block adjacency as excavation.

The third failure is the current local proof result from 2026-09-01. The branch now represents foundation fill and the maximum permitted existing surface as separate neutral planes. The change does not enlarge the fill bound, flatten the fixture, or special-case woodland mansions: it preserves fill-depth measurement at the structure floor while using the already-resolved first-free claim only as the excavation ceiling.

A fresh successful CI run on the final pre-interactive head is required before milestone acceptance.

## Revised interactive acceptance gate

On Windows Command Prompt, update the branch and run:

```text
git pull --ff-only
gradlew.bat :skyforge-neoforge-1211:runAccommodationClient
```

Create a **new** disposable world using the Skyforge Development world type and inspect the forced origin woodland mansion near X=8, Y=242, Z=8.

The run must emit:

```text
SF-IMP-0046 FOUNDATION ATTACHED
```

The runtime fixture deliberately fails unless the real mansion start is resolved at the claimed Skyforge surface, rejects the natural relief policy, and accepts bounded fill-only accommodation. This prevents a false-positive visual result.

Manual evidence must confirm:

1. the native woodland mansion exists at the elevated Skyforge site;
2. a fill-only foundation visibly supports depressed portions of its footprint;
3. surrounding Skyforge terrain is not flattened or cut beyond normal native structure placement in its lowest occupied layer;
4. unsupported island-edge space is not bridged;
5. save/quit and reload preserve the structure and foundation correctly.

The historical forced desert-pyramid development resources have been removed from the active SF-IMP-0046 datapack so they cannot compete with or contaminate the mansion proof. Their lifecycle finding remains recorded in ADR-0050 and the architecture note.

Only after the five revised properties are observed should this review status change to **Accepted** and PR #47 be considered ready for the explicit merge gate.
