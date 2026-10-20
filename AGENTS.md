# Skyforge agent map

Skyforge uses **repository-first, disposable agents**.

```text
conversation/thread = execution workspace
GitHub              = durable memory
current main/tests  = authority
```

This file is a map, not the full manual. Read deeper documents only when the current task requires them.

## Canonical sources

- Compact bootstrap snapshot: `docs/agent-state/CURRENT_PROJECT_STATE.md`
- Program rules: `docs/agent-state/PROGRAM_CHARTER.md`
- Product-convergence sequence: `docs/agent-state/PROGRAM_ROADMAP.md`
- Validation/evidence economy: `docs/agent-state/VALIDATION_POLICY.md`
- Cross-lane invariants: `docs/agent-state/CROSS_LANE_CONTRACTS.md`
- Human product decisions/triggers: `docs/agent-state/HUMAN_STRATEGY_ROADMAP.md`
- Lane state:
  - Authorship: `docs/agent-state/AUTHORSHIP_STATE.md`
  - Implementation: `docs/agent-state/IMPLEMENTATION_STATE.md`
  - Content: `docs/agent-state/CONTENT_STATE.md`
  - Music/Audio: `docs/agent-state/MUSIC_STATE.md`
  - Presentation: `docs/agent-state/PRESENTATION_STATE.md`

`CURRENT_PROJECT_STATE.md` is a compact context cache, not an authority override. Read it early, then verify its snapshot boundary against current `main`, active issue/PR authority, tests, and merged history before relying on it. Update it in the same bounded tranche when a human/product boundary or primary next-work boundary materially changes.

If summaries disagree with `main`, source/tests, or merged history, the repository evidence wins.

`PROGRAM_ROADMAP.md` routes already-decided product convergence; it does not override current source/tests, lane ownership, an active bounded issue/PR, or an unresolved human gate. Where the roadmap explicitly records an owner-approved resolution of an older human-strategy sequencing question, do not resurrect that stale question solely because an older strategy entry has not yet been reconciled.

## Validation authority

```text
GitHub / Actions = project source + automated machine validation
Nicholas local PC = manual/interactive/human verification
```

Use GitHub Actions for repeatable builds, tests, and other machine evidence. Reserve the local workstation for owner play, visual review, and other manual judgment. Keep a required gate pending when its authorized evidence path is unavailable.

## Lane ownership

- **Authorship** — backend-neutral world meaning, descriptors, provenance, morphology/geology/hydrology/ecology semantics.
- **Implementation** — Minecraft/NeoForge realization, exact-volume runtime, lifecycle, persistence, performance.
- **Content / Experience** — mod integration, progression, gameplay capability, Bootstrap Province, player experience.
- **Music / Audio** — score/audio authorship, source identity, production evidence and listening gates.
- **Presentation** — accurate project communication/showcase packaging grounded in accepted project truth.
- **Audit** — convergence, validation economy, and human-gate escalation.

Do not seize another lane's technical or product authority merely to keep work moving.

## Worker reconstruction

A producer starting or resuming bounded work should:

0. read `CURRENT_PROJECT_STATE.md`, verify its snapshot boundary against current `main` and active issue/PR ownership before selecting work;
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
- `CURRENT_PROJECT_STATE.md` is refreshed when the primary human/product boundary or recommended next tranche materially changed;
- the PR is mergeable/current enough for the relevant dependency surface;
- deferred work is explicit rather than silently absorbed into the next milestone.

Do not create bookkeeping-only follow-up PRs merely to copy a merge SHA when merged history already establishes the boundary.
