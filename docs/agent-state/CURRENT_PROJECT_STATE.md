# Skyforge current project state

This file is a compact operator/agent bootstrap cache. It does **not** override current `main`, source/tests, merged history, active issue/PR authority, or live orchestrator state.

## Latest hydrology boundary — 2026-10-15

Verified development baseline: main@9a87e7ecd63ac1ab94106a191aa273d3c91a706b (PR #1489). Owner accepted key-77 F4K only as an early proof of concept worth improving, not finished hydrology or DR-70 approval (issue #1452). Draft PR #1490 diagnoses the remaining joins. Focused run 38828422251 passes: 55 wet cross sections have level semantic stage but up to 1.410379951 world units of transverse projected-stage variation, entirely from the pointwise world-frame offset. Both reviewed centerlines are straight, excluding the separately demonstrated bend seam as this specimen's cause.

The owner approved that contract change on 2026-10-15. PR #1490 now implements an exact-association physical-terrain candidate in one fixed world-elevation datum, reusing the existing hydraulic solvers and D2 limits. Initial focused validation passes level stage across all 55 checked sections, D2, association binding and voxel admission. Integrated run 38830308214 proves both integer rivers connected with no missing centerline samples and 3,928 persisted water voxels, but the retained old-solid ceiling clips 219 materialized columns. Deeper placement violates excavation burden; a conservative flow-capacity trial rejects (not proof of general impossibility). These trials are archived, not active code. Next boundary is an explicit human decision on a bounded connected water envelope allowed above the old ground while preserving exact island/foreign-volume isolation. Qualified and read-only viewer static tick fencing is repaired; final validation is in progress. No new visual review is requested yet. This is not final hydrology/DR-70 acceptance. See docs/authorship/HYDROLOGY_RIVER_JOIN_CONTINUITY.md and PR #1490 for evidence and exact decision. Controller/orchestrator work is excluded by owner direction; older platform snapshots below are historical, not a dependency for this work.

## Operator snapshot

| Item | Current state |
| --- | --- |
| Repository truth | current GitHub `main`; fetch/verify it when an exact SHA matters |
| Verified runtime baseline | **407f9b973e9d90b99f12319f6d197cf9dd9e67e3** after the OPT-2Q production upgrade |
| Production writer | **Platform-v2 active + enabled** |
| Legacy writer | **inactive + disabled**; retained for rollback/upgrade recovery |
| Production execution | gate clear; driver running and idle; **0 active concurrency claims** |
| Platform reliability pass | **complete through OPT-2Q live proof** |
| Product gate | **DR-70 CHANGES REQUIRED — hydrology architecture reset #1084** |

## What matters now

- **Hydrology is in the Sep-25 continuous-solution reset, not an H6 patch loop.** Issue #1084 remains the design dependency for DR-70/#754.
- The latest H6 implementation is preserved at `archive/hydrology-h6-2026-09-25` (`aa2ac58f15f67c6b64040bea71dfbc4d6a0c43cf`); never extend it.
- The reset has merged semantic-corridor centerlines (C2), hard river/basin qualification machinery (D2/E2), strict realization authority (F0), ordinary qualified fluvial realization (F1), and C2-based discontinuity diagnostics (D3).
- C3 is accepted in PR #1240 (`daf568ee0cc6554da56548fc3eafe5e26375e159`): route cost uses physical-length quadrature, fixed physical probes, and 4/8/16 analytic regressions. Exact-head CI, Hydrology Reset Geometry, DR-40, and DR-50 passed; post-C3 qualification/confluence evidence was regenerated without widening thresholds. Key 287 remains rejected.
- Hydrology has since advanced through F2C/F3/F4 to the F4K prepared-world adapter review. PR #1238 bounds water placement to F4D-authorized carved support. The owner recorded a limited ordinary-fixture containment pass; it is distinct from the final DR-70 gate. Merged Studio S2 / PR #1253 visibly plots bound hydrology causes, flow, channel envelopes, terrain response, and F4E water intent against the exact terrain semantic SHA. The owner accepted this semantic-only review on 2026-10-01; it is not voxel/Minecraft or DR-70 acceptance. Key 287 remains rejected under the unchanged corridor and hard D2 contract. Terminal CASCADE edge outlets remain fail-closed; no free-outfall contract or ordinary-profile substitution is authorized. F3K / PR #1383 now records the fixed-grid natural confluence/CASCADE census for keys 1–768 (1,536 identities): 376 topology candidates and one natural F3H solve at 8/81 key 700, but no qualified F3I/F3E component because connected ordinary reaches violate existing D2 envelope/compatibility constraints. Issue #1396 targets that natural candidate for bounded diagnosis/refinement within unchanged C2/D2 limits. Do not expand the census or alter thresholds, corridor, key-287, or terminal-outlet policy under this work.
- The remaining DR-70 human/product review belongs after Minecraft discretization and must use the key-287 product-review specimen; do not treat the F4K quick-play fixture as that acceptance.
- Retained-basin terrain remains blocked on adequate POND/LAKE calibration; pure-INCISED qualification remains provisional pending dedicated evidence.
- There is **no pending Platform-v2 console action** from the reliability campaign.
- Normal Skyforge development may resume under current lane/product authority.
- The final forcing run ended correctly:
  - **#1030 → `STALE_MANAGED_BASE` / `CLEANED`**
  - **#1031 → `STALE_BASE` / `CLEANED`**
- Draft PR **#1032** is intentionally preserved as stale evidence. It remains open/draft/unmerged; do not rebase, update, or merge it as part of cleanup.
- Platform acceptance does **not** satisfy DR-70 or any other human/product gate.

## Platform capability summary

Platform-v2 has live evidence for:

- signed objective intake, exact-scope promotion, deterministic bounded workers, and managed PR/CI handoff;
- replay suppression, `NO_CHANGE`, and repository-snapshot advancement without weakening activation review;
- two simultaneous disjoint bounded workers with explicit concurrency claims;
- stale-base cleanup both before PR creation (`STALE_BASE`) and after a managed PR already exists (`STALE_MANAGED_BASE`);
- atomic multi-authority rollback transfer without marking transferred work complete;
- scheduling that prevents a human-gated PR from starving unrelated runnable work.

The reliability campaign is complete; do not extend it with additional adversarial platform work unless a new concrete defect appears.

## Product boundary

DR-70 remains **CHANGES REQUIRED**. The reset has progressed beyond the initial contract into qualified continuous Authorship terrain, but it has **not** yet earned a Minecraft/backend restart. #1084 must complete the coupled longitudinal/transition solution, basin calibration, and regenerated qualification evidence first. Platform acceptance does not complete DR-70 or weaken this human/product gate.

## Operator references

| Need | Read/use |
| --- | --- |
| Normal accepted-main Platform-v2 update | `docs/operations/PLATFORM_V2_ROUTINE_UPGRADE.md` |
| Cutover, rollback, or protected-authority recovery | `docs/operations/PLATFORM_V2_R5C26_OPERATOR_CUTOVER.md` |
| Execution-location rules | `docs/agent-state/EXECUTION_BOUNDARIES.md` |
| Historical migration/acceptance evidence | `docs/operations/PLATFORM_V2_R*.md` and tracked evidence files |

## Bootstrap rule

Fresh agents should verify current GitHub `main`, live checkout/service ownership, current issue/PR authority, and accepted evidence before acting. Ordinary activation-equivalent repository work may move the live repository snapshot beyond the runtime baseline above; if this cache is stale, newer authoritative state wins.
