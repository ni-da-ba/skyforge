# SF-IMP-0046 Terrain Accommodation Acceptance

- **Status:** Pending interactive proof
- **Date:** 2026-08-31
- **Decision:** ADR-0050

## Acceptance statement

SF-IMP-0046 is automated-test complete but not yet milestone-complete. The implementation establishes a deterministic fill-only accommodation path for native Minecraft structures that preserves authoritative Skyforge geometry, vanilla structure identity and vanilla structure-set fallback semantics.

Final acceptance is intentionally withheld until the dedicated `runAccommodationClient` specimen is generated, inspected, saved and reloaded on a real Minecraft client.

## Automated implementation evidence

The implementation now proves:

- natural structure admission remains unchanged from SF-IMP-0045;
- accommodation is considered only after natural support rejection;
- the Minecraft accommodation footprint is sampled at one-block resolution, covering every integral column in the native bounding box;
- 100% interior support is required from one independently compiled Skyforge volume;
- excavation is rejected;
- required fill depth is explicitly bounded;
- the realized foundation's block-placement range cannot exceed that admitted depth;
- `SkyforgeFoundationPiece` is a registered serializable Minecraft structure piece;
- the piece persists the exact supporting `SkyIslandWorldVolumeId`, foundation top and fill bound;
- foundation realization verifies support against that exact volume rather than attaching to arbitrary vanilla terrain, another structure or another stacked island;
- original vanilla structure pieces, start chunk and references are preserved;
- rejected accommodation restores the prior structure-start state and leaves vanilla fallback authoritative;
- no Minecraft or NeoForge type enters `skyforge-world`.

CI #116 passed the initial implementation gate. CI #122 passed the full repository gate after the dedicated self-checking accommodation client fixture was added. A further full CI run is required for the support-identity, one-block sampling and exact-depth hardening before interactive execution.

## Interactive acceptance gate

Run:

```text
./gradlew :skyforge-neoforge-1211:runAccommodationClient
```

Create a new disposable world using the Skyforge Development world type and inspect the forced origin desert pyramid near X=8, Y=235, Z=8.

The run must emit:

```text
SF-IMP-0046 FOUNDATION ATTACHED
```

The runtime fixture deliberately fails if the origin structure is naturally accepted or if bounded accommodation rejects it, preventing a false-positive visual result.

Manual evidence must confirm:

1. the vanilla desert pyramid exists at the elevated Skyforge site;
2. a fill-only foundation visibly supports it;
3. surrounding Skyforge terrain is not flattened or cut;
4. unsupported island-edge space is not bridged;
5. save/quit and reload preserve the structure and foundation correctly.

Only after those five properties are observed should this review status change to **Accepted** and PR #47 be considered ready for the explicit merge gate.
