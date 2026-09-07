# Skyforge durable agent handoff

This directory is the repository-resident entry point for durable development state.

**Governing rule**

> Conversation = active working session.  
> Repository = durable project memory.  
> Git history/tests = authoritative evidence.

A fresh agent should read these files before relying on conversational recollection:

1. [PROGRAM_CHARTER.md](PROGRAM_CHARTER.md)
2. the relevant lane state, currently [IMPLEMENTATION_STATE.md](IMPLEMENTATION_STATE.md)
3. [CROSS_LANE_CONTRACTS.md](CROSS_LANE_CONTRACTS.md)
4. recent referenced PRs/commits, source, and tests.

Historical milestone handoffs in this directory remain evidence for their period but are not the canonical current lane state unless explicitly named as such.

## State-document rules

- Keep lane state concise and current.
- Distinguish **MERGED / ACCEPTED**, **IN PROGRESS**, **PROPOSED**, and **MANUAL VERIFICATION REQUIRED**.
- Update lane state at meaningful merge boundaries, contract changes, significant hazards, or handoffs.
- Do not copy chat transcripts into the repository.
- Put detailed proof in tests, PRs, commits, acceptance docs, or dedicated technical documentation; link or reference it from lane state.
- Existing milestone numbering/history is cumulative and must not be reset by this workflow.
