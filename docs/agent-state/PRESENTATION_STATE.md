# Skyforge Presentation lane state

**Canonical lane:** PRESENTATION  
**Updated:** 2026-09-07 (America/Chicago)  
**Status:** PRES-0004 in progress under issue #354  
**Highest merged Presentation milestone:** **PRES-0003**  
**PRES-0003 merge:** PR #351, `aee9508d2648859079c17487505ccd0a6d48b59f`

Always reconstruct current project claims from `main`, the program charter, validation policy, producer-lane ledgers, cross-lane contracts, relevant acceptance records, and merged history before publishing or revising an external-facing artifact.

## Mission and authority

Presentation owns accurate project-level communication and durable showcase packaging. It translates accepted engineering, authorship, content, audio, and program evidence into audience-appropriate explanations without becoming an independent source of technical truth.

Publication categories remain:

- **ACCEPTED / PROVEN** — current capability supported by merged evidence;
- **IN PROGRESS** — active bounded work, explicitly unfinished;
- **ROADMAP / PLANNED** — documented intended work, not current capability;
- **ASPIRATIONAL / STRATEGIC** — conditional longer-range direction.

Presentation does not accept producer milestones, invent world/game/audio semantics, define runtime behavior, or substitute communication polish for missing evidence.

## MERGED / ACCEPTED

### PRES-0001 — Presentation lane bootstrap

PR #344 / `a880f2fb3298b76d5abf601b2163adcbfb3b6706`.

Established Presentation ownership/non-authority, durable repository memory, fresh-agent reconstruction, and publication-state discipline.

### PRES-0002 — current claim registry and audience matrix

PR #350 / `08bc6ff65da5f49f499a3ce16d4fceaf474aa216`.

Accepted:

- `docs/presentation/claims/current-claims.md`;
- `docs/presentation/audiences/current-audience-matrix.md`;
- `docs/presentation/audiences/nontechnical-overview.md`.

Exact-head CI run #1776 passed before merge.

### PRES-0003 — flagship 90-second demo narrative

PR #351 / `aee9508d2648859079c17487505ccd0a6d48b59f`.

Accepted:

- `docs/presentation/demos/flagship-90-second-demo.md` — nontechnical-first shot order, narration, on-screen text, evidence map, accepted capture choreography, optional technical branch, and explicit current-vs-roadmap boundaries.

Exact-head CI run #1781 passed before merge.

Acceptance is the **narrative/evidence architecture**, not a claim that final edited media exists. Issue #348 remains a non-blocking asset-enrichment follow-up for canonical runtime stills. Generated/concept art may never substitute for current-capability runtime evidence.

## IN PROGRESS

### PRES-0004 — flagship portfolio/reviewer package

Issue #354. Branch `presentation/pres-0004-portfolio-package`.

Target one compact reusable package rather than presentation volume.

Primary artifact:

- `docs/presentation/portfolio/skyforge-flagship-project-page.md`

Required content:

- one-sentence project thesis;
- transparent personal role wording for AI-assisted multi-agent development;
- 15-second and 30-second explanations;
- recruiter/resume bullets;
- technical engineering highlights;
- evidence-linked reviewer paths;
- explicit present-vs-roadmap boundary;
- reuse of PRES-0002 claims and PRES-0003 demo story rather than restating producer ledgers.

## OPEN ASSET FOLLOW-UP

### Issue #348 — canonical runtime capture set

Acquire actual accepted Skyforge/Minecraft stills when a cheap valid capture path is available:

- morphology hero/orbit;
- five-family comparison;
- underside/3-D view;
- ecology view;
- persistence/reopen pair where practical.

This is visual enrichment for PRES-0003/PRES-0004, not a blocker on their source/narrative architecture.

## Current communication hazards

- Do not describe Skyforge merely as a Minecraft mod; Minecraft is the first runtime realization of a backend-neutral engine.
- Do not claim a second production backend until one exists and is accepted.
- Do not call authored opportunity/evidence a physical deposit, resource guarantee, species population, settlement, runway, dock, or structure unless the owning lanes accept that mapping.
- Do not imply issue #214 is fully closed.
- Do not present Bootstrap Province as current capability.
- Do not use generated/concept images as runtime evidence.
- In personal-authorship contexts, prefer **“architected and led development of”** over wording that implies every implementation line was manually authored by one developer.
- Do not make Presentation work a blocker on production convergence.

## Recommended next work

Complete PRES-0004's flagship project page and recruiter/interview compression, verify every present-tense statement against `current-claims.md`, then merge the bounded portfolio package. Keep #348 as later visual enrichment unless a cheap canonical capture path appears.
