# Skyforge Aircraft Compiler state

**Program authority:** issue #545  
**Productionization boundary:** AIRCRAFT-RUNTIME-001 / issue #668
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
- aircraft-specific save/reload/disassembly-reassembly behavior, higher governed RPM points, and live behavior beyond the bounded 128-RPM powertrain gate;
- yaw mechanism, cockpit routing, player interaction, and moving-body tracking.

Those capabilities must be extracted through their own bounded production issues from current
`main`. Historical passing runtime evidence does not authorize bypassing current Integration Platform
contracts.

## Still unqualified / new aircraft work

The following remain unqualified as aircraft capabilities:

- completed-control persistence/save-reload;
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

## Next bounded aircraft tranche

After AIRCRAFT-RUNTIME-001 is accepted on `main`, the next required aircraft gate is completed-aircraft
save/reload persistence before new control-axis production work. Current Platform lifecycle grants do
**not** qualify persistence; Agent B must consume a reusable accepted save/reload seam when one exists
and must not invent an aircraft-specific persistence workaround. Until that dependency is available,
static frozen yaw-control evidence may remain reference material but must not be promoted as a
production-ready new axis.

## AIRCRAFT-RUNTIME-002 in progress

Issue #674 consumes accepted `SABLE_COMPOSED_MECHANISM_PERSISTENCE_LIFECYCLE` authority from PLATFORM-007. Its dedicated two-boot workflow recompiles the same production v0.12 Guild utility chain in both fresh processes, preserves manifest/powertrain digest identity and the persistent primary Sable UUID, normalizes the nine-block Propeller Bearing child before save, then re-resolves current body/physics/block-entity authority after reload. Missing compiler-emitted Super Glue entities may only be re-realized after the accepted bounded rehydration grace semantics; the proof must recover exactly five compiler glue domains without duplicates, explicitly reactivate only the Portable Engine burn/governor lifecycle state, and return to the already-qualified 128-RPM stress/thrust boundary.

Until the dedicated exact-stack workflow passes on an authority-bearing head, aircraft persistence remains unqualified and no control-axis production tranche may be promoted.

## Prepared by AIRCRAFT-DESIGN-001

Issue #687 records target-neutral pitch/roll semantic direction and a discrete feasibility study against the accepted Guild utility blockspace plus corrected-yaw occupancy. It defines nose-up/down and port/starboard-down moment signs in the production coordinate frame, demonstrates collision-aware split-elevator and mirrored outboard-aileron candidate partitions, and quantifies their target-realization centroid shifts without feeding those shifts back into the analytical design.

This is design evidence only. Pitch, roll, multi-axis control, stability, handling, and flight remain unqualified. No target mechanism, actuator sign, lowering IR, or runtime authority is introduced by #687.
