# Skyforge Agent State

This directory is the concise durable handoff layer for active agent work.

## Read order for a fresh agent

1. [Program charter](PROGRAM_CHARTER.md)
2. The relevant lane state:
   - [Authorship](AUTHORSHIP_STATE.md)
   - [Content / Experience](CONTENT_STATE.md)
   - [Implementation](IMPLEMENTATION_STATE.md)
   - [AUDIT](AUDIT_STATE.md)
   - other lane states as they are migrated into this directory
3. [Cross-lane contracts](CROSS_LANE_CONTRACTS.md)
4. The recent PRs/issues and source/tests linked from the lane state

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
