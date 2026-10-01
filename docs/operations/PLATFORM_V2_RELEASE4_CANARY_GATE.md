# Platform v2 Release-4 canary mutation gate

Release 3 is accepted, but **live v2 mutation remains disabled** until the outstanding Release-0 primary-workstation preservation audit is completed and recorded.

The authoritative machine gate is:

`docs/agent-state/PLATFORM_V2_MUTATION_GATE.json`

The gate is intentionally fail-closed. A live canary requires all of the following:

1. the Release-3 acceptance record remains accepted;
2. every Skyforge checkout/worktree on Nicholas' primary workstation has been enumerated;
3. no unpushed commit containing irreplaceable work remains without an independent preserved copy;
4. no stash containing irreplaceable work remains without an independent preserved copy;
5. no dirty/untracked local file containing irreplaceable work remains without an independent preserved copy;
6. durable audit evidence is recorded in the gate;
7. exactly one canary issue is enabled in the gate;
8. legacy production holds an active external-producer claim for that exact issue and canary branch;
9. the canary task base SHA still equals current accepted `main`;
10. the v2 canary process acquires the dedicated writer fence before any mutation adapter may run.

R4A implements only the pure guard. It cannot create branches, push, create PRs, comment, merge, invoke Codex, or mutate legacy state.

The first live canary remains constrained to a pre-staged documentation/state-only branch under `platform/v2-canary/*`, with its only changed artifact under `docs/operations/platform-v2-canary/`. Subsequent authority expansion requires a separately accepted tranche.
