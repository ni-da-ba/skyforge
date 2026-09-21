# Skyforge current project state

This file is a compact bootstrap cache for disposable/manual/hosted agents. It does not override current `main`, source/tests, merged history, active issue/PR authority, or live orchestrator state.

## Snapshot boundary

- Platform-v2 is the active production orchestrator at accepted SHA `42124c184602f58c99819463fe0a833752da3743`.
- The production activation gate is healthy, and Platform-v2 owns writer authority.
- Legacy is inactive and boot-disabled; it remains an emergency/upgrade rollback path while Platform-v2 is authoritative.
- The accepted production platform includes the post-merge replay/no-change lifecycle correction in #1009.
- Standalone objective acceptance completed the autonomous #1006 → PR #1007 development cycle through normal repository validation and handoff.
- Protected-authority transfer is productized through `platform_v2_operator_cutover.py transfer-authority` and has been exercised in production.

Before using this snapshot, verify current `main`, live orchestrator state, and current issue/PR ownership. If they materially supersede this file, follow the newer authority and update this checkpoint in the same bounded tranche when practical.

## Current product boundary

- DR-70 remains unfinished and deferred.
- Its migration hold was waived only for the Platform-v2 migration. The waiver does not complete DR-70, satisfy its human/product gate, or authorize unrelated milestone transitions.
- Do not represent the migration waiver as DR-70 acceptance.

## Active convergence tranche

The Platform-v2 migration/soak tranche is complete. The canonical post-migration optimization plan is `docs/architecture/SKYFORGE_DEVELOPMENT_PLATFORM_OPTIMIZATION_ROADMAP.md`. OPT-1A/1B natural objective intake is accepted in live production: signed `Continue DR-70` resolves durably to the explicit DR-70 human re-review gate without creating task/worker authority. The current platform work remains bounded context and exact-scope packaging, now exercised by the accepted #1006 → PR #1007 cycle. Platform optimization and product development should proceed as one workload-driven loop.

## Orchestration boundary

Platform-v2 is the production writer. Legacy remains inactive/boot-disabled as the accepted rollback/upgrade path. Fresh agents should verify this snapshot against current `main`, live ownership, and accepted evidence, then follow the current optimization roadmap and current product/lane authority rather than reconstructing the migration or standalone acceptance run from conversation history.
