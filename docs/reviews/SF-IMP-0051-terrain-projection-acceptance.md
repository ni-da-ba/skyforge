# SF-IMP-0051 Terrain Projection Acceptance

- **Status:** Pending automated and interactive proof
- **Date:** 2026-09-01
- **Decision:** ADR-0055
- **Issue:** #52

## Acceptance statement

SF-IMP-0051 prevents native/modded jigsaw `terrain_matching` projection from selecting an unrelated upper Skyforge world volume solely because it is the highest heightmap surface at the same X/Z. The native lower structure must remain otherwise vanilla-owned.

## Automated requirements

1. The shared `TERRAIN_MATCHING` processor list contains one Skyforge-scoped gravity processor in place of the exact vanilla gravity processor and bootstrap is idempotent.
2. The replacement processor calls vanilla behavior first and is inert outside a Skyforge terrain-projection scope.
3. A heightmap top with no Skyforge ownership is preserved unchanged.
4. A uniquely owned upper Skyforge volume is skipped only when the native placement anchor is at/below its exact compiled underside.
5. Multiple independently stacked upper volumes can be skipped without merging their identities.
6. An anchor within/above the selected volume's underside preserves the vanilla top.
7. overlapping/multiple ownership fails open.
8. missing underside evidence fails open.
9. absence of a lower opaque terrain surface fails open.
10. The dedicated development fixture keeps the forced village root outside the upper island while providing an X/Z overlap corridor with exact Skyforge provenance.
11. Full repository tests and both standard evidence-publication stages pass on the exact PR head.

## Interactive requirements

Run the dedicated SF-IMP-0051 ModDev client against a **new disposable Skyforge Development world**.

The proof is accepted only if:

1. a lower plains village generates around the forced lower candidate;
2. the clean floating Skyforge island generates above/east of that village and remains stable;
3. the console emits at least one `SF-IMP-0051 TERRAIN PROJECTION SCOPED` marker where village terrain-matching projection encounters the unrelated upper island;
4. no village path, plank, road, foundation, or other terrain-matching continuation appears on the upper island;
5. the lower village remains present rather than being rejected as a whole;
6. no old SF-IMP-0046 pocket/ring morphology appears on the 0051 island;
7. generation completes without crash or loading hang.

## Merge boundary

The SF-IMP-0051 PR must remain unmerged until exact-head automated validation and the interactive Minecraft proof pass, this review is updated to **Accepted**, and the user explicitly authorizes that PR's merge.
