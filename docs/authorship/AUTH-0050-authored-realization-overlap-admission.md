# AUTH-0050 — Authored-Realization Overlap Admission

AUTH-0050 defines backend-neutral post-realization admission policy for overlap between explicit AUTH-0046 authored-realization associations.

It sits after conservative group/archipelago planning and after physical realization has been compiled. It does not replace the existing planners.

## Why this milestone exists

The existing group and archipelago planners already provide cheap deterministic spacing guarantees through reserved horizontal member radii, required center spacing, reserved group radii, and minimum group gaps.

Those are useful conservative planning envelopes. They are not the authoritative current native authored domain.

AUTH-0048 established the exact pointwise ownership boundary:

    conservative associated bounds
        -> exact compiled physical occupant
        -> current native authored owner

AUTH-0050 adds policy around complete association pairs.

The central distinction is:

    no overlap witness found
        !=
    continuous separation proved

AUTH-0050 therefore fails closed whenever it cannot prove a strict pair is safe.

## Scope

AUTH-0050 answers:

> May this realized pair coexist in one accepted authored world under the declared pair policy?

It does not choose point ownership, semantic material, concrete backend material, or Minecraft mutation.

## Pair modes

Every unordered AUTH-0046 association pair has one mode. Unlisted pairs default to SEPARATE.

### SEPARATE

True native authored overlap is forbidden.

The pair is admitted only when AUTH-0050 has a conservative proof that the native authored domains cannot share a world point.

### STACKED

The pair is an intentional same-X/Z vertical stack.

STACKED requires:

- bit-exact equal realized center X;
- bit-exact equal realized center Z;
- a positive declared minimum vertical separation;
- conservative realized bounds proving at least that much vertical gap.

Because the bounds are conservative, a positive bound gap is also a safe lower bound on actual physical separation.

A same-X/Z pair with broad overlapping vertical reservations cannot be certified merely because sampled exact columns appear separate. It is rejected until a proof-grade conservative vertical envelope/gap is available.

### COMPOSE

True native authored overlap is explicitly permitted.

COMPOSE is the only mode that may admit a pair without separation proof.

This is an authored policy declaration, not an implicit fallback. AUTH-0050 does not itself define how overlapping authored content should semantically combine.

## Canonical pair identity

SkyIslandAuthoredOverlapPairKey is an unordered pair of full AUTH-0046 association canonical tokens.

It is independent of list order, backend encounter order, physical proximity, or seed similarity.

Pair rules therefore remain stable under catalog input reversal.

## Safe separation certificates

AUTH-0050 currently recognizes two proof-quality strict-separation certificates.

### Disjoint conservative world bounds

If the associated conservative WorldBounds do not intersect, the pair cannot share a world point.

This is sufficient for CERTIFIED_SEPARATE.

Broad bounds only cause false uncertainty, never unsafe admission.

### Disjoint native horizontal support discs

AUTH-0020 guarantees every naturalized native authored boundary radius is at most the authored nominal radius.

Therefore each current native authored domain is contained inside the horizontal disc centered on the realized X/Z placement with radius equal to the authored nominal radius.

If:

    centerDistance >= radiusA + radiusB

the positive-interiority native authored domains cannot overlap.

Equality is safe because AUTH-0020 ownership reaches zero at the boundary itself.

This certificate allows conservative query bounds to overlap without creating a false overlap rejection.

Thus conservative bounds overlap does not imply authored overlap.

## Intentional stacked certificate

STACKED is stronger than ordinary separation.

It requires same-X/Z placement and a declared minimum vertical gap.

AUTH-0050 certifies STACKED only from conservative vertical bounds.

This is deliberately strict. A finite sample of exact compiled columns cannot prove that two continuous variable surfaces never intersect somewhere between samples.

If broad query bounds overlap vertically, AUTH-0050 returns REJECTED_STACK_REQUIREMENT.

A later milestone may introduce provider-backed certified physical extrema. Until then, proof-grade vertical envelopes are required for accepted intentional stacks.

## Exact overlap witness

When strict separation cannot be certified, AUTH-0050 performs a deterministic witness search over the intersection of the two conservative bounds.

The search uses a pair-only AUTH-0048 ownership resolver.

A returned witness is exact in the important sense:

    AUTH-0048(worldPoint).authoredOwners == both associations

The witness proves that native authored overlap exists at that concrete world coordinate.

For strict or failed STACKED policy, this produces REJECTED_WITNESSED_OVERLAP.

## Why witness absence is not proof

The witness search is finite.

Continuous authored/physical fields may overlap in a region not hit by the deterministic witness lattice.

Therefore:

    witness found
        -> exact proof overlap exists

    no witness found
        -> no proof either way

For strict SEPARATE pairs lacking another certificate, no witness produces REJECTED_UNCERTIFIED_SEPARATION.

It never produces admission.

This asymmetry is a central AUTH-0050 invariant.

## Pair statuses

AUTH-0050 exposes six pair statuses.

- CERTIFIED_SEPARATE — admitted by disjoint conservative bounds or disjoint native support discs.
- CERTIFIED_STACKED — admitted same-X/Z stack with conservative vertical gap meeting policy.
- ACCEPTED_EXPLICIT_COMPOSITION — admitted because COMPOSE is explicitly declared.
- REJECTED_WITNESSED_OVERLAP — rejected with an exact AUTH-0048 native-overlap witness.
- REJECTED_UNCERTIFIED_SEPARATION — rejected because no proof of separation exists.
- REJECTED_STACK_REQUIREMENT — rejected because STACKED placement cannot prove its declared semantics.

## Catalog-level admission

SkyIslandAuthoredOverlapAdmissionAuditor evaluates every unordered pair in an AUTH-0046 catalog.

SkyIslandAuthoredOverlapAdmissionReport.admitted() is true only when every pair is admitted.

One unsafe or uncertified pair therefore rejects the authored-realization catalog as a whole.

## Policy identity validation

Every explicit pair rule must refer to associations present in the audited catalog.

AUTH-0050 rejects policy containing an unknown association token, duplicate pair rule, or self-pair rule.

This prevents stale or cross-world policy from silently controlling another catalog.

## Determinism

AUTH-0050 inherits canonical association ordering from AUTH-0046 and canonicalizes pair order.

Reversing catalog input order must not change pair keys, pair modes, certificates, admission status, or deterministic witnesses.

No random retry or backend traversal state participates.

## Conservative bounds may overlap

AUTH-0050 does not use the naive rule:

    bounds intersect -> reject

Intersecting conservative bounds may still be admitted when native horizontal support discs are provably disjoint or explicit COMPOSE permits overlap.

STACKED may also coexist with overlapping horizontal bounds because its proof dimension is vertical.

What AUTH-0050 refuses is uncertified overlap across all relevant conservative proof dimensions.

## Authored cave void

AUTH-0050 is below cave/material semantics in the same way as AUTH-0048 ownership.

A native-authored cave void remains part of the island's authored ownership domain.

Cave void therefore cannot make two overlapping islands count as separated.

## Evidence

The AUTH-0050 evidence corpus is policy/scenario oriented.

Canonical scenarios:

- DISJOINT_BOUNDS;
- OVERLAP_BOUNDS_SUPPORT_DISJOINT;
- STACK_CERTIFIED;
- STACK_BROAD_UNCERTIFIED;
- STRICT_TRUE_OVERLAP;
- STRICT_UNCERTIFIED;
- COMPOSE_TRUE_OVERLAP.

The manifest records pair mode, pair status, admission, conservative-bounds intersection, native support-disc separation, conservative vertical gap, declared minimum vertical separation, and witness presence.

Acceptance requires:

- overlapping bounds plus disjoint native support -> admitted strict separation;
- valid same-X/Z vertical stack -> certified;
- broad overlapping stack bounds -> rejected;
- strict true overlap -> exact witness and rejection;
- strict uncertain pair with no witness -> rejection, never admission;
- true overlap plus explicit COMPOSE -> admitted;
- catalog order reversal -> same canonical pair results.

The visual matrix is diagnostic rather than an aesthetic morphology gate.

## Parallel implementation boundary

AUTH-0050 changes no Minecraft runtime behavior.

It does not modify NeoForge physical admission, mutation ledgers, carvers, structures, decorators, chunk traversal, persistence, save/reload, or client state.

The implementation lane already owns runtime physical-admission and mutation-fence responsibilities.

AUTH-0050 is the native world-authorship planning gate upstream of those adapter concerns.

## Acceptance gate

Reject AUTH-0050 if:

- finite no-witness search is treated as proof of separation;
- conservative bounds intersection alone is treated as native overlap;
- native support-disc separation is ignored;
- STACKED may use different horizontal centers;
- STACKED may be accepted without a proof-grade minimum vertical gap;
- true strict overlap is admitted without explicit COMPOSE;
- COMPOSE is inferred rather than declared;
- policy references associations outside the catalog;
- catalog/list order changes pair outcomes;
- backend material identity influences admission;
- Minecraft or NeoForge types enter the contract.

## Next milestone

AUTH-0050 intentionally exposes one remaining limitation: broad conservative vertical query bounds can prevent certification of physically safe stacks.

The next useful authorship milestone should address proof-grade realization envelopes rather than weaken AUTH-0050.

A likely AUTH-0051 direction is certified realized support envelopes/extrema.

The goal would be to let accepted morphology providers expose conservative but tighter support information such as maximum physical upper Y relative to suspension and minimum physical underside Y relative to suspension.

AUTH-0050 could then consume those certified envelopes while broad backend query reservations remain unchanged.

That preserves the distinction:

    backend query bounds
        !=
    proof-grade authored realization envelope
