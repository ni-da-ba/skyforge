# Skyforge Asset Compiler Proof

This directory is a bounded feasibility spike for issue #488.

It is **not** a general procedural-building framework.

The first target is one small temperate Guild branch. The proof should establish whether a compact semantic `AssetSpec` can be lowered deterministically into inspectable voxel/block geometry, semantic anchors, static validation, and eventually one exact Minecraft structure export.

Expected lowering:

```text
AssetSpec
  -> resolved parameters
  -> Layout / Geometry IR
  -> VoxelModel
  -> BlockStateModel
  -> semantic anchors
  -> validation
  -> QA previews
  -> exact Minecraft export only after the representation proves credible
```

## Current scope

Stage 1 begins with `specimens/bootstrap_guild_branch_v0.1.json`.

The specimen intentionally fixes a narrow first family:

- small temperate Guild branch;
- three structural bays;
- south public access;
- east working / airfield face;
- universal service core;
- warehouse / freight capability;
- light repair capability;
- machine-readable service/activity anchors.

Do not generalize beyond what this specimen or its immediate sibling-variant test actually requires.

## Coordinate convention

Local asset coordinates use:

```text
+X = east / working-face direction
+Y = up
+Z = south / public-access direction
```

The first specimen is local-space only. Final world placement/orientation remains an Implementation concern downstream of site selection and exact admission.

## Acceptance discipline

The compiler hypothesis is not accepted merely because it can place blocks.

It must eventually show:

1. visual credibility;
2. deterministic output;
3. a semantic spec substantially smaller than raw block enumeration;
4. useful automatic validation;
5. sensible functional anchors;
6. a materially cheaper sibling revision/variant;
7. editable/comprehensible exported geometry.

If those do not emerge, stop or redesign rather than expanding the framework.