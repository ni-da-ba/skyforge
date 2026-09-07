# Create: Big Cannons Material Access Closure v0.1

**Snapshot:** 2026-09-05  
**Status:** Working progression/recipe-closure contract. CBC is retained; this document determines whether every CBC cannon material has an intentional survival acquisition route in Skyforge and which recipe changes are actually required.

# Core conclusion

Skyforge does **not** need new geology for CBC's material ladder.

Current CBC 1.21.1 can close its entire cannon-material chain from retained resources:

~~~text
IRON PLATES + GUNPOWDER
    -> Wrought-Iron cannon family

IRON + COAL + HEATED processing
    -> Cast Iron

COPPER + ZINC + CINDER FLOUR + HEATED mixing
    -> Bronze

IRON + COAL + HEATED mixing
    -> Steel

NETHERITE SCRAP
+ Cast Iron or Steel
+ SUPERHEATED mixing
    -> Nethersteel
~~~

The integration task is therefore not "invent sources for five metals."

It is:

1. guarantee the upstream resources/capabilities that CBC recipes assume;
2. decide where each material belongs in Skyforge progression;
3. normalize duplicate metal identities if other mods also supply them;
4. change recipes only when the stock gate conflicts with the desired gameplay role.

# Material closure matrix

| CBC family/material | Current survival input | Current process gate | Skyforge source obligation | Recipe action |
|---|---|---|---|---|
| Wrought Iron cannon family | Iron plates + Gunpowder | mechanical pressing for plates; no CBC alloy step | Iron + renewable/obtainable Gunpowder | **No change presently** |
| Cast Iron | Iron ingot + Coal/Charcoal | **HEATED compacting** | Iron + coal + Blaze Burner heat | **No access change required**; balance test |
| Bronze | Copper + Zinc + Cinder Flour | **HEATED mixing** | Copper + Zinc + Netherrack -> Cinder Flour + Blaze heat | **No access change required if post-Nether** |
| Steel | 2 Iron + Coal/Charcoal | **HEATED mixing** | Iron + coal + Blaze heat | **Accessible, but likely balance/progression audit** |
| Nethersteel | Netherite Scrap + 8 Cast Iron **or** 4 Steel | **SUPERHEATED mixing** | Ancient Debris/Netherite Scrap + retained cannon metal + Blaze Cake superheat | **No access change required**; high-tier balance test |

# Important distinction: Wrought Iron is not another ingot economy

CBC's current Wrought-Iron cannon chamber recipe uses:

~~~text
4 x c:plates/iron
1 x CBC gunpowder-tag item
~~~

and the cannon end uses Iron plates, an Iron ingot, and Gunpowder.

Therefore Skyforge does not need:

- Wrought Iron ore;
- Wrought Iron ingots;
- another smelting/alloy recipe;
- a separate regional resource.

It is the **directly fabricated primitive cannon family**.

This is favorable.

# Hidden capability gates

The raw ingredients alone do not describe progression.

## Gunpowder

Wrought-Iron cannon construction consumes Gunpowder before the later CBC metallurgy chain is available.

Skyforge already intends to preserve renewable Gunpowder through some combination of:

- engineered hostile spawning / Creeper farming;
- civilization trade;
- structure loot and salvage.

### Closure requirement

A player capable of mature pre-Nether Create industry must have at least one reliable route to Gunpowder.

This does **not** require Gunpowder to be abundant at spawn.

It does require that CBC's earliest cannon family not become accidentally impossible because Skyforge hostile-spawn governance removed the ordinary Creeper economy without replacement.

# HEATED processing is a real progression gate

CBC's Cast Iron, Bronze, and Steel recipes use Create's `HEATED` processing requirement.

In ordinary Create progression this means Blaze-Burner heat.

Therefore the present natural material progression is closer to:

~~~text
PRE-NETHER / PRE-BLAZE
    Wrought-Iron cannon family

POST-BLAZE HEAT
    Cast Iron
    Bronze
    Steel

POST-NETHERITE + SUPERHEAT
    Nethersteel
~~~

This is substantially cleaner than adding arbitrary recipe tiers.

## Design implication

Do **not** remove the Blaze heat gate merely because Iron/Coal/Copper/Zinc exist earlier.

It gives the Nether an immediate industrial payoff.

If playtesting later shows one of these materials should exist pre-Nether, change that specific recipe rather than flattening the whole heat ladder.

# Bronze closure

CBC's preferred Skyforge-compatible route is already the stock tinless recipe:

~~~text
Copper ingot
+ Zinc ingot
+ Cinder Flour
+ HEATED mixing
    -> 2 Bronze ingots
~~~

Create produces Cinder Flour by crushing Netherrack.

Therefore Bronze needs:

~~~text
Copper
Zinc
Nether access
Netherrack
Crushing
Blaze heat
~~~

and **does not need Tin**.

## Decision

Tin remains excluded unless another selected system independently justifies it.

## Progression consequence

Stock Bronze is naturally post-Nether.

That is acceptable unless later cannon/gameplay testing demonstrates a need for a pre-Nether lightweight artillery material.

Wrought Iron already supplies the pre-Nether cannon role.

# Cast Iron closure

Current CBC route:

~~~text
Iron ingot
+ Coal / Charcoal
+ HEATED compacting
    -> Cast Iron ingot
~~~

This route uses only retained resources but is still heat-gated.

## Role hypothesis

Cast Iron should be the first **true foundry/cast cannon material** after Blaze heat:

~~~text
Wrought Iron
    hand/direct fabrication

Cast Iron
    first molten-metal / cast-cannon industry
~~~

This creates a visible industrial transition without new geology.

# Steel closure and likely recipe-tuning issue

Current CBC route:

~~~text
2 Iron
+ Coal / Charcoal
+ HEATED mixing
    -> 2 Steel
~~~

This is fully obtainable with retained resources.

However, Steel is a much stronger cannon material than Cast Iron and Bronze, and the stock recipe is chemically/process-wise almost as easy as Cast Iron once Blaze heat exists.

Therefore Steel is **closed for access but not yet closed for progression balance**.

This is likely one of the real recipe-tuning surfaces.

## Candidate outcomes

### S-0 — keep stock

Use stock Steel if:

- machine/factory costs dominate enough;
- Steel's greater mass and welding penalties make Bronze/Cast Iron remain useful;
- cannon manufacturing itself provides sufficient tier separation.

This is the lowest-bespoke option.

### S-1 — retained-resource process escalation

If stock Steel arrives too cheaply, require a more mature retained process without adding another ore.

Possible low-bespoke concepts include:

~~~text
Iron + carbon feedstock
    + SUPERHEATED processing
        -> Steel
~~~

or a coke/refractory path **only if** Create: Metallurgy wins its foundry A/B.

Do not choose an exact replacement recipe before representative cannon testing.

### S-2 — Metallurgy-owned Steel process

If Create: Metallurgy is retained because its foundry is fun, let its coke/molten-iron Steel process become the canonical bulk route and normalize CBC's simple Steel recipe around that progression.

This is acceptable only if it does not make CBC depend on Metallurgy merely for dependency's sake.

# Nethersteel closure

Current CBC provides two routes:

~~~text
1 Netherite Scrap
+ 8 Cast Iron
+ SUPERHEATED
    -> 8 Nethersteel
~~~

or:

~~~text
1 Netherite Scrap
+ 4 Steel
+ SUPERHEATED
    -> 8 Nethersteel
~~~

This is already an excellent Skyforge high-tier recipe.

It ties together:

- Nether exploration/mining;
- existing cannon metallurgy;
- superheat;
- high-volume manufacturing.

No new ore is required.

# Superheat closure

Nethersteel requires Create `SUPERHEATED` processing.

The standard Create superheat route depends on Blaze Cakes.

Current Create Blaze Cake production uses:

~~~text
Egg
+ Sugar
+ Cinder Flour
    -> Blaze Cake Base

Blaze Cake Base
+ 250 mB Lava
    -> Blaze Cake
~~~

Therefore **Nethersteel access silently depends on**:

- a renewable/guaranteed egg source;
- Sugar / sugar-cane access;
- Netherrack -> Cinder Flour;
- Lava;
- captured Blaze Burner.

These are now explicit world/progression obligations.

## SUPERHEAT_FUEL capability

Skyforge should treat this as a capability closure:

~~~text
SUPERHEAT_FUEL
    requires:
        EGG_SOURCE
        SUGAR_SOURCE
        CINDER_FLOUR_SOURCE
        LAVA_ACCESS
        BLAZE_BURNER
~~~

If semantic fauna/population rules make ordinary chickens unreliable, Skyforge must guarantee another egg source or adjust the Blaze Cake recipe.

Do **not** discover this only after the player reaches Nethersteel.

# Ancient Debris / Netherite obligation

Nethersteel makes Ancient Debris a real CBC industrial resource, not merely a vanilla armor upgrade.

If Skyforge eventually authors Nether geology, it must preserve an intentional route to Netherite Scrap.

Possible realization is not locked, but the capability must remain:

~~~text
NETHERITE_SCRAP_SOURCE
    reachable
    repeatable enough for artillery-scale use
    expensive enough to remain strategic
~~~

Do not simply inherit vanilla ore frequency without checking CBC's consumption scale.

# Common-tag closure

CBC already uses common item tags for most material inputs:

~~~text
c:plates/iron
c:ingots/iron
c:ingots/copper
c:ingots/zinc
c:ingots/cast_iron
c:ingots/steel
~~~

and common molten-fluid tags for cannon casting.

This is favorable for integration.

The pack should use common tags to make retained producers interchangeable wherever they truly represent the same material.

# If Create: Metallurgy is removed

CBC's entire material ladder remains viable.

Required recipe work is potentially very small:

~~~text
REQUIRED
    none for basic material access

LIKELY
    Steel progression/balance tuning only if playtest shows a problem

WORLD / PROGRESSION
    guarantee Gunpowder
    guarantee Blaze/heat
    guarantee Copper/Zinc
    guarantee Netherrack
    guarantee Netherite Scrap
    guarantee superheat fuel inputs
~~~

# If Create: Metallurgy is retained

Then recipe/data work grows because the goal becomes **one foundry economy**, not two parallel mods.

Required integration becomes roughly:

~~~text
common molten-fluid tag bridges
Steel identity normalization
Bronze identity normalization
Industrial Crucible recipe replacement
Wolframite/Tungsten/Obdurium progression removal
duplicate-recipe exploit audit
recipe-viewer cleanup
~~~

This is the significant recipe-tweaking branch.

It is optional and must be justified by foundry gameplay.

# Recommended progression hypothesis

Current lowest-bespoke progression to test:

~~~text
P2 EARLY AVIATION / MATURE OVERWORLD
    Iron plates
    Gunpowder
        -> Wrought-Iron artillery

NETHER / BLAZE HEAT
    Iron + Coal
        -> Cast Iron

    Copper + Zinc + Cinder Flour
        -> Bronze

    Iron + Coal
        -> Steel
        [balance gate under test]

MATURE NETHER INDUSTRY
    Blaze Cakes / superheat
    Netherite Scrap
    Cast Iron or Steel
        -> Nethersteel
~~~

The materials are not intended as a simple linear upgrade ladder.

Their cannon properties should preserve sidegrades/tradeoffs:

- Wrought Iron: primitive/direct;
- Cast Iron: cheap cast industrial;
- Bronze: lighter, pressure-tolerant, different failure/weld behavior;
- Steel: heavy high-performance;
- Nethersteel: extreme pressure/performance with mass and repair constraints.

# Recipe-work classification

## Category 0 — no change

Keep upstream recipe exactly as-is unless playtesting disproves it.

Current candidates:

- Wrought-Iron cannon components;
- Cast Iron;
- Bronze;
- Nethersteel.

## Category 1 — balance/progression override

Recipe works, but its gate or yield may undermine the intended progression.

Current leading candidate:

- Steel.

## Category 2 — interoperability normalization

Needed only when two retained mods represent the same material/process.

Current candidates if Metallurgy remains:

- Steel item/fluid identity;
- Bronze item/fluid identity;
- shared molten tags;
- duplicate processing routes.

## Category 3 — emergency closure override

Use only if semantic Skyforge world design accidentally removes a vanilla/Create input.

Potential examples:

- Egg source unavailable for Blaze Cakes;
- renewable Gunpowder unavailable;
- Ancient Debris omitted from authored Nether;
- Sugar source omitted.

Prefer fixing world/progression availability before changing recipes.

# Acceptance tests

## CBC-MAT-1 — Wrought-Iron closure

A pre-Nether mature Create player can obtain Iron plates and an intentional Gunpowder source and build Wrought-Iron artillery.

## CBC-MAT-2 — Heat transition

Blaze access visibly unlocks Cast Iron/Bronze/Steel manufacturing.

## CBC-MAT-3 — Bronze without Tin

Bronze is fully obtainable with Copper/Zinc/Cinder Flour and no Tin geology.

## CBC-MAT-4 — material choice remains real

Steel does not make Cast Iron/Bronze irrelevant immediately after Blaze acquisition.

## CBC-MAT-5 — superheat closure

The player can produce Blaze Cakes from guaranteed retained inputs.

## CBC-MAT-6 — Nethersteel closure

Netherite Scrap + cannon metals + superheat produces Nethersteel without hidden unavailable resources.

## CBC-MAT-7 — artillery-scale Netherite

Netherite Scrap availability supports expensive strategic artillery without becoming either trivial or effectively nonrenewable for realistic play.

## CBC-MAT-8 — no duplicate Steel economy

If another mod supplies Steel, all retained recipes accept one coherent tagged Steel identity without loops/exploits.

## CBC-MAT-9 — no orphan CBC metal

Every CBC cannon material shown to the player has a documented survival acquisition route.

## CBC-MAT-10 — minimal bespoke rule

No recipe override exists merely for aesthetic consistency; each override resolves a demonstrated progression, balance, or interoperability problem.

# Immediate implementation/design consequences

Add to Skyforge capability requirements:

~~~text
GUNPOWDER_SOURCE
BLAZE_HEAT
CINDER_FLOUR_SOURCE
SUPERHEAT_FUEL
NETHERITE_SCRAP_SOURCE
CBC_CAST_IRON
CBC_BRONZE
CBC_STEEL
CBC_NETHERSTEEL
~~~

and explicitly verify those capabilities against starter/province/Nether authoring before content lock.

# Acceptance principle

> CBC material access should be closed by world resources and meaningful process gates first; recipe rewriting is the exception used to repair progression or interoperability, not the primary way Skyforge manufactures complexity.
