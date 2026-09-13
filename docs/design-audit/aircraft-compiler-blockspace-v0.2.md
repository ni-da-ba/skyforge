# AIRCRAFT-001 block-space transcription v0.2

## Decision

Advance the accepted v0.1 continuous aircraft-design IR into a **deterministic target-neutral block-space skeleton** before resolving any concrete Minecraft, Create, Sable, or Create Aeronautics blocks.

The continuous aircraft geometry remains authoritative. v0.2 measures the error introduced by a discrete lattice and emits semantic target capabilities, rather than silently allowing Minecraft geometry to redefine the aircraft.

The pipeline is now:

```text
mission/design condition
-> continuous first-principles AircraftDesignIR (v0.1)
-> explicit lattice transcription assumptions
-> deterministic block-space AircraftBlockspaceIR (v0.2)
-> semantic capability contract
-> later exact-target adapter
-> later in-engine assembly / COM / flight measurement
```

## Exact target context, not yet a target implementation

Skyforge's current pinned validation stack is:

- Minecraft `1.21.1`;
- NeoForge `21.1.249`;
- Create `6.0.10+mc1.21.1`;
- Sable `2.0.5+mc1.21.1`;
- Create Aeronautics `1.3.2+mc1.21.1`.

Those identities come from `skyforge-neoforge-1211/wave-c1-mods.properties`. v0.2 uses the family label `create_aeronautics_1.3.2_mc1.21.1_capability_contract`, but it deliberately emits **no concrete registry/resource names**. Exact released-artifact API/block semantics must be inspected before the next adapter may bind a capability to a block.

## Lattice model

The specimen declares `2 blocks/m` as a transcription input. It is not a statement about real-world scale, a universal aircraft scale, or an Aeronautics physics constant.

Coordinates use:

- `x`: nose to tail;
- `y`: upward;
- `z`: starboard positive;
- occupied cells centered on integer lattice coordinates.

A continuous coordinate `u >= 0` used as an anchor is quantized deterministically by nearest integer with a fixed half-up rule:

`i = floor(u * s + 1/2)`

where `s` is blocks per meter.

For a straight-taper horizontal surface with half-span `h=b/2`, root chord `c_r`, and tip chord `c_t`, each admitted spanwise lattice center uses

`eta = |z| / h`

and

`c(eta) = c_r + (c_t-c_r) eta`.

The occupied chordwise integer centers are exactly those whose metric centers lie within

`x_LE <= x <= x_LE + c(eta)`.

The vertical tail uses the same linear interpolation over height. This is a deterministic scanline transcription of the already accepted straight-taper geometry; it is not a new aerodynamic model.

## Bounded discretization error

v0.2 records continuous dimensions and realized lattice extents separately. It does not pretend exact equality when the requested metric dimension cannot be represented by the declared lattice.

Because the specimen is mirror-symmetric about `z=0` and cells are centered on integer coordinates, the 10.0 m wing at 2 blocks/m occupies centerline-inclusive samples from `z=-10` through `z=+10`. Counting cell width gives a realized occupied extent of 10.5 m: a **one-block total span error**. That error is retained as QA evidence rather than hidden by an asymmetric rounding convention.

The current specimen permits at most one block of dimensional transcription error and at most half a block of longitudinal CG-anchor quantization error.

## Structural/topological skeleton

v0.2 is intentionally sparse:

- fuselage: centerline structural spine only;
- wing and horizontal tail: discrete surface-intent cells;
- vertical tail: discrete surface-intent cells;
- wing/tail attachment: axis-aligned connector cells only.

Diagonal connector approximation is rejected rather than silently voxelized. Full fuselage volume, landing gear, control surfaces, cockpit envelope, cargo envelope, and aesthetic shaping are later bounded stages.

A six-neighbor flood-fill proves that the occupied intent skeleton is one connected component. Mirror symmetry is checked exactly for wing and horizontal-tail cells.

## Propeller clearance

The v0.1 propeller envelope is carried forward as a geometric exclusion disk. At the quantized propeller plane, an occupied cell violates clearance when its radial distance from the propeller axis lies outside the declared hub/cowling exclusion radius and inside the propeller radius:

`r_hub < sqrt((y-y_p)^2 + z^2) <= D_p/2`.

This is only a **clearance test**. It does not calculate thrust, torque, shaft power, blade aerodynamics, or Create Aeronautics propulsion behavior.

## Semantic capability contract

The target-neutral block-space IR asks a later exact-target adapter for capabilities rather than block names:

- `fuselage_spine` -> `rigid_physics_member`;
- wing/tail surfaces -> `aerodynamic_lift_surface` + `rigid_physics_member`;
- attachment cells -> `rigid_load_path`;
- propeller axis -> `rotational_thrust_producer` + `shaft_power_input` + `clearance_disk`;
- pilot station -> `vehicle_control_station`;
- cargo station -> `payload_volume_or_interface`.

If the exact pinned target cannot realize one of these requirements, the target adapter must fail explicitly or return a bounded approximation result. It must not invent a block mapping.

## What v0.2 proves

The machine gate proves that:

1. the v0.1 design digest is bound into the v0.2 result;
2. transcription is deterministic;
3. continuous straight-taper geometry produces a repeatable integer lattice;
4. declared dimensional and CG quantization errors are bounded and reported;
5. wing/tail mirror symmetry is preserved;
6. the structural intent skeleton is six-neighbor connected;
7. the declared propeller disk is clear of non-hub occupied cells;
8. capability intent remains separate from concrete target resource identity.

A negative-control test shrinks the permitted propeller hub radius and requires the clearance gate to fail, proving that the clearance result is computed rather than hard-coded.

## Explicit non-claims

v0.2 does **not** prove:

- concrete Minecraft/Create/Aeronautics block legality;
- Create Aeronautics assembly connectivity;
- in-engine mass or center of mass;
- in-engine aerodynamic forces;
- static or dynamic stability;
- control-surface authority;
- structural strength or load-path capacity;
- propulsion performance;
- landing/ground handling;
- aesthetic quality.

The analytical equations from v0.1 remain pre-design authority only. They must not be fitted to the mod by inventing compatibility constants.

## Next bounded stage

The next stage is an **exact Create Aeronautics 1.3.2 / Sable 2.0.5 target capability adapter** based on the pinned released artifact and executable runtime evidence. It should resolve only the capability vocabulary required by this specimen, then measure assembly connectivity, target mass/COM, propulsion/lift response, clearances, and control behavior in-engine.

Where runtime behavior differs from the analytical pre-design model, record the difference as measured target behavior. Do not rewrite the underlying aerodynamics equations merely to make them imitate the mod.
