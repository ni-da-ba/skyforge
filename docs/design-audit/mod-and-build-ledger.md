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
- Eternal Nether — strong 1.21.1 NeoForge Nether structure/threat prototype; MIT; maintained branch exposes Piglin Manor/Citadel/Catacomb through data-driven structure/structure-set/template-pool resources, making Skyforge-controlled placement especially promising
- BetterNether: New Dawn — strong broad Nether-content R&D candidate; MIT; 1.21.1 NeoForge; current releases expose configuration for biomes/structures and provide mobs, plants, materials, farmables, dungeons/cities; high dependency/worldgen surface requires A/B
- Jaden's Nether Expansion — strong alternative single broad Nether-content substrate; actively targeting 1.21.1 NeoForge; broad mobs/mechanics/biomes; restrictive license means external-dependency use only and worldgen authority must remain with Skyforge
- Unusual End — leading broad End behavior/content prototype; 1.21.1 NeoForge; configurable generation changes, Create compatibility, behavior-rich mobs, mapped structures, flying ships/stations; native density/placement requires governance
- MES / Moog's End Structures — strong narrow End structure-vocabulary candidate; 1.21.1 NeoForge and server-side capable; use only if broader End content does not provide enough structure roles
- Enderman Overhaul — strong End creature/reward A/B candidate; 1.21.1 NeoForge; special pearl effects create mobility/entity-transport audit obligations
- End's Delight — optional 1.21.1 NeoForge expedition-base sustenance layer if the End needs more local-life depth
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
- Create Propulsion: Simulated — strong advanced-propulsion R&D candidate for 1.21.1 NeoForge; chemical/solid/ion thrust and optional pressure coupling are especially relevant to End/high-altitude gameplay, but recipe balance and pressure configuration are not locked
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
Dimension Gameplay Requirements
Dimension Realization Value Audit
Dimension Exploration Enrichment Audit
Cross-Dimension Route and Infrastructure Grammar
Route Capability / Payload / Reliability Proof
Dimension Route-Value Node Planning
Nether Mixed-Mode Corridor Proof
End Staging / Recovery Route Proof
Third-Party Dimension Content Authority Decomposition
Broad-Substrate A/B Selection (one per dimension)
Nether Exploration Variation Acceptance
End Exploration Variation Acceptance
Overworld Realization Audit
Nether Realization Audit
End Realization Audit
Dimension Repeatability / Persistent-Value Acceptance
Cross-Dimension Skyforge Authorship Strategy
Dimension World-Grammar Matrix
Dimension-Domain Authority Boundary
Sable Dimension-Physics Acceptance
End Thin-Air Aircraft Acceptance
End Aeronautics Progression Contract
End Stone -> Levitite Recipe/Process Acceptance
Levitite Lift-Support / No-Free-Climb Acceptance
Pre-Dragon Levitite / Dragon-Encounter Compatibility
Nether Gameplay and Aviation Contract
Nether Roof-Pressure Aviation Acceptance
Nether Route-Topology / Mixed-Mode Mobility Acceptance
Fortress / Bastion Authored-Terrain Compatibility
Wolframite -> Tungsten -> Obdurium -> Industrial-Crucible Acceptance
Nether Portal Arrival / Recovery Acceptance
Cross-Dimension Contraption Transfer Audit
Advanced Low-Pressure Propulsion Audit
Outer-End Skyforge Pilot
End Gateway / End City Compatibility Contract
Nether Solid-Dominant Cavern Province Pilot
Nether Fortress / Bastion Compatibility Contract
Dimension-Specific Environment Profile
Interdomain Travel Edge Semantics
Teleport / Waystone Dependency Audit
Portable Storage / Freight Integrity Contract
Backpack Capacity / Nesting Audit
Shulker / Ender-Chest Late-Courier Acceptance
Bulk / Fluid / Entity / Contraption Freight Acceptance
Post-Flight Regional Specialization Sequence
Post-Flight Capability Payoff Audit
Copper / Zinc First-Route Placement Contract
Copper Fluid/Electrical Payoff Acceptance
Zinc Persistence / Brass / Capacitor / Levitite Acceptance
Electricity Conversion / Storage / CC Integration Acceptance
Silver / Electrum Dependency Resolution
Petroleum Distillation / Heavy-Engine Payoff Acceptance
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
21. Nether and End vanilla terrain generation are current implementation defaults, not permanent exceptions to Skyforge authorship.
22. Cross-dimension reuse should occur at the kernel/planning/provenance/ownership level while each dimension keeps a distinct semantic terrain grammar.
23. Prefer the outer End as the first cross-dimension pilot; use a solid-dominant Nether cavern province to test whether the architecture generalizes beyond suspended islands.
24. Do not rename/generalize the accepted SkyIsland APIs until a real second-domain implementation proves what abstraction is actually shared.
25. Dimension morphology is downstream of gameplay role, progression, traversal, resources, Aeronautics behavior, structures, and hazards.
26. Preserve and test Sable's existing dimension-pressure physics before adding bespoke End/Nether flight penalties.
27. Do not assume assembled Aeronautics craft can cross Nether portals, End portals, or End gateways; cross-dimension contraption transfer requires explicit proof.
28. Advanced reaction/ion propulsion should earn a specialized low-pressure/high-altitude role rather than replacing propellers everywhere.
29. End-derived Levitite should be treated as lift support, not self-contained propulsion: preserve the upstream no-free-climb behavior and test its low-speed handling cost.
30. Do not artificially post-Dragon-gate Levitite unless actual Dragon/outer-End play proves central-island access breaks the desired progression.
31. Dimension technology may deliberately combine resources from multiple worlds; prefer meaningful cross-domain production chains over isolated per-dimension tech trees.
32. The Nether should preserve mixed-mode route engineering: aircraft may solve suitable vault/lava crossings without making tunnels, rail, bridges, staging sites, and defended corridors obsolete.
33. If Create: Metallurgy remains selected, treat Wolframite/Tungsten as advanced foundry capability whose deposit scale follows real Obdurium/Industrial-Crucible demand.
34. Nether terrain morphology remains downstream of route, structure, resource, pressure, and recovery gameplay; enclosed cavern geometry is a leading hypothesis, not a locked aesthetic.
35. Full dimension realization requires durable gameplay value, not merely unique first-time loot or an attractive terrain concept.
36. Dimension value may take different forms: Overworld breadth/network permanence, Nether hostile operational depth, End expeditionary/specialist engineering depth.
37. Treat capital-unlock resources separately from recurring economic resources: current Tungsten/Obdurium demand and Levitite demand must be measured before using them as freight assumptions.
38. Do not claim Create Propulsion ion thrust as End-gated under current source; its present recipe lacks an End-specific input.
39. Exploration variation is a separate requirement from economic worth: difficult traversal must lead to behaviorally/structurally/resource-distinct discoveries.
40. Prefer at most one broad content-overhaul dependency per dimension in the first integrated prototype; add narrow structure/boss/ecology layers only when they fill non-overlapping roles.
41. Third-party dimension mods are content libraries, not semantic authorities: decompose mobs, structures, biomes, resources, loot, and global mechanics and assign KEEP/GOVERN/DISABLE/REDIRECT decisions.
42. Preserve local sparsity even with a large installed catalogue; End especially should gain contrast through rare high-value phenomena rather than dense biome/structure coverage.
43. Route semantics are capability- and payload-specific: personal reach does not imply bulk freight, and directed modes such as gliding require explicit return/recovery reasoning.
44. Generated civilization and player-built infrastructure should share the same visible route language; infrastructure roles should be semantic services rather than bespoke NPC-only mechanics.
45. Nether route difficulty must purchase meaningful destination value; End forward staging/navigation/recovery must measurably improve expedition capability.
46. Post-flight resources must be justified by engineering capability payoffs rather than nominal tiering or material rarity.
47. Preserve the current electrical asymmetry where useful: current CC&A Alternator is achievable without Brass while Electric Motor/storage/control branches are more mature and pull in Brass/capacitor/electrum dependencies.
48. Resolve the final pack's Silver/electrum path before treating Modular Accumulator as required progression; do not invent Silver geology by assumption.
49. Petroleum should create a sustained field->refinery->fuel-network loop; current Diesel Generators heated/superheated distillation and Brass-heavy engine progression are strong prototype evidence.
50. Treat Tungsten/Obdurium and Levitite as demand-measurement gates: special materials do not automatically imply recurring freight economies.
