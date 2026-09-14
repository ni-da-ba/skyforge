# ASSET-001 backend-neutral realization intent IR

The Guild compiler originally carried concrete Minecraft `BlockState` objects all the way from architectural generation to target export. The Minecraft adapter proved that those resource names could be ignored, but the software boundary still accepted the concrete-state carrier.

This pass makes the architecture/target boundary explicit:

```text
architectural VoxelModel
    -> RealizationIntentModel
    -> target adapter
    -> concrete Minecraft BlockState model
    -> topology/support/block-entity lowering
    -> NBT
```

## RealizationIntent

A realization intent contains only:

- semantic material families;
- required geometric/functional capabilities;
- discrete geometry properties such as axis/facing/half/type;
- optional target preferences.

It contains no Minecraft resource identifier. `RealizationIntentModel.to_dict()` is therefore suitable for audit and for future non-Minecraft adapters.

## v0.14 authority

For the current bounded Guild specimen, `guild_realization_intent.py` projects all 1,885 architectural cells through the accepted Guild role/module grammar into this IR. `minecraft_target_lowering.py` then resolves each intent into a legal target state and hands only target-created states to the Minecraft topology/support pass.

A destructive test replaces every upstream resource name before projection and verifies that the complete Guild structure still lowers successfully. A second test serializes the projected IR and asserts that it contains no `minecraft:` or placeholder resource identifiers.

The v0.14 compiler still constructs concrete `BlockState`s internally for legacy render/QA paths, so this is not yet the final removal of the transitional carrier from every compiler stage. It is, however, the first export path on which concrete architecture resource identity is no longer an input to target realization.

## Next boundary cleanup

Future compiler families should emit realization intent directly after architectural geometry/material-role resolution, rather than constructing a concrete preview state and then projecting it away. The existing Minecraft adapter and NBT exporter no longer require that upstream concrete identity, so that migration can be incremental without changing the accepted target contract.
