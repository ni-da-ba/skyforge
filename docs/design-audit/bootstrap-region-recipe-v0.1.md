# Bootstrap Region Recipe v0.1

**Snapshot:** 2026-09-05  
**Status:** Working design direction. Not yet an accepted ADR.

## Core principle

> The starting world should be procedurally varied but progression-complete.

Skyforge should not require one fixed starter island template.

Instead, world planning should satisfy a **bootstrap recipe**: a set of semantic/resource/traversal requirements that may be fulfilled across a starting island, group, cluster, or province.

The recipe guarantees that a normal player can discover and understand the intended early progression without needing lucky worldgen.

## Scope-flexible guarantee

The planner should satisfy each requirement at the smallest sensible scope.

~~~text
STARTING ISLAND
    |
    +--> immediate survival and first local decisions
    |
STARTING GROUP
    |
    +--> first short traversal
    +--> foundational resource variation
    |
STARTING CLUSTER
    |
    +--> complete first-engineering / first-flight closure
    +--> first navigation / structure / ecology examples
    |
STARTING PROVINCE
    |
    +--> post-flight regional specialization
    +--> civilization / route / strategic-resource introduction
~~~

A specific seed may satisfy more requirements on the starting island.

Another may distribute them naturally across several nearby islands.

Both are valid if accessibility and progression closure remain correct.

## Recipe is not a fixed layout

Do not specify:

- exact island coordinates;
- one required morphology family;
- exact structure positions;
- one settlement template;
- fixed ore veins;
- one visual biome.

Specify capabilities and relationships.

Example:

~~~text
needs:
  renewable wood
  food/water
  stone
  iron-class metal
  andesite / early mechanical-material closure
  selected local-traversal closure
  one manageable first crossing
  one useful cave/resource-learning opportunity
  first-flight material closure
~~~

Then allow normal Skyforge authoring to decide the actual islands.

## Starting-island requirements

The immediate spawn island should normally guarantee:

### Survival

- safe spawn surface;
- renewable basic building material or an immediately accessible equivalent;
- food path;
- water access or extremely nearby guaranteed water;
- stone/basic crafting path.

### Basic terrain literacy

The island should expose enough natural variation for the player to understand:

- upper surface;
- cliff/edge;
- underside risk;
- local ecology;
- basic geology/material variation;
- at least one useful reason to inspect terrain.

### Mobility

The player should be able to reach at least one neighboring objective without already having reliable aircraft.

Possible means:

- short bridge;
- climbable/connected terrain;
- glide/paraglider;
- simple rope/lift tool if selected;
- very short primitive flight.

Do not require a long blind void crossing as the first progression step.

### Selected early-glider path

If the pack includes the selected early glider prototype, Skyforge may use it as one realization of the first local traversal capability.

The world recipe should still express the semantic requirement rather than a concrete item:

~~~text
LOCAL_PERSONAL_CROSSING
~~~

Possible realizations remain:

- bridge;
- connected/climbable terrain;
- rope/lift;
- glider;
- another deliberately selected low-tier traversal tool.

When a starter layout **requires** gliding, the glider recipe closure must be satisfied before the first required glide edge. Rare hostile loot is not an acceptable hidden prerequisite.

Glider-enabled planning should use a directed edge model such as:

~~~text
GLIDE_EDGE {
    launchElevation
    arrivalElevation
    horizontalGap
    verticalDrop
    approachClearance
    landingMargin
    fallbackRoute
}
~~~

A high-to-low crossing does not imply that the same route is possible in reverse.

Progression-critical glide edges therefore require a proven return/recovery path.

See [Early Glider Mobility Contract v0.1](early-glider-mobility-contract-v0.1.md).

## Starting-group requirements

The local group should teach that islands differ.

Desired guarantees may include:

- at least two meaningfully different island morphologies or functional surfaces;
- a foundational metal/resource site distinct from the spawn island;
- one ecological difference;
- one cave/mineral opportunity;
- one modest structure or historical trace when compatible;
- when gliding is selected, at least one layout where the cheap glider is genuinely useful for local movement without becoming mandatory in every seed.

The point is not to showcase every Skyforge system immediately.

The point is to establish:

> leaving the first island reveals materially different opportunities.

## Starting-cluster bootstrap closure

The starting cluster should prove the complete transitive material/functional closure for:

~~~text
ordinary survival
-> iron-level tools
-> local crossing capability
-> basic Create
-> first practical powered-aircraft closure
-> first reliable flight
-> basic repair
-> basic storage
-> basic navigation
~~~

The exact item list is derived later from the final recipe/modpack audit.

### Important constraint

The closure must not rely on:

- a rare random structure;
- a rare villager trade;
- one specific dungeon chest;
- hostile-faction victory;
- strategic petroleum;
- a resource only reachable by reliable flight.

Guaranteed structures/trade may contribute only when the world recipe itself guarantees them.

## Starting-province requirements

The province can introduce systems that become meaningful once flight exists.

Candidate guarantees:

- at least one mature resource-specialization example;
- one civilization or historical network if civilization is enabled for the pack;
- one maintained or abandoned route/navigation example;
- one strategic-resource clue or distant destination;
- enough regional ecological/geological variation to reward flight.

Province guarantees should not make every province identical.

They define a minimum learning envelope.

## Bootstrap recipe model

Candidate neutral representation:

~~~text
BootstrapWorldRecipe {
    recipeIdentity
    spawnSiteRequirement
    islandScopeRequirements
    groupScopeRequirements
    clusterScopeRequirements
    provinceScopeRequirements
    progressionClosures
    tutorialExperienceRequirements
    recoveryRequirements
}
~~~

Each scope requirement is semantic and backend-neutral.

## Requirement kinds

Potential neutral requirement vocabulary:

### RESOURCE

Guarantee access to a semantic resource family.

### CAPABILITY_SITE

Guarantee a site such as:

- buildable surface;
- cave;
- mine-capable geology;
- airfield-capable surface;
- cliff/dock-capable geometry.

### TRAVERSAL

Guarantee that an early objective is reachable with the capabilities available before it.

### STRUCTURE_ROLE

Guarantee a role, not necessarily a specific concrete structure asset.

Examples:

- modest ruin;
- route station;
- active homestead;
- abandoned workshop.

### ECOLOGY

Guarantee one or more visible ecological teaching opportunities.

### INFORMATION

Guarantee a way to discover the next progression objective.

### RECOVERY

Guarantee that early failure is not an unrecoverable soft lock.

## Constraint solving

The recipe should be treated as a planning/admission problem.

Conceptually:

~~~text
generate ordinary semantic world plan
        |
        v
evaluate bootstrap requirements
        |
        +--> all satisfied
        |       -> accept
        |
        +--> missing relocatable requirement
        |       -> select / relocate / condition nearby content
        |
        +--> missing hard requirement
                -> re-plan affected starting scope
~~~

Do not patch final generated chunks ad hoc.

## Morphology diversity

Bootstrap requirements must not create a recognizable "starter island morphology."

Acceptance should explicitly verify that several primary morphology families/hybrids can satisfy the starting recipe.

Example valid starting arrangements:

### Massif start

- spawn on broad shoulder;
- mine in deep interior;
- nearby small agricultural Tableland;
- second island supplies andesite / deeper iron.

### Basin start

- protected settlement/survival floor;
- water retained in basin;
- nearby Spine contains metal geology.

### Lobed start

- spawn on one lobe;
- another lobe supplies agriculture;
- neighboring island supplies deep resources.

The recipe should preserve procedural surprise.

## Civilization in the bootstrap region

Civilization should be optional to the **core survival closure** but can be guaranteed as a learning aid in the broader starting cluster/province.

Possible first examples:

### Frontier homestead

Teaches:

- villagers/trade;
- agriculture;
- ordinary settlement infrastructure.

### Abandoned workshop / route station

Teaches:

- salvage;
- Create machinery;
- navigation infrastructure.

### Small route beacon

Teaches:

- navigation network;
- horizon signaling.

Avoid beginning directly inside a giant mature city unless a future experience preset explicitly chooses that style.

## Structured teaching sequence

A likely experiential sequence:

~~~text
1. survive on starting island
2. inspect nearby horizon
3. cross to second island by a valid local method, potentially the cheap glider
4. find first meaningful resource difference
5. begin Create/mechanical processing
6. encounter simple civilization/ruin infrastructure
7. assemble first reliable flight
8. use flight to reach regional specialization
9. discover mature infrastructure network
10. begin building own network
~~~

This is a design sequence, not a scripted quest chain.

## Recovery guarantees

The early region should tolerate mistakes.

Potential requirements:

- more than one accessible source of foundational materials;
- renewable food/wood path;
- no single irreplaceable generated component required for first flight;
- a lower-tech traversal fallback after aircraft loss;
- accessible repair/crafting materials.

Do not make the starting experience depend on preserving one unique chest item.

## Multiplayer

The first-generation bootstrap recipe should remain world-level rather than per-player when practical.

If all players share one world spawn, one starting region is simplest.

Future team-specific islands or starts are a separate product/design choice and should not be required by the neutral recipe.

## Presets

The same architecture can later support experience presets.

Examples:

~~~text
STANDARD
  balanced starting cluster

HARD_FRONTIER
  fewer civilization services, same hard progression closure

ENGINEERING_TUTORIAL
  stronger infrastructure demonstrations

WILD_START
  no active civilization nearby, abandoned examples only
~~~

Presets alter how requirements are satisfied, not the fundamental progression guarantees.

## Evidence requirements

Future bootstrap evidence should include:

- world seed;
- recipe/preset;
- spawn island morphology;
- requirement satisfaction by scope;
- exact resource closure;
- directed traversal graph before first flight;
- capability label for each required edge (walk/climb/bridge/glide/other);
- launch/arrival geometry and recovery proof for required glide edges;
- distance/cost to each required material;
- optional civilization/teaching sites;
- first-flight closure proof.

A visual atlas should show several seeds with meaningfully different valid starts.

## Acceptance tests

### No-flight bootstrap

A player with only pre-flight capabilities can obtain every transitive input required for first reliable flight.

### Morphology diversity

Valid starts span multiple morphology families/hybrids.

### No lucky-loot dependency

Bootstrap closure survives removal of non-guaranteed random loot.

### Traversal proof

Every required pre-flight destination has at least one plausible route using already-available capabilities.

If a required route uses gliding, the proof is directional and includes a return/recovery path rather than assuming reciprocal reach.

### Glider / aircraft separation

When a glider is selected, representative starter-group crossings may be glider-reachable, but the first powered aircraft must materially expand route reach, reversibility, and cargo capability.

### Regional reveal

After first flight, the province presents at least one meaningful reason to use it.

### Civilization independence

A world can satisfy core bootstrap progression even if the player ignores or dismantles optional nearby civilization.

## Acceptance principle

> The starting recipe guarantees learnability and progression closure, not a predetermined starting landscape.
