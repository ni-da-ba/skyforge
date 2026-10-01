# Onboarding, Guidance, and Quest Layer v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.

## Core principle

> The world must be progression-complete without quests; quests should make complex systems legible, not make the sandbox function.

The bootstrap-region recipe is the progression guarantee.

The guidance layer explains:

- what systems exist;
- what a sensible next experiment is;
- how advanced infrastructure relates to early mechanics;
- which player achievements indicate understanding.

## Preferred tooling strategy

### Leading prototype: FTB Quests

FTB Quests currently has native Minecraft 1.21.1 NeoForge support and is the leading candidate for a conventional modpack-style progression book.

Advantages:

- mature modpack quest UX;
- familiar to modded Minecraft players;
- avoids bespoke Skyforge quest UI/code;
- supports explicit chapters and progression presentation;
- can coexist with ordinary Minecraft gameplay.

Costs:

- additional dependency chain;
- content-authoring workload;
- risk of turning a sandbox into checklist progression if overused.

### Lightweight fallback: vanilla advancements

Use where:

- a full quest book is unnecessary;
- milestones can be communicated through normal advancements;
- a lower-dependency profile is desired.

Skyforge should preserve enough clean milestone semantics that either presentation layer can be used.

## Quest layer is not world authority

The quest book should not decide:

- where resources generate;
- whether the player is allowed to craft an item;
- whether a structure exists;
- whether progression is physically possible.

Those belong to:

- bootstrap recipe;
- resource geography;
- recipes;
- world semantics.

The guidance layer observes or explains that reality.

## Guidance modes

Candidate pack/user presets:

~~~text
FULL_GUIDANCE
  modpack-style quest chapters and explanations

LIGHT_GUIDANCE
  advancements + concise optional references

MINIMAL
  ordinary advancements / world-only teaching
~~~

The core world should remain playable under all three.

## Quest philosophy

Prefer quests that answer:

> What should I understand or try next?

Avoid quests that merely ask:

> Did you craft every item in this mod?

The quest book should emphasize systems and causal relationships.

## Core chapter structure

### Chapter 0 — Read the Sky

Purpose:

- orient the player to floating-island survival;
- point out edges, underside risk, nearby islands, weather, and horizon landmarks;
- establish that the world is spatially meaningful.

Potential milestones:

- establish shelter;
- obtain basic food/water;
- inspect another island;
- recognize a visible route/structure cue.

### Chapter 1 — Mechanical Foundations

Purpose:

- introduce Create as the basic engineering vocabulary;
- explain rotation, transmission, processing, and mechanical assembly.

Potential milestones:

- make first rotational power source;
- process a material mechanically;
- use shafts/gears/belts;
- create a basic workshop.

Keep this about concepts, not exhaustive Create completion.

### Chapter 2 — Crossing the Gap

Purpose:

- introduce deliberate inter-island mobility before full aviation.

Potential milestones:

- reach another island;
- use a bridge/glider/rope/primitive traversal method;
- recover or obtain a resource not present on the spawn island.

This teaches resource differentiation.

### Chapter 3 — First Reliable Flight

Purpose:

- guide the player through the minimum Aeronautics/Sable craft path.

Potential milestones:

- understand lift/buoyancy/propulsion primitives;
- build a controllable craft;
- perform a short flight;
- land safely;
- carry modest cargo.

This chapter should be strongly tested against the bootstrap recipe closure.

### Chapter 4 — Weather and Air

Purpose:

- make wind/weather mechanically legible.

Potential milestones:

- observe wind direction;
- use a windsock/instrument;
- experience headwind/crosswind/updraft;
- visit or use a weather station;
- add onboard instrumentation.

Avoid requiring atmospheric theory before the player can fly.

### Chapter 5 — Navigation

Purpose:

- move from visual navigation to instrumentation.

Potential milestones:

- use a map/compass;
- identify a route beacon;
- receive/use CC GPS or navigation data;
- use radar/contact information when available.

### Chapter 6 — Resource Geography

Purpose:

- explain that possession and industrial supply are different.

Potential milestones:

- find a specialized mineral/resource region;
- establish a remote mine/resource site;
- move bulk material by vehicle;
- refine strategic fuel if selected.

### Chapter 7 — Industry and Power

Purpose:

- introduce deeper processing, electricity, diesel, metallurgy, and storage only after the player has mobility.

Potential milestones:

- establish industrial power;
- refine fuel;
- process ore at scale;
- create electrical distribution;
- integrate CC/instrument systems.

Only chapters corresponding to selected dependencies should exist.

### Chapter 8 — Build a Network

Purpose:

- synthesize the game.

Potential milestones:

- maintain multiple functional sites;
- create a cargo route;
- build navigation infrastructure;
- build/operate a dedicated airfield or dock;
- automate part of a logistics chain.

The final lesson:

> Mature Skyforge progression is a network, not one crafting table with more recipes.

## Optional exploration chapters

Separate from the core learning path:

- ecology;
- dungeons;
- Ancient Cities;
- illager territory;
- bosses/legendary encounters;
- rare structures;
- ocean/marine systems;
- Nether/End progression.

These should not clutter the engineering spine.

## World and quest correspondence

Generated civilization can act as the physical example for a quest concept.

Examples:

~~~text
quest: understand navigation
world example: route station / beacon

quest: understand bulk extraction
world example: mining settlement

quest: understand mature logistics
world example: regional civilian hub

quest: understand salvage
world example: abandoned industrial network
~~~

The quest text should point the player toward observations, not provide a full solution diagram when the world already shows it.

## Avoid dynamic-coordinate quest complexity initially

A procedurally generated world makes quests like:

> Go to the Route Station at X=1234 Z=-567

expensive and brittle.

Prefer:

- visit any structure/role of a class;
- obtain/use a map found in-world;
- detect an advancement/milestone;
- build/use a system;
- find a relevant item/resource.

If dynamic world-specific quest targets later prove valuable, implement a thin adapter only then.

## Skyforge milestone seam

A small bespoke integration seam is likely justified.

Skyforge can expose stable milestone events/advancements such as:

~~~text
BOOTSTRAP_SURVIVAL_COMPLETE
FIRST_INTER_ISLAND_CROSSING
BASIC_CREATE_ESTABLISHED
FIRST_RELIABLE_FLIGHT
FIRST_SAFE_LANDING
FIRST_CARGO_FLIGHT
FIRST_ROUTE_BEACON_USED
FIRST_WEATHER_INSTRUMENT_USED
FIRST_REGIONAL_RESOURCE_SITE
FIRST_PLAYER_AIRFIELD
FIRST_AUTOMATED_CARGO_ROUTE
~~~

These semantics can feed:

- vanilla advancements;
- FTB Quests;
- telemetry/testing.

This is much smaller than a custom quest engine.

## Rewards

Quest rewards should be restrained.

Good rewards:

- food;
- XP;
- maps;
- a few common components;
- cosmetic/building items;
- one small convenience component;
- repair supplies.

Potentially useful at transition points:

- one example sensor;
- one navigation component;
- one small batch of otherwise tedious low-tier parts.

Avoid:

- unique required progression items;
- complete aircraft;
- large quantities of strategic fuel;
- endgame machinery;
- rewards that undermine the resource geography the quest is explaining.

## Quests should be optional guidance, not recipe gates

Do not require quest completion to:

- unlock ordinary crafting;
- enable flight;
- make ores generate;
- permit access to dimensions.

Recipe locks/stages can be considered later only if playtesting proves the natural progression path impossible to communicate otherwise.

Default: information and reward, not permission.

## Quest volume budget

Avoid a giant encyclopedia of checkbox tasks.

Initial target should be a compact critical path plus optional branches.

A useful first content budget is conceptually:

~~~text
8-10 core chapters
roughly 3-8 meaningful milestones per chapter
optional side chapters as needed
~~~

The exact count is not a requirement; the point is to keep authorship tractable.

Prefer one quest:

> Build and fly a controllable aircraft.

over fifteen quests for every intermediate crafting component unless those components teach genuinely distinct concepts.

## Documentation links

Complex mods already have their own documentation.

Use the quest layer to explain:

- Skyforge-specific relationships;
- why a mechanic matters in this world;
- a sensible next goal.

Do not rewrite complete Create, CC:Tweaked, or Aeronautics manuals inside the quest book.

Link/reference existing documentation when possible.

## Starting-region integration

The bootstrap recipe and guidance layer should be designed together.

The starting region can guarantee examples that make early quest steps meaningful:

- another reachable island;
- resource differentiation;
- optional simple settlement/ruin;
- enough materials for first flight;
- visible post-flight destination.

But the quest system must not be required to make those guarantees true.

## Failure and recovery guidance

Quests can reduce frustration by explaining recovery paths.

Examples:

- lost first aircraft;
- missing fuel;
- stranded on another island;
- need a replacement component.

The actual world must still contain a recovery path.

The quest book merely explains it.

## Multiplayer

FTB-style team progression may be useful in multiplayer, but world progression should not require every player to maintain a private duplicate quest state.

Preferred initial policy:

- quest completion can be personal/team UI state;
- world infrastructure and resource state remain shared;
- no per-player world-generation branches.

## Dependency decision

Current recommendation:

### Strong prototype / likely pack guidance dependency

FTB Quests for the guided default experience.

### Required dependencies if selected

Account for its current required dependency chain in the modpack maintenance budget.

### Fallback

Vanilla advancements plus in-world teaching if the dependency burden or UX proves undesirable.

Do not write a custom quest engine.

## To-build package

Likely bespoke work:

~~~text
Skyforge Milestone / Advancement Semantics
Optional FTB Quest Completion Adapter
Bootstrap Recipe Evidence Hooks
Structure/Role Visit Detection only if truly needed
~~~

Not justified:

~~~text
custom quest UI
custom dialogue engine
custom reputation system
custom quest scripting language
custom progression-stage engine
~~~

## Acceptance tests

### Quest-off test

Disable/ignore the quest system.

PASS only if the world remains progression-complete and understandable through ordinary play plus existing mod docs.

### No-gate test

A knowledgeable player can progress without completing checklist tasks.

### Bootstrap correspondence

Every mandatory early concept has a guaranteed world/material path in the bootstrap recipe.

### Reward integrity

Quest rewards do not bypass the resource/progression geography being taught.

### Authoring-cost test

The quest layer remains compact enough to maintain when mods update.

## Acceptance principle

> Skyforge should use quests to teach a complicated sandbox, not turn the sandbox into a quest tree.
