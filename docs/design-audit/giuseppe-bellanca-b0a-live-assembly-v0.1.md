# Giuseppe Bellanca B0-A Live Assembly Evidence v0.1

**Parent:** CONTENT C12 / issue #239  
**Status:** Candidate; live workflow pending.  
**Historical design input:** closed/unmerged PR #242.

## Scope

This slice owns B0-A1 assembly integrity and B0-A2 live mass/center-of-mass only.

It deliberately does **not** yet own:

- PROP_CHILD preassembly/reassembly;
- lift sign;
- single/dual-engine stress cooperation;
- governed propeller RPM;
- control surfaces;
- landing gear;
- taxi/flight;
- power-off glide/restart;
- production silhouette.

## Exact specimen

The fixture recomposes the historical B0-A MAIN_BODY as 105 unique block positions:

- 66 regular Create sails;
- 15-block spruce wing carry-through;
- 12-block spruce keel;
- two-block center pylon;
- two engine mounts;
- two Portable Engines;
- one Rotation Speed Controller;
- one large cog;
- two prop shafts;
- one Propeller Bearing controller block;
- one Physics Assembler.

The historical `physics_assembler_support` role aliases the existing keel position `(0,1,+2)`,
which is why 106 semantic role entries resolve to 105 unique blocks.

The exact four MAIN_BODY Super Glue domains from PR #242 are materialized as real Create
`SuperGlueEntity` volumes.

## Required live proof

The dedicated-server specimen must:

1. activate the actual Simulated Physics Assembler through its `assembleOrDisassemble()` path;
2. create exactly one new Sable `ServerSubLevel`;
3. prove every one of the 105 source positions is vacated;
4. derive Sable's pure integer source->plot translation from live COM/pose state;
5. prove all 105 expected blocks and relevant block states exist at their translated positions;
6. prove no additional block occupies the translated MAIN_BODY bounding volume;
7. read Sable's live merged mass tracker;
8. record local X/Y/Z COM and longitudinal offset from the wing geometric center;
9. stop for explicit review if bare MAIN_BODY mass exceeds 60 kpg.

The prior 38.5-kpg paper MAIN_BODY estimate and paper COM are comparison hypotheses only.
