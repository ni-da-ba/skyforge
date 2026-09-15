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

## Current v0.4 boundary

The implementation remains deliberately narrow and now has three explicit lowering layers:

- `model.py` — backend-neutral compiler IR (`BlockState`, cells, voxel model, semantic volumes);
- `guild_branch.py` — stable v0.1/v0.2 structural pass: dimensions, bay rhythm, shell, openings, rooms, block states, anchors, digest, validation;
- `guild_branch_detail.py` — v0.3 exterior/detail pass: roof articulation, public identity, canopies/aprons, working-face grammar;
- `guild_branch_interior.py` — v0.4 interior pass: corrected public/staff circulation, bounded furnishing modules, interior anchors/volumes, freight/repair furnishing, interior validation;
- `render.py` — exterior orthographic/isometric QA plus semantic floorplan, interior plan, cutaway isometric, and longitudinal interior section;
- `compile.py` — CLI dispatcher;
- v0.2/v0.3/v0.4 specimens and focused tests.

The v0.4 pass deliberately corrects the circulation problem exposed by the v0.3 floorplan:

```text
PUBLIC ENTRANCE
  -> contract / route information
  -> compact waiting space
  -> SERVICE_COUNTER (player/public side)
  -> NPC_WORK_POINT (staff side)
  -> compact back office / records
```

The working wing remains split into:

```text
light repair <-> repair apron / airfield
warehouse    <-> freight apron / airfield
```

Interior furnishing is semantic and sparse rather than decorative noise. Current reusable modules include:

- contract board and route-information panel;
- waiting benches;
- clerk backbar / records shelving;
- compact back-office desk;
- freight stacks with preserved handling lane;
- repair workbench and tool storage;
- public/staff task lighting.

The central public entrance-to-counter aisle is explicitly reserved and validated. Functional anchors are kept out of furnishing occupancy.

The compiler still fixes the first proof family to:

- temperate Guild Mercantile Functionalism;
- south public access;
- east working / airfield face;
- universal service core;
- warehouse / freight capability;
- light repair capability;
- machine-readable service/activity anchors.

Unsupported orientations fail closed rather than silently producing misleading geometry.

## Running the proof

From repository root:

```bash
python tools/asset_compiler/compile.py \
  tools/asset_compiler/specimens/bootstrap_guild_branch_v0.4.json \
  --out build/asset-compiler-v04

python -m unittest discover -s tools/asset_compiler/tests -v
```

v0.4 QA outputs include:

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
interior_plan.svg
cutaway_isometric.svg
interior_section.svg
```

The dedicated `Asset compiler proof` GitHub Actions workflow runs the tests, compiles the v0.4 specimen, and uploads the complete QA directory as a short-lived artifact for visual review.

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

The v0.4 proof still intentionally stops before `.schem` / structure-NBT export. The next material gate is visual inspection of the compiled interior/cutaway output. If that reads as a credible working Guild branch, proceed to one exact Minecraft export path rather than continuing to polish the offline representation indefinitely.
