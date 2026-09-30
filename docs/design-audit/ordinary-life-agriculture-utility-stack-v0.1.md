# Ordinary Life, Agriculture, and Utility Stack v0.1

**Snapshot:** 2026-09-29  
**Status:** Provisional closure. Exact recipes, world placement, crop geography, traversal-sensitive utility tuning, and runtime compatibility remain deferred.

## Decision

Skyforge should retain a compact ordinary-life stack centered on Farmer's Delight, Create integration, and a curated Supplementaries configuration.

~~~text
FARMER'S DELIGHT
  -> ordinary food / cooking / agricultural substrate

CREATE: CENTRAL KITCHEN
  -> preferred Create integration for kitchen logistics and automation

ASSOCIATED FOOD ADDONS
  -> narrow additions only where they add a distinct production role

SUPPLEMENTARIES
  -> strong keep as curated vanilla-scale utility / settlement-life library

SKYFORGE
  -> ecology, placement, worldgen, wind, navigation, traversal, and population authority
~~~

A future bespoke Skyforge agriculture/ecology addon remains justified only if the sky-island setting creates a gameplay need not cleanly represented by existing content.

## Farmer's Delight

Retain as the ordinary-life anchor.

Desired role:

- crops and food production;
- Cutting Board / preparation;
- Cooking Pot / meal production;
- Skillet / cooking;
- Rich Soil and agricultural improvement;
- settlement-scale kitchens, farms, taverns, and food trade.

Food should create settlement and logistics value without becoming a separate technological progression tree.

## Create: Central Kitchen

Preferred automation bridge.

Use it to make Farmer's Delight machinery participate naturally in Create logistics rather than replacing kitchens with an unrelated food-factory technology.

Exact dependency surface, including Create: Dragons Plus, remains an implementation-time compatibility audit.

## Slice & Dice

Reserve / A-B candidate rather than automatic dependency.

Its dedicated Slicer/cooking automation overlaps Central Kitchen. Retain interest primarily where a distinct feature such as sprinklers, irrigation, greenhouse machinery, or moving agricultural equipment proves valuable.

Do not install solely to duplicate food-processing verbs already covered by Farmer's Delight + Central Kitchen.

## Associated food addons

Brewin' & Chewin' and similar addons may be retained where they add distinct ordinary-life production such as fermentation, brewing, preservation, or regional trade goods.

Avoid broad food-addon accumulation where the result is merely recipe/catalog growth.

## Supplementaries

Promote to **strong keep with curated configuration**.

Its principal value is the large human-scale utility and architectural vocabulary absent from Skyforge's engineering stack:

- notice boards;
- shelves;
- planters / flower boxes;
- lamps / sconces;
- flags / signs;
- jars / cages;
- ropes;
- domestic and workshop utility;
- settlement detail;
- small redstone affordances;
- ordinary civic / household objects.

### Authority boundary

Supplementaries may provide assets and local interactions.

It does not gain independent authority over:

- long-range navigation;
- authoritative wind/weather;
- engineering actuation where Create already owns the verb;
- heavy weapons;
- world population;
- structure/worldgen placement;
- strategic traversal.

## Initial Supplementaries curation

Strongly retain:

- building/detail blocks;
- notice boards / signs / flags;
- jars and cages;
- modest ordinary-life redstone utility;
- lighting and domestic/workshop objects;
- rope as a physical construction/traversal vocabulary subject to tuning;
- faucets as local/domestic utility rather than industrial fluid authority;
- flax as a possible crop/material if Skyforge owns placement.

Disable or intercept initially:

- Supplementaries Pulley, due to overlap with Create / Linear Bearing / articulated actuation;
- Cannon / Cannon Boat / Cannonball system, due to CBC authority;
- native galleon / naval-raid behavior;
- native cave-urn, mineshaft, and wild-flax placement until governed by Skyforge;
- automatic generic Road Sign structure search;
- Weather Vane's stock weather-derived instrument semantics;
- traversal-sensitive Rope Arrow and Spring Launcher until acceptance testing;
- duplicate weapon gadgets where they conflict with the established combat stack.

## Weather vane integration

Keep the object, replace the authority.

Target behavior:

~~~text
SKYFORGE AUTHORITATIVE WIND
  -> local direction
  -> vane orientation

SKYFORGE LOCAL WIND SPEED
  -> comparator / redstone signal
~~~

The vane should become a low-tech atmospheric instrument rather than an independent weather model.

## Navigation signage

Manual and settlement-authored signage is desirable.

Generic omniscient structure search is not.

If automated signage is retained later, it should resolve against Skyforge-known routes, settlements, or infrastructure rather than arbitrary global structure discovery.

## Rope boundary

Rope is strongly thematically appropriate for:

- docks;
- mooring structures;
- cliff access;
- rigging;
- farms;
- mines;
- cargo handling;
- bridges.

However, fast descent, fall-damage negation, rope arrows, and remote placement must be tested against early traversal and glider progression.

## Storage boundary

Supplementaries small containers do not reopen the dedicated-storage decision.

Small jars, cages, sacks, and similar manual containers are acceptable if they remain human-scale.

They must not become freight-scale portable storage.

## Worldgen and ecology

Supplementaries vegetation or structure assets may be reused, but Skyforge owns where they occur.

Examples:

- Flax may become a settlement crop or ecology-bound fiber source.
- Urns may populate suitable authored ruins/caves.
- Galleon or pirate assets, if reused at all, must be admitted through Skyforge civilization/population semantics.

## Design principle

> Ordinary-life mods should make settlements, farms, workshops, ports, and homes feel inhabited. They should not quietly become competing authorities over engineering, traversal, weather, combat, or world composition.
