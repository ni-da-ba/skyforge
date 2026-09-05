# Civilization Modification, Looting, and Civic Assets v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.

## Core constraint

Skyforge civilization exists inside Minecraft.

Players can normally:

- break buildings;
- take crops;
- remove workstations;
- move villagers;
- dismantle machinery;
- empty containers;
- reshape terrain.

The civilization design must treat this as a baseline affordance rather than assume generated settlements are untouchable scenery.

## Preferred policy

> Design active civilization so that unrestricted Minecraft-style modification is acceptable by default; reserve selective protection for specific progression-sensitive assets only if playtesting proves it necessary.

Do not begin with broad settlement protection.

## Why chunk claims are not the default answer

Current 1.21.1 NeoForge claim mods such as FTB Chunks and Open Parties and Claims provide mature player/server chunk protection.

They are useful multiplayer/server options.

They are poorly matched to generated Skyforge civilization because a chunk claim:

- protects unrelated terrain;
- can cover several unrelated structures;
- can collide with vertically stacked islands sharing X/Z;
- makes ordinary settlement remodeling feel arbitrarily forbidden;
- introduces a large permission system to solve a narrower content-economy problem.

Skyforge-generated civilization should therefore not depend on chunk claims.

## Asset classes

Every civilization block/content role should conceptually belong to one of four classes.

### A. Ordinary world fabric

Examples:

- stone;
- wood;
- glass;
- roads;
- decorative blocks;
- fences;
- common workstations;
- ordinary crops;
- common containers.

Policy:

~~~text
breakable = yes
normal drops = yes
player may reshape freely
~~~

If a player wants to dismantle a village for building materials, that is Minecraft.

Do not fight it.

### B. Ordinary functional infrastructure

Examples:

- common Create shafts/gears/belts;
- ordinary storage;
- simple farm machinery;
- common docking structures;
- routine workshop blocks.

Policy:

~~~text
breakable = yes
normally recoverable = yes
worldgen quantity = deliberately bounded
removal may physically disable local service
~~~

The player may dismantle these systems.

The cost is that the settlement's demonstrated/usable infrastructure no longer exists.

No hidden service state should magically remain functional after its blocks are removed.

### C. Progression-sensitive civic assets

Examples depend on final mod stack:

- unusually expensive propulsion blocks;
- advanced radar/computing hardware;
- progression-gating power components;
- rare modded machinery;
- blocks whose mass placement would trivialize a major progression step.

Default policy:

1. avoid mass placement in active generated civilization;
2. demonstrate the concept with common components where possible;
3. use only a small intentional quantity when advanced hardware is necessary;
4. prefer services/trade/observation over large free stockpiles;
5. treat any recovered component as intentional component acceleration.

Only if this remains exploitable should Skyforge add selective civic-asset provenance/protection.

### D. Salvage assets

Abandoned and hostile sites deliberately expose recoverable valuable infrastructure.

Policy:

~~~text
breakable = yes
recoverable = yes
loot/salvage budget = intentional
risk/state balances value
~~~

This is the main direct reverse-engineering layer.

## Active civilization must be hoover-safe

A player should be able to strip an active settlement without obtaining an accidentally game-breaking quantity of rare materials.

Achieve this primarily through content budgeting.

### Building palette

Use abundant/common materials for most visual mass.

Do not decorate a warehouse with hundreds of progression-significant blocks merely because they look industrial.

### Machinery

A mature-looking settlement need not contain enormous quantities of endgame machinery.

Use:

- efficient layouts;
- shared infrastructure;
- modest numbers of expensive components;
- common mechanical transmission;
- partial/static evidence where a fully functional advanced assembly would require excessive high-tier material.

Generated machinery should remain mechanically plausible.

### Containers

Active civilian containers should not contain large free stocks of valuable components.

Prefer:

- ordinary supplies;
- small functional inventories;
- villager trade;
- services.

High-value free salvage belongs mainly in abandoned/hostile sites.

### Fuel

Avoid generating huge filled fuel reservoirs that can trivially supply the player for the rest of progression.

Active fuel infrastructure can be:

- modestly stocked;
- supplied through trade;
- partly represented by tanks/pipes whose inventory is intentionally bounded.

### Villagers, crops, livestock

These remain ordinary Minecraft resources.

The player may:

- move villagers;
- harvest farms;
- breed livestock;
- repurpose workstations.

Do not introduce artificial restrictions simply because the settlement was generated.

## Dismantling should have physical consequences

If a player removes infrastructure, the settlement should degrade through ordinary block-level causality wherever possible.

Examples:

~~~text
remove beacon transmitter
-> beacon no longer transmits

remove weather computer
-> station loses quantitative display

remove fuel machinery
-> fuel service no longer physically exists

dismantle mill
-> mill no longer processes

strip hangar workshop
-> workshop is gone
~~~

Avoid an abstract "settlement service remains available because semantic state says so."

Generated civilization should be materially real.

## Services reduce the need for loot protection

Active settlements can remain valuable without being rich salvage targets because their primary value is:

- trade;
- safe staging;
- navigation;
- fuel access;
- weather information;
- working infrastructure;
- maps;
- local resource access.

Destroying the settlement trades long-term service value for one-time material recovery.

That choice is sufficiently Minecraft-like that it should be allowed unless balance testing demonstrates otherwise.

## Progression-sensitive block audit

Before finalizing civilization structures, every installed dependency used in generated infrastructure should undergo a simple block/item audit.

Candidate data categories:

~~~text
WORLDGEN_COMMON
WORLDGEN_LIMITED
ACTIVE_SITE_AVOID
ABANDONED_SALVAGE
HOSTILE_REWARD
~~~

Inputs:

- recipe/progression tier;
- material cost;
- portability;
- quantity needed by generated structures;
- whether possession bypasses intended progression;
- whether the block is visually/technically necessary.

This is primarily a content/data problem, not an AI/protection problem.

## Example: airfield

An active airfield may contain:

- runway/landing geometry;
- hangar;
- common mechanical systems;
- storage;
- windsock;
- beacon;
- modest fuel supply;
- perhaps a small number of advanced navigation components.

It should not contain:

- several complete endgame aircraft as block loot;
- enormous advanced engine stock;
- enough rare fuel to erase logistics progression.

If the player strips it, they gain some useful components and destroy a valuable staging/navigation site.

That is acceptable.

## Example: industrial hub

Use the visible mass of:

- buildings;
- shafts;
- belts;
- pipes;
- warehouses;
- stockpiles;
- common processing blocks.

Budget rare central components.

A mature industrial skyline does not require every visible block to be expensive.

## Example: abandoned industrial hub

Here the balance intentionally changes.

More machinery may be:

- broken;
- incomplete;
- directly salvageable;
- guarded by environmental/hostile risk;
- spread across a large site.

This is a legitimate progression reward.

## Example: illager military-industrial center

Valuable components are recoverable because combat and territorial risk are the intended price.

Still budget them.

Hostile civilization should not automatically become the optimal universal source for every advanced technology.

## Selective civic-asset safeguard — fallback only

If playtesting reveals that one or more specific active-civilization blocks create unacceptable progression exploits, use a thin exact-position safeguard rather than broad settlement protection.

Conceptual model:

~~~text
CivicAssetProvenance {
    settlementId
    worldPosition
    assetClass
    currentState
}
~~~

Only generated progression-sensitive asset positions are marked.

Important properties:

- sparse, not every settlement block;
- exact-volume/position aware, not chunk based;
- unrelated terrain remains fully editable;
- vertically stacked islands remain independent;
- player-placed replacement blocks are not automatically protected;
- abandonment/hostile state can change salvage policy.

Possible policies, in order of preference:

1. limit/rewrite recoverable drop;
2. block specialized pickup/disassembly paths such as wrench extraction;
3. as a last resort, deny destruction of that specific civic asset.

The first two preserve world-shaping more fully than hard block protection.

Do not implement this layer until a real progression-sensitive block set requires it.

## Breakable but non-recoverable caveat

Allowing destruction while suppressing recovery preserves shaping but can feel arbitrary.

Therefore:

- use only for clearly communicated rare civic fixtures if necessary;
- do not apply to ordinary building blocks;
- prefer avoiding progression-sensitive active-site blocks entirely.

This is a fallback, not the desired baseline experience.

## Settlement integrity — no bespoke meter initially

Do not add an abstract settlement-health meter simply to punish dismantling.

Ordinary block loss already creates visible consequences.

If later services need a semantic availability check, derive it from a small set of required physical components where possible.

Example:

~~~text
weather service available
iff
required weather terminal / power / sensor remains present
~~~

Do not make the player manage an invisible "town integrity 63%" system.

## Social consequences — defer

Do not initially add:

- theft reputation;
- villager hostility for block breaking;
- fines;
- ownership permissions;
- police/guard crime AI.

These are substantial RPG/social systems.

They may be considered later only if unrestricted active-settlement stripping proves emotionally or mechanically damaging even after asset budgeting.

## Multiplayer/server protection

Player or server administrators may still want ordinary claims.

Current 1.21.1 options such as:

- FTB Chunks;
- Open Parties and Claims;
- Flan;

can remain external/server choices.

They should not define generated Skyforge civilization semantics.

## Interaction with history state

Asset policy may change with current settlement state.

### ACTIVE / MAINTAINED

- low free salvage;
- services/trade dominate value;
- progression-sensitive block quantity minimized.

### DECLINING

- some free salvage;
- reduced service;
- more damaged infrastructure.

### ABANDONED / RUINED

- salvage intentionally meaningful;
- no service protection rationale;
- direct dismantling expected.

### ILLAGER_OCCUPIED / HOSTILE

- direct salvage expected after risk/combat;
- faction infrastructure remains physically destructible.

This can often be achieved through different structure/processor/loot variants rather than runtime protection.

## Player freedom invariant

The civilization system should preserve the following:

> The player may reshape the terrain and built environment of Skyforge using ordinary Minecraft mechanics.

Any exception must be:

- narrow;
- visible/comprehensible;
- tied to a concrete balance problem;
- limited to specific generated assets rather than regions or chunks.

## Acceptance tests

### Active village dismantling

PASS if the player can dismantle buildings, farms, workstations, roads, and ordinary machinery normally.

### Active industrial hub hoover test

PASS if stripping the entire site yields useful material but does not trivially bypass major intended progression because progression-sensitive assets were sensibly budgeted.

### Service destruction

PASS if physically removing the equipment that provides a service removes that service.

### Abandoned salvage

PASS if abandoned infrastructure provides materially better direct salvage than an equivalent active civilian site.

### Stacked-island protection

If selective civic provenance is ever implemented, PASS only if protecting an asset on one island does not protect unrelated blocks above/below it at the same X/Z.

### Player replacement

If a protected civic asset is removed/replaced through an allowed state transition, player-placed blocks at that location must behave as ordinary player blocks unless deliberately registered otherwise.

## Acceptance principle

> Generated civilization must be designed for a player who is allowed to mine Minecraft. Protection is a surgical fallback for specific progression exploits, not the foundation of settlement design.
