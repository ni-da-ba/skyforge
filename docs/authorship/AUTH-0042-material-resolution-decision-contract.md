# AUTH-0042 — Backend-Neutral Material Resolution Decision Contract

AUTH-0042 defines the auditable boundary between Skyforge semantic ranking and a backend-owned concrete material selection.

It does not store or choose concrete backend material identity.

## Functional position

~~~text
AUTH-0039 stable material request
        ↓
AUTH-0040 hard compatibility
        ↓
AUTH-0041 semantic ranking
        ↓
AUTH-0042 resolution frontier + decision provenance
        ↓
backend-owned concrete candidate identity
        ↓
concrete material binding
~~~

AUTH-0042 answers:

> Was the semantic decision already decisive, or did the backend need a stable identity tie-break among semantically tied candidates?

## Resolution frontier

SkyIslandMaterialResolutionFrontier retains:

- the stable AUTH-0039 request;
- every AUTH-0040-compatible AUTH-0041 rank;
- candidate multiplicity;
- best-first semantic ordering.

Candidate multiplicity is deliberate.

Two distinct backend materials may advertise exactly the same semantic capability profile.

Skyforge must not invent a semantic distinction between them.

The frontier therefore preserves duplicate profiles as duplicate semantic ranks without storing either material identity.

## Decision envelope

SkyIslandMaterialResolutionDecision retains:

- resolution frontier;
- selected semantic capability profile;
- exact AUTH-0040 compatibility assessment;
- exact AUTH-0041 rank;
- final-selection provenance method.

The decision validates that:

- the selected profile occurs in the compatible frontier;
- compatibility evidence exactly matches AUTH-0040;
- rank exactly matches AUTH-0041;
- selected rank lies on the top semantic frontier.

A lower-ranked compatible profile cannot be presented as selected.

## Selection provenance

Two methods are allowed.

### SEMANTIC_RANK_WINNER

Used only when exactly one candidate lies on the top semantic frontier.

AUTH-0041 is sufficient to make the semantic choice decisive.

### BACKEND_STABLE_IDENTITY_TIE_BREAK

Required when more than one compatible candidate lies on the exact top semantic frontier.

The backend must have used an adapter-owned stable candidate identity to choose the concrete material.

Skyforge records that this tie-break occurred but does not store the identity itself.

This is the explicit architectural boundary:

~~~text
Skyforge knows:
- stable request;
- compatible semantic candidates;
- semantic ranks;
- top tie multiplicity;
- selected semantic profile;
- whether backend identity tie-break was required.

Backend alone knows:
- actual candidate identities;
- stable identity ordering/token;
- selected concrete material.
~~~

## Why duplicate semantic profiles are preserved

If two backend candidates expose the same capability profile, deduplicating them would incorrectly turn a backend tie into a unique semantic winner.

AUTH-0042 therefore preserves candidate multiplicity.

A backend must supply one semantic profile per actual candidate.

The world model cannot and should not verify whether the backend supplied the same concrete candidate twice.

## Order independence

Candidate encounter order cannot change the semantic frontier or decision.

AUTH-0041 ranking remains the ordering authority.

Exact semantic ties remain exact ties.

The backend identity tie-break must itself be stable and encounter-order independent, but that backend identity policy remains outside the world model.

## Evidence

The authorship-material-resolution-decision-v1 corpus uses the canonical six representatives at semantic depth 0.52.

The diagnostic candidate pool intentionally contains duplicate semantic profiles for:

- ALTERATION;
- ACCENT.

This proves that AUTH-0042 can expose a real backend identity boundary without importing material ids.

Panels:

- SELECTED;
- SEMANTIC WINNER;
- BACKEND TIE-BREAK;
- COMPATIBLE COUNT;
- TOP TIE COUNT;
- MIN HEADROOM.

Expected behavior:

- matrix/fabric/water requests are semantically decisive in the diagnostic pool;
- alteration and mineral-bearing structural requests require backend stable identity tie-breaks because the top semantic profile occurs twice;
- the selected semantic profile still follows AUTH-0041 specialist ranking;
- every repeated stable request produces one identical AUTH-0042 decision envelope;
- unstableRequests remains zero.

The evidence does not pretend to identify which duplicate concrete material the backend chose.

## Acceptance gate

Reject AUTH-0042 if:

- an incompatible profile appears in the frontier;
- a lower-ranked compatible profile can be selected;
- selected compatibility differs from AUTH-0040;
- selected rank differs from AUTH-0041;
- duplicate top semantic profiles are silently deduplicated;
- a semantic tie can be labeled SEMANTIC_RANK_WINNER;
- a unique semantic winner can be labeled BACKEND_STABLE_IDENTITY_TIE_BREAK;
- backend material identity enters the world model;
- candidate encounter order changes the decision;
- the same stable request produces different decision envelopes;
- evidence reports unstable requests;
- backend tie-break evidence is absent despite deliberate duplicate top profiles.

## Parallel implementation boundary

AUTH-0042 changes no cave, carver, persistence, mutation, Minecraft block, or registry behavior.

It creates the contract a future backend material resolver can satisfy without requiring the Skyforge world model to know concrete backend identities.

## Next milestone

If AUTH-0042 is accepted, the next authorship milestone should define **backend-neutral material realization allocation semantics**.

That layer should use:

- resolved material-binding identity supplied by the backend;
- AUTH-0037 local support and expression ceiling;
- stable AUTH-0038 coherence;
- AUTH-0039 role semantics;

to determine where primary, secondary, alteration, hydrologic, and structural-accent bindings are allowed to express spatially.

The world layer should still describe allocation semantics without storing Minecraft blocks.
