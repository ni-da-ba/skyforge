# ADR-0055: Vertical-scope terrain-matching structure projection

- **Status:** Proposed
- **Date:** 2026-09-01
- **Milestone:** SF-IMP-0051

## Context

The SF-IMP-0050 interactive proof exposed a stacked-surface compatibility defect unrelated to its underside-contradiction rule. A vanilla village rooted on ordinary Overworld terrain below the floating Skyforge specimen left path/plank continuation blocks on the upper island.

Minecraft's jigsaw `terrain_matching` projection owns this behavior generically. `StructureTemplatePool.Projection.TERRAIN_MATCHING` contributes a shared `GravityProcessor` whose block processing asks the current heightmap for the highest surface at each projected X/Z. In a vertically stacked Skyforge world, that globally highest surface may belong to a different suspended volume than the structure being placed.

Rejecting villages or cataloguing affected structures would violate Skyforge's naturalization and unknown-mod compatibility goals.

## Decision

Skyforge SHALL retain Minecraft's shared terrain-matching projection and replace only its one vanilla `GravityProcessor` with a subtype that executes the vanilla processor first.

The replacement SHALL return the vanilla result unchanged unless all of the following are true:

1. placement is executing inside the biome-decoration path of `SkyforgeNoiseBasedChunkGenerator`;
2. an active Skyforge runtime binding exists;
3. the heightmap-selected solid sample is owned by exactly one independently compiled Skyforge volume;
4. the native template placement anchor lies at or below that exact volume's compiled underside at the projected X/Z;
5. a lower heightmap-opaque surface can be found without guessing ownership.

When those conditions prove that the heightmap selected an unrelated upper Skyforge terrain body, the resolver SHALL skip below that volume's underside and continue downward. Multiple vertically stacked upper Skyforge volumes may be skipped independently under the same proof rule.

If ownership is absent, overlapping, unavailable, non-finite, or otherwise ambiguous, Skyforge SHALL preserve the vanilla result.

## Shared processor seam

Minecraft's `terrain_matching` projection is shared by vanilla and ordinary modded jigsaw pool elements. Skyforge widens only the projection's processor-list field through an Access Transformer so bootstrap can replace the exact vanilla `GravityProcessor` while preserving every other processor.

If bootstrap does not observe exactly one exact vanilla gravity processor, it logs a warning and leaves the shared projection unchanged. This avoids overwriting another mod's incompatible ownership of the seam.

The replacement processor remains globally installed, but its correction is gated by a thread-local scope opened only around `SkyforgeNoiseBasedChunkGenerator.applyBiomeDecoration`. Ordinary generators and dimensions therefore receive the exact superclass result.

## Placement-anchor contract

The structure/template placement anchor is used only as conservative vertical provenance evidence. If the anchor is above an island's underside, Skyforge cannot positively prove that island is unrelated to the placement and preserves the vanilla top.

This intentionally prefers false negatives over moving native structure blocks to a guessed surface.

## Naturalization boundary

The rule does not inspect:

- village identity;
- structure registry keys;
- template names;
- block palette semantics;
- mod ownership;
- jigsaw pool names.

Any vanilla or modded jigsaw element using ordinary `terrain_matching` projection receives the same vertical-ownership correction automatically.

## Development proof

A dedicated SF-IMP-0051 ModDev run uses a clean, pocket-free floating island centered east of a forced lower plains-village candidate. The village root lies outside the island, while its outskirts can overlap the island in X/Z.

A successful correction emits:

```text
SF-IMP-0051 TERRAIN PROJECTION SCOPED
```

Interactive acceptance requires the lower village to remain present and coherent while the upper island remains free of path/plank or other village terrain-projection contamination.

## Rejected alternatives

### Reject villages near upper islands

Rejected. This is a generic height-projection defect and unknown modded jigsaw structures would remain broken.

### Reject every jigsaw structure under a Skyforge island

Rejected. It destroys valid native structures instead of constraining only the erroneous adaptation.

### Copy or reimplement jigsaw placement

Rejected. Skyforge should preserve Minecraft's processor pipeline and alter only the proven cross-volume height choice.

### Choose the nearest surface heuristically

Rejected. Ambiguity must preserve vanilla behavior; Skyforge does not invent structure intent.

## Validation

SF-IMP-0051 requires automated evidence for vanilla passthrough, one and multiple upper-volume skips, anchor-within-volume preservation, ambiguous ownership fail-open behavior, missing-evidence fail-open behavior, scope isolation, bootstrap idempotence, and exact runtime volume provenance. It additionally requires an interactive Minecraft proof before this ADR can become **Accepted**.
