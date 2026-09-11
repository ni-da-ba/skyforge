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
- Product-convergence sequence: `docs/agent-state/PROGRAM_ROADMAP.md`
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

`PROGRAM_ROADMAP.md` routes already-decided product convergence; it does not override current source/tests, lane ownership, an active bounded issue/PR, or an unresolved human gate. Where the roadmap explicitly records an owner-approved resolution of an older human-strategy sequencing question, do not resurrect that stale question solely because an older strategy entry has not yet been reconciled.

## Lane ownership

- **Authorship** — backend-neutral world meaning, descriptors, provenance, morphology/geology/hydrology/ecology semantics.
- **Implementation** — Minecraft/NeoForge realization, exact-volume runtime, lifecycle, persistence, performance.
- **Content / Experience** — mod integration, progression, gameplay capability, Bootstrap Province, player experience.
- **Music / Audio** — score/audio authorship, source identity, production evidence and listening gates.
- **Presentation** — accurate project communication/showcase packaging grounded in accepted project truth.
- **Audit** — convergence, validation economy, session/process health, orchestration and human-gate escalation.

Do not seize another lane's technical or product authority merely to keep work moving.

## Orchestrator phase routing

The lightweight orchestrator still uses the fast path in `ORCHESTRATION_PROTOCOL.md`: current supervisory state plus relevant PR/issue/Actions movement first.

It must additionally read `PROGRAM_ROADMAP.md` **before** classifying the program as directionless/dormant or escalating merely because no pre-created next issue exists when:

- the current primary milestone/phase has just reached an acceptance boundary;
- a lane ledger names no next task after its accepted boundary;
- the remaining work appears to be only future/speculative work;
- a human-strategy trigger may have been superseded by an explicit owner-approved roadmap resolution;
- multiple technically valid next tasks exist and product-convergence priority is unclear.

Use the roadmap's state/entry/exit/missing-issue rules. Do not fully reread it on every ordinary wake when an active bounded issue/PR already determines the next action.

## Worker reconstruction

A producer starting or resuming bounded work should:

1. verify current `main`, its branch/PR, and whether a newer accepted boundary exists;
2. read the Program Charter;
3. read the Program Roadmap when the task affects phase sequencing, next-work selection, or a cross-phase dependency;
4. read Validation Policy;
5. read its lane state;
6. read Cross-Lane Contracts;
7. inspect the active issue/PR and relevant source/tests;
8. inspect only the merged history needed since the lane ledger boundary;
9. continue the smallest technically prudent active milestone.

Conversational history is supplementary.

## Work discipline

- Prefer one bounded active acceptance target per lane.
- Preserve the Program Roadmap's primary convergence focus when selecting new work, while allowing already-authorized bounded cross-phase work to continue.
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
- an unresolved item in `HUMAN_STRATEGY_ROADMAP.md` whose trigger is active and which is not already explicitly resolved by `PROGRAM_ROADMAP.md`;
- changing an accepted cross-lane/product contract;
- credentials/permissions not already granted;
- destructive/external action requiring approval;
- a producer session restart after liveness failure;
- evidence showing no information-bearing next action remains.

Waiting on CI is not work: do not poll repeatedly. Resume when useful evidence exists.

If a current phase closes and no issue names the next task, use the bounded missing-issue rule in `PROGRAM_ROADMAP.md`; lack of a pre-created issue is not by itself a human gate.

## Durable completion

Before declaring a meaningful milestone complete:

- source/tests/evidence support the claim;
- required human gates are recorded;
- lane state and genuinely changed cross-lane contracts are updated in the milestone PR when practical;
- the PR is mergeable/current enough for the relevant dependency surface;
- deferred work is explicit rather than silently absorbed into the next milestone.

Do not create bookkeeping-only follow-up PRs merely to copy a merge SHA when merged history already establishes the boundary.
