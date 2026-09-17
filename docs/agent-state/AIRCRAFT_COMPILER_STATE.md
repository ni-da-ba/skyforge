# Skyforge Aircraft Compiler state

**Program authority:** issue #545  
**Productionization boundary:** AIRCRAFT-RUNTIME-002 / issue #674
**Frozen evidence only:** `aircraft-compiler-proof@0651bcee7dc879ab43db2f0b224f77e5026222bd`  
**Frozen implementation beneath checkpoint:** `cc020c872b9c53198cebbb0ae9094097f23c9c45`

This ledger records production capability only. The frozen AIRCRAFT-001 branch is evidence/source
material and must not be resumed, rebased into `main`, or merged wholesale.

## Productionized by AIRCRAFT-PROD-001

The current production foundation is backend-neutral and contains:

- `AircraftDesignSpec`, the finite mission/design-domain contract;
- `AircraftAnalyticalMath`, the accepted first-principles analytical primitives;
- `AircraftDesignCompiler`, deterministic exhaustive lexicographic constrained search;
- `AircraftDesignIR`, separating resolved geometry, mass/balance, metrics, solver evidence,
  validation scope, and the unapplied target boundary;
- `AircraftDesignIRJson` plus SHA-256 identity over deterministic production JSON;
- the Guild utility-monoplane v0.1 regression preserving the accepted analytical selection,
  rejection accounting, and equation-derived metrics.

The historical frozen v0.1 digest
`ac04df8a723f83a8ee0d19e01c0a375c34acccc2f33a5875b8efce47c985881c` remains provenance for the
Python prototype artifact. Production Java does not claim byte-for-byte serialization compatibility
with that prototype; its canonical JSON/SHA-256 identity is the production provenance boundary for
new downstream artifacts.

## Productionized by AIRCRAFT-PROD-002

On acceptance of issue #618, the production compiler also contains:

- explicit artifact identity plus exact source-design asset/digest provenance for blockspace;
- deterministic `x = nose_to_tail`, `y = up`, `z = starboard_positive` integer-lattice transcription;
- straight-taper scanline wing/tail surfaces, centerline fuselage spine, and orthogonal-only connectors;
- target-neutral propeller, CG, pilot, and cargo anchors as requirements rather than occupied cells;
- fail-closed six-neighbor connectivity, mirror-symmetry, propeller-clearance, CG-quantization, and dimensional-error validation;
- coordinate-unique semantic assembly planning that unions roles/capabilities without inventing target resources;
- canonical production JSON/SHA-256 identity for both blockspace and assembly artifacts.

## Productionized by AIRCRAFT-PROD-003

On acceptance of issue #632, the aircraft compiler also has a backend-owned static exact-stack target
preflight layer over production `AircraftAssemblyPlanIR`:

- exact retained Minecraft/NeoForge/Create/Sable/Aeronautics stack identity is explicit;
- coordinate-unique assembly sites lower to one target provider by semantic specificity first, with
  provider priority used only as an equal-specificity tie-breaker;
- concrete target resource IDs remain confined to the NeoForge backend rather than entering
  `skyforge-model`;
- semantic stations remain explicit target contracts and are not converted into fake occupied sites;
- unresolved blockstate rules, companion requirements, required-station providers, and runtime
  obligations remain fail-closed blockers;
- static capability coverage is reported separately from schematic readiness, runtime qualification,
  and flight qualification.

This tranche does **not** claim live registry/blockstate validation, world placement, Sable assembly,
propulsion realization, persistence, or any control/flight capability.

## Productionized by AIRCRAFT-PROD-004

On acceptance of issue #636, the backend target-realization chain also contains deterministic static
propulsion companion topology:

- the production propeller-axis station and AIRCRAFT-PROD-003 target profile are provenance-bound;
- the source-backed Aeronautics bearing, hub neighbor, and Simulated symmetric-sail resource/state
  requirements are emitted as a deterministic companion geometry;
- blade geometry fails closed on collisions, axis mismatch, disconnected topology, nonzero transverse
  first moment, missing central symmetry, or sail-power mismatch;
- satisfying the generated sail-power contract resolves only the upstream propeller companion blocker;
- kinetic connectivity/RPM/stress, thrust sign, nested capture, persistence, and flight remain explicit
  unverified runtime obligations.

This is compiler-visible topology, not runtime mechanism qualification.

## Productionized by AIRCRAFT-PROD-005

On acceptance of issue #641, regular Create airframe-sail block-state lowering is productionized over
the exact AIRCRAFT-PROD-003 target-preflight and AIRCRAFT-PROD-004 propulsion identities:

- the retained regular-sail profile binds `create_white_sail_lift_v1` to a declared `facing` state
  vocabulary;
- wing and horizontal-tail surface roles demand `facing=up`, while vertical-tail surface roles demand
  `facing=south`;
- role states must belong to the declared legal-state set before compilation can proceed;
- a coordinate resolves only when all co-located aerodynamic semantics demand exactly one state;
- incompatible co-located surface orientations and aerodynamic-provider sites with no declared role
  state remain explicit compile-visible conflicts with no arbitrary fallback;
- only the upstream unresolved blockstate/resource blocker actually discharged by this pass is cleared;
- pilot-station, propulsion-runtime, aircraft-runtime, persistence, control, and flight blockers remain
  explicit.

The emitted facing remains source-backed static lowering. Live Sable force sign for that facing is not
qualified by this tranche.

## Productionized by AIRCRAFT-PROD-006

On acceptance of issue #645, the bounded horizontal/vertical regular-sail tail junction is lowered as a
deterministic discrete transformation downstream of AIRCRAFT-PROD-005:

- v0.6 conflict coordinates must match exactly the horizontal/vertical tail intersections;
- the complete vertical-tail cell set is translated exactly one block upward rather than deleting
  aerodynamic area or selecting an arbitrary facing at the shared lattice coordinate;
- the former junction retains only the horizontal-tail role with its accepted horizontal state;
- translated vertical-tail cells retain the accepted vertical state;
- the compiler fails closed on immutable-placement collisions, duplicate aerodynamic output coordinates,
  lost vertical cells, changed longitudinal first moment, changed relative fin shape, disconnected fin
  topology, missing root-face attachment, or missing/mismatched v0.6 conflicts;
- only the `unresolved_surface_state_conflicts` blocker is discharged by a passing transform; pilot,
  runtime, persistence, control, and flight blockers remain explicit.

The transformation proves discrete geometry/topology only. It does not prove Create/Sable attachment or
runtime side-force behavior at the translated fin root.

## Productionized by AIRCRAFT-PROD-007

On acceptance of issue #648, the unresolved semantic pilot station is lowered to one deterministic
static Create seat placement downstream of target preflight, propulsion, and tail-junction lowering:

- exactly one unresolved `PILOT_STATION` contract is required from the accepted target preflight;
- the retained bounded profile places `create:brown_seat` exactly one block above the semantic pilot
  anchor with explicit `waterlogged=false` state;
- the pilot anchor must coincide with accepted structural airframe geometry;
- the seat coordinate must be free of structural, resolved aerodynamic, and propulsion placements and
  must be supported directly by that pilot-anchor structure;
- target-preflight, propulsion, and tail artifacts are provenance-bound by production SHA-256 identities;
- only `unresolved_required_station_providers` is discharged by a passing static placement;
- pilot occupancy and pilot-control binding remain explicit runtime obligations and flight qualification
  still requires the latter.

This tranche does not claim Create-seat passenger behavior on a Sable body, input routing, persistence,
or any control/flight authority.

## Productionized by AIRCRAFT-PROD-008

On acceptance of issue #651, the accepted static aircraft realization layers are combined into one
deterministic v0.9-equivalent probe placement manifest:

- structural target-preflight placements, excluding the aerodynamic provider, emit with explicit empty
  block-state objects;
- AIRCRAFT-PROD-006 resolved aerodynamic placements emit with their accepted Create sail states;
- AIRCRAFT-PROD-004 bearing, hub, and Simulated symmetric-sail companions emit unchanged;
- AIRCRAFT-PROD-007 contributes exactly one explicit pilot-seat placement;
- target, propulsion, tail, and pilot identities are provenance-bound by production SHA-256 digests;
- placements sort deterministically by lattice, kind, and resource and fail closed on duplicate
  coordinates, malformed resource IDs, or tail/propulsion/pilot count mismatch;
- manifest bounds, resource IDs, kind counts, duplicate coordinates, and the frozen isolated destructive
  probe-origin contract are explicit production data;
- static manifest/command readiness is separate from physics assembly, runtime qualification, and flight.

The manifest still carries explicit mechanical blockers for Physics Assembler placement, airframe
adhesion, and control-surface child-body topology. It does not claim world placement, Sable capture,
Super Glue completeness, nested-propeller capture, persistence, or flight.

## Productionized by AIRCRAFT-PROD-009

On acceptance of issue #656, the v0.9 placement manifest is lowered into the bounded v0.10 static
Physics Assembler fixture topology:

- manifest placement kinds are partitioned explicitly into moving main-body and nested-propeller-child
  sets, with unknown or overlapping classification rejected;
- one source-backed `simulated:physics_assembler` fixture placement is added exactly one block below
  seed `[0,2,0]`, with `face=ceiling` so the sticky/seed direction is upward;
- the assembler is required to become part of the moving main body rather than remaining an external
  assemble-only actuator;
- a deterministic six-neighbor spanning tree expresses conservative logical adhesion intent over the
  moving main body including the assembler, with full reachability and N-1 edge count required;
- nested propeller hub/sails are excluded from that adhesion graph while exactly one main-body bearing
  must have exactly one face-adjacent child hub;
- Physics Assembler capture, adhesion application, and nested-propeller capture remain explicit unverified
  runtime obligations.

This tranche proves fixture geometry and logical adhesion topology only. It does not encode Super Glue,
invoke Sable assembly, prove nested capture, or qualify persistence, controls, forces, or flight.

## Productionized by AIRCRAFT-PROD-010

On acceptance of issue #658, the accepted v0.10 main-body spanning-tree adhesion intent is lowered to
deterministic bounded Create 6.0.10 Super Glue command/domain data:

- each proof edge is revalidated as unique, face-adjacent, main-body-only, and part of the exact N-1 tree;
- the retained encoding uses `create glue`, permission level 2, a 24-block maximum selection dimension,
  and the source-backed `Create-6.0.10-AllCommands/GlueCommand+SuperGlueEntity.span` contract;
- every configured glue domain must cover at least one accepted proof edge, and the domain set must cover
  all proof edges;
- domains fail closed if they exceed the selection bound, contain nested propeller payload cells, or
  contain both endpoints of a forbidden dynamic boundary;
- relative glue commands and the Physics Assembler `setblock` command are deterministic, with assembler
  block-state keys sorted in command output;
- live command registration, SuperGlueEntity realization, Physics Assembler capture, nested propeller
  capture, and moved-glue persistence remain explicit runtime obligations.

This tranche is static encoding only and does not consume the separate Platform-owned live Super Glue
qualification work. Runtime qualification, persistence, controls, forces, and flight remain unclaimed.

## Productionized by AIRCRAFT-PROD-011

On acceptance of issue #664, the frozen v0.12 governed powertrain topology is productionized as a
static, provenance-bound patch over the accepted probe manifest, assembly fixture, and glue encoding:

- the exact `manifest -> assembly fixture -> glue encoding` chain is SHA-256-bound before lowering;
- two Simulated Portable Engines remain source-constrained 32-RPM additions on opposite sides of the
  governor coordinate;
- exactly one `airframe_structure`/`minecraft:spruce_planks` cell is replaced by
  `create:rotation_speed_controller`, while the dedicated large cog and X-axis prop shaft are additions;
- the large cog must remain directly above the governor, and the shaft must be collinear/face-adjacent
  between the cog and the retained propeller bearing;
- no powertrain placement may enter the nested propeller child domain;
- the bounded powerplant glue domain must stay within the accepted Create selection limit, contain every
  emitted powertrain coordinate, exclude nested-child cells, and overlap accepted main-body glue coverage;
- 128 RPM remains the only accepted initial governor point; 160/192/224/256 RPM remain explicitly
  downstream of the 128-RPM runtime gate;
- Sable recapture, dual-engine shared-network behavior, live governor speed, kinetic stress margin, and
  propeller thrust sign/magnitude remain explicit unverified runtime obligations.

This tranche does not claim live powertrain behavior, save/reload persistence, controls, force fidelity,
or flight qualification.

## Qualified by AIRCRAFT-RUNTIME-001

On acceptance of issue #668, the production v0.12 Guild utility specimen is qualified at the first
exact-stack governed-powertrain operating point only:

- the runtime fixture compiles the retained production aircraft in-process through AIRCRAFT-PROD-011,
  then materializes that exact manifest/fixture/glue/powertrain chain rather than maintaining a second
  hand-authored aircraft geometry;
- the 118-block moving main body plus nine-block propeller payload must transfer into exactly one
  canonical Sable primary body, with persistent UUID re-resolution and current block-entity/physics
  authority on every later phase;
- the runtime consumes the accepted Agent-B authorities for Sable primary assembly, bounded Super Glue,
  Create kinetics on Sable, and nested Propeller Bearing lifecycle without reproducing their reduced
  qualification fixtures;
- both real Simulated Portable Engines must remain 32-RPM sources on one shared moved kinetic network;
- only the first accepted Rotation Speed Controller point, 128 RPM, is qualified, with finite positive
  kinetic stress margin;
- the compiler-emitted propeller payload must re-form as one nine-block nested child with one hub, eight
  symmetric sails, and realized sail power 8 without an undeclared child-glue domain;
- WEST-facing propeller thrust must be finite and nonzero with the expected local -X applied-force sign;
- analytical aerodynamic constants remain independent first-principles authority and are not fitted to
  the runtime force observation.

This gate does **not** qualify 160/192/224/256 RPM, save/reload persistence, any control axis, handling,
or flight.

## Frozen evidence not yet productionized

The frozen branch contains accepted evidence for later compiler/runtime stages, but that evidence is
not current-`main` production authority for:

- live registry/blockstate validation beyond the bounded 128-RPM specimen;
- higher governed RPM points and live behavior beyond the bounded 128-RPM powertrain/persistence gates;
- yaw mechanism, cockpit routing, player interaction, and moving-body tracking.

Those capabilities must be extracted through their own bounded production issues from current
`main`. Historical passing runtime evidence does not authorize bypassing current Integration Platform
contracts.

## Still unqualified / new aircraft work

The following remain unqualified as aircraft capabilities:

- completed-control mechanism persistence/save-reload;
- pitch;
- roll;
- multi-axis control;
- aircraft-level atmosphere envelope qualification;
- closed-loop stability/handling;
- stable powered flight;
- human flight/feel.

## Integration Platform dependency

Reusable Create/Sable/Simulated runtime seams are owned by issue #613. Aircraft may consume a seam
only when `docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json` on accepted `main` explicitly
grants Agent B production authority for that capability. Until then, Aircraft must not reproduce or
work around the Platform fixture inside a complete aircraft.

## Qualified by AIRCRAFT-RUNTIME-002

On acceptance of issue #674, the production v0.12 Guild utility specimen is qualified across a genuine
fresh-process save/stop/reload boundary at the already accepted 128-RPM operating point:

- both boots compile the same production chain and require identical manifest and powertrain SHA-256
  identities before runtime evidence is accepted;
- Boot A reaches 128 RPM with finite positive stress margin and the accepted WEST/-X nonzero thrust sign,
  normalizes the nine-block Propeller Bearing child before save, and persists the canonical primary Sable
  UUID plus the real `GlobalSavedSubLevelPointer` locator needed by the accepted production-scale seam;
- Boot B makes the saved locator chunk available, re-resolves the **same** persistent primary UUID to fresh
  canonical/current physics authority, and never treats a pre-reload Java object, block entity, entity,
  physics handle, child, or kinetic-network reference as authoritative;
- exactly five compiler-emitted Super Glue domains recover through the accepted bounded rehydration path
  without duplicate glue authority;
- the normalized propeller payload reassembles as a fresh child with exactly one hub plus eight symmetric
  sails and realized sail power 8, with no stale pre-reload child retained;
- the two Portable Engines and governor are lifecycle-reactivated only where the accepted Platform contract
  requires it, return the production network to 128 RPM, and reproduce finite positive stress margin plus
  the accepted thrust observation;
- production setup consumes PLATFORM-013 source readiness at aircraft scale: all compiler-emitted source
  chunks must be Sable-ready and Minecraft entity-ticking-ready, all five glue domains must be Create-visible,
  the real Physics Assembler must synchronously transfer the exact 127 source members to zero, and temporary
  source-chunk tickets are released after canonical-body capture;
- locator/liveness qualification tickets are released before PASS. These tickets and readiness gates are
  qualification sequencing only and are not production force-load policy.

This gate qualifies completed-aircraft persistence/recovery only for the production v0.12 specimen at
128 RPM. It does **not** qualify 160/192/224/256 RPM, rudder/yaw, cockpit routing, pitch, roll, multi-axis
control, atmosphere-envelope behavior, handling, stable powered flight, or human flight/feel.

## AIRCRAFT-PROD-012 acceptance boundary

On acceptance of issue #682, the production Java compiler extends the accepted v0.12 Guild utility chain
to the corrected **static-only** v0.13.1 yaw-control topology:

- profile lineage is `skyforge.yaw_control.simulated.guild_utility.v0_13_1` and binds the exact current
  manifest, assembly-fixture, glue-encoding, and powertrain SHA-256 identities rather than accepting
  schema/version labels alone;
- the superseded v0.13 digest
  `59f451bb7bb71433016af249f5ded7a87aed47b69b2a26215b4f573322f2d951` and exact-stack run
  `34764106343` remain falsification evidence: the spruce hinge/separator topology is rejected because
  relative cell `[16,4,0]` remained at the source; the failed Python predecessor is **not** revived as
  production code;
- seven fixed `create:white_sail[facing=south]` fin cells remain at x=15..16, all four x=17 cells
  `[17,4..7,0]` are a true air gap, and one parent `simulated:swivel_bearing` is added at `[18,3,0]`
  with `facing=up,assembled=false,powered=false`;
- the UP-facing bearing seed is exactly `[18,4,0]`; the declared rudder child is exactly four connected,
  parent/propeller-disjoint `simulated:white_symmetric_sail[axis=z]` cells at `[18,4..7,0]`;
- the old `vertical_tail` glue domain is replaced by parent `vertical_tail_fixed` and
  `yaw_bearing_mount` plus child `rudder_child`; together with retained fuselage/wing/horizontal-tail
  glue and the v0.12 powerplant domain this yields exactly seven bounded domains. Parent glue contains
  no rudder cell, no domain contains an x=17 gap cell, and child glue excludes the bearing;
- current-production reconciliation is fail-closed at 118 corrected parent-main members including the
  Physics Assembler, nine independent propeller-child members, and four rudder-child members: 130
  resulting manifest placements and 131 primary transfer members before the Swivel child separates;
- source resource/state mismatch, occupied bearing coordinates, reintroduced gap geometry, propeller
  overlap, glue-boundary drift, count drift, or upstream provenance mismatch reject lowering;
- static readiness emits the five still-unverified runtime obligations for primary Sable recapture, exact
  Swivel child capture, neutral initial constraint, real kinetic actuation/commanded neutral return, and
  aircraft-specific rudder yaw-force authority.

This tranche does **not** qualify live Swivel assembly/actuation, rudder physical pose, yaw force/moment,
Steering Wheel/cockpit routing, player interaction, pitch, roll, multi-axis control, higher governed RPM,
handling, stable flight, or human feel.

## AIRCRAFT-RUNTIME-003 acceptance boundary

On acceptance of issue #684, the production corrected v0.13.1 Guild utility aircraft consumes accepted
`SWIVEL_CONTROL_CHILD_ON_SABLE_LIFECYCLE` authority and qualifies the rudder mechanism through commanded
neutral return on the exact retained stack:

- the production runtime fixture materializes the exact corrected partition of 118 parent-main members,
  nine independent propeller-child members, and four symmetric-sail rudder-child members, with all seven
  compiler-emitted Super Glue domains visible before the primary Sable transfer;
- the moved parent keeps one UP-facing `simulated:swivel_bearing`; assembling it captures exactly the four
  declared `simulated:white_symmetric_sail[axis=z]` cells as one fresh current child while the 118 parent
  members and nine-cell propeller child remain independent;
- the rudder begins at target `0.0` degrees and a physically neutral pose, and the bounded command fixture
  drives the Swivel hidden extra cog only through a real Create Creative Motor/cogwheel network at 16 RPM;
- exact-stack run `35153062672`, job `104986575772`, observed motor/drive/hidden-cog speeds
  `+16/+16/-16` RPM, target deflection `-96.0000038` degrees, and physical rudder deflection
  `-95.3417122` degrees; no direct target-angle mutation is used;
- commanding the same live motor to zero yields zero hidden-cog drive and a locked target hold; commanding
  the same live network in reverse returns the target to `0.0` degrees and the physical rudder to
  approximately `-0.0000018` degrees before the command network is stopped;
- the retained production tolerances remain target-neutral `0.1` degrees, physical-neutral `2.0` degrees,
  and minimum meaningful physical deflection `5.0` degrees; the observed exact-stack margins are well
  inside those bounds;
- the surrounding accepted v0.12 powertrain remains at 128 RPM with finite positive stress margin and the
  accepted WEST/-X propeller-thrust sign, and fixture-only Sable liveness/source-chunk tickets are released
  before PASS.

This gate qualifies exact rudder child capture, real kinetic signed actuation, locked hold, and **commanded**
return to neutral only. It does **not** qualify passive self-centering, aerodynamic rudder side force or yaw
moment, Steering Wheel/cockpit routing, actual-client control, pitch, roll, multi-axis control, handling,
stable powered flight, or human flight/feel.

## AIRCRAFT-RUNTIME-004 acceptance boundary

On acceptance of issue #686, the production corrected v0.13.1 Guild utility aircraft consumes accepted
`SABLE_AERODYNAMIC_FORCE_OBSERVATION_LIFECYCLE` authority and qualifies **physical rudder yaw authority at
one declared controlled-flow condition** without fitting the analytical aircraft model to target runtime data:

- the probe uses the production coordinate convention `+x` nose-to-tail (forward local `-x`), `+y` up,
  `+z` starboard, one declared 10.0 m/s forward-flow condition, 80 physics-settle ticks, minimum physical
  rudder deflection 5 degrees, and independent `1e-4` numerical floors for lateral force and yaw moment;
- the accepted #684 real 16-RPM Create/Swivel command path remains the actuator authority. #686 samples that
  same path after the 10-tick network warmup plus three bounded command ticks, keeping the yaw-sign probe
  below the 90-degree surface-normal reversal while #684 retains its separate full 10-command-tick gate;
- exact-stack run `35157354210`, job `105000314590`, observed target `-62.4000025` degrees and physical rudder
  yaw `-61.7417068` degrees with one recorded real Sable `ForceGroups.DRAG` point force;
- under that current parent/child pose the aircraft-local aggregate lateral force is
  `-0.5648170237` and the aircraft-local yaw moment about the current parent center of mass is
  `+5.3224699306`, both finite/nonzero and well above the numerical floor; the yaw moment therefore has the
  first-principles opposite sign to the sub-90-degree physical rudder yaw under the production axis contract;
- the force observation records current parent/child identity, current parent COM, world/local controlled-flow
  vector, recorded point force(s), aggregate force, and aggregate moment; individual force tracking is bounded
  to the measurement window and the reusable Platform observer remains separately green;
- analytical aircraft geometry/aerodynamics remain independent authority. No runtime force magnitude is fed
  back into analytical lift/drag/yaw coefficients or used as a handling/stability calibration target;
- exact-head regressions are green on candidate `7a41992e74122bee2ef651b88524b09b846b887f`:
  #684 rudder actuation run `35157354200`, 128-RPM run `35157354244`, persistence run `35157354251`,
  Platform aerodynamic-force observation run `35157354219`, and ordinary CI run `35157354166`.

This gate qualifies only nonzero, correctly signed physical yaw authority at the declared controlled-flow
point. It does **not** qualify cockpit routing, actual-client control, passive self-centering, pitch/roll,
atmosphere envelopes, static/dynamic stability, handling, stable powered flight, or human flight/feel.

## AIRCRAFT-PROD-013 acceptance boundary

On acceptance of issue #691, the production Java aircraft compiler advances the accepted corrected-yaw chain
through a deterministic **static-only Steering Wheel source contract** while consuming current runtime authority
by digest rather than reviving frozen Python schemas:

- the retained source interface is the accepted AIRCRAFT-RUNTIME-003 tail command boundary: Swivel
  `[18,3,0]`, aft drive cog `[18,3,1]`, and temporary source coordinate `[18,2,1]`;
- the production source resource is exactly `simulated:steering_wheel` at `[18,2,1]` with block state
  `facing=north,on_floor=false,waterlogged=false`; the profile fails closed if the source is no longer directly
  below the accepted Y-axis drive cog;
- exact Simulated source authority is pinned to `Creators-of-Aeronautics/Simulated-Project` commit
  `50443d00afa06e0982b45f40cd686f7ecf978132`: the Steering Wheel uses Y-axis kinetics, exposes its upward
  shaft when `on_floor=false`, generates the retained 16-RPM magnitude, and provides bounded signed
  `updateTargetAngle` command behavior;
- accepted Platform `STEERING_WHEEL_CLIENT_ON_SABLE_LIFECYCLE` remains the reusable runtime authority for
  ordinary actual-client acquisition and real +/-16-RPM command/release behavior. AIRCRAFT-PROD-013 references
  that authority and does not reproduce its actual-client fixture;
- `SkyforgeAircraftRudderControlAuthority` derives deterministic actuation and commanded-neutral-return digests
  from the **current** v0.13.1 yaw-control IR plus accepted AIRCRAFT-RUNTIME-003 coordinates, 16-RPM source,
  target/physical neutral tolerances, minimum deflection, source-off hold, inverse-command neutral return, and
  `passiveSelfCentering=false`; mixed/stale authority is rejected;
- `SkyforgeAircraftSteeringYawSourceIR` serializes exact current yaw, rudder-actuation, and neutral-return digests,
  source coordinate/resource/state/provenance, 16-RPM magnitude, retained bounded 48-degree command reference,
  Platform capability ID, topology checks, runtime obligations, readiness, and validation into a deterministic
  SHA-256 identity;
- fail-closed tests reject mixed current runtime authority, source/drive coordinate drift, resource drift, block-
  state drift, RPM drift, wrong Platform capability, and exact Simulated source-provenance drift.

Readiness advances only `steeringWheelSourceStaticTopologyPassed=true` and records the reusable Platform runtime
authority reference. Aircraft-tail response to the real Steering Wheel, cockpit routing, pilot interaction, passive
self-centering, pitch/roll, handling, stable powered flight, and human flight/feel remain unqualified.

## Next bounded aircraft tranche

After AIRCRAFT-PROD-013 is accepted on `main`, the next bounded Agent-B gate is AIRCRAFT-PROD-014 / issue #692:
productionize the deterministic cockpit-to-rudder Create route from a pilot-adjacent Steering Wheel to the accepted
aft drive-cog/Swivel interface. AIRCRAFT-RUNTIME-005 / #723 and later client/pilot gates remain downstream.

## Prepared by AIRCRAFT-DESIGN-001

Issue #687 records target-neutral pitch/roll semantic direction and a discrete feasibility study against the accepted Guild utility blockspace plus corrected-yaw occupancy. It defines nose-up/down and port/starboard-down moment signs in the production coordinate frame, demonstrates collision-aware split-elevator and mirrored outboard-aileron candidate partitions, and quantifies their target-realization centroid shifts without feeding those shifts back into the analytical design.

This is design evidence only. Pitch, roll, multi-axis control, stability, handling, and flight remain unqualified. No target mechanism, actuator sign, lowering IR, or runtime authority is introduced by #687.

## Prepared by AIRCRAFT-DESIGN-002

Issue #727 defines the analytical evidence boundary for future atmosphere-envelope, stability, trim,
dynamic-mode, handling, and stable-flight qualification. Current v0.1 authority remains one declared
sea-level-density / 45 m/s cruise design point with algebraic dynamic pressure, lift-at-declared-CL,
induced-drag proxy, one-dimensional CG, and tail-volume metrics only.

The design audit explicitly records that equal dynamic pressure is not general aerodynamic equivalence,
that current CG/tail-volume outputs are not static-stability derivatives, and that the compiler still
lacks atmosphere-property modeling beyond direct density input, lift-curve/stall authority, static and
control derivatives, full mass/inertia properties, damping derivatives, trim solutions, and dynamic-mode
criteria. Runtime Create/Sable/Aeronautics force observations remain target-realization evidence and may
not be fitted back into the analytical model.

This is design evidence only. Aircraft-level atmosphere envelope, static stability, dynamic stability,
trim, handling, stable powered flight, and human flight/feel remain unqualified.
