# Skyforge current project state

This file is a compact operator/agent bootstrap cache. It does **not** override current `main`, source/tests, merged history, active issue/PR authority, or live orchestrator state.

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
- F2 and the F2A deterministic bounded-QP primitive are merged. Current next work is F2B: extract the head-independent C2 hydraulic geometry skeleton with exact legacy equivalence; F2C will then be the first bounded-profile consumer before regenerated qualification evidence and any Minecraft discretization.
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
