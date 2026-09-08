# Skyforge Presentation lane state

**Canonical lane:** PRESENTATION  
**Updated:** 2026-09-07 (America/Chicago)  
**Status:** lane bootstrap in progress under issue #343  
**Highest merged Presentation milestone:** none yet

Always reconstruct current project claims from `main`, the program charter, producer-lane ledgers, cross-lane contracts, relevant acceptance records, and merged history before publishing or revising an external-facing artifact.

## Mission

Presentation owns the accurate communication of Skyforge as a project.

It translates accepted engineering, authorship, content, audio, and program evidence into audience-appropriate explanations and durable showcase artifacts without becoming an independent source of technical truth.

Primary audiences include:

- recruiters and hiring managers;
- technical interviewers and engineers;
- open-source contributors and procedural-generation practitioners;
- Minecraft players/modpack users where appropriate;
- prospective collaborators, users, customers, or commercial evaluators if the project later reaches those boundaries;
- the project owner, when a concise project-wide explanation or demo narrative is needed.

## Ownership

Presentation owns:

- project-level narrative and terminology for external communication;
- concise capability summaries and claim registries;
- architecture/explanation diagrams whose semantics are traceable to accepted repository evidence;
- demo scripts, showcase sequencing, captions, and reviewer paths;
- portfolio/recruiter/interview descriptions;
- release/showcase communication packages assigned to this lane;
- audience-specific compression of technical material while preserving material caveats;
- freshness review of external-facing project claims when producer milestones materially change what Skyforge can demonstrate.

Presentation does **not** own:

- backend-neutral world meaning (Authorship);
- Minecraft/runtime correctness or physical realization (Implementation);
- gameplay/progression/content meaning (Content / Experience);
- soundtrack/audio authorship or source identity (Music / Audio);
- producer acceptance, validation policy, or repository-wide supervisory judgment (Audit / Program Health).

Presentation may identify ambiguity, stale prose, missing evidence, or a communication gap and hand that problem to the owning lane. It may not repair the gap by inventing semantics or promoting an unaccepted result.

## Publication contract

Every substantive external claim must be classifiable as one of:

- **ACCEPTED / PROVEN** — directly supported by current merged repository evidence;
- **IN PROGRESS** — active work with an explicit issue/milestone/branch/PR boundary;
- **ROADMAP / PLANNED** — documented intended work that is not yet accepted;
- **ASPIRATIONAL / STRATEGIC** — longer-range possibility or product direction not guaranteed by the current roadmap.

Presentation must not collapse these categories in prose, diagrams, demos, resumes, portfolio material, release notes, or commercial discussion.

When a simplified artifact omits implementation detail, it must preserve the claim boundary. For example, "backend-neutral architecture" is currently proven; "multiple production backends" is not yet proven.

## Durable artifact architecture

Canonical Presentation artifacts live under `docs/presentation/`.

```text
docs/agent-state/PRESENTATION_STATE.md
    -> concise live lane ledger and next work

docs/presentation/
    -> durable presentation policy and audience artifacts

docs/showcase/
    -> existing generated/technical showcase material owned by its producing context

docs/reviews/, docs/authorship/, docs/architecture/, source/tests/PRs
    -> evidence sources; Presentation consumes but does not supersede them
```

Presentation should prefer links/pointers to authoritative evidence rather than duplicating detailed milestone measurements into its state ledger.

## Fresh-session reconstruction order

1. `docs/agent-state/PROGRAM_CHARTER.md`
2. `docs/agent-state/VALIDATION_POLICY.md`
3. `docs/agent-state/HUMAN_STRATEGY_ROADMAP.md`
4. `docs/agent-state/PRESENTATION_STATE.md`
5. `docs/agent-state/CROSS_LANE_CONTRACTS.md`
6. current producer-lane state files relevant to the artifact being produced
7. current `README.md`, relevant architecture/review/showcase docs, source/tests
8. recent merged PRs/commits since the Presentation ledger was last updated

Repository state and accepted evidence outrank existing presentation prose.

## Milestones

### PRES-0001 — Presentation lane bootstrap — IN PROGRESS

Issue #343.

Acceptance target:

- establish this canonical lane ledger;
- add Presentation to the program charter and fresh-agent read order;
- persist the publication/authority boundary;
- establish `docs/presentation/` as the durable artifact home;
- seed the first bounded Presentation work queue.

No technical capability claim is added by PRES-0001.

### PRES-0002 — current claim registry and audience matrix — PROPOSED

Create a concise project-wide register of what Skyforge can currently claim, with source pointers and audience-specific phrasing. At minimum distinguish:

- 30-second project explanation;
- recruiter/hiring-manager summary;
- technical-engineering summary;
- procedural-generation specialist summary;
- Minecraft/player-facing summary;
- roadmap/future wording that cannot be presented as current capability.

The registry should be maintained for claim freshness, not become a duplicate technical ledger.

### PRES-0003 — flagship technical demo narrative — PROPOSED

Define the smallest demonstrable sequence that lets a reviewer understand why Skyforge is technically significant. Target a roughly 90-second core story with optional deeper branches. The demo should connect semantic intent, deterministic procedural graphs, morphology/volumetric ownership, Minecraft realization, and evidence/reproducibility using only accepted artifacts.

### PRES-0004 — portfolio/showcase package — PROPOSED

Produce a durable portfolio-facing package after PRES-0002/0003 establish the claim and demo boundaries. Likely components include architecture graphics, selected current-world visuals, concise engineering bullets, reviewer path, and explicit current limitations.

Do not build a slide deck, marketing site, or commercial pitch merely because the lane exists; create those only for a concrete audience/use case.

## Current accepted project boundary relevant to Presentation

At lane bootstrap, the root README reports:

- backend-neutral deterministic world synthesis with Minecraft 1.21.1/NeoForge as the first runtime backend;
- immutable typed procedural graphs, deterministic reference evaluation, exact finite suspended volumes, five primary morphology families, and higher-order authored composition machinery;
- accepted Minecraft lifecycle through SF-IMP-0082, including exact-volume realization, stacked-volume isolation, biome/surface population, structure admission/support, deferred realization, native+authored cave composition, interior population, fluid provenance/fencing, persistence/reopen evidence, and all five SMALL/seed-skyforge morphology carriers;
- Authorship accepted through at least AUTH-0096 in the README snapshot, with newer lane/cross-lane state potentially ahead of that prose;
- Content / Experience and Music / Audio have separate accepted boundaries;
- Skyforge remains pre-release and does not promise a stable public API.

Fresh work must verify those statements against current lane ledgers and merged history because parallel lanes advance independently.

## Known communication hazards

- Do not describe Skyforge merely as a Minecraft mod; Minecraft is the first runtime realization of a backend-neutral engine.
- Do not claim a second production backend until one exists and is accepted.
- Do not call roadmap semantics physically realized in Minecraft before Implementation accepts them.
- Do not equate authored opportunity/evidence with gameplay guarantees, resource deposits, structures, or named roles unless the owning lanes have accepted those mappings.
- Do not describe human visual acceptance as a numerical proof, or machine evidence as a substitute for a required human visual/listening gate.
- Do not inflate project scale by listing tests/metrics without explaining the engineering risk they retire.
- Do not let presentation work become a reason to stall production convergence.

## Recommended next work

After PRES-0001 lands, proceed with **PRES-0002**: build the current claim registry and audience matrix from current `main`, prioritizing a recruiter/technical-reviewer explanation of Skyforge's already accepted engineering boundary.