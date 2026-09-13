# ASSET-001 v0.12 — Mathematical optimization stage

Status: bounded Guild-branch proof, draft PR #489.

## Purpose

v0.1-v0.11 proved that a deterministic compiler can lower Guild architectural semantics into a real Minecraft structure and that repeated in-game review can improve the result. v0.12 changes the authority model: recurring architectural relationships are represented as constraints, graphs, grammars, and fields before Minecraft blocks are treated as the final evidence.

The resulting lowering is still deliberately bounded to the small temperate Guild branch family. This is not a universal procedural architect.

## 1. Finite-domain architectural optimization

The current proof solves the discrete design vector

```text
x = (mainRoofRise, roofOverhang, publicWindowWidth)
```

over a finite candidate domain. Hard feasibility rules protect physical/Minecraft constraints. Reference ratios contribute target-fit penalties.

For a ratio r with target t and working-reference interval [a,b], the target term is normalized as

```text
E(r) = ((r - t) / max(t-a, b-t))^2
```

and the total objective is

```text
J(x) = sum_i w_i E_i(x).
```

The solver is deterministic. Equal scores resolve lexicographically by named variable/value pairs, never by Python hash ordering.

The bootstrap specimen currently evaluates 18 integer candidates. Its selected design is the already-established 4-block main rise, 1-block overhang, and 3-block public-window width. The significance is not that v0.12 discovered a radically different proportion; it formalized why those integer dimensions survive the current evidence envelope.

### Evidence boundary

`guild_branch_temperate_small_working_reference_v0.1.json` is an **internal working reference** derived from Guild canon and prior in-game review. It is not measured master-builder data. The optimizer preserves that provenance in the compiled artifact. Measured donor/reference profiles can later enter the same interface through `reference_analysis.py` without silently changing provenance.

## 2. Attributed split grammar for facades

Public-hall facade repetition is represented as a bay production rather than independent window coordinates:

```text
BAY(post_i, post_i+1)
  -> jamb + centered opening + residual infill/jamb
```

with a semantic interruption production for the entrance bay.

For the current 3 x 5-block public rhythm and 3-block opening width, the solved grammar yields:

```text
south: [1..3], ENTRANCE, [11..13]
north: [1..3], [6..8], [11..13]
```

The compiler records the production trace and an MDL-style description-length proxy. The proxy penalizes rule vocabulary and one-off exceptions; it is a compactness heuristic, not a claim of a statistically inferred grammar.

## 3. Facade layer contracts

v0.12 gives facade responsibilities explicit semantic layers:

```text
structure
opening_void
glazing
envelope
hardware
furnishing
```

Incompatible claims are checked before acceptance. Current contracts include:

```text
structure      !~ opening_void
envelope       !~ opening_void
glazing        !~ structure
glazing        !~ envelope
glazing        !~ hardware
glazing        !~ furnishing
```

This converts earlier overwrite failures — furniture replacing panes, structure occupying intended apertures, wall detail consuming openings — into formal validation errors.

## 4. Shared roof field

The rectangular Guild proof now uses a `TwoEaveGableField`. It is the closed-form two-eave wavefront case and intentionally exposes a field interface suitable for later replacement by a general straight-skeleton or weighted-wavefront solver.

For the current discrete implementation:

```text
h(z) = roofBase + floor(rise * edgeDistance(z) / halfRun)
```

where `edgeDistance(z)` is distance to the nearer eave after clamping to the roof footprint.

The same field now drives:

```text
weather-skin edge locations
exterior eave stairs
ridge location and cap
interior rafter targets
ridge beams
tie-beam stations
```

so inside/outside roof systems no longer derive from unrelated coordinate rules.

### Connected digital rafters

A sampled sloped line can become diagonally disconnected on an integer voxel lattice. v0.12 therefore converts the field samples into a 4-connected digital polyline. Rising transitions advance then climb; falling transitions descend then advance. This keeps bridge cells under the roof field while guaranteeing face-connected framing.

## 5. Structural support graph

Structural-frame and foundation cells become a 6-neighbor graph:

```text
Gs = (Vs, Es)
```

where edges join face-adjacent structural cells. Roof rafters, ridge beams, and tie beams are target nodes; low/foundation structure supplies grounded support seeds. Acceptance requires every roof-frame target to have a graph path to at least one support.

This is a **structural-legibility invariant only**. It is not finite-element analysis, code compliance, load-capacity certification, or a claim that Minecraft block sections represent real timber sizing.

v0.12 also adds explicit working-wing post footings, eave posts, and wall plates so the working roof has the same topological support requirement as the public hall.

## 6. Space graph and visibility

The public interior is sampled as a 4-neighbor walkability graph over floor cells with two-block player clearance. The compiler currently records:

```text
entrance -> service-counter shortest-path distance
Manhattan path stretch
connected fraction from entrance
entrance and service closeness centrality
entrance visibility count
entrance -> service line of sight
```

For a node v, closeness is

```text
C(v) = (|R(v)| - 1) / sum_u d(v,u)
```

for the reachable set `R(v)`.

The bootstrap acceptance contract requires a route from the entrance to the service approach, bounded path stretch, high connected fraction, and entrance-to-service visibility. This extends the old `centerline is empty` test into graph-theoretic spatial QA.

## 7. What v0.12 changes architecturally

The major visible geometry change from v0.11 is the roof-frame realization, especially inside:

- rafters are face-connected digital paths instead of diagonal samples;
- public and working ridge beams connect rafter families;
- tie beams are field-derived stations;
- the working wing receives explicit post footings, eave posts, and wall plates;
- exterior eaves and ridges continue to descend from the same roof field.

Window dimensions do not change because the optimizer selects the current 3-block opening as the best integer realization under the working reference. Their authority does change: south/north window groups are now generated and audited as grammar productions.

## 8. Research lessons represented versus still deferred

Implemented now:

- shape/split grammar concepts -> attributed facade production trace;
- parametric CAD constraint solving -> deterministic finite-domain hard/soft solver;
- graph-theoretic floor/spatial analysis -> walkability and visibility graph metrics;
- computational roof geometry -> common wavefront roof-field abstraction;
- layered facade modeling -> incompatibility contracts;
- inverse-procedural/MDL ideas -> grammar description-length proxy;
- BIM-style automated checking -> semantic rules emitted into artifact validation;
- structural topology ideas -> explicit support-connectivity graph.

Deliberately deferred until there is evidence requiring them:

- general polygon straight-skeleton implementation;
- weighted roof facets / valleys / complex roof intersections;
- integer-programming or rectangular-dual automatic room generation;
- true inverse grammar induction from an external donor corpus;
- FEA or structural sizing;
- learned/statistical optimization before measured reference data exists.

## 9. Next evidence gate

v0.12 should be judged in Minecraft at player height for two separate questions:

1. Does the mathematically connected roof frame actually read as a better architectural interior and exterior?
2. Do any mathematically valid additions become visually excessive at Minecraft scale?

If the second answer is yes, the right correction is to tune objective weights or grammar/field realization rules, not return to arbitrary coordinate patches.
