# Skyforge Aircraft Compiler state

**Program authority:** issue #545  
**Productionization boundary:** AIRCRAFT-PROD-003 / issue #632
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

## Frozen evidence not yet productionized

The frozen branch contains accepted evidence for later compiler/runtime stages, but that evidence is
not current-`main` production authority for:

- resolved blockstate/orientation lowering, complete placement-manifest emission, and Create sail/state lowering;
- Sable primary/nested assembly, bounded Super Glue domains, and propulsion/governor topology;
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

After AIRCRAFT-PROD-003 is accepted on `main`, continue target lowering only by resolving the remaining
state/orientation and companion/station blockers into a deterministic placement manifest. Live runtime
qualification must consume only capabilities explicitly granted by
`docs/agent-state/COMPILER_INTEGRATION_CAPABILITIES.json`; persistence and new control-axis work remain
downstream of those applicable Platform contracts.
