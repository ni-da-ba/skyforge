# SF-IMP-0051 Terrain Projection Acceptance

- **Status:** Pending final owner-domain interactive proof
- **Date:** 2026-09-01
- **Decision:** ADR-0055
- **Issue:** #52

## Acceptance statement

SF-IMP-0051 isolates native/modded jigsaw `terrain_matching` projection by explicit terrain ownership. Vanilla base terrain and every independently compiled Skyforge island are distinct domains; a terrain-sensitive operation resolves one domain first and unrelated vertically stacked terrain is invisible to its surface query.

## Automated requirements

1. The shared `TERRAIN_MATCHING` processor list contains one Skyforge-scoped gravity processor in place of the exact vanilla gravity processor and bootstrap is idempotent.
2. The replacement processor executes vanilla transformation behavior first and is inert outside a Skyforge terrain-projection scope.
3. `BASE_WORLD` projection reads only a surface snapshot captured after vanilla surface construction and before Skyforge realization.
4. `SKYFORGE_VOLUME(id)` projection reads only the deterministic first-free surface of that exact compiled volume.
5. No projection resolver begins from the composite/global top surface or scans downward between terrain domains.
6. A placement anchor below all Skyforge envelopes resolves to `BASE_WORLD` even when an island is the highest block surface at the same X/Z.
7. A placement anchor inside exactly one island envelope resolves to that exact `SkyIslandWorldVolumeId`.
8. Ambiguous multi-volume anchor ownership fails open to vanilla rather than merging or selecting an island heuristically.
9. Missing domain surface evidence fails open to vanilla.
10. The base-world snapshot is chunk-scoped, consumed for the matching decoration pass, and not serialized.
11. The dedicated development fixture resolves the lower village root and upper island as different terrain domains.
12. Full repository tests and both standard evidence-publication stages pass on the exact PR head.

## Existing interactive evidence

The pre-refactor compatibility bridge already reproduced and corrected the real failure mode in Minecraft. The user observed an overlapping lower village while the upper island remained uncontaminated. The run log recorded hundreds of corrections where Minecraft's composite heightmap selected the upper island around Y=224 while the lower village terrain belonged around Y=63–76.

That evidence validates the specimen and visual target, but the final owner-domain refactor materially changed the source of the corrected height. One short final rerun is therefore required.

## Final interactive requirements

Run the dedicated SF-IMP-0051 ModDev client against a **new disposable Skyforge Development world**.

The proof is accepted only if:

1. a lower plains village generates around/overlapping the forced lower candidate;
2. the clean floating Skyforge island generates and remains stable;
3. the console emits at least one `SF-IMP-0051 TERRAIN PROJECTION SCOPED` marker;
4. no village path, plank, road, foundation, or other terrain-matching continuation appears on the upper island;
5. the lower village remains present rather than being rejected as a whole;
6. no old SF-IMP-0046 pocket/ring morphology appears on the 0051 island;
7. generation completes without crash or loading hang.

## Merge boundary

The SF-IMP-0051 PR must remain unmerged until exact-head automated validation and the final owner-domain interactive Minecraft proof pass and this review is updated to **Accepted**. The user has already authorized proceeding and merging as needed once those gates are satisfied.
