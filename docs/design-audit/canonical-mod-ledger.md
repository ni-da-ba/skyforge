# Canonical Mod Ledger

**Snapshot:** 2026-10-02  
**Target backend:** Minecraft 1.21.1 / NeoForge  
**Authority:** Canonical design-selection/status surface for third-party Minecraft dependencies in Project Skyforge.  
**Release status:** This is a **design roster**, not yet the public-alpha redistribution lock. Version pinning, licenses, transitive dependencies, and exact runtime compatibility are reverified before pack assembly under HS-10.

**Execution sequence:** [Staged Mod Integration Strategy](staged-mod-integration-strategy.md). The stack is integrated as launchable slices; unresolved A/B candidates are comparison overlays rather than automatic cumulative dependencies.

When this file conflicts with older design-audit candidate language, **this file wins** unless a newer accepted lane-state milestone, source/test result, or explicit owner decision supersedes it.

The purpose of this ledger is to end repeated mod discovery. Skyforge now has enough content vocabulary. New dependency searches require a **specific demonstrated capability gap**, a hard incompatibility in the selected solution, or a distribution/licensing blocker.

## Decision vocabulary

| State | Meaning |
| --- | --- |
| **KEEP** | Current design selection. Do not reopen without evidence. General version/license/runtime gates still apply. |
| **PREFERRED** | Leading solution; bounded integration/play proof remains before KEEP. |
| **A/B** | Genuine live choice between named alternatives or keep/remove. |
| **CONDITIONAL** | Add only if the stated gap appears in integrated play. |
| **RESERVE** | Known fallback/R&D source; no active adoption work. |
| **REJECT** | Excluded from the main Skyforge stack under the current design. |
| **DEV** | Development/validation aid rather than player-facing dependency. |

## Global dependency rules

1. **Skyforge owns meaning.** Mods provide assets, mechanics, and realization vocabulary; they do not independently own macro geography, ecology, resource geography, civilization placement, or population density.
2. **One authority per physical quantity.** Wind, pressure, weather, resource identity, radar tracks, etc. may have many consumers but one semantic source.
3. **One game across dimensions.** Nether and End content extends the same Create/Farmer's Delight/CBC/electrical/logistics economy instead of creating parallel technology trees.
4. **Installed vocabulary may be large; realized density stays sparse.** Dependency count does not authorize biome confetti, structure spam, or mob soup.
5. **Prefer reuse over bespoke code:** vanilla -> retained mod -> config/datapack/tag integration -> thin adapter -> bespoke system.
6. **Do not add a mod solely to preserve its stock recipes/material ladder.** Normalize recipes and shared materials into the canonical Skyforge economy.
7. **No new broad Overworld/Nether/End content packs** until integrated testing identifies a concrete missing semantic role.
8. **Public-alpha hard-dependency status is separate from design selection.** HS-05/HS-10 still own scope, redistribution, update policy, and dependency-budget decisions.

# Unresolved decision queue

These are the remaining decisions that can actually change the dependency roster. Everything else in the audit corpus is integration, tuning, placement, recipe, or balance work.

| ID | Decision | Current direction | Close when |
| --- | --- | --- | --- |
| D-01 | Authoritative atmosphere backend | **Aerodynamics4MC PREFERRED** | Pinned-stack authority/performance proof is acceptable. |
| D-02 | Cloud renderer / enhanced visual profile | **RESOLVED: Simple Clouds selected as the renderer foundation; Better Clouds is RESERVE** | Owner confirmed Simple Clouds rendering with Distant Horizons in the pinned S1 profile (2026-10-03). Semantic Skyforge weather/climate mapping remains technical integration work. |
| D-03 | Early-game glider | **RESOLVED: PROMOTE Reliable Gliders for the early-game role; KEEP Create: Ornithopter Glider as a later-game Elytra-gated tool; REJECT Hang Glider** | Owner accepted Reliable Gliders as good for the early-game role after the integrated S1 review (2026-10-03). Reuse WBY-INT-0004 compatibility evidence. Any later recipe/balance/quest tuning is separate. |
| D-04 | Heavy ground running gear | **Aeronautics: No Horizon vs Tracks+ A/B** | Runtime handling, Sable integration, performance, distribution fit. |
| D-05 | General foundry depth | **Create: Metallurgy keep/remove A/B** | It must materially improve industrial play enough to justify machinery + normalization work. |
| D-06 | Automated aircraft logistics | **Create:Aero Automated Logistics A/B** | Pinned-stack route/persistence/cargo/contention proof; no geography teleportation. |
| D-07 | Ordinary landmark library | **Structory vs Explorify vs both A/B** | Integrated role/visual overlap under Skyforge density control. |
| D-08 | Alpha Nether terrain substrate | **Vanilla/control vs Jaden's/Mosaic vs Incendium A/B** | Physical cavern/vault/chasm/lava-basin quality and governability; temporary substrate only. |
| D-09 | Bastion overhaul | **Better Bastions A/B vs governed vanilla fallback** | Stability, structure identity, placement/worldgen interoperability. |
| D-10 | Wither overhaul | **Wither: Reincarnated A/B vs current fight** | Behavioral quality, co-op scaling, engineering counterplay, compatibility. |
| D-11 | Piglin behavioral depth | **Piglin Proliferation CONDITIONAL** | Add only if Eternal Nether + bastion stack still feels behaviorally thin. |
| D-12 | End dragon encounter | **multi-candidate A/B slate** | Realistic late-Nether solo/2p/4p playtests; arena compatibility and engineering counterplay. |
| D-13 | End broad terrain realization | **Nullscape PREFERRED instrument; Stellarity RESERVE control branch** | Controllability audit proves Skyforge morphology cannot be overridden. |
| D-14 | Optional XP/enchantment industrial bridge | **Create: Enchantment Industry A/B** | Economy test proves value without reducing exploration to generic XP farming. |
| D-15 | Quest presentation depth | **FTB Quests KEEP as authored milestone/journal layer; vanilla advancements remain fallback/complement** | Remaining decision is content depth/alpha scope, not whether the dependency is useful. |
| D-16 | Distant Horizons packaging | **Design KEEP; hard public-alpha dependency unresolved** | HS-05/HS-10 packaging decision. |
| D-17 | Personal firearms | **TaCZ family vs Scorched Guns NeoForge A/B** | Moving-Sable projectile behavior, NPC use, ammo economy, content pruning, performance. |
| D-18 | Radar / terminal ballistics | **Create: Radars and CBC Terminal Ballistics remain active acceptance A/Bs** | Shared track/IFF/projectile authority and cross-mod interoperability. |
| D-19 | Small field backpack | **No backpack by default; bounded 9–18 slot field pack CONDITIONAL** | Only if expedition inventory playtest demonstrates a real UX gap without erasing freight. |
| D-20 | Broad performance additions | **ModernFix / Create: Catalyst / StellarCreateOptimization / Entity Culling are A/B or RESERVE overlays** | Promote individually only after correctness + benchmark evidence on cumulative stack. |

# Core runtime, movement, rendering

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| Create | **KEEP** | Core mechanical/industrial language. | Pin/version compatibility. |
| Sable | **KEEP** | Vehicle rigid-body/contraption runtime. | Pin/version compatibility. |
| Create Aeronautics | **KEEP** | Core aircraft/Levitite vehicle layer. | Pin/version compatibility. |
| Distant Horizons | **KEEP** | Long-range world presentation essential to Skyforge scale. | Whether it is a hard Alpha dependency remains HS-05/HS-10. |
| Separate Sable distant-contraption renderer/equivalent | **CONDITIONAL** | Distant moving-vehicle visibility if DH does not solve it. | Add only after concrete visibility gap. |
| Reliable Gliders | **PROMOTE** | Owner-approved early-game personal soaring role; existing WBY-INT-0004 compatibility evidence is reused. | No open selection gate. Recipe/balance/quest tuning remains separate. |
| Hang Glider | **REJECT** | Rejected by the owner; excluded from the active S1 roster. | No further test unless the owner reopens the choice. |
| Create: Ornithopter Glider | **KEEP** | Owner-selected later-game mobility tool; requires an Elytra to use. | No open selection gate. Balance/availability tuning remains a separate owner decision. |
| No More Elytra Boosting | **KEEP** | Removes safe firework propulsion while preserving normal fireworks/fall-flying. | Accepted pinned runtime; only human clarity remains. |
| Disable Elytra Outside The End | **RESERVE** | Alternative boost-control dependency. | Use only if selected solution fails distribution/runtime needs. |
| Elytra Tuning | **RESERVE** | Non-binary boost tuning fallback. | Only if full suppression feels wrong. |
| Gliders by Jeryn | **RESERVE** | Richer glider progression. | Avoid unless a real missing capability appears. |
| Aeronautics: No Horizon | **A/B** | Preferred tracks/suspension/wheels/oleo landing gear. | D-04. |
| Tracks+ | **A/B** | Heavy-ground fallback. | D-04. |
| Aeronautics Offroad | **KEEP** | Baseline wheeled-vehicle layer. | Integration tuning. |
| Create trains + Steam 'n' Rails | **KEEP** | Fixed-route rail/freight. | Integration tuning. |
| Create Linear Bearing | **PREFERRED** | Physical linear actuation. | Runtime usefulness/overlap. |
| Create Aeronautics: Transmission & Linkage | **PREFERRED** | Hinges/U-joints/hydraulics/articulated machinery. | Runtime usefulness/overlap. |

# Atmosphere, weather, sound, survival

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| Aerodynamics4MC | **PREFERRED** | Atmospheric/wind solver beneath Skyforge semantic climate/weather. | D-01. |
| Particle Rain | **PREFERRED** | Precipitation presentation consuming canonical wind. | Must not own wind/weather semantics. |
| Better Clouds | **RESERVE** | Known fallback/reference cloud renderer. | Revisit only if Simple Clouds cannot support an evidenced capability or compatibility need. |
| Iris | **OPTIONAL** | Enhanced shader profile only. | Never required for semantic readability. |
| Iris Flywheel Compat / Iris Veil Compat / Create Shader Fixes | **CONDITIONAL** | Compatibility layer only if Iris profile needs them. | Add only with accepted shader profile. |
| Sound Physics: Aeronautics | **KEEP** | Moving-sublevel-aware acoustics / attenuation / flyby behavior. | Cumulative presentation stability. |
| Presence Footsteps | **PREFERRED** | Material-aware movement audio. | Client compatibility/performance. |
| True Adaptive Music | **PREFERRED** | Runtime delivery for Skyforge-authored adaptive soundtrack. | Music-state integration. |
| Simple Clouds | **PREFERRED** | Selected cloud-renderer foundation for Skyforge weather presentation; owner confirmed it renders with Distant Horizons in the pinned S1 profile. | Technical mapping from authored Skyforge weather/climate into Simple Clouds remains unproven and needs a bounded integration design. |
| Weather2 / Expanded Weather2 Dynamics | **RESERVE** | Severe-weather R&D/content source. | Not baseline authority. |
| AmbientSounds | **RESERVE** | Possible ambient-audio delivery layer. | Use only if the selected presentation stack leaves a real ambience gap. |
| Sound Physics Remastered | **RESERVE** | Generic acoustic propagation fallback. | Prefer the Aeronautics-aware acoustic layer where it covers the role. |
| Wind Tunnel | **DEV** | Atmosphere/flight validation. | Not player-facing requirement. |
| ThinAir: ReLived | **PREFERRED** | Breathability/respiration frontend; especially End and selected hostile Nether regions. | Cheap Skyforge-field coupling if practical; categorical fallback acceptable for Alpha. |
| Create: FlyHigher | **PREFERRED** | Aeronautics pressure/atmosphere behavior frontend. | Verify pressure-curve integration and overlap with Sable/A4MC. |
| Create: Deep Seas | **CONDITIONAL** | Sealed-hull/oxygen/pressure-differential R&D, underwater plus possible arbitrary atmospheres. | Keep only if generalized pressure behavior is cheap and useful. |
| Cold Sweat | **RESERVE** | Continuous body-temperature simulation. | Add only if localized heat hazards prove insufficient. |
| Airborne / pollution systems | **RESERVE** | Industrial-pollution R&D. | Do not confuse pollution with natural Nether atmospheric chemistry. |
| Serene Seasons | **RESERVE** | Seasonal presentation/ecology. | No current capability gap. |

# Overworld ecology and biome vocabulary

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| Naturalist | **KEEP** | Ordinary-fauna library under Skyforge habitat/population authority. | Species-level tuning only. |
| Fowl Play | **KEEP** | Preferred ordinary-bird/raptor implementation; shared atmosphere consumer. | Soaring behavior tuning. |
| Critters & Companions | **KEEP (selective)** | Small fauna/arthropod/companion niches. | Cull duplicates/progression side effects. |
| Sky Whales | **PREFERRED** | Exceptional aerial megafauna. | Progression/lift role playtest. |
| Alex's Mobs | **KEEP (selective)** | Specialist ecological/threat vocabulary only. | No blanket native ecology/worldgen authority. |
| Birds/Boids Reforged | **RESERVE** | Flocking alternative. | Only if retained bird behavior is inadequate. |
| Hybrid Birds | **RESERVE** | Bird variety. | No current gap. |
| Biomes O' Plenty | **PREFERRED** | Broad ecological/material vocabulary; also selected End content. | Integration/duplication curation. |
| Regions Unexplored | **PREFERRED** | Broad Overworld/Nether ecological/material vocabulary. | Integration/duplication curation. |
| Nature's Spirit | **PREFERRED** | Additional ecological/material vocabulary. | Integration/duplication curation. |
| Terralith | **A/B / instrument** | Meso-scale morphology donor/reference, never macro-authority. | Density-function/noise-router controllability audit. |
| Ecologics | **REJECT** | Insufficient value relative to overlap. | Reopen only for a concrete missing asset. |
| Environmental | **REJECT** | Insufficient value relative to overlap. | Reopen only for a concrete missing asset. |

# Hostiles, bosses, and encounter vocabulary

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| Friends & Foes | **KEEP** | Vanilla-adjacent hostile/creature vocabulary. | Curation. |
| It Takes a Pillage Continuation | **KEEP** | Organized Illager hostile-faction depth. | Curation. |
| Illager Structures | **KEEP** | Illager faction structure vocabulary. | Skyforge placement/density. |
| Mowzie's Mobs | **KEEP** | Territorial elites/minibosses. | Encounter placement. |
| Ice & Fire CE | **PREFERRED** | Mythic/deep-sky dependency: dragons, Stymphalian Birds, selected fauna/ruins. | Dragonsteel/taming/loot/progression separately normalized. |
| Bosses of Mass Destruction | **PREFERRED** | Rare legendary boss prototype. | Current-target compatibility/runtime. |
| Cataclysm | **RESERVE** | Rare legendary/boss library. | Use only where a distinct role remains. |
| In Control! | **KEEP** | Static spawn-orchestration safety/integration layer. | Skyforge remains semantic population authority. |
| Hostile Harmony | **CONDITIONAL** | Data-driven mob relationship layer. | Only if stable and materially useful. |
| Illager Invasion | **RESERVE** | Additional Illager content. | Current faction stack likely sufficient. |
| Creeper Overhaul | **RESERVE** | Creature variants. | No current gap. |
| Enderman Overhaul | **CONDITIONAL** | End behavior/reward variety. | Mobility/pearl side effects and overlap with richer End stack. |
| Rotten Creatures | **RESERVE** | Undead variety. | No current gap. |
| Born in Chaos | **REJECT** | Threat-density philosophy conflicts with Skyforge. | — |

# Engineering, industry, weapons, control

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| CC:Tweaked | **KEEP** | Programmable computing, GPS, logistics/control substrate. | Accepted executable Content evidence. |
| Create: Avionics | **KEEP** | Programmable aircraft I/O with CC:Tweaked. | Accepted executable Content evidence. |
| Create: Radars | **PREFERRED** | Preferred radar/track infrastructure. | Cross-mod track authority. |
| Create Aero Radars | **PREFERRED** | Aeronautics turret-actuation bridge. | Cross-mod runtime. |
| Create: Fire Control | **PREFERRED** | Advanced stabilization, EO/thermal, target lock/guided weapon control. | Must consume/defer to selected sensor authority. |
| Mianbao's NewModernWarfare | **PREFERRED** | Aircraft stores, guided munitions, APS/countermeasures/CIWS hardware. | Suppress duplicate direct-fire/radar/economy paths. |
| Create: Big Cannons | **KEEP** | Heavy artillery + industrial material/manufacturing spine. | Balance/aircraft interoperability. |
| CBC Neo Warfare | **PREFERRED** | Medium/rotary cannon, specialist ammo, launchers, armor, ERA. | Runtime/interoperability. |
| CBC Terminal Ballistics | **PREFERRED** | Penetration/armor interaction. | Cross-mod projectile acceptance. |
| Drive By Wire | **PREFERRED** | Onboard control integration. | Pinned runtime. |
| Tweaked Controllers | **PREFERRED** | Onboard control integration. | Pinned runtime. |
| Create Crafts & Additions Sable compatibility | **PREFERRED** | Vehicle/electrical bridge. | Pinned runtime. |
| Create Diesel Generators | **KEEP** | Petroleum/refining/compact dispatchable power. | Skyforge owns petroleum geography. |
| SJE / Aero Propulsion | **PREFERRED** | Advanced air-breathing engine family. | Recipe/performance integration. |
| Create Propulsion: Simulated | **PREFERRED** | Reaction/vector/solid/ion propulsion; specialized low-pressure role. | Do not make it an End gate without evidence. |
| Create Crafts & Additions | **KEEP** | Single electrical ecosystem. | Electrum recipe normalization if needed. |
| Create: Metallurgy | **A/B** | Optional foundry mechanics, not material/worldgen authority. | D-05. |
| Create: Metalwork | **RESERVE** | Reference/processing layer. | Prefer thin CBC↔Metallurgy integration instead. |
| Create: New Age | **RESERVE** | Future nuclear/advanced-heat candidate. | No current need. |
| Create:Aero Automated Logistics | **A/B** | Automated aircraft routes/logistics. | D-06. |

## Canonical industrial-material rule

CBC remains the player-facing heavy-industry owner of **Cast Iron / Bronze / Steel / Nethersteel**.  
Silver geology is excluded. Tin is not required. Wolframite/Tungsten are not planned progression resources. Obdurium is excluded. Electrum is retained only if integrated electrical testing proves a useful manufactured high-current tier.

# Ordinary life, food, building, exploration

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| Farmer's Delight | **KEEP** | Common food/agriculture substrate. | — |
| Create: Central Kitchen | **PREFERRED** | Preferred Create/Farmer's Delight automation bridge. | Transitive dependency/runtime acceptance. |
| Slice & Dice | **CONDITIONAL** | Agricultural automation only where Central Kitchen leaves a real verb gap. | Integrated recipe coverage. |
| Supplementaries | **KEEP (curated)** | Ordinary-life/building/utility vocabulary. | Disable competing pulley/cannon/navigation/weather/worldgen authorities. |
| Brewin' & Chewin' | **PREFERRED** | Fermentation/brewing/preservation/cheese extension to common food economy. | Dependency/recipe overlap. |
| Cultural Delights | **PREFERRED** | Regional crops/cuisine/trade vocabulary. | Skyforge owns crop geography; suppress autonomous placement where needed. |
| Farmer's Respite | **RESERVE** | Tea/beverage extension. | Reconsider only with a stable target build and a concrete role. |
| Artifacts | **PREFERRED** | Exploration rewards. | Progression/loot curation. |
| Lootr | **CONDITIONAL** | Multiplayer loot handling. | Add if multiplayer structure-loot contention warrants it. |
| Quark | **RESERVE** | General utility/content. | No current capability gap. |

# Structures, civilization, archaeology

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| More Villagers | **PREFERRED** | Additional civilian profession/trade vocabulary. | Trade-economy curation. |
| Guard Villagers | **KEEP** | Maintained-settlement defense / armed civilian vocabulary. | Weapon-stack integration. |
| Easy NPC | **PREFERRED** | Named/authored/special-purpose actors only. | Do not use as general population simulator. |
| CTOV | **PREFERRED (curated)** | Additional civilian settlement vocabulary. | Native generation subordinated; retain only if role split with Towns & Towers stays legible. |
| Towns & Towers | **PREFERRED** | Civilian village/settlement vocabulary. | Terrain/visual fit. |
| Structory | **A/B** | Ordinary landmark vocabulary. | D-07. |
| Explorify | **A/B** | Ordinary landmark vocabulary. | D-07. |
| YUNG's Better Dungeons | **PREFERRED** | Expanded dungeon vocabulary. | Exact-volume integration. |
| YUNG's Better Mineshafts | **PREFERRED** | Historical extraction/subsurface mine vocabulary. | Exact-volume integration. |
| Selected YUNG temple/hut/monument modules | **CONDITIONAL** | Replace weak vanilla structures only where a retained role exists. | Role-by-role. |
| Create: Structures Arise | **PREFERRED (curated)** | Industrial/technical/history asset library. | Admit selected assets semantically; no unrestricted native density. |
| Create Aeronautics Structures — MIT project | **PREFERRED** | Aviation/history ruins and engineering-world texture. | Distribution/runtime. |
| Create Aeronautics Discovery | **PREFERRED** | Physical aircraft prefabs/flyovers/structure-linked patrol realization. | Skyforge retains mission/faction authority. |
| Aeronautical Explorations | **PREFERRED** | Narrow abandoned airstrip/petrol-station roles. | ARR/distribution + governance seam. |
| Radio Towers Lite | **PREFERRED** | Navigation/weather/radar tower shells. | Runtime/visual fit. |
| Create Aeronautics Structures — ARR project | **RESERVE** | Prefab/repairable craft source. | Avoid if preferred MIT sources cover role. |
| Moog's Voyager Structures | **RESERVE** | Asset quarry for isolated roles. | Never unrestricted 100+ structure generation. |
| Repurposed Structures | **RESERVE** | Selective variants. | Concrete gap only. |
| When Dungeons Arise | **CONDITIONAL (curated subset)** | Exceptional landmark vocabulary. | Never unrestricted worldgen. |
| Dungeons & Taverns | **RESERVE** | Structure variety. | No current gap. |
| CTOV | **RESERVE** | Settlement alternative. | Use only if Towns & Towers fails. |
| Create: Sky Village | **RESERVE** | Detached settlement reference/assets. | Currently redundant. |
| Abandoned Structures: Aviation | **RESERVE** | Aviation ruins. | Currently redundant. |
| Currents of Trade | **RESERVE** | Future maritime content. | Too immature/currently out of scope. |
| IDAS | **REJECT** | Broad integration/worldgen layer conflicts with Skyforge's role. | — |

# Nether — canonical roster

The Nether acquisition pool is **frozen after the closure tests below**. Its design identity is hostile industrial operation through coherent cavern/magmatic geography, not a pile of independent biome mods.

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| BetterNether: New Dawn | **PREFERRED** | Primary broad Nether ecology/material/content library. | Suppress/govern native worldgen where it conflicts with Skyforge; dependency surface A/B. |
| Jaden's Nether Expansion / Mosaic | **A/B substrate** | Temporary Alpha regional/worldgen substrate and content source. | D-08; external dependency only under license. |
| Incendium | **A/B substrate** | Alternative physical-Nether terrain prototype. | D-08; do not blindly stack with another broad substrate. |
| Eternal Nether | **KEEP (curated)** | Piglin Manor/Citadel/Catacomb + associated threat vocabulary. | Semantic placement/density. |
| Nether Depths Upgrade | **PREFERRED** | Lava-sea ecology and exploration vocabulary. | Test overlap/performance. |
| Luminous: Nether | **CONDITIONAL** | Extra fauna/rare/legendary threat vocabulary. | Add only if post-substrate ecology/threat stack has a real niche gap. |
| My Nether's Delight | **PREFERRED** | Nether-side Farmer's Delight sustainment/economy. | Recipe/ecology overlap. |
| Create: Nether Industry | **PREFERRED** | Nether-specific inputs/processes folded into common Create economy. | Recipe graph, soul semantics, Nylium farming, bypass risk. |
| YUNG's Better Nether Fortresses | **PREFERRED** | Progression-critical fortress/Blaze structure overhaul. | Authored-terrain compatibility; preserve fortress/Blaze semantics. |
| Better Bastions | **A/B** | Bastion replacement/expansion. | D-09. |
| Wither: Reincarnated | **A/B** | Focused Wither encounter overhaul. | D-10. |
| Piglin Proliferation | **CONDITIONAL** | Additional Piglin behavioral roles. | D-11. |
| ThinAir: ReLived | **PREFERRED** | Unbreathable/hazardous Nether regions where justified. | Same canonical pressure/breathability contract as End. |
| Create: Netherless | **REJECT** | Bypasses geographic reason to enter/use Nether. | — |

**Nether progression rule:** Blaze Rods remain dimension-bound and progression-critical. Difficulty should come from route, geography, fortress realization, combined threats, environment, access/egress, and preparation—not inflated HP, drop grind, or arbitrary anti-farm rules. Industrialized Blaze production after the player conquers and infrastructures the route is acceptable.

# End — canonical roster

The End content pool is also **frozen for breadth**. Integration decides what is realized; do not keep adding biome/structure packs.

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| Nullscape | **PREFERRED instrument** | Primary terrain/morphology realization candidate/library. | Must not override explicit Skyforge morphology; density/noise/router controllability audit. |
| BetterEnd: New Dawn | **PREFERRED** | Primary broad ecology/material/biome/content library. | Treat imported progression as vocabulary, not accepted tech tree. |
| Biomes O' Plenty — End content | **PREFERRED (selective)** | Additional End ecology/material vocabulary. | Skyforge placement/density. |
| End's Phantasm | **PREFERRED** | Distinctive ecology, under-island vocabulary, crystals/resources. | Integration/overlap. |
| Unusual End | **PREFERRED (supplementary)** | Hazards, creatures, resources, structures, encounter mechanics. | Prevent native worldgen/biome authority from overriding Skyforge. |
| MES / Moog's End Structures | **PREFERRED** | Broad data-driven structure vocabulary. | Semantic placement/rarity. |
| YUNG's Better End Island | **PREFERRED** | Central dragon-island/set-piece realization. | Must yield if it blocks a superior dragon fight. |
| YUNG's Better Strongholds | **PREFERRED** | Ancient portal-route/stronghold overhaul. | Progression compatibility. |
| End's Delight | **PREFERRED** | End expedition sustainment through common food economy. | Recipe/progression overlap. |
| Farmer's Cutting: BetterEnd | **PREFERRED** | Lightweight BetterEnd/Farmer's Delight compatibility. | Recipe coverage/duplicates. |
| Enderman Overhaul | **CONDITIONAL** | Behavior/reward diversity. | Special-pearl mobility risk and redundancy. |
| Stellarity | **RESERVE control branch** | Coherent all-in-one alternative if modular branch fails. | Conflicts with BetterEnd; not part of preferred additive stack. |

## End dragon encounter A/B slate

No single dragon mod is locked yet. This is a **behavioral encounter decision**, not another broad End-content search.

| Candidate | State | Intended use |
| --- | --- | --- |
| Beyond Bosses 2 | **A/B** | Multi-stage/behavioral candidate; broad world changes must be governable. |
| True Ending | **A/B** | Behaviorally ambitious candidate; arena conflict must be tested. |
| Ender Dragon Fight Remastered | **A/B** | Conservative behavior/phase baseline. |
| Savage Ender Dragon | **A/B / supplement** | Scaling/pressure/anti-cheese; insufficient alone, anti-flight must not invalidate aviation. |
| MCS Ender Dragon / Better Ender Dragon | **A/B component** | Evaluate configurable mechanics/components. |
| Ender Trigon / Progressive Bosses | **RESERVE** | Future/reference unless compatible current ports exist. |

**End encounter contract:** the first Ender Dragon is a capstone capability exam for late-Overworld/Nether mastery and a gate into outer-End expedition play, not the ultimate End threat. Prefer behavior, positioning, pressure, preparation, counterplay, and arena design over raw HP/damage inflation.

# Cross-dimensional ecosystem extensions

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| End's Delight | **PREFERRED** | Common food language in End. | Covered above. |
| My Nether's Delight | **PREFERRED** | Common food language in Nether. | Covered above. |
| Farmer's Cutting: BetterEnd | **PREFERRED** | Existing processing verb for BetterEnd materials. | Covered above. |
| Create: Nether Industry | **PREFERRED** | Nether inputs into shared Create economy. | Covered above. |
| Create: Enchantment Industry | **A/B** | XP/enchantment resources into shared industrial economy. | D-14. |
| Slice & Dice | **CONDITIONAL** | Additional food automation only if Central Kitchen leaves gaps. | Recipe coverage. |

# Onboarding, multiplayer, navigation utilities

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| FTB Quests | **PREFERRED** | Optional guided onboarding/quest book. | D-15; ordinary progression must remain quest-off complete. |
| Vanilla advancements | **KEEP fallback** | Lightweight guidance independent of quest dependency. | — |
| FTB Chunks / Open Parties and Claims / Flan | **CONDITIONAL** | Multiplayer claim/protection options. | Server/product scope. |
| JourneyMap / Xaero | **RESERVE** | Navigation convenience. | Only if omniscience can be constrained to preserve authored navigation. |


# Pack integration, information, recovery, and operations

These systems were selected in the Sep. 30 pack-integration freeze and are retained here because they make the chosen content behave as one coherent game. They do **not** become Skyforge simulation authority.

## Pack integration

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| KubeJS | **KEEP** | Primary Minecraft-facing recipe/tag/compat scripting authority. | Keep simulation semantics in Skyforge code. |
| KubeJS Create | **KEEP** | Create-native recipe/process integration. | Recipe closure. |
| LootJS | **KEEP** | Loot curation/suppression. | Strategic/shared vs per-player loot policy. |
| Paxi | **KEEP** | Datapack/resource-pack delivery and load order. | Pack assembly. |
| Almost Unified | **KEEP (configured)** | Normalize implementation duplicates only. | Explicit canonical-material allowlist. |
| Item Obliterator | **RESERVE** | Hard content suppression fallback. | Add only if KubeJS/datapacks cannot cleanly prune content. |
| CraftTweaker | **REJECT** | Would create a second pack-script authority. | — |
| GameStages / broad recipe staging | **REJECT by default** | Progression should emerge from geography/resources/capability. | Reopen only for a demonstrated sequence-break exploit. |

## Information / QoL / navigation

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| JEI | **KEEP** | Recipe/usage discovery. | — |
| Jade + Jade Addons + Jade Sable Compat | **KEEP** | Contextual machine/world/sublevel information. | Pin/dependency closure. |
| Controlling | **KEEP** | Keybind conflict/search management. | — |
| Mouse Tweaks | **KEEP** | Inventory input friction reduction. | — |
| Crafting Tweaks | **KEEP** | Craft-grid convenience without progression bypass. | — |
| AppleSkin | **KEEP** | Hunger/saturation legibility. | — |
| Polymorph | **KEEP** | Recipe-collision safety net. | Do not use as excuse for unresolved canonical recipes. |
| Clumps | **KEEP** | XP/performance cleanup. | — |
| Shulker Box Tooltip | **PREFERRED** | Container inspection without extra capacity. | Client acceptance. |
| Enchantment Descriptions | **PREFERRED** | Enchantment legibility. | Redundancy check. |
| Inventory Profiles Next | **CONDITIONAL** | Sorting/locked slots/loadouts. | Disable automation that erases resource/equipment state. |
| BetterF3 | **OPTIONAL** | Diagnostic/readability UI. | — |
| Map Atlases | **KEEP (configured)** | Physical learned cartography. | Disable entity radar/automatic structure reveal. |
| Spyglass Improvements | **KEEP** | Legitimate optical information. | No supernatural locator behavior. |
| Xaero / JourneyMap | **REJECT baseline** | Omniscient digital map/radar conflicts with navigation design. | Optional noncanonical profile only if policy changes. |
| Explorer's Compass / Nature's Compass | **REJECT** | Global locator behavior conflicts with exploration/information design. | — |

## Exploration / recovery / player tools

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| Tool Belt | **KEEP** | Professional tool storage; tools, not freight. | Exact pin/distribution. |
| Graveless | **KEEP** | Persistent death/void recovery without cargo teleport exploit. | Recovery-policy verification. |
| Create Aeronautics: Toolgun | **PREFERRED** | Aircraft blueprint/reconstruction knowledge with real material cost. | No cargo/fuel/ammo/value cloning. |
| OpenFlares | **PREFERRED** | Visual signaling/rescue/LZ/rendezvous. | Performance. |
| Climbable Ropes for Create Aeronautics | **PREFERRED** | Aircraft/rope traversal. | Runtime fit. |
| Create Grappling Hooks | **KEEP (configured)** | Physical grappling/cable utility. | Disable unsafe swing traversal if needed. |

## Multiplayer / operations

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| Simple Voice Chat | **KEEP** | Proximity/directional voice. | Server/network acceptance; unrestricted global/group voice disabled. |
| Walkie-Talkie Plus | **KEEP** | Physical long-range radio communication. | Range/energy/frequency policy. |
| Simple Backups | **KEEP** | Scheduled bounded-retention server backups. | Restore drill. |
| ServerCore | **KEEP (configured)** | Conservative server optimization. | Behavior-changing knobs disabled until individually proven. |
| Chunk-Pregenerator | **DEV** | Admin pregeneration/benchmarking. | Never whole-world default. |
| spark | **DEV** | Profiling/diagnostics. | — |
| FTB Chunks / Open Parties and Claims / Flan | **CONDITIONAL** | Public-server claims/permissions. | Public-server product scope. |

# Personal firearms

| Dependency | State | Role / authority boundary | Outstanding gate |
| --- | --- | --- | --- |
| TaCZ + Create: TaCZ + Aeronautics compat | **A/B** | Personal firearm finalist. | D-17. |
| Guard Villagers TaCZ support | **CONDITIONAL** | Armed settlement integration if TaCZ wins. | D-17. |
| TaCZ Durability | **OPTIONAL** | Maintenance layer. | Only if maintenance is enjoyable. |
| Scorched Guns NeoForge | **A/B** | Competing firearm finalist. | D-17; prune ExoSuit/worldgen/excess progression if selected. |
| Create: Gunsmithing + NTGL | **RESERVE** | Compact Create-native fallback. | Only if finalists fail. |
| Trigger Mobs | **RESERVE** | Gun-aware mob AI fallback. | Only with fallback firearm path. |
| Create: Caliber | **RESERVE** | Future Create-native option. | Maturity watch only. |
| Vic's Point Blank | **REJECT** | Insufficient Skyforge-specific integration advantage. | — |


# Explicit rejects / no-search zones

These exclusions are current design decisions, not invitations to find near-duplicates.

| Dependency / class | State | Reason |
| --- | --- | --- |
| Born in Chaos | **REJECT** | Threat-density/content philosophy conflicts with Skyforge. |
| Alex's Caves worldgen | **REJECT** | Competes with Skyforge cave authorship. |
| Caves & Creatures | **REJECT** | Redundant cave-authoring/content model. |
| IDAS | **REJECT** | Adds another broad integration/worldgen authority. |
| Create: Netherless | **REJECT** | Erases Nether geographic/economic value. |
| Unrestricted Waystones / convenience teleportation | **REJECT** | Erases distance, aviation, route, and logistics value. |
| Unrestricted When Dungeons Arise generation | **REJECT** | Structure-density/world-composition conflict. |
| Multiple competing sky/weather authorities | **REJECT** | Violates one-authority environmental contract. |
| New ore/material mods merely to satisfy recipes | **REJECT** | Canonical economy prefers retained materials/manufactured complexity. |
| New broad Nether biome/resource/bestiary packs | **REJECT by default** | Current catalogue is sufficient; integration is now the problem. |
| New broad End biome/structure packs | **REJECT by default** | Current catalogue is sufficient; integration is now the problem. |
| Dedicated temperature-survival dependency | **REJECT by default / RESERVE Cold Sweat** | Too much gameplay surface unless play proves a real need. |

# Dependency freeze / promotion procedure

A dependency changes state only when at least one of the following is true:

1. **A concrete gameplay capability is missing** from the integrated stack.
2. **The preferred dependency fails** runtime, performance, authority, licensing, or distribution acceptance.
3. **A narrower dependency can replace a broader one** while preserving required capability.
4. **Human play/presentation review** demonstrates the selected mechanic materially harms Skyforge's intended experience.
5. **An upstream change** invalidates the pinned compatibility assumptions.

Promotion order:

```text
RESERVE / CONDITIONAL
        -> bounded proof
PREFERRED / A-B winner
        -> pinned integration + authority + progression proof
KEEP
        -> HS-10 release/license/version audit
PUBLIC-ALPHA LOCK
```

## What is deliberately *not* a mod-selection decision

The following remain important, but should not trigger more dependency discovery:

- exact biome/ecology weights and regional placement;
- spawn budgets and population density;
- structure rarity and semantic site selection;
- recipe/tag/material normalization;
- pressure curves and breathable-region thresholds;
- vehicle balance, recoil, fuel, propulsion, and flight handling;
- exact resource abundance/deposit geometry;
- Nether cavern/magmatic morphology;
- End negative-space cadence;
- boss numerical tuning;
- visual coherence and texture/palette curation;
- public-alpha licensing, redistribution, and version pinning.

Those are integration/productization problems over this roster.

# Immediate convergence sequence

1. Build the pinned integrated dependency manifest from all **KEEP/PREFERRED/A-B** rows.
2. Resolve D-01 through D-16 with the smallest information-bearing test for each; do not reopen broad discovery.
3. After each A/B, update this file in the same PR/milestone that records the evidence.
4. Once no **A/B** rows remain, change this ledger's status to **DESIGN FROZEN**.
5. Run HS-10 separately to determine the public-alpha distributable subset, exact versions, licenses, and update policy.
