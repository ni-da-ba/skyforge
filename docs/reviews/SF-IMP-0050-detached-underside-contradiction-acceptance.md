# SF-IMP-0050 Detached Underside Contradiction Acceptance

- **Status:** Pending automated and interactive proof
- **Date:** 2026-09-01
- **Decision:** ADR-0054

## Acceptance statement

SF-IMP-0050 is the first consumer of proof-grade 3-D native-piece evidence that can reject a live structure candidate. It must remain conservative: only a disconnected native piece-box component that is entirely proven beneath the exact supporting Skyforge island may trigger rejection.

## Automated requirements

1. Detached wholly-below components classify as contradictions.
2. A connected vertical piece chain reaching the resolved surface is preserved.
3. A disconnected component containing any unproved or ambiguous piece is preserved.
4. Multiple disconnected components rooted at the resolved structure floor are preserved.
5. Piece boxes separated by at most one block on every axis count as connected.
6. Missing surface-root geometry fails open.
7. Connectivity arithmetic is safe at extreme integer coordinates.
8. The active development island runtime can prove the dedicated detached evidence box wholly below the exact island underside.
9. The live hook runs only after a start is proven surface-resolved against exactly one Skyforge volume and before natural support/foundation accommodation.
10. A positive contradiction restores the pre-candidate start map and returns `false`, retaining Minecraft's normal weighted fallback path.
11. Production candidate geometry contains only ordinary native `StructurePiece` bounding boxes; the synthetic detached box exists only in the dedicated development proof path.
12. Full repository tests and both standard evidence-publication stages pass on the exact PR head.

## Interactive requirements

Run the dedicated SF-IMP-0050 ModDev client against a **new disposable Skyforge Development world**.

The proof is accepted only if:

1. the console emits `SF-IMP-0050 UNDERSIDE CONTRADICTION REJECTED`;
2. the bounded floating Skyforge island appears normally near the origin;
3. the forced origin woodland mansion is absent because its candidate was rejected;
4. no synthetic detached structure geometry appears anywhere in the world;
5. the client/server remains stable without generation crash or loading hang;
6. the development datapack does not produce nearby mansion spam.

Ordinary vanilla terrain below the floating specimen remains expected because this development preset overlays Skyforge terrain on the Overworld.

## Merge boundary

PR #51, once opened, must remain unmerged until both the exact-head automated gate and the interactive Minecraft proof pass, the review is updated to **Accepted**, and the user explicitly authorizes that PR's merge.
