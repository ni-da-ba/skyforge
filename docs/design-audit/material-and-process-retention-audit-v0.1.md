# Material and Process Retention Audit v0.1

**Snapshot:** 2026-09-05  
**Status:** Working subtraction audit. The default decision for a new material/process is **exclude unless it changes engineering, logistics, or geography enough to justify its cognitive and integration cost**.

# Design rule

> Skyforge does not need a large periodic table. It needs a small set of materials and processes that create genuinely different engineering decisions.

A candidate material/process earns retention only if it contributes one or more of:

- a distinct engineering capability;
- a durable logistics/economic role;
- a materially different vehicle/weapon/industrial design choice;
- a useful cross-dimensional dependency;
- a process loop that is fun enough to justify its machinery and recipe surface.

A material should normally be rejected when it exists mainly because:

- one mod happens to reference its tag;
- it colors an otherwise identical recipe tier;
- it gates one machine and then becomes irrelevant;
- another retained material can provide the same capability with a small recipe adjustment.

# Decision summary

| Candidate | Current decision | Reason |
|---|---|---|
| Iron | KEEP | foundational construction/mechanical resource |
| Copper | KEEP | fluid handling + electrical conductors + Brass |
| Zinc | KEEP | Brass + capacitors + Levitite; persistent multi-era use |
| Brass | KEEP | logistics/control/fluid/electrical/heavy-engine convergence |
| Gold | KEEP | vanilla value + electrical conductor tier + redstone/advanced crafting |
| Silver | **EXCLUDE AS RAW RESOURCE** | no strong independent capability; currently mostly exists to make Electrum |
| Electrum | PROVISIONAL KEEP | hard-coded high-current electrical conductor may justify manufactured material, but must not force Silver geology |
| Cast Iron | KEEP | CBC-native cannon tier with distinct pressure/weight/failure behavior; made from Iron + Coal |
| Bronze | KEEP | CBC-native lighter/stronger cannon/autocannon tier; can be made from Copper + Zinc + Cinder Flour without Tin |
| Steel | **KEEP** | major CBC structural/ballistic material; no new ore required |
| Nethersteel | **KEEP** | CBC top cannon material; distinct pressure/accuracy/weight/weldability tradeoff; uses Netherite Scrap + existing metals + superheat |
| Tin | EXCLUDE unless another selected system independently justifies it | CBC does not require Tin because it has a tinless Bronze route |
| Wolframite | **EXCLUDE FROM SKYFORGE GEOLOGY / PROGRESSION** unless later evidence reverses | its current downstream role is too narrow |
| Tungsten | **EXCLUDE FROM PRIMARY PROGRESSION** unless later cross-mod demand appears | current meaningful consumers are too sparse |
| Obdurium | **EXCLUDE FROM PRIMARY PROGRESSION** | currently functions mainly as a gate into Create: Metallurgy's own Industrial Crucible |
| Create superheating | **KEEP** | already supports Create boilers/processes, improved petroleum refining, and CBC Nethersteel |
| Create: Metallurgy foundry system | PROVISIONAL A/B | core foundry mechanics may add useful industrial-scale metal handling, but its extra material tree is not automatically retained |
| Graphite molds | PROVISIONAL with Metallurgy | process consumable/tooling, not geology; acceptable if casting gameplay earns retention |
| Refractory mortar | PROVISIONAL with Metallurgy | simple sand/clay/water process material; acceptable if foundry gameplay earns retention |
| Coke / Metallurgy Steel | PROVISIONAL with Metallurgy | must not create a redundant parallel Steel economy to CBC |

# Silver

## Current evidence

Create Crafts & Additions 1.21.1:

- does **not** provide a Silver ore/worldgen loop;
- conditionally enables its Electrum mixing recipe only when `c:ingots/silver` is non-empty;
- uses Electrum principally as a high-current conductor and in a few electrical/equipment recipes.

Current wire transfer values are materially different:

~~~text
Copper   256 FE/t
Gold     1024 FE/t
Electrum 8196 FE/t
~~~

Current large connectors are configured for up to 5000 FE/t.

Current Electric Motor, Alternator, Accumulator, and Portable Energy Interface transfer limits are also around 5000 FE/t by default.

Therefore Electrum can provide a real electrical capability.

**Silver itself does not.**

## Decision

~~~text
SILVER RAW RESOURCE
    EXCLUDE

SILVER ORE / DEPOSIT
    DO NOT AUTHOR

SILVER REGIONAL ECONOMY
    DO NOT CREATE
~~~

Do not install or retain another ore-generating dependency merely to satisfy the Electrum recipe.

## Electrum resolution gate

Electrum remains provisional because the electrical distinction is real, but current CC&A data gives us a lower-bespoke baseline than inventing an alloy recipe.

Current source defines `createaddition:modular_accumulator_usable_wires` as accepting:

- Gold wire;
- Electrum wire.

Yet the stock Modular Accumulator crafting recipe directly hard-requires Electrum wire.

Therefore the leading pack integration is:

~~~text
Modular Accumulator recipe
    Electrum-only wire ingredient
        -> use the mod's existing usable-wire tag
~~~

This lets **Gold** satisfy baseline electrical storage without Silver or Electrum.

Electrum can then remain an optional high-current conductor rather than a progression dependency.

Current preferred sequence:

1. normalize the accumulator recipe onto the existing Gold/Electrum usable-wire tag;
2. run representative electrical networks on Gold's 1024 FE/t ceiling;
3. if that ceiling creates a useful high-current engineering problem, retain Electrum as an optional manufactured/byproduct upgrade;
4. only then design an Electrum production route, without Silver geology;
5. if 1024 FE/t is sufficient, do not force Electrum production at all.

Do **not** invent a Silver mining economy to preserve the name of one alloy.

# CBC-native metallurgy changes the audit

Create: Big Cannons 5.11.7 for 1.21.1 supplies a coherent material ladder without adding ore geography.

## Cast Iron

Current route:

~~~text
Iron
+ Coal / Charcoal
+ HEATED compacting
    -> Cast Iron
~~~

CBC big-cannon properties:

~~~text
max safe propellant stress = 2
weight                     = 3
failure mode               = fragment
weldable                   = yes
~~~

CBC autocannon properties also make Cast Iron the short-barrel/high-base-speed low-tier material.

### Decision

KEEP.

It is not a gratuitous new ore.

It is a **manufactured material with a distinct mechanical identity**.

# Bronze

CBC provides a tinless route:

~~~text
Copper
+ Zinc
+ Cinder Flour
+ HEATED mixing
    -> 2 Bronze
~~~

It also supports Tin when another mod supplies it, but Tin is not required.

CBC big-cannon properties:

~~~text
max safe propellant stress = 4
weight                     = 2
failure mode               = rupture
weld stress penalty        = 0
~~~

Compared with Cast Iron, Bronze is lighter, more pressure-tolerant, and behaves differently under failure/welding.

CBC autocannons also differ materially in barrel length, speed curve, recoil, and lifetime.

### Decision

KEEP BRONZE.

DO NOT ADD TIN solely for Bronze.

This is a good example of a material that changes design without requiring new geology.

# Steel

CBC 1.21.1 provides Steel directly:

~~~text
2 Iron
+ Coal / Charcoal
+ HEATED mixing
    -> 2 Steel
~~~

No Steel ore is required.

CBC Steel properties:

~~~text
max safe propellant stress = 8
weight                     = 5
failure mode               = fragment
weld damage                = 2
weld stress penalty        = 2
minimum spread             = 0.025
~~~

Steel also enables:

- long-barrel/high-performance autocannons;
- steel breeches and screw breeches;
- built-up cannon construction;
- spring-wire efficiency;
- a direct route to Nethersteel.

### Decision

**KEEP STEEL.**

Steel now has a major cross-system industrial consumer in CBC and does not require new geology.

It should become one of the principal mature industrial materials in Skyforge.

# Nethersteel

CBC current recipes:

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

CBC properties:

~~~text
max safe propellant stress = 10
weight                     = 6
weldable                   = no
minimum spread             = 0.02
minimum velocity/barrel    = lower than Steel
~~~

Nethersteel is therefore not merely "Steel +1."

It gives:

- higher pressure tolerance;
- improved ballistic envelope;
- increased weight;
- loss of weldability.

### Decision

**KEEP NETHERSTEEL.**

It provides a strong Nether-derived industrial payoff using existing Netherite Scrap rather than another ore.

It also gives superheating an important retained purpose.

# Superheating

Superheating now has multiple independent uses:

- Create high-output boiler behavior;
- existing Create superheated recipes;
- improved Diesel Generators crude-oil distillation efficiency;
- CBC Nethersteel manufacture.

Therefore its retention no longer depends on Create: Metallurgy.

### Decision

**KEEP SUPERHEATING.**

Do not add additional superheated recipes merely to make the tier feel important.

Its existing cross-system uses are already sufficient to justify the capability.

# Wolframite / Tungsten / Obdurium

## Current source-backed usage

Create: Metallurgy provides:

~~~text
Wolframite
    -> Tungsten

Andesite Alloy
+ molten Tungsten
+ SUPERHEATED
    -> Obdurium

Refractory Mortar
+ Obdurium Plate
+ 30 mB molten Tungsten
    -> Industrial Crucible
~~~

Tungsten additionally supports:

- Tungsten forms;
- Tungsten wire;
- Metallurgy light bulbs;
- generic casting/melting.

Obdurium largely supports:

- its own forms;
- Industrial Crucible construction.

The present cross-mod consumer evidence is weak.

## Decision

~~~text
WOLFRAMITE WORLDGEN
    REJECT

WOLFRAMITE DISTRICT
    REMOVE FROM CURRENT NETHER PLAN

TUNGSTEN PRIMARY PROGRESSION
    REJECT

OBDURIUM PRIMARY PROGRESSION
    REJECT
~~~

This reverses the previous working assumption that Wolframite should become a strategic Nether resource.

A later mod can reopen the decision only if it gives Tungsten/Obdurium a real mechanical role.

# Create: Metallurgy itself

The rejection of Tungsten does **not** automatically reject Create: Metallurgy.

Its core foundry machinery has potentially distinct value:

- molten-metal handling;
- general casting into molds;
- casting basins;
- alloying;
- industrial crucible bulk melting;
- multi-fluid crucible storage;
- heat-scaled foundry throughput;
- ladles, gauges, foundry logistics.

These are process capabilities rather than ore tiers.

## Problem

The current Industrial Crucible recipe is self-gated by Tungsten/Obdurium.

If Skyforge keeps the foundry system while rejecting those materials, the pack needs a small integration change.

Candidate replacement gate:

~~~text
refractory construction
+ STEEL
+ possibly NETHERSTEEL for the highest-temperature capital component
    -> Industrial Crucible
~~~

Exact recipe is not locked.

## CBC interoperability issue

CBC cannon casting consumes common fluid tags such as:

~~~text
c:molten_cast_iron
c:molten_bronze
c:molten_steel
c:molten_nethersteel
~~~

Create: Metallurgy currently exposes its own molten-metal fluids but the audited source does not provide matching common molten-metal tags for those fluids.

Therefore a retained Metallurgy stack needs a **small data-level fluid-tag bridge** so that, for example:

~~~text
Metallurgy molten Steel
    -> CBC cannon cast
~~~

works directly.

This is the kind of bespoke/config work that may be justified because it removes duplicate industrial silos.

## A/B decision

### Variant A — CBC-only metal industry

Keep:

- CBC Basin Foundry Lid;
- CBC molten metals;
- CBC cannon casting;
- CBC cast/drill/built-up cannon manufacturing.

Advantages:

- very low integration burden;
- no Wolframite/Tungsten/Obdurium clutter;
- CBC industry already deep;
- cannon material chain is coherent.

### Variant B — CBC + stripped Metallurgy foundry

Keep Metallurgy machinery but:

- disable Wolframite worldgen;
- remove Wolframite/Tungsten/Obdurium from progression;
- replace Industrial Crucible gate;
- bridge molten-metal tags;
- hide/disable obsolete recipe surfaces where practical;
- ensure CBC and Metallurgy Steel do not create confusing duplicate material identities.

Advantages:

- larger foundry;
- bulk metal processing;
- more satisfying heavy-industry plant;
- reusable metal casting beyond cannon-specific parts.

Cost:

- more integration/configuration;
- duplicate fluid/material namespace risk;
- more recipe-viewer clutter;
- additional player learning.

### Current decision

**A/B test rather than assume Variant B is better.**

If the Metallurgy foundry is not more fun or operationally useful during realistic CBC production, remove the mod.

# Process-material rule

Materials such as:

- Graphite molds;
- Refractory Mortar;
- Coke;

are not treated like geological resources.

They may be retained if they function as legible industrial tooling/consumables.

Current simple recipes are favorable:

~~~text
Graphite
    Clay + Coal

Refractory Mortar
    Sand + Clay + Water
~~~

These do not require another ore geography.

However they still must justify their recipe/cognitive load through the foundry A/B.

# Revised Nether industrial identity

Previous working thesis:

~~~text
Nether
    -> Wolframite
    -> Tungsten
    -> Obdurium
    -> Industrial Crucible
~~~

Current stronger thesis:

~~~text
Nether
    -> Blaze / superheat
    -> Quartz / control
    -> Netherite Scrap
    -> Nethersteel
    -> high-performance artillery / heavy engineering
~~~

This is cleaner because the Nether reward uses retained vanilla/CBC materials and produces a visibly different machine capability.

# Acceptance tests

## MAT-1 — no orphan geology

No Skyforge-authored ore exists solely because one recipe references its tag.

## MAT-2 — Silver exclusion

The integrated electrical stack works without a Silver deposit or Silver-specific regional economy.

## MAT-3 — Electrum justification

If Electrum remains, its high-current role is used enough to justify the material.

## MAT-4 — CBC Steel payoff

Steel materially improves viable cannon/autocannon designs over Cast Iron/Bronze.

## MAT-5 — Nethersteel payoff

Nethersteel produces real design tradeoffs and is worth its Netherite/superheat cost.

## MAT-6 — superheat independence

Superheating remains useful even if Create: Metallurgy is removed.

## MAT-7 — no Tungsten vanity tier

Wolframite/Tungsten/Obdurium remain excluded unless a concrete external consumer or unique engineering property appears.

## MAT-8 — Metallurgy A/B

Retain Create: Metallurgy only if its foundry changes industrial gameplay enough to justify its machinery and integration work.

## MAT-9 — unified Steel identity

CBC, Create: Metallurgy, and any other retained mod share one coherent Steel economy via common tags/recipes.

## MAT-10 — minimal bespoke surface

Prefer datapack/tag/recipe integration to code patches.

# Immediate changes to prior assumptions

Remove from current design assumptions:

- Wolframite as a required Nether strategic district;
- Tungsten/Obdurium as guaranteed late metallurgy;
- Silver as a likely electrical geology requirement.

Add:

- CBC Cast Iron/Bronze/Steel/Nethersteel as the leading heavy-industry material ladder;
- Nethersteel as a principal Nether superheat payoff;
- Create: Metallurgy as a **foundry-mechanics A/B**, not a material-tree commitment.

# Acceptance principle

> Every retained material should make the player design, route, process, or operate something differently enough that they would notice its absence.
