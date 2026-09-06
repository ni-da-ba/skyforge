# Dimension Exploration Enrichment Audit v0.1

**Snapshot:** 2026-09-05
**Status:** Working content-curation audit. This document extends the dimension realization audits by treating **exploration variation** as a first-class requirement.

## Core rule

> A dimension is not fully realized merely because it has a useful resource loop. It must also remain interesting to traverse, observe, and explore.

The three dimensions should differ not only in economic role but in the **kinds of variation** the player encounters.

A useful exploration system combines several independent axes:

~~~text
SPATIAL VARIATION
    terrain / route geometry / negative space

BEHAVIORAL VARIATION
    mobs / fauna / hazards / faction behavior

STRUCTURAL VARIATION
    ruins / settlements / dungeons / infrastructure

RESOURCE VARIATION
    materials / loot / farms / process capability

ENVIRONMENTAL VARIATION
    atmosphere / visibility / heat / lift / local phenomena

EXCEPTIONAL VARIATION
    rare bosses / megafauna / extraordinary sites
~~~

A dimension can be sparse and still score highly if the few realized encounters are meaningfully different.

The target is **high semantic variety with controlled local density**, not biome soup.

## Cross-dimension exploration requirement

The player should eventually be able to distinguish dimensions by the question exploration asks.

~~~text
OVERWORLD
    What kind of place is over there, and how does it connect to my network?

NETHER
    What is beyond this dangerous route, and is reaching it worth establishing access?

END
    What is that distant anomaly/site, and can I mount an expedition that reaches it and returns?
~~~

If all three reduce to:

~~~text
fly until structure icon appears
-> loot chest
-> fly home
~~~

the dimension design has failed even if the terrain is attractive.

## Variation is not population density

Skyforge already prefers sparse realization.

That principle is even more important in Nether and End.

### Bad enrichment

~~~text
every chamber
    5 mobs
    3 structures
    particle effect
    special ore
~~~

or:

~~~text
every End island
    unique biome
    village
    dungeon
    boss
~~~

This destroys contrast.

### Healthy enrichment

~~~text
long ordinary region
    -> strong environmental transition
    -> distinctive behavioral encounter
    -> rare structure
    -> unusual resource/process opportunity
    -> quiet again
~~~

The player notices variation because it is **composed and separated**.

# Overworld exploration portfolio

The Overworld already has the strongest broad-content stack.

It earns variety through:

- climate;
- island morphology;
- water;
- caves;
- geology;
- ecology;
- civilization;
- weather;
- aviation routes;
- ruins/structures;
- exceptional fauna/threats.

The Overworld therefore does not currently need another broad content overhaul.

Its main enrichment risk is **overinstallation**.

Existing selected candidates already provide enough raw assets if Skyforge realizes them sparsely.

## Overworld target

~~~text
broadest variety
highest settlement/ecology density of the three
largest number of ordinary systems
many moderate-interest discoveries
few exceptional discoveries
~~~

This becomes the baseline against which Nether/End must feel equally worthwhile without copying its density.

# Nether exploration portfolio

## Experience target

> The Nether should be **dangerous, spatially constrained, and difficult to traverse — but every significant traversal decision should carry a credible chance of discovering something worth the effort.**

The player should repeatedly face:

~~~text
danger / route cost
    vs
unknown reward / capability / site
~~~

This gives tight traversal meaning.

If movement is difficult but destinations are repetitive, the Nether becomes tedious.

If destinations are excellent but movement is trivial, the Nether loses its identity.

## Required Nether variation axes

A mature Skyforge Nether should have meaningful variation in at least:

### 1. Route geometry

- tight passages;
- large vaults;
- lava margins;
- vertical shafts;
- bridgeable gaps;
- defensible choke points;
- open chambers suitable for aircraft;
- regions where rail/tunnel wins.

### 2. Hostile ecology / faction behavior

- Piglin territory;
- Ghast exposure;
- fortress-associated hostiles;
- Wither/Soul regions;
- biome-specific creature regimes;
- occasional rare predators/legendary encounters.

### 3. Structures

- Fortresses;
- Bastions;
- ruins;
- hostile outposts;
- catacomb/fortified-site archetypes;
- industrial or extraction remnants where appropriate.

### 4. Resource/process opportunities

- quartz;
- gold;
- Wolframite/Tungsten if selected;
- Ancient Debris;
- soul materials;
- Blaze/heat capability;
- lava;
- fungi/renewables.

### 5. Environmental regimes

- heat;
- visibility;
- lava exposure;
- pressure/altitude envelope;
- soul / fungal / basaltic regions;
- local convective or vent phenomena if later supported.

## Nether third-party candidate matrix

### Eternal Nether — **STRONG STRUCTURE/THREAT PROTOTYPE**

Current verified state:

- Minecraft 1.21.1;
- NeoForge;
- maintained 1.21.1 branch;
- MIT code license;
- client/server.

Current content includes:

- Piglin Manor;
- Citadel;
- Catacombs;
- Piglin Hunters;
- Piglin Prisoners / rescue interaction;
- corrupted Warped Endermen;
- Wither Skeleton variants and other hostile mobs;
- special loot/equipment;
- additional Nether building materials.

Role fit:

~~~text
STRUCTURE VARIATION      STRONG
FACTION VARIATION        STRONG
BEHAVIORAL VARIATION     STRONG
WORLDGEN TAKEOVER RISK   MODERATE
LICENSE/MAINTENANCE      STRONG
~~~

**Leading use:** selected hostile/civilization structures and associated mobs.

Source inspection of the maintained 1.21.1 branch is encouraging: Piglin Manor, Citadel, and Catacomb content is represented through data-driven `worldgen/structure`, `structure_set`, and `template_pool` resources, with associated mob features in configured-feature data.

That does not by itself prove zero-code Skyforge relocation, but it gives this candidate a **strong integration seam** compared with a hardcoded monolithic generator.

Skyforge requirement:

> Native structure placement must not become independent authority.

Prefer:

~~~text
Eternal Nether asset / mob / encounter
    -> Skyforge semantic site
    -> structure realization
~~~

If native placement cannot be cleanly disabled/redirected, lower its role.

A direct prototype should attempt:

~~~text
disable / bypass Eternal Nether structure sets
    -> retain template pools / NBT / mob content
    -> admit selected structure through Skyforge semantic site planning
~~~

before any bespoke recreation of those structures.

### BetterNether: New Dawn — **STRONG BROAD-CONTENT R&D CANDIDATE**

Current verified state:

- supports 1.21.1 NeoForge;
- MIT;
- actively maintained;
- adds biomes, plants, materials, mobs, structures, food, mechanics;
- includes farmable resources;
- supports vertical/volumetric biome systems;
- current releases advertise configurable blocks, items, structures, biomes, and plant density;
- recent 1.21.1-compatible release added config to disable biomes and structures.

Representative content includes:

- Naga;
- Flying Pig;
- Hydrogen Jellyfish;
- Skull;
- Gloomwisps;
- dark dungeons;
- rare Nether Cities;
- many plants/materials;
- large lava features.

This is unusually relevant because its recent configuration work may allow Skyforge to take terrain/structure authority while retaining selected assets and mechanics.

Role fit:

~~~text
ECOLOGY / AMBIENCE       VERY STRONG
BUILDING PALETTE         VERY STRONG
RESOURCE VARIATION       STRONG
BEHAVIORAL VARIATION     STRONG
WORLDGEN TAKEOVER RISK   HIGH but configurable
DEPENDENCY SURFACE       HIGH
~~~

Dependencies include BCLib: New Dawn, WunderLib: New Dawn, and WorldWeaver: New Dawn.

**Leading use:** broad Nether ecology/material prototype if the dependency surface and worldgen suppression prove clean.

Do not automatically combine with another full Nether overhaul.

### Jaden's Nether Expansion — **STRONG ALTERNATIVE BROAD SUBSTRATE**

Current verified state:

- 1.21.1;
- NeoForge/Forge;
- actively targeting 1.21.1;
- broad additions across biomes, blocks, items, mobs, mechanics;
- restrictive CC-BY-NC-ND-4.0 project license.

Its roadmap explicitly targets all five vanilla Nether biomes plus additional new biomes, each with blocks/items/mobs/mechanics.

Role fit:

~~~text
CONTENT BREADTH          VERY STRONG
VANILLA-FRIENDLY FIT     STRONG
BEHAVIORAL POTENTIAL     STRONG
WORLDGEN TAKEOVER RISK   HIGH
LICENSE FLEXIBILITY      LOW
~~~

**Leading use:** A/B competitor to BetterNether for a single broad Nether-content slot.

Use only as an external dependency under its license; do not treat source/assets as material to modify or redistribute.

It should not coexist with another broad Nether overhaul by default.

### Luminous: Nether — **FAUNA / LEGENDARY R&D RESERVE**

Current verified state:

- 1.21.1 NeoForge;
- 2 new biomes;
- 16 new mobs;
- 3 legendary beasts;
- 3 rare beasts;
- additional blocks and gameplay mechanics;
- ARR.

Role fit:

~~~text
CREATURE VARIETY         VERY STRONG
LEGENDARY CONTENT        STRONG
WORLDGEN COMPETITION     MODERATE/HIGH
LOCAL DENSITY RISK       HIGH
PERFORMANCE VALIDATION   REQUIRED
~~~

**Leading use:** creature/legendary encounter A/B testing, not a default broad dependency.

Skyforge must retain population budgets.

### Bosses of Mass Destruction — **STRONG SPARSE EXCEPTIONAL LAYER**

Current verified 1.21.1 NeoForge port is available and currently maintained.

Relevant bosses:

- Nether Gauntlet in a rare Nether structure;
- Obsidilith in rare End-island structure;
- additional Overworld bosses.

Role fit:

~~~text
EXCEPTIONAL ENCOUNTER    VERY STRONG
LOCAL DENSITY RISK       LOW if kept rare
PROGRESSION RISK         MODERATE
STRUCTURE AUTHORITY      NEEDS REDIRECT/ADMISSION
~~~

This remains a strong candidate precisely because it adds **rare peaks** rather than filling ordinary space.

## Nether candidate stack principle

Do **not** install all broad candidates.

Preferred experiment:

~~~text
VANILLA NETHER CONTENT
    +
ONE broad enrichment substrate
    BetterNether New Dawn
    OR Jaden's Nether Expansion
    OR another winner after A/B
    +
Eternal Nether selected structures/mobs
    +
Bosses of Mass Destruction exceptional encounter
    +
Create / Metallurgy / Aeronautics systems
    +
Skyforge semantic placement/population authority
~~~

Luminous: Nether is a reserve competitor if the creature catalogue materially improves experience.

The target is not maximum catalogue size.

It is:

> enough distinct content roles that entering a new Nether province/chamber can change what the player does.

# End exploration portfolio

## Experience target

> The End should remain sparse, alien, and expeditionary, but emptiness must be punctuated by enough genuinely different phenomena that long-range exploration produces anticipation rather than monotony.

The player should not expect an interesting object every minute.

They should expect that **distant travel can reveal something they have not seen before**.

## Required End variation axes

### 1. Landmass / negative-space composition

- long void crossings;
- denser archipelagos;
- isolated major landmasses;
- high/low routes;
- unusual silhouette families;
- safe and unsafe staging locations.

### 2. Behavioral encounters

- ordinary Endermen;
- locally distinct Ender behavior;
- aerial/void-crossing threats;
- ambush/hazard organisms;
- structure-associated mobs;
- rare neutral or useful fauna.

### 3. Structures

- End Cities;
- End Ships;
- gateways;
- ruins;
- isolated towers/stations/ships;
- extremely sparse exceptional structures.

### 4. Resource / process variation

- End Stone / Levitite;
- Chorus;
- Shulkers;
- rare structure resources;
- selected specialist materials only if they produce a real capability.

### 5. Phenomena

The End especially needs things that are not merely "another biome."

Potential roles:

- unusual visibility;
- local gravity/levitation phenomena if compatible;
- strange flow/pressure regimes;
- drifting/flying structures;
- large rare organisms;
- spatial anomalies;
- visually legible route hazards.

Prefer existing mod behavior where possible.

## End third-party candidate matrix

### Unusual End — **LEADING BROAD END-ENRICHMENT PROTOTYPE**

Current verified state:

- 1.21.1;
- NeoForge/Forge;
- actively updated;
- ARR;
- many generation changes are configurable;
- project explicitly documents config controls for several vanilla-biome generation additions;
- includes Create compatibility.

Current behavior/content examples include:

- tameable Ender Fireflies that defend against selected End creatures;
- Endstone Trappers hidden in Infested End Stone, detectable/avoidable through player behavior;
- hostile flying Undead Enderlings that make void bridges more dangerous;
- Citrine Candle counterplay to Undead Enderlings;
- End Houses that can provide maps to Ancient End Towers;
- Ancient End Towers with traps/loot;
- Flying End Ships with maps to End Cities;
- Wanderer Islands;
- Draglings with a temporary interaction-disruption behavior;
- Ender Infection effects;
- Warped Reef creature/plant/fishing systems;
- rare Shulker variant;
- Warped Ships and Stations.

This is exactly the kind of **behavioral exploration variety** Skyforge needs more than a generic biome pack.

Role fit:

~~~text
BEHAVIORAL VARIATION     VERY STRONG
STRUCTURE VARIATION      VERY STRONG
EXPEDITION INFORMATION   VERY STRONG
ECOLOGY VARIATION        STRONG
CREATE COMPAT             STRONG SIGNAL
WORLDGEN TAKEOVER RISK   MODERATE/HIGH but partly configurable
DENSITY RISK             HIGH if native defaults are not governed
~~~

**Leading use:** broad End content/behavior prototype.

Skyforge should attempt to:

- suppress or subordinate undesired native terrain tweaks;
- govern population;
- govern structure sites;
- retain useful behavior/reward loops.

This candidate deserves direct in-pack evaluation.

### Moog's End Structures (MES) — **STRONG STRUCTURE-ASSET PROTOTYPE**

Current verified state:

- supports 1.21.1 NeoForge;
- server-side capable;
- LGPL-3.0-only;
- focused on vanilla-styled End structures;
- actively maintained;
- current versions include additional large/mega ship variants.

Role fit:

~~~text
STRUCTURE VARIETY        STRONG
VISUAL FIT               STRONG/VANILLA-LIKE
CLIENT DEPENDENCY        LOW
WORLDGEN TAKEOVER RISK   MODERATE
BEHAVIORAL VARIATION     LOW
~~~

**Leading use:** structure vocabulary supplement if Skyforge can control density/placement.

Its strength is narrowness.

It should not by itself solve End exploration, because structures without behaviors/ecology/phenomena still become repeated loot silhouettes.

### Enderman Overhaul — **STRONG BEHAVIOR/REWARD CANDIDATE WITH MOBILITY RISK**

Current verified state:

- 1.21–1.21.1;
- NeoForge;
- over 20 Enderman variants;
- multiple variant pearls with special effects.

Examples include pearls that:

- teleport bound mobs;
- teleport nearby mobs;
- randomly teleport enemies;
- provide status effects;
- summon/recall pet Endermen.

Role fit:

~~~text
CREATURE VARIATION       VERY STRONG
REGIONAL IDENTITY        STRONG
REWARD VARIATION         STRONG
MOBILITY BYPASS RISK     HIGH
POPULATION DENSITY RISK  MODERATE/HIGH
~~~

**Leading use:** A/B ecology/behavior prototype.

Before selection, audit every pearl against:

- long-distance travel;
- entity transport;
- logistics;
- combat;
- recovery.

The visual/behavioral variants may be valuable even if some reward mechanics require disabling or exclusion.

### End's Delight — **LIFE / EXPEDITION-SUSTENANCE OPTIONAL LAYER**

Current verified state:

- 1.21.1;
- NeoForge;
- MIT;
- Farmer's Delight addon;
- actively updated, but currently described as beta.

Role fit:

~~~text
FOOD / LOCAL LIFE        STRONG
EXPLORATION VARIATION    MODERATE
ENGINEERING IDENTITY     LOW
PERMANENT BASE VALUE     MODERATE
~~~

This can help the End feel **inhabitable enough for expedition bases** without turning it into an Overworld clone.

Use only if the food/ecology loop fits the desired sparse alien identity.

### The Beyond — **REFERENCE / R&D ONLY FOR NOW**

Current verified state:

- 1.21.1 NeoForge;
- early-access beta;
- full terrain overhaul;
- new biomes/mobs/mechanics/lighting;
- explicitly recommends Isleweaver when mixing End mods;
- ARR.

Role fit:

~~~text
IDEA / VISUAL R&D        STRONG
BEHAVIOR R&D             PROMISING
WORLDGEN COMPETITION     VERY HIGH
MATURITY RISK            HIGH
~~~

Do not use as a core baseline now.

Its full-overhaul scope competes directly with Skyforge terrain authority.

It may remain useful as:

- inspiration;
- A/B prototype;
- source of observations about what End exploration benefits from.

### Bosses of Mass Destruction — **SPARSE EXCEPTIONAL LAYER**

The Obsidilith provides an existing rare End boss/structure target.

Use as a potential **exceptional expedition destination**, not ordinary population.

This fits the End especially well because huge quiet distances make rare monumental encounters more meaningful.

### The End: Expanded — **NOT CURRENT BASELINE-COMPATIBLE**

The current Modrinth release found in this audit targets Minecraft 1.21.8 rather than the project's 1.21.1 baseline.

Its ideas—new niches, structures, miniboss, resources—may be useful reference material.

Do not treat it as a current dependency.

### Endergetic Expansion — **HIGH-QUALITY REFERENCE, WRONG VERSION**

The Endergetic Expansion remains conceptually relevant because its Poise Forest ties a biome to unique mobs/mechanics and shows how a dimension can gain depth without simply adding ore.

However its current supported line found in this audit ends at Minecraft 1.20.1.

Do not include it in the 1.21.1 dependency slate unless a compatible port appears and is separately audited.

## End candidate stack principle

Preferred initial prototype:

~~~text
VANILLA END CORE
    +
Create Aeronautics / Sable
    +
Unusual End
    as broad behavior/content prototype
    +
MES selected structures
    only if Unusual End structure coverage is insufficient
    +
Bosses of Mass Destruction
    sparse exceptional target
    +
optional End's Delight
    only if expedition-base sustenance needs more depth
    +
Skyforge placement/population authority
~~~

Enderman Overhaul should be A/B tested separately because its special pearl rewards intersect mobility governance.

Do not install multiple broad End-overhaul mods merely to maximize biome count.

# Exploration grammar by dimension

## Overworld

Variation cadence:

~~~text
ordinary wilderness
-> ecological/geological change
-> settlement/resource clue
-> route decision
-> destination
~~~

The player mostly discovers **systems they can integrate into a permanent network**.

## Nether

Variation cadence:

~~~text
constrained hostile route
-> route hazard / choke
-> environmental regime change
-> fortified / resource / biological discovery
-> decision whether to establish access
~~~

The player mostly discovers **sites worth making reachable**.

## End

Variation cadence:

~~~text
long sparse crossing
-> distant silhouette / signal / anomaly
-> expedition commitment
-> unusual behavior/site/resource
-> staging / recovery decision
-> next horizon
~~~

The player mostly discovers **reasons to push farther**.

# Content-role budget

For each generated province/large region, do not attempt to realize the full installed catalogue.

Candidate budget model:

## Nether ordinary region

May realize:

- 1 dominant environmental regime;
- 1 secondary sub-regime;
- 2–4 ordinary mob roles;
- 0–2 faction/structure roles;
- 1 meaningful resource/process distinction;
- rare exceptional encounter only when specifically selected.

## End ordinary outer region

May realize:

- 1 strong landmass/environmental identity;
- 1–3 ordinary/specialist creature roles;
- 0–1 ordinary structure family;
- 0–1 special resource/process identity;
- exceptional structure/boss at very low frequency.

These are authoring budgets, not literal spawn constants.

The purpose is to protect contrast.

# Third-party authority model

Every candidate dependency must be decomposed.

~~~text
MOD
    ├── blocks/items
    ├── mobs/AI
    ├── structures
    ├── biomes
    ├── placed features
    ├── ores/resources
    ├── loot
    └── global mechanics
~~~

Skyforge can then assign:

~~~text
KEEP
KEEP_GOVERNED
DISABLE_NATIVE_PLACEMENT
REDIRECT_TO_SKYFORGE
ALLOW_NATIVE
REJECT
~~~

Example:

~~~text
Unusual End
    mob AI            -> KEEP_GOVERNED
    useful items      -> KEEP / AUDIT
    structures        -> REDIRECT_TO_SKYFORGE if practical
    flora tweaks      -> KEEP_GOVERNED / selective
    terrain authority -> SKYFORGE

BetterNether
    mobs/plants       -> KEEP_GOVERNED
    materials         -> KEEP / AUDIT
    structures        -> DISABLE_NATIVE / REDIRECT
    biomes            -> DISABLE_NATIVE or map semantic regimes
    terrain authority -> SKYFORGE
~~~

This is the preferred integration style.

# Selection rule: one broad substrate per dimension

A broad content overhaul has compounding effects:

- spawn tables;
- loot;
- progression;
- block palettes;
- structures;
- biomes;
- performance;
- compatibility.

Therefore:

> Prefer at most **one broad enrichment dependency per dimension** during the first integrated prototype.

Narrow additions may layer on top when they fill distinct roles.

Example healthy Nether stack:

~~~text
BetterNether OR Jaden
    broad content

Eternal Nether
    selected hostile structures/faction content

BOMD
    rare boss
~~~

Example unhealthy Nether stack:

~~~text
BetterNether
+ Jaden
+ Luminous
+ several structure packs
+ uncontrolled vanilla spawns
~~~

even if every mod is individually good.

# Variation acceptance tests

## EXP-V1 — traversal reward

Difficult traversal leads to enough distinct discoveries that route difficulty feels meaningful rather than tedious.

## EXP-V2 — behavior diversity

At least some regional variation changes **what the player does**, not only what blocks are visible.

## EXP-V3 — structure role diversity

Structures have different gameplay roles:

- progression;
- faction;
- information;
- farm;
- salvage;
- exceptional encounter;
- staging.

They are not all chest rooms.

## EXP-V4 — local sparsity

An ordinary view/region is not saturated with every installed content family.

## EXP-V5 — repeated discovery

After several hours in a mature dimension, the player can still encounter:

- a new structure role;
- unusual fauna;
- rare environment;
- exceptional site;
- different route problem.

## EXP-V6 — worldgen authority

Third-party mods do not independently dictate macro terrain/resource/structure geography once Skyforge authors that dimension.

## EXP-V7 — reward integrity

Third-party loot/items do not erase:

- aviation;
- freight;
- portal;
- resource;
- progression;

systems.

## EXP-V8 — environmental identity

Added content strengthens the dimension rather than making all three worlds converge toward the same modded-fantasy aesthetic.

## EXP-V9 — exploration legibility

Distinct sites/regions provide enough visual/audio/behavioral clues that exploration can be intentional rather than pure coordinate wandering.

## EXP-V10 — exceptional rarity

Bosses/legendary creatures remain exceptional enough to retain meaning.

# Immediate R&D slate

## Nether

1. Install/A-B **BetterNether: New Dawn** with native biomes/structures disabled where possible.
2. Determine whether its mobs/plants/materials remain independently usable under Skyforge-authored environments.
3. Install/A-B **Eternal Nether** and inventory its structures/mob bindings.
4. Compare BetterNether against **Jaden's Nether Expansion** as the single broad-content substrate.
5. Test **Luminous: Nether** only if the winning substrate lacks creature/legendary variety.
6. Keep BOMD Nether Gauntlet as sparse exceptional prototype.
7. Record local spawn/performance density before selecting any combination.

## End

1. Install/A-B **Unusual End** and disable as much native terrain generation as practical.
2. Verify which mobs/behaviors/structures survive cleanly under altered End worldgen.
3. Audit all Unusual End mobility/reward items.
4. Test **MES** as a narrow structure supplement only if needed.
5. Test **Enderman Overhaul** separately, with special-pearl mobility audit.
6. Keep BOMD Obsidilith as sparse exceptional prototype.
7. Test **End's Delight** only if expedition-base sustenance/life feels too thin.
8. Do not make The Beyond or other full terrain overhauls core while Skyforge End authorship is the goal.

# Current recommendation

The content problem appears solvable **without large bespoke content production**.

The likely architecture is:

~~~text
SKYFORGE
    owns world meaning / geography / density

THIRD-PARTY BROAD SUBSTRATE
    supplies a curated ecology/material/behavior vocabulary

NARROW STRUCTURE / BOSS MODS
    supply specialized encounters

VANILLA
    preserves recognizable dimension identity

CREATE / AERONAUTICS
    supplies engineering consequences
~~~

This is particularly promising in the Nether and End because current 1.21.1 NeoForge ecosystem options already contain much richer behavioral content than vanilla while still giving us several configurable/modular candidates.

# Acceptance principle

> The Nether should make the next chamber worth fighting to reach. The End should make the next horizon worth mounting an expedition toward.
