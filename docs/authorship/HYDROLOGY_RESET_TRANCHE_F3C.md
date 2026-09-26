# Hydrology reset tranche F3C — authored CASCADE head discontinuity compatibility

**Status:** mathematical transition experiment under issue #1084  
**Depends on:** accepted F3/F3A and F3B head-envelope authority  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

D3 established that the rejected primary-287 mixed reach already carries most of its accumulated
downhill drop through authored `CASCADE` profiles. F3/F3A then gave every maximal contiguous CASCADE
run an exact finite C2 interval.

F3C tests the next narrow hypothesis:

> ordinary D2-compatible hydraulic states on each side of an authored internal CASCADE may be joined
> by an explicit bounded non-climbing head discontinuity, without imposing ordinary longitudinal
> grade across the CASCADE interval.

This is compatibility evidence only. It does not yet solve the intervening ordinary subreaches or
grant transition terrain authority.

## Boundary authority

For an internal maximal CASCADE run:

- the upstream boundary uses the immediately preceding ordinary profile class;
- the downstream boundary uses the immediately following ordinary profile class;
- both boundary states use the shared D2 pointwise head-envelope implementation introduced by F3B;
- the current semantic-reach D2 limit set is retained, so F3C does not silently recalibrate mixed
  reaches.

A CASCADE run touching the start or end of its semantic macro reach is `BOUNDARY_COUPLED`. Such a
run may interact with source, confluence, terminal, or retained-basin ownership and is not solved
locally.

## Discontinuity problem

For each internal CASCADE interval F3C solves two head variables:

```text
H_up
H_down
```

with ordinary-side D2 box bounds and objective targets inherited from the head-envelope authority.

Across the authored CASCADE interval:

```text
0 <= H_up - H_down <= D_authored
```

where `D_authored` is the sum of positive watershed surface-potential drops on the owned CASCADE
profiles, converted through the descriptor relief budget.

The lower bound prevents an uphill hydraulic jump. The upper bound prevents transition code from
claiming more discontinuity authority than the authored coarse drop structure supplies.

F3C deliberately does **not** apply the ordinary maximum-longitudinal-grade constraint across the
CASCADE interval. Removing that one constraint is the mathematical content of explicit drop
ownership.

The quadratic objective uses equal half-interval arc-length weights at the two finite boundaries.
The existing deterministic F2A bounded QP remains the only numerical optimizer.

## Why the authored drop is an upper bound rather than an equality

The reset explicitly demoted coarse planning geometry from immutable physical geometry. Therefore the
coarse watershed drop is not promoted back into an exact physical water-surface jump.

Using it as a maximum preserves semantic authority while allowing the continuous solution to choose a
smaller discontinuity if ordinary-side terrain/head compatibility supports one.

A zero solved discontinuity is valid compatibility evidence but is not by itself sufficient to
realize a visible cascade.

## Required outcomes

Every F3C interval returns one of:

- `SOLVED`;
- `INFEASIBLE`;
- `NUMERICAL_FAILURE`;
- `BOUNDARY_COUPLED`.

No status is converted directly into terrain mutation.

## Fixed-corpus result

The fixed evidence supports explicit internal discontinuity ownership without widening D2:

Primary key 287 has three internal maximal CASCADE runs and all three solve:

```text
profiles 7..8    authored max drop =  11.6863 world   solved drop =   7.4710
profiles 11..15  authored max drop =  79.5437 world   solved drop =  33.5600
profiles 27..32  authored max drop = 122.4525 world   solved drop = 112.5487
```

All three QPs print zero primal residual at nine decimal places. The third run uses most, but not all,
of its authored discontinuity authority, demonstrating why the coarse drop is correctly an upper
bound rather than an equality.

The control distribution remains fail-closed:

- legacy-control-649 reach 710->995 / profiles 4..5 is `INFEASIBLE` because an ordinary-side D2
  envelope is empty;
- legacy-control-649 reach 995->2095 / profiles 16..20 is `SOLVED`;
- retained-83 reach 660->895 / profiles 3..4 is `INFEASIBLE`;
- retained-83 reach 895->595 / profiles 1..5 is `SOLVED`;
- CASCADE runs that touch semantic reach boundaries remain `BOUNDARY_COUPLED`, including the fixed
  stress-512 cascade-only reaches.

Thus F3C removes exactly one inappropriate requirement—ordinary longitudinal continuity across an
authored drop—while leaving unrelated D2 failures and transition coupling visible.

## Hard invariants

F3C must preserve:

1. exact F3/F3A CASCADE ownership;
2. exact accepted C2 centerline geometry;
3. the shared D2 pointwise head-envelope implementation;
4. non-climbing hydraulic direction across the drop;
5. solved drop no larger than authored CASCADE downhill-drop authority;
6. explicit deferral of semantic-boundary CASCADE runs;
7. deterministic QP evidence;
8. zero terrain delta;
9. unchanged Minecraft behavior.

## Next boundary

If fixed evidence shows useful internal `SOLVED` cases, the next tranche must partition mixed macro
reaches around F3C transitions and require every intervening ordinary span to independently solve and
re-pass geomorphic qualification.

F3C success alone does not rescue a mixed reach. Remaining ordinary-span failures continue to belong
to route/node refinement or fail-closed rejection.
