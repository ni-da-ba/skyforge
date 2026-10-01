# Create: Big Cannons Industrial Integration Audit v0.1

**Snapshot:** 2026-09-05  
**Status:** Working integration contract for Create: Big Cannons 5.11.7 on Minecraft 1.21.1 / NeoForge / Create 6.0.7+.

## Selection status

Create: Big Cannons is **retained**.

This audit is not a keep/remove decision.

It answers:

> How much of CBC's native industrial chain should Skyforge preserve, what should connect to the rest of the pack, and how much bespoke/configuration work is actually necessary?

## Current upstream state

CBC 5.11.7:

- supports Minecraft 1.21.1 NeoForge;
- targets Create 6.0.7–6.0.x;
- declares Sable 2.0.x compatibility;
- provides data-driven big-cannon and autocannon material properties;
- provides data-driven cannon casting recipes and fluid casting times;
- provides a deep native metallurgy/manufacturing chain without adding new ore worldgen.

This is a favorable fit for Skyforge's low-bespoke policy.

## Material-access closure

The exact survival acquisition paths and hidden capability gates are tracked separately in [Create: Big Cannons material access closure v0.1](create-big-cannons-material-access-closure-v0.1.md).

Important correction to simplistic ingredient-only readings: CBC Cast Iron, Bronze, and Steel use Create `HEATED` processing and are therefore naturally **post-Blaze-Burner** in the stock Create progression. Wrought-Iron cannon components remain the leading pre-Blaze family. Nethersteel adds `SUPERHEATED` processing and therefore also requires the Blaze-Cake supply chain.

# Why CBC matters to Skyforge industry

CBC is not merely a combat-content mod.

Its production chain creates several industrial verbs:

~~~text
ALLOY
MELT
CAST
BORE
BUILD UP
HEAT-TREAT
WELD
LOAD
ASSEMBLE AMMUNITION
MANUFACTURE PROPELLANT
AIM
AUTOMATE
SUPPLY
REPAIR
~~~

These are useful because Skyforge wants late-game industry to create visible infrastructure and logistics rather than only stronger inventory items.

# Native material ladder

CBC's current material progression is unusually clean because it derives from already useful resources.

## Wrought Iron

Uses ordinary Iron.

Role:

- crude/early cannon construction;
- low pressure;
- directly assembled;
- no new metallurgy system required.

## Cast Iron

Current route:

~~~text
Iron
+ Coal / Charcoal
+ HEATED compacting
    -> Cast Iron
~~~

Big-cannon behavior:

~~~text
safe stress 2
weight      3
fragment failure
weldable
~~~

This makes Cast Iron an inexpensive, accessible industrial weapon material.

## Bronze

Current tinless route:

~~~text
Copper
+ Zinc
+ Cinder Flour
+ HEATED mixing
    -> 2 Bronze
~~~

Alternative routes can use Tin or Brass if available.

Big-cannon behavior:

~~~text
safe stress 4
weight      2
rupture failure
weldable
no weld stress penalty
~~~

This is a strong material tradeoff rather than a cosmetic intermediate.

## Steel

Current CBC route:

~~~text
2 Iron
+ Coal / Charcoal
+ HEATED mixing
    -> 2 Steel
~~~

Big-cannon behavior:

~~~text
safe stress 8
weight      5
fragment failure
weldable
weld damage / stress penalty
better minimum spread
~~~

Autocannon Steel also supports:

- longest barrel class;
- highest mature projectile envelope;
- heavier/recoiling weapon construction.

Steel is therefore a **major industrial material** even if Create: Metallurgy is removed.

## Nethersteel

Current routes:

~~~text
Netherite Scrap
+ 8 Cast Iron
+ SUPERHEATED
    -> 8 Nethersteel
~~~

or:

~~~text
Netherite Scrap
+ 4 Steel
+ SUPERHEATED
    -> 8 Nethersteel
~~~

Big-cannon behavior:

~~~text
safe stress       10
weight            6
weldable          false
minimum spread    0.02
velocity/barrel   better than Steel
~~~

Nethersteel is an excellent top-tier material because it is:

- stronger;
- more accurate/capable;
- heavier;
- less field-repairable through welding;
- explicitly tied to Netherite and superheating.

It does not need a new ore.

# Cannon manufacturing chain

CBC already contains a complete manufacturing grammar.

## 1. Metal production

~~~text
resource metals
    -> alloy / heated process
    -> metal ingots
~~~

## 2. Melting

CBC's Basin Foundry Lid melts:

- Cast Iron;
- Bronze;
- Steel;
- Nethersteel;

into dedicated molten fluids.

Current melting uses **HEATED** processing.

## 3. Cannon casting

Cannon casts consume molten metal by shape.

Current fluid requirements per cast section:

~~~text
VERY_SMALL              630 mB  = 7 ingots
SMALL                   810 mB  = 9 ingots
MEDIUM                 1080 mB  = 12 ingots
LARGE                  1260 mB  = 14 ingots
VERY_LARGE             1800 mB  = 20 ingots
CANNON_END              810 mB  = 9 ingots
SLIDING_BREECH          810 mB  = 9 ingots
SCREW_BREECH            810 mB  = 9 ingots

AUTOCANNON_BARREL       270 mB  = 3 ingots
AUTOCANNON_BREECH       360 mB  = 4 ingots
AUTOCANNON_RECOIL       360 mB  = 4 ingots
~~~

These are substantial enough to create real metal demand.

A long built-up Steel or Nethersteel cannon can therefore represent a meaningful industrial investment.

## 4. Boring

Cast cannon parts are generally produced unbored.

The Cannon Drill then creates usable cannon components.

This is valuable because it makes the factory layout physically legible:

~~~text
MELT
    -> CAST
    -> COOL
    -> BORE
    -> ASSEMBLE
~~~

## 5. Built-up cannon construction

Steel and Nethersteel can be built from concentric cast layers.

The layers are assembled and then transformed by heating/blasting.

This gives the high-tier cannon industry another genuinely distinct process instead of only changing ingredient counts.

## 6. Breech mechanisms

CBC adds:

- sliding breeches;
- screw breeches;
- quick-firing mechanisms;
- autocannon breeches/recoil systems.

These consume material and Create machinery rather than bypassing the industrial system.

# Production practicality / fun audit

The native CBC chain has enough physical process structure to justify playtesting as a factory rather than replacing it with simplified recipes.

## Casting cadence

Current 5.11.7 data assigns material casting times:

~~~text
Cast Iron   2400 ticks = 120 s
Bronze      2400 ticks = 120 s
Steel       3600 ticks = 180 s
Nethersteel 6000 ticks = 300 s
~~~

Current source applies the casting-time value at the connected cast controller, then finishes each segment in the connected cast structure when the timer completes.

This appears to make a multi-segment cast a **batch operation**, which helps turn the long cooldown into factory planning rather than repeated per-block waiting.

There is a documentation/source discrepancy worth manual verification: current Ponder text says solidification time varies with cast size, while the audited controller code currently selects the timer from the molten fluid's casting-time data. Do not balance around either interpretation until runtime proof.

### Design judgment

The stock 2–5 minute cadence is plausible for large capital artillery if:

- multiple sections can be cast together;
- the player can do other factory work during cooling;
- the result is expensive enough to feel significant;
- automated metal supply is useful.

It becomes bad friction if the player repeatedly watches one small cast finish.

Casting time is data-driven, so this is a **low-cost datapack tuning axis**, not a reason for bespoke code.

## Boring

Current Cannon Drill behavior is more industrially interesting than a generic crafting step:

- requires rotational machinery;
- requires water/lubrication;
- drill speed must meet the target lathe's speed;
- water use increases with rotational speed;
- bore movement slows inversely with cannon-material weight;
- the active material adds stress impact to the machine.

This is a useful physical tradeoff.

Heavier Steel/Nethersteel are therefore not merely more expensive at the recipe level: they are more demanding to machine.

### Design judgment

Keep this behavior unless integrated play proves the lathe setup too tedious.

The desired player experience is:

~~~text
large casting
    -> cooling while factory continues
    -> mount on lathe
    -> supply water + power
    -> bore
    -> assemble / weld / build-up
~~~

not:

~~~text
stand still
    -> wait
    -> repeat identical operation
~~~

## Built-up guns and welding

CBC's high-end cannon path also adds process variety:

- concentric Steel/Nethersteel layers;
- Cannon Builder assembly;
- blasting to finish built-up cannon blocks;
- welding as a material-dependent assembly/repair operation.

Nethersteel's inability to weld is particularly valuable because it turns the top material into a design/maintenance tradeoff rather than a universal upgrade.

## Practicality acceptance

Record for representative guns:

- active player-attention time;
- passive cooling time;
- number of distinct machines used;
- metal throughput;
- water/lubricant demand;
- rebuild/repair burden;
- whether batching/automation removes repetitive work;
- whether a second gun feels easier because infrastructure now exists.

The last point is crucial.

A good industry loop should make the first factory expensive and later production **systematically easier**, rewarding infrastructure.

## Tuning hierarchy

If CBC manufacturing is too slow or tedious:

1. first tune data-driven casting times/material properties;
2. then adjust recipes/yields;
3. then adjust automation/access;
4. only write bespoke code if a specific hard limitation remains.

# Ammunition industry

CBC's ammunition chain also fits Skyforge unusually well.

## Gunpowder economy

Standard ammunition uses ordinary Gunpowder and compacted derivatives.

This creates a potential renewable logistics relationship through:

- Creeper farms;
- civilization trade;
- loot/salvage;
- engineered hostile spawning systems.

No sulfur/niter geology is necessary.

## Guncotton

Current recipe:

~~~text
nitrable material (default Paper)
+ Gunpowder
+ Water
+ nitro acidifier (default Redstone)
    -> Guncotton
~~~

Packed Guncotton is then compacted and used as high explosive material.

This is a good industrial recipe because it connects:

- paper/farm economy;
- gunpowder;
- water;
- redstone;
- Create mixing.

## Nitro chain

Current paths use combinations of:

- Blaze Powder;
- Magma Cream;
- Gunpowder;
- Guncotton;
- Slime;
- Water;
- Redstone.

Congealed Nitro can be hardened, then milled into Nitropowder.

Again, no new ore is required.

## Interpretation

CBC ammunition already creates a substantial chemical-manufacturing branch from retained materials.

Skyforge should **not** add sulfur, saltpeter, nitrate ore, or chemical elements merely to make the artillery industry feel more realistic.

The existing abstraction is deep enough.

# CBC as a logistics consumer

CBC gives several retained resources new recurring demand.

## Iron

- Cast Iron feedstock;
- Steel feedstock;
- projectiles/components;
- cannon machinery.

## Coal

- Cast Iron;
- Steel;
- ordinary fuel.

## Copper + Zinc

- Bronze;
- Brass;
- electrical infrastructure.

## Brass

- cartridges;
- machinery;
- cannon welder;
- overlapping Create infrastructure.

## Redstone

- fuzes;
- Guncotton;
- controls.

## Quartz

- proximity fuze/control uses;
- existing Create control chain.

## Gunpowder

- propellant;
- ammunition;
- chemical chain.

## Nether materials

- Blaze Powder;
- Magma Cream;
- Netherite Scrap;
- superheat;
- Nethersteel.

This is exactly the kind of cross-system consumption Skyforge needs.

# Interaction with Create: Metallurgy

CBC already provides:

- metal production;
- molten metal;
- cannon casting;
- large casting structures;
- material progression.

Therefore Create: Metallurgy is **not required to make CBC complete**.

This matters.

## What Metallurgy could add

A retained Create: Metallurgy foundry could provide:

- large general-purpose Industrial Crucibles;
- bulk melting;
- multi-fluid molten-metal storage;
- casting tables/basins;
- generic metal molds;
- foundry mixers;
- gauges/ladles;
- a more elaborate metalworks floor.

This may be fun.

But it is an enhancement, not a missing CBC dependency.

## Current interoperability gap

CBC uses common molten-fluid tags:

~~~text
c:molten_cast_iron
c:molten_bronze
c:molten_steel
c:molten_nethersteel
~~~

The audited Create: Metallurgy 1.21.1 source does not currently expose its molten fluids through those same common CBC tags.

Therefore CBC + Metallurgy is not automatically one seamless foundry.

### Minimal bridge if Metallurgy is retained

A small datapack should add appropriate Metallurgy fluids to CBC's common molten tags where material identity matches.

Example concept:

~~~text
c:molten_steel
    includes CBC molten Steel
    includes Metallurgy molten Steel
~~~

The goal is:

~~~text
Industrial Crucible
    -> pipe molten Steel
    -> CBC cannon cast
~~~

without bucket conversion or duplicate silos.

## Steel item unification

Both CBC and Metallurgy can provide Steel.

They already use common Steel item tags in many recipes.

Integrated testing must ensure:

- one Steel identity in recipe viewer;
- interchangeable Steel ingots;
- no accidental duplication loops;
- no yield exploit;
- no need to learn which mod's Steel is "correct."

Prefer common tags and recipe normalization over bespoke code.

# Recommended integration variants

## Variant A — native CBC industry

Keep CBC as designed.

Required Skyforge work:

1. no new CBC ore worldgen;
2. ensure ordinary Skyforge geology supplies enough Iron/Copper/Zinc/Coal;
3. preserve Netherite Scrap access;
4. ensure Gunpowder can be produced renewably;
5. integrate CBC materials into quests/structure loot where useful;
6. test Sable/Aeronautics behavior.

This is the **minimum-bespoke baseline**.

## Variant B — CBC + stripped Create: Metallurgy foundry

Additional pack work:

1. disable Wolframite worldgen;
2. remove Tungsten/Obdurium from required progression;
3. re-recipe Industrial Crucible around retained refractory + Steel/Nethersteel materials;
4. bridge common molten-fluid tags;
5. normalize duplicate Steel;
6. hide or disable obsolete Tungsten/Obdurium recipe surfaces where practical;
7. verify no loops/exploits across CBC compacting and Metallurgy casting.

This is acceptable only if the foundry is materially more fun/useful.

# Bespoke/configuration work estimate

## CBC itself

**Low.**

The current CBC industrial chain is largely usable as-is.

Likely required pack integration:

~~~text
DATA / CONFIG
    material balance if playtest requires
    loot/quest integration
    world interaction tuning
    possibly firearm/cannon damage tuning

CODE
    ideally none
~~~

## CBC + Metallurgy

**Moderate data integration, ideally no code.**

Likely work:

~~~text
DATAPACK
    fluid tag bridges
    Industrial Crucible recipe override
    optional recipe removals/normalization

WORLDGEN
    Wolframite disable

RECIPE VIEWER
    hide obsolete material surfaces if possible

CODE
    avoid unless an actual incompatibility proves necessary
~~~

# Sky-island gameplay implications

CBC becomes much more interesting in Skyforge than in ordinary terrain because artillery competes with aircraft mass, recoil, ammunition, and logistics.

Potential roles:

- fixed settlement defense;
- fortress batteries;
- airship artillery;
- anti-aircraft autocannons;
- convoy defense;
- siege of hostile structures;
- legendary-threat weapons;
- mining/demolition only if balance permits;
- naval-style engagements in open sky.

## Important design constraint

A cannon mounted on an aircraft must impose an engineering cost through existing systems:

- weapon mass;
- ammunition mass;
- recoil;
- firing arc;
- craft stability;
- reload machinery;
- ammunition storage;
- structural vulnerability.

Do not add an arbitrary "aircraft gun penalty" before testing existing Sable/CBC physics.

# Sable / Aeronautics compatibility status

CBC 5.11.7 explicitly targets Sable 2.0.x support.

However upstream compatibility is not yet assumed complete.

Current open upstream reports include:

- deployers failing to apply fuzes to projectiles on Sable sublevels;
- a destructive edge-case server crash involving HE shells and a removed Sable physics body.

These are **manual acceptance gates**, not reasons to cut CBC.

## Mandatory Sable/CBC tests

~~~text
CBC-SABLE-1
    fixed cannon on ordinary world

CBC-SABLE-2
    cannon mounted on Sable/Aeronautics craft

CBC-SABLE-3
    autocannon on moving craft

CBC-SABLE-4
    recoil changes craft motion/stability plausibly

CBC-SABLE-5
    manual reload on sublevel

CBC-SABLE-6
    automated reload on sublevel

CBC-SABLE-7
    fuze application on sublevel

CBC-SABLE-8
    shell firing from moving/rotating craft

CBC-SABLE-9
    impact on static Skyforge terrain

CBC-SABLE-10
    impact on another physical craft

CBC-SABLE-11
    HE destruction of physical structure

CBC-SABLE-12
    save/reload with armed craft

CBC-SABLE-13
    crash/failure recovery
~~~

Any crash or fundamental reload incompatibility is a human-visible blocker before showcase.

# Balance questions for manual play

## Cannon progression

Test whether:

~~~text
Wrought Iron
    -> Cast Iron
    -> Bronze
    -> Steel
    -> Nethersteel
~~~

actually feels like a set of engineering choices.

Do not assume every tier must be used.

If one tier is dominated in all practical Skyforge use cases, either:

- rebalance properties through CBC datapacks;
- change its recipe timing;
- or allow it to remain an optional niche rather than forcing quest progression through it.

## Material consumption

Measure actual consumption for:

- small defensive cannon;
- practical aircraft autocannon;
- medium field gun;
- heavy fixed gun;
- airship big cannon;
- built-up Steel cannon;
- Nethersteel late-game cannon.

This determines whether artillery creates:

- one-time capital demand;
- recurring metal logistics;
- ammunition logistics;
- both.

## Ammunition throughput

Measure:

- gunpowder/minute;
- redstone;
- paper;
- brass/copper;
- iron;
- special explosive ingredients.

Ammunition may become a stronger recurring logistics driver than cannon-metal production.

This would be healthy.

# Civilization and structure implications

CBC can make generated settlements communicate industrial maturity.

Potential roles:

~~~text
EARLY FORT
    wrought iron / cast iron gun

REGIONAL PORT
    bronze / steel battery

INDUSTRIAL HUB
    foundry + ammunition works + heavy gun

NETHER FORTIFICATION
    Steel / Nethersteel artillery

ABANDONED SITE
    broken gun / shells / casting infrastructure

END EXPEDITION BASE
    compact defensive or anti-air battery
~~~

Do not put rare cannon blocks into every settlement.

Artillery should communicate:

- wealth;
- danger;
- strategic importance;
- industrial capacity.

# Recommended current direction

The leading heavy-industry stack is now:

~~~text
IRON / COAL
    -> Cast Iron
    -> Steel

COPPER / ZINC / CINDER
    -> Bronze

NETHER
    -> Blaze / superheat
    -> Netherite Scrap
    -> Nethersteel

CBC
    -> melt
    -> cast
    -> bore
    -> build-up / weld
    -> arm
    -> ammunition production
~~~

Create: Metallurgy is evaluated as an optional **industrial foundry enhancement**, not as the source of the material progression.

# Acceptance tests

## CBC-IND-1 — no gratuitous ore

CBC industry works without introducing Tin, Silver, Wolframite, or other new ore solely for CBC.

## CBC-IND-2 — material distinction

Cast Iron, Bronze, Steel, and Nethersteel create observable cannon/vehicle engineering tradeoffs.

## CBC-IND-3 — manufacturing depth

Casting, boring, built-up construction, breech assembly, and ammunition production feel like useful industrial steps rather than chores.

## CBC-IND-4 — logistics payoff

A mature artillery installation creates meaningful metal/ammunition supply demand.

## CBC-IND-5 — Nether payoff

Superheat + Netherite Scrap -> Nethersteel is worth establishing Nether operations for players interested in heavy artillery.

## CBC-IND-6 — no Metallurgy dependency

CBC remains fully playable if Create: Metallurgy is removed.

## CBC-IND-7 — foundry enhancement gate

If Create: Metallurgy is retained, it must improve CBC production enough to justify its added machinery and integration surface.

## CBC-IND-8 — fluid interoperability

Any retained general foundry can feed CBC casts through common molten-fluid identity.

## CBC-IND-9 — Sable stability

CBC operations on physical aircraft do not produce unacceptable crashes, reload failures, or physics exploits.

## CBC-IND-10 — balance by existing physics first

Aircraft artillery balance is attempted through existing mass/recoil/ammunition/stability systems before bespoke penalties.

# Acceptance principle

> CBC should feel like the reason a civilization builds a steelworks and ammunition plant—not the reason Skyforge invents six extra ores.
