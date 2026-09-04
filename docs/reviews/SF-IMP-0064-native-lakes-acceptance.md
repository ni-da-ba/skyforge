# SF-IMP-0064 — Whole-footprint native LAKES acceptance

Status: **ACCEPTED**

Issue: #119  
Pull request: #120  
Accepted synchronized feature head: `6183976b77dbe442132a02de459661a9ac86b359`  
Merge commit: `2702977af4ac9501c5080d3c936837aa7ac4a0a2`

## Objective

Extend the accepted exact-volume native Minecraft population architecture through
`GenerationStep.Decoration.LAKES` without accepting a partially clipped lake.

The milestone had to preserve native LakeFeature placement and later fluid behavior while proving
that an unsafe whole-feature footprint is rejected before LakeFeature performs its first mutation.

## Accepted architecture

```text
final-registry LAKES placed feature
  -> unchanged native placement / RNG
  -> exact local vertical frame
  -> LakeFeature configured-feature-class capability gate
  -> exact 1.21.1 16 x 8 x 16 whole-footprint owner preflight
  -> ACCEPT: native LakeFeature executes unchanged
     or
     REJECT: feature is cancelled at HEAD before mutation
  -> accepted lake fluids reuse SF-IMP-0063 persistent provenance
```

Unknown/custom configured feature classes in the LAKES phase remain fail-closed until they expose
an equivalent bounded-footprint contract. No registry-ID allowlist or copied vanilla feature
definition is used.

## Final deterministic runtime evidence

The accepted exact head was synchronized with `main` through AUTH-0028 before the final run.

Two independent worlds reproduced:

- biome: `minecraft:river`
- placed-feature attempts: 42
- native configured LakeFeature attempts reaching the gate: 1
- successful native lakes: 1
- successful feature: `minecraft:lake_lava_underground`
- admitted whole lakes: 1
- deterministic edge rejection probe: rejected before mutation
- rejected-probe changed blocks: 0
- unsupported LAKES feature classes: 0
- native lake changed blocks: 340
- changed blocks outside admitted footprint: 0
- native height samples: 3
- mapped height samples outside exact volume: 0
- initial tracked generated-lake fluids: 56
- final tracked generated-lake fluids: 56
- matching persistent fluids: 56
- generated-lake propagation ticks: 56
- generated-fluid boundary writes rejected: 0
- scheduled descendants outside owner: 0
- stable BASE_WORLD proof blocks preserved

Accepted deterministic invariants:

- whole-lake admission digest: `9b568d83c71c5d04`
- lake vertical transform digest: `13c87b04bebea8ea`
- generated-fluid provenance digest: `f35dcb47fa1a38ef`

Representative persistent native lake fluid:

```text
BlockPos{x=1, y=237, z=5}
minecraft:lava[level=0]
```

## Full stop/reload persistence

The deterministic B lake world was stopped completely and reopened through the automated Quick
Play client path.

The reload verifier restored only deterministic compiled terrain ownership. It did not rerun native
LAKES placement or physical admission.

Evidence:

- persisted tracked lake-fluid positions: 56
- fresh post-reload generated-fluid propagation ticks: 1
- persisted provenance digest: `f35dcb47fa1a38ef`
- server retained the expected native lava sample
- actual logical `ClientLevel` independently observed the same persisted native lake state

## Stacked-volume isolation

The same-X/Z stacked fixture independently found whole-footprint-valid lake origins in both exact
volumes:

- lower origin Y: 122
- upper origin Y: 222
- owner whole-footprint admission: PASS
- foreign-volume whole-footprint rejection: PASS
- provenance volume isolation: PASS
- same-X/Z independence: PASS

## Regression gates

Final synchronized exact-head acceptance reproduced:

- SF-IMP-0063 spring transform digest: `c8103b2012e79269`
- SF-IMP-0062 decoration digest: `ce242ec84fb8ccfc`
- SF-IMP-0061 transform digest: `e97b5e7ee026c422`
- SF-IMP-0059 transform digest: `3397c516a115d6e4`
- SF-IMP-0060 transform digest: `4fe92d09d07f8002`

## Verification

Final synchronized workflow run: `33883170328`  
Acceptance artifact: `9940947791`  
Final normal CI run: `33883170304`

All automated acceptance and normal CI gates passed on the exact synchronized feature head.

No human-eye or manual Minecraft run was required.

## Architectural consequence

The exact-volume native interior pipeline now includes finite native lakes in addition to spring
placement and persistent generated-fluid propagation:

```text
physical admission
  -> native carving
  -> persistent cave topology
  -> native underground decoration
  -> native fluid springs
  -> native whole-footprint lakes
  -> persistent volume-scoped vanilla fluid propagation
```

Authored hydrology, groundwater policy, and cave-exposure authorship remain separate upstream
semantics and were not collapsed into native lake policy by this milestone.
