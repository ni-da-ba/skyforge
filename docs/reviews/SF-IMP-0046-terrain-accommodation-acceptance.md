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
- the neutral continuous foundation boundary is kept distinct from the highest discrete Minecraft foundation block;
- excavation is rejected;
- required fill depth is explicitly bounded;
- the realized foundation's block-placement range cannot exceed that admitted depth;
- `SkyforgeFoundationPiece` is a registered serializable Minecraft structure piece;
- the piece persists the exact supporting `SkyIslandWorldVolumeId`, foundation top block and fill bound;
- foundation realization verifies support against that exact volume rather than attaching to arbitrary vanilla terrain, another structure or another stacked island;
- original vanilla structure pieces, start chunk and references are preserved;
- rejected resolved accommodation restores the prior structure-start state and leaves vanilla fallback authoritative;
- no Minecraft or NeoForge type enters `skyforge-world`.

CI #116 passed the initial implementation gate, CI #122 passed after the first self-checking client fixture, and CI #130 passed after one-block sampling, exact support-volume provenance and exact-depth hardening.

Two interactive failures were then correctly treated as evidence rather than waived:

1. the first fixture used a 3-D-only graph node in a 2-D surface graph;
2. the desert-pyramid specimen demonstrated that a native structure can consult elevated Skyforge height while retaining a provisional Y until a later placement phase.

The branch now contains regression coverage for the first defect and explicit resolved-vs-deferred filtering for the second. A fresh exact-head CI pass is required before the revised local proof can count toward acceptance.

## Revised interactive acceptance gate

On Windows Command Prompt, run:

```text
gradlew.bat :skyforge-neoforge-1211:runAccommodationClient
```

Create a new disposable world using the Skyforge Development world type and inspect the forced origin woodland mansion near X=8, Y=242, Z=8.

The run must emit:

```text
SF-IMP-0046 FOUNDATION ATTACHED
```

The runtime fixture deliberately fails unless the real mansion start is resolved at the claimed Skyforge surface, rejects the natural relief policy, and accepts bounded fill-only accommodation. This prevents a false-positive visual result.

Manual evidence must confirm:

1. the native woodland mansion exists at the elevated Skyforge site;
2. a fill-only foundation visibly supports depressed portions of its footprint;
3. surrounding Skyforge terrain is not flattened or cut;
4. unsupported island-edge space is not bridged;
5. save/quit and reload preserve the structure and foundation correctly.

The older forced desert-pyramid data may still be present in the development datapack for historical specimens. It is no longer the SF-IMP-0046 positive accommodation proof because its scattered-feature lifecycle defers final vertical alignment beyond `STRUCTURE_STARTS`.

Only after the five revised properties are observed should this review status change to **Accepted** and PR #47 be considered ready for the explicit merge gate.
