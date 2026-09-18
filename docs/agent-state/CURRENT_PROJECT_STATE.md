# Skyforge current project state

This file is a compact bootstrap cache for disposable/manual/hosted agents. It does not override current `main`, source/tests, merged history, active issue/PR authority, or live orchestrator state.

## Snapshot boundary

- Platform-v2 is the active production orchestrator at accepted SHA `86e83aeb3981bee3c53bc2a390c16ce13c1fa03d`.
- The production activation gate is healthy, and Platform-v2 owns writer authority.
- Legacy is inactive and boot-disabled; it is retained only as rollback history during post-cutover soak.
- This state supersedes the earlier #916 revision that resulted in `RECLASSIFY`.

Before using this snapshot, verify current `main`, live orchestrator state, and current issue/PR ownership. If they materially supersede this file, follow the newer authority and update this checkpoint in the same bounded tranche when practical.

## Current product boundary

- DR-70 remains unfinished and deferred.
- Its migration hold was waived only for the Platform-v2 migration. The waiver does not complete DR-70, satisfy its human/product gate, or authorize unrelated milestone transitions.
- Do not represent the migration waiver as DR-70 acceptance.

## Active convergence tranche

The immediate next program phase is to complete the post-cutover soak, productize upgrade authority transfer, and then resume renewed DR-70 development. Preserve the accepted Platform-v2 production authority and rollback history while soak evidence is completed; do not change unrelated project history or implementation behavior in this documentation checkpoint.

## Orchestration boundary

Platform-v2 is the production writer during soak. Legacy remains inactive/boot-disabled and available only as rollback history. Fresh agent reconstruction should verify this snapshot against current `main`, live ownership, and accepted evidence before selecting bounded work.
