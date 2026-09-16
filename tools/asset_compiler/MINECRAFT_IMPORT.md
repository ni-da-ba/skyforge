# ASSET-001 Minecraft 1.21.1 import gate

The bounded compiler can now emit a vanilla Java Edition 1.21.1 structure-template NBT file. The proof exporter uses data version `3955` and refuses structures larger than the vanilla 48-block-per-axis structure-template limit.

## Build the structure artifact

From repository root:

```powershell
python tools/asset_compiler/compile.py `
  tools/asset_compiler/specimens/bootstrap_guild_branch_v0.4.json `
  --out build/asset-compiler-v04 `
  --minecraft-structure
```

The generated file is:

```text
build/asset-compiler-v04/guild_branch_bootstrap_temperate_v0_4.nbt
```

## Make the template available to the development client

For the ordinary Skyforge NeoForge 1.21.1 development run, copy the generated file to:

```text
skyforge-neoforge-1211/src/development/resources/data/skyforge/structure/guild_branch_bootstrap_temperate_v0_4.nbt
```

This is a development-only resource. Do not treat the generated specimen as an accepted production asset yet.

Then launch the ordinary development client from repository root:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:runClient
```

Open a creative, cheats-enabled disposable test world and place the template with:

```mcfunction
/place template skyforge:guild_branch_bootstrap_temperate_v0_4 ~ ~ ~
```

For a predictable inspection site, a fixed clear coordinate is preferable, for example:

```mcfunction
/place template skyforge:guild_branch_bootstrap_temperate_v0_4 0 120 0
```

The compiled local coordinate convention is:

```text
+X east  = working / airfield face
+Y up
+Z south = public entrance face
```

The current specimen is approximately `26 x 15 x 19`, so leave sufficient clear space around the placement origin.

## Alternative world-local import

If a development-resource copy is inconvenient, a world-local structure template can be used instead. After creating and closing the test world, place the NBT under that save's generated structure-template directory using namespace `skyforge`, then relaunch the world and use the same `/place template skyforge:guild_branch_bootstrap_temperate_v0_4 ...` command. For this proof, prefer the development-resource route because it is reproducible and source-tree-local.

## First in-game acceptance checklist

The purpose of this gate is not to approve the building. It is to expose errors that the offline renderer cannot reliably show.

Check at minimum:

- overall scale at player eye height;
- public entrance readability on the south/+Z face;
- roof silhouette, overhangs, stair/slab states and gable closure;
- window-pane neighbor behavior;
- door halves/orientation and working-opening behavior;
- lantern support and lighting placement;
- service-counter approach and clerk-side working space;
- player circulation from entrance to counter and back office;
- warehouse freight lane and east working opening;
- repair-bay clear floor and workbench/tool placement;
- chest/barrel orientation and block-entity behavior;
- any block state that changes or breaks after neighbor updates;
- whether the structure still reads as Guild Mercantile Functionalism with actual Minecraft textures.

Capture screenshots from the public approach, working/airfield side, public interior, counter/staff side, warehouse and repair bay. Record every runtime discrepancy before doing another visual-polish pass.
