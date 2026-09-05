# Bootstrap Experience Recipes v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design profiles. Not yet accepted world presets.

## Core rule

> Bootstrap experience profiles may change how progression is presented, but not whether progression is possible.

Every profile must satisfy the same hard closure:

~~~text
survival
-> basic Create workshop
-> adhesive path
-> first practical inter-island traversal
-> first reliable flight
-> repair/recovery margin
~~~

The profile changes distribution, civilization presence, teaching aids, and atmospheric framing.

## Shared hard guarantees

Every normal bootstrap profile should guarantee, within pre-flight-accessible scope:

- renewable wood/building material;
- food and water;
- stone/smooth-stone path;
- sufficient accessible iron for workshop + first craft + retry margin;
- sufficient accessible andesite for early Create + first craft;
- renewable/repeatable wool or fiber path;
- ordinary furnace fuel;
- one viable adhesive path;
- one viable workshop-power path;
- safe-enough build/test area;
- at least one meaningful nearby destination;
- no dependence on brass, petroleum, electricity, or rare loot unless the tested first-flight design later proves otherwise.

## Profile A — STANDARD

Intent: default balanced Skyforge onboarding.

### Starting island

- strong survival viability;
- some initial iron;
- ordinary morphology with no fixed family;
- one clear neighboring objective visible.

### Starting group

- at least one contrasting island;
- deeper iron/andesite opportunity;
- renewable wool/fiber and adhesive opportunity;
- one cave/geology teaching opportunity.

### Starting cluster

- complete first-flight closure;
- one modest historical/civilization example when compositionally appropriate;
- one safe workshop/test-flight site;
- one visible post-flight regional destination.

### Guidance

- FTB Quests or advancements explain the progression spine;
- no mandatory NPC/tutorial structure.

## Profile B — WILD_START

Intent: maximum isolation while preserving fair progression.

### Civilization

- no guaranteed active settlement in immediate cluster;
- abandoned/minor traces allowed;
- active civilization may first appear at province scale.

### Teaching

- terrain/resource differences do most of the instruction;
- optional abandoned workshop or wreck may demonstrate engineering without providing required unique components.

### Resource closure

Identical to STANDARD.

### Player lesson

> The world itself is sufficient; civilization is useful but not required.

## Profile C — FRONTIER_START

Intent: use sparse civilization as an early contextual teacher.

### Guaranteed semantic role

One small frontier site in group/cluster scope, such as:

- homestead;
- route station;
- tiny mining camp;
- modest farm;
- weather/navigation outpost.

### Allowed services

- basic food;
- bed/shelter;
- modest trade;
- route information;
- small repair/workshop access.

### Forbidden dependency

The player must still be able to reach first flight if they ignore, dismantle, or lose the site.

### Player lesson

> Civilization is an accelerator and reference example, not the progression gate.

## Profile D — ENGINEERING_GUIDED

Intent: stronger modpack-style teaching without fixed terrain.

### Guaranteed teaching examples

May include:

- abandoned basic Create workshop;
- simple beacon or route marker;
- safe first-flight test field;
- a few ordinary low-tier example components;
- strong quest/advancement guidance.

### Loot rule

Examples may reduce recipe discovery friction but must not supply a complete first aircraft or bypass the workshop.

### World diversity

Still uses normal morphology and resource placement subject to bootstrap constraints.

### Player lesson

> Observe, reproduce, then extend.

## Profile E — HARD_FRONTIER

Intent: lower service availability, higher logistical pressure after bootstrap.

### Hard guarantees

Unchanged through first reliable flight.

### Soft differences

- fewer settlement services;
- less free salvage;
- longer but still valid pre-flight traversal edges;
- fewer redundant conveniences;
- stronger post-flight regional specialization.

### Prohibition

Do not make hardness by deleting a transitive first-flight resource or relying on lucky loot.

## Scope distribution can vary by seed

Even inside one profile, the same requirement may be satisfied differently.

Example STANDARD seed 1:

~~~text
spawn Massif:
  wood + food + initial iron

nearby Tableland:
  wool + safe workshop

nearby Spine:
  andesite + deeper iron
~~~

Example STANDARD seed 2:

~~~text
spawn Basin:
  food + water + workshop

same island deep interior:
  most iron

nearby Lobed island:
  andesite + fiber/adhesive ecology
~~~

The recipe should admit both.

## Post-flight reveal

Every profile should arrange at least one strong reason to use the first aircraft after the player builds it.

Candidate post-flight targets:

- regional settlement;
- rich mineral island;
- route station;
- large ruin;
- unusual ecology;
- strategic fuel clue;
- dungeon/structure landmark.

This prevents first flight from feeling like an engineering achievement with nowhere meaningful to go.

## Recovery

All profiles should retain:

- renewable food/wood;
- replacement adhesive path;
- enough foundational materials for another attempt;
- a lower-tech traversal fallback;
- no unique irreplaceable first-flight chest item.

## Quest interaction

Profiles can select different guidance intensity without changing world closure.

~~~text
STANDARD            -> full or light guidance
WILD_START          -> light/minimal guidance
FRONTIER_START      -> full/light guidance
ENGINEERING_GUIDED  -> full guidance
HARD_FRONTIER       -> light/minimal guidance
~~~

Guidance remains optional in all cases.

## Implementation implication

The planner should separate:

~~~text
BootstrapHardRequirements
BootstrapPresentationProfile
~~~

Hard requirements are acceptance constraints.

Presentation profile influences:

- where requirements are satisfied;
- civilization examples;
- salvage examples;
- route visibility;
- teaching-site preference;
- quest/advancement defaults.

## Acceptance tests

1. All profiles satisfy identical first-flight hard closure.
2. Ignoring civilization does not invalidate bootstrap.
3. Multiple morphology families can realize each profile.
4. Different seeds distribute resources/examples differently.
5. Every profile provides a meaningful post-flight destination.
6. HARD_FRONTIER increases pressure without introducing soft locks.
7. ENGINEERING_GUIDED teaches more without giving away mature capability.

## Acceptance principle

> Starting recipes vary the story of learning Skyforge, not the physical right to learn it.
