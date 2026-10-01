# Working Mod and To-Build Ledger

**Snapshot:** 2026-09-29  
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
- Aerodynamics4MC promoted to leading provisional atmospheric-solver backend under Skyforge-owned climate/weather semantics
- AmbientSounds retained as environmental-audio content
- Sound Physics Remastered retained as acoustic propagation
- Particle Rain promoted to strong provisional precipitation presentation, contingent on consuming canonical Skyforge/A4MC wind rather than owning wind semantics
- Simple Clouds retained as an A/B cloud renderer only; its localized-weather authority must not supersede Skyforge/A4MC
- Weather2 / Expanded Weather2 Dynamics moved to severe-weather R&D/reserve rather than baseline atmospheric authority
- Wind Tunnel retained as a strong dev/validation tool

### Ecology
- Naturalist — strong core ordinary-fauna library under Skyforge habitat/population authority
- Fowl Play — strong core and preferred ordinary-bird implementation; flying fauna should consume the shared atmosphere contract
- Critters & Companions — selective strong keep for small-fauna / arthropod / companion niches; duplicate species and progression rewards audited independently
- Sky Whales — exceptional aerial-megafauna prototype; native spawning subordinated to Skyforge; whale-derived lift may be early, parallel, or specialist relative to Levitite pending playtesting
- Alex's Mobs — reserve species library pending species-level audit; both real and fantastical creatures may be admitted where they fill a coherent ecological, encounter, or world-texture niche

See [Creature Ecology Stack v0.1](creature-ecology-stack-v0.1.md) for the provisional ordinary-fauna libraries, Skyforge population authority, shared flying-fauna atmosphere contract, and Sky Whale progression caveat.

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
- Ice & Fire CE — strong provisional mythic/deep-sky content dependency; dragons, Stymphalian Birds, ghosts, hippogryphs/amphitheres, sea serpents, and selected roost/lair structures are in scope under Skyforge threat/geography authority; progression/taming/Dragonsteel remain independently audited
- Alex's Mobs — selected aerial/anomalous threat specialists including Guster, Farseer, Void Worm, Enderiophage, Soul Vulture, and Crimson Mosquito
- Bosses of Mass Destruction as a strong legendary prototype
- YUNG's Better Dungeons as a strong dungeon candidate
- YUNG's Better Mineshafts as a likely underground-history candidate
- In Control! as a generic/static integration safety layer

See [Deep-Sky and Mythic Threat Stack v0.1](deep-sky-threat-stack-v0.1.md) for the provisional above/between/below-island threat vocabulary, Ice & Fire CE role, and generic aircraft-target bridge requirement.

### Infrastructure / engineering
- CC:Tweaked + Create: Avionics retained as the baseline programmable aircraft-computing / avionics substrate
- Create: Radars promoted to strong provisional radar/track infrastructure; Create Aero Radars is the preferred Aeronautics turret-actuation bridge
- Create: Fire Control promoted to strong provisional advanced stabilization / EO / thermal / guided-weapon fire-control layer
- Mianbao's NewModernWarfare promoted to strong provisional aircraft-stores / guided-munition / APS / countermeasure / CIWS hardware library; duplicate direct-fire/radar content remains subject to suppression and recipe normalization
- CBC Neo Warfare promoted to strong provisional extension for medium/rotary cannon, specialist ammunition, launchers, armor, and ERA
- CBC Terminal Ballistics is the leading penetration/armor-interaction extension pending cross-mod runtime acceptance
- Drive By Wire, Tweaked Controllers, and Create Crafts & Additions Sable compatibility are strong provisional onboard-control/electrical integration components
- Create Diesel Generators promoted to strong keep for Skyforge-owned petroleum geography, refining, and compact dispatchable mechanical power
- SJE / Aero Propulsion promoted to strong provisional keep as the canonical advanced air-breathing engine family
- Create Propulsion: Simulated promoted to strong provisional keep as the canonical reaction/vector/solid/ion propulsion family; exact fuel/oxidizer/ion balance remains deferred
- Create Linear Bearing promoted to strong provisional physical linear-actuation candidate for Sable/Aeronautics machinery
- Aeronautics: No Horizon promoted to strong provisional primary heavy-ground locomotion layer for tracks, suspension, wheels, and oleo landing gear; Tracks+ retained as the A/B fallback
- Create Aeronautics: Transmission & Linkage promoted to strong provisional articulated-mechanism layer for hinges, universal joints, hydraulics, and linked machinery
- Create trains + Steam 'n' Rails retained as the preferred fixed-route rail locomotion/freight layer; Aeronautics Offroad remains the baseline wheeled-vehicle layer
- Create: Big Cannons retained; leading heavy-industry/artillery material and manufacturing spine
- Create: Metallurgy promoted to strong provisional keep for industrial melting, alloying, casting, molten handling, and foundry tooling; its native material tree is normalized rather than adopted wholesale
- Coke, Graphite, Refractory Mortar, and Slag retained as meaningful foundry process materials
- CBC remains semantic owner of Cast Iron/Bronze/Steel/Nethersteel; simpler CBC workshop routes remain provisionally accessible while Metallurgy provides industrial-scale alternatives
- Tungsten/Wolframite moved to reserve specialist status with native worldgen suppressed; Obdurium removed/hidden from player-facing progression
- Create: Metalwork moved to reserve/reference status; current preference is a thin Skyforge CBC↔Metallurgy compatibility datapack rather than adopting its broad processing/yield layer wholesale
- Create Crafts & Additions retained as the preferred single-electricity ecosystem; Silver geology excluded and Electrum retained only if integrated high-current demand proves useful
- Create: New Age reserved specifically as a future nuclear/advanced-heat candidate, not as a second electrical authority
- late-game logistics automation candidate
- Dedicated storage mods are provisionally omitted: Vanilla + Create storage/logistics are the default substrate. Sophisticated Storage/Backpacks remain reserve-only if playtesting exposes a concrete capacity, performance, or UX gap; freight-scale portable storage, nesting, and bulk-fluid backpacks remain disfavored.

See [Engineered Weapons Systems Stack v0.1](engineered-weapons-systems-stack-v0.1.md) for the current provisional combat-system authority map, recipe-normalization doctrine, and compatibility backlog.

See [General Engineering Power Stack v0.1](general-engineering-power-stack-v0.1.md) for the provisional mechanical/electrical/chemical/thermal authority model and deferred balance/compatibility work.

See [General Locomotion and Physical Actuation Stack v0.1](general-locomotion-stack-v0.1.md) for the provisional ground/rail/running-gear/joint authority model and locomotion compatibility backlog.

### Ordinary life/building
- Farmer's Delight promoted to strong keep as the ordinary food/agriculture substrate
- Create: Central Kitchen promoted to strong provisional keep as the preferred Create/Farmer's Delight automation bridge; exact Dragons Plus dependency surface remains implementation-time acceptance work
- Supplementaries promoted to strong keep under curated configuration: ordinary-life/building/utility assets retained while duplicate pulley/cannon/navigation/weather/worldgen authorities are disabled or intercepted
- Slice & Dice moved to reserve/A-B status, primarily for distinct agricultural machinery such as sprinklers where Central Kitchen does not already cover the processing verb
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
- Create: Structures Arise — strong provisional industrial/technical/history structure library; selected assets such as fluid tanks, crusher/crane sites, containers, mine trains, lost stations, windmills, and airborne assets should be admitted semantically rather than through unrestricted native generation
- Creat Aeronautics Structures (MIT project) — strong provisional aviation/history structure library for engineering ruins, abandoned boats, airships, balloons, and related world texture
- Create Aeronautics Discovery — strong integration prototype for datapack-defined physical aircraft prefabs, flyovers, structure-linked patrols, and reusable autopilot realization under Skyforge civilization/faction authority
- Aeronautical Explorations — strong narrow external candidate for abandoned airstrip and ruined petrol-station roles; ARR and governance seam require runtime acceptance
- Radio Towers Lite — strong narrow MIT candidate for navigation/weather/radar tower shells
- Create Aeronautics Structures (ARR project) — reserve prefab/repairable-craft source; avoid stacking if MIT Aeronautics Structures + Discovery cover the role
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
- Abandoned Structures: Aviation — reserve; valid aviation-ruin library but currently redundant with retained Aeronautics structure sources
- Create: Sky Village — reserve only; detached-settlement content currently overlaps Structures Arise / Towns & Towers / Aeronautics assets
- Moog's Voyager Structures — reserve asset quarry for individual religious/navigation/agricultural/maritime roles, not unrestricted catalogue generation
- Currents of Trade — future maritime R&D candidate; too immature for current lock
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

- airfield site-plan / approach-clearance geometry;
- cliff-port / hanging-dock anchor grammar;
- underside-installation anchor/orientation grammar;
- mooring / route-beacon site plans;
- weather/radar functional overlays using retained tower and systems content.

Current audit no longer justifies bespoke block sets or broad bespoke building catalogues for these roles. See [Structure Content Gap and Reuse Audit v0.1](structure-content-gap-and-reuse-audit-v0.1.md) and [Structure Content Stack v0.1](structure-content-stack-v0.1.md) for the provisionally closed structure dependency/authority policy..

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
CBC Steel / Nethersteel Heavy-Industry Acceptance
CBC Melt -> Cast -> Bore -> Build-Up Manufacturing Acceptance
CBC Ammunition / Guncotton / Nitro Throughput Acceptance
Create: Metallurgy Foundry A/B
Metallurgy-to-CBC Molten-Fluid Tag Bridge Acceptance
Unified Steel Identity / Conversion-Loop Acceptance
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
Material and Process Retention Audit
Unified Industrial Production Graph
Recipe and Material Normalization Backlog
Exact Recipe / Worldgen Collision Manifest
Wave C1 Integrated Engineering Prototype Acceptance
Canonical Shared-Material Tag/Recipe Audit
Create Big Cannons Industrial Integration Audit
Copper / Zinc First-Route Placement Contract
Copper Fluid/Electrical Payoff Acceptance
Zinc Persistence / Brass / Capacitor / Levitite Acceptance
Electricity Conversion / Storage / CC Integration Acceptance
Silver Exclusion / Electrum High-Current Resolution
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
33. Do not author Wolframite or require Tungsten/Obdurium under the current stack; Create: Metallurgy must earn retention through foundry gameplay rather than its material tree.
34. Nether terrain morphology remains downstream of route, structure, resource, pressure, and recovery gameplay; enclosed cavern geometry is a leading hypothesis, not a locked aesthetic.
35. Full dimension realization requires durable gameplay value, not merely unique first-time loot or an attractive terrain concept.
36. Dimension value may take different forms: Overworld breadth/network permanence, Nether hostile operational depth, End expeditionary/specialist engineering depth.
37. Treat capital materials separately from recurring logistics: CBC cannon metals may create large one-time builds while ammunition/fuel may create the stronger continuing freight demand; Levitite demand still requires measurement.
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
48. Silver is excluded as a Skyforge raw resource. Electrum may remain only as a manufactured high-current electrical tier if integrated throughput testing justifies it; otherwise normalize required electrical recipes onto retained materials.
49. Petroleum should create a sustained field->refinery->fuel-network loop; current Diesel Generators heated/superheated distillation and Brass-heavy engine progression are strong prototype evidence.
50. CBC Cast Iron/Bronze/Steel/Nethersteel are the leading heavy-industry material ladder because they alter weapon engineering without requiring new ores; Tin is not required because CBC provides a tinless Bronze route.
51. Superheating remains retained independently of Create: Metallurgy through Create power/process uses, improved petroleum refining, and CBC Nethersteel.
52. If Create: Metallurgy survives A/B testing, bridge its molten Steel/other shared metals into CBC common fluid tags and normalize duplicate Steel recipes rather than maintaining parallel metal silos.
53. CBC on Sable/Aeronautics craft requires explicit manual acceptance for recoil, moving-fire behavior, reload/fuze automation, HE destruction, save/reload, and crash recovery before showcase.
54. Prefer CBC's existing mass/recoil/ammunition/stability costs for aircraft artillery balance before adding bespoke airborne-weapon penalties.
55. Skyforge owns the canonical material/process vocabulary: retained mods are producers and consumers of one economy, not independent progression trees.
56. Prefer manufactured complexity over new geology; rejected Silver/Tin/Platinum/Wolframite/Tungsten/Obdurium may not silently return as required dependencies.
57. Recipe integration should proceed in order: common tags -> datapack recipes -> configs/worldgen overrides -> presentation cleanup -> thin adapters -> Java only when a real behavioral incompatibility remains.
58. Build a machine-readable capability-closure verifier after the integrated pack prototype so upstream mod updates cannot silently break first flight, CBC metals, superheat, electrical storage, petroleum, or Levitite closure.
59. Reliable Gliders atmosphere integration should use NeoForge EntityTickEvent.Post rather than PlayerTickEvent.Post: the former observes the completed Player.tick after Reliable Gliders' tail velocity mutation.
60. Treat A4MC updraft as a physical vertical-air velocity in m/s. The first glider mapping is `updraft / 20 - 0.05 blocks/tick`, preserving Reliable Gliders' ~1 m/s baseline sink and naturally aligning the A4MC cap with the stock block-updraft scale.
61. In the first shared-lift prototype, atmospheric lift may raise the final Reliable-Gliders vertical result but must not blindly add to an already stronger block-heat updraft.
62. Atmospheric truth and ecology ownership remain separate: thermals may influence soaring behavior but may not independently spawn soaring fauna.
63. Human-eye review is not a correctness gate for atmosphere/glider integration; ordering, authority, unit conversion, composition, and shared-consumer provenance require automated evidence. Human play is reserved for perceptual tuning.
64. Fowl Play's existing red-tailed hawk is the leading THERMAL_SOARER realization; do not create a bespoke Skyforge hawk entity unless the external SmartBrain integration seam fails.
65. SmartBrainLib 1.16.11 supports post-construction activity insertion and schedule replacement, so the leading hawk integration is an optional compat hook rather than a Fowl Play fork or entity replacement.
66. Thermal soaring should substitute SOAR into the stock raptor HUNT windows only while trusted useful lift exists; preserve stock idle/rest windows and higher-priority avoid/fight/interaction behavior.
67. Thermal presence may alter movement choice for an already-admitted hawk but may not create population. Ecology remains the sole species/population authority.
68. Soaring-fauna route selection should reuse a low-frequency shared A4MC lift cache; do not perform independent wide atmosphere scans per bird per tick.
69. Vehicle combat is one engineered system: Sable owns rigid-body truth, CBC/CBC-family mods own conventional ballistic hardware, Radars owns preferred tracks, Avionics + CC:Tweaked own general programmable aircraft I/O, and Fire Control is the preferred advanced weapon-electronics layer.
70. Mianbao is a selected military-hardware/content library rather than an independent industrial authority; normalize its recipes onto the canonical Create/CBC/CC&A economy and suppress same-tier duplicates where stronger native Create/CBC implementations exist.
71. Cross-mod defense interoperability is an acceptance gate, not an assumption. APS, ERA, countermeasures, CIWS, radar/IFF, missile guidance, and terminal ballistics must be tested against foreign projectile classes before production lock.


## 2026-09-30 structure-stack closure

The general V1 structure-content stack is **provisionally closed**.

Key policy:
- structure assets are vocabulary, not semantic authority;
- acquisition progression may be bypassed through earned salvage/capture/trade without requiring every player to manufacture everything;
- operating/support economies remain meaningful;
- asset-level curation is independent of dependency-level retention;
- content richness does not increase realized density;
- Create Aeronautics Discovery is a physical-realization substrate under Skyforge mission/civilization authority;
- airfield topology, cliff docks, underside sites, mooring/beacon plans, and weather/radar overlays remain Skyforge-owned composition/realization work;
- visual coherence is a later production gate.

See [Structure Content Stack v0.1](structure-content-stack-v0.1.md).
