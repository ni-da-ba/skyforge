# SF-IMP-0067 — Native/authored cave precedence acceptance

Status: **ACCEPTED**

Issue: #131  
Pull request: #132  
Accepted synchronized feature head: `4bc439babfab9be87af8045b0aa1441bd3daa542`  
Merge commit: `fe29e0ed258c18875d0dda2ade29f8d592df9fc2`

## Objective

Compose the accepted registry-native AIR-carver path from SF-IMP-0061 with the accepted AUTH-0030 exterior-connected authored cave realization from SF-IMP-0066 inside one exact Skyforge volume.

The accepted ordering contract is:

```text
exact physical Skyforge terrain
  -> final-registry native AIR carvers
  -> AUTH-0030 authored exterior-connected cave realization
  -> final cave AIR
```

Native cave carving remains additive. AUTH-0030 is the guaranteed final minimum.

Equivalently:

```text
final cave AIR = native-carver AIR union authored AUTH-0030 AIR
```

## Production seam

`SkyforgeComposedCaveRealizer` is a narrow coordinator. It:

1. invokes the existing SF-IMP-0061 registry-native carver runner;
2. invokes the existing SF-IMP-0066 AUTH-0030 realizer;
3. returns both result records.

It copies neither vanilla carver definitions nor authored cave geometry.

The authored-last pass never refills native AIR. It only guarantees that every representable AUTH-0030-positive cell is AIR in the final topology.

## Deterministic representative

The accepted proof uses the AUTH-0030 BASIN representative from SF-IMP-0066:

- island key: 653
- native biome for registry carvers: `minecraft:taiga`
- composed attempts: 1
- selected native chunk: `[-2,-2]`

Two independent worlds reproduced:

### Native contribution

- native changed blocks: 1,400
- successful native carver calls: 73
- native-only final AIR outside AUTH-0030: 584
- cells opened by native carving that are also AUTH-0030-positive: 582
- rejected native writes: 0
- native mapped-outside-target samples: 0

Native deterministic invariants:

- transform digest: `95c046280c7f1c11`
- carve digest: `c277e3af5030dd01`

Representative native-only AIR:

```text
BlockPos{x=-32, y=230, z=-25}
minecraft:air
```

### Authored contribution

- AUTH-0030 positive samples: 89,068
- BASE_CAVE samples: 78,030
- EXPOSURE_CONNECTION samples: 11,038
- unsafe authored samples: 0
- blocks changed by authored-last pass: 88,486
- final AUTH-0030-positive AIR: 89,068

The difference between positive authored samples and authored-pass changes is the 582-cell native/authored overlap. Native carving had already opened those cells.

Authored deterministic invariants:

- changed digest: `6d2120967a6c73bd`
- provenance digest: `3032a41620c93935`

Composed digest:

- `911b02f4fe5b0518`

Representative authored connection:

```text
mouth:
BlockPos{x=-14, y=174, z=-3}
minecraft:air

outward exterior:
BlockPos{x=-14, y=173, z=-3}
minecraft:air

connected BASE_CAVE:
BlockPos{x=-14, y=185, z=-3}
minecraft:air
```

BASE_WORLD controls remained unchanged.

## Full stop/reload persistence

The deterministic B world was stopped completely and reopened through the automated Quick Play client path.

No Skyforge terrain/admission/cave mutation binding was installed during reload.

Evidence:

- native-only AIR persisted;
- authored mouth AIR persisted;
- outward exterior AIR persisted;
- connected BASE_CAVE AIR persisted;
- actual logical `ClientLevel` independently observed all four states.

## Stacked-volume isolation

The same native-first/authored-last ordering was exercised against two vertically stacked physical volumes in target chunk `[-2,-2]`.

Accepted evidence:

- lower native changed blocks: 1,224
- upper native changed blocks: 1,194
- lower native-only AIR: 859
- upper native-only AIR: 839
- lower authored-positive cells: 1,999
- upper authored-positive cells: 1,983
- lower discrete authored anchor: `BlockPos{x=-32, y=121, z=-32}`
- upper discrete authored anchor: `BlockPos{x=-32, y=221, z=-32}`
- unsafe lower samples: 0
- unsafe upper samples: 0
- lower realization left upper authored-positive terrain solid before the upper pass;
- upper realization preserved the lower native/authored union;
- foreign-volume isolation: PASS.

The proof intentionally selects actual discrete AUTH-0030-positive cells rather than rounding a continuous authored control point.

## Regression gates

Final synchronized acceptance reproduced:

- SF-IMP-0066 exterior authored cave digest: `f97a685cce4bd5e4`
- SF-IMP-0065 sealed authored cave digest: `5e80ba344cffe29`
- SF-IMP-0064 native lake admission digest: `9b568d83c71c5d04`
- SF-IMP-0063 spring transform digest: `c8103b2012e79269`
- SF-IMP-0062 decoration digest: `ce242ec84fb8ccfc`
- SF-IMP-0061 native-carver transform digest: `e97b5e7ee026c422`
- SF-IMP-0059 ore transform digest: `3397c516a115d6e4`
- SF-IMP-0060 local-modification transform digest: `4fe92d09d07f8002`

## Verification

Accepted head includes `main` through AUTH-0033.

Final acceptance workflow: `33907132953`  
Acceptance artifact: `9950314900`  
Final normal CI: `33907133160`

All automated acceptance and normal CI gates passed on the exact synchronized feature head.

AUTH-0034 was open but unmerged at acceptance and is not part of the milestone.

No human-eye or manual Minecraft run was required.

## Architectural consequence

Skyforge now has a proven precedence policy between native and authored cave generation:

```text
registry-native variation
       +
authored required topology
       ↓
persistent exact-volume cave union
       ↓
save/reload
       ↓
actual ClientLevel
```

Authored cave topology can therefore coexist with vanilla cave variation without surrendering the guaranteed authored connectivity and without requiring a feature-ID allowlist or copied Minecraft carver definitions.

Material-domain realization remains downstream.
