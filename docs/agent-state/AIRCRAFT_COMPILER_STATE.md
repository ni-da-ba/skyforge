# Skyforge Aircraft Compiler state

**Program authority:** issue #545  
**Productionization boundary:** AIRCRAFT-PROD-005 / issue #641
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

## Frozen evidence not yet productionized

The frozen branch contains accepted evidence for later compiler/runtime stages, but that evidence is
not current-`main` production authority for:

- complete combined placement-manifest emission and live registry/blockstate validation;
- Sable primary/nested assembly, bounded Super Glue domains, and live propulsion/governor mechanism behavior;
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

After AIRCRAFT-PROD-005 is accepted on `main`, close the remaining static target-lowering seam by
combining accepted airframe placements, propulsion companions, and resolved regular-sail block states
into one deterministic placement manifest without inventing unresolved pilot/control resources. Live
runtime qualification must consume only capabilities explicitly granted by
`docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json`; persistence and new control-axis work remain
downstream of those applicable Platform contracts.
