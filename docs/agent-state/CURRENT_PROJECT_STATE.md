# Skyforge current project state

This file is a compact bootstrap cache for disposable/manual/hosted agents. It does **not** override current main, source/tests, merged history, active issue/PR authority, or live orchestrator state.

## Read this first

- OPT-2Q source acceptance is anchored by merge **434f89a60d13404f5752052867bb8c39702a8f1a**; verify current GitHub main because later documentation/product work may have advanced it.
- The live production checkout is still **24fd6b3eb0aa9ead0818122146bab6231f1080c4** pending one root/operator routine upgrade. The read-only routine plan against 434f89a6... was **READY** with no blockers immediately before this readability tranche; re-plan against exact current origin/main before execution.
- Platform-v2 is the active/enabled production writer. Legacy is inactive/disabled and remains the emergency/upgrade rollback path.
- Do not equate repository acceptance with live deployment. Verify both before acting.

## What the platform has proven

The post-migration optimization/reliability pass has demonstrated:

- natural signed objective intake and bounded exact-scope promotion;
- deterministic worker execution, managed PR creation, CI handoff, replay suppression, and NO_CHANGE completion;
- repository-snapshot advancement without weakening the reviewed activation boundary;
- two disjoint task authorities, two active concurrency claims, and **two simultaneous bounded workers**;
- fail-closed stale-base terminalization before PR creation (OPT-2N);
- atomic multi-authority legacy transfer for concurrent rollback (OPT-2O);
- runnable/handoff scheduling fixes that prevent one human-gated PR from starving unrelated runnable work (OPT-2M/OPT-2P);
- stale **already-open** managed PR terminalization after base advance without merge/rebase/update (OPT-2Q).

The final concurrency forcing run used #1030 and #1031 from the same accepted base. #1030 created draft PR #1032; later accepted control-plane work advanced main, making that PR semantically stale. OPT-2Q is source-accepted, but the live #1032 terminalization proof still requires the pending root/operator routine upgrade.

## Product boundary

- DR-70 remains **CHANGES REQUIRED / DEFERRED**.
- Its migration hold was waived only for Platform-v2 migration/upgrade continuity.
- The waiver does not complete DR-70, satisfy its human/product gate, or authorize unrelated milestone transitions.
- Do not represent migration/platform acceptance as DR-70 acceptance.

## What to do next

1. Root/operator: re-run the read-only routine plan against exact current origin/main, execute only if it is READY, then verify v2 health and exact target SHA.
2. Observe the live #1030/#1032 forcing case terminalize as **STALE_MANAGED_BASE** without mutating PR #1032; preserve its worker/pipeline/effect evidence.
3. Once that operational proof is recorded, stop adversarial platform testing and return to normal Skyforge development under current lane/product authority.

For accepted-main upgrades use **docs/operations/PLATFORM_V2_ROUTINE_UPGRADE.md**. For privileged writer transitions, rollback, or protected-authority transfer use **docs/operations/PLATFORM_V2_R5C26_OPERATOR_CUTOVER.md**.

## Bootstrap rule

Fresh agents should verify current GitHub main, live checkout/service ownership, current issue/PR authority, and accepted evidence. Follow newer authoritative state when this cache is stale; update this file only as a bounded documentation task rather than treating it as executable authority.
