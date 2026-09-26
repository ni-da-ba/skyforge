# Hydrology reset tranche F3D — ordinary-span partition, solve, and requalification

**Status:** transition-integration evidence under issue #1084  
**Depends on:** C3-backed D1/D2 authority; F3A finite geometry; F3B confluence compatibility; F3C CASCADE compatibility  
**Terrain mutation:** none  
**Minecraft changes:** none

## Purpose

F3B and F3C prove local transition compatibility. They do not prove that the ordinary channel geometry
between those transitions remains physically admissible.

F3D removes every explicitly transition-owned interval from the parent F2B centerline and asks the
remaining ordinary spans to stand on their own:

```text
accepted C2/F2B parent geometry
    -> subtract finite confluence transition legs
    -> subtract authored CASCADE intervals
    -> apply only solved transition boundary heads
    -> solve each remaining ordinary span
    -> recompute D1 measurements on that finite span
    -> apply D2 to that span's retained profile composition
```

A locally solved confluence or cascade cannot rescue an ordinary span that still needs unacceptable
terrain surgery.

## Transition boundaries

### Confluences

An ordinary span adjacent to a confluence begins or ends at the **finite F3A leg boundary**, not at
the shared node.

If F3B solved the confluence, F3D fixes that span boundary to the solved finite-leg head.

If F3B returned `INFEASIBLE`, `NUMERICAL_FAILURE`, or `CASCADE_COUPLED`, the boundary remains
`DEFERRED`. F3D does not substitute a free head.

### CASCADE intervals

Every finite F3C CASCADE interval is removed from ordinary-span geometry.

If F3C solved the interval:

- the upstream ordinary span receives the solved upstream cascade head;
- the downstream ordinary span receives the solved downstream cascade head.

If the cascade is infeasible, numerically failed, or boundary-coupled, adjacent ordinary geometry
remains transition-deferred.

### Sources and edge outlets

A source boundary and an explicit watershed `EDGE_OUTLET` remain free within their D2 pointwise
head envelopes. F3D introduces no arbitrary source or outlet datum.

### Retained and unresolved terminals

`RETAINED_OPEN_WATER`, `RETAINED_WETLAND`, and `UNRESOLVED` terminal fates remain deferred.
F3D does not turn the absence of basin calibration into a free outlet.

### Overlapping transition ownership

If a CASCADE interval overlaps a finite confluence transition leg, the ordinary-facing boundary is
marked deferred for a later combined transition solve. F3D does not compose independently solved
local transition models across overlapping geometry.

## Span profile authority

A finite confluence boundary may trim a span inside a coarse semantic profile. Therefore F3D does not
fabricate a new semantic macro reach merely to reuse D2.

Instead:

- each span sample carries the ordinary profile kind from the parent semantic interval;
- every ordinary profile interval with positive overlap contributes to the span qualification class;
- a span containing one ordinary kind uses that class;
- a span containing both ALLUVIAL and INCISED geometry uses the existing MIXED aggregate class;
- CASCADE is forbidden inside an ordinary span.

This means removing a CASCADE interval also removes its permissive transition semantics from the
ordinary span's D2 class.

## Shared D1/D2 kernel

F3D extracts the D1 measurement vector from whole-reach container identity.

Whole reaches and finite ordinary spans now use the same implementation for:

- centerline lowering;
- lateral recovery;
- bank containment;
- depth/width;
- relief/valley width;
- normalized excavation burden and volume proxy;
- curvature × width;
- C3 fixed-physical-scale arc-length ridge occupancy;
- longitudinal grade.

D2 violation evaluation is likewise shared.

The refactor must reproduce existing whole-reach D1 values exactly.

## Span hydraulic solve

For every non-deferred span, F3D uses the existing deterministic F2A bounded QP.

At each sample:

- the existing profile-sensitive D2 pointwise head envelope is evaluated;
- solved transition heads intersect that envelope as exact boundary values;
- a transition head outside the D2 envelope makes the span infeasible;
- ordinary longitudinal grade remains bounded by the span's D2 class.

There is no longitudinal ordinary-grade constraint across removed CASCADE geometry because that
geometry is not part of the ordinary span.

## Outcomes

Each span is exactly one of:

- `SOLVED_QUALIFIED`;
- `SOLVED_REJECTED`;
- `INFEASIBLE`;
- `NUMERICAL_FAILURE`;
- `BOUNDARY_DEFERRED`.

Only `SOLVED_QUALIFIED` means the ordinary span independently passes the current evidence-backed
D2 envelope. It still grants no terrain authority in F3D.

## Hard invariants

F3D must preserve all of the following:

1. no ordinary span contains a CASCADE sample or positive-width CASCADE interval;
2. confluence spans stop at finite F3A boundaries rather than shared nodes;
3. only F3B/F3C `SOLVED` outcomes may provide exact transition head values;
4. retained/unresolved terminal ownership remains deferred;
5. overlapping transition ownership remains deferred;
6. source and edge-outlet heads remain free inside D2 rather than receiving invented constants;
7. span D1 metrics use the exact same measurement kernel as whole reaches;
8. span D2 violations use the exact same evaluator as whole reaches;
9. a solved transition head may not override an incompatible D2 pointwise envelope;
10. no threshold is widened to make a span pass;
11. F3D contributes zero terrain delta;
12. Minecraft behavior remains unchanged.

## Next boundary

If the fixed corpus demonstrates useful `SOLVED_QUALIFIED` ordinary spans, the next tranche may
assemble **non-overlapping solved transition + qualified ordinary-span chains** as realization
candidates.

That later assembly must still fail closed on:

- any rejected/infeasible/deferred span;
- any unresolved combined confluence/CASCADE transition;
- retained-basin production authority;
- unresolved terminal fate.

Only after a complete chain owns its geometry and boundary conditions can transition terrain
realization be considered.
