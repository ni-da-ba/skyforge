# Skyforge Asset Compiler Proof

This directory is the bounded feasibility spike for issue #488. It is **not** a general procedural-building framework.

The first target is one small temperate Guild branch. The proof asks whether a compact semantic `AssetSpec` can be lowered deterministically into inspectable geometry, concrete block states, semantic anchors, static validation, QA views, and eventually one exact Minecraft export.

```text
AssetSpec
  -> resolved parameters
  -> Layout / Geometry IR
  -> VoxelModel
  -> BlockStateModel
  -> semantic anchors
  -> validation
  -> QA previews
  -> exact Minecraft export only after visual credibility
```

## Current v0.2 boundary

The implementation is deliberately narrow but is now a real compiler pipeline rather than one hard-coded specimen script:

- `model.py` — backend-neutral compiler IR (`BlockState`, cells, voxel model, semantic volumes);
- `guild_branch.py` — Guild-branch spec validation, parameter resolution, geometry lowering, block-state lowering, semantic anchors, deterministic digest, and static validation;
- `render.py` — front/east/top orthographic QA, semantic floorplan, and isometric voxel preview;
- `compile.py` — CLI wrapper;
- `specimens/bootstrap_guild_branch_v0.2.json` — primary three-bay specimen;
- `specimens/bootstrap_guild_branch_4bay_v0.2.json` — sibling specimen proving bay-count variation without compiler changes;
- `tests/test_guild_branch.py` — deterministic, semantic-anchor, fail-closed, QA-output, and sibling-variant tests.

The compiler currently fixes the first proof family to:

- temperate Guild Mercantile Functionalism;
- south public access;
- east working / airfield face;
- universal service core;
- warehouse / freight capability;
- light repair capability;
- machine-readable service/activity anchors.

Unsupported orientations fail closed rather than silently producing misleading geometry.

## Running the proof

From `tools/asset_compiler`:

```bash
python3 compile.py specimens/bootstrap_guild_branch_v0.2.json --out build/bootstrap-branch
python3 -m unittest discover -s tests -v
```

Expected QA outputs include:

```text
resolved.json
blocks.json
anchors.json
validation.txt
front.svg
east.svg
top.svg
floorplan.svg
isometric.svg
```

The exact digest and block count are evidence for one compiler/spec version, not long-lived product constants.

## Coordinate convention

Local asset coordinates use:

```text
+X = east / working-face direction
+Y = up
+Z = south / public-access direction
```

The specimen is local-space only. Final world placement/orientation remains an Implementation concern downstream of semantic site selection and exact admission.

## Acceptance discipline

The compiler hypothesis is not accepted merely because it can place blocks. It must eventually show:

1. visual credibility;
2. deterministic output;
3. a semantic spec substantially smaller than raw block enumeration;
4. useful automatic validation;
5. sensible functional anchors;
6. a materially cheaper sibling revision/variant;
7. editable/comprehensible exported geometry.

The current v0.2 spike intentionally stops before `.schem` / structure-NBT export. The next gate is human inspection of the generated structure. If the structural language is not credible, improve or redesign the representation before adding runtime/export complexity.
