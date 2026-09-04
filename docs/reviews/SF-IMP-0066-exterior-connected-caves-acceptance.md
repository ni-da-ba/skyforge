# SF-IMP-0066 — AUTH-0030 exterior-connected authored cave acceptance

Status: **ACCEPTED**

Issue: #129  
Pull request: #130  
Accepted feature head: `aaa76aafb31451a7d41ad003aa0fe2dbff682de1`  
Merge commit: `141a56daf3dc2903cfbae017dae48337bd574619`

## Objective

Extend the accepted SF-IMP-0065 authored-cave Minecraft realization seam from sealed AUTH-0026 cave volume to the merged AUTH-0030 exterior-connected cave-volume union.

The implementation consumes backend-neutral AUTH-0030 semantics through AUTH-0027 physical realization without copying or reauthoring cave-mouth geometry in the NeoForge adapter.

## Accepted realization seam

```text
AUTH-0030 exterior-connected cave volume
  -> AUTH-0027 semantic-depth physical realization
  -> Minecraft BlockPos / island-local XZ
  -> BASE_CAVE or EXPOSURE_CONNECTION provenance
  -> exact owner-solid / foreign-solid preflight
  -> existing SkyforgeCarverExecutionStage
  -> AIR
```

An authored mouth is recognized only when an EXPOSURE_CONNECTION-positive exact-owner cell has an outward neighbor on the accepted exposure side that is neither owner-solid nor foreign-solid. The outward exterior cell is inspected but never modified.

Any positive AUTH-0030 sample that cannot be represented by exact owner-solid terrain vetoes the target chunk before mutation.

No neighbor chunks are forced.

## Deterministic representative

The accepted canonical AUTH-0030 representative is:

- island key: 653
- morphology: BASIN
- exposure side: UNDERSIDE
- proof chunks: 16

Two independent worlds reproduced:

- positive samples: 89,068
- BASE_CAVE samples: 78,030
- EXPOSURE_CONNECTION samples: 11,038
- upper-surface exposure samples: 0
- underside exposure samples: 11,038
- unsafe positive samples: 0
- mouth cells: 663
- persistent AIR changes: 89,068

Accepted deterministic invariants:

- changed-position digest: `f97a685cce4bd5e4`
- AUTH-0030 provenance digest: `3032a41620c93935`

Representative authored exterior connection:

```text
mouth owner cell:
BlockPos{x=-14, y=174, z=-3}
minecraft:air

outward underside exterior:
BlockPos{x=-14, y=173, z=-3}
minecraft:air

connected BASE_CAVE sample:
BlockPos{x=-14, y=185, z=-3}
minecraft:air
```

The mouth AIR component visited 673 authored cells before reaching BASE_CAVE provenance.

The accepted underside specimen produced zero upper-surface EXPOSURE_CONNECTION samples.

## Full stop/reload persistence

The deterministic B world was stopped completely and reopened through the automated Quick Play client path.

No Skyforge terrain/admission/exterior-cave mutation binding was installed during reload.

Evidence:

- persisted mouth AIR: PASS
- outward exterior AIR: PASS
- connected BASE_CAVE AIR: PASS
- actual logical `ClientLevel` independently observed all three states: PASS

## Stacked-volume isolation

The same AUTH-0030 semantic exterior connection was realized independently through two vertically stacked physical volume shapes:

- lower mouth: `BlockPos{x=-14, y=104, z=-4}`
- upper mouth: `BlockPos{x=-14, y=204, z=-6}`
- lower changed blocks: 42,980
- upper changed blocks: 42,815
- lower exposure samples: 5,326
- upper exposure samples: 5,350
- lower unsafe samples: 0
- upper unsafe samples: 0
- horizontal discrete mouth offset: 2 blocks
- both mouths resample to the same accepted AUTH-0030 EXPOSURE_CONNECTION side/provenance
- lower realization preserved upper terrain before upper realization
- upper realization preserved the lower mouth
- foreign-volume isolation: PASS

Different physical column shapes need not choose the identical discrete mouth voxel. The accepted invariant is semantic AUTH-0030 exposure correspondence plus exact-volume isolation.

## Regression gates

Final exact-head acceptance reproduced:

- SF-IMP-0065 changed digest: `5e80ba344cffe29`
- SF-IMP-0064 admission digest: `9b568d83c71c5d04`
- SF-IMP-0063 spring transform digest: `c8103b2012e79269`
- SF-IMP-0062 decoration digest: `ce242ec84fb8ccfc`
- SF-IMP-0061 native-carver transform digest: `e97b5e7ee026c422`
- SF-IMP-0059 ore transform digest: `3397c516a115d6e4`
- SF-IMP-0060 local-modification transform digest: `4fe92d09d07f8002`

## Verification

Final acceptance workflow: `33897845175`  
Acceptance artifact: `9946819286`  
Final normal CI run: `33897845163`

All automated acceptance and normal CI gates passed on the exact accepted feature head.

No human-eye or manual Minecraft run was required.

## Architectural consequence

Skyforge now has a proven authored cave path that reaches the actual island exterior without weakening exact-volume ownership:

```text
authored cave topology
  -> continuous cave volume
  -> authored exposure connection
  -> exterior-connected AUTH-0030 union
  -> semantic-depth physical realization
  -> exact-volume Minecraft AIR
  -> exterior adjacency
  -> save/reload persistence
  -> actual ClientLevel persistence
```

Authored subsurface material semantics and final authored/native cave precedence remain downstream concerns.
