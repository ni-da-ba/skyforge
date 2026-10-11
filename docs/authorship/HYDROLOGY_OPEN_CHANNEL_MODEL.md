# Open-channel hydrology model contract (working proposal)

**Status:** implementation design proposal under issue #1452; not accepted product authority yet  
**Scope:** replace heuristic hydraulic head feasibility with a physically interpretable calculation while retaining the authored drainage graph and independent geomorphic rejection

## Why this boundary is needed

The current pipeline has reliable authored topology and relative discharge ordering, but the bounded profile planner currently turns terrain/D2 plausibility bounds into pointwise water-surface intervals and asks a generic quadratic program to find a head field inside them. That is a useful feasibility check, but it is not an open-channel hydraulics model. Empty D2 intervals can make the water solve infeasible before the hydraulics are evaluated.

Current hydraulic geometry carries relative discharge in [0,1], dimensionless water-depth potential, bankfull half-width in world units, and normalized pre-fluvial terrain elevation. These do not identify physical discharge, Manning roughness, a wetted cross-section, energy slope, or a boundary water level. No conversion to SI should be invented.

## Proposed pipeline

```
authored drainage graph + catchment discharge ordering
  -> terrain-conditioned flow diagnostics (conditioned elevation, receiver(s), accumulation)
  -> constrained continuous centerline candidate within semantic corridor
  -> explicit cross-section geometry and calibrated hydraulic parameters
  -> open-channel water-surface / bed solution with named boundary conditions
  -> independent D2 geomorphic qualification (may reject; does not alter the hydraulic solution)
  -> F3 component/junction/transition qualification
  -> existing F4C quantization -> existing F4D/adapter -> human visual gate
```

Conditioned DEM routing is evidence for and a proposal mechanism for routes; it must not silently replace authored catchment, fate, or graph connectivity. A D8 receiver is an acceptable deterministic first diagnostic, while D-infinity/multiple-flow-direction evidence may later be compared for diffuse hillslope convergence. Tie handling and edge/no-data behavior must be explicit. Priority-Flood conditioning identifies drainage paths through depressions but does not imply that every depression should be filled in the authored result; retained basins remain transition-owned.

## Hydraulic equations and domain

For an ordinary gradually-varied reach, define downstream station (x), bed elevation (z_b), water depth (y), top width (T(y)), area (A(y)), wetted perimeter (P(y)), hydraulic radius (R=A/P), and discharge (Q). For a trapezoidal section with bottom width (b) and side slope (m) (horizontal:vertical):

[
A(y)=y(b+my),quad P(y)=b+2ysqrt{1+m^2},quad T(y)=b+2my.
]

Manning conveyance gives:

[
Q=rac{1}{n}A R^{2/3}S_f^{1/2},qquad
S_f=left(rac{nQ}{A R^{2/3}}ight)^2.
]

For a prismatic reach with hydrostatic pressure and energy coefficient (alpha), the gradually-varied-flow equation is:

[
rac{dy}{dx}=rac{S_0-S_f}{1-Fr^2},quad
S_0=-rac{dz_b}{dx},quad
Fr^2=rac{alpha Q^2T}{gA^3}.
]

The implementation uses the standard-step energy equation, solving iteratively for controlled subcritical or supercritical profiles. Reach friction loss uses the average-conveyance method (the HEC-RAS default), with section-specific discharge for gradual distributed inflow and one velocity coefficient. The exploratory ordinary-span solver now applies the owner-selected source closure for a source-to-CASCADE-critical span: Manning normal depth from a least-squares downstream bed grade over the first quarter of the reach. A supercritical source is joined to the downstream-controlled subcritical profile only by a localized hydrostatic specific-force/momentum-matched jump with positive energy loss; a subcritical source is accepted only if its independently downstream-controlled profile matches the normal depth. Near-critical, unsupported, or inconsistent cases fail closed. This source closure is a game-scale modeling hypothesis, not a universal boundary condition. The jump assumes momentum coefficient one and negligible bed force, friction, and lateral-inflow momentum across the short jump. Abrupt confluences, drops, and basins still need explicit transition solvers; this ordinary-span solver does not supply their junction/basin equations or lateral momentum/contraction/expansion losses. Normal depth remains only a uniform-flow reference state, not a backwater, confluence, lake, or cascade solution. These additions are exploratory and are not yet wired into the production F4C/F4D adapter.

## Boundary and transition rules

- Each ordinary profile span needs an identified control/boundary stage at the end dictated by flow regime (subcritical downstream control; supercritical upstream control), plus a documented rule for critical/near-critical transitions. For a source-to-CASCADE-critical exploratory span without an authored source stage, the currently owner-selected rule is a Manning normal-depth source condition using a least-squares local bed grade; accept only a downstream-controlled profile consistent with that depth, or a supercritical branch with an admissible momentum-matched jump. This is not a closure for arbitrary spans or transitions.
- Confluences should conserve discharge and use an energy balance by default; use a momentum balance only when junction geometry/angle and its assumptions are represented. They cannot be approximated by independently solved reach heads.
- A CASCADE is an explicit head-loss/drop transition with its own upstream and downstream controls. Do not integrate the ordinary GVF equation smoothly across the authored drop.
- Retained basins need a basin water datum, connected depression/spill condition, and inlet/outlet transition solution. They remain fail-closed until those inputs/semantics exist.
- A free edge outfall remains permitted only where explicit terminal-fate policy grants it; missing control data must produce a typed deferral/rejection, not an inferred arbitrary datum.

## Calibration and dimensional consistency

The current semantic (Q_r) is a relative ordering, not a discharge with units. The normalized depth/width curves are currently geometric calibration, not a measured Manning cross-section. Before applying the SI equations to a generated reach, provide either:
1. actual calibrated (Q,n,b,m), bed elevations/energy slope, gravity and boundary stage in consistent units; or
2. an explicitly dimensionless formulation with declared reference length/velocity (or discharge), Froude/gravity scale, resistance parameter, and a calibration procedure.

The owner selected a game-calibrated parameter model rather than waiting for authored physical climate/material inputs, and selected normal depth at source where no stage is authored. The current exploratory sweep screens explicit parameter combinations against accepted key 77 and rejected key 287, with key 700 held out from calibration. The mapping from normalized runoff and geometry to SI quantities remains a calibration hypothesis, not accepted physical truth; report sensitivity to roughness, width/depth, discharge scale, and boundary condition, and do not tune solely to make key 700 pass. No parameter set or ordinary-span success alone establishes a qualified natural component or production hydraulics.

### Provisional event-to-flow mapping

For a bounded small catchment, the standard Rational Method offers a dimensionally explicit bridge
from the existing accumulated effective-runoff potential to discharge:

\[
Q = i\,A_{eff},\qquad
A_{eff}=\left(\sum_j C_j\right)\,\Delta x^2\,s^2
\]

where the current flow accumulation is \(\sum C_j\), grid spacing is \(\Delta x\) in world
units, \(s\) converts world units to metres, and \(i\) is a selected design-storm intensity in
m/s. The code helper accepts rainfall intensity in mm/h and converts units explicitly. This treats
the existing normalized runoff potential as an effective runoff coefficient; that interpretation is
a **calibration hypothesis**, not accepted hydrology truth. The Rational Method also assumes a storm
duration equal to time of concentration and a small basin where peak flow is meaningful. Until the
project supplies/accepts storm intensity, time-of-concentration treatment, runoff-potential mapping,
and catchment-domain evidence, production must not select this mapping silently.

## Separation of solution and geomorphic acceptance

The hydraulic solve returns water surface, bed, section state, flow regime, and residual/error diagnostics. D2 then independently evaluates terrain fit: incision/lowering, bank containment, lateral/valley recovery, ridge occupancy, curvature-width compatibility, and excavation burden. D2 may reject the physically solved candidate. Its bounds must not be fed back as invented hydraulic boundary stages or modified after solving to force acceptance.

A constrained optimization/QP remains suitable for enforcing mathematical continuity, explicit boundary values, and permitted smoothing/regularization after the hydraulics are defined. It must not stand in for Manning conveyance or the energy equation. Any optimization residual must be reported separately from hydraulic equation residuals and geomorphic violations.

## Required evidence before integration

- Analytic tests for rectangular and trapezoidal Manning normal depth and parameter monotonicity.
- GVF tests against closed-form/simple backwater cases or a trusted reference calculation, plus mass/energy residual bounds and convergence under step refinement.
- Explicit source-normal-depth subcritical and supercritical cases, including a successful profile-level momentum-matched jump regression, near-critical fail-closed behavior, and fixed-control discrimination; confluences, CASCADEs, and basins need their own transition evidence and unsupported regimes fail closed.
- Fixed-proving-ground calibration and sensitivity report for accepted ordinary controls, natural key 700, and rejected controls (including key 287); retain the semantic graph unchanged.
- Exact F3E whole-component solve and existing F4C/F4D zero-authority, quantization, ownership, and persistence contracts.
- Only after machine qualification: prepared Minecraft specimen and human review. A Manning unit test or a semantic overlay is not a realized hydrology gate.

## References

- Standard-step energy equation: USACE HEC-RAS technical reference, [equations for basic profile calculations](https://www.hec.usace.army.mil/confluence/rasdocs/ras1dtechref/6.0/theoretical-basis-for-one-dimensional-and-two-dimensional-hydrodynamic-calculations/1d-steady-flow-water-surface-profiles/equations-for-basic-profile-calculations).
- Manning friction slope and average-conveyance reach loss (the HEC-RAS default): USACE HEC-RAS, [friction loss evaluation](https://www.hec.usace.army.mil/confluence/rasdocs/ras1dtechref/6.3/theoretical-basis-for-one-dimensional-and-two-dimensional-hydrodynamic-calculations/1d-steady-flow-water-surface-profiles/friction-loss-evaluation).
- One-dimensional steady-flow assumptions and exclusions: USACE HEC-RAS, [program limitations](https://www.hec.usace.army.mil/confluence/rasdocs/ras1dtechref/6.4/theoretical-basis-for-one-dimensional-and-two-dimensional-hydrodynamic-calculations/1d-steady-flow-water-surface-profiles/1d-steady-flow-program-limitations).
- Junction energy/momentum modeling assumptions: USACE HEC-RAS, [modeling stream junctions](https://www.hec.usace.army.mil/confluence/rasdocs/ras1dtechref/6.1/overview-of-optional-capabilities/modeling-stream-junctions).
- Depression conditioning/flow direction/accumulation foundations: Jenson & Domingue, USGS, [Extracting topographic structure from digital elevation data for geographic information system analysis](https://pubs.usgs.gov/publication/70142175); Barnes et al., [Priority-Flood](https://doi.org/10.1016/j.cageo.2013.04.024); Tarboton, [D-infinity flow directions](https://doi.org/10.1029/96WR03137).
