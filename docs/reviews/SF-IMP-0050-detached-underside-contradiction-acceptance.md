# SF-IMP-0050 Detached Underside Contradiction Acceptance

- **Status:** Accepted
- **Date:** 2026-09-01
- **Decision:** ADR-0054

## Acceptance statement

SF-IMP-0050 is the first consumer of proof-grade 3-D native-piece evidence that can reject a live structure candidate. It remains conservative: only a disconnected native piece-box component that is entirely proven beneath the exact supporting Skyforge island may trigger rejection.

The exact-head automated gate and the dedicated interactive Minecraft proof both passed. The development harness is self-checking: if the forced origin mansion does not generate, resolve at the Skyforge surface, and traverse the detached-underside contradiction path, world generation throws. The successful stable manual run produced the bounded floating island with the forced mansion absent, which therefore confirms the live rejection/restore path.

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

**Automated result:** CI #183 passed the full build/test suite and both evidence-publication stages on PR #51 head `105b2c8cf2e31003c65a9220f9ae3c0759d7a565` before this acceptance-record update.

## Interactive proof

The dedicated SF-IMP-0050 ModDev client was run against a new disposable Skyforge Development world.

Observed results:

1. the bounded floating Skyforge island appeared normally near the origin;
2. the forced origin woodland mansion was absent;
3. no synthetic detached structure geometry appeared in the world;
4. the client/server remained stable without generation crash or loading hang;
5. no nearby forced-mansion grid appeared.

The successful path emits `SF-IMP-0050 UNDERSIDE CONTRADICTION REJECTED`; because the fixture throws if its forced proof candidate fails to reach or satisfy the contradiction path, the stable mansion-free result confirms the same live path even though the console line was not separately copied into the review.

### Known development-fixture geometry

Four identical concentric stepped depressions arranged in a square were visible on the island. These are the four intentional rational-falloff pockets inherited from the SF-IMP-0046 accommodation specimen (`POCKET_COORDINATES = {-16, 32}`), not production morphology or a 0050 rejection artifact. Minecraft's integer voxelization makes the smooth radial test falloff appear as concentric rings.

### Incidental compatibility finding

A nearby vanilla village rooted on the ordinary Overworld terrain below projected some path/plank blocks onto the upper Skyforge island. This is orthogonal to the detached-underside contradiction rule and did not invalidate the 0050 proof. It is tracked separately as issue #52 / SF-IMP-0051: cross-volume terrain-matching structure projection.

The suspected generic mechanism is vanilla jigsaw `terrain_matching` / gravity processing consulting a top-surface heightmap during placement, allowing a lower structure's terrain-adaptive blocks to snap to an unrelated upper Skyforge surface at the same X/Z.

## Acceptance conclusion

SF-IMP-0050 is **Accepted**. PR #51 may proceed to its final documentation-inclusive exact-head CI and explicit user merge gate.
