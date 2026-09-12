# ASSET-001 Minecraft adapter v0.2

The second adapter pass moves the target boundary from syntactic validation toward actual Minecraft
realization semantics while keeping the accepted v0.14 architecture unchanged.

```text
architectural / voxel result
    -> erase concrete resource identity into semantic block intent
    -> capability-based Minecraft block selection
    -> explicit default-state normalization
    -> neighbor-dependent topology realization
    -> collision/support-shape validation
    -> structure-template NBT
```

## Semantic re-resolution

Every v0.14 cell is converted to a `BlockIntent` containing semantic material families, required
shape/runtime capabilities, and state-independent geometric properties. The intent resolver does not
receive the original Minecraft resource name. It chooses a legal registered block by deterministic
family/capability cost, then fills explicit Minecraft default properties.

This is still a transitional boundary because the architectural compiler internally constructs
`BlockState`s before they are erased into intent. However, NBT export now treats the adapter's
re-realized model as authoritative. A later pass can move the intent model upstream without changing
the target resolver.

## Neighbor topology

v0.2 resolves three neighbor-sensitive families after semantic block selection:

- glass-pane cardinal connections;
- fence cardinal connections;
- stair `shape` (`straight`, inner-left/right, outer-left/right).

Stair corner derivation follows the same local dependency structure as vanilla Java stairs: compare
the stair in the facing direction for compatible perpendicular outer corners, then the opposite
direction for compatible perpendicular inner corners, with a same-facing/same-half side guard.
The algorithm is deterministic and depends only on the final target neighborhood.

## Shape/support model

The adapter now assigns a bounded target shape descriptor to every current Guild block family. The
descriptor records a coarse collision-volume fraction, whether collision is partial, whether shape is
neighbor-dependent, and which block-boundary faces are sturdy attachment surfaces.

This is deliberately not a replacement for Minecraft's full `VoxelShape` implementation. It is an
explicit compiler model sufficient to distinguish full cubes, slabs, stairs, panes, fences, doors,
carpet, containers, lanterns and lightning rods for current Guild validation. It can later be
replaced by authoritative NeoForge-generated shape evidence behind the same interface.

Hanging-lantern, standing-lantern, lightning-rod and door-ground support checks now consult these
support faces instead of accepting any occupied neighboring voxel.

## Explicit defaults

The adapter fills bounded Java 1.21.1 default state for the registered palette: waterlogging flags,
pane/fence connections, stair shape, slab type, door open/powered state, chest/barrel orientation,
lantern hanging state, log axis and lightning-rod state. This eliminates reliance on unspecified
parser/runtime defaults in generated structure palettes.

## Acceptance

The v0.14 architecture digest is not expected to change because architecture remains upstream. The
Minecraft structure artifact may change because the adapter now emits more complete and
neighbor-correct block states.

Acceptance requires:

- all asset-compiler tests green;
- zero adapter issues;
- semantic re-resolution does not require concrete resource names;
- all doors and attachment-sensitive Guild blocks have legal support;
- no illegal state/property combinations;
- deterministic neighbor realization;
- in-game A/B review confirms that roof corners, panes/fences, fixtures and doors behave at least as
  well as the previous v0.14 artifact.

Block-entity NBT, authoritative generated registry data, and Create/Create Aeronautics capability
providers remain later adapter stages.
