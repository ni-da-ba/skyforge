# Skyforge SF-IMP-0043 — Native Structure Height Visibility Architecture Update

SF-IMP-0043 extends the accepted early generator-query bridge into a real Minecraft structure-start consumer.

Accepted relationship:

```text
Skyforge semantic geometry
    -> non-mutating early generator height query
    -> vanilla structure-start terrain sampling
    -> native StructureStart can react to elevated island geometry

later:

vanilla surface
    -> Skyforge physical realization
    -> vanilla structure pieces realized into the chunk
```

The live desert-pyramid proof establishes that early native structure generation is no longer blind to Skyforge terrain. Several pyramids overlapping the Massif were vertically associated with the island, while a candidate outside the Massif remained on native ground.

The run also establishes an equally important negative boundary: a truthful scalar height query is not enough to decide whether a multi-block structure belongs on a particular island surface. Native structures may sample a footprint spanning slopes or island edges and consequently embed at varying depths.

Future structure integration therefore needs an explicit suitability/support layer upstream of concrete Minecraft structure realization. That layer should reason about footprint coverage, slope, edge clearance, coherent target surface, and stacked-surface selection while leaving generic early height queries geometrically truthful.

This does not imply a second structure implementation. Minecraft may continue to own concrete vanilla/modded structure pieces and realization while Skyforge owns higher-level occurrence, relationship, and suitability policy.
