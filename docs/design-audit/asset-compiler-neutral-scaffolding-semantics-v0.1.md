# Asset Compiler Neutral Scaffolding Semantics v0.1

## Scope

This tranche defines a backend-neutral scaffolding primitive for structure realization. It is intentionally stricter than Create 6.0.10 `MetalScaffoldingBlock`: Create disables vanilla survival/falling, while the compiler will require a portable support path and derive state from geometry rather than relying on permissive target behavior.

## Exact Create boundary

At the pinned Create source commit `79b5d3b37e2d1970818dd97ca460b649cd0a456c`, `MetalScaffoldingBlock` extends vanilla `ScaffoldingBlock` but:

- `canSurvive(...)` always returns true;
- scheduled `tick(...)` is empty, so vanilla unsupported-fall behavior is disabled;
- `isScaffolding(...)` returns true;
- collision/selection shape changes when `bottom=true`;
- a downward neighbor update recomputes `bottom` as true only when the block below is neither the same scaffolding block nor a sturdy upward support face.

The exact Wave C1 runtime exposes `bottom` boolean, `distance` in 0..7, and `waterlogged` boolean, with defaults false/7/false.

## Neutral semantic contract

A scaffolding-capable target resource is:

- climbable;
- neighbor-sensitive;
- partial-collision rather than a solid support cube;
- organized into a support-distance topology;
- invalid for compiler emission when no portable support path exists.

The compiler owns `bottom` and `distance`; authored values are not authoritative.

### `bottom`

For a scaffolding cell at `p`, `bottom=true` exactly when the cell below `p` is neither another scaffolding cell nor a block exposing sturdy `up` support through the adapter support-face model. Otherwise `bottom=false`.

### `distance`

Distance is solved over the complete scaffolding cluster, independently of iteration order:

1. a scaffold directly above sturdy `up` support is seeded at distance 0;
2. a scaffold directly above another scaffold inherits that scaffold's distance;
3. horizontal scaffold adjacency propagates support at neighbor distance + 1;
4. values are capped at 7;
5. a cell that cannot reach a support seed remains 7 and is rejected for neutral compiler validity.

This is a conservative portability rule. Create may keep distance-7 scaffolding alive, but generated structures must not depend on that Create-specific relaxation.

## Shape contract

Scaffolding has its own coarse partial-collision shape class and exposes no sturdy support faces. `bottom=true` may use a distinct `scaffolding_bottom` shape class because the pinned Create implementation changes its collision/selection geometry in that state. Shape classification is neighbor-dependent because `bottom` is geometry-derived.

## WBY authority

The three Create metal scaffolds remain non-selectable until this neutral topology is implemented and proven. After proof they may be moved into the WBY catalog as `cataloged`, not `active`:

- `create:andesite_scaffolding`;
- `create:brass_scaffolding`;
- `create:copper_scaffolding`.

No current Guild semantic role is remapped. Activation requires an explicit upstream neutral scaffolding/access intent.

## Concurrency boundary

This tranche is structure-side only. It does not touch the aircraft compiler or Sable, Aeronautics, or Propulsion contracts.
