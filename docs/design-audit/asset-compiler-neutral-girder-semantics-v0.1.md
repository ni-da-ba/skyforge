# Asset Compiler Neutral Girder Semantics v0.1

## Scope

This tranche defines a backend-neutral girder primitive for structure realization. The contract is intentionally narrower than Create 6.0.10 `GirderBlock`: the compiler models beam/pole topology and ordinary structural contact, but does not reproduce Create-specific attachment rules for nixie tubes, placards, brackets, tracks, chutes, shafts, walls, lanterns, or other gameplay blocks.

## Exact Create boundary

At the pinned Create source commit `79b5d3b37e2d1970818dd97ca460b649cd0a456c`, `GirderBlock` exposes:

- `axis` in `x`, `y`, `z`;
- `x`, `z`, `top`, `bottom`, and `waterlogged` booleans;
- default state `axis=y`, all booleans false;
- a visible shape selected by the `x`/`z` pair: vertical pole, X beam, Z beam, or X/Z cross;
- horizontal connection state that can be enabled by the girder's primary axis, neighboring girder state, or several Create-specific attachment types;
- vertical `top`/`bottom` attachment state with additional Create-specific cases.

The structure compiler uses only the generic beam/pole subset of that state surface.

## Neutral semantic contract

A girder-capable target resource is:

- axis-oriented;
- thin/partial-collision rather than a full cube;
- neighbor-sensitive;
- structurally descriptive, not a replacement for generic full-cube support;
- target-neutral at the semantic layer.

`axis` is the authored primary orientation and remains authoritative. The target adapter owns derived topology booleans.

### Horizontal beam topology

For the conservative generic subset:

- `axis=x` implies `x=true`;
- `axis=z` implies `z=true`;
- `axis=y` implies neither horizontal beam flag by itself;
- perpendicular neighboring girder cells may add the corresponding horizontal flag at an intersection;
- non-girder target-specific neighbors do not create girder beam flags.

This permits straight X beams, straight Z beams, vertical poles, and girder-only X/Z intersections without importing Create-specific machinery semantics. Legal authored `x`/`z` values are replaced by this derived state.

### Vertical attachment flags

`top` and `bottom` are derived only from ordinary girder/structural contact:

- a neighboring girder in the corresponding vertical direction may set the flag;
- a neighboring cell exposing the corresponding sturdy support face may set the flag;
- Create-specific attachment blocks are ignored by the neutral compiler contract.

Legal authored `top`/`bottom` values are likewise non-authoritative. The compiler does not require these flags for survival; they are topology/detail state only.

## Shape contract

The coarse realization model distinguishes:

- `girder_pole` when `x=false,z=false`;
- `girder_beam_x` when `x=true,z=false`;
- `girder_beam_z` when `x=false,z=true`;
- `girder_cross` when `x=true,z=true`.

All are partial-collision and neighbor-dependent. They expose no generic sturdy support faces in the compiler support model; support/attachment authority remains explicit rather than being inferred from a thin decorative beam.

## WBY authority

`create:metal_girder` is now present in the Create 6.0.10 WBY capability document as `cataloged`, not `active`. Its exact `axis/bottom/top/waterlogged/x/z` state and defaults are mandatory in the pinned-runtime registry probe.

Cataloged status does not place the girder in `wby_c1_create_registry()`, so it cannot change current Guild output. No current Guild semantic role is remapped. Activation still requires an explicit upstream neutral girder/brace intent.

With the girder contract enforced, all ten previously deferred stateful structural resources in this audit set—three bars, three ladders, three scaffolds, and the girder—have generic compiler semantics plus exact live registry contracts. There are zero remaining observation-only resources in that bounded set.

## Concurrency boundary

This tranche is structure-side only. It does not touch the aircraft compiler or Sable, Aeronautics, or Propulsion contracts.
