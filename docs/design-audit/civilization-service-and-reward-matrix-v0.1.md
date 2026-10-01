# Civilization Service and Reward Matrix v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design matrix. Not yet an accepted ADR.

This matrix maps civilization archetypes to player-facing services, information, salvage, and risk. It is intended to expose implementation gaps early.

## Service vocabulary

~~~text
REST
BASIC_SUPPLY
FOOD
TRADE
REPAIR
FUEL
STORAGE
NAVIGATION
WEATHER
CRAFTING
CARGO
RESOURCE_ACCESS
SALVAGE
INTELLIGENCE
COMBAT_REWARD
~~~

A service may be:

- NONE;
- LIGHT;
- NORMAL;
- STRONG.

## Archetype matrix

| Archetype | Rest | Supply | Trade | Repair | Fuel | Nav | Weather | Cargo/Storage | Salvage | Risk |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Frontier Homestead | Strong | Normal | Light | Light | None | Light | None | Light | None | Low |
| Route Station | Light | Light | Light | Normal | Normal | Strong | Strong | Normal | Light | Low |
| Agricultural Cluster | Normal | Strong | Normal | Light | Light | Light | None | Normal | Light | Low |
| Mining Settlement | Normal | Normal | Normal | Normal | Normal | Light | None | Strong | Light | Low/Med |
| Industrial Hub | Normal | Normal | Strong | Strong | Strong | Normal | Light | Strong | Light | Low/Med |
| Civilian Town | Strong | Strong | Strong | Normal | Normal | Strong | Light | Strong | None/Light | Low |
| Regional Hub | Strong | Strong | Strong | Strong | Strong | Strong | Strong | Strong | None/Light | Low |
| Abandoned Network | None | Light | None | None | Light | Clue-only | Clue-only | Light | Strong | Med |
| Illager Frontier | None | Loot-only | None | None | Light | Intelligence | None | Loot-only | Normal | High |
| Illager Military-Industrial Center | None | Loot-only | None | None | Normal | Intelligence | Possible | Loot-only | Strong | Very High |

Exact strength is later tuning.

## Implementation mapping

### REST

Prefer:

- beds;
- sheltered structures;
- normal spawn-point mechanics.

No custom inn system required.

### BASIC_SUPPLY / FOOD

Prefer:

- villager trades;
- vanilla farms;
- Farmer's Delight / Slice & Dice;
- modest generated stock/loot where appropriate.

### TRADE

Prefer:

- vanilla villagers and emerald economy.

Possible later extensions:

- role-biased profession distribution;
- data-driven custom trades only where a concrete gap exists.

Do not add a new currency.

### REPAIR

Prefer:

- anvils;
- crafting stations;
- Create workshops;
- ordinary player repair/crafting mechanics.

The settlement provides infrastructure and components rather than an NPC repair menu.

### FUEL

Depends on final propulsion/power stack.

Preferred realization:

- actual fuel containers/storage;
- trade or modest stock;
- industrial/airfield fuel infrastructure.

Avoid abstract "refuel button" systems.

### NAVIGATION

Prefer:

- vanilla maps/cartographers;
- lodestone/compass-like infrastructure where useful;
- CC:Tweaked GPS/wireless;
- Create: Radars or selected radar/navigation stack;
- visual beacons and route identifiers.

Thin Skyforge adapters may expose semantic route/weather truth only where existing APIs lack a clean source.

### WEATHER

Prefer:

- visible windsocks/vanes/instruments;
- authoritative atmosphere mod's own readings if exposed;
- CC/radar display integration where possible.

A bespoke sensor block is not justified unless the chosen atmosphere system provides no adequate instrumentation seam.

### CARGO / STORAGE

Prefer:

- vanilla containers;
- Sophisticated Storage if selected;
- Create logistics;
- actual warehouses/cargo yards.

Do not implement an abstract settlement inventory.

### SALVAGE

Prefer:

- generated blocks that can be dismantled;
- role-specific loot tables;
- incomplete Create/Aeronautics machinery;
- limited fuel/components/materials.

### INTELLIGENCE

Prefer:

- maps;
- route charts;
- signs/books only where needed;
- structure locations encoded through existing map mechanics;
- visible network relationships.

No quest-log intelligence system.

### COMBAT_REWARD

Prefer:

- vanilla/mod entity drops;
- structure loot tables;
- faction role-specific containers;
- existing boss/structure rewards.

## Functional loot families

Recommended Skyforge integration-owned loot semantics:

~~~text
CIV_AGRICULTURAL
CIV_MINING
CIV_INDUSTRIAL
CIV_AIRFIELD
CIV_ROUTE_STATION
CIV_WEATHER_NAV
CIV_CIVIC
CIV_ABANDONED_GENERAL
CIV_ABANDONED_INDUSTRIAL
CIV_ILLAGER_LOGISTICS
CIV_ILLAGER_MILITARY
~~~

Concrete tables should favor item tags or dependency-aware pools where practical.

## Progression effect

Civilization should primarily provide three kinds of progression acceleration.

### Convenience acceleration

The player can buy or access something they could already make.

Examples:

- food;
- common components;
- fuel;
- maps;
- repair infrastructure.

### Knowledge acceleration

The player learns where to go or how mature systems are organized.

Examples:

- landmark maps;
- route charts;
- infrastructure demonstrations;
- weather/navigation information.

### Component acceleration

The player obtains a small amount of machinery/material ahead of fully manufacturing it themselves.

Examples:

- Create parts;
- navigation computer;
- propulsion component;
- storage/logistics part.

This should not replace normal progression.

## Site identity must matter

If every settlement sells the same goods, functional geography becomes cosmetic.

The service/reward layer should preserve some specialization.

Examples:

~~~text
agricultural cluster
  -> food / seeds / livestock / farm processing

mining settlement
  -> tools / raw material / industrial supplies

industrial hub
  -> mechanical parts / fuel / repair

route station
  -> navigation / weather / fuel

regional hub
  -> broad but not necessarily cheapest supply
~~~

The exact villager trade implementation can remain approximate; loot/infrastructure should reinforce role even if profession simulation is imperfect.

## Active versus abandoned reward inversion

An important balance principle:

### Active site
High service value, low free salvage.

### Abandoned site
Low service value, high salvage.

This creates a natural reason to care about both.

An active airfield may be safer and useful for fuel/maps/repair.

An abandoned airfield may contain better free parts, but no services and more environmental/hostile risk.

## Hostile reward profile

Hostile settlements should offer:

- supplies;
- industrial/military components;
- weapons/armor through existing mods;
- maps/intelligence;
- occasional rare faction/structure rewards.

Avoid making them the universal best source of all advanced technology.

Civilian industry and wilderness resources must remain relevant.

## No hard quest dependencies

Nothing in this matrix requires:

- accepting a quest;
- reaching a reputation threshold;
- speaking to a named NPC;
- completing scripted objectives.

The world itself supplies the interaction.

## Gap test

Before adding any new civilization mod or bespoke system, ask:

1. Which service/reward cell is currently impossible?
2. Is that cell important to the intended experience?
3. Can vanilla or an already-selected dependency supply it?
4. Can a data/config/loot integration solve it?
5. Only then: is bespoke code/content justified?

## Acceptance principle

> Every civilization interaction feature must correspond to a visible functional role in the settlement and should use the cheapest existing mechanic capable of expressing it.
