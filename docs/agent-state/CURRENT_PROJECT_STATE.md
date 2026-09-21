# Skyforge current project state

This file is a compact bootstrap cache for disposable/manual/hosted agents. It does not override current `main`, source/tests, merged history, active issue/PR authority, or live orchestrator state.

## Snapshot boundary

- Platform-v2 is the active production orchestrator at accepted SHA `f43b6fef2bc430252db01e0c20a7445334adef94`.
- The production activation gate is healthy, and Platform-v2 owns writer authority.
- Legacy is inactive and boot-disabled; it remains an emergency/upgrade rollback path while Platform-v2 is authoritative.
- The accepted production platform includes post-merge replay/no-change lifecycle cleanup (#1009), standalone objective scoping, admission, worker execution, and repository-snapshot synchronization.
- OPT-2K (#1015) separates fresh repository snapshots from reviewed V2 runtime activation; runtime activation remains gated while ordinary repository advances can synchronize safely.
- The accepted Music objective cycle runs through #1018, and OPT-2L (#1020) promotes up to two disjoint task authorities with overlap/capacity protection.
- Protected-authority transfer is productized through `platform_v2_operator_cutover.py transfer-authority` and has been exercised in production.

Before using this snapshot, verify current `main`, live orchestrator state, and current issue/PR ownership. If they materially supersede this file, follow the newer authority and update this checkpoint in the same bounded tranche when practical.

## Current product boundary

- DR-70 remains unfinished and deferred.
- Its migration hold was waived only for the Platform-v2 migration. The waiver does not complete DR-70, satisfy its human/product gate, or authorize unrelated milestone transitions. Platform acceptance does not satisfy any human/product gate.
- Do not represent the migration waiver as DR-70 acceptance.

## Active convergence tranche

The Platform-v2 migration/soak tranche is complete. The canonical post-migration optimization plan is `docs/architecture/SKYFORGE_DEVELOPMENT_PLATFORM_OPTIMIZATION_ROADMAP.md`. OPT-1A/1B natural objective intake is accepted in live production: signed `Continue DR-70` resolves durably to the explicit DR-70 human re-review gate without creating task/worker authority. Current platform infrastructure now covers standalone scoping, admission, worker execution, lifecycle cleanup, repository-snapshot synchronization, and bounded disjoint concurrency. Platform optimization and product development should proceed as one workload-driven loop.

## Orchestration boundary

Platform-v2 is the production writer. Legacy remains inactive/boot-disabled as the accepted rollback/upgrade path. Fresh agents should verify this snapshot against current `main`, live ownership, and accepted evidence, then follow the current optimization roadmap and current product/lane authority rather than reconstructing the migration or standalone acceptance run from conversation history.
