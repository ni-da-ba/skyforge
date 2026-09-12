# ASSET-001 Minecraft adapter v0.3

The Minecraft adapter is now an actual target-realization boundary for the v0.14 Guild path rather
than only a validator around concrete block choices.

```text
first-principles architecture + detail
    -> semantic Guild material/shape intent profile
    -> Minecraft capability solver
    -> explicit Java 1.21.1 default-state normalization
    -> pane/fence/stair neighborhood realization
    -> coarse collision/support-shape validation
    -> bounded minimum-edit support repair when legal
    -> structure-template NBT
```

## Strong independence proof

`minecraft_guild_profile.py` maps v0.14 architectural `role`, `module`, and geometric orientation
properties into `BlockIntent`. It never reads the source Minecraft resource location.

The test suite now performs a deliberately destructive proof: compile the complete v0.14 Guild Hall,
replace the resource name on every one of its 1,885 cells with the invalid placeholder
`ignored:architecture_preview_only`, preserve only architectural role/module and geometric properties,
and run the Minecraft target adapter. The adapter successfully reconstructs the complete legal Guild
palette and passes its target constraints.

This proves that concrete material identity is no longer required from the architecture compiler for
v0.14 export. The architecture still uses `BlockState` as a transitional in-memory carrier, but its
resource name can be erased before target lowering.

## Capability resolution

`BlockIntent` requests semantic families and target capabilities rather than block IDs. Examples:

```text
structural_frame + ordinary beam
    -> families = dark_timber, structural_timber
    -> requires = full_cube, solid_support, axis_orientable

roof + eave
    -> family = dark_roof
    -> requires = stair, directional, neighbor_sensitive
    -> carries only facing + half

window
    -> family = glazing
    -> requires = pane, neighbor_sensitive, thin
```

The bounded Java 1.21.1 registry then selects a legal concrete block deterministically. Unsupported or
ambiguous intent fails closed.

## Neighbor topology

After material selection, the adapter realizes local state that depends on Minecraft neighborhood
semantics:

- glass-pane cardinal connections;
- fence cardinal connections;
- stair `shape` (`straight`, inner-left/right, outer-left/right).

Stair corner derivation follows the same local dependency structure as vanilla Java stairs: the
adapter compares compatible same-half stairs in front/behind and applies perpendicular corner state
only when side-neighbor guards permit it. Current v0.14 eave geometry is straight, so its canonical
artifact legitimately reports zero stair-shape changes; dedicated corner fixtures exercise and verify
the non-straight cases.

## Shape/support model

Every registered Guild block family has a bounded target shape descriptor recording:

- coarse collision-volume fraction;
- partial-vs-full collision;
- neighbor dependence;
- sturdy attachment faces.

This is not claimed to be the full Minecraft `VoxelShape` implementation. It is an explicit bounded
compiler model sufficient for current Guild full cubes, slabs, stairs, panes, fences, doors, carpet,
containers, lanterns and lightning rods. It can later be replaced by NeoForge-generated authoritative
shape evidence behind the same interface.

Doors and attachment-sensitive blocks validate against those support faces. This immediately found a
real target-realization defect that the architectural compiler and v0.1 adapter had not modeled: the
Guild roof signal sat on a bottom ridge slab, whose top boundary is not a sturdy attachment face.

The adapter does not weaken the validation. For this bounded roof-attachment case it searches legal
same-family target repairs and chooses the minimum deterministic edit. The accepted realization
promotes the single ridge slab under the signal to a double deepslate-tile slab, preserving the
architectural location while making its Minecraft support explicit. The repair is recorded in
`minecraft_adapter.json`.

## Explicit state defaults

The target registry carries bounded Java 1.21.1 default state for the accepted palette. Generated NBT
therefore explicitly resolves waterlogging, pane/fence connections, stair shape, slab type, door
open/powered state, chest/barrel orientation, lantern hanging state, log axis and lightning-rod state
instead of depending on unspecified parser/runtime defaults.

## Current acceptance result

Dedicated compiler CI passes the complete historical compiler suite plus adapter tests and v0.14
export. The canonical v0.14 architecture remains 1,885 cells with architecture digest
`cbb02ebaea269bbb836d96fe11edf2ac2716fbfd0e7bac8b963fa9430b2991b8`.

The adapter report is required to pass with zero issues. It also records semantic re-resolution,
neighbor-state changes, shape statistics, door/attachment checks, block-entity-bearing cells and any
bounded target repairs.

## Remaining target work

The largest remaining backend tasks are narrower than the original abstraction problem:

1. replace the handwritten bounded capability/shape registry with authoritative Minecraft/NeoForge
   generated data;
2. compile explicit block-entity NBT for containers and later functional/modded blocks;
3. promote the semantic `BlockIntent` carrier into the architecture IR itself so concrete
   `BlockState` construction disappears from the v0.14 architecture code rather than merely becoming
   irrelevant at export;
4. add Create/Create Aeronautics capability providers when functional Guild machinery and aircraft
   enter this pipeline.

The human gate should now be primarily an A/B check of target fidelity: roof signal/ridge, panes,
fences, doors, lanterns and roof edge states. Architectural massing and spatial organization are not
being reopened by this adapter pass.
