# Working Mod and To-Build Ledger

**Snapshot:** 2026-09-05  
**Status:** Working selection ledger; versions/licensing must be reverified at implementation time.

This document consolidates the current candidate stack from the content-integration audit.

## Core/probable platform

### Traversal / rendering
- Create
- Sable
- Create Aeronautics
- Distant Horizons
- Separate Sable Render Distance or equivalent distant-contraption support

### Atmosphere / sound
- AmbientSounds
- Sound Physics Remastered
- Particle Rain as a low-risk visual candidate
- Aerodynamics4MC as the leading authoritative wind/atmosphere prototype
- Wind Tunnel as a strong dev/validation tool

### Ecology
- Naturalist
- Fowl Play
- Critters & Companions, pending performance/visual validation
- Sky Whales

### Hostiles / structures
- vanilla hostile and structure mechanics
- Friends & Foes
- It Takes a Pillage Continuation
- Illager Structures
- Mowzie's Mobs
- Bosses of Mass Destruction as a strong legendary prototype
- YUNG's Better Dungeons as a strong dungeon candidate
- YUNG's Better Mineshafts as a likely underground-history candidate
- In Control! as a generic/static integration safety layer

### Infrastructure / engineering
- CC:Tweaked likely core advanced automation/navigation
- Create: Radars candidate
- Create avionics/telemetry integration candidate
- Create Diesel Generators probable; strategic petroleum/refining candidate
- Create: Metallurgy strong processing-depth candidate; Skyforge remains ore/geology authority
- Create Crafts & Additions currently leads the single-electricity-ecosystem slot; avoid redundant power stacks initially
- late-game logistics automation candidate
- Sophisticated Storage/Backpacks only if freight gameplay remains meaningful; reject warehouse-scale early backpack configurations, recursive portable storage, or early fluid capacity that erases freight roles

### Ordinary life/building
- Supplementaries strong candidate
- Farmer's Delight strong candidate
- Slice & Dice compatible extension candidate
- Artifacts strong exploration-reward candidate
- Lootr for multiplayer if needed

## Strong prototypes / decision-stage candidates

- FTB Quests — leading guided-onboarding/quest-book prototype; vanilla advancements remain lightweight fallback
- Reliable Gliders — leading cheap personal-soaring prototype for 1.21.1 NeoForge; requires a pack-level early recipe override, while its campfire/fire/lava/magma updraft behavior should be retained for testing as a useful local-thermal proxy rather than disabled by default
- Disable Elytra Outside The End — strong reuse candidate for selectively suppressing vanilla firework boosting on 1.21.1 NeoForge while preserving ordinary fireworks; LGPL-3.0-or-later
- No More Elytra Boosting — mechanically narrow server-side 1.21.1 NeoForge alternative; restrictive license means dependency candidate only, not code source
- Elytra Tuning — reserve tuning candidate if reduced boost strength/duration is preferable to a binary disable

- Aerodynamics4MC
- Simple Clouds
- Weather2 / Expanded Weather2 Dynamics for severe-weather R&D only
- Birds/Boids Reforged
- Hybrid Birds
- Towns & Towers — leading civilian village/settlement vocabulary prototype
- Explorify **or** Structory
- selected YUNG temple/hut/monument mods
- Create Aeronautics Structures, pending license/distribution verification
- Repurposed Structures as a selective reserve
- When Dungeons Arise only as a small curated exceptional subset
- Hostile Harmony if its data-driven relationship layer proves stable/useful

## Reserve / optional

- FTB Chunks / Open Parties and Claims / Flan — multiplayer/server claim options; not generated-civilization protection

- Illager Invasion
- Creeper Overhaul
- Enderman Overhaul
- Rotten Creatures
- CTOV
- Guard Villagers — optional maintained-settlement defense prototype only if vanilla golems prove insufficient
- Dungeons & Taverns
- Cataclysm
- Quark
- Serene Seasons
- Cold Sweat
- JourneyMap/Xaero only if omniscience can be constrained appropriately
- Hang Glider — A/B reserve if Reliable Gliders handling proves too simple/arcade-like
- Gliders by Jeryn — reserve; broader tier/upgrade/updraft system risks creating a parallel mobility progression tree

## Currently omit / reject as foundations

- Born in Chaos — threat-density philosophy conflicts with Skyforge
- Alex's Caves worldgen — competes with Skyforge cave authorship
- Caves & Creatures — redundant cave-authoring/content model
- IDAS — imports another broad integration layer
- unrestricted When Dungeons Arise worldgen
- unrestricted Waystones/teleportation if it erases distance/logistics
- multiple competing sky renderers/weather authorities

## Bespoke Skyforge content currently justified

### Species
- cliff raptor
- legendary dragon only if existing options fail the aviation/ecological role

### Structure/content
Likely Skyforge-specific structure families where existing libraries are insufficient:

- navigation towers/beacons;
- weather stations;
- mooring towers;
- cliff ports/hanging docks;
- airfields/hangars/fuel depots;
- radar posts;
- route markers;
- salvage/wreck markers;
- settlement/industrial layouts driven by Skyforge semantics.

The preferred strategy is to build layouts/roles from existing block palettes rather than add bespoke block sets unless needed.

## Bespoke Skyforge integration — to-build package

### Ecology
```text
Habitat Context
Niche Feasibility
Spawn/Population Ownership
Cross-Mod Ecological Tags
Habitat Anchors
Wind/Thermal Preference Hooks
Population/Performance Budgets
```

### Threats
```text
Threat Context
Darkness Provenance
Spawn Provenance
Ambient Admission Governor
Per-Island / Active-Area Budgets
Pack-Size / Local Saturation Controls
Engineered-Spawning Context
Faction Geography
Threat Evidence
Population Telemetry
```

### Structures
```text
Exact-Volume Structure Population Stage
Surface-Supported Structure Admission Reuse
Settlement/Network Realization
Subsurface Occupancy/Excavation Contract
Cliff/Underside Anchor Mode
Detached Structure Mode
Structure-Seeded Terrain Mode
Required/Progression-Critical Structure Intent
Structure Terrain Envelope
Structure Population Provenance
Structure Site Plan / Claim Set
Staged Structure Site Capability Profile
Structure-to-Authorship Negotiation Policy
```

### Civilization
~~~text
Province Civilization Context
Cluster Settlement Plan
Settlement Tier
Island Functional Roles
Coarse Needs / Capacities
Route / Logistics Intent
Maintenance / Abandonment State
Faction / Control State
Settlement Site Planning
Distance / Horizon Signaling
Civilization History Grammar
Regional Hub / Route Graph
Successor-Use / Repurposing State
Data-Driven Active / Declining / Abandoned / Occupied Variants
Asset-Role Indirection / Fallback Mapping
Functional Civilization Loot Tables
Settlement Service Profile
Active / Abandoned / Hostile Interaction Policy
Navigation / Weather Information Rewards
Repairable Infrastructure via Ordinary Block Mechanics
Progression-Sensitive Civilization Asset Audit
Optional Sparse Civic-Asset Provenance Fallback
~~~

### Resources / progression
~~~text
Resource Availability Classes
Bootstrap Completeness Requirement
Resource Deposit Scale / Quality
Resource-to-Geology Mapping
Resource-to-Ecology Mapping
Strategic Fuel Geography
Trade / Salvage Substitution Profiles
Civilization Resource Dependencies
Progression-Sensitive Asset Classification
Selected-Mod Worldgen Authority Audit
Create Zinc / Striated-Material Worldgen Redirect
Diesel-Generator Petroleum Authority Adapter
Oil-Field Column-Exclusivity Prototype
Resource Evidence / Telemetry
Engineering / Mobility Progression Ladder
First-Flight Transitive Recipe Closure Audit
Source-Backed First-Flight BOM / Playable Craft Proof
Adhesive Bootstrap Path
Cargo / Logistics Progression
Bootstrap Region Recipe
Scope-Flexible Progression Guarantees
Starting-Region Traversal Proof
Quest-Off Bootstrap Acceptance
Pre-Brass First-Flight Prototype
Bootstrap Presentation Profiles
Parameterized First-Flight BOM Verifier
Early Glider Mobility Contract
Directed Glider Traversal-Edge Proof
Glider Recipe Override
Thermal / Updraft Compatibility Layer
Glider-vs-Aircraft Logistics Non-Substitution Acceptance
Starter-Group Recovery Connectivity
Vanilla Mobility Bypass Governance
Elytra Firework-Boost Suppression
Optional Hazardous Rocket-Use Feedback
Riptide-to-Glider Acceptance
Nether Portal Distance-Compression Audit
1:1 Nether Dimension-Type Datapack Prototype
Teleport / Waystone Dependency Audit
Portable Storage / Freight Integrity Contract
Backpack Capacity / Nesting Audit
Shulker / Ender-Chest Late-Courier Acceptance
Bulk / Fluid / Entity / Contraption Freight Acceptance
Post-Flight Regional Specialization Sequence
Copper / Zinc First-Route Placement Contract
Brass Capability-Payoff Acceptance
Sample-vs-Industrial Deposit Scale Contract
Petroleum Freight-Route Acceptance
~~~

### Onboarding / guidance
~~~text
Skyforge Milestone / Advancement Semantics
Optional FTB Quests Adapter
Bootstrap Evidence Hooks
Optional Structure/Role Visit Detection
Compact Quest/Guidance Content
~~~

### World composition
```text
Sky Exposure / Persistent Occlusion Metrics
Layering-Without-Roofing Cluster Constraint
Exceptional Twilight/Shadow Ecology
```

### Atmosphere / aviation
```text
Authoritative Wind Selection
Skyforge-to-Atmosphere Semantic Adapter
True Relative-Airflow Acceptance Tests
Glider Interaction with Authoritative Wind/Thermals
Soaring-Fauna Shared Lift Response
Anthropogenic Heat / Local-Thermal Compatibility
Wind/Weather Instrumentation Hooks
Distant Entity/Contraption Visibility Validation
```

### Navigation
```text
CC/radar integration where existing APIs suffice
coverage/infrastructure semantics
non-omniscient sensor access to Skyforge truth
```

## Key architecture rules

1. Skyforge owns semantics and placement meaning.
2. Third-party assets do not gain independent authority over population/world composition.
3. One authoritative source per environmental quantity.
4. Large installed catalogues are acceptable; local realized complexity remains constrained.
5. Meaning must survive without shaders.
6. Open sky and ordinary wilderness remain sparse.
7. Progression-critical structures cannot be silently rejected.
8. Player construction is not automatically exempt from ambient-spawn governance.
9. Technical farms should remain viable through engineered/structure/spawner semantics.
10. New dependencies must fill a genuine missing role rather than merely add variety.
11. Cheap personal gliding may solve local starter-group topology and may extend much farther through natural or prepared thermal routes, but powered flight remains the first logistics-enabling mobility transition.
12. Player-buildable updraft routes are allowed as low-throughput personal infrastructure; balance them against aircraft through freight, flexibility, convenience, and throughput rather than an artificial hard range prohibition.
13. Vanilla firework rockets should not provide safe sustained propulsion while fall-flying; preserve ordinary fireworks and prefer an existing server-side control before bespoke behavior.
14. Dimension identity and dimension transport are separate concerns: Nether/End content may remain intact while portal distance compression is altered if it would erase aviation geography.
15. General-purpose teleport convenience must not silently bypass authored distance and logistics.
16. Do not reduce vanilla inventory merely to manufacture aircraft demand; preserve freight value through throughput and payload classes.
17. Vanilla Shulker Boxes remain provisionally acceptable as late manual-courier storage; large early backpack capacity and recursive portable-container nesting do not.
18. Prefer a datapack-level Nether `coordinate_scale = 1.0` prototype before any bespoke portal implementation.
19. Copper, zinc, and Brass are current early-R2/post-flight resources, not first-aircraft guarantees; preserve the audited pre-brass closure unless manual testing disproves it.
20. Petroleum should first create strategic freight geography, not first-flight dependency.
