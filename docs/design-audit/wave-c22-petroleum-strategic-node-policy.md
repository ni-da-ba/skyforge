# Wave C22 — AUTH-0098 petroleum strategic-node policy

**Status:** MERGE CANDIDATE synchronized with accepted AUTH-0100  
**Issue:** #357  
**Predecessor:** C21 / PR #315  
**Authorship producer:** AUTH-0098 / PR #335  
**Parent vertical slice:** #224

## Purpose

C22 is the Content-owned bridge between accepted AUTH-0098 petroleum-system geological opportunity
and later regional petroleum planning/realization.

AUTH-0098 supplies deterministic backend-neutral source/reservoir/seal/system opportunity over exact
AUTH-0033 provenance. It deliberately does not assign gameplay rarity, guarantees, province policy,
literal deposits, reserves, extraction, or Minecraft identity.

The existing Content corpus already places petroleum in mature R3 industry and treats crude petroleum
as the leading true strategic-node resource.

C22 makes that gameplay boundary executable.

## Fixed petroleum policy

```text
availability                 STRATEGIC_NODE
progression                   R3_MATURE_INDUSTRY
first-flight critical         false
ordinary-province guarantee   none
alternative access            bounded trade/salvage
mature industrial supply      primary extraction or logistics required
regional node selection       canonical AUTH-0098 regional inventory required
```

This deliberately permits ordinary provinces with no petroleum.

C22 does **not** define how many provinces contain petroleum. `STRATEGIC_NODE` is a semantic
availability class, not a numeric frequency threshold.

## AUTH-0098 consumption rule

For one accepted AUTH-0098 island profile:

```text
peakSystemOpportunity == 0
    -> geologically ineligible

peakSystemOpportunity > 0
    -> geologically eligible
    -> meanSystemOpportunity may rank this candidate against other eligible candidates
```

C22 introduces no threshold beyond the accepted zero/nonzero geological boundary.

The unchanged mean opportunity is ordinal planning evidence only. It is not:

- reserves;
- saturation;
- grade;
- pressure;
- recoverable volume;
- deposit count or thickness;
- extraction rate;
- industrial throughput;
- a Minecraft placement weight.

## Strategic-node selection and absence

Petroleum differs deliberately from C20 base metals.

C20 provides hard Iron/Copper/Zinc availability guarantees at starting-cluster or post-flight-province
scope. C22 provides **no ordinary-province hard petroleum guarantee**.

That supports the existing geography:

```text
many ordinary provinces
    -> no petroleum required

some regions
    -> small trade/salvage/import indications may exist

intentionally petroleum-bearing strategic-node province
    -> must select/re-plan around AUTH-0098-eligible geology
    -> must not inject petroleum into zero-opportunity geology
```

Trade/salvage may introduce the player to petroleum, provide emergency fuel, or accelerate one
experiment. They do not satisfy mature industrial throughput indefinitely.

## Concrete Authorship consumer boundary

C22 creates a specific need that AUTH-0098 island-scale evidence cannot itself satisfy.

When Content intentionally selects or re-plans a petroleum-bearing strategic-node province, it needs:

- complete canonical coverage of every published island in the candidate region;
- each island's exact AUTH-0098 profile/provenance;
- geological eligibility at the unchanged AUTH-0098 zero/nonzero boundary;
- deterministic descriptive ranking among eligible islands by unchanged mean system opportunity;
- exact tie behavior preserving canonical association order.

Therefore a canonical **regional AUTH-0098 petroleum opportunity inventory is now justified**.

This is analogous in shape to AUTH-0094 only because the downstream planner now needs regional
coverage. It must not add petroleum availability classes, reserves, deposit quantities, site
selection, or worldgen to Authorship.

## Implementation boundary

Implementation remains owner of:

- concrete petroleum fluid/block/item identity;
- deposit geometry/count/thickness/volume;
- pressure/depletion/extraction mechanics;
- pumpjack/refinery integration;
- exact worldgen/placement;
- persistence and lifecycle.

C22 does not authorize a literal oil deposit from a nonzero AUTH-0098 value.

## Bootstrap / HS-06 boundary

C22 does not resolve or constrain HS-06 first-hours guarantees.

Petroleum remains explicitly non-bootstrap and non-first-flight-critical. The Bootstrap Province must
retain a practical non-petroleum route to early air mobility.

## Acceptance

C22 is accepted only if:

1. petroleum is fixed to `STRATEGIC_NODE`;
2. petroleum is fixed to `R3_MATURE_INDUSTRY`;
3. first-flight criticality is false;
4. ordinary provinces carry no petroleum hard guarantee;
5. alternative trade/salvage is bounded and cannot substitute for mature industrial supply;
6. geological eligibility is exactly AUTH-0098 nonzero peak system opportunity;
7. candidate ranking passes through unchanged mean system opportunity only for eligible candidates;
8. regional petroleum-node selection explicitly requires a canonical AUTH-0098 regional inventory;
9. targeted backend-neutral policy tests pass;
10. repository CI passes;
11. shared state records the new concrete Authorship consumer boundary.

No Minecraft manual run or visual gate applies to C22.


## Accepted candidate evidence

Exact C22 candidate `b4cd2777007ea3fcfc8b2385eecbee310b1c126b` passed:

- Wave C22 Petroleum Content Policy run `34183752198`;
- repository CI run `34183752179`;
- incidental Wave C20 Base Metal Content Policy regression run `34183752159`.

Intervening `main` movement before acceptance changed only Audit-state documentation, so the tested
C22 dependency surface remained unchanged under `VALIDATION_POLICY.md`.


## AUTH-0100 fulfillment

While C22 was validating, Authorship consumed issue #357 and merged **AUTH-0100 / PR #361** as
`35b9b420d338415105237e56317580535808178a`.

AUTH-0100 supplies exactly the regional evidence C22 requested:

- complete canonical AUTH-0087/AUTH-0046 published-island coverage;
- one exact AUTH-0098 profile per associated authored descriptor;
- zero/nonzero geological eligibility at unchanged `peakSystemOpportunity() > 0`;
- canonical eligible-island lists/counts;
- stable descending ranking by unchanged `meanSystemOpportunity()`;
- canonical association order for exact ties;
- no STRATEGIC_NODE frequency, guarantee, reserves, deposit quantity, site selection, or backend
  realization.

C22 therefore no longer waits on an Authorship producer. The next Content step may consume AUTH-0100
to evaluate petroleum-bearing strategic-node province requirements. Final island/site selection must
not be reduced to geological rank alone if later route/site/civilization requirements need additional
accepted evidence.
