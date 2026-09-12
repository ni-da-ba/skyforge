# Minecraft target adapter status

Current target path: **minecraft-adapter-0.3** for the v0.14 first-principles Guild branch.

The acceptance boundary is now:

```text
architecture role/module + geometric orientation
    -> Guild semantic BlockIntent profile
    -> Minecraft capability resolver
    -> explicit default states
    -> pane/fence/stair neighborhood realization
    -> shape/support checks and bounded target repair
    -> NBT
```

The v0.14 target-lowering tests deliberately erase every upstream Minecraft resource location before
adapter resolution. Passing this test is the current proof that the Minecraft palette is no longer an
architectural authority.

The architecture compiler still uses `BlockState` as a transitional carrier for geometric properties;
moving `BlockIntent` into the upstream IR is deferred until after the current Minecraft human gate so
that the accepted v0.14 architecture remains stable during target-adapter validation.

Remaining backend work: authoritative NeoForge-generated capability/shape data, explicit block-entity
NBT, upstream intent IR promotion, and mod capability providers.
