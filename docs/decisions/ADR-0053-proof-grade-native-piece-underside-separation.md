# ADR-0053: Proof-grade native-piece underside separation

- **Status:** Proposed
- **Date:** 2026-09-01
- **Milestone:** SF-IMP-0049

## Context

SF-IMP-0048 established a read-only sampled 3-D observation seam for relating native `StructurePiece` geometry to one exact independently compiled Skyforge island. Its four categories intentionally remain factual rather than prescriptive.

The next architectural question is how Skyforge can distinguish positive physical evidence from uncertainty without drifting into a semantic structure taxonomy. Sparse four-block observation is appropriate for descriptive evidence but is too weak to support a future veto: unsampled Minecraft block coordinates would remain unknown.

A particularly strong geometric fact is available below a floating island. If every integer coordinate represented by an entire native piece bounding box lies at or below one exact island's compiled underside, then every possible block placed inside that bounding box is separated beneath that island. Because a piece's actual blocks are a subset of its bounding box, bounding-box over-approximation cannot turn an intersecting piece into a wholly-below proof.

This fact still does not, by itself, authorize rejection of a native structure. A mod may intentionally create unusual geometry. The evidence and the admission policy therefore remain separate milestones.

## Decision

Skyforge SHALL add a Minecraft-side proof-grade underside-separation probe for native `StructurePiece` bounding boxes.

The probe SHALL:

1. retain the exact supporting `SkyIslandWorldVolumeId`;
2. translate the native integer `BoundingBox` through the accepted neutral 3-D observation seam;
3. sample at spacing `1.0`, covering every integer Minecraft coordinate represented by the closed bounding box;
4. return positive evidence only when every sampled coordinate is `atOrBelowUndersideSurface` for the exact supporting volume;
5. return no evidence for mixed, solid, above-crown, or `openBetweenSurfaces` observations;
6. return no evidence when the exact proof would exceed a bounded one-million-coordinate budget;
7. make no change to `StructureStart` admission, fallback, placement, accommodation, or terrain.

The existing four-block observation policy remains unchanged for descriptive SF-IMP-0048 observation. Proof-grade sampling is a separate backend policy because a future intervention requires stronger evidence than ordinary telemetry.

## Why integer-grid coverage is sufficient here

Minecraft structure pieces operate on integer block coordinates. For a native integer bounding box, one-unit sampling from each minimum through each maximum coordinate visits every lattice coordinate the box can represent.

This proof does not claim anything about continuous space between Minecraft block coordinates. It does not need to: the future question is whether a native Minecraft piece can place a block coordinate that intersects the supporting Skyforge volume.

## Conservative failure behavior

If exact proof would exceed the bounded work budget, the probe SHALL return no evidence rather than throwing, approximating, or falling back to sparse sampling.

Thus:

```text
cannot prove cheaply and exactly -> preserve uncertainty
```

not:

```text
cannot prove cheaply and exactly -> reject
```

## Consequences

### Positive

- Unknown vanilla/modded pieces receive identical treatment.
- A future admission policy can consume a strong, explicit geometric fact instead of guessing from sparse samples.
- Bounding-box over-approximation is conservative for the wholly-below condition.
- Stacked islands remain independent because the proof is against one exact volume identity.
- Large or unusual modded pieces fail open when proof is too expensive.

### Tradeoffs

- The proof detects only one narrow relation: an entire piece beneath one island underside.
- It deliberately does not classify caves, lateral exposure, or mixed boundary crossings.
- It does not establish that a wholly-below piece makes the overall native structure invalid; that policy remains future work.
- Exact integer sampling is more expensive than descriptive four-block sampling, hence the explicit work bound.

## Rejected alternatives

### Treat any below-underside sample as a contradiction

Rejected because mixed pieces may validly intersect or emerge from the island body.

### Use the existing four-block observation as proof

Rejected because unsampled Minecraft coordinates could intersect terrain.

### Infer lateral exterior from `openBetweenSurfaces`

Rejected because that category deliberately includes legitimate caves and concavities.

### Immediately wire the proof into `tryGenerateStructure`

Rejected for this milestone. Evidence semantics must be accepted independently before changing live generation behavior.

## Validation

SF-IMP-0049 requires automated evidence that:

- proof requirements preserve all X/Y/Z bounds and use one-block sampling;
- wholly-below exact observations produce evidence;
- mixed observations do not;
- `openBetweenSurfaces` observations do not;
- evidence cannot substitute a different volume identity;
- oversized pieces fail open before runtime observation;
- the active development runtime can prove a small integer box wholly below its exact island underside;
- no generator admission path is changed;
- complete repository CI and both standard evidence-publication stages pass on the exact PR head.

ADR-0053 remains **Proposed** until these tests and exact-head CI pass.
