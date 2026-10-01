# Aircraft atmosphere and stability qualification contract v0.1

**Authority:** AIRCRAFT-DESIGN-002 / issue #727
**Scope:** first-principles analytical qualification architecture only
**Production status:** design evidence; atmosphere envelope, stability, handling, and flight remain unqualified

## Current analytical authority is a single design point

The production analytical compiler is intentionally target-neutral. The retained Guild utility mission currently supplies one atmosphere/operating point:

- gross mass: `1100 kg`;
- air density: `1.225 kg/m^3`;
- gravity: `9.80665 m/s^2`;
- cruise speed: `45 m/s`;
- design lift coefficient: `0.55`.

At that point the accepted deterministic analytical output is:

- dynamic pressure `q = 1240.3125 Pa`;
- weight `W = 10787.315 N`;
- required wing area at the declared design `C_L`: `15.813192239858905 m^2`;
- selected wing area: `16.0 m^2`;
- aspect ratio: `6.25`;
- mean aerodynamic chord: `1.6333333333333333 m`;
- cruise lift: `10914.75 N`;
- cruise lift residual fraction: `0.01181341232734942`;
- analytical induced-drag coefficient proxy: `0.01925774811411934`;
- longitudinal CG: `3.4736363636363636 m`, or `0.16753246753246742` MAC from the selected wing leading edge;
- horizontal tail volume: `0.7003348214285713`;
- vertical tail volume: `0.03997200520833333`.

Those values prove only the scope already named by `AircraftDesignIR.Validation`: `analytical_geometry_and_balance_only`. The IR explicitly does not prove target-runtime lift or stability, structural strength, stall behavior, dynamic stability, or control authority.

## What can be swept without adding new physics

The existing relations support deterministic recomputation of quantities that depend only on already-declared inputs and geometry:

- dynamic pressure, `q = 1/2 rho V^2`;
- lift from a **declared** coefficient, `L = q S C_L`;
- required area for a declared weight, density, speed, and `C_L`;
- induced-drag coefficient proxy `C_Di = C_L^2/(pi e AR)` when the existing declared span-efficiency assumption is retained;
- one-dimensional longitudinal CG from the declared lumped mass ledger;
- horizontal and vertical tail-volume coefficients from geometry.

A future atmosphere-envelope compiler may therefore enumerate declared `(rho, V)` operating points and recompute these algebraic quantities without target-runtime fitting. It must preserve the distinction between an **input coefficient assumption** and a derived aerodynamic result.

Equal dynamic pressure is not, by itself, authority to collapse two atmosphere points into one equivalent aerodynamic condition. The current model has no viscosity, Reynolds number, speed of sound, Mach number, compressibility correction, transition model, or coefficient dependence on those variables. Until those physics are explicitly modeled, equal-`q` points may only be described as sharing the same dynamic pressure under the present algebraic relation—not as having identical lift, drag, stall, stability, or control behavior.

## Missing atmosphere-envelope authority

The current mission field `airDensityKgM3` is a direct input. The string `1976_US_Standard_Atmosphere_sea_level_density_input` documents provenance for the retained specimen but is not an implemented atmosphere model.

A genuine atmosphere envelope therefore requires an upstream, target-neutral environment contract that can provide, for each declared operating point, at minimum the atmospheric properties actually used by the analytical equations. If future equations depend on Reynolds or Mach effects, that contract must also provide the necessary viscosity and speed-of-sound/temperature authority rather than deriving them from runtime behavior.

No altitude-to-atmosphere table, lapse-rate model, humidity correction, or runtime Aeronautics pressure mapping is authorized by this audit. Those require their own sourced analytical contract.

## Static stability requires derivatives, not tail-volume heuristics

The current CG location and tail-volume coefficients are useful geometric design evidence, but neither is a proof of static stability.

Longitudinal static stability ultimately requires a signed relationship between angle of attack and pitching moment. A future analytical contract must establish quantities sufficient to determine at least the neutral-point/static-margin relation or an equivalent `C_m_alpha` criterion. That requires aerodynamic slope and interference assumptions not present today, such as wing/tail lift-curve slopes, tail dynamic-pressure/efficiency effects, downwash response, aerodynamic-center locations, and the CG relation to the resulting neutral point.

Directional static stability requires an analytical sideslip-to-yawing-moment relation such as `C_n_beta` or an equivalent first-principles derivative construction. Vertical-tail volume near a reference target is not a substitute for that derivative.

Lateral static behavior similarly needs a roll-moment response to sideslip, such as `C_l_beta`, plus the geometric/aerodynamic contributors used to derive it. The current high-wing label and planform geometry do not by themselves prove dihedral effect or roll stability.

Any future static-stability acceptance must preserve sign conventions explicitly and must not infer derivative values from Create/Aeronautics motion.

## Stall and low-speed envelope require lift-curve authority

The present compiler accepts one design `C_L = 0.55`; it does not contain `C_L(alpha)`, `C_Lmax`, stall angle, flap state, or a post-stall model. Therefore it cannot compute an authoritative stall speed or certify low-speed margin.

A future low-speed envelope must introduce a sourced target-neutral lift model or bounded coefficient contract before using relations such as `V_stall = sqrt(2W/(rho S C_Lmax))`. The numerical value of `C_Lmax` must not be tuned until runtime lift happens to match a desired speed.

The same boundary applies to high-angle control authority: a runtime sail force observation can validate target realization at a declared test point, but it cannot supply missing analytical lift-curve or stall physics.

## Trim and control authority are separate from stability

Static stability does not imply trim, and control-surface motion does not imply sufficient trim authority.

A future trim contract must solve force and moment equilibrium at each declared operating point using analytical aerodynamic contributions and control derivatives. For longitudinal trim this means, at minimum, enough authority to solve lift/weight balance and pitching-moment balance with an elevator/control variable. Directional/lateral trim requires corresponding side-force, roll, and yaw moment authority where relevant.

The pitch/roll semantic contract from AIRCRAFT-DESIGN-001 defines desired moment signs and feasible discrete control-surface partitions. It intentionally does not provide control derivatives. Yaw runtime work may prove a real rudder produces the correct sign of physical yaw moment, but that observation remains a target-realization qualification—not an analytical derivative to be fitted back into this model.

## Dynamic stability requires inertia and damping authority

The current mass ledger is one-dimensional and resolves only longitudinal CG. It cannot provide an aircraft inertia tensor or products of inertia.

A future dynamic-stability model must therefore introduce target-neutral mass-property authority sufficient for angular dynamics, including the relevant moments/products of inertia about the chosen body axes. It also requires aerodynamic rate/damping derivatives and control derivatives appropriate to the modeled modes.

Without those quantities, the compiler cannot legitimately classify or time-resolve longitudinal short-period/phugoid behavior, roll subsidence, Dutch roll, or spiral behavior. Runtime motion that appears damped or divergent is useful falsification/realization evidence but must not silently become the analytical damping model.

## Required staged qualification sequence

The recommended aircraft-owned sequence after the current persistence/control prerequisites is:

1. **Environment contract:** define sourced target-neutral atmospheric operating points and provenance. Keep runtime mappings separate.
2. **Aerodynamic coefficient model:** introduce only the minimum sourced coefficient/slope relations required for the intended envelope; preserve declared assumptions and validity ranges.
3. **Static stability:** derive longitudinal, directional, and lateral static derivatives/margins analytically with explicit sign conventions and bounded validity.
4. **Stall/trim envelope:** add `C_L(alpha)`/`C_Lmax` authority as needed, then solve bounded trim points and reject points with insufficient control margin.
5. **Mass properties:** extend beyond one-dimensional CG to the inertia authority required by the dynamic model.
6. **Dynamic stability:** derive/evaluate the chosen linearized modes and damping criteria over the already-trimmed analytical envelope.
7. **Target realization checks:** use accepted Sable/Aeronautics force/pose observation seams to prove that compiled mechanisms produce the intended qualitative signs and finite forces/moments at declared test points. Do not fit analytical coefficients to these observations.
8. **Closed-loop handling:** only after open-loop analytical stability and control authority exist, qualify any controller/autopilot/human-input response as a separate layer.
9. **Stable powered flight:** demonstrate bounded sustained flight scenarios against explicit machine criteria.
10. **Human flight/feel:** final manual gate after machine qualification; subjective handling feedback may drive a new design revision but may not retroactively redefine prior analytical proof.

The stages are ordered so that each downstream result depends on explicit upstream physics rather than on favorable target-runtime behavior.

## Fail-closed evidence boundaries

Future aircraft code and ledgers should distinguish at least these evidence classes:

- **analytical input authority:** declared environment, coefficient, mass, geometry, and validity-range assumptions;
- **analytical derived authority:** forces, moments, margins, trim states, modes, and rejected envelope points computed only from the analytical contract;
- **target realization authority:** exact-stack resource/state/mechanism identity plus observed force/pose/sign behavior;
- **closed-loop behavior:** controller-plus-aircraft response under declared initial/environment conditions;
- **human evaluation:** subjective feel and usability after machine gates.

A PASS in one class must never silently upgrade another. In particular, Create/Aeronautics force magnitude is not an aerodynamic coefficient source, a tail-volume match is not a static-stability derivative, and visually stable motion is not dynamic-stability proof.

## What this audit does not establish

This audit adds no aerodynamic coefficients, atmosphere model, stability derivative, inertia tensor, stall speed, trim solution, controller, or runtime acceptance threshold. It does not qualify pitch, roll, yaw handling, atmosphere envelope, static stability, dynamic stability, stable powered flight, or human feel.

Its purpose is to make the next analytical extensions fail closed: every future stability or envelope claim must identify the missing physical quantity it has added, its provenance and validity range, and the exact boundary between first-principles analytical authority and target-runtime realization evidence.
