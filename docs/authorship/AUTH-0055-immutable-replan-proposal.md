# AUTH-0055 — Immutable Proof-Aware Re-Plan Proposal

AUTH-0055 converts AUTH-0054 reservation requirements into an explicit, reviewable planning proposal.

It does not mutate an accepted request or plan and does not execute the candidate re-plan.

## Inputs

Proposal construction requires five immutable inputs:

1. the original SkyIslandArchipelagoRequest;
2. the exact SkyIslandArchipelagoPlan produced from that request;
3. the AUTH-0054 reservation requirement synthesis for that plan;
4. the current SkyIslandWorldVerticalReservation;
5. explicit author-selected additive safety margins.

The original plan alone is insufficient because realized placement is not a complete substitute for original reusable semantic planning intent.

## Provenance validation

AUTH-0055 validates the input triple before constructing a candidate.

The original request is deterministically replayed and must reproduce the supplied original plan exactly.

The AUTH-0054 synthesis must then match:

- archipelago root seed;
- group count/order;
- group identifier/ordinal;
- current member reservation;
- current group reservation;
- member group/ordinal;
- exact descriptor seed;
- morphology stable identifier;
- current member reservation.

A mismatched request/plan/synthesis combination is rejected.

The replay is validation of the **original** request.

AUTH-0055 does not execute the candidate request.

## Author margin

SkyIslandSupportReplanMargin contains four explicit additive margins:

- memberHorizontal;
- groupRadius;
- belowSuspension;
- aboveSuspension.

Margins are independent of analytical proof minima.

SkyIslandSupportReplanValue preserves four pieces of provenance:

- originalValue;
- optional proofMinimum;
- authorMargin;
- proposedValue.

The proposal baseline is:

    max(originalValue, proofMinimum)

when proof exists, otherwise:

    originalValue

Author margin is then added explicitly above that baseline.

This means author margin is never silently swallowed by an already-larger original reservation.

## Incomplete proof

When AUTH-0054 is incomplete because one or more members remain uncertified:

- proposal construction still succeeds;
- uncertified identities remain visible through synthesis;
- proof minima for affected group/global values remain absent;
- no candidate SkyIslandArchipelagoRequest is published;
- no candidate vertical reservation is published;
- requireComplete() fails.

Author margin can still be displayed relative to original intent, but it does not convert missing proof into a complete proposal.

## Member horizontal reservation

For a certified group:

    proposed member horizontal
        = max(original member horizontal,
              AUTH-0054 required member horizontal)
          + explicit author member-horizontal margin

with outward floating-point addition when the margin is positive.

AUTH-0055 never reduces an existing reservation.

## Pairwise layout spacing

For groups with more than one member, group layout spacing must continue to satisfy:

    minimumCenterSpacing
        >= 2 * proposedMemberHorizontal
           + minimumMemberGap

AUTH-0055 therefore synthesizes a proof-aware minimum center spacing.

The proof-only spacing uses the AUTH-0054 proof member radius.

The author contribution induced by member-horizontal margin is retained separately as the corresponding doubled spacing margin.

The final exact candidate spacing is checked against the proposed member reservation and raised if one floating-point step is still required.

### Cluster

SkyIslandGroupLayout.Cluster keeps:

- phase;
- radial jitter fraction;
- orientation jitter.

Only minimumCenterSpacing may increase.

### Chain

SkyIslandGroupLayout.Chain keeps:

- heading;
- spacing jitter fraction;
- lateral jitter;
- curve amplitude;
- orientation jitter.

Because Chain.minimumCenterSpacing is:

    centerSpacing * (1 - spacingJitterFraction)

AUTH-0055 raises centerSpacing just enough for the accepted minimum to meet the proposed spacing floor.

### Single-member groups

Pairwise spacing has no semantic effect for one member.

AUTH-0055 preserves the original layout spacing control unchanged rather than manufacturing a pairwise-spacing increase.

## Provisional outer group radius

AUTH-0054 provides an exact-plan outer group radius for the **current** member centers.

AUTH-0055 uses that as a proof minimum, but it cannot treat it as proof for the fresh placement.

The proposal also computes a dependent current-layout floor using the proposed member horizontal radius:

    max(
      distance(currentMemberCenter, currentGroupCenter)
      + proposedMemberHorizontal
    )

The candidate group's provisional radius is at least:

- the original group radius;
- the AUTH-0054 current-plan proof minimum;
- the dependent current-layout floor;
- the proposed member horizontal radius;
- explicit author group-radius margin above the original/proof baseline.

This makes the candidate request internally informed by every currently known requirement.

It is still **provisional** because changing member reservation/layout spacing can move members in the fresh plan.

## Why candidate planning is deliberately separate

A two-member canonical fixture starts with:

    member radius = 120
    member spacing = 260
    group radius = 280

AUTH-0054 requires a substantially larger member support radius.

AUTH-0055 raises:

- member reservation;
- pairwise layout spacing;
- a provisional outer group floor.

That candidate request is syntactically valid.

However, when explicitly planned, the larger pairwise spacing moves members farther from the group center and the provisional current-layout group floor can become insufficient.

AUTH-0055 proposal construction succeeds because it does not execute that candidate.

This is intentional.

The candidate must be reviewed and, when executed downstream, either:

- succeed under sufficient author margin / outer reservation, or
- fail deterministically and be revised.

No hidden retry or silent radius inflation occurs.

## Successful fresh-plan path

A second canonical fixture supplies explicit author margin sufficient for the new outer placement.

The candidate is then explicitly planned by the test.

The fresh plan is different from the original.

AUTH-0054 is run again on that fresh plan.

Its exact-plan group requirement differs from the original AUTH-0054 value.

AUTH-0053 is then run against the fresh plan and candidate vertical reservation.

Only that fresh result may be treated as admitted.

The original group proof is never inherited.

## Vertical reservation

When AUTH-0054 is complete:

    proposedBelow
        = max(originalBelow, requiredBelow)
          + authorBelowMargin

    proposedAbove
        = max(originalAbove, requiredAbove)
          + authorAboveMargin

Vertical-only changes do not force a fresh horizontal placement.

The candidate archipelago request may remain byte/record-equal to the original request while the candidate vertical reservation changes.

## Candidate request

A complete proposal publishes an immutable candidate SkyIslandArchipelagoRequest.

It preserves unchanged:

- root seed;
- archipelago center X/Z;
- base suspension elevation;
- minimum group gap;
- archipelago layout;
- group identifiers;
- group roles;
- member templates;
- member morphology intent;
- member minimum gaps;
- member elevation jitter;
- all layout controls unrelated to required spacing.

It changes only explicitly proposed reservation/spacing controls.

The candidate is a planning **proposal**, not an accepted plan.

## No apply/replan side effect

AUTH-0055 intentionally exposes no method that:

- mutates originalRequest;
- mutates originalPlan;
- replaces the accepted plan;
- silently invokes SkyIslandArchipelagoPlanner on the candidate;
- silently loops until a candidate happens to fit.

Proposal review remains a distinct boundary.

## World-catalog facade

SkyIslandWorldCatalogCompiler exposes:

    proposeSupportAwareReplan(...)

for callers already using the world-planning facade.

The returned object is still only an AUTH-0055 proposal.

## Visual evidence atlas

AUTH-0055 uses a 1280x720 16:9 atlas with six panels:

- NO_CHANGE;
- PROOF_RAISES_HORIZONTAL;
- AUTHOR_MARGIN;
- VERTICAL_ONLY;
- INCOMPLETE_UNCERTIFIED;
- CANDIDATE_REPLAN_BOUNDARY.

The first four show original, proof minimum, author margin, and proposed values separately.

The incomplete panel shows missing proof without a candidate request.

The boundary panel shows:

    proposal built
        -> candidate not automatically planned
        -> explicit fresh plan
        -> fresh AUTH-0054
        -> fresh AUTH-0053

and distinguishes the original exact-plan group proof from the fresh one.

## Acceptance gate

Reject AUTH-0055 if:

- original semantic request is reverse-engineered from realized placement;
- mismatched request/plan/synthesis inputs are accepted;
- an uncertified synthesis publishes a complete candidate request;
- proof minima and author margin are merged into one opaque number;
- original reservations can decrease;
- multi-member layout spacing can violate 2R + gap;
- single-member layout spacing is changed without semantic need;
- unrelated layout controls are altered;
- current-layout outer group radius is described as final proof for the fresh plan;
- candidate planning occurs during proposal construction;
- candidate planning silently retries or inflates reservations;
- original request or plan is mutated;
- fresh placement inherits old exact-plan group proof;
- Minecraft or NeoForge types enter the proposal contract.

## Next milestone

AUTH-0055 ends at a reviewable candidate request.

A likely AUTH-0056 direction is **explicit candidate execution and proof convergence**.

It should:

1. execute an approved AUTH-0055 candidate exactly once;
2. report deterministic planner success/failure;
3. if successful, re-run AUTH-0054 on the fresh plan;
4. run AUTH-0053 against fresh requirements/reservations;
5. produce a convergence report distinguishing:
   - accepted in one pass;
   - fresh-plan reservation defect;
   - uncertified fresh member;
   - planner containment/spacing failure.

It must not introduce an unbounded automatic retry loop.
