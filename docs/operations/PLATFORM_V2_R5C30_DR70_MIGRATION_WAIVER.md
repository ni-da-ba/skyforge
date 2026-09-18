# Platform v2 R5C30 — DR-70 migration waiver

Status: **OPERATOR WAIVER ACCEPTED FOR MIGRATION; DR-70 REMAINS UNFINISHED**

R5C30 is a narrow post-freeze correction directed by the project operator. DR-70 is still an unfinished product/development milestone and must not be represented as complete. It is also no longer allowed to block the Platform-v2 migration.

The production activation envelope now distinguishes two facts:

- dr70_migration_hold_cleared: the underlying migration hold was resolved through normal milestone/review completion;
- dr70_migration_hold_waived: the operator explicitly authorizes migration to proceed while the underlying DR-70 milestone remains unfinished.

Production activation requires at least one of those facts. A false/false state remains fail-closed. A false/true state is valid and preserves the truth that DR-70 is not complete.

For the current migration, final activation evidence must use dr70_migration_hold_cleared=false and dr70_migration_hold_waived=true, while the product/milestone status remains unfinished/deferred for additional development.

This waiver affects only Platform-v2 migration authority. It does not close DR-70, satisfy its human/product gate, accept its content, or authorize any unrelated milestone transition.

R5C29's architecture freeze otherwise remains in force. After exact-head CI and Orchestrator Smoke accept R5C30, the migration resumes at live checkout synchronization, unit staging, read-only preflight, and privileged writer handoff.
