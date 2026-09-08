# Skyforge Presentation lane state

**Canonical lane:** PRESENTATION  
**Updated:** 2026-09-07 (America/Chicago)  
**Status:** PRES-0003 in progress under issue #347  
**Highest merged Presentation milestone:** **PRES-0002**  
**PRES-0002 merge:** PR #350, `08bc6ff65da5f49f499a3ce16d4fceaf474aa216`

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

PR #344 / `a880f2fb3298b76d5abf601b2163adcbfb3b6706`.

Established Presentation ownership/non-authority, durable repository memory, fresh-agent reconstruction, and publication-state discipline.

### PRES-0002 — current claim registry and audience matrix

PR #350 / `08bc6ff65da5f49f499a3ce16d4fceaf474aa216`.

Accepted artifacts:

- `docs/presentation/claims/current-claims.md` — evidence-linked present/in-progress/roadmap/aspirational claim surface plus explicit “must not claim yet” boundaries;
- `docs/presentation/audiences/current-audience-matrix.md` — nontechnical, recruiter, technical-engineer, procedural-generation, Minecraft/player, contributor, and collaborator/commercial compression guidance;
- `docs/presentation/audiences/nontechnical-overview.md` — reusable “Skyforge at a Glance” general-audience narrative.

Exact-head CI run #1776 passed before merge. PRES-0002 creates no producer technical claim or cross-lane contract.

## IN PROGRESS

### PRES-0003 — flagship 90-second demo narrative

Issue #347. Branch `presentation/pres-0003-flagship-demo`.

Current artifact:

- `docs/presentation/demos/flagship-90-second-demo.md` — exact ~90-second shot/narration/on-screen-text/evidence map for a nontechnical-first demo.

Core sequence:

```text
meaning
-> five recognizable landform identities
-> finite 3-D volume / underside
-> Minecraft realization
-> native ecology/lifecycle
-> deterministic persistence/evidence
-> clearly labeled Bootstrap Province direction
```

The storyboard consumes the PRES-0002 claim registry and the accepted SF-IMP-0080/0081/0082 viewer choreography. Issue #348 tracks canonical still acquisition.

Inspection of the retained SF-IMP-0082 Showcase Acceptance artifacts found acceptance properties/logs but no image captures, so Presentation must not substitute generated or fabricated runtime visuals. Reuse existing accepted viewer worlds/commands or the cheapest valid capture path rather than rerunning the expensive morphology matrix solely for screenshots.

## NEXT

### PRES-0004 — portfolio/showcase package

Follow PRES-0003 once the flagship story has canonical runtime images. Prefer reusable high-signal artifacts over presentation volume.

## Current communication hazards

- Do not describe Skyforge merely as a Minecraft mod; Minecraft is the first runtime realization of a backend-neutral engine.
- Do not claim a second production backend until one exists and is accepted.
- Do not call authored opportunity/evidence a physical deposit, resource guarantee, species population, settlement, runway, dock, or structure unless the owning lanes accept that mapping.
- Do not imply issue #214 is fully closed: all five SMALL / `seed-skyforge` families are accepted, but broader seed/scale/hybrid/regional review remains open.
- Do not present Bootstrap Province as current capability.
- Do not use generated/concept images as runtime evidence.
- Do not make Presentation work a blocker on production convergence.

## Recommended next work

Complete PRES-0003 by acquiring the small canonical capture set from issue #348, insert those visuals into the flagship storyboard/demo package, verify every frame against `current-claims.md`, then merge the bounded narrative package before beginning PRES-0004.