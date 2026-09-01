# ADR-0054: Detached underside structure contradiction admission

- **Status:** Proposed
- **Date:** 2026-09-01
- **Milestone:** SF-IMP-0050

## Context

SF-IMP-0048 established descriptive 3-D observation of native `StructurePiece` geometry against one exact Skyforge island. SF-IMP-0049 strengthened one narrow observation into proof-grade evidence: an entire native integer piece bounding box can be proven wholly at or below that island's underside.

That evidence alone is not enough to reject a native structure. Vanilla and modded structures may legitimately contain deep shafts, hanging geometry, exposed underground pieces, or other unusual forms. Skyforge therefore needs a stronger structure-level contradiction that remains generic and strongly biased toward preserving native behavior.

## Decision

For a structure start whose floor has already been proven resolved at one elevated Skyforge surface and whose height claims resolve to exactly one `SkyIslandWorldVolumeId`, Skyforge MAY reject the candidate before support/accommodation only when all of the following are true:

1. native piece bounding boxes form at least one geometric component rooted at the resolved structure floor;
2. a different piece-box component is geometrically disconnected from every surface-rooted component;
3. every piece bounding box in that disconnected component has proof-grade SF-IMP-0049 evidence that every represented Minecraft integer coordinate lies at or below the exact supporting island underside;
4. every required proof remains within the bounded proof budget.

If any condition is uncertain, Skyforge SHALL preserve the native candidate with respect to this rule.

When the contradiction is proven, Skyforge SHALL restore the chunk's pre-candidate structure-start map and return `false` from the existing wrapped native candidate method. Minecraft therefore remains responsible for structure-set weighting, alternatives and fallback.

## Connectivity contract

Two native piece bounding boxes count as connected when their closed integer intervals overlap or are separated by at most one block on **all three axes**. This intentionally generous adjacency means near-touching geometry is treated as connected even when the actual placed blocks might not touch.

The policy therefore biases toward false negatives rather than false-positive rejection:

```text
near / uncertain connection -> connected -> preserve vanilla
proven detached + proven wholly below -> contradiction
```

All interval arithmetic uses widened integer arithmetic so extreme coordinates cannot overflow the adjacency test.

## Surface-root contract

A component is surface-rooted when at least one of its native piece boxes has `minY` equal to the already-resolved `StructureStart` floor Y.

Separated surface buildings can therefore form multiple surface-rooted components and remain valid. A vertical chain of pieces that reaches the surface remains one rooted component and is not rejected merely because lower pieces extend beneath the island.

## Naturalization boundary

The production policy consumes only:

- the native structure start's ordinary `StructurePiece` bounding boxes;
- the already-established resolved Skyforge surface claim;
- the exact supporting island volume identity;
- proof-grade underside observations from SF-IMP-0049.

It does not inspect structure registry identity, semantic type, template names, piece subclasses, biome purpose or mod ownership.

Unknown modded structures therefore receive the same rule automatically.

## Development proof

The dedicated SF-IMP-0050 ModDev run reuses the accepted isolated mansion/floating-island development terrain and forced origin mansion candidate.

For **admission evidence only**, the development runtime appends one small synthetic bounding box at Y 150..152, far beneath the test island underside. The synthetic box is never inserted into the `StructureStart`, never serialized, never placed and never registered as a structure piece. Production runs use only real native piece boxes.

The proof run must cause the real forced mansion candidate to traverse the normal native start path, resolve at the Skyforge surface, encounter the injected detached-below evidence, restore the pre-candidate start state and return `false`.

A successful proof emits:

```text
SF-IMP-0050 UNDERSIDE CONTRADICTION REJECTED
```

The resulting world must retain the bounded floating island while containing no forced origin mansion.

## Rejected alternatives

### Reject any piece that reaches below the underside

Rejected. Mixed pieces and connected shafts can be legitimate and are not positive contradictions.

### Reject any wholly-below piece

Rejected. A wholly-below piece may still be connected to a valid surface-rooted component through other native pieces.

### Infer structure semantics

Rejected. Skyforge does not need to know whether a structure is a mineshaft, village, mansion or modded ruin.

### Use sparse four-block observation

Rejected for live veto. SF-IMP-0049's exact integer-lattice proof is required.

### Remove only the offending piece

Rejected. Skyforge must not reconstruct or semantically edit an unknown native structure. A proven contradiction rejects the native candidate as a whole and lets Minecraft fallback normally.

## Validation

SF-IMP-0050 requires automated evidence that:

- detached wholly-below components classify as contradictions;
- connected vertical chains are preserved;
- disconnected components containing any unproved piece remain uncertain;
- separated surface-rooted components are preserved;
- one-block adjacency is treated as connected;
- absence of a surface root fails open;
- extreme integer coordinates do not overflow connectivity;
- the active development island runtime proves the injected detached component below the exact island;
- rejection remains before natural support and foundation accommodation and restores the prior structure-start state;
- complete repository CI and both standard evidence-publication stages pass.

It additionally requires an interactive Minecraft proof of the dedicated development run before ADR-0054 can become **Accepted**.
