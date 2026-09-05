# Selected-Mod Resource Worldgen Authority Audit v0.1

**Snapshot:** 2026-09-05  
**Status:** Source audit for current 1.21.1 candidate engineering stack. Not yet an implementation decision.

## Governing rule

> Skyforge owns Overworld resource geography; selected mods should retain processing/gameplay mechanics while their independent resource placement is disabled, redirected, or constrained.

## Create — zinc

Current Create 1.21.1 source registers `create:zinc_ore` as a NeoForge biome modifier in every Overworld biome.

Current placement:

~~~text
8 attempts per chunk
uniform Y -63 through 70
ore vein size 12
stone/deepslate replacement
~~~

### Decision direction

~~~text
CREATE ZINC
native Overworld placement -> DISABLE / BYPASS for Skyforge islands
ore blocks/items/processing -> KEEP
Skyforge mineral geology -> authoritative placement
~~~

Zinc should be realized from mineral-bearing Skyforge geology and exact-volume island ownership rather than Create's generic Overworld biome modifier.

## Create — striated Overworld material veins

Create also injects `create:striated_ores_overworld` into every Overworld biome.

The configured feature creates layered material bodies using Create decorative stones plus vanilla materials such as:

- scoria;
- crimsite;
- asurine;
- veridium;
- limestone;
- tuff;
- andesite;
- diorite;
- calcite/deepslate combinations.

Current placement uses a rarity filter around one feature per 18 chunks over Y -30 through 70.

### Decision direction

These are primarily lithologic/material bodies rather than progression ores, but they conflict directly with Skyforge's authored lithologic assemblages.

~~~text
CREATE STRIATED OVERWORLD MATERIALS
native island placement -> DISABLE / BYPASS
block palette -> KEEP as realization vocabulary
Skyforge lithology -> decides where/if these stones appear
~~~

This is also a potential source vocabulary for authored bootstrap Andesite rather than requiring Create's independent layered feature.

## Create: Diesel Generators — petroleum

Current 1.21.1 implementation is not ordinary ore-feature worldgen.

`OilChunksSavedData` computes a petroleum amount for a ChunkPos from:

- world seed;
- Perlin noise;
- biome membership;
- configurable normal/high-oil multipliers and thresholds.

Amounts are persisted/depleted through saved data.

The mod exposes a KubeJS oil-amount event and public saved-data accessors.

Pumpjacks:

- require a vertical pipe terminating in a block tagged as an oil deposit;
- default oil-deposit tag currently contains bedrock;
- withdraw/deplete the petroleum amount associated with the pumpjack's chunk.

### Conflict

Petroleum ownership is currently **chunk-column based**.

Two vertically stacked Skyforge islands sharing X/Z cannot own independent reservoirs in the same chunk.

The oil scanner likewise reads the chunk reservoir rather than one island volume.

### Reuse-first prototype

Keep:

- pumpjack machinery;
- oil scanner;
- crude oil fluid;
- depletion/persistence;
- distillation/refining;
- diesel gameplay.

Replace/redirect only resource authority.

First prototype:

1. Disable/default-zero native oil assignment.
2. Skyforge selects strategic petroleum fields from authored geology/province semantics.
3. Skyforge assigns reservoir amounts to intersecting chunk columns through a thin adapter/public saved-data seam.
4. Oil-field planning reserves those chunk columns against unrelated vertical petroleum ownership.
5. Author one or more valid pumpjack deposit anchors at coherent drilling sites.

This accepts the mod's chunk reservoir model initially because petroleum is rare and regional.

### Escalation path

If column exclusivity harms Skyforge composition or stacked-island behavior, replace the reservoir key with an island/site-aware adapter later.

Do not begin by reimplementing pumpjacks/refineries.

## Create: Metallurgy — Wolframite/Tungsten

Current 1.21.1 source adds Wolframite through a NeoForge biome modifier restricted to:

~~~text
#minecraft:is_nether
~~~

Current feature:

~~~text
7 attempts per chunk
uniform Y 0 through 60
vein size 9
replaces netherrack
~~~

### Gameplay role

Current 1.21.1 Create: Metallurgy source gives Wolframite/Tungsten a concrete advanced-industry payoff:

~~~text
Wolframite Ore
-> crushed/raw Tungsten processing
-> molten Tungsten

Andesite Alloy
+ molten Tungsten
+ SUPERHEATED alloying
-> molten Obdurium

Obdurium plate
+ molten Tungsten
+ refractory/assembly steps
-> Industrial Crucible
~~~

This supports treating Wolframite as an advanced Nether mining resource rather than generic decorative ore.

Its required deposit scale should be derived from actual foundry/Obdurium demand during pack testing.

### Decision direction

Skyforge's **current Minecraft implementation authority** is the Overworld island system.

Therefore, for the current implementation:

~~~text
METALLURGY WOLFRAMITE
Nether native worldgen -> ALLOW_DIMENSION_NATIVE
processing/alloys -> KEEP
~~~

This is explicitly provisional.

The cross-dimension authorship strategy now treats the Nether as a future Skyforge domain candidate. If/when the Nether cavern-world pilot begins, reopen Wolframite and every other Nether resource so authored geology—not legacy dimension-native distribution—can become the authority where appropriate.

## Create Crafts & Additions

Current 1.21.1 source contains material/processing systems such as electrum and zinc sheets, but the audited tree shows no independent ore/configured-feature/biome-modifier worldgen layer.

### Decision direction

~~~text
CREATE CRAFTS & ADDITIONS
resource worldgen conflict -> NONE FOUND
processing/electricity -> KEEP subject to recipe/progression audit
~~~

Electrum and electrical materials can remain manufactured goods downstream of Skyforge-authored raw resources.

## Authority matrix

| Dependency | Resource | Native domain | Skyforge action | Keep gameplay? |
|---|---|---|---|---|
| Create | Zinc | Overworld | Redirect/disable native island placement | Yes |
| Create | Striated materials | Overworld | Redirect into Skyforge lithology | Yes, as block vocabulary |
| Diesel Generators | Crude petroleum | Overworld chunk columns | Skyforge-select fields; seed reservoir via adapter | Yes |
| Create: Metallurgy | Wolframite | Nether | Allow dimension-native for now | Yes |
| Crafts & Additions | Electrum/electrical goods | Manufactured | No worldgen conflict found | Yes |

## Exact-volume principle

Overworld resource population must ultimately satisfy:

~~~text
resource belongs to one authored Skyforge owner/site
-> exact-volume/resource-domain realization
-> no accidental placement into stacked neighboring island
-> no generic biome/Y rule overriding semantic geology
~~~

Chunk-column mechanics are acceptable only where the semantic design intentionally adopts a column field and prevents ownership ambiguity.

## Integration priority

### Immediate

1. Suppress/redirect Create zinc on Skyforge islands.
2. Suppress/redirect Create striated Overworld material bodies on Skyforge islands.
3. Prototype Skyforge-authored petroleum fields on top of Diesel Generators' existing reservoir/pumpjack mechanics.

### Later

4. Audit every additional selected mod for configured features, biome modifiers, saved-data resources, plants, or deposits.
5. Reopen Nether resources when the planned Nether Skyforge-authorship pilot reaches geology/resource scope.
6. Perform the same resource-authority audit for End resources before any Skyforge End material system is locked.

## Acceptance principle

> Preserve the mod's player-facing resource mechanics whenever possible; replace only the part that incorrectly decides where the resource belongs.
