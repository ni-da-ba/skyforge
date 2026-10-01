# Skyforge Recipe and Material Normalization Backlog v0.1

**Snapshot:** 2026-09-05  
**Status:** Working implementation backlog derived from the unified industrial production graph.

# Purpose

This document translates the player-facing production graph into concrete pack-integration work.

Change classes:

~~~text
R0 — REQUIRED CLOSURE
    retained capability is otherwise unavailable/broken

R1 — REQUIRED NORMALIZATION
    duplicate/rejected material would leak into progression

R2 — CONDITIONAL BALANCE
    stock recipe works but may undermine intended progression

R3 — CONDITIONAL MOD A/B
    only needed if the relevant optional mod survives testing

R4 — PRESENTATION CLEANUP
    recipe viewer / naming / quest guidance
~~~

Default implementation preference:

~~~text
TAG
-> DATAPACK RECIPE
-> CONFIG
-> WORLDGEN OVERRIDE
-> RESOURCE/PRESENTATION OVERRIDE
-> THIN ADAPTER
-> JAVA/MIXIN only if unavoidable
~~~

# Highest-priority normalization work

## R0-01 — Gunpowder source closure

**Why:** CBC Wrought-Iron artillery and ammunition depend on Gunpowder.

Risk:

Skyforge hostile-spawn governance could accidentally make ordinary Creeper acquisition unreliable.

Required proof:

- engineered Creeper farm path;
- trade/loot fallback where appropriate;
- progression timing suitable for CBC's earliest artillery.

Preferred fix:

**world/spawn/economy closure before recipe change.**

Recipe override only if no healthy renewable source survives.

## R0-02 — Blaze/HEATED closure

**Why:** Brass, CBC Cast Iron/Bronze/Steel, and multiple advanced processes depend on Create HEATED processing.

Required proof:

- Blaze acquisition;
- Blaze Burner creation;
- safe enough route to establish post-Nether industry.

No recipe override currently planned.

## R0-03 — Blaze Cake / SUPERHEAT closure

Required inputs:

- Egg;
- Sugar;
- Cinder Flour;
- Lava;
- Blaze Burner.

Risk:

semantic ecology could make Eggs unavailable or unnecessarily rare.

Preferred action:

1. guarantee an ordinary egg-producing ecology/trade route;
2. guarantee Sugar source;
3. preserve Netherrack/Cinder Flour;
4. preserve Lava;
5. only re-recipe Blaze Cake if these world closures are unhealthy.

## R0-04 — Netherite Scrap closure at CBC scale

**Why:** Nethersteel consumes Netherite Scrap repeatedly for large artillery.

Required action:

- measure representative Nethersteel cannon demand;
- author Ancient Debris/Netherite availability accordingly if Nether geology becomes Skyforge-owned.

Do not tune purely around vanilla armor consumption.

## R0-05 — End Stone / Levitite closure

Required:

- End Stone extraction;
- Zinc import;
- Water logistics;
- HEATED mixing;
- crystallization.

No current recipe change planned.

# Required rejected-material normalization

## R1-01 — Silver exclusion

Decision:

~~~text
SILVER WORLDGEN
    NONE

SILVER REQUIRED PROGRESSION
    NONE
~~~

CC&A actions:

- ensure baseline required electrical recipes can be completed without Silver;
- normalize Modular Accumulator or equivalent hard Electrum dependency onto the mod's usable-wire abstraction or another retained conductor route;
- preserve Gold as baseline advanced conductor.

## R1-02 — Platinum exclusion

If Create Propulsion: Simulated is selected:

- disable Platinum ore/deepslate ore biome/worldgen injection;
- remove Platinum as required progression;
- rebase retained Platinum-gated machines onto retained materials.

Likely targets:

- cables/relays;
- Redstone Converter;
- Coral Generator if retained;
- Vector Thruster;
- high-capacity fluid storage.

Recipe semantics should map to:

- conductor -> Copper/Gold/optional Electrum;
- structure -> Steel/Nethersteel/Sturdy Sheet;
- precision control -> Brass;
- advanced sensing -> existing Sable/Simulated components.

## R1-03 — Wolframite/Tungsten/Obdurium exclusion

If Create: Metallurgy is installed for A/B:

- disable Wolframite worldgen;
- remove Tungsten/Obdurium from required progression;
- prevent their recipe chain from becoming an accidental quest/progression requirement;
- hide/ignore obsolete surfaces where practical.

No Skyforge-authored Wolframite deposit.

## R1-04 — Tin non-requirement

CBC Bronze uses the tinless route.

Do not add Tin geography.

If another retained mod exposes Tin-only recipes:

- audit the capability independently;
- rebase or exclude unless Tin earns a separate role.

# Shared-material normalization

## R1-05 — canonical Steel identity

CBC Steel is currently the leading player-facing Steel.

If Metallurgy or another mod remains:

- ensure common Steel tags accept all intended forms;
- choose canonical production route(s);
- remove exploitative conversion loops;
- make all consumers accept the shared Steel identity;
- normalize plates/wires/rods only where real consumers exist.

Player should never need to ask:

> Which mod's Steel is this recipe asking for?

## R1-06 — canonical Bronze identity

CBC Bronze is leading.

If Metallurgy remains:

- bridge Bronze item/fluid tags where identities match;
- prevent duplicate-yield loops;
- prefer one clear player-facing Bronze production path.

## R1-07 — common molten-fluid bridge — conditional on Metallurgy

If Metallurgy wins A/B:

Bridge appropriate Metallurgy molten fluids into CBC/common tags:

~~~text
c:molten_steel
c:molten_bronze
possibly c:molten_cast_iron if a coherent producer exists
~~~

Target experience:

~~~text
Metallurgy bulk foundry
    -> pipe molten retained metal
    -> CBC cannon cast
~~~

No bucket-conversion silo.

## R1-08 — unified Brass/Zinc/Copper tags

Audit all retained Create addons for:

- old Forge-tag paths;
- modern common tags;
- direct-item hardcoding.

Normalize only where a direct item breaks otherwise valid cross-mod interchangeability.

# Conditional balance changes

## R2-01 — CBC Steel progression

Stock:

~~~text
2 Iron
+ Coal
+ HEATED mixing
    -> 2 Steel
~~~

Question:

Does Steel immediately invalidate Cast Iron/Bronze once Blaze heat exists?

Test:

- representative cast-iron cannon;
- Bronze cannon/autocannon;
- Steel cannon/autocannon;
- metal cost;
- machining time;
- weight/recoil;
- repair/welding;
- performance.

Outcomes:

### Keep stock

if tradeoffs preserve all materials.

### Escalate process

if Steel is too easy.

Possible retained-resource escalation:

- superheat;
- more carbon/process cost;
- foundry/coke route if Metallurgy survives.

Do not choose before manual testing.

## R2-02 — CBC casting cadence

Current stock casting times are significant.

Tune only if:

- passive wait dominates play;
- batching does not make the wait productive;
- repeated production remains tedious after infrastructure exists.

Prefer data tuning over code.

## R2-03 — petroleum yield / engine fuel economy

Measure:

- crude extraction rate;
- heated vs superheated refining;
- aircraft/heavy-engine burn;
- depot scale.

Tune so petroleum creates strategic fluid logistics without becoming either infinite trivia or constant chores.

## R2-04 — Electrum survival

Test Gold-only representative networks.

If Gold transfer ceiling creates a meaningful high-current bottleneck:

- retain Electrum as manufactured upgrade;
- define a Silver-free production route.

If not:

- remove Electrum from required progression;
- do not preserve alloy complexity for its own sake.

# Conditional Create: Metallurgy A/B work

## R3-01 — Industrial Crucible re-recipe

Only if Metallurgy foundry wins.

Current Tungsten/Obdurium gate must be removed.

Candidate semantic recipe:

~~~text
REFRACTORY MATERIAL
+ STEEL
+ advanced heat-resistant component
    -> INDUSTRIAL CRUCIBLE
~~~

Nethersteel may participate only if doing so creates useful progression rather than circularly gating basic foundry access behind extreme artillery materials.

## R3-02 — Graphite / refractory / coke retention

Retain only if foundry play benefits from:

- mold preparation;
- furnace/refractory tooling;
- separate carbon processing.

These are process materials, not worldgen resources.

## R3-03 — generic casting overlap

A/B:

- CBC Basin Foundry Lid only;
- CBC + Metallurgy generic foundry.

Keep Metallurgy only if the second factory:

- improves bulk production;
- creates satisfying plant layout;
- reduces repeated busywork;
- enables useful non-cannon metal processing.

# Conditional Propulsion integration

## R3-04 — Vector Thruster rebase

Only if Propulsion is retained.

Recipe should communicate:

~~~text
ADVANCED THRUST
+ HIGH-LOAD STRUCTURE
+ PRECISION VECTOR CONTROL
+ SENSOR
~~~

Candidate vocabulary:

- Ion Thruster;
- Steel/Nethersteel/Sturdy Sheet;
- Brass;
- gimbal sensor;
- advanced electrical/control part.

No Platinum requirement.

## R3-05 — high-capacity tank rebase

Treat capability as **reinforced compact fluid storage**, not Platinum geology.

Candidate concept:

~~~text
ordinary fluid tank
+ retained structural reinforcement
    -> high-capacity tank
~~~

## R3-06 — cable/control rebases

Use:

- Copper;
- Gold;
- optional Electrum;
- Brass;

according to actual electrical/control role.

# Recipe-viewer / player-facing cleanup

## R4-01 — canonical recipe guidance

JEI/EMI/quest layer should emphasize the intended route when multiple compatibility recipes exist.

Examples:

- Bronze: Copper + Zinc + Cinder Flour;
- Steel: canonical selected route;
- Nethersteel: retained CBC route;
- Levitite: End Stone Powder + Zinc + Water;
- baseline electrical storage: Silver-free route.

## R4-02 — rejected-material suppression

Where possible, hide or de-emphasize:

- Silver-only dead recipes;
- Platinum ore/raw forms;
- Wolframite/Tungsten/Obdurium progression surfaces;
- Tin-only compatibility routes.

Do not spend bespoke code solely to hide an inert creative-tab item unless it materially harms player comprehension.

## R4-03 — one material naming language

If duplicate ingots/fluids remain internally, use tags and guidance so the player-facing production system still reads as one Steel/Bronze/etc. economy.

# Verification harness concept

The final pack should eventually have a machine-readable closure table:

~~~text
CAPABILITY
    -> acceptable recipes
    -> required tags/items/fluids
    -> process gate
    -> source class
~~~

Examples:

~~~text
CBC_STEEL
    -> iron + coal + HEATED
    -> output shared Steel

SUPERHEAT_FUEL
    -> egg + sugar + cinder_flour + lava
    -> Blaze Cake

LEVITITE
    -> end_stone + zinc + water + HEATED
    -> crystallization

BASELINE_ELECTRICAL_STORAGE
    -> retained conductor
    -> NO SILVER requirement
~~~

This can later power automated pack acceptance so recipe changes do not silently reintroduce rejected materials.

# Work ordering

## Wave 1 — paper/data closure

1. lock unified production graph;
2. enumerate exact recipe collisions;
3. identify config/datapack override points;
4. keep optional-mod work conditional.

## Wave 2 — integrated pack prototype

1. load selected core engineering mods;
2. inspect JEI/EMI;
3. run closure recipes;
4. record accidental dead ends / duplicate loops.

## Wave 3 — balance A/B

1. CBC material ladder;
2. Metallurgy foundry;
3. Gold vs Electrum networks;
4. petroleum throughput;
5. Propulsion recipe rebases.

## Wave 4 — automation

Build a repeatable verifier for:

- rejected-material absence from required paths;
- first-flight independence;
- CBC material closure;
- superheat closure;
- Levitite closure;
- petroleum/electrical closure.

# Current override forecast

### Required now in principle

- Silver-free baseline electrical storage;
- Platinum worldgen disable/rebase **if Propulsion selected**;
- Wolframite disable/material-tree pruning **if Metallurgy selected**;
- common-material normalization where duplicate mods coexist.

### Likely but unproven

- CBC Steel progression adjustment.

### Explicitly not yet justified

- broad CBC recipe rewrite;
- custom Steel ore;
- custom Silver/Tin/Platinum/Tungsten geology;
- Java compatibility layer;
- bespoke new foundry machinery.

# Acceptance principle

> Recipe tweaking is successful when the player stops seeing mod boundaries, not when every upstream recipe has been replaced.
