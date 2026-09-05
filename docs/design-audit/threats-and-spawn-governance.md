# Threats, Hostile Spawning, and Farm Compatibility

**Snapshot:** 2026-09-05  
**Status:** Working design direction / future implementation requirement.

## Failure mode to prevent

Prior sky-island experience showed a critical mismatch between vanilla spawning assumptions and vertically layered worlds:

- multiple stacked dark surfaces;
- permanently shaded undersides/lower islands;
- caves and exposed dark geometry;
- all within active player range.

The result can be visually and mechanically overwhelming even if global monster-cap numbers are technically ordinary.

Skyforge must therefore govern hostile realization rather than treating darkness as sufficient authorization.

## Threat semantics

Candidate coarse fields:

```text
ambientMonsterPressure
subterraneanThreat
undeadPressure
anomalyPressure
factionControl
predatorPressure
legendarySites
historicalViolence
abandonment
```

Threat categories:

1. ecological predator;
2. ambient monster;
3. subterranean monster;
4. factional hostile;
5. anomalous hostile;
6. legendary encounter.

## Darkness provenance

Skyforge should distinguish why a position is dark.

Useful morphological/provenance classes:

```text
SURFACE
SKY_SHADED_SURFACE
CLIFF
UNDERSIDE
CAVE_MOUTH
CAVE
DEEP_CAVE
STRUCTURE_INTERIOR
PLAYER_ENCLOSURE
PLAYER_EXCAVATION
```

Darkness caused by an overhead island must not automatically be treated as cave/night monster habitat.

General rule:

> Darkness may remain a vanilla spawn prerequisite, but it is not Skyforge authorization.

## Spawn provenance

Every hostile should ideally be attributable to one population domain:

```text
AMBIENT_AUTHORED
ENGINEERED
ECOLOGICAL
STRUCTURE
SPAWNER
TRIAL
FACTION
RAID_EVENT
BOSS
```

These domains should not all consume one small Skyforge budget.

### Ambient authored

Natural spawning on ordinary Skyforge terrain. Strictly governed.

### Engineered

Player-created or heavily modified enclosed spawn environment. Should approach vanilla technical-play behavior, subject to global safety constraints.

### Structure/spawner/trial/faction/boss

Encounter-specific systems with their own semantics and budgets.

## Farm compatibility

Do **not** use either of these simplistic policies:

- globally lower the vanilla MONSTER cap;
- exempt every player-placed block from Skyforge controls.

The latter would make bridges, cities, hangars, and large dark builds into hostile magnets.

The desired distinction is:

> ambient world darkness vs engineered spawning environment.

A useful future local context could include:

```text
localModificationDensity
enclosure
skyAccess
excavatedVolume
playerPlacedSupport
purpose-built dark volume evidence
```

Examples:

- open player bridge at night -> ambient pressure still applies;
- unlit city street -> sparse ambient pressure;
- large dark hangar -> some danger, but not a monster carpet;
- sealed heavily engineered spawning chamber -> increasingly vanilla-like natural spawning;
- spawner/trial/raid/structure farm -> preserve native mechanics.

The system should not attempt brittle "is this a mob farm?" AI classification.

## Population budgets

Avoid trusting the vanilla global MONSTER cap as the intended gameplay density.

Use layered budgets:

- per-island ambient budget;
- active-area/near-player ambient budget to prevent stacked-island accumulation;
- local saturation;
- family/group cooldown;
- encounter/faction/boss budgets separately.

Pack size should also be governed for ambient spawning.

Exact numbers must be established by gameplay testing rather than documented as architecture constants.

## Telemetry requirement

Future implementation should make hostile population and denied attempts observable.

Example desired debug categories:

```text
Ambient-authored
Engineered
Ecological
Structure
Faction
Raid/event
Boss

Denied:
  geometric shadow
  unsuitable morphology
  threat-family mismatch
  island budget
  active-area budget
  local saturation
```

This is an acceptance/debug requirement because density failures are otherwise difficult to diagnose.

## Hostile content direction

### Vanilla — core, curated

Keep familiar vocabulary but distribute semantically:

- zombie;
- skeleton;
- creeper;
- spider;
- Enderman;
- husk;
- stray;
- bogged;
- slime;
- witch;
- silverfish/cave spider where appropriate.

Phantom insomnia behavior should be reconsidered; the entity may be useful but the player-centered insomnia spawner is not aligned with Skyforge semantics.

### Friends & Foes — strong probable/core candidate

Use as vanilla-adjacent creature/illager expansion.

Disable or subordinate free-roaming/custom spawners where they assume ordinary Overworld geography.

Retain faction/structure realizations such as Iceologer/Illusioner/Wildfire where semantically admitted.

### It Takes a Pillage Continuation — probable

Use compact illager combat roles and selected assets.

Disable the custom Pillage Siege subsystem for Skyforge; faction/raid semantics should determine incursions.

### Illager Structures — probable/core structure vocabulary

Use as the leading hostile-civilization architecture library.

Skyforge should admit structures based on faction state instead of allowing unrestricted random-spread worldgen.

### Mowzie's Mobs — probable exceptional-content dependency

Default biome spawning should be disabled/subordinated.

Use creatures selectively as anomalous, ecological-specialist, or legendary content:

- Grottol -> mineral-rich cave context;
- Foliaath -> dangerous botanical anomaly;
- Naga -> selected aerial/highland context;
- Frostmaw -> legendary cold site;
- Wroughtnaut -> historical guardian.

### Bosses of Mass Destruction — strong legendary prototype

Good fit because bosses are destination/structure driven rather than ambient population expansion.

### Illager Invasion — reserve

Technically controllable and not primarily ambient spam, but likely redundant once vanilla + Friends & Foes + It Takes a Pillage are present.

### Creeper Overhaul / Enderman Overhaul / Rotten Creatures — reserve/omit initially

Technically controllable, but currently unnecessary because Skyforge already has stronger ways to communicate regional identity.

### Born in Chaos — reject as foundational

Its broad-danger-everywhere premise conflicts with the intended sparse, semantically localized threat topology.

### Cataclysm — later audit only

Large and impressive, but adds another major combat/progression ecosystem. Reconsider only if a real endgame encounter gap remains.

## In Control! — useful integration/development tool

Use as a generic/static safety layer for:

- disabling unwanted default mod spawns;
- spawn-reason restrictions;
- emergency/config-driven filtering.

Do not make it the semantic authority. Skyforge must still handle island ownership, morphology, shadow provenance, budgets, faction geography, and ecology.

## Threat rhythm

Desired world rhythm:

```text
ORDINARY WILD      mostly ecological danger
NIGHT              sparse Minecraft monster pressure
CAVE/DEEP          context-dependent subterranean pressure
ABANDONED          undead / hazards / scavengers
HOSTILE TERRITORY  organized faction encounters
ANOMALOUS          unusual regional threats
LEGENDARY          major destination encounter
```

Hostiles should often alter or signal the environment before direct confrontation.
