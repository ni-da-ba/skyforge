# AUTH-0054 — Reservation Requirement Synthesis

AUTH-0054 synthesizes admission-safe reservation minima from one exact deterministic archipelago plan.

It is the constructive companion to AUTH-0053.

AUTH-0053 asks:

    Are the reservations already consumed by this plan sufficient?

AUTH-0054 asks:

    If support is certifiable, what reservation values would be sufficient for this exact plan?

AUTH-0054 does not mutate the plan and does not silently re-run placement.

## Position in the pipeline

    templates
        -> deterministic archipelago/group plan
        -> exact member descriptors/seeds
        -> AUTH-0054 requirement synthesis
        -> author/tool decides whether to construct a fresh request
        -> fresh deterministic re-plan if horizontal/group assumptions change
        -> AUTH-0053 admission
        -> AUTH-0052 proof-backed world compilation

This ordering preserves the rule that already-consumed spacing assumptions are never rewritten in place.

## Member requirements

SkyIslandSupportReservationMemberRequirement records one exact planned member:

- group ordinal and identifier;
- member ordinal;
- exact descriptor seed;
- morphology identifier;
- current member horizontal reservation;
- optional support certificate;
- optional required horizontal reservation;
- optional required below-suspension reservation;
- optional required above-suspension reservation.

Exact numerical requirements are present if and only if support is analytically certified.

Uncertified members receive no guessed values.

## Exact world-space arithmetic

AUTH-0054 starts from the same proof-grade support bounds as AUTH-0052:

    supportMinX = nextDown(centerX - supportRadius)
    supportMaxX = nextUp(centerX + supportRadius)

and equivalently for Z and vertical support.

The synthesized horizontal reservation is the largest exact center-to-bound difference over X/Z.

The synthesized below/above reservations are the exact suspension-to-bound differences.

The synthesizer then verifies each result against the AUTH-0053 member-admission predicate.

If a binary64 cancellation/rounding edge leaves the candidate insufficient, the synthesizer expands conservatively by the relevant world-coordinate ulp and rechecks.

A synthesized value is therefore not merely the provider-relative extent.

It is an admission-safe reservation value in the exact member world frame.

## Group shared member reservation

SkyIslandGroupTemplate uses one reservedHorizontalRadius for all members of a group.

AUTH-0054 therefore synthesizes, per exact group plan:

    requiredMemberHorizontalRadius
        = max(member required horizontal radius)

The existing current member reservation remains recorded independently.

If the synthesized requirement exceeds the current value, a fresh re-plan is required before treating the new value as a planning assumption.

## Exact-plan outer group radius

For every certified member in the current exact plan:

    member reach from group center
        = outward(
            hypot(memberCenter - groupCenter)
            + synthesized member horizontal requirement)

The exact-plan required group radius is the maximum member reach.

This value answers:

    What group radius contains the certified support of the members
    at the centers that exist in this exact plan?

It does **not** answer:

    What group radius will be sufficient after changing member reservation
    and re-running placement?

Those are different questions.

## Why group radius is not re-plan invariant

Group placement can depend on reservation-derived spacing constraints.

A fresh request with a larger member horizontal reservation may require:

- a larger minimum center spacing;
- different accepted cluster candidates;
- different member centers;
- a different exact outer group requirement.

AUTH-0054 includes a regression proving this.

Two plans use the same root seed and morphology intent but different member reservation/spacing inputs.

Their member centers differ after re-planning, and their exact-plan required group radii differ.

Therefore the first plan's synthesized group radius must not be copied forward as though it were a proof for the second plan.

The correct workflow is:

1. synthesize from current plan;
2. construct a fresh planning request;
3. re-plan;
4. synthesize again;
5. run AUTH-0053.

## Global vertical reservation

When every member is certified, AUTH-0054 synthesizes archipelago-wide:

    requiredBelowSuspension
        = max(member required below)

    requiredAboveSuspension
        = max(member required above)

These become an optional SkyIslandWorldVerticalReservation.

If any member is uncertified, global vertical synthesis is absent.

AUTH-0054 does not claim a complete vertical recommendation while one member's support is unknown.

## Partial synthesis

A plan containing an uncertified non-endpoint provider blend remains valid as an ordinary deterministic plan.

AUTH-0054 records:

- the member identity;
- its exact descriptor seed;
- certificate absence;
- absent numerical requirements.

For any group containing an uncertified member:

- requiredMemberHorizontalRadius is absent;
- exactPlanRequiredGroupRadius is absent.

For any archipelago containing an uncertified member:

- global below/above requirements are absent;
- fullySynthesized is false.

Known requirements from other certified members remain visible at member level, but are not promoted into a complete world recommendation.

## Seed-aware synthesis

AUTH-0054 preserves AUTH-0053's seed-awareness.

A provider may analytically vary support according to descriptor.seed.

The canonical proof uses two otherwise equivalent plans with distinct root seeds.

The exact derived member seeds differ.

The provider returns approximately:

- 300 horizontal support for seed A;
- 120 horizontal support for seed B.

AUTH-0054 produces correspondingly different admission-safe requirements without compiling procedural graphs.

No reusable-template assumption replaces the exact member descriptor.

## No graph compilation

Requirement synthesis calls only the provider support-certification seam.

It does not call:

- compilePrimary;
- procedural graph construction;
- world-volume compilation;
- backend realization.

A test provider throws if compilePrimary is invoked; AUTH-0054 still synthesizes successfully from the support certificate.

## World-catalog facade

SkyIslandWorldCatalogCompiler exposes:

    synthesizeSupportReservationRequirements(plan, registry)

for callers already using the world-planning facade.

This is advisory.

There is intentionally no:

    applyRequirements(plan)

or in-place mutation API.

## Relationship to AUTH-0053

AUTH-0054 and AUTH-0053 are mechanically cross-checked.

For every certified member, the synthesized horizontal/below/above values are substituted into SkyIslandSupportReservationMemberCheck.

The synthesizer fails if AUTH-0053 would reject those values.

For a fully synthesized plan, synthesizedVerticalReservation can be passed directly into AUTH-0053.

If the plan's existing horizontal/group reservations are already sufficient, AUTH-0053 should admit it.

If horizontal/group recommendations exceed current assumptions, callers must first create and re-plan a new request.

## Evidence atlas

AUTH-0054 uses a 1280x720 16:9 atlas.

The panels are:

- ADEQUATE_CURRENT;
- SYNTHESIZE_HORIZONTAL_GROUP;
- SYNTHESIZE_VERTICAL;
- UNCERTIFIED_INCOMPLETE;
- SEED_A_VS_SEED_B;
- REPLAN_CHANGES_GROUP_RADIUS.

The atlas shows current reservations as broad bars and synthesized minima as requirement markers.

The final panel compares exact-plan outer group requirements before and after a fresh deterministic re-plan, making the non-transferability of the first outer radius visually explicit.

## Acceptance gate

Reject AUTH-0054 if:

- numerical requirements are synthesized for uncertified members;
- template identity replaces exact descriptor/seed inputs;
- synthesized member values fail AUTH-0053;
- world-space outward rounding from AUTH-0052 is ignored;
- a group with any uncertified member receives a complete shared/group requirement;
- global vertical requirements are published for a partially uncertified plan;
- exact-plan group radius is described as re-plan invariant;
- synthesis mutates an existing plan or template;
- synthesis compiles procedural graphs;
- applying recommendations is hidden inside the synthesis API;
- Minecraft or NeoForge types enter the contract.

## Next milestone

AUTH-0054 makes reservation defects actionable, but applying horizontal/group recommendations still requires manual request reconstruction.

A likely AUTH-0055 direction is **explicit re-plan proposal construction**.

It should create a new immutable proposal/request from:

- original semantic planning intent;
- synthesized requirements;
- explicit author-selected safety margins;

without modifying the existing plan.

The proposal should remain inspectable before deterministic re-planning, and it should clearly distinguish:

- values copied unchanged from prior intent;
- values raised by proof requirements;
- values increased by optional author margin.
