# Wave C1 Static Recipe Closure Audit v0.1

**Snapshot:** 2026-09-05  
**Status:** Source-level closure evidence recorded; exact pinned-jar/runtime acceptance remains pending.

## Purpose

Reduce the Wave C1 human runtime gate to the questions that genuinely require Minecraft.

This audit traces the selected 1.21.1 engineering stack at recipe/resource level and distinguishes:

~~~text
STATICALLY CLOSED
    current data shows a viable retained-material path

OPTIONAL ALTERNATIVE
    rejected material appears, but canonical progression does not require it

CONFIRMED LEAK
    retained capability hard-requires a rejected material

RUNTIME QUESTION
    resource precedence, recipe-viewer behavior, or gameplay value must be observed live
~~~

The current upstream source trees corroborate the intended pinned release lines, but the pinned jars remain authoritative for final acceptance.

# CBC ↔ Metallurgy Steel/Bronze interoperability

## CBC already uses common ingot tags

Create: Big Cannons registers its Steel and Bronze into the common NeoForge material vocabulary and consumes:

~~~text
#c:ingots/steel
#c:ingots/bronze
~~~

in multiple cannon recipes.

Its melting recipes likewise consume those common ingot tags.

This is a strong compatibility result.

Skyforge does not need to replace CBC's player-facing Steel/Bronze items merely to make another foundry recognize them.

## Metallurgy melts the same common tags

Current Create: Metallurgy recipes accept:

~~~text
#c:ingots/steel
    -> 90 mB createmetallurgy:molten_steel

#c:ingots/bronze
    -> 90 mB createmetallurgy:molten_bronze
~~~

Therefore CBC Steel/Bronze can enter the Metallurgy fluid process directly.

### Remaining identity collision

The fluids are still separate from CBC's own molten fluids.

There is also an output asymmetry:

~~~text
Metallurgy molten Steel
    -> createmetallurgy:steel_ingot

Metallurgy molten Bronze
    -> #c:ingots/bronze
~~~

This means the integrated runtime must still inspect:

- which Bronze item the tag-valued casting output resolves to;
- whether Steel visibly converts between CBC and Metallurgy identities;
- whether CBC and Metallurgy molten fluids need common fluid-tag bridges;
- whether any recipe cycle changes yield.

No static yield amplification is evident from the ordinary 90 mB ingot melt/cast pair.

Classification:

~~~text
ITEM INTEROPERABILITY
    STATICALLY PROMISING

FLUID / OUTPUT IDENTITY
    RUNTIME QUESTION
~~~

# Create: Metallurgy rejected-material closure

## Foundry Mixer

The current Foundry Mixer recipe is:

~~~text
Create Cogwheel
+ Create Copper Casing
+ Sturdy Whisk
    -> Foundry Mixer
~~~

The Sturdy Whisk itself is crafted from Andesite Alloy and Create Sturdy Sheets.

Its recipe-unlock criterion references Tungsten Sheet, but Tungsten is not an ingredient in the actual whisk or mixer recipe.

That is a recipe-book/presentation concern, not a hard material dependency; JEI is part of the Wave C1 specimen specifically so the actual craft path can be inspected.

Classification:

~~~text
HARD TUNGSTEN REQUIREMENT
    NONE FOUND

RECIPE-BOOK DISCOVERABILITY
    RUNTIME / PRESENTATION QUESTION
~~~

## Industrial Crucible

Upstream Industrial Crucible sequenced assembly hard-requires:

~~~text
#c:plates/obdurium
+
30 mB createmetallurgy:molten_tungsten
~~~

With Wolframite worldgen disabled, that is a confirmed rejected-material leak if the larger foundry/crucible capability is retained.

Wave C1 therefore now overrides exactly this recipe.

Prototype route:

~~~text
Deepslate Bricks
    + Refractory Mortar
    + Create Sturdy Sheet
    + Grinding
    + 90 mB Metallurgy Molten Steel
        -> Industrial Crucible
~~~

Why this route:

- refractory construction remains explicit;
- Sturdy Sheet communicates reinforced capital equipment;
- one ingot-equivalent of molten Steel makes the Industrial Crucible an upgrade from the basic foundry rather than a new ore gate;
- CBC Steel can feed it through Metallurgy's shared-tag melting route;
- no Nethersteel circularity is introduced;
- no Java is required.

The override is guarded by a NeoForge mod-loaded condition for createmetallurgy so baseline Wave C1 runs do not attempt to load a recipe containing absent Metallurgy registry objects.

Classification after override:

~~~text
TUNGSTEN / OBDURIUM CRUCIBLE GATE
    STATICALLY CLOSED
    RUNTIME CRAFT PROOF PENDING
~~~

## Metallurgy Bronze and Tin

Metallurgy's own Bronze alloying recipe is:

~~~text
30 mB molten Copper
+ 10 mB molten Tin
    -> 40 mB molten Bronze
~~~

Skyforge still rejects Tin as a required geology/progression material.

However CBC already supplies a tinless Bronze route, and Metallurgy can melt any #c:ingots/bronze item into its molten Bronze.

Therefore Tin is not presently required to obtain or use Bronze in the integrated prototype.

Classification:

~~~text
METALLURGY TIN-BRONZE ALLOY RECIPE
    OPTIONAL ALTERNATIVE

CANONICAL SKYFORGE BRONZE
    CBC TINLESS ROUTE
~~~

If JEI makes the Tin route look canonical or creates a dead-end onboarding problem, Wave C1 may disable that one alloying recipe as presentation cleanup. Do not add Tin merely to satisfy it.

# Propulsion Platinum acquisition correction

The earlier material audit correctly rejected Platinum geology, but static source tracing found an important non-geological acquisition path.

Create Propulsion overrides Create's crushed-gold washing recipe so that one washed:

~~~text
create:crushed_raw_gold
~~~

produces the normal Gold result plus:

~~~text
5% chance
    -> 1 createpropulsion:platinum_nugget
~~~

Therefore:

~~~text
PLATINUM ORE WORLDGEN OFF
~~~

does not imply:

~~~text
PLATINUM UNOBTAINABLE
~~~

Platinum can instead behave as a rare Gold-refining byproduct.

## Expected acquisition cost

At a 5% independent nugget chance:

~~~text
1 Platinum Nugget
    ≈ 20 washed Crushed Raw Gold expected

1 Platinum Ingot / Sheet
    = 9 nuggets
    ≈ 180 washed Crushed Raw Gold expected
~~~

Ignoring variance and the ordinary Gold output, current Platinum-gated recipes imply approximately:

| Capability | Platinum content | Expected washed Crushed Raw Gold |
|---|---:|---:|
| Cable batch (4) | 1 sheet + 1 nugget = 10 nuggets | 200 |
| Redstone Converter | 1 sheet = 9 nuggets | 180 |
| Platinum Tank/Vessel | 2 sheets = 18 nuggets | 360 |
| Vector Thruster | 2 sheets + 2 nuggets = 20 nuggets | 400 |
| Cable Relay | casing + one 4-cable batch + 4 nuggets = 23 nuggets | 460 |
| Coral Generator | 4 sheets = 36 nuggets | 720 |

The Cable Relay estimate counts its Platinum Casing as one Platinum ingot and one cable recipe yielding four cables.

This changes the design question materially.

## Revised Platinum decision for C1

Do not author Platinum ore.

Do not automatically rebase every Platinum recipe yet.

Instead test:

~~~text
GOLD REFINING
    -> rare Platinum byproduct
    -> advanced propulsion/electrical capital equipment
~~~

against:

~~~text
retained-material recipe rebase
~~~

The byproduct model has potential advantages:

- no extra geology;
- creates a strategic mature demand for Gold processing;
- makes Platinum a refinery coproduct rather than a second mining economy;
- preserves upstream recipes with less bespoke work.

Its likely weakness is severity: hundreds of washed Gold inputs per advanced machine may be excessive, especially for multi-thruster aircraft.

Classification:

~~~text
PLATINUM GEOLOGY
    REJECT

PLATINUM BYPRODUCT ECONOMY
    PROVISIONAL / RUNTIME BALANCE QUESTION

PLATINUM RECIPE REBASE
    TEST-TRIGGERED, NOT AUTOMATIC
~~~

# CC&A Silver-free baseline

Wave C1 still replaces the Modular Accumulator's hard conductor ingredient with CC&A's existing usable-wire tag.

The runtime specimen must still prove that the exact pinned tag resolves to the intended Gold/Electrum alternatives and that JEI communicates the Gold route clearly.

Classification:

~~~text
SILVER-FREE OVERRIDE
    STATICALLY PRESENT

TAG CONTENT / JEI PRESENTATION
    RUNTIME PROOF PENDING
~~~

# Static closure summary

~~~text
CBC Steel/Bronze common ingot interoperability
    PROMISING / no item-tag bridge currently justified

Metallurgy Industrial Crucible Tungsten/Obdurium leak
    CLOSED by one development recipe override

Metallurgy Tin Bronze route
    OPTIONAL ALTERNATIVE, not canonical

Metallurgy molten Steel/Bronze identity
    RUNTIME collision audit still required

Propulsion Platinum geology
    DISABLED

Propulsion Platinum acquisition
    GOLD-REFINING BYPRODUCT EXISTS

Propulsion Platinum economics
    RUNTIME balance gate

CC&A Silver-free accumulator
    STATIC override present / runtime viewer proof pending
~~~

## Acceptance principle

> Subtract geology before subtracting capability. Reuse shared tags before inventing bridges. Override a recipe only when the current data proves that a rejected material actually blocks a capability we intend to test.
