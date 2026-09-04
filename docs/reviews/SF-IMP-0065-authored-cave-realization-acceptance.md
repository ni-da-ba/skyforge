# SF-IMP-0065 — Sealed authored cave realization acceptance

Status: **ACCEPTED**

Issue: #124  
Pull request: #125  
Accepted synchronized feature head: `b61c852c5d2f396ca9fb063b0b9a4457f15d9f2a`  
Merge commit: `ea7a86fdd2505ca508fe5b4a711f841cc4e90ec9`

## Objective

Consume the merged AUTH-0026 continuous cave-volume field through the merged AUTH-0027
semantic-depth realization transform and materialize Skyforge-authored cave AIR inside one exact
Minecraft island volume.

This milestone deliberately realizes a **sealed** authored cave. AUTH-0028/0029 exterior exposure
semantics are not consumed by the accepted implementation.

## Accepted realization seam

```text
Minecraft BlockPos
  -> active SkyIslandWorldVolume
  -> world X/Z -> island-local X/Z
  -> authoritative compiled upper/underside column
  -> physical Y -> AUTH-0027 semantic depth
  -> AUTH-0026 cave occupancy
  -> positive?
  -> existing exact owner-solid / foreign-solid carver fence
  -> AIR
```

The NeoForge adapter does not copy or reinterpret chamber/passage geometry. Cave topology and
continuous volume remain backend-neutral authorship data.

Every positive authored sample is preflighted before target-chunk mutation. A positive sample
outside the owning compiled volume or inside a vertically stacked foreign volume vetoes the target
chunk before AIR writes.

No neighbor chunks are forced.

## Deterministic authored fixture

The accepted proof uses the AUTH-0026 isolated-chamber control:

- world seed: `0x534B59464F524745`
- province key: 8
- cluster key: 81
- island key: 1439
- morphology: TABLELAND
- cave systems: 1
- chambers: 1
- passages: 0

The authored field is paired explicitly with an authoritative schema-2 physical volume sharing the
same nominal radius and morphology. Fixture placement centers the authored chamber at Minecraft
X/Z=0.

This pairing is integration evidence only; it is not an authored-descriptor-to-physical-descriptor
policy.

## Final exact-head evidence

The accepted exact head was synchronized with `main` through AUTH-0031.

Two independent worlds reproduced:

- proof chunks: 16
- sampled physical blocks: 782,336
- positive AUTH-0026 samples: 27,379
- exact-owner-authorized samples: 27,379
- unsafe positive samples: 0
- persistent AIR changes: 27,379
- changed-position digest: `5e80ba344cffe29`
- authored primitive-provenance digest: `eabea7e356033e45`
- sealed: true
- BASE_WORLD preserved: true

Representative persisted authored cave sample:

```text
BlockPos{x=0, y=237, z=0}
minecraft:air
AUTH-0026 system = 0
AUTH-0026 primitive = CHAMBER:0
```

Outside-cave owner-solid control:

```text
BlockPos{x=35, y=238, z=0}
minecraft:deepslate[axis=y]
```

The outside-cave control remained unchanged.

## Full stop/reload persistence

The deterministic B world was stopped completely and reopened through the automated Quick Play
client path.

No terrain/admission/authored-cave mutation binding was installed during reload.

Evidence:

- server retained the expected authored cave AIR;
- server retained the expected outside-cave solid control;
- actual logical `ClientLevel` independently observed the same cave AIR;
- actual logical `ClientLevel` independently observed the same solid control.

## Stacked-volume isolation

The same authored cave field was realized independently through two different physical column
realizations at the same X/Z:

- lower chamber center Y: 134
- upper chamber center Y: 234
- lower changed AIR blocks: 1,875
- upper changed AIR blocks: 1,870
- lower unsafe positive samples: 0
- upper unsafe positive samples: 0
- lower realization did not modify upper terrain;
- upper realization preserved lower authored cave;
- same-X/Z independence: PASS.

The differing integer voxel counts are expected because AUTH-0027 maps one semantic cave through
different physical column shapes; semantic correspondence, not equal physical voxel cardinality, is
the invariant.

## Regression gates

Final synchronized acceptance reproduced:

- SF-IMP-0064 whole-lake admission digest: `9b568d83c71c5d04`
- SF-IMP-0063 spring transform digest: `c8103b2012e79269`
- SF-IMP-0062 decoration digest: `ce242ec84fb8ccfc`
- SF-IMP-0061 native-carver transform digest: `e97b5e7ee026c422`
- SF-IMP-0059 ore transform digest: `3397c516a115d6e4`
- SF-IMP-0060 local-modification transform digest: `4fe92d09d07f8002`

## Verification

Final synchronized workflow run: `33892565183`  
Acceptance artifact: `9944734369`  
Final normal CI run: `33892565359`

All automated acceptance and normal CI gates passed on the exact synchronized feature head.

No human-eye or manual Minecraft run was required.

## Architectural consequence

Skyforge now has a proven end-to-end authored cave path from backend-neutral cave semantics to
persistent Minecraft AIR while retaining exact-volume isolation:

```text
authored cave topology/continuous volume
  -> semantic-depth physical realization
  -> exact-volume Minecraft AIR
  -> save/reload persistence
  -> actual ClientLevel persistence
```

Exterior-connected cave geometry, authored subsurface materials, and the final precedence policy
between authored caves and registry-native cave carvers remain downstream concerns.
