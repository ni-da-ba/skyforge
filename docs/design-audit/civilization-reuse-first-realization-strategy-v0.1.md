# Civilization Reuse-First Realization Strategy v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.

## Objective

Minimize bespoke civilization implementation while preserving Skyforge semantic authority.

The default question is:

> Can this civilization state be realized by selecting, composing, processing, populating, or damaging existing content?

Only build a new content system when the answer is no and the missing role matters to gameplay.

## Reuse layers

### Layer 1 — Native mechanics

Prefer vanilla:

- villagers and professions;
- iron golems;
- raids;
- structure loot;
- maps;
- bells and workstations;
- crops/livestock;
- structure tags and normal entity persistence.

### Layer 2 — Existing structure libraries

Leading current candidates:

- Towns & Towers for active civilian village vocabulary;
- Explorify or Structory for small sites, ruins, farmsteads, towers, and historical traces;
- Illager Structures for hostile civilization;
- selected YUNG structures for dungeons/mines/major sites.

Skyforge owns occurrence, role, state, and site.

### Layer 3 — Existing functional blocks

Use:

- Create;
- Create Aeronautics / Sable ecosystem;
- Supplementaries;
- Farmer's Delight / Slice & Dice;
- selected storage, power, radar, and CC:Tweaked content.

These should realize real infrastructure functions before new Skyforge blocks are considered.

### Layer 4 — Data-driven variation

Java structure realization already supports data-driven structure templates, template pools, processor lists, and loot tables.

Use this layer for:

- weighted structure-piece selection;
- block aging/replacement;
- moss/decay/damage;
- abandoned-state transforms;
- loot differences;
- role-specific storage contents;
- variants of small Skyforge-owned layouts.

Where a third-party mod exposes stable structure templates/pools that can safely be referenced, a thin integration datapack may compose them without copying assets.

Where it does not, preserve the mod's native realization and use supported configuration/hooks rather than depending on undocumented internal resource names.

Respect each dependency's license; referencing installed resources is not permission to redistribute them.

### Layer 5 — Small Skyforge layouts

Build only the site types existing content is unlikely to provide coherently:

- airfields;
- cliff docks;
- mooring towers;
- weather stations;
- route beacons;
- radar/navigation posts;
- cargo-transfer sites.

These should be small template/layout libraries built primarily from existing blocks.

### Layer 6 — Bespoke code/content

Last resort.

Requires a demonstrated gameplay/design gap.

## State variants

Do not hand-build a completely separate settlement for every historical state.

Preferred model:

~~~text
base semantic role
    +
current/history state
    +
data/config/processor/population overlays
    =
final realization
~~~

Example route station:

~~~text
ACTIVE
  maintained blocks
  lights
  useful supplies
  civilian population
  working/complete equipment

DECLINING
  partial damage
  reduced supplies
  less activity

ABANDONED
  aging / block-loss processors
  salvage loot
  vegetation
  no civilians
  possible scavengers

ILLAGER_OCCUPIED
  faction banners/details
  military storage
  faction population
  repaired or crude defensive additions
~~~

The exact mechanism may differ by structure source.

## Avoid false generalization

Not every third-party structure can necessarily accept arbitrary Skyforge processors without adapter work.

The reuse strategy should prefer:

1. public/data-driven structure seams;
2. documented configuration;
3. thin explicit compatibility adapter;
4. duplicate/derived assets only when license and maintenance cost are acceptable.

Do not make Skyforge depend broadly on fragile private template IDs across many mods.

## Asset-role indirection

Skyforge semantics should select an abstract role:

~~~text
CIVILIAN_SMALL_FARM
CIVILIAN_VILLAGE_CORE
ABANDONED_ROUTE_STATION
ILLAGER_WATCH_POST
MINING_SURFACE_SITE
~~~

The Minecraft integration layer maps that role to one or more available realizers.

Example:

~~~text
CIVILIAN_VILLAGE_CORE
    -> Towns & Towers compatible pool
    -> vanilla village fallback
~~~

This allows dependencies to be changed without rewriting civilization semantics.

## Fallback hierarchy

Every noncritical civilization role should define a fallback.

Example:

~~~text
preferred Towns & Towers asset
-> vanilla equivalent
-> smaller Skyforge-owned generic layout
-> omit if nonessential
~~~

This prevents optional content mods from becoming hard architectural dependencies unless they truly deserve that status.

## Loot realization

Loot should be data-driven wherever possible.

Skyforge can define functional loot-table families:

~~~text
skyforge:civilization/mine
skyforge:civilization/airfield
skyforge:civilization/weather_station
skyforge:civilization/industrial
skyforge:civilization/agricultural
skyforge:civilization/abandoned_salvage
~~~

Structure adapters can map compatible containers to these tables where supported.

Prefer tags or broad item categories in loot integration when they reduce direct dependency coupling.

## Population realization

Use existing entities first.

Civilian:

- villagers;
- iron golems;
- livestock;
- ordinary ecological fauna.

Hostile civilization:

- vanilla illagers;
- Friends & Foes;
- It Takes a Pillage.

Optional Guard Villagers may be prototyped if vanilla golems do not visually/gameplay-wise communicate maintained settlement defense.

No bespoke civilian/guard AI is justified yet.

## Machine realization

Functional machinery should use existing Create-family blocks.

Three acceptable states:

### Functional

Generated assembly actually works.

Use where robust and inexpensive.

### Plausibly incomplete

Mechanically coherent layout with one or more missing/broken components.

Often ideal for ruins/salvage.

### Static evidence

Only when actual function would be too fragile or expensive.

Still ensure the assembly looks mechanically plausible.

Avoid fake machinery that violates the mechanics players are learning.

## Civilian village dependency decision

Current first prototype:

> Towns & Towers before CTOV.

Reasoning:

- current 1.21.1 NeoForge support;
- compact structure-focused package;
- broad village vocabulary;
- avoids adding another overlapping village suite and CTOV's additional Lithostitched dependency at first.

CTOV remains reserve if Towns & Towers fails terrain adaptability, visual fit, or integration testing.

## Minor structure dependency decision

Do not install both Explorify and Structory automatically.

Prototype them against Skyforge needs.

Prefer Explorify if the priority is:

- simple vanilla-like landmarks;
- farmsteads;
- guideposts;
- caches;
- watchtowers;
- campsites;
- straightforward ruins.

Prefer Structory if the priority is:

- stronger atmospheric history;
- ruins;
- cottages;
- graveyards;
- light lore;
- more authored-feeling environmental storytelling.

The final choice can be one primary library plus selected supplemental assets only if a real gap remains.

## Maintenance budget rule

Every dependency and bespoke system incurs:

- version-port cost;
- compatibility testing;
- configuration maintenance;
- licensing/distribution review;
- performance risk;
- interaction debugging.

A new feature should justify its lifetime maintenance cost, not just its initial implementation effort.

## Acceptance principle

> Skyforge should be bespoke where meaning and cross-system coordination require it, and aggressively conventional everywhere else.

The ideal civilization implementation is a thin semantic/planning layer that makes existing Minecraft/mod content appear far more intentional than it was originally designed to be.
