# Skyforge Wave C2 1:1 Nether Datapack

Development-only Minecraft 1.21.1 datapack prototype for testing Nether portal distance integrity.

The pack overrides only the vanilla `minecraft:the_nether` dimension type and changes:

```text
coordinate_scale
    8.0 -> 1.0
```

All other recorded vanilla 1.21 dimension-type properties are retained.

This datapack is deliberately separate from Skyforge's ordinary development resource set and from
the personal-mobility datapack. Enabling it changes registry/world behavior and must not contaminate
unrelated SF-IMP acceptance runs. Test it in a disposable/new world and record actual portal mapping
and recovery behavior before any production decision.
