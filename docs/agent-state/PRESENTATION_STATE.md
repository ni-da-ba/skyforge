# Skyforge Presentation lane state

**Canonical lane:** PRESENTATION  
**Updated:** 2026-09-07 (America/Chicago)  
**Status:** PRES-0002 ready for acceptance under issue #345  
**Highest merged Presentation milestone:** **PRES-0001**  
**PRES-0001 merge:** PR #344, `a880f2fb3298b76d5abf601b2163adcbfb3b6706`

Always reconstruct current project claims from `main`, the program charter, validation policy, producer-lane ledgers, cross-lane contracts, relevant acceptance records, and merged history before publishing or revising an external-facing artifact.

## Mission and authority

Presentation owns accurate project-level communication and durable showcase packaging. It translates accepted engineering, authorship, content, audio, and program evidence into audience-appropriate explanations without becoming an independent source of technical truth.

Presentation may compress detail but may not collapse these publication states:

- **ACCEPTED / PROVEN** — current capability supported by merged evidence;
- **IN PROGRESS** — active bounded work, explicitly unfinished;
- **ROADMAP / PLANNED** — documented intended work, not current capability;
- **ASPIRATIONAL / STRATEGIC** — conditional longer-range direction.

Presentation does not accept producer milestones, invent world/game/audio semantics, define runtime behavior, or substitute communication polish for missing evidence.

## Durable artifacts

Canonical Presentation artifacts live under `docs/presentation/`:

- `claims/` — current externally usable capability/limitation register;
- `audiences/` — audience-specific compression and reusable explanations;
- `demos/` — demo scripts, shot lists, narration, and evidence mapping;
- `diagrams/` — explanatory diagrams traceable to accepted semantics;
- `portfolio/` — portfolio/recruiter/interview reviewer paths when needed.

Existing producer-owned technical showcase records remain under `docs/showcase/`, `docs/reviews/`, `docs/authorship/`, source/tests, and merged PR history. Presentation consumes but does not supersede them.

## Fresh-session reconstruction order

1. `docs/agent-state/PROGRAM_CHARTER.md`
2. `docs/agent-state/VALIDATION_POLICY.md`
3. `docs/agent-state/HUMAN_STRATEGY_ROADMAP.md`
4. `docs/agent-state/PRESENTATION_STATE.md`
5. `docs/agent-state/CROSS_LANE_CONTRACTS.md`
6. relevant current producer-lane ledgers
7. `README.md` plus relevant architecture/review/showcase/source/tests
8. recent merged PRs/commits since the Presentation ledger was last updated

Repository state and accepted evidence outrank existing presentation prose.

## MERGED / ACCEPTED

### PRES-0001 — Presentation lane bootstrap

PR #344 merged as `a880f2fb3298b76d5abf601b2163adcbfb3b6706`.

Accepted boundary:

- canonical Presentation lane and persistence architecture;
- explicit ownership/non-authority in the program charter;
- fresh-agent reconstruction path;
- durable `docs/presentation/` artifact home;
- accepted/in-progress/roadmap/aspirational claim discipline.

## READY FOR ACCEPTANCE

### PRES-0002 — current claim registry and audience matrix

Issue #345. Branch `presentation/pres-0002-claim-registry`.

Candidate artifacts:

- `docs/presentation/claims/current-claims.md` — evidence-linked present/in-progress/roadmap/aspirational claim surface plus explicit “must not claim yet” boundaries;
- `docs/presentation/audiences/current-audience-matrix.md` — nontechnical, recruiter, technical-engineer, procedural-generation, Minecraft/player, contributor, and commercial/collaborator compression guidance;
- `docs/presentation/audiences/nontechnical-overview.md` — reusable “Skyforge at a Glance” general-audience narrative.

Acceptance intent: establish one stable claim surface that later demos/portfolio artifacts can consume without duplicating producer technical ledgers.

## NEXT

### PRES-0003 — flagship 90-second demo narrative

Issue #347 is open. Define the smallest show-don’t-tell sequence that makes a nontechnical reviewer understand why Skyforge is significant:

```text
meaning
-> five recognizable landform identities
-> finite 3-D volume / underside
-> Minecraft realization
-> native ecology/lifecycle
-> deterministic persistence/evidence
-> clearly labeled Bootstrap Province direction
```

Issue #348 tracks acquisition/reuse of canonical accepted visuals: Massif hero/orbit, five-family comparison, underside view, ecology view, and a persistence/reopen pair where cheap existing fixtures can supply them.

### PRES-0004 — portfolio/showcase package

Follow PRES-0003 once the claim surface and flagship story are stable. Do not create presentation volume for its own sake; prefer a small set of reusable high-signal artifacts.

## Current communication hazards

- Do not describe Skyforge merely as a Minecraft mod; Minecraft is the first runtime realization of a backend-neutral engine.
- Do not claim a second production backend until one exists and is accepted.
- Do not call authored opportunity/evidence a physical deposit, resource guarantee, species population, settlement, runway, dock, or structure unless the owning lanes accept that mapping.
- Do not imply issue #214 is fully closed: all five SMALL / `seed-skyforge` families are accepted, but broader seed/scale/hybrid/regional review remains open.
- Do not present Bootstrap Province as current capability.
- Do not inflate scale with raw test/metric counts when the audience needs the engineering consequence instead.
- Do not make Presentation work a blocker on production convergence.

## Recommended next work

Merge PRES-0002 after its documentation-only exact-head gate passes, then create PRES-0003 from current `main` and build the 90-second storyboard using the accepted morphology/ecology viewer choreography and the canonical claim registry.