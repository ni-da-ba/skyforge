# Wave C1 Integrated Engineering Prototype Acceptance v0.1

**Snapshot:** 2026-09-05  
**Status:** Development acceptance specification. Static data scaffolding exists; optional-mod runtime proof remains pending.

## Objective

Prove that the first Skyforge industrial-normalization layer can coexist with the selected engineering mods while preserving one canonical economy.

The Wave C1 specimen is deliberately narrow.

It does **not** attempt to balance the full pack.

It proves:

1. rejected raw materials do not become required progression;
2. the Silver-free electrical-storage path works;
3. optional-mod native ore injection can be suppressed without Java;
4. CBC's retained metal ladder remains intact;
5. recipe-viewer output exposes no obvious dead ends or duplicate material silos.

## Prototype mod set

Required baseline:

- Minecraft 1.21.1;
- NeoForge 21.1.x compatible with Skyforge;
- Skyforge development build;
- Create;
- Create: Big Cannons;
- Create Crafts & Additions.

Optional test branches:

- Create Propulsion: Simulated;
- Create: Metallurgy.

Aeronautics/Sable/Diesel may remain installed if the local development pack already requires them, but Wave C1 does not require their balance acceptance.


### Reproducible runtime specimen

The exact development stack is now pinned and launchable through:

~~~text
docs/design-audit/wave-c1-runtime-specimen-v0.1.md
docs/design-audit/wave-c1-loader-dependency-closure-v0.1.md
skyforge-neoforge-1211/wave-c1-mods.properties
~~~

Focused run profiles:

~~~text
waveC1BaselineClient
waveC1MetallurgyClient
waveC1PropulsionClient
waveC1IntegratedClient
~~~

The optional jars are isolated to those runs through ModDevGradle per-run runtime classpaths; they are not production Skyforge dependencies.

## Development resources currently prepared

The runtime-authoritative fixture is now a standalone Minecraft 1.21.1 datapack at:

~~~text
skyforge-neoforge-1211/src/development/wave-c1-datapack
~~~

It declares data pack format 48 and mirrors the development-resource overrides.

This matters because NeoForge explicitly supports higher-priority datapacks overriding mod biome modifiers at the same resource IDs. The standalone datapack therefore provides the deterministic runtime path if mod-resource ordering alone is insufficient.

### Silver-free accumulator

Override resource:

~~~text
data/createaddition/recipe/crafting/modular_accumulator.json
~~~

Expected result:

~~~text
Modular Accumulator
    accepts #createaddition:modular_accumulator_usable_wires

therefore

Gold wire
    -> valid baseline storage path

Electrum wire
    -> also valid if Electrum exists
~~~

No Silver production is required.

### Platinum suppression

Overrides:

~~~text
data/createpropulsion/neoforge/biome_modifier/add_ore_platinum.json
data/createpropulsion/neoforge/biome_modifier/add_ore_platinum_buried.json
data/createpropulsion/neoforge/biome_modifier/add_ore_platinum_large.json
data/createpropulsion/neoforge/biome_modifier/add_ore_platinum_medium.json
~~~

Each is:

~~~json
{
  "type": "neoforge:none"
}
~~~

NeoForge 1.21.1 explicitly supports this no-op biome modifier for datapack suppression.

### Wolframite suppression

Override:

~~~text
data/createmetallurgy/neoforge/biome_modifier/wolframite_ore.json
~~~

also uses:

~~~json
{
  "type": "neoforge:none"
}
~~~


### Industrial Crucible retained-material override

Static closure tracing confirmed that Metallurgy's stock Industrial Crucible requires both an Obdurium plate and molten Tungsten.

Because Factory B explicitly removes Wolframite/Tungsten/Obdurium progression, Wave C1 now overrides:

~~~text
data/createmetallurgy/recipe/sequenced_assembly/industrial_crucible.json
~~~

with a conditional retained-material sequence:

~~~text
Deepslate Bricks
+ Refractory Mortar
+ Create Sturdy Sheet
+ Grinding
+ 90 mB createmetallurgy:molten_steel
    -> Industrial Crucible
~~~

The recipe is gated by a NeoForge mod-loaded condition for createmetallurgy.

The 90 mB Steel input is one Metallurgy ingot-equivalent and can be supplied by melting CBC Steel through the shared #c:ingots/steel path.

See:

~~~text
docs/design-audit/wave-c1-static-recipe-closure-audit-v0.1.md
~~~

## Phase C1-A — build/static gate

Current automated checks must prove:

- development override files exist;
- accumulator uses the existing usable-wire tag;
- Platinum modifiers are no-ops;
- Wolframite modifier is a no-op;
- Industrial Crucible has no Tungsten/Obdurium ingredient and uses retained Sturdy Sheet + molten Steel;
- normal Skyforge CI still passes.

This is necessary but not sufficient.

## Phase C1-B — datapack precedence proof

With Create Propulsion installed:

1. create/load a disposable development world;
2. confirm the effective registry entry for each Platinum biome modifier is neoforge:none;
3. generate representative Overworld chunks;
4. verify no native Platinum ore placement occurs.

With Create: Metallurgy installed:

1. confirm Wolframite biome modifier resolves to neoforge:none;
2. generate representative Nether chunks;
3. verify no native Wolframite placement occurs.

If mod-resource precedence does not reliably select the Skyforge development override:

> Do not add Java.

Instead package the same overrides as the actual Skyforge pack datapack at higher datapack priority.

## Phase C1-C — recipe closure proof

### Electrical baseline

Craft or recipe-inspect:

~~~text
Copper/Zinc
    -> Brass

Zinc + Copper + Redstone
    -> Capacitor

Gold conductor path
    -> Modular Accumulator
~~~

Acceptance:

- no Silver required;
- no Electrum required;
- Electrum may remain a valid alternate ingredient;
- JEI/EMI shows an understandable path.

### CBC baseline metals

Verify stock survival routes:

~~~text
Iron plates + Gunpowder
    -> Wrought-Iron artillery

Iron + Coal + HEATED
    -> Cast Iron

Copper + Zinc + Cinder Flour + HEATED
    -> Bronze

Iron + Coal + HEATED
    -> Steel

Netherite Scrap + Cast Iron/Steel + SUPERHEATED
    -> Nethersteel
~~~

Acceptance:

- no Tin required;
- no Steel ore required;
- no Metallurgy material required;
- all CBC metals shown in survival have reachable recipes.

## Phase C1-D — rejected-material leakage audit

Search JEI/EMI for:

~~~text
silver
platinum
wolframite
tungsten
obdurium
tin
~~~

For each result classify:

~~~text
INERT CONTENT
    item exists but nothing important requires it

OPTIONAL ALTERNATIVE
    recipe exists but canonical route does not depend on it

LEAK
    retained capability requires rejected material
~~~

Wave C1 passes only with zero **LEAK** classifications.

An inert creative-tab item is not automatically a failure.

## Phase C1-E — shared Steel/Bronze collision audit

Only with Create: Metallurgy loaded.

Record:

- every Steel ingot item;
- every Bronze ingot item;
- all common tags containing them;
- all Steel/Bronze production recipes;
- all molten Steel/Bronze fluids;
- CBC cannon-cast fluid tags;
- any conversion/yield loops.

Desired outcome:

~~~text
ONE PLAYER-FACING STEEL ECONOMY
ONE PLAYER-FACING BRONZE ECONOMY
~~~

If two internal item identities remain, common tags and recipe guidance must make them interoperable.

## Phase C1-F — Metallurgy A/B

Factory A:

~~~text
CBC only
~~~

Factory B:

~~~text
CBC
+ stripped Metallurgy foundry
- Wolframite progression
- Tungsten progression
- Obdurium progression
~~~

Build comparable cannon-production workflows.

Record:

- number of distinct machines;
- setup effort;
- active player interactions;
- batch throughput;
- molten storage usefulness;
- piping/logistics value;
- idle waiting;
- recipe-viewer clutter;
- subjective plant readability/fun.

Retain Metallurgy only if Factory B provides a clear systems payoff.

## Phase C1-G — first balance questions

Do **not** tune before observing.

Record:

### CBC Steel

Does immediate post-Blaze Steel invalidate:

- Cast Iron?
- Bronze?

If no:

> Keep stock recipe.

If yes:

> Prototype one retained-resource process escalation.

### Electrical throughput

Does Gold wiring become a meaningful bottleneck in a representative mature network?

If no:

> Electrum may be unnecessary.

If yes:

> Design a Silver-free optional Electrum production route.

## Evidence record

For each test capture:

~~~text
MOD SET / VERSIONS
SKYFORGE COMMIT
WORLD SEED
DATAPACK LIST / PRIORITY
RECIPE SCREENSHOT OR MACHINE OUTPUT
WORLDGEN RESULT
PASS / FAIL
NOTES
~~~

For human-eye A/B:

- one factory overview screenshot;
- one JEI/EMI progression screenshot;
- brief subjective note on clarity and tedium.

## Pass condition

Wave C1 passes when:

~~~text
normal CI PASS
+
Silver-free baseline electricity PASS
+
Platinum native worldgen OFF when Propulsion loaded
+
Wolframite native worldgen OFF when Metallurgy loaded
+
CBC material closure PASS
+
zero rejected-material progression leaks
+
Metallurgy A/B evidence recorded
~~~

A pass does **not** require Metallurgy or Propulsion to survive.

Removing an optional mod is a valid successful outcome.

## Next wave after C1

Wave C2 should implement only the normalization demonstrated necessary by C1:

- shared Steel/Bronze tags and fluid bridges;
- promote/refine the development Industrial Crucible retained-material recipe if Metallurgy wins;
- Platinum-free Propulsion recipes for features that survive;
- CBC Steel retuning only if material choice collapses;
- Electrum route only if high-current demand earns it.

## Acceptance principle

> Wave C1 succeeds by discovering the smallest recipe/config layer that makes the engineering stack coherent—not by preserving every feature of every installed mod.
