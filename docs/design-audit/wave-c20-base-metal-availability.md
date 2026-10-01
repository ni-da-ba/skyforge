# Wave C20 — AUTH-0093 base-metal availability policy

**Status:** MERGED / ACCEPTED  
**PR:** #302  
**Merge:** `0b76038a0b0a98628b6b3ec0e39b5cb0cd76a8d9`  
**Accepted policy head:** `efc03a4f5233eade9eff24d438315315524b247d`  
**Issue:** #301  
**Authorship producer:** AUTH-0093 / PR #294  
**Parent vertical slice:** #224

## Purpose

C20 is the narrow Content-owned bridge between accepted AUTH-0093 geological opportunity and later
resource realization.

AUTH-0093 already supplies deterministic, backend-neutral Iron/Copper/Zinc geological opportunity
that is subordinate to exact mineral-bearing structural host support. It deliberately does not
assign gameplay rarity, bootstrap guarantees, deposit size, ore ids, or Minecraft placement.

C20 therefore owns only the gameplay semantics that existing Content audits already support.

## Fixed availability policy

| Metal | Availability | First-flight critical | Hard guarantee scope | Alternative access |
| --- | --- | ---: | --- | --- |
| Iron | COMMON_REGIONAL | yes | STARTING_CLUSTER | bounded trade/salvage |
| Copper | COMMON_REGIONAL | no | POST_FLIGHT_PROVINCE | bounded trade/salvage |
| Zinc | COMMON_REGIONAL | no | POST_FLIGHT_PROVINCE | bounded trade/salvage |

This keeps Iron reliable enough for bootstrap closure while preserving Copper/Zinc as immediate
post-flight regional engineering rewards.

## AUTH-0093 consumption rule

For one accepted AUTH-0093 island profile and one base metal:

```text
peakOpportunity == 0
    -> geologically ineligible

peakOpportunity > 0
    -> geologically eligible
    -> meanOpportunity may rank this candidate against other eligible candidates
```

C20 introduces no magnitude threshold beyond the accepted zero/nonzero geological boundary.

Availability class and guarantee scope are fixed gameplay policy. They do not become "rare",
"industrial", or "strategic" because one AUTH-0093 value is numerically larger or smaller.

## Guarantee behavior

A hard Content guarantee must be satisfied by selecting or re-planning an authored scope that
contains eligible AUTH-0093 geology.

It must **not** be satisfied by injecting a metal into a zero-opportunity island.

For Iron this means the Bootstrap planner must eventually prove an eligible source within the
starting-cluster closure.

For Copper and Zinc this means the post-flight starting province must provide a deliberately
findable eligible route for each metal without making either one a pre-flight dependency.

Trade/salvage may satisfy a hard guarantee only when that access path is itself guaranteed by the
world recipe. Probabilistic loot or a lucky settlement does not count.

## Quantity boundary

C20 does not claim deposit scale or exact material quantity.

The existing 48-64 accessible iron-ingot-equivalent band remains an engineering estimate from the
first-flight BOM. It must not become a worldgen constant until C11/C12 and the real aircraft closure
supply accepted executable demand.

Likewise, AUTH-0093 normalized opportunity must not be interpreted as:

- grade;
- reserves;
- deposit count;
- deposit volume;
- vein thickness;
- physical accessibility;
- industrial throughput.

The existing sample-versus-industrial-supply distinction remains open for a later resource
realization milestone.

## Implementation handoff

Implementation may consume C20 together with AUTH-0093 to realize retained Minecraft/mod resources.

Implementation still owns:

- ore/block/tag identity;
- deposit/vein geometry;
- counts, volume, and grade;
- physical accessibility;
- placement/worldgen;
- chunk lifecycle and persistence.

C20 adds no Minecraft or NeoForge ontology to the neutral engine.

## Acceptance

C20 is accepted when:

1. the executable policy maps all three AUTH-0093 metals exhaustively;
2. Iron/Copper/Zinc match the fixed Content matrix above;
3. geological eligibility is exactly AUTH-0093 nonzero opportunity;
4. ranking passes through unchanged AUTH-0093 mean opportunity;
5. policy does not classify availability from opportunity magnitude;
6. backend-independence checks remain green;
7. repository CI passes on the exact synchronized head.

## Accepted evidence

```text
Wave C20 Base Metal Content Policy: PASS (run 34158865011)
repository CI: PASS (run 34158865063)
```

Decision: **ACCEPT** the fixed backend-neutral availability/guarantee policy above. Concrete resource
identity selection is a Content integration follow-up; deposit geometry, quantity, accessibility,
placement, and lifecycle remain Implementation-owned. No manual Minecraft or visual gate applies to
C20 itself.
