# Skyforge Agent State

This directory is the concise durable handoff layer for active agent work.

## Read order for a fresh agent

Root [`AGENTS.md`](../../AGENTS.md) is the concise injected map for coding agents.

Start with [Current project state](CURRENT_PROJECT_STATE.md), verify its source boundary, and read
[Execution boundaries](EXECUTION_BOUNDARIES.md) before selecting any execution surface.

1. [Program charter](PROGRAM_CHARTER.md)
2. [Validation and evidence economy policy](VALIDATION_POLICY.md)
3. [Human strategy roadmap](HUMAN_STRATEGY_ROADMAP.md)
4. [Lightweight orchestration protocol](ORCHESTRATION_PROTOCOL.md) when supervising or dispatching multiple agents
5. The relevant lane state:
   - [Authorship](AUTHORSHIP_STATE.md)
   - [Content / Experience](CONTENT_STATE.md)
   - [Implementation](IMPLEMENTATION_STATE.md)
   - [Music / Audio](MUSIC_STATE.md)
   - [Presentation](PRESENTATION_STATE.md)
   - [AUDIT](AUDIT_STATE.md)
   - other lane states as they are migrated into this directory
6. [Cross-lane contracts](CROSS_LANE_CONTRACTS.md)
7. The recent PRs/issues and source/tests linked from the lane state

The [27 September director audit](../audit/2026-09-27/START_HERE.md) is a dated cross-project
reference: change register, proposed machine/human roadmap, dependencies, evidence and decisions.
Its graph has dispatch disabled and cannot override the canonical roadmap or active ownership.

Historical milestone handoffs remain under `docs/handoffs/`. Detailed architecture, acceptance evidence,
and design records remain under their existing directories.

## Authority rule

```text
repository state + git history + tests
    > agent-state summaries
    > conversation recollection
```

Agent-state files are navigation documents, not replacements for source, acceptance records, or git history.

Update the relevant lane state whenever a meaningful merge boundary, contract change, hazard, or handoff occurs.