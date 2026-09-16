# Skyforge Structure + Functional Asset Compiler state

**Completed structure proof authority:** issue #488  
**Accepted compact-Hall tranche:** PR #612 / merge `0eca2194ddf4a7e3be401e5715a32e60525f315e`  
**Accepted sibling/ROI tranche:** PR #623 / merge `20c3b9e37e16800eb258d1613714b90d1ec9c048`  
**Accepted functional-asset authority:** issue #628 (`MECH-001`) / PR #640 / merge `c7c23e0d2f3abbb88db09e9b1feaf0d9d005209c`
**Accepted natural-power authority:** issue #679 (`MECH-002`) / PR #706 / merge `c82f9efcf05716880b32b68dc81dc7ce55c91541`
**Shared runtime qualification authority:** issue #613 / `docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json`

This ledger records current production capability for Compiler Program Agent C. Historical
`wby-structure-*`, `asset-compiler-proof`, and draft PR #489 material is evidence/source material only;
it must not be resumed, rebased into `main`, or merged wholesale.

## Productionized by completed issue #488

Current `main` contains a bounded structure-compiler path under `tools/asset_compiler/**` that can:

- consume a compact semantic Guild-branch specification rather than raw block enumeration;
- solve first-principles Guild Hall/branch geometry deterministically;
- lower the solved design to semantic voxel/block-state intent;
- validate circulation, support, attachments, doors, apertures, target legality, and deterministic identity;
- emit inspectable QA artifacts and exact Minecraft structure NBT;
- realize the accepted target through the current Minecraft adapter without using a broad generic-CAD or universal-architect abstraction;
- regenerate a materially larger sibling from the same architectural/module-family vocabulary.

The accepted compact Hall is the neutral-palette v0.14 first-principles detail specimen:

`tools/asset_compiler/specimens/bootstrap_guild_branch_v0.14_first_principles_detail.json`

The accepted working-heavy sibling is:

`tools/asset_compiler/specimens/bootstrap_guild_branch_v0.14_sibling_working_heavy.json`

## Human-accepted compact Hall behavior

Minecraft review established that the compact Hall reads as a sane Guild/clerical/institutional
building with a strong roof and clear public/working character. The gate also drove the following
source-level corrections before acceptance:

- removed the calcite repair/warehouse divider so the working wing remains open-plan;
- removed useless high glazing above both working/service bay doors;
- cleared the decorative entrance plinth/slab treatment across the customer threshold and shoulders;
- removed provisional blue/yellow Guild identity colors and the temporary blue rug;
- retained semantic identity hooks while deferring final Guild color/material representation;
- removed/remounted unsupported detail instead of teaching the target adapter to waive floating geometry.

The accepted palette is intentionally neutral. No blue/yellow Guild color scheme is production canon
until a later identity/material authority establishes one.

## Stage 7 sibling / ROI proof

PR #623 proved one contrasting larger Guild branch with no new module-family vocabulary. The accepted
sibling resolves to:

- 5 bays at 5-block bay width;
- 26-block public Hall width;
- 18-block Hall depth;
- 9-block working wing;
- 3,306 architecture / realization-intent cells;
- 3,344 realized Minecraft cells;
- deterministic digest `cf4ca96b71d01545a692173ca4ca02e448eaebc9aa0f896f7e76c84afd09d902`;
- bounds `[38, 15, 21]`.

Generated ROI evidence records:

- configuration-field reuse: 91.566% (76 unchanged of 83 union fields);
- exact module-ID reuse: 88.462%;
- normalized module-family reuse: 100%;
- sibling-only module families: 0;
- realized-cell leverage: 836 cells per changed semantic design field;
- sibling realized size: about 1.79x the accepted compact Hall.

The lower exact module-ID overlap is expected: the larger facade creates additional deterministic
per-window instance IDs. Normalizing only trailing instance indices shows that these are repeated
productions, not new architectural rules.

## Generality defect found by the sibling

The larger wing exposed one legitimate reusable compiler defect: `fp_repair_service_shelf` was only
coincidentally connected in the compact Hall and became floating when the working wing widened.

The accepted source fix mounts that shelf directly against the retained public/working separator and
adds a support regression test. No adapter waiver or new vocabulary was added.

This is the important Stage 7 result: the sibling found one scale-safety bug, the bug was repaired in
the reusable source layer, and the same compiler then produced a coherent larger branch.

## Human-accepted sibling behavior

Minecraft review confirmed that the sibling clearly reads as a larger Guild branch and has no
material structural/scale regression. The following are **non-blocking deferred refinement notes**:

1. the larger branch is somewhat empty and can benefit from a later furnishing/flavor pass;
2. the small high wall apertures near the roof read well, but a later architectural/material pass may
   test glass infill while preserving their useful shape and placement.

These notes do not reopen #488 and must not be used to invent final Guild furnishing, identity, or
material canon without the appropriate product/content authority.

## Frozen evidence / non-authority

The old WBY structure branches and draft PR #489 remain useful evidence for implementation history,
but current work must start from accepted `main`. Do not reactivate catalog-only resources or hidden
Create vocabulary merely because it existed in frozen proof branches.

Likewise, the accepted static structure compiler does **not** authorize:

- final settlement/civilization placement policy;
- production-geography accommodation or dressed-region lifecycle work;
- final Guild colors, symbols, furnishing density, or furniture vocabulary;
- arbitrary Create mechanism resources;
- mobile/Sable contraption behavior;
- aircraft work.

## Explicit ownership boundary: issue #493

Issue #493 owns production-geography structure reintegration, admission, accommodation, persistence,
mutation handling, and dressed-region physical placement. Agent C must not use the structure compiler
to seize that controller/Implementation roadmap.

The structure compiler may provide authored compiled assets to downstream placement systems when a
consumer contract exists, but it does not choose semantic sites, settlement layouts, or final world
placement policy.

## Integration Platform dependency

Reusable Create/Sable/Simulated runtime seams are owned by issue #613. Agent C may consume a runtime
seam only when `docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json` on accepted `main` marks that
capability `accepted` and grants Agent C production authority.

`CREATE_KINETIC_NETWORK_LIFECYCLE` is accepted at L2 with Agent C production authority. Its bounded
fixed-world contract qualifies Create kinetic source -> relay -> endpoint initialization, nonzero speed
propagation, deterministic sever/inactivity, and rebuild/recovery on the exact retained C11 stack.

`SABLE_PRIMARY_ASSEMBLY_LIFECYCLE` is separately accepted, but is not required for fixed-world MECH-001.
Moving-body Create kinetics remain a separate platform capability and are not implied here.

`CREATE_WATER_WHEEL_SOURCE_LIFECYCLE` is accepted at L2 with Agent C production authority through
PLATFORM-009 / #697 / PR #702. Its bounded contract qualifies the fixed-world small Create water wheel,
the accepted north falling-water drive position, signed 8 RPM generation, environmental disable, and
recovery on the retained C11 stack. It does not authorize belts/item transport, Sable/mobile machinery,
aircraft, production geography, or general hydrology policy.

## Accepted functional tranche: MECH-001 / issue #628

PR #640 accepted one deliberately bounded fixed-world airflow utility bench:

`qualified_kinetic_source -> required axial relay -> airflow endpoint`

The semantic specimen remains target-neutral. Exact C11 target lowering is:

- `create:creative_motor[facing=east]` as a qualified test source only;
- `create:shaft[axis=x]` as the compiler-designated required relay/sever node;
- `create:encased_fan[facing=east]` as the useful downstream airflow endpoint;
- a 3x3 stone-brick mounting/support pad;
- a three-block unobstructed discharge path.

The Creative Motor is explicitly **not gameplay/power-source canon**. Water-driven generation and
conveyor/item transport remain later seams rather than being silently activated by this proof.

Canonical MECH-001 identity:

- compiler version `mech-0.1-fixed-world`;
- plan digest `da905478f5f3e42e197bd0ef1a38baba848c8ce65c2a911d7153743597098ce1`;
- 12 placements;
- deterministic Minecraft structure SHA256
  `dee1563daa925034f13a9481744bbf2da9b0bb36937ae8cb6a7192a1b45e9b84`;
- structure resource `skyforge:mech_001_airflow_bench` in the development-only local-run data pack.

### Structure-native gate correction

The first human-gate instructions reconstructed the mechanism with manual `/setblock` commands under
an older Wave-C1 client profile. Local review reported `unknown block type`; that gate was therefore
invalid as a reproduction path and does **not** count as a mechanism visual/function failure.

PR #640 head `3a91b558a7bfa2812af4c8cf5681622ed9187ed0` corrects the reproduction boundary:

- the compiler emits the exact Minecraft structure NBT;
- CI byte-compares regenerated NBT against the committed development structure;
- the L3 fixture itself invokes Minecraft `place template skyforge:mech_001_airflow_bench ...` and
  verifies every resulting target block state against the compiled plan before kinetic observation;
- the human gate has a dedicated `mech001FunctionalMechanismClient` using the same C11 runtime source
  set as the accepted platform/runtime proof.

Final acceptance evidence:

- conflict-resolved authority head `50554946e5fa598a36f84c3eb85e54e35c61380d`;
- MECH-001 pull-request run `35010817569`: L1 PASS and L3 exact-stack PASS;
- structure generation and exact byte comparison PASS;
- Minecraft structure-template placement and state verification PASS;
- fan initial speed `16.0` with source/network state;
- relay sever observed with downstream stop/source loss;
- relay restore recovered fan speed `16.0`, source speed `16.0`, and source/network state;
- all three compiled airflow-clearance cells remained clear;
- full repository CI run `35010817444`: PASS;
- affected Create/Sable/platform, actual-client, aircraft powertrain, C11/C12, persistence/cutoff, and asset-compiler regressions: PASS;
- local Minecraft structure-native human gate: **PASS** for readability, support/clearance, visible operation, sever/restore behavior, and practical editability;
- PR #640 merged as `c7c23e0d2f3abbb88db09e9b1feaf0d9d005209c`.

## Compiler maintenance completed while MECH-002 was blocked

Three bounded common-hardening tranches were completed without changing the accepted MECH-001 plan
identity, Minecraft structure bytes, runtime behavior, or mechanism vocabulary:

- MECH-COMMON-001 / #681 / PR #685 / merge `da369dc35106aaff59f2897bf101cf879244e5e9` separated common semantic validation, Agent C platform-authority validation, and MECH-001 target lowering; it also hardened structure export against malformed geometry, graph, support, and clearance references.
- MECH-COMMON-002 / #689 / PR #690 / merge `c7be546e6b20163841f9ee270d8e261926655064` added fail-closed scalar/resource/role checks and requires plan digest integrity before structure export, preventing post-compile plan mutation from silently becoming different NBT.
- MECH-COMMON-003 / #693 / PR #696 / merge `6928524c1710d9b067ab21fd27f5962d860dd42e` derives structure output as `<spec-stem>.nbt` instead of hard-coding the MECH-001 filename; the canonical specimen still emits `mech_001_airflow_bench.nbt`.

Across these maintenance tranches, the accepted MECH-001 plan digest remained
`da905478f5f3e42e197bd0ef1a38baba848c8ce65c2a911d7153743597098ce1` and the accepted structure
SHA256 remained `dee1563daa925034f13a9481744bbf2da9b0bb36937ae8cb6a7192a1b45e9b84`. No human gate was required
because the emitted canonical plan and structure remained byte-identical.

## Accepted functional tranche: MECH-002 / issue #679

PR #706 accepted one bounded natural stationary workshop-power specimen:

`renewable_stationary_source -> required axial relay -> airflow endpoint`

Exact target realization is a contained source-fed falling-water chute driving
`create:water_wheel[facing=east] -> create:shaft[axis=x] -> create:encased_fan[facing=east]`. The
semantic specimen remains target-neutral; exact Create/vanilla states are introduced only by compiler
lowering under accepted PLATFORM-009 authority.

Canonical MECH-002 identity:

- compiler version `mech-0.2-fixed-world`;
- plan digest `6a8834d2c2d18cd0914fc488807d8e4042eb4a12f8fe11258a94d931c400f293`;
- 37 placements;
- envelope `[7, 4, 5]`;
- deterministic Minecraft structure SHA256 `180d02ed902704f6cd31b88843c7047cc075a15d94d77c9c5e7c5b30471f20cd`;
- structure resource `skyforge:mech_002_waterwheel_airflow_bench`.

The first human gate exposed a real authored-geometry defect: the compiler emitted a standalone
`minecraft:water[level=8]` falling-water cell. Create consumed it correctly, but normal vanilla fluid
ticking removed the un-fed transient cell. This did **not** invalidate PLATFORM-009. Agent C corrected
the asset by adding a contained persistent source-water feeder above the accepted drive cell. The
unnecessary follow-up PLATFORM-010 / #730 was closed not-planned.

The corrected L3 proof never directly repairs the falling drive cell. It requires the source-fed
mechanism to remain continuously active for 100 ordinary ticks, removes only the feeder source, observes
natural drain plus source/fan stop, restores only the feeder, and observes natural falling-water
reformation plus recovery. Exact-stack run `35138536017`, L3 job `104939179844` passed with initial and
recovered source/fan speed `-8.0`, flow vector `(0.0, -1.0, 0.0)`, live source/network state, and clear
compiled moving/discharge cells. Full repository CI and all triggered retained runtime/client regressions
passed on exact head `65503466684463486ed1a462105d2ec6ad256249`.

Local Minecraft review of that exact corrected head gave **FULL PASS**: water persisted under ordinary
ticks; removing only the feeder naturally stopped the mechanism; restoring only the feeder naturally
reformed the falling drive and restarted the mechanism; mounting/containment/readability were accepted.
PR #706 merged as `c82f9efcf05716880b32b68dc81dc7ce55c91541`; issue #679 closed completed.

This proves water-wheel power is a credible stationary workshop option. It does **not** require
water-wheel geography for every workshop and does not activate belts, item transport, or sequenced
assembly. Those remain separate capability/design decisions.

## Current stop state

Structure proof / #488: **ACCEPTED / COMPLETE**.
MECH-001 / #628 / PR #640: **ACCEPTED / MERGED**.
MECH-002 / #679 / PR #706: **ACCEPTED / MERGED**.
PLATFORM-009 / #697 / PR #702: **ACCEPTED** with Agent C water-wheel source authority.
No human action is pending. Belt/item transport and sequenced assembly remain unactivated separate seams.
No production-geography authority is changed and #493 remains untouched.
