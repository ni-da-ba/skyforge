# Deep-Sky and Mythic Threat Stack v0.1

**Snapshot:** 2026-09-30  
**Status:** Provisional content-stack closure. Runtime aircraft-target compatibility, exact spawn governance, progression curation, and performance remain integration gates.

## Decision

Skyforge should avoid bespoke V1 aerial-threat content unless existing creature libraries fail runtime integration.

The preferred deep-sky/threat vocabulary is:

~~~text
VANILLA
  Phantom and retained familiar hostile vocabulary

ICE & FIRE CE
  dragons
  Stymphalian Birds
  ghosts
  hippogryphs
  amphitheres
  sea serpents
  selected mythic structures / roosts / lairs
  additional mythic encounters where they fit

ALEX'S MOBS
  Guster
  Farseer
  Void Worm
  Enderiophage
  Soul Vulture
  Crimson Mosquito
  other selected specialists

MOWZIE'S MOBS
  selected exceptional / legendary encounters

ILLAGER / FACTION STACK
  physical Sable/Aeronautics aircraft and crew

SKYFORGE
  geography
  threat admission
  faction authority
  spawn/population budgets
  airspace context
  vehicle-target integration
~~~

## Airspace semantics

Do not treat all open air as one spawn domain.

Preferred contexts:

~~~text
ABOVE_ISLAND
CLIFF_EDGE
UNDERSIDE
INTERISLAND_ROUTE
DEEP_VOID
STORM_AIR
HOSTILE_TERRITORY
OCEAN_ISLAND / MARITIME
~~~

These contexts choose eligible threat families and density.

## Ice & Fire CE

Promote Ice & Fire CE from reserve/A-B dragon candidate to **strong provisional mythic/threat content dependency**.

Reason: one dependency may fill several otherwise-bespoke gaps.

### Dragons

Primary role:
- legendary territorial aerial predator;
- high-visibility regional hazard;
- eventual aircraft-capable threat.

Current AI already contains genuine 3-D flight logic and multiple aerial attack modes including tackle, hover-blast, and strafing/scorch-stream behavior.

Target Skyforge treatment:
- Skyforge owns territory/admission;
- native roost/cave placement may be retained selectively where appropriate rather than globally suppressed;
- dragons may function as both encounter and regional-signaling content;
- player taming/riding/progression remains independently audited;
- aircraft targeting should use a generic vehicle-target bridge rather than an Ice & Fire-specific Sable fork.

### Stymphalian Birds

Primary role:
- hostile aerial flock;
- light-aircraft / glider harassment;
- ranged airspace pressure.

Current behavior already includes flocking, 3-D pursuit, ranged feather volleys, line-of-sight checks, and fear/retreat behavior.

Prefer as the ordinary dangerous aerial-pack realization before authoring bespoke flock predators.

### Ghosts

Primary role:
- underside / ruin / wreck / abandoned-site threat;
- sparse phasing danger around complex geometry.

Do not use as generic ambient undead.

### Hippogryphs and Amphitheres

Retain as potentially useful mythic aerial fauna / predators / mounts.

Their existence does not automatically authorize unrestricted player flight progression.

Treat independently:
- wild ecology;
- taming;
- riding;
- breeding;
- equipment;
- loot.

### Sea Serpents

Retain explicitly for future ocean-island / maritime threat design.

They may supply a real large-scale marine hazard without bespoke Skyforge sea-monster content.

### Structures / roosts / lairs

Do **not** strip Ice & Fire to entities-only by default.

Selected dragon roosts, caves, lairs, nests, or other structures may be retained where they:
- communicate territory;
- support exploration;
- provide environmental warning;
- fit island morphology;
- do not overwhelm Skyforge placement authority.

Skyforge may admit them semantically rather than permitting unrestricted stock worldgen.

### Progression baggage

Audit independently:
- Dragonsteel;
- Dragon Forge;
- eggs;
- taming;
- mount progression;
- weapons/armor;
- special materials;
- loot.

Useful creatures/structures do not imply automatic adoption of the full progression tree.

## Alex's Mobs specialist roles

Retain Alex's Mobs as a selective specialist library rather than general authority.

Leading aerial / deep-sky roles:

- Guster -> weather/anomalous-air threat;
- Farseer -> deep-void ranged anomaly;
- Void Worm -> rare deep-void legendary encounter;
- Enderiophage -> End specialist / exposed-creature threat;
- Soul Vulture -> Nether aerial scavenger/predator;
- Crimson Mosquito -> Nether/anomalous light-air threat.

Not every flying creature needs to attack enclosed aircraft.

## Vanilla Phantom

Retain the entity.

Subordinate or replace the insomnia-centered spawning rule.

Potential Skyforge roles:
- sparse nocturnal open-sky hazard;
- abandoned/anomalous airspace;
- high ambient-monster-pressure regions.

## Organized aerial threats

Faction aircraft remain separate from ambient-monster budgets.

Illager / hostile-faction aircraft should eventually materialize as real Sable/Aeronautics vehicles using the same physical substrate as player craft.

They should occupy:
- patrol;
- scout;
- interceptor;
- raider;
- gunship;
- transport / boarding roles.

## Shared vehicle-target bridge

Aircraft-threat integration should scale by attack style, not by species.

Conceptual target abstraction:

~~~text
AirVehicleTarget:
  worldPosition
  linearVelocity
  angularVelocity
  worldBounds
  aimPoints
  owner / faction
  vehicleClass
  vulnerability hints
~~~

Consumers may use species-specific policy over that shared target.

Examples:
- dragon -> intercept / tackle / blast / strafe;
- Stymphalian flock -> standoff / volley / disengage;
- Farseer -> ranged tracking;
- Naga -> pursuit / close attack;
- faction aircraft -> mission-level intercept.

## Aircraft damage integration

Prefer existing Sable collision/projectile/explosion behavior where possible.

Two broad attack classes:

### Physical
- projectile;
- explosion;
- collision;
- impulse;
- local block damage.

These should interact with real Sable geometry.

### Semantic
- direct LivingEntity hurt calls;
- potion/status effects;
- latching/infection mechanics.

Translate only where the fiction remains coherent. Otherwise target exposed occupants rather than the vehicle.

## Threat signaling

Threat content should often advertise itself environmentally before combat.

Examples:
- dragon roost / scorched territory;
- circling Stymphalian flock;
- whale / megafauna corridor;
- sea-serpent waters;
- ghostly wreck/ruin site;
- severe-weather Guster conditions;
- faction radar / patrol presence.

This may reduce the need for bespoke Skyforge warning content.

## Population rule

Installed richness does not imply dense simultaneous realization.

A region should select a small threat vocabulary appropriate to its context.

Example:

~~~text
wild temperate route
  -> Naga or Stymphalian risk

night anomalous route
  -> Phantom

storm corridor
  -> Guster

hostile faction territory
  -> aircraft

legendary territory
  -> dragon

deep void
  -> Farseer / rare Void Worm

ocean island waters
  -> sea serpent
~~~

## Acceptance gates

Before production lock:

1. Prove Ice & Fire CE boots on the pinned stack.
2. Prove selective worldgen/spawn suppression and semantic admission.
3. Prove dragon flight behavior at Skyforge altitude/void geometry.
4. Prove generic Sable aircraft target acquisition.
5. Prove dragon attack pass against moving aircraft.
6. Prove Stymphalian flock attack against the same vehicle-target abstraction.
7. Prove at least one ranged specialist (for example Farseer) against the same abstraction.
8. Verify physical projectiles/explosions interact with Sable sublevels as intended.
9. Measure multiplayer/entity/pathfinding performance.
10. Audit taming/mount/progression bypasses.
11. Audit dragon griefing and block destruction around player craft/settlements.
12. Audit sea-serpent behavior once ocean-island realization exists.

## Bespoke-content consequence

Provisional V1 stance:

- bespoke cliff raptor: **not justified unless existing candidates fail**;
- bespoke legendary dragon: **not justified unless Ice & Fire dragons fail**;
- bespoke hostile aerial flock: **not justified unless Stymphalian Birds fail**;
- bespoke sea megathreat: **not justified unless Sea Serpents fail**.

Bespoke Skyforge work should concentrate on:
- semantic admission;
- shared aircraft targeting;
- atmosphere integration where appropriate;
- population budgets;
- faction geography;
- interoperability.

## Design principle

> Prefer one coherent mythic content dependency plus selective specialist libraries over multiple bespoke creature families. Skyforge owns where and why threats exist; retained mods supply the creatures, structures, and combat behaviors.
