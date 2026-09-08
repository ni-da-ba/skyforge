# Skyforge Presentation Artifacts

This directory is the durable home for project-level communication artifacts produced by the Presentation lane.

Presentation material explains accepted Skyforge work to a specific audience. It does not replace the technical authorities from which its claims are derived.

## Authority

Use this order when resolving a claim:

```text
current source/tests + merged git history
    > producer-lane acceptance/state + cross-lane contracts
    > architecture/review/evidence documents
    > Presentation artifacts
    > conversational recollection
```

If a Presentation artifact conflicts with newer accepted evidence, the artifact is stale and must be revised or retired.

## Artifact classes

Recommended durable artifacts include:

- `claims/` — externally usable capability/limitation registers with evidence pointers;
- `audiences/` — audience-specific summaries such as recruiter, technical reviewer, contributor, or player-facing explanations;
- `demos/` — demo scripts, shot/sequence plans, narration, and evidence mapping;
- `diagrams/` — source-controlled explanatory diagrams and their semantic notes;
- `portfolio/` — portfolio-facing project summaries and reviewer paths;
- `releases/` — presentation packages tied to an actual release/showcase boundary when assigned;
- `strategy/` — communication/product-positioning notes only when they are explicitly useful and clearly separated from accepted capability claims.

Directories should be created when an artifact actually exists; empty taxonomy directories are unnecessary.

## Claim discipline

Every substantive statement should remain traceable to one of these categories:

| Category | Meaning | External use |
| --- | --- | --- |
| ACCEPTED / PROVEN | Merged and supported by required evidence | May be stated as current capability |
| IN PROGRESS | Active bounded work | Must be described as unfinished |
| ROADMAP / PLANNED | Documented intended work | May be described as planned, never current |
| ASPIRATIONAL / STRATEGIC | Longer-range possibility | Must be presented as conditional or exploratory |

A good presentation compresses detail; it does not compress away the distinction between those states.

## Evidence pointers

Prefer links to authoritative repository locations over copying large quantities of measurements or implementation history into presentation prose. A useful claim record generally needs:

- the claim;
- its status category;
- the owning lane(s);
- one or more durable evidence pointers;
- any material caveat that changes what a reasonable reader would infer;
- the date or project boundary at which freshness was checked.

## Update triggers

Presentation artifacts should be reviewed when:

- a producer lane accepts a milestone that materially changes an external claim;
- a claim is weakened, superseded, or reopened;
- a new human visual/listening gate changes what may be shown or said;
- a release, portfolio review, interview, public post, or demo creates a concrete audience need;
- Audit or a producer lane flags stale or misleading presentation prose.

Do not update artifacts for every internal milestone. Presentation maintenance should follow meaningful communication changes, not mirror repository churn.

## Scope economy

The lane exists to make Skyforge understandable, not to generate promotional volume.

Prefer one strong, evidence-grounded explanation or demo over multiple redundant decks, screenshots, or summaries. Presentation work must not become a blocker on world-realization convergence.