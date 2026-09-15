# Asset Compiler Neutral Ladder Semantics v0.1

## Scope

This tranche defines a backend-neutral climbable ladder primitive for structure realization. It is deliberately narrower than Create's full `MetalLadderBlock` behavior: compiler-emitted ladders must be valid against an ordinary backing support face and do not rely on Create's optional same-ladder-above survival extension.

## Semantic contract

A ladder-capable target resource is:

- climbable;
- directional in the horizontal plane;
- thin and partial-collision;
- attachment-sensitive;
- wall-backed for compiler validity.

`facing` denotes the outward-facing side of the ladder. Its backing support is therefore the neighboring cell in the opposite cardinal direction. The support cell must expose the face toward the ladder through the adapter's existing support-face model.

## Conservative Create boundary

Exact Create 6.0.10 source shows `MetalLadderBlock` extends vanilla `LadderBlock`, and additionally permits survival when an identical ladder with matching facing exists directly above. The structure compiler will not depend on that looser rule. Requiring ordinary backing support keeps authored structures portable and avoids hanging ladder chains whose validity depends on target-specific behavior.

## WBY authority

The exact Wave C1 runtime already established the Create metal ladder state surface: `facing` in north/east/south/west and `waterlogged` boolean, with default north/false. Those resources remain non-selectable until this neutral primitive and its support validation are proven.

No current Guild semantic role is remapped by this tranche. Activation still requires an explicit upstream climbable/access intent.
