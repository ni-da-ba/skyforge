# AIRCRAFT-001 — bounded aircraft compiler proof

This directory is the first backend-neutral aircraft-design/compiler layer for Skyforge.

It is deliberately **not** a Create Aeronautics schematic generator. v0.1 proves the continuous analytical pre-design chain, and v0.2 adds a deterministic target-neutral block-space transcription without concrete target resource identities:

```text
mission/design condition
  -> declared discrete design domains
  -> source-backed geometry/aerodynamics equations
  -> explicit component mass ledger
  -> deterministic constrained search
  -> target-neutral AircraftDesignIR (v0.1)
  -> deterministic integer-lattice transcription (v0.2)
  -> semantic target capability contract
  -> engineering / mobile QA
  -> later exact Create Aeronautics adapter + runtime measurement
```

## Run

Analytical v0.1 only:

```powershell
python tools/aircraft_compiler/compile.py `
  tools/aircraft_compiler/specimens/guild_utility_monoplane_v0.1.json `
  --out build/aircraft-compiler-v01
```

Analytical v0.1 plus block-space v0.2:

```powershell
python tools/aircraft_compiler/compile.py `
  tools/aircraft_compiler/specimens/guild_utility_monoplane_v0.1.json `
  --blockspace-config tools/aircraft_compiler/specimens/guild_utility_monoplane_blockspace_v0.2.json `
  --out build/aircraft-compiler-v02
```

The v0.1 output contains `resolved.json`, `validation.txt`, three orthographic SVGs, `mass_balance.svg`, `lift_check.svg`, and `mobile_review.svg`.

With the v0.2 config it additionally emits `blockspace_resolved.json`, `blockspace_validation.txt`, and `blockspace_mobile_review.svg`.

## Mathematical boundary — v0.1 continuous pre-design

The implementation uses:

- `q = 1/2 rho V^2` and `L = q S C_L`;
- straight-taper wing area `S = b(c_r+c_t)/2`;
- `AR = b^2/S`;
- straight-taper mean aerodynamic chord `c_bar = (2/3)c_r(1+lambda+lambda^2)/(1+lambda)`;
- `x_CG = sum(m_i x_i)/sum(m_i)`;
- horizontal tail volume `V_H = S_H l_H/(S c_bar)`;
- vertical tail volume `V_V = S_V l_V/(S b)`;
- induced-drag comparison proxy `C_Di = C_L^2/(pi e AR)`.

The equations are not a claim that Create Aeronautics uses the same force model. The eventual target adapter must be validated against actual in-engine behavior rather than by inventing compatibility constants.

## Mathematical boundary — v0.2 discrete transcription

The block-space specimen declares a lattice scale and quantizes anchors with a deterministic half-up rule:

`i = floor(u * blocksPerMeter + 1/2)`.

Straight-taper lifting surfaces are rasterized by evaluating the exact linear chord at each admitted spanwise or heightwise lattice center and occupying only chordwise centers inside that continuous section. v0.2 then reports continuous-versus-realized dimensions instead of hiding quantization error.

The current skeleton validates:

- six-neighbor connectivity;
- exact left/right mirror symmetry for wing and horizontal tail;
- bounded dimensional and CG-anchor quantization error;
- propeller-disk geometric clearance;
- absence of concrete Minecraft/Create/Aeronautics resource identities.

The v0.2 capability contract names requirements such as `aerodynamic_lift_surface`, `rigid_physics_member`, `rigid_load_path`, `rotational_thrust_producer`, and `vehicle_control_station`. Concrete block mappings are deliberately deferred until the exact pinned Create Aeronautics/Sable artifact is inspected and exercised.

## Sources

Primary/teaching references used to establish the v0.1 equations and terminology:

- NASA Glenn Research Center, **Lift Equation**: https://www1.grc.nasa.gov/beginners-guide-to-aeronautics/lift-equation/
- NASA Glenn Research Center, **Dynamic Pressure**: https://www1.grc.nasa.gov/beginners-guide-to-aeronautics/dynamic-pressure/
- NASA Glenn Research Center, **Wing Geometry**: https://www1.grc.nasa.gov/beginners-guide-to-aeronautics/wing-geometry/
- NASA Glenn Research Center, **Induced Drag Coefficient**: https://www1.grc.nasa.gov/beginners-guide-to-aeronautics/induced-drag-coefficient/
- Virginia Tech AOE 3104 wing notes, mean aerodynamic chord for a straight tapered wing: https://archive.aoe.vt.edu/lutze/AOE3104/airfoilwings.pdf
- FAA **Weight & Balance Handbook**, CG/stability/control context: https://www.faa.gov/sites/faa.gov/files/regulations_policies/handbooks_manuals/aviation/FAA-H-8083-1.pdf
- Embry-Riddle, **Aircraft Stability & Control**, tail-volume definitions and representative airplane-class values: https://eaglepubs.erau.edu/introductiontoaerospaceflightvehicles/chapter/aircraft-stability-control/
- NASA standard-atmosphere reference for the specimen sea-level density input `rho=1.225 kg/m^3`: NASA/TP-2006-213486.

## Assumptions versus authority

Some quantities in the specimen are design or transcription inputs rather than universal aeronautical truths:

- gross mass, cruise speed, design `C_L`;
- component masses and longitudinal stations;
- permitted aspect-ratio and CG/MAC search ranges;
- span efficiency `e=0.8` used only for relative induced-drag comparison;
- candidate geometry domains;
- `2 blocks/m` lattice scale and local mount heights used by the v0.2 specimen.

Representative GA single-engine tail-volume values (`V_H≈0.7`, `V_V≈0.04`) are reference targets, not stability certification criteria.

v0.1/v0.2 intentionally do **not** claim structural strength, stall prediction, static margin, dynamic stability, control authority, concrete Create Aeronautics block compatibility, or Create Aeronautics flight performance.
