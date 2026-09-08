# Skyforge agent map

Skyforge uses **repository-first, disposable agents**.

```text
conversation/thread = execution workspace
GitHub              = durable memory
current main/tests  = authority
```

This file is a map, not the full manual. Read deeper documents only when the current task requires them.

## Canonical sources

- Program rules: `docs/agent-state/PROGRAM_CHARTER.md`
- Validation/evidence economy: `docs/agent-state/VALIDATION_POLICY.md`
- Current supervisory snapshot: `docs/agent-state/AUDIT_STATE.md`
- Cross-lane invariants: `docs/agent-state/CROSS_LANE_CONTRACTS.md`
- Human product decisions/triggers: `docs/agent-state/HUMAN_STRATEGY_ROADMAP.md`
- Lane state:
  - Authorship: `docs/agent-state/AUTHORSHIP_STATE.md`
  - Implementation: `docs/agent-state/IMPLEMENTATION_STATE.md`
  - Content: `docs/agent-state/CONTENT_STATE.md`
  - Music/Audio: `docs/agent-state/MUSIC_STATE.md`
  - Presentation: `docs/agent-state/PRESENTATION_STATE.md`
- Lightweight multi-agent operation: `docs/agent-state/ORCHESTRATION_PROTOCOL.md`

If summaries disagree with `main`, source/tests, or merged history, the repository evidence wins.

## Lane ownership

- **Authorship** — backend-neutral world meaning, descriptors, provenance, morphology/geology/hydrology/ecology semantics.
- **Implementation** — Minecraft/NeoForge realization, exact-volume runtime, lifecycle, persistence, performance.
- **Content / Experience** — mod integration, progression, gameplay capability, Bootstrap Province, player experience.
- **Music / Audio** — score/audio authorship, source identity, production evidence and listening gates.
- **Presentation** — accurate project communication/showcase packaging grounded in accepted project truth.
- **Audit** — convergence, validation economy, session/process health, orchestration and human-gate escalation.

Do not seize another lane's technical or product authority merely to keep work moving.

## Worker reconstruction

A producer starting or resuming bounded work should:

1. verify current `main`, its branch/PR, and whether a newer accepted boundary exists;
2. read the Program Charter;
3. read Validation Policy;
4. read its lane state;
5. read Cross-Lane Contracts;
6. inspect the active issue/PR and relevant source/tests;
7. inspect only the merged history needed since the lane ledger boundary;
8. continue the smallest technically prudent active milestone.

Conversational history is supplementary.

## Work discipline

- Prefer one bounded active acceptance target per lane.
- Do not invent work merely because the lane is idle.
- Do not synchronize only because a branch is numerically behind.
- Do not rerun expensive evidence only to refresh a timestamp or SHA.
- Cheap deterministic evidence: broad/exhaustive where practical.
- Expensive runtime/client evidence: representative by risk-equivalence class.
- Sampled failure: widen only the affected class until the failure domain is understood.
- Reuse expensive evidence across orthogonal changes when the tested dependency surface is unchanged.
- Once standalone risk is credibly retired, prefer the next integration risk.
- If independently proved generic fixes are held hostage by an unrelated unresolved gate, checkpoint them cleanly if they form a coherent correctness claim.

## Stop / escalate gates

Stop autonomous extension and surface the gate when progress requires:

- human visual, play, listening or taste judgment;
- an unresolved item in `HUMAN_STRATEGY_ROADMAP.md`;
- changing an accepted cross-lane/product contract;
- credentials/permissions not already granted;
- destructive/external action requiring approval;
- a producer session restart after liveness failure;
- evidence showing no information-bearing next action remains.

Waiting on CI is not work: do not poll repeatedly. Resume when useful evidence exists.

## Durable completion

Before declaring a meaningful milestone complete:

- source/tests/evidence support the claim;
- required human gates are recorded;
- lane state and genuinely changed cross-lane contracts are updated in the milestone PR when practical;
- the PR is mergeable/current enough for the relevant dependency surface;
- deferred work is explicit rather than silently absorbed into the next milestone.

Do not create bookkeeping-only follow-up PRs merely to copy a merge SHA when merged history already establishes the boundary.
