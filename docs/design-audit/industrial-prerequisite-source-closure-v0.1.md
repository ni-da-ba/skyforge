# Industrial Prerequisite Source Closure v0.1

**Snapshot:** 2026-09-05  
**Status:** Working content contract; world/economy closure specified, runtime tuning pending.

## Purpose

Close the non-recipe prerequisites that the retained Skyforge industrial graph assumes will exist in the world.

The Wave C1 prototype can prove that recipes do not require rejected materials, but that is insufficient if the world fails to supply ordinary inputs at useful scale.

This contract therefore covers the five highest-priority source closures from the recipe/material normalization backlog:

```text
R0-01  Gunpowder
R0-02  Blaze / HEATED
R0-03  Blaze Cake / SUPERHEAT
R0-04  Netherite Scrap at CBC scale
R0-05  End Stone / Levitite
```

The governing rule is:

> Preserve healthy world, ecology, encounter, trade, and logistics sources before changing recipes.

Skyforge should not solve a world-authoring failure by gratuitously re-reciping a retained vanilla/Create/CBC progression chain.

---

# 1. Source classes

Every industrial prerequisite should have an explicit source class.

```text
COMMON LOCAL
    ordinary material obtainable in the player's current operating region

REGIONAL
    requires travel to a different island/cluster/province or specialist settlement

DIMENSIONAL
    requires Nether/End access or another dimension-specific route

ENGINEERED RENEWABLE
    becomes renewable through a deliberate player-built farm or processing system

TRADE / CIVIC
    obtainable from civilization infrastructure, exchange, salvage, or service rewards

EXPEDITIONARY
    finite or low-renewal source whose intended cost is repeated exploration/logistics
```

A source may occupy more than one class over progression.

Example:

```text
GUNPOWDER
    early: ambient hostile / loot / trade
    mature: ENGINEERED RENEWABLE
```

This is desirable. Progression can transform acquisition from opportunistic to infrastructural without replacing the material itself.

---

# 2. Gunpowder closure

## Required role

Gunpowder must remain available for:

- ordinary vanilla uses;
- fireworks as fireworks;
- TNT and demolition;
- CBC propellant/ammunition chains;
- any retained industrial or military recipes that consume it.

The planned suppression of Elytra firework propulsion does **not** justify suppressing fireworks or Gunpowder as materials.

## Failure mode

Skyforge's semantic hostile-spawn governance deliberately prevents every dark underside, bridge, cave mouth, and layered shadow from becoming a monster carpet.

If that policy simply reduces Creeper throughput everywhere, CBC can become progression-starved and technical players lose a familiar renewable industrial input.

## Closure contract

Gunpowder should have three layers of access:

### Opportunistic access

Ordinary Creepers remain part of the curated vanilla hostile vocabulary where ambient monster pressure admits them.

This provides low-volume early acquisition without requiring the world to sustain vanilla-density hostile spawning everywhere.

### Supplemental access

Loot, hostile-structure rewards, faction salvage, and selected civic/trade routes may provide modest Gunpowder quantities.

These are resilience paths, not the intended mature bulk source.

They should prevent a player from being hard-blocked because local Creeper ecology is sparse.

### Engineered renewable access

The engineered-spawning domain defined by `threats-and-spawn-governance.md` must permit purpose-built hostile farms to approach useful vanilla-like behavior.

The relevant principle is not "player block = exempt."

Instead, local evidence should increasingly classify a space as engineered when it exhibits properties such as:

```text
high enclosure
low sky access
high local modification density
excavated / constructed spawning volume
purpose-built support geometry
controlled collection / kill geometry
```

An ordinary dark hangar or bridge should not inherit the same privilege.

## Acceptance questions

A later gameplay/runtime pass must answer:

1. Can a player obtain enough early Gunpowder for initial CBC experimentation without building a farm first?
2. Can a deliberate Creeper/general hostile farm sustain repeated artillery use at mature scale?
3. Does the farm path work without restoring monster carpets to ordinary Skyforge terrain?
4. Do supplemental loot/trade sources prevent geographic bad luck from becoming a hard lock?

No Gunpowder recipe override is justified until those questions fail.

---

# 3. Blaze / HEATED closure

## Required role

Create HEATED processing is a systemic gate for the retained engineering graph.

It participates in, among other things:

- Brass progression;
- CBC Cast Iron/Bronze/Steel processing;
- advanced Create-family manufacturing;
- later dimensional materials such as Levitite.

Blaze access is therefore not merely a combat reward. It is an industrial capability unlock.

## Closure contract

Skyforge must preserve a reliable path from Nether access to:

```text
Blaze encounter
    -> Blaze capture / Blaze Burner establishment
    -> stable HEATED processing at the player's industrial base
```

The exact Nether morphology may change, but the world grammar must not make Blazes an accidental rarity.

Preferred source hierarchy:

```text
PRIMARY
    guaranteed / strongly discoverable Blaze-bearing Nether encounter or structure ecology

SECONDARY
    ordinary admitted Nether hostile ecology where appropriate

RESILIENCE
    civilization clueing, map intelligence, quest guidance, or salvage that helps a player locate the primary source
```

The preferred solution is **geographic/encounter guarantees**, not a Blaze Burner recipe rewrite.

## Geographic requirement

If future Nether realization becomes Skyforge-authored, at least one practical Blaze-bearing route must exist within the intended first Nether operational envelope.

That route should be dangerous enough to preserve the Nether's hostile-corridor identity, but not so rare that the player must perform blind province-scale searching before Brass or CBC heat processing becomes possible.

## Acceptance questions

1. Is the first Blaze source discoverable with ordinary guidance?
2. Can a player establish HEATED processing after one credible Nether expedition?
3. Does continued Blaze access remain possible for replacement/expansion?
4. Does the route reinforce Nether gameplay rather than bypassing it?

---

# 4. Blaze Cake / SUPERHEAT closure

## Required role

SUPERHEAT is a later process capability and should feel like an infrastructural upgrade over ordinary HEATED processing.

The retained Blaze Cake chain depends on several otherwise ordinary resources:

```text
Egg
Sugar
Cinder Flour / Netherrack-derived input
Lava
Blaze Burner
```

The risk is not recipe complexity. The risk is Skyforge ecology/geography accidentally making one supposedly ordinary ingredient pathological.

## Egg closure

Egg production must be supported by the ecology/content stack or by ordinary domestication/trade.

Skyforge's niche-first ecology must not interpret "open sky should remain sparse" as "basic domestic animal outputs are unavailable."

At least one low-friction egg-producing path should exist in the ordinary inhabited/settled game:

```text
domestic bird husbandry
or
settlement/civic food production
or
another retained egg-producing ecology
```

The exact species asset is subordinate to the gameplay role.

## Sugar closure

Sugar should remain an ordinary renewable agricultural input.

The authored world must therefore admit a practical sugar-bearing crop/resource niche or a civilization agriculture route.

It should not become a rare exploration commodity merely because island hydrology differs from vanilla continents.

## Cinder Flour / Netherrack closure

The Nether must retain sufficient Netherrack or an equivalent retained Create-compatible source for the Cinder Flour path.

If Nether terrain eventually becomes Skyforge-owned, this is a world-material compatibility requirement.

## Lava closure

Lava must remain available at industrially useful scale through Nether geography, authored geological sites, renewable vanilla mechanics where applicable, or infrastructure-compatible fluid acquisition.

Petroleum is not a substitute for this role unless a future explicit recipe decision says so.

## Acceptance questions

1. Can a player establish renewable Egg and Sugar production before SUPERHEAT becomes desirable?
2. Can the Nether route supply Cinder Flour and Lava without repetitive micro-expeditions?
3. Does SUPERHEAT become a meaningful plant capability rather than a scavenger chore?
4. Is any one ingredient dominating the cost for reasons unrelated to the intended progression gate?

Only a failed world/economy closure justifies changing the Blaze Cake recipe.

---

# 5. Netherite Scrap at CBC scale

## Required role

CBC Nethersteel turns Netherite Scrap from a mostly personal-equipment resource into a potentially high-volume heavy-industry input.

That changes the relevant balance question.

Skyforge must not tune Ancient Debris solely around vanilla armor/tool consumption if retained artillery can consume materially larger quantities.

## Intended source character

Netherite Scrap should remain strategically Nether-bound and expeditionary.

It should **not** become:

- an ordinary Overworld ore;
- a trivial bulk quarry output;
- a manufactured substitute divorced from Nether operations.

The desired mature loop is closer to:

```text
Nether intelligence / prospecting
    -> dangerous extraction node or debris-bearing operation
    -> protected freight route
    -> scrap transport
    -> SUPERHEAT heavy industry
    -> Nethersteel artillery / exceptional structures
```

This supports the Nether's current role as a hostile, high-value industrial operating environment.

## Scaling rule

Ancient Debris / Scrap abundance should be calibrated against representative retained industrial demand.

Before final tuning, measure at least:

```text
one practical Nethersteel cannon build
one larger artillery emplacement or shipboard battery
replacement / repair demand
expected expedition yield
travel + extraction time
freight risk
```

The target is not "make Nethersteel cheap."

The target is:

> A serious Nethersteel project should justify a serious Nether operation without requiring absurd repetitive strip-mining.

## World-authoring implication

If Skyforge later owns Nether geology, Ancient Debris placement should become semantically authored resource geography rather than a blind copy of vanilla distribution.

Candidate roles include:

- rare debris-bearing strata;
- tectonic/thermal provinces;
- dangerous extraction pockets;
- civilization-known deposits;
- salvage from exceptional Nether infrastructure.

These are authoring options, not yet locked algorithms.

## Acceptance questions

1. How much Scrap does a representative CBC Nethersteel project actually consume?
2. How many credible Nether expeditions should that project require?
3. Does the extraction loop reward aircraft/freight/infrastructure where dimension mechanics permit it?
4. Does Nethersteel remain exceptional without becoming practically decorative?

---

# 6. End Stone / Levitite closure

## Required role

Create Aeronautics gives the End an existing technological payoff:

```text
End Stone
    -> End Stone Powder
    -> Zinc + Water + HEATED processing
    -> Levitite Blend
    -> crystallized Levitite
```

Levitite is valuable because it changes aircraft architecture rather than simply replacing an earlier metal tier.

It supports lift/afloat behavior while still requiring another system for climb/propulsion.

## Closure contract

The End must preserve reliable End Stone extraction as a **bulk expedition material**, not merely a decorative block.

The logistics loop should therefore remain meaningful:

```text
End expedition
    -> End Stone extraction
    -> bulk return freight
    -> imported Zinc + Water + established HEATED industry
    -> Levitite processing
    -> new airframe / lift-support capability
```

This is a strong reason not to make Levitite directly craftable from Overworld-only materials.

## Resource geography

End Stone itself can be common while the cost comes from:

- reaching useful extraction sites;
- payload mass/volume;
- sparse safe staging;
- navigation and recovery;
- return logistics.

That is preferable to making the base stone artificially rare.

The End's scarcity language should come from **distance, staging, hazards, and negative space**, not necessarily tiny ore veins.

## Water and Zinc

Water and Zinc are deliberately imported inputs.

This creates a cross-dimensional production chain rather than an isolated End crafting tree.

Skyforge should preserve that asymmetry unless gameplay proves it unhealthy.

## Acceptance questions

1. Is End Stone extraction straightforward once the player reaches a suitable End site?
2. Is moving useful quantities back home a meaningful logistics problem?
3. Does Levitite unlock a distinct aircraft design capability?
4. Does the chain reward existing Zinc/Water/HEATED infrastructure rather than replacing it?
5. Does End progression remain valuable after the first Shulker/Elytra expedition?

---

# 7. Civilization and guidance integration

Source closure does not require the player to infer every resource route blindly.

Civilizations can teach mature infrastructure through observation, loot, service, and geography without becoming vending machines for all industrial inputs.

Useful guidance surfaces include:

```text
Creeper / powder-handling infrastructure
Blaze-related furnace or burner infrastructure
agricultural Egg/Sugar production
Nether freight / debris-processing evidence
End-derived Levitite machinery
```

The goal is to communicate that these are **systems to build**, not arbitrary crafting-table trivia.

Progression quests may point toward source classes and capabilities while preserving discovery.

---

# 8. Runtime / human-eye gate additions

Wave C1 currently focuses on recipe/worldgen/material collisions. The broader engineering acceptance pass should record these source-closure observations alongside it.

Minimum evidence:

```text
GUNPOWDER
    early acquisition path
    engineered renewable path
    estimated sustained throughput

HEATED
    first Blaze route
    replacement / expansion route

SUPERHEAT
    Egg source
    Sugar source
    Cinder Flour source
    Lava source
    sustained Blaze Cake practicality

NETHERSTEEL
    representative project Scrap demand
    expedition yield / burden

LEVITITE
    End Stone bulk acquisition
    return logistics
    processing closure
```

Exact numerical tuning remains a gameplay-test problem.

---

# 9. Decisions this contract intentionally does not make

This document does **not** yet:

- increase Creeper spawn rates globally;
- add a custom Gunpowder recipe;
- rewrite Blaze Burner acquisition;
- rewrite Blaze Cake;
- set Ancient Debris frequency;
- make Netherite renewable;
- change the Levitite recipe;
- prescribe exact Nether or End morphology;
- require bespoke mobs/crops/ores when vanilla/mod content can satisfy the role.

Those interventions require evidence.

---

# 10. Cross-system invariants

The following should remain true:

1. Sparse, semantically governed hostile spawning and useful technical farms can coexist.
2. Core industrial heat must be geographically guaranteed without trivializing the Nether.
3. Ordinary agricultural inputs must remain ordinary even in a sky-island ecology.
4. High-volume Nethersteel demand must be balanced against industrial use, not vanilla armor assumptions alone.
5. End Stone may be common while End logistics remain strategically expensive.
6. World authoring should close progression before recipes are rewritten.
7. No rejected-material geology is reintroduced merely to solve one recipe.

## Acceptance principle

> Industrial progression is closed only when the world can supply the retained recipes at the scale the retained gameplay actually demands.
