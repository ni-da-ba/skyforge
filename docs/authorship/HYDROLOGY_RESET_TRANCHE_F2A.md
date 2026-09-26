# Hydrology reset tranche F2A — deterministic bounded hydraulic QP primitive

**Status:** implementation candidate under issue #1084  
**Depends on:** F2 coupled bounded-profile contract  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

F2A implements and proves the numerical optimization primitive required by the F2 network-global
longitudinal solve before any planner consumes it.

The solver is intentionally specialized to the exact F2 convex structure:

```text
minimize  0.5 * sum_i w_i (H_i - Hhat_i)^2

subject to
          L_i <= H_i <= U_i
          l_k <= H_a - H_b <= u_k
```

with strictly positive diagonal weights and finite bounds.

Shared semantic nodes are represented by shared variables. Exact fixed datums and exact difference
relations become equality constraints rather than large penalty weights.

## Feasibility before optimization

Box and pair-difference bounds form a standard difference-constraints graph. F2A first runs a
deterministic Bellman-Ford feasibility pass with an explicit anchor datum.

A negative cycle means the hydraulic boundary problem is **INFEASIBLE**. The optimizer is not allowed
to widen grade, excavation, or datum bounds to recover feasibility.

## Optimization

Starting from a feasible point, F2A uses a feasible primal active-set method for the strictly convex
quadratic projection.

Each iteration:

1. maintains all active equalities and binding inequalities;
2. removes metric-linearly-dependent active rows deterministically;
3. solves the equality-constrained search-direction KKT system;
4. uses a positive-definite Cholesky factorization of the active normal matrix;
5. either advances to the first blocking inequality or removes a dual-infeasible active inequality;
6. terminates only when the KKT residual budget is satisfied.

Constraint and active-set ordering is canonical and independent of caller list order.

## Numerical conditioning

Before solve:

- heads are translated by a deterministic local datum;
- heads/difference bounds are uniformly scaled to an O(1) problem envelope;
- objective weights are normalized by a common positive factor without changing the optimum;
- constraint rows retain the sparse +/-1 difference form;
- near-dependent active rows are detected in the inverse-Hessian metric with re-orthogonalization;
- active search directions are computed by weighted QR projection, avoiding the condition-number
  squaring inherent in normal-equation solves.

An ill-conditioned or rank-ambiguous active system is **NUMERICAL_FAILURE**, never acceptance.

## Evidence

The focused test corpus includes:

- unconstrained analytic projection;
- active box projection;
- simultaneous upper/lower difference-bound activation with analytic optimum;
- symmetric/constraint-order permutation invariance;
- exact retained-basin datum equality;
- exact pair-difference equality;
- large absolute world datum with small hydraulic head differences;
- explicit drop partition without invented cross-drop continuity;
- contradictory exact boundaries -> INFEASIBLE;
- redundant active constraint handling;
- variable-order permutation equivalence.

This tranche does not yet replace the staged hydraulic network planner. The next tranche must construct
the actual F2 world-space head variables/bounds from C2/D2/E2 semantics, consume this solver, regenerate
D0/D1/D2/D3/E1 evidence, and prove discretization convergence before gaining additional terrain
authority.
