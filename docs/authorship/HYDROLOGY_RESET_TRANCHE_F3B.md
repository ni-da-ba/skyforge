# Hydrology reset tranche F3B — bounded confluence head compatibility

**Status:** mathematical transition tranche under issue #1084  
**Depends on:** accepted F3/F3A finite confluence geometry and F2A bounded QP  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

F3B answers the first coupled transition question after F3A:

> can all ordinary incident hydraulic legs at one finite confluence share one exact node head while
> remaining inside the existing D2-derived pointwise head envelopes and longitudinal-grade limits?

The answer is explicit evidence: `SOLVED`, `INFEASIBLE`, `NUMERICAL_FAILURE`, or
`CASCADE_COUPLED`.

No F3B outcome directly grants terrain authority.

## Shared head-envelope authority

Before F3B, F2C constructed D2-derived pointwise head bounds internally.

F3B extracts that construction into `SkyIslandHydraulicHeadEnvelopePlanner` so ordinary F2C reaches
and transition solvers consume one implementation for:

- preferred water-surface target;
- maximum centerline lowering;
- lateral recovery;
- relief/valley-width compatibility;
- bank containment;
- profile-sensitive valley width.

This is a refactor of authority, not a new envelope. F2C regression tests must remain unchanged.

## Confluence variables

For a confluence with `m` incident finite F3A legs, F3B solves:

```text
H_node
H_boundary[0..m-1]
```

There is exactly one node variable, so confluence head continuity is exact rather than penalty-based.

Each finite boundary has its own D2-derived admissible interval. The node interval is the intersection
of the incident node-side D2 envelopes.

If that shared interval is empty, the confluence is `INFEASIBLE`.

## Directional constraints

For an incoming leg:

```text
0 <= H_boundary - H_node <= G_max * L_leg
```

For an outgoing leg:

```text
0 <= H_node - H_boundary <= G_max * L_leg
```

where `G_max` is the accepted D2 maximum longitudinal grade for the owning semantic reach and
`L_leg` is the exact F3A retreat length.

Thus an F3B solution may not climb upstream/downstream or use the transition to bypass D2 grade
authority.

## Objective

The existing deterministic F2A bounded QP is reused.

Each finite boundary receives half-leg arc-length weight, and the shared node receives the sum of all
incident half-leg weights. Targets remain the existing preferred water-surface heads.

No new optimizer, penalty coefficient, or hydraulic loss constant is introduced.

## CASCADE coupling

If an incident finite confluence leg touches an authored `CASCADE` profile at either its node or
finite boundary, F3B returns `CASCADE_COUPLED`.

F3B does not disguise a confluence/drop interaction as ordinary head continuity. F3C owns the
separate authored-CASCADE discontinuity experiment; a later combined transition tranche must resolve
true confluence/cascade overlap.

## Evidence

The fixed confluence corpus records, for each semantic confluence:

- status;
- leg count;
- shared-node admissible interval;
- solved shared node head when available;
- QP objective and KKT residual evidence;
- explicit diagnostic on deferred/failing cases.

The corpus includes the established confluence/control specimens and is deterministic.

## Fixed-corpus result

The current fixed confluence corpus establishes three distinct outcomes without widening any bound:

- control-241 / node 671: `SOLVED`;
- confluence-632 / node 710: `SOLVED`;
- stress-512 / node 1729: `SOLVED`;
- retained-83 / node 895: `INFEASIBLE` because an incident ordinary-side D2 envelope is empty;
- legacy-control-649 / node 995: `CASCADE_COUPLED`;
- control-77 / node 897: `CASCADE_COUPLED`.

The solved cases have zero printed primal/stationarity/complementarity/dual residual at nine decimal
places. The confluence-632 solve selects a shared node head of approximately 129.972 world units
inside an incident-envelope intersection of approximately [127.104, 131.686].

This distribution is the intended evidence shape: ordinary confluences can solve, existing D2
infeasibility remains visible, and true confluence/CASCADE overlap remains deferred rather than
being hidden by a permissive transition rule.

## Hard invariants

F3B:

1. preserves C2/F3/F3A geometry exactly;
2. uses one exact shared node-head variable;
3. uses the same D2 pointwise envelope authority as F2C;
4. preserves non-climbing directional flow;
5. preserves D2 longitudinal-grade limits on finite ordinary legs;
6. treats CASCADE overlap as explicit coupling;
7. widens no bound to manufacture feasibility;
8. contributes zero terrain delta;
9. changes no Minecraft behavior.

## Next boundary

A solved local confluence is necessary but not sufficient for realization.

Later work must combine solved transition boundaries with independently qualified ordinary spans,
CASCADE transitions where present, and any terminal/basin boundary conditions before a transition may
receive terrain authority.
