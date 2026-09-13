# AIRCRAFT-001 first-principles aircraft compiler v0.1

## Decision

Begin the aircraft lane with a mathematically explicit pre-design solver before permitting Minecraft/Create block identity into the design process.

The first specimen is a small high-wing, nose-tractor, conventional-tail utility monoplane. It is a bounded configuration proof, not a universal airplane generator and not an exact historical Bellanca model.

## Why this is separate from the structure compiler

The reusable architecture is the compiler *pipeline*, not building grammar:

```text
semantic intent
-> constrained geometry
-> target-neutral IR
-> target adapter
-> runtime validation
```

Buildings solve occupancy, circulation, facade, roof, and architectural support. Aircraft require coupled planform, balance, propulsion/clearance, structure, control, assembly, and flight constraints. Treating aircraft as moving buildings would hide the physics that should be authoritative.

## v0.1 mathematical kernel

### Design condition

Dynamic pressure:

`q = 1/2 rho V^2`

Lift:

`L = q S C_L`

At the declared design condition, the required reference wing area is therefore:

`S_required = W/(q C_L)`.

NASA Glenn is the equation authority. The specimen uses `rho=1.225 kg/m^3` as an explicit sea-level standard-atmosphere input; this is not yet Skyforge atmospheric canon.

### Straight-taper planform

For full span `b`, root chord `c_r`, tip chord `c_t`, and taper `lambda=c_t/c_r`:

`S = b(c_r+c_t)/2`

`AR = b^2/S`

`c_MAC = (2/3)c_r(1+lambda+lambda^2)/(1+lambda)`.

The MAC expression is checked against Virginia Tech AOE wing notes. Aspect-ratio terminology follows NASA Glenn.

### Induced-drag comparison

`C_Di = C_L^2/(pi e AR)`.

This is used only as a relative analytical design proxy. `e=0.8` is a declared specimen assumption, not a Create Aeronautics calibration and not a general constant.

### Mass balance

For declared lumped masses:

`x_CG = sum(m_i x_i)/sum(m_i)`.

The mass ledger closes exactly to the mission gross mass. FAA weight-and-balance material is used as qualitative authority that CG location is consequential to stability/control. v0.1 does not infer stability merely from CG/MAC position.

### Tail volumes

`V_H = S_H l_H/(S c_MAC)`

`V_V = S_V l_V/(S b)`.

ERAU's aircraft-stability material is the reference for the definitions and for representative GA single-engine values near `V_H=0.7` and `V_V=0.04`. These values are search references only; actual stability and control must later be proven with a fuller model and then the target runtime.

## Search formulation

v0.1 enumerates bounded discrete design domains. No random search is permitted.

Hard filters:

1. valid taper and positive dimensions;
2. wing location contained in the fuselage station envelope;
3. declared specimen aspect-ratio interval;
4. declared cruise-lift residual tolerance;
5. mass-ledger closure;
6. declared specimen CG/MAC interval;
7. positive tail arms;
8. tail-volume reference tolerances.

The feasible set is ordered lexicographically by:

1. cruise lift residual;
2. combined tail-volume reference error;
3. analytical induced-drag coefficient;
4. geometric complexity proxy;
5. exact geometry tuple.

This makes every selected design explainable and reproducible. Future versions can replace or extend objective terms only with an explicit design reason and test evidence.

## Target boundary

`resolved.json` is the aircraft-design IR. It contains no concrete Minecraft/Create/Aeronautics resource locations.

The eventual target stack should be:

```text
AircraftDesignIR
-> block-space envelope / skeleton
-> Create/Aeronautics capability intents
-> Minecraft/Create target solver
-> assembly/connectivity validation
-> in-engine flight measurement
```

Analytical equations are not to be tuned until they imitate the mod. Runtime measurements remain a separate authority and can later inform a documented surrogate model.

## Explicitly deferred

- airfoil selection and section lift curve;
- stall / `C_Lmax`;
- neutral point and static margin;
- control-surface sizing and authority;
- dynamic stability derivatives;
- structural bending/load paths;
- propulsion power/thrust matching;
- landing gear loads and ground handling;
- Create/Aeronautics block/assembly semantics;
- Minecraft voxel styling.

Those omissions are intentional. v0.1 is accepted only as an analytical geometry/balance foundation.
