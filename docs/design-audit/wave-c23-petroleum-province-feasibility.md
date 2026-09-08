# Wave C23 — petroleum strategic-node province feasibility

**Status:** IN PROGRESS  
**Issue:** #365  
**Predecessor:** C22 / PR #360  
**Authorship input:** AUTH-0100 / PR #361  
**Parent vertical slice:** #224

## Purpose

C22 establishes the petroleum gameplay policy and AUTH-0100 supplies the exact regional geological
inventory. C23 is the first Content layer that turns those accepted inputs into a province-level
feasibility/replan decision.

It answers:

> Can this exact published province satisfy an intentionally requested petroleum strategic node from
> accepted AUTH-0100 geological evidence, or must Content re-plan the province?

It does not select a literal petroleum site.

## Province intents

### ORDINARY_PROVINCE

Petroleum imposes no acceptance requirement.

A normal province is acceptable with:

- zero eligible petroleum islands;
- one eligible island;
- many eligible islands.

C23 therefore returns `NO_PETROLEUM_REQUIREMENT` regardless of AUTH-0100 eligible count.

This preserves C22's rule that ordinary petroleum-free provinces are valid.

### PETROLEUM_STRATEGIC_NODE

Content has deliberately requested a petroleum-bearing province.

```text
AUTH-0100 eligibleIslandCount == 0
    -> REPLAN_REQUIRED

AUTH-0100 eligibleIslandCount > 0
    -> CANDIDATES_AVAILABLE
```

No opportunity magnitude threshold is added.

## Candidate evidence

Every C23 plan exposes exactly:

- `AUTH-0100 eligibleIslands()` in canonical association order;
- `AUTH-0100 rankedEligibleIslands()` in unchanged descriptive geological order.

The plan envelope rejects substituted candidate lists.

C23 deliberately exposes **no selected island**. The highest-ranked geological candidate is not
silently promoted into an oilfield, refinery, route hub, or civilization site.

## Replan meaning

`REPLAN_REQUIRED` means only:

> the exact current published province cannot satisfy the deliberately requested petroleum-node
> geological prerequisite.

It does not prescribe how Authorship replans the region and does not inject petroleum into
zero-opportunity geology.

## No new Authorship gap yet

C23 requires no new Authorship producer beyond AUTH-0100.

AUTH-0100 is sufficient for **province geological feasibility**. It is intentionally insufficient for
final petroleum site selection because final extraction/refinery/logistics roles may need additional
Content requirements over already accepted site/access evidence or may expose a specific new
backend-neutral evidence gap.

Do not request another Authorship wrapper merely because final selection remains open.

## Downstream boundary

A later Content milestone may define explicit petroleum infrastructure roles, for example:

- extraction/pumpjack support;
- refinery/processing support;
- tank/freight transfer;
- route/service relationship.

Those requirements should first attempt to consume accepted AUTH-0096 local surface-site and
AUTH-0097 directional-access evidence. Only a demonstrated missing world cause should reopen
Authorship.

Implementation continues to own concrete petroleum identity, deposit geometry/quantity, extraction,
Minecraft placement, structure geometry/admission, persistence, and lifecycle.

## Acceptance

1. ordinary-province intent never replans solely for petroleum absence;
2. strategic-node intent returns `REPLAN_REQUIRED` at zero AUTH-0100 eligible islands;
3. strategic-node intent returns `CANDIDATES_AVAILABLE` at nonzero eligible coverage;
4. canonical and ranked candidate views are exact AUTH-0100 pass-throughs;
5. plan construction rejects substituted candidate evidence;
6. public planner accepts only one exact AUTH-0100 profile plus one Content intent;
7. no selected island, frequency, threshold, reserves, quantity, site role, or backend ontology enters
   C23;
8. targeted tests and repository CI pass.

No Minecraft manual or human visual gate applies to C23.
