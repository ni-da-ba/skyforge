# Exact Recipe / Worldgen Collision Manifest v0.1

**Snapshot:** 2026-09-05  
**Status:** File-level implementation manifest for the current candidate engineering stack.

# Purpose

This document identifies concrete upstream data resources that conflict with the unified Skyforge industrial production graph.

It does **not** mean every listed file must be overridden immediately.

Action classes:

~~~text
MANDATORY
    required if the owning mod is selected

CONDITIONAL
    required only if an optional mod survives A/B

TEST-TRIGGERED
    change only if integrated gameplay demonstrates a problem

NO-CHANGE BASELINE
    explicitly audited and currently accepted
~~~

# Create Crafts & Additions

Branch audited: current 1.21.1 line.

## CC&A-01 — Modular Accumulator hard Electrum dependency

Upstream recipe:

~~~text
src/generated/resources/data/createaddition/recipe/crafting/modular_accumulator.json
~~~

Related existing abstraction:

~~~text
src/generated/resources/data/createaddition/tags/item/modular_accumulator_usable_wires.json
~~~

Problem:

- baseline electrical storage currently hard-requires Electrum in the recipe;
- Electrum production conditionally depends on Silver;
- Skyforge rejects Silver geology.

Action:

**MANDATORY if CC&A remains core.**

Preferred override:

- replace the hard Electrum wire ingredient with the mod's existing usable-wire tag or another retained-material equivalent;
- preserve Gold as the baseline advanced-conductor route;
- keep Electrum optional until throughput testing justifies it.

## CC&A-02 — Electrum production

Upstream:

~~~text
src/generated/resources/data/createaddition/recipe/mixing/electrum.json
~~~

Action:

**CONDITIONAL.**

Do not author Silver solely to activate this recipe.

Possible outcomes:

1. leave Electrum optional/inert if Silver tag is absent;
2. add a Silver-free manufactured Electrum route only if high-current testing earns it;
3. de-emphasize Electrum entirely if Gold is sufficient.

## CC&A-03 — Electrum forms

Examples:

~~~text
recipe/crafting/electrum_spool.json
recipe/pressing/electrum_ingot.json
recipe/rolling/electrum_ingot.json
recipe/rolling/electrum_plate.json
~~~

Action:

**CONDITIONAL PRESENTATION/USABILITY.**

Only relevant if Electrum survives.

# Create Propulsion: Simulated

Branch audited: current main/1.21.1 project state.

## PROP-01 — Platinum worldgen injection

Upstream biome modifiers:

~~~text
src/main/resources/data/createpropulsion/neoforge/biome_modifier/add_ore_platinum.json
src/main/resources/data/createpropulsion/neoforge/biome_modifier/add_ore_platinum_buried.json
src/main/resources/data/createpropulsion/neoforge/biome_modifier/add_ore_platinum_large.json
src/main/resources/data/createpropulsion/neoforge/biome_modifier/add_ore_platinum_medium.json
~~~

Associated worldgen:

~~~text
worldgen/configured_feature/ore_platinum*.json
worldgen/placed_feature/ore_platinum*.json
~~~

Action:

**MANDATORY if Propulsion is selected.**

Disable native Platinum placement.

Do not redirect Platinum into Skyforge geology.

## PROP-02 — Platinum processing surface

Examples:

~~~text
recipe/crushing/platinum_ore.json
recipe/crushing/deepslate_platinum_ore.json
recipe/smelting/platinum_ingot_from_*.json
recipe/blasting/platinum_ingot_from_*.json
recipe/pressing/platinum_sheet.json
~~~

Action:

**CONDITIONAL CLEANUP.**

These may remain inert after worldgen suppression if they do not confuse progression.

Do not spend code solely to delete harmless dead recipes.


## PROP-02A — Gold-refining Platinum byproduct

Current Propulsion also overrides Create's crushed-gold washing recipe:

~~~text
data/create/recipe/splashing/crushed_raw_gold.json
~~~

to add:

~~~text
5% chance
    -> createpropulsion:platinum_nugget
~~~

This is a non-geological acquisition path.

Action:

**TEST-TRIGGERED BALANCE.**

Worldgen suppression no longer implies that every Platinum consumer must immediately be rebased.

First test whether a rare Gold-refining coproduct is a coherent late-game material role.

At stock probability one Platinum Sheet costs approximately 180 washed Crushed Raw Gold in expectation, so multi-sheet machines may still be impractically expensive.

## PROP-03 — Vector Thruster

Upstream:

~~~text
src/main/resources/data/createpropulsion/recipe/crafting/vector_thruster.json
~~~

Problem:

- advanced vectored-thrust capability is currently Platinum-gated;
- Platinum itself does not justify geology.

Action:

**TEST-TRIGGERED if Vector Thruster is retained.**

The Gold-washing Platinum byproduct makes the stock recipe survival-craftable without Platinum ore. Rebase only if runtime economics or material identity fail.

If rebasing is needed, map ingredients by role:

~~~text
load-bearing structure
    -> Steel / Nethersteel / Sturdy Sheet

precision
    -> Brass

advanced propulsion
    -> Ion Thruster / retained thruster core

sensing
    -> existing Sable/Simulated gimbal sensor
~~~

Exact recipe awaits propulsion gameplay lock.

## PROP-04 — Redstone Converter

Upstream:

~~~text
src/main/resources/data/createpropulsion/recipe/crafting/redstone_converter.json
~~~

Action:

**TEST-TRIGGERED if retained.**

Keep stock only if the Platinum-byproduct cost is useful rather than punitive. Otherwise rebase toward:

- Brass;
- Redstone;
- retained conductor/control components.

## PROP-05 — Cable / Cable Relay

Known upstream recipes include:

~~~text
src/main/resources/data/createpropulsion/recipe/crafting/cable.json
src/main/resources/data/createpropulsion/recipe/crafting/cable_relay.json
~~~

Action:

**TEST-TRIGGERED if feature retained.**

The byproduct route makes it reachable; use retained conductor vocabulary instead only if the resulting Gold-processing burden is excessive:

- Copper;
- Gold;
- optional Electrum;
- Brass for relay/control hardware.

## PROP-06 — Coral Generator

Upstream:

~~~text
src/main/resources/data/createpropulsion/recipe/crafting/coral_generator.json
~~~

Action:

**TEST-TRIGGERED if retained.**

The stock four-sheet cost is approximately 720 washed Crushed Raw Gold in expectation. Rebase to retained electrical + structural materials if runtime confirms that this is disproportionate.

The generator capability must justify itself independently of Platinum.

## PROP-07 — Platinum fluid tank / vessel

Upstream recipes:

~~~text
recipe/crafting/platinum_fluid_tank.json
recipe/crafting/platinum_fluid_vessel.json
recipe/crafting/platinum_fluid_tank_from_vessel.json
recipe/crafting/platinum_fluid_vessel_from_tank.json
recipe/crafting/platinum_fluid_vessel_compat.json
~~~

Action:

**TEST-TRIGGERED if compact high-capacity fluid storage is retained.**

Two Platinum Sheets imply approximately 360 washed Crushed Raw Gold in expectation. Keep that rare-material compactness only if it earns the cost; otherwise treat the block as a reinforced advanced tank capability.

Candidate recipe vocabulary:

- ordinary Create fluid tank/vessel;
- Steel/Sturdy structural reinforcement;
- Brass control/fixture components where appropriate.

## PROP-08 — Platinum Casing

Upstream:

~~~text
recipe/deploying/platinum_casing.json
recipe/item_application/platinum_casing.json
~~~

Action:

**CONDITIONAL.**

If the block remains only as an internal visual/material dependency, either:

- rebase its recipe to retained advanced materials;
- or remove it from required progression.

No Platinum geology.

# Create: Metallurgy

Branch audited: mc1.21.1/dev at source snapshot 89a6993968a18503ff74256db0ecedb76dac64e9.

All actions in this section are **CONDITIONAL on Metallurgy winning the foundry A/B.**

## MET-01 — Wolframite worldgen

Upstream:

~~~text
src/generated/resources/data/createmetallurgy/neoforge/biome_modifier/wolframite_ore.json
src/generated/resources/data/createmetallurgy/worldgen/configured_feature/wolframite_ore.json
src/generated/resources/data/createmetallurgy/worldgen/placed_feature/wolframite_ore.json
~~~

Action:

**MANDATORY if Metallurgy is installed.**

Disable Wolframite placement.

Do not redirect into Skyforge Nether geology.

## MET-02 — Industrial Crucible self-gate

Upstream:

~~~text
src/generated/resources/data/createmetallurgy/recipe/sequenced_assembly/industrial_crucible.json
~~~

Problem:

- current gate depends on Obdurium/Tungsten;
- those materials are rejected from primary progression.

Action:

**IMPLEMENTED IN THE WAVE C1 DEVELOPMENT SPECIMEN.**

Current prototype replaces the Obdurium/Tungsten gate with:

~~~text
Deepslate Bricks
+ Refractory Mortar
+ Create Sturdy Sheet
+ Grinding
+ 90 mB createmetallurgy:molten_steel
    -> Industrial Crucible
~~~

This makes the larger crucible a Steel-fed upgrade from the basic foundry. CBC Steel can enter Metallurgy through #c:ingots/steel, avoiding Nethersteel circularity and rejected ores. Runtime craft proof remains pending.

## MET-03 — Steel alloying overlap

Upstream:

~~~text
src/generated/resources/data/createmetallurgy/recipe/alloying/steel.json
~~~

Action:

**CONDITIONAL NORMALIZATION.**

If Metallurgy owns the preferred bulk Steel process:

- keep this as canonical mature foundry route;
- decide whether CBC stock Steel mixing remains as a simpler route;
- prevent conversion/yield exploits.

If it does not improve play:

- CBC remains canonical and Metallurgy may be removed.

## MET-04 — Bronze alloying overlap

Upstream:

~~~text
src/generated/resources/data/createmetallurgy/recipe/alloying/bronze.json
~~~

Action:

**CONDITIONAL NORMALIZATION.**

CBC's tinless Bronze route remains the leading player-facing Bronze route.

Retained Metallurgy Bronze must not require Tin progression or create duplicate-yield exploits.

## MET-05 — molten Steel/Bronze bridge

Metallurgy provides:

~~~text
createmetallurgy:molten_steel
createmetallurgy:molten_bronze
~~~

CBC cannon casting consumes common molten tags.

Action:

**MANDATORY if Metallurgy is retained as upstream foundry.**

Add pack-level common-fluid tag bridges where identities are genuinely equivalent.

Target:

~~~text
Metallurgy bulk foundry
    -> common molten Steel/Bronze
    -> CBC cannon casting
~~~

No parallel fluid silos.

# Create: Big Cannons

CBC is retained.

## CBC-01 — Steel recipe

Upstream:

~~~text
src/generated/resources/data/createbigcannons/recipe/mixing/alloy_steel.json
~~~

Current:

~~~text
2 Iron + Coal + HEATED
    -> 2 Steel
~~~

Action:

**TEST-TRIGGERED ONLY.**

Do not override until gameplay establishes that Steel trivializes Cast Iron/Bronze.

If retuning is needed, prefer:

- process escalation;
- carbon/refractory/foundry complexity;
- or Metallurgy bulk Steel only if that mod wins.

Do not add Steel ore.

## CBC-02 — Bronze tinless recipe

Upstream:

~~~text
src/generated/resources/data/createbigcannons/recipe/mixing/alloy_bronze_tinless.json
~~~

Action:

**NO-CHANGE BASELINE.**

This route is important because it avoids Tin geology.

## CBC-03 — Nethersteel

Upstream:

~~~text
recipe/mixing/alloy_nethersteel_cast_iron.json
recipe/mixing/alloy_nethersteel_steel.json
~~~

Action:

**NO-CHANGE BASELINE.**

Strong current progression:

- Netherite Scrap;
- retained cannon metal;
- superheat.

## CBC-04 — Cast Iron

Upstream:

~~~text
recipe/compacting/iron_to_cast_iron_ingot.json
~~~

Action:

**NO-CHANGE BASELINE.**

Retained-material route with useful heat gate.

# Create Aeronautics / Simulated

## AERO-01 — Levitite

Current direct recipe uses retained:

- End Stone Powder;
- Zinc;
- Water;
- HEATED processing.

Action:

**NO-CHANGE BASELINE** pending vehicle-balance testing.

## AERO-02 — direct Create Brass/Iron/Copper references

Current source contains some direct references to Create's own Brass Sheet / Iron Sheet and common Copper inputs.

Action:

**NO CHANGE presently.**

These refer to canonical Create materials rather than a competing resource economy.

Only normalize if another retained producer must interoperate with a hard direct item reference.

# Create Diesel Generators

Current key heavy-engine recipes already rely principally on:

- Brass;
- Andesite Alloy;
- Create fluid infrastructure;
- Flint and Steel / ignition;
- petroleum fluids.

Action:

**NO MATERIAL NORMALIZATION currently identified.**

Future work is balance/throughput, not resource subtraction.

# Collision priority

## Wave C1 — required integrated-prototype overrides

1. CC&A Silver-free baseline electrical storage.
2. Propulsion Platinum worldgen disable **if Propulsion is loaded**.
3. Propulsion Platinum-byproduct economics first; recipe rebases **only if selected features fail the runtime cost test**.
4. Metallurgy Wolframite disable + retained-material Crucible re-recipe **for the A/B specimen**.

## Wave C2 — shared-material normalization

1. Steel item tags.
2. Bronze item tags.
3. molten Steel/Bronze tags if Metallurgy survives.
4. duplicate-yield loop checks.

## Wave C3 — gameplay-triggered balance

1. CBC Steel recipe.
2. CBC casting cadence.
3. Electrum tier.
4. petroleum throughput.
5. advanced propulsion recipe severity.

# Acceptance tests

## COLL-1

No selected mod can force a rejected raw material into required progression.

## COLL-2

Every mandatory override is data/config driven unless proven impossible.

## COLL-3

Conditional overrides do not land before the relevant mod wins its A/B.

## COLL-4

CBC baseline material routes remain intact unless a measured balance problem requires change.

## COLL-5

Recipe viewer exposes one coherent path per major capability.

## COLL-6

Upstream mod updates can be re-audited by checking this manifest against changed file paths.

# Acceptance principle

> Override exact points of disagreement between upstream mods and the Skyforge economy; do not fork whole recipe trees when a handful of data resources can make them coherent.
