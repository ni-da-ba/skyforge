# AUTH-0044 — Deterministic Discrete Semantic Material Realization

AUTH-0044 converts AUTH-0043 continuous material-expression allocations into one deterministic backend-neutral semantic material winner at one exact authored point.

No Minecraft block id, registry key, resource location, named rock, mineral species, or concrete backend material identity enters the world model.

## Functional position

~~~text
AUTH-0042 resolution decision provenance
        ↓
AUTH-0043 continuous structural shares + conditioned claims
        ↓
AUTH-0044 deterministic discrete semantic winner
        ↓
future backend mapping:
stable binding key → concrete material
~~~

AUTH-0043 answers:

> How much structural share or conditioned expression is available here?

AUTH-0044 answers:

> Which stable semantic binding wins this exact point?

## Smooth deterministic spatial selector

AUTH-0044 introduces a stateless smooth 3D lattice field.

The field is keyed by:

- the stable AUTH-0038 binding key;
- island-local x;
- island-local z;
- semantic depth fraction.

It does not use:

- chunk coordinates;
- sample traversal order;
- candidate encounter order;
- runtime randomness;
- backend material identity.

The field interpolates deterministic lattice values with a cubic smoothstep.

Nearby points therefore receive nearby selector values within one binding domain.

This avoids direct per-point hash noise and the resulting checkerboard appearance.

## Structural matrix realization

AUTH-0043 guarantees:

~~~text
primaryShare + secondaryShare = 1
~~~

If SECONDARY_MATRIX is absent, PRIMARY_MATRIX wins structurally.

If SECONDARY_MATRIX is present:

~~~text
secondary wins when spatialField(secondaryBinding, position) < secondaryShare
otherwise primary wins
~~~

PRIMARY_MATRIX remains the residual structural host.

## Conditioned realization

Each conditioned allocation remains independent.

For each alteration, hydrologic, or mineral-bearing allocation:

~~~text
active when spatialField(binding, position) < conditionedClaim
~~~

This preserves AUTH-0043's refusal to normalize unrelated conditioned systems prematurely.

## Conditioned conflict resolution

More than one conditioned claim may be active at one point.

For every active conditioned allocation:

~~~text
normalizedMargin = (claim - field) / claim
~~~

The active conditioned allocation with the greatest normalized margin wins.

This compares how deeply the point lies inside each authored conditioned realization, rather than simply favoring the role with the largest raw claim.

If normalized margins are exactly tied, stable binding-key canonical order breaks the tie.

The tie-break uses semantic binding identity only.

## Final winner

The final point winner is:

1. strongest active conditioned claim, if any conditioned claim is active;
2. otherwise the structural matrix winner.

The result retains the exact AUTH-0043 allocation and therefore its exact AUTH-0042 resolution decision.

A future backend only needs to map the winning stable binding key to the concrete material it already resolved.

## Backend identity boundary

AUTH-0044 returns:

- winning semantic role;
- winning stable AUTH-0038 binding key;
- winning AUTH-0043 allocation;
- structural winner;
- active conditioned-claim count.

It does not return or store concrete material identity.

## Non-material state

Outside-island samples and authored cave void remain without a winner.

AUTH-0044 never fills authored void.

## Evidence

The authorship-discrete-material-realization-v1 corpus uses the canonical six representatives at semantic depth 0.52.

Panels:

- STRUCTURAL;
- FINAL ROLE;
- ALTERATION WIN;
- WATER WIN;
- MINERAL WIN;
- ACTIVE CONDITIONED.

Expected behavior:

- structural primary/secondary regions remain coherent;
- conditioned winners occur as patches within their accepted broader AUTH-0043 systems;
- overlapping conditioned activity is visible;
- final winners do not form uncorrelated sample-scale checkerboard noise;
- repeated realization at the same exact point returns the same binding key.

The manifest records:

- structural primary/secondary winner counts;
- final winner counts by semantic role;
- conditioned winner samples;
- multi-active conditioned samples;
- repeat determinism mismatches;
- unique winning binding keys;
- horizontal neighbor transitions.

## Acceptance gate

Reject AUTH-0044 if:

- the same exact point can produce different winners;
- candidate/allocation encounter order changes the winner;
- traversal order enters the realization;
- runtime randomness enters the realization;
- backend material identity enters the world model;
- a conditioned claim can win when its field is not below its AUTH-0043 claim;
- conditioned conflict resolution ignores normalized margin;
- a structural winner is absent from material-present points;
- a final winner is absent from material-present points;
- outside or authored void receives a winner;
- the smooth selector is discontinuous at arbitrarily nearby points inside one binding domain;
- evidence reports any determinism mismatch;
- visual evidence shows sample-scale checkerboard realization unrelated to upstream boundaries.

## Parallel implementation boundary

AUTH-0044 changes no cave, carver, persistence, mutation, Minecraft block, or registry behavior.

It defines the backend-neutral semantic winner only.

## Next milestone

If AUTH-0044 is accepted, the next authorship milestone should define a **backend material-binding application contract**.

That contract should allow an adapter to supply the concrete material resolved for each stable AUTH-0038 binding key and apply it to AUTH-0044 semantic winners while preserving:

- authored void;
- stable binding reuse;
- deterministic winner identity;
- no backend identity leakage into the world layer.

The concrete Minecraft mapping itself should remain in the adapter.
