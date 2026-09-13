# AIRCRAFT-001 — bounded aircraft compiler proof

This directory is the first backend-neutral aircraft-design/compiler layer for Skyforge.

It is deliberately **not** a Create Aeronautics schematic generator. v0.1 proves a narrower chain:

```text
mission/design condition
  -> declared discrete domains
  -> source-backed geometry/aerodynamics equations
  -> explicit component mass ledger
  -> deterministic constrained search
  -> target-neutral aircraft-design IR
  -> engineering QA
```

## Run

From repository root:

```powershell
python tools/aircraft_compiler/compile.py `
  tools/aircraft_compiler/specimens/guild_utility_monoplane_v0.1.json `
  --out build/aircraft-compiler-v01
```

The output contains `resolved.json`, `validation.txt`, three orthographic SVGs, `mass_balance.svg`, `lift_check.svg`, and `mobile_review.svg`.

## Mathematical boundary

The implementation uses:

- `q = 1/2 rho V^2` and `L = q S C_L`;
- straight-taper wing area `S = b(c_r+c_t)/2`;
- `AR = b^2/S`;
- straight-taper mean aerodynamic chord `c_bar = (2/3)c_r(1+lambda+lambda^2)/(1+lambda)`;
- `x_CG = sum(m_i x_i)/sum(m_i)`;
- horizontal tail volume `V_H = S_H l_H/(S c_bar)`;
- vertical tail volume `V_V = S_V l_V/(S b)`;
- induced-drag comparison proxy `C_Di = C_L^2/(pi e AR)`.

The equations are not a claim that Create Aeronautics uses the same force model. The first target adapter must be calibrated/validated against actual in-engine behavior rather than by inventing compatibility constants.

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

Some quantities in the specimen are design inputs rather than universal aeronautical truths:

- gross mass, cruise speed, design `C_L`;
- component masses and longitudinal stations;
- permitted aspect-ratio and CG/MAC search ranges;
- span efficiency `e=0.8` used only for relative induced-drag comparison;
- candidate geometry domains.

Representative GA single-engine tail-volume values (`V_H≈0.7`, `V_V≈0.04`) are reference targets, not stability certification criteria.

v0.1 intentionally does **not** claim structural strength, stall prediction, static margin, dynamic stability, control authority, or Create Aeronautics flight performance.
