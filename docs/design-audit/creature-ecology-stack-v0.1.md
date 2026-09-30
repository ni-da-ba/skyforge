# Creature Ecology Stack v0.1

**Snapshot:** 2026-09-30  
**Status:** Provisional stack closure for ordinary fauna. Alex's Mobs remains a separate species-level audit. Threat/monster ecology remains out of scope.

## Decision

Skyforge should own habitat admission, carrying-capacity semantics, population budgets, and ecological niche assignment.

Creature mods should primarily supply entity implementations and behaviors.

Preferred ordinary-fauna stack:

~~~text
NATURALIST
  -> broad terrestrial / aquatic fauna library

FOWL PLAY
  -> preferred ordinary bird / flying-bird behavior library

CRITTERS & COMPANIONS
  -> selective small-fauna / arthropod / pet-scale niche library

SKY WHALES
  -> exceptional aerial megafauna prototype

ALEX'S MOBS
  -> reserve species library pending deeper species-by-species audit

SKYFORGE
  -> habitat, niche, population, climate, migration, spawn admission, and progression authority
~~~

## Ecological authority

Skyforge decides:

- whether a habitat can support a niche;
- which species implementation fills that niche;
- carrying-capacity descriptors;
- realized population budgets;
- climate and weather suitability;
- settlement pressure;
- migration / transient-use semantics;
- exceptional-fauna admission.

Installed creature mods do not gain authority merely because a Minecraft biome tag matches their default spawn rules.

## Core libraries

### Naturalist

Strong core library for broad ordinary fauna.

Preferred roles include:

- large terrestrial herbivores;
- ordinary terrestrial predators;
- reptiles;
- general aquatic fauna;
- selected insects / ambient fauna where not superseded by a stronger specialist implementation.

Native spawn semantics should be subordinated to Skyforge habitat admission wherever practical.

### Fowl Play

Strong core and preferred ordinary-bird authority.

Prefer Fowl Play over duplicate Naturalist bird implementations where Fowl Play supplies the same broad niche with stronger flying / schedule / perching / roosting behavior.

Target roles:

- songbirds;
- corvids;
- waterfowl;
- ordinary aerial predators;
- gull/coastal birds;
- migratory or weather-responsive birds where appropriate.

### Critters & Companions

Selective strong keep for small-fauna niches.

Prefer where it provides distinct:

- insects / arthropods;
- small terrestrial fauna;
- otter/ferret-scale niches;
- small aquatic or domestic/companion fauna.

Duplicate species should be A/B tested against Naturalist.

Stock progression rewards remain independently auditable; retaining an animal does not automatically retain every drop, recipe, grappling mechanic, potion shortcut, or progression side effect.

## Shared atmospheric interface for flying fauna

Do not write bespoke wind/thermal logic per species unless a species has a genuinely unique requirement.

Instead define a shared flight-environment sample consumable by flying-creature AI.

Conceptual interface:

~~~text
FlightEnvironmentSample(position, time):
  windVector
  verticalVelocity
  thermalStrength
  turbulence
  gustMagnitude
  shear
  precipitation
  visibility
  hazardSeverity
~~~

This should be backed by the canonical Skyforge weather/atmosphere contract.

Flying-creature implementations may then choose species-specific policy over the same environmental data:

- soarers may seek lift;
- small birds may avoid strong turbulence;
- waterfowl may alter routing in heavy weather;
- large aerial fauna may tolerate stronger conditions;
- migratory species may use regional wind fields.

The atmospheric system is shared; behavior policy is species-specific.

This is preferable to one-off integrations because the concept remains backend-neutral and extensible.

## Sky Whales

Retain as exceptional aerial-megafauna prototype.

Native near-player / Phantom-style spawn authority should be disabled or subordinated to Skyforge ecology.

Skyforge should instead admit whales through habitat / territory / migration semantics.

### Whale-derived lift

Whale-derived lift is **not required to remain subordinate to Levitite**.

It may serve as an early-game alternative or replacement pathway if playtesting shows that this improves progression.

Candidate balance space:

- biological lift available earlier through dangerous / rare ecological interaction;
- lower efficiency, durability, scalability, controllability, or availability than mature engineered lift;
- different logistical / ethical / regional constraints;
- Levitite remaining a later engineered, scalable, standardized solution.

However, no hierarchy is locked yet.

Playtesting should compare:

~~~text
WHALE LIFT
  acquisition difficulty
  rarity
  lift density
  durability
  scalability
  repairability
  ethical / ecological pressure
  regional dependence

LEVITITE
  industrial gating
  End access
  lift density
  reliability
  manufacturing scale
  logistics
~~~

The correct outcome may be:
- whale lift before Levitite;
- parallel alternatives;
- whale lift as niche specialist;
- or whale lift removed from progression.

This remains a balance decision, not an ecological assumption.

## Alex's Mobs

Do not provisionally adopt Alex's Mobs as a general fauna authority.

It deserves a dedicated species-level audit because it may fill high-value niches not covered well by the core libraries.

Audit each attractive species independently using:

~~~text
KEEP
  fills a real Skyforge niche cleanly

DUPLICATE
  overlaps an already preferred implementation

SPECIALIST
  useful only in particular climates / habitats / regions

PROGRESSION-AUDIT
  entity is useful but drops / mechanics may disrupt progression

FANTASTICAL-REVIEW
  requires explicit setting / lore justification

DISABLE
  adds no useful role or conflicts with established authority
~~~

Do not import the entire Alex's Mobs item / structure / progression surface merely to retain a small subset of useful species.

## Population realization

Skyforge should not simulate every unloaded animal continuously.

Preferred hierarchy:

~~~text
PROVINCE
  -> possible fauna assemblages

CLUSTER
  -> broad habitat / migration opportunities

ISLAND
  -> niche suitability + carrying-capacity descriptors

LOADED REGION
  -> sparse realized entities
~~~

Maintain high potential species richness while keeping active entity density modest.

Population realization should be deterministic enough to preserve ecological identity without requiring full continuous predator-prey simulation outside loaded areas.

## Niche preference map

Current provisional ownership:

| Niche | Preferred source |
|---|---|
| Large terrestrial herbivores | Naturalist |
| Ordinary terrestrial predators | Naturalist |
| Reptiles | Naturalist |
| General freshwater / marine fauna | Naturalist |
| Songbirds | Fowl Play |
| Corvids | Fowl Play |
| Waterfowl | Fowl Play |
| Ordinary aerial predator | Fowl Play |
| Scavenging bird | Naturalist initially; A/B if better specialist exists |
| Tiny insects / arthropods | Critters & Companions selectively |
| Small companion / pet-like fauna | Critters & Companions selectively |
| Rare aerial megafauna | Sky Whales provisional |
| Missing specialist species | Alex's Mobs reserve |
| Legendary / world-defining fauna | bespoke Skyforge only when justified |

## Deferred compatibility work

### CREATURE-COMP-1 — Spawn authority
Disable or suppress native biome-driven spawning where required and prove Skyforge admission works without duplicate populations.

### CREATURE-COMP-2 — Flying-fauna atmosphere adapter
Build one shared weather/flight sample adapter and validate it with several behavior classes:
- small flier;
- soaring raptor;
- waterfowl;
- large aerial fauna.

### CREATURE-COMP-3 — Species overlap
Build a species-by-species Naturalist / Fowl Play / Critters & Companions matrix.

### CREATURE-COMP-4 — Progression side effects
Audit drops, recipes, taming rewards, traversal items, potions, lift materials, and other progression impacts independently from entity retention.

### CREATURE-COMP-5 — Population performance
Characterize AI, pathfinding, flocking, flying, and aggregate active-entity budgets across representative islands and multiplayer.

### CREATURE-COMP-6 — Alex's Mobs
Run the dedicated niche audit before deciding whether to import selected species or the mod itself.

## Acceptance boundary

This document provisionally closes the ordinary-fauna stack only.

It does not yet lock:
- exact species catalogue;
- whale-lift progression;
- Alex's Mobs;
- hostile-monster / threat ecology;
- legendary fauna;
- full migration simulation.

## Design principle

> Skyforge owns ecological meaning. Creature mods supply implementations. Flying organisms should experience the same atmosphere as aircraft through a shared environmental contract rather than bespoke per-species weather hacks.
