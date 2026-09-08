# Skyforge Presentation lane state

**Canonical lane:** PRESENTATION  
**Updated:** 2026-09-07 (America/Chicago)  
**Status:** PRES-0005 explanatory graphics suite in progress under issue #383  
**Highest merged Presentation milestone:** **PRES-0004**  
**PRES-0004 merge:** PR #355, `993fba2a7da0725b1f64571bf17b25a9c0525204`

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

### PRES-0004 — flagship portfolio/reviewer package

PR #355 / `993fba2a7da0725b1f64571bf17b25a9c0525204`.

Accepted:

- `docs/presentation/portfolio/skyforge-flagship-project-page.md` — compact flagship project page with recruiter/reviewer wording, transparent AI-assisted development role framing, evidence-linked technical highlights, and explicit present-vs-roadmap boundaries.

## RUNTIME CAPTURE ACQUISITION

### Issue #348 — canonical runtime capture set

PR #362 adds the durable manual capture path and records the resulting source-set boundary.

Validated branch capture run #2 / Actions run 34185345915 produced:

- five accepted morphology families × four actual-client views;
- three accepted ecology views;
- **23 non-empty Minecraft PNGs total**.

The durable source and limitations are recorded in:

- `docs/presentation/demos/runtime-capture-set.md`.

The remaining caveat is compositional, not evidentiary: some engineering-viewer camera rotations are not guaranteed to synchronize to the actual client, so human-eye selection should use the strongest source frame rather than treating all guided stops as publication-ready photography.

A rigorously matched pre-save/post-reopen image pair remains unavailable; use a reopened-world still together with accepted persistence/digest evidence instead of implying a visual proof the workflow does not provide.

## Current communication hazards

- Do not describe Skyforge merely as a Minecraft mod; Minecraft is the first runtime realization of a backend-neutral engine.
- Do not claim a second production backend until one exists and is accepted.
- Do not call authored opportunity/evidence a physical deposit, resource guarantee, species population, settlement, runway, dock, or structure unless the owning lanes accept that mapping.
- Do not imply issue #214 is fully closed.
- Do not present Bootstrap Province as current capability.
- Do not use generated/concept images as runtime evidence.
- In personal-authorship contexts, prefer **“architected and led development of”** over wording that implies every implementation line was manually authored by one developer.
- Do not make Presentation work a blocker on production convergence.

## IN PROGRESS

### PRES-0005 — explanatory graphics suite

Issue #383. Branch `presentation/pres-0005-explanatory-graphics`.

The first five exact source diagrams and the controlled rendering guide are now present under `docs/presentation/diagrams/`:

- `how-skyforge-works.mmd`;
- `authority-layers.mmd`;
- `volume-not-heightmap.mmd`;
- `evidence-architecture.mmd`;
- `current-vs-roadmap.mmd`;\n- `PRES-0005-rendering-guide.md`.

This milestone intentionally does not depend on production-quality runtime screenshots. It exists to communicate Skyforge's architecture, authority model, volumetric ownership, validation philosophy, and current-vs-roadmap boundary using claim-controlled vector-first graphics.

The deterministic five-panel infographic candidate has also been rendered outside the repository from these controlled sources. Generated island artwork is used only as an explicitly labeled explanatory illustration; all factual copy is repository-controlled. The remaining acceptance gate is human-eye communication quality of that rendered set. That gate may approve the visual language, request bounded restyling, or identify one diagram that is not legible enough. It does not reopen technical producer evidence.

## Recommended next work

Take the first five rendered PRES-0005 graphics through the bounded human-eye presentation gate. If the visual language is accepted, persist any final rendering/style guidance, take PRES-0005 through exact-head CI, and merge. Afterward, use these diagrams as the stable explanatory layer while runtime screenshots remain maturity-dependent.
