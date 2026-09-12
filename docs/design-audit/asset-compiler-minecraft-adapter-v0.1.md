# ASSET-001 Minecraft adapter v0.1

The asset compiler previously crossed directly from architectural geometry into concrete Minecraft
`BlockState`s. The structure-template exporter checked NBT syntax and dimensions but did not know
whether a particular block actually supported a requested state property, whether paired blocks were
coherent, or whether neighbor-sensitive blocks would realize the intended local topology.

`minecraft_adapter.py` introduces the first explicit target-realization boundary.

```text
architectural / voxel result
    -> Minecraft capability adapter
    -> legal block-state realization
    -> target-specific support/topology checks
    -> structure-template NBT
```

## v0.1 responsibilities

- A bounded Minecraft Java 1.21.1 capability registry covers the current v0.14 Guild palette.
- Unknown blocks fail closed under strict adapter use.
- State properties and values are validated against the selected block's explicit capability record.
- Semantic `BlockIntent` resolution selects blocks by material family and required capabilities rather
  than by assuming a concrete block name upstream.
- Glass panes and fences receive deterministic cardinal connection states from their realized
  neighborhood instead of relying on unspecified placement/update behavior.
- Door halves are checked as matched two-block structures with consistent facing and hinge.
- Hanging lanterns are checked for direct support.
- Block-entity-bearing blocks are counted and explicitly reported. v0.1 still permits default/empty
  runtime block-entity state; payload compilation is a later adapter stage.

## Transitional boundary

v0.14 still emits concrete `BlockState`s internally. The adapter validates and normalizes that result
and also exposes the semantic resolver that the next lowering pass will use. This keeps the accepted
first-principles architecture stable while the Minecraft boundary is introduced incrementally.

The next adapter stages should move material/shape decisions out of the architectural compiler,
derive stair corner states from neighboring roof intent, add visual/collision/support shape models,
compile block-entity NBT, and source registry/capability data from authoritative Minecraft/NeoForge
registries rather than maintaining a bounded handwritten vanilla subset.
