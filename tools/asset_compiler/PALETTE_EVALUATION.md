# Guild blue / brass palette evaluation

Status: **provisional; not an accepted Guild palette**.

The v0.7 cohesion pass intentionally freezes geometry work against the current temporary realization:

- institutional blue: `minecraft:blue_wool`
- brass accent: `minecraft:yellow_terracotta`

Those blocks are placeholders for semantic material roles, not canon. The compiler must continue to refer to `institutionalAccent` and `brassAccent` rather than depending on either concrete block ID.

## Target read

The Guild palette should communicate:

- a deep navy / service-institution blue rather than bright royal blue;
- aged warm brass hardware rather than flat yellow paint;
- restrained use: blue marks institutional identity, brass marks hardware/focal details;
- compatibility with dark structural timber, pale infill, masonry base, and deepslate roof;
- readability in ordinary daylight, shade, lantern light, and common resource-pack-free Minecraft rendering.

## Candidate families for an in-game swatch gate

Blue candidates worth comparing side by side:

- `minecraft:blue_wool`
- `minecraft:blue_concrete`
- `minecraft:blue_terracotta`
- `minecraft:cyan_terracotta` as a muted dark comparator, not an assumed final blue
- mixed treatment using a dark base plus blue banners/wool only at identity surfaces

Brass-like candidates worth comparing by role rather than forcing one universal block:

- `minecraft:yellow_terracotta` for muted painted/accent surfaces
- `minecraft:raw_gold_block` for rough warm-metal mass
- `minecraft:gold_block` for very small polished focal details
- exposed/waxed copper variants as bronze-like comparators where a darker aged-metal read is useful
- `minecraft:bell` and gold-weighted thin hardware only where their actual block form fits the function

The likely final answer may be a **small material family**, not one blue block plus one brass block. For example, institutional cloth/paint can use one blue realization while signage or trim uses another; brass hardware can use a restrained mix of rough metal, polished focal pieces, and purpose-built hardware blocks.

## Acceptance gate

Do not settle the palette from offline SVG colors. Build a compact in-game swatch/reference wall using the same neighboring materials as the Guild branch and inspect it under:

1. daylight;
2. shadow / overhang;
3. lantern-lit interior conditions;
4. direct comparison against the actual Guild facade.

Judge hue, value, saturation, texture scale, material read, and whether the blue/brass pair remains subordinate to the architecture. Once accepted, record the chosen role-to-block mapping in the Guild grammar and update all compiler specimens through the semantic material roles.
