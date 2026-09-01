# Skyforge SF-IMP-0041 — Supplemental Tree Architecture Update

SF-IMP-0041 extends the accepted multi-surface feature path from single-block/patch features to a real multi-block Minecraft tree feature.

Accepted runtime relationship:

```text
Skyforge lower-surface reachability
    -> Minecraft-owned physical suitability (`dry_open`)
    -> supplemental placed-feature origin
    -> Minecraft sapling survival predicate
    -> Minecraft configured tree feature
    -> trunk + foliage + block replacement in the live chunk
```

The result proves that a floating Skyforge island does not have to monopolize all heightmap-driven vegetation in the same `(x,z)` columns. Selected lower surfaces can participate through a supplemental path without rerunning the entire biome-decoration stage or moving tree implementation into Skyforge.

The former emerald/lapis/diamond marker fixtures are retired from active development resources. Their underlying suitability classes remain part of the adapter contract.

This remains an integration mechanism rather than final ecology. Future Skyforge-owned climate, biome, vegetation-density and ecological fields can provide semantic intent upstream while Minecraft-specific adapters continue to translate that intent into concrete feature placement.
