# Skyforge current project state

## Owner-approved private-server alpha direction — 2026-10-18

Immediate target: one playable private-server province in a purely floating Overworld. Skyforge owns island form/underside, island and cluster placement, province/world coherence, sky-biome identity, and exact generation boundaries. Vanilla and selected modded generation should shape local island terrain, biomes, hydrology, and useful features inside those boundaries. Nether and End remain on the selected mod-stack paths.

The earlier DR-70 / #754 hydrology completion gate applies to the broader hydrology product, not to beginning this alpha. Keep accepted hydrology and meso-scale work; defer unfinished multi-reach/F4 work from the alpha critical path pending provider evidence and classify it KEEP / ADAPT / DEFER / SUPERSEDE in #1514. Do not delete active implementations or close their PRs. See `docs/agent-state/PROGRAM_ROADMAP.md` and #1514.

This file is a compact operator/agent bootstrap cache. It does **not** override current `main`, source/tests, merged history, active issue/PR authority, or accepted runtime evidence.

## Active private-alpha pilot — 2026-10-19

Verified current main: `2f2d1554e685515ccd111dad854c59b84fad1c71` (merged PR #1531). The owner-authorized provider pilot is issue [#1514](https://github.com/ni-da-ba/skyforge/issues/1514): one coherent province in a genuinely void Overworld, with Skyforge retaining island/cluster/province/world/sky-biome authority and vanilla plus selected modded providers contributing within exact island boundaries.

### Provider and SkyBiome work in flight

- Vanilla/void-base integration: draft PR #1518; Tectonic terrain A/B: #1522; provider surface rules: #1533; local visual review path: #1535. Machine A/B run [39305703495](https://github.com/ni-da-ba/skyforge/actions/runs/39305703495) passed, including exterior-void and island-body witnesses. The owner reports the latest treatment client still spawned away from the island, found it by flying, and saw a scuffed surface. Spawn and visual acceptance remain open; gravel is observed on provider tops, while a gravel-to-seam cause is unverified.
- Modded biome contribution: draft PRs #1519 and #1532; provider-biome diagnostic PR #1537. Its exact-head scan is intended to record live biome-key distribution at admitted shared surface columns before changing target or search scope.
- SkyBiome meaning and geometry: taxonomy PR #1523 and geometry zones PR #1531 are merged. Persistent world identity remains in draft PR #1525.
- Standard CI for provider review remains in flight where recorded by the exact PR checks; only machine evidence attached to the exact current head should be treated as acceptance.

### Hydrology and meso-scale disposition

Issue #1514 retains the owner-approved classification: KEEP accepted semantics, exact ownership, determinism, cave safety, fluid containment and D2 limits; ADAPT provider hydrology as a local proposal subject to Skyforge admission; DEFER unfinished multi-reach/F4 production work and broad DR-70 completion from the first alpha path; SUPERSEDE only the old sequencing assumption that bespoke hydrology must finish first. Do not delete existing work or close its independent PRs. A4MC remains the live atmosphere authority, and #1142 remains a separate deferred terrain-input seam.

The private-server alpha authorization does not decide the separate first-public-alpha gate HS-05 or broader DR-70 acceptance.

## Product boundary

DR-70 remains **CHANGES REQUIRED** for the broader hydrology product. The owner-approved private-server alpha may proceed without completing #1084 first by using a bounded native/modded provider path. This sequencing change does not complete DR-70, alter its technical or human acceptance gates, or weaken its ownership safeguards. Platform acceptance does not satisfy DR-70.

## Operator references

| Need | Read/use |
| --- | --- |
| Normal accepted-main Platform-v2 update | `docs/operations/PLATFORM_V2_ROUTINE_UPGRADE.md` |
| Cutover, rollback, or protected-authority recovery | `docs/operations/PLATFORM_V2_R5C26_OPERATOR_CUTOVER.md` |
| Execution-location rules | `docs/agent-state/EXECUTION_BOUNDARIES.md` |
| Historical migration/acceptance evidence | `docs/operations/PLATFORM_V2_R*.md` and tracked evidence files |

## Bootstrap rule

Fresh producers should verify current GitHub `main`, current issue/PR authority, and accepted evidence before acting. Ordinary activation-equivalent repository work may move the live repository snapshot beyond the runtime baseline above; if this cache is stale, newer authoritative state wins.
