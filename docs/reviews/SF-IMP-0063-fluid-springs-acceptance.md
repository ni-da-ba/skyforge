# SF-IMP-0063 — Exact-volume native fluid-springs acceptance

Status: **ACCEPTED**

Issue: #114  
Pull request: #115  
Accepted synchronized feature head: `bee35876a7eb69305521d76dacd90ba8a6ff850e`  
Merge commit: `0d6c51712746ab4ff256c97652d1768cb332026d`

## Objective

Extend the accepted exact-volume native Minecraft population architecture through
`GenerationStep.Decoration.FLUID_SPRINGS`, including the asynchronous vanilla fluid-tick path
that continues after native population scope has closed.

The milestone had to preserve vanilla spring realization inside an already-carved Skyforge volume
without allowing generated water/lava to escape into BASE_WORLD or a vertically stacked foreign
Skyforge volume.

## Accepted architecture

Initial spring placement remains registry-native:

```text
final-registry biome
  -> native FLUID_SPRINGS placed feature
  -> unchanged placement/RNG stream
  -> owner-local vertical frame
  -> exact-volume synchronous read/write fence
  -> water/lava placement
```

SF-IMP-0063 adds persistent provenance only to fluid ticks descended from an admitted native
Skyforge spring:

```text
Skyforge spring schedules fluid tick
  -> provenance stored in ServerLevel SavedData
  -> population scope closes
  -> later FlowingFluid tick begins
  -> originating volume is recovered
  -> exact compiled owner domain is reinstated
  -> exterior/foreign reads become impermeable
  -> exterior/foreign writes are rejected
  -> descendant schedules inherit provenance
```

Ordinary vanilla/player fluids receive no Skyforge provenance and remain untouched.

AUTH-0022 through AUTH-0025 geology/cave authorship remains semantically upstream. This milestone
does not convert groundwater or authored cave geometry into literal Minecraft spring policy.

## Final deterministic runtime evidence

The accepted exact head was synchronized with `main` through AUTH-0025 before the final run.

Two independent worlds reproduced:

- biome: `minecraft:dripstone_caves`
- attempted native spring features: 42
- successful native spring features: 7
- successful feature keys:
  - `minecraft:spring_water`
  - `minecraft:spring_lava`
- native spring height samples: 945
- mapped samples outside exact volume: 0
- initial tracked generated fluids: 9
- all 9 initial springs adjacent to actual persistent carved AIR
- final tracked generated fluids after 100 vanilla server ticks: 138
- matching persistent generated fluids: 138
- captured generated-fluid schedules: 325
- asynchronous generated-fluid propagation ticks: 256
- exact-boundary reads hidden as impermeable terrain: 1,661
- rejected boundary writes: 0
- scheduled descendants outside owner: 0
- unrelated vanilla-water control flowed normally and never acquired Skyforge provenance
- vertically unrelated BASE_WORLD proof columns preserved

Accepted deterministic invariants:

- prerequisite carver transform digest: `10d4f06df3d8814f`
- prerequisite carved-position digest: `9432af9ead2c865d`
- spring transform digest: `c8103b2012e79269`
- generated-fluid provenance digest: `2aa9b41371236b93`

Representative persistent generated fluid:

```text
BlockPos{x=-14, y=236, z=0}
minecraft:water[level=2]
```

## Full stop/reload persistence

The deterministic B world was stopped completely and reopened through the automated Quick Play
client path.

The reload mode restored only deterministic compiled Skyforge terrain ownership. It did not rerun:

- physical admission;
- carving;
- underground decoration;
- native spring population.

Saved generated-fluid provenance rehydrated successfully:

- persisted tracked positions after reload: 148
- fresh post-reload generated-fluid propagation ticks: 14
- post-reload provenance digest: `1d5c7834871894c3`
- server retained the expected sample fluid
- actual logical `ClientLevel` independently observed the same persisted fluid

This proves containment state is ordinary world persistence rather than an artifact of one population
thread.

## Stacked-volume isolation

The accepted same-X/Z stacked fixture mapped one identical native spring-frame probe independently:

- lower volume mapped Y: 124
- upper volume mapped Y: 224
- owner propagation accepted
- foreign-volume propagation rejected
- persisted provenance remained volume-specific

## Regression gates

Final synchronized exact-head acceptance reproduced:

### SF-IMP-0062 cave decoration
- decoration digest: `ce242ec84fb8ccfc`
- successful native decoration features: 33
- changed actual carved AIR positions: 2,031
- mapped outside volume: 0

### SF-IMP-0061 native carvers
- transform digest: `e97b5e7ee026c422`
- carve digest: `61f96a61f81c9b55`

### SF-IMP-0059 underground ores
- transform digest: `3397c516a115d6e4`

### SF-IMP-0060 local modifications
- transform digest: `4fe92d09d07f8002`

## Verification

Final synchronized workflow run: `33835912662`  
Acceptance artifact: `9923371373`  
Final normal CI run: `33835912630`

All automated acceptance and normal CI gates passed on the exact synchronized feature head.

No human-eye or manual Minecraft run was required.

## Architectural consequence

The exact-volume native interior pipeline now includes asynchronous fluid behavior:

```text
physical admission
  -> native carving
  -> persistent cave topology
  -> native underground decoration
  -> native fluid springs
  -> persistent volume-scoped vanilla fluid propagation
```

The remaining downstream work should keep backend containment separate from authorship policy for
groundwater, lakes, waterbodies, and authored cave realization.
