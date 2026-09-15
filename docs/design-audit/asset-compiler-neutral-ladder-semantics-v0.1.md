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

The structural-detail adapter preserves both `ladder` and `climbable` semantic capabilities during target-neutral round-trip, classifies ladders as a distinct partial-collision shape, and rejects ladders whose backing face is absent or placed on the wrong side.

## Conservative Create boundary

Exact Create 6.0.10 source shows `MetalLadderBlock` extends vanilla `LadderBlock`, and additionally permits survival when an identical ladder with matching facing exists directly above. The structure compiler does not depend on that looser rule. Requiring ordinary backing support keeps authored structures portable and avoids hanging ladder chains whose validity depends on target-specific behavior.

## Exact runtime evidence

The Wave C1 exact-artifact probe now enforces, rather than merely observes, the three Create ladder contracts:

- `create:andesite_ladder`;
- `create:brass_ladder`;
- `create:copper_ladder`.

Each must expose exactly `facing` in north/east/south/west and boolean `waterlogged`, with defaults north/false. The live gate therefore validates fourteen catalog resources in total, including three pane, three bars, and three ladder contracts. The remaining discovery-only structural observations are the three scaffolds and `create:metal_girder`.

## WBY authority

The three Create metal ladders are now present in the WBY capability catalog with `ladder`, `climbable`, `directional`, `attachment_sensitive`, and `thin` capabilities, but remain `cataloged` rather than `active`. They do not enter `wby_c1_create_registry()` and cannot alter current WBY Guild output.

No current Guild semantic role is remapped by this tranche. Activation remains gated on an explicit upstream neutral access/climbable intent. Ordinary vanilla Guild realization is unchanged.

## Concurrency boundary

This tranche changes only shared structure-realization infrastructure and WBY structure-side authority evidence. It does not touch the aircraft compiler or Sable, Aeronautics, or Propulsion contracts.
