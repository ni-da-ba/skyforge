# AUTH-0041 — Backend-Neutral Compatible-Candidate Ranking Semantics

AUTH-0041 defines deterministic semantic ranking among material capability profiles that already pass AUTH-0040 hard compatibility.

It does not select or store a concrete backend material.

No Minecraft block id, registry key, resource location, tag, named rock, mineral species, or backend candidate identity enters the authored world model.

## Functional position

The material pipeline is now:

~~~text
AUTH-0036 compositional lithologic realization
        ↓
AUTH-0037 semantic role eligibility + local expression limits
        ↓
AUTH-0038 stable coherence/binding key
        ↓
AUTH-0039 stable backend-neutral binding request
        ↓
AUTH-0040 hard capability compatibility
        ↓
AUTH-0041 semantic ranking among compatible capability profiles
        ↓
future backend-owned identity tie-break / selection
        ↓
concrete backend material binding
~~~

AUTH-0040 answers:

> May this candidate satisfy the request at all?

AUTH-0041 answers:

> Among compatible semantic profiles, which profiles are preferred?

## Compatibility remains absolute

AUTH-0041 never ranks an AUTH-0040-incompatible profile.

rankCompatible filters incompatible profiles before producing any rank.

Calling rank directly on an incompatible profile is an invariant violation.

Ranking can therefore never weaken AUTH-0040 hard floors.

## Ranking vector

Every compatible profile receives four ordered ranking components.

Higher values are preferred in this exact order:

1. minimumRequiredHeadroom;
2. meanRequiredHeadroom;
3. specializationPurity;
4. requestAffinity.

The ordering is lexicographic rather than a hidden weighted sum.

This keeps ranking behavior directly inspectable.

## Required capability headroom

For each AUTH-0040 hard requirement:

~~~text
headroom = (advertised - minimum) / (1 - minimum)
~~~

clamped to [0, 1].

minimumRequiredHeadroom is the weakest normalized margin across all required capabilities.

It is the first ranking dimension.

This prevents a multi-capability candidate from compensating for one barely adequate required capability by being extremely strong in another.

meanRequiredHeadroom is the second dimension and prefers stronger overall relevant capability after the weakest requirement is protected.

## Specialization purity

After required semantic capability quality is compared, AUTH-0041 prefers profiles that devote less capability mass to functions unrelated to the current request.

~~~text
specializationPurity = 1 - mean(unrequired capability values)
~~~

This is a preference, not an eligibility rule.

A broad generalist remains compatible and can still win if its required capability strength is genuinely superior.

Specialization cannot make an incompatible profile eligible.

## Request-scoped deterministic affinity

When two profiles are otherwise equal on required headroom and specialization purity, AUTH-0041 uses requestAffinity as the final semantic tie-break.

requestAffinity is deterministically derived from:

- the AUTH-0038/AUTH-0039 canonical binding token;
- the complete backend-neutral capability profile.

It does not use:

- sample position;
- chunk position;
- traversal order;
- candidate encounter order;
- runtime randomness;
- backend material identity.

Therefore one stable material-binding request always sees the same semantic preference ordering.

Different stable requests may prefer different otherwise-equivalent semantic profiles, providing controlled variation without voxel-scale noise.

## Exact semantic ties

Two exactly identical capability profiles receive exactly the same rank for one request.

AUTH-0041 deliberately does not invent a fake distinction.

If two distinct backend materials advertise identical semantic profiles, the adapter must use an adapter-owned stable candidate identity to break the final tie.

That identity must not depend on encounter order.

The backend identity remains outside the Skyforge world model.

## Candidate order independence

rankCompatible sorts by the AUTH-0041 semantic rank.

Reversing or permuting input candidate order therefore cannot alter the resulting semantic ordering except for exact semantic ties, which are intentionally unresolved inside the world layer.

## Evidence

The authorship-material-candidate-ranking-v1 corpus uses the canonical six representatives at semantic depth 0.52.

Six backend-neutral diagnostic capability profiles participate:

- MATRIX;
- FABRIC;
- ALTERATION;
- WATER;
- ACCENT;
- GENERALIST.

These are evidence probes, not concrete materials.

Every panel shows where that diagnostic profile wins local AUTH-0039 requests after AUTH-0040 filtering and AUTH-0041 ranking.

Expected behavior:

- MATRIX wins massive host-matrix requests;
- FABRIC wins fabric-rich host-matrix requests;
- ALTERATION wins alteration requests;
- WATER wins hydrologic requests;
- ACCENT wins mineral-bearing structural requests;
- GENERALIST remains compatible but does not erase specialist distinctions in this diagnostic set.

The visual gate rejects ranking that collapses all authored functions into one generalist winner or produces sample-scale checkerboard winner noise.

## Acceptance gate

Reject AUTH-0041 if:

- an incompatible profile receives a rank;
- ranking can weaken AUTH-0040 compatibility;
- candidate encounter order changes semantic ranking;
- local sample/chunk coordinates enter the rank;
- backend material identity enters the world-layer rank;
- weakest required capability can be hidden by strength in another capability;
- specialization can override a stronger earlier ranking dimension;
- request affinity precedes substantive semantic ranking criteria;
- one stable request/profile pair yields different affinity;
- exact identical profiles receive different ranks;
- visual evidence shows generalist collapse or incoherent sample-scale winner noise.

## Parallel implementation boundary

AUTH-0041 changes no cave, carver, persistence, mutation, Minecraft material, or backend registry behavior.

The implementation lane should not construct backend candidate identities inside the world model.

## Next milestone

If AUTH-0041 is accepted, the next authorship milestone should define a **backend-neutral material resolution decision contract** that packages:

- the stable AUTH-0039 request;
- AUTH-0040 compatibility evidence;
- AUTH-0041 semantic rank;
- deterministic final-selection provenance supplied by a backend resolver.

The world model should still not own concrete backend identities. The contract should make the boundary between semantic ranking and backend-owned concrete selection explicit before any Minecraft palette implementation lands.
