# Skyforge — Wild Blue Yonder Canonical Mod Manifest

**Status:** Provisional integration baseline  
**Target:** Minecraft 1.21.1 / NeoForge  
**Snapshot:** 2026-09-30  
**Purpose:** Single authoritative selection manifest for Wild Blue Yonder mod-stack assembly.

> This document supersedes the September 29 **Working Mod and To-Build Ledger** for *selection status*.  
> It is not yet the exact jar lockfile. Exact versions, hashes, licenses, transitive dependencies, and client/server technical requirements must be pinned and verified during implementation assembly.

## Source precedence

When records disagree, use this order:

1. Latest dated `Skyforge_WBY_*` provisional freeze/addendum.
2. Merged `docs/design-audit/*-stack-v0.1.md` decisions.
3. September 29 `docs/design-audit/mod-and-build-ledger.md`.
4. Older exploratory notes.

## Status vocabulary

| Status | Meaning |
|---|---|
| **CORE** | Intended baseline. Remove only for a hard technical/design failure. |
| **PROVISIONAL** | Intended baseline, but still requires exact-stack acceptance/configuration. |
| **PLAYTEST** | Active finalist or A/B candidate; winner not yet selected. |
| **CONFIGURED** | Keep only under explicit Skyforge configuration/curation. |
| **OPTIONAL** | Supported profile or convenience; not required for the core game. |
| **RESERVE** | Not baseline. Reconsider only if a demonstrated gap appears. |
| **DEV/ADMIN** | Development, profiling, validation, or server administration only. |
| **OMIT** | Explicitly excluded from the baseline. |

## Global authority rules

- **Skyforge owns semantics:** world composition, geology, climate meaning, ecological admission/population, civilization planning, structure placement/density, progression intent, and strategic geography.
- **Mods supply implementations and vocabulary:** blocks, entities, machines, structures, UI, local behaviors, and reusable physical systems.
- **One authoritative source per gameplay quantity or verb.**
- Installed catalogue size must not imply realized world density.
- Progression is capability-driven, not “one chapter per mod.”
- New dependencies must fill a real role, not merely add variety.

---

# 1. Core World / Flight / Visibility

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Skyforge** | CORE | Both | World semantics, procedural synthesis, ecology/civilization/placement authority | Backend remains authoritative over imported content placement |
| **Create** | CORE | Both | Primary engineering, kinetics, automation, logistics language | Canonical engineering substrate |
| **Sable** | CORE | Both | Moving sublevel / physical contraption substrate | Exact pinned compatibility with Create/Aeronautics |
| **Create Aeronautics** | CORE | Both | Physical aircraft construction and flight | Core aviation system |
| **Distant Horizons** | CORE | Client | Long-range terrain visibility; part of navigation language | Required unless hard incompatibility |
| **Separate Sable Render Distance (SSRD)** | CORE | Client | Keeps distant Sable/Aeronautics craft visible | Pair with DH |
| **Aerodynamics4MC** | PROVISIONAL | Both | Leading atmospheric solver backend under Skyforge semantics | Must consume/serve canonical atmosphere rather than become world authority |
| **Wind Tunnel** | DEV/ADMIN | Dev | Atmospheric/flight validation | Validation only |

---

# 2. Engineering / Power / Vehicles / Heavy Industry

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **CC:Tweaked** | CORE | Both | Player-programmable computing | Player-facing automation; NPC aircraft AI remains Skyforge-owned |
| **Create: Avionics** | PROVISIONAL | Both | Programmable aircraft I/O | CC:Tweaked aircraft interface |
| **Create: Radars** | PLAYTEST | Both | Physical radar/track infrastructure | Sensor range, IFF, Sable/Aero behavior, guided-weapon implications |
| **Create Aero Radars** | PROVISIONAL | Both | Aeronautics turret/radar actuation bridge | Requires radar stack acceptance |
| **Create: Fire Control** | PROVISIONAL | Both | Advanced stabilization / EO / thermal / guided-weapon electronics | Keep subordinate to common combat authority map |
| **Create: Big Cannons** | CORE | Both | Heavy artillery + manufacturing spine | Aircraft recoil/moving-fire acceptance required |
| **CBC Neo Warfare** | PROVISIONAL | Both | Medium/rotary cannon, specialist ammo, armor/ERA | Suppress overlap |
| **CBC Terminal Ballistics** | PLAYTEST | Both | Penetration/armor interaction | Cross-mod projectile acceptance |
| **Mianbao's NewModernWarfare** | PROVISIONAL | Both | Aircraft stores, guided munitions, APS/countermeasures/CIWS library | Content library only; duplicate weapons/radar suppressed |
| **Create Diesel Generators** | CORE | Both | Petroleum/refining/dispatchable mechanical power | Petroleum geography owned by Skyforge |
| **SJE / Aero Propulsion** | PROVISIONAL | Both | Canonical advanced air-breathing engine family | Recipe/balance pin |
| **Create Propulsion: Simulated** | PROVISIONAL | Both | Reaction/vector/solid/ion propulsion | Fuel/oxidizer/low-pressure balance |
| **Create Linear Bearing** | PROVISIONAL | Both | Physical linear actuation | Sable/Aero runtime acceptance |
| **Aeronautics: No Horizon** | PROVISIONAL | Both | Heavy ground running gear/tracks/suspension/oleo gear | A/B against Tracks+ fallback |
| **Create Aeronautics: Transmission & Linkage** | PROVISIONAL | Both | Hinges, universal joints, hydraulics, articulated machinery | Sable persistence/runtime test |
| **Steam 'n' Rails** | PROVISIONAL | Both | Expanded fixed-route rail freight | Create trains remain rail authority |
| **Aeronautics Offroad** | PROVISIONAL | Both | Baseline wheeled vehicle layer | Must not eclipse aircraft/rail roles |
| **Create: Metallurgy** | PROVISIONAL | Both | Industrial melting/alloying/casting/molten handling | Normalize material tree; bridge CBC fluids |
| **Create Crafts & Additions** | CORE | Both | Preferred single electricity ecosystem | Silver excluded; Electrum only if justified |
| **Drive By Wire** | PROVISIONAL | Both | Onboard control integration | Aircraft-controls acceptance |
| **Tweaked Controllers** | PROVISIONAL | Both | Programmable/control interface | Validate overlap and latency |
| **CC&A Sable compatibility** | PROVISIONAL | Both | Electrical behavior on Sable craft | Exact-stack test |
| **Create: New Age** | RESERVE | Both | Future nuclear/advanced-heat candidate | Not a second electricity authority |
| **Create: Metalwork** | RESERVE | Both | Reference/compat processing | Prefer thin Skyforge CBC↔Metallurgy bridge |
| **Tracks+** | RESERVE | Both | Heavy-ground locomotion fallback | Only if No Horizon fails |

---

# 3. Personal Firearms / Combat Finalists

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **TaCZ 1.21.1 stack** | PLAYTEST | Both | Personal firearm finalist | Compare handling, NPC use, ammo economy, Sable projectile behavior |
| **Create: TaCZ** | PLAYTEST | Both | Create ammunition production for TaCZ | Included with TaCZ finalist |
| **TaCZ Aeronautics Compat** | PLAYTEST | Both | Projectile interaction with physical craft | Mandatory if TaCZ wins |
| **Guard Villagers TaCZ support** | PLAYTEST | Both | Armed settlement NPC compatibility | Verify friendly-fire/resupply behavior |
| **TaCZ Durability** | OPTIONAL | Both | Maintenance/durability layer | Use only if maintenance is fun, not busywork |
| **Scorched Guns NeoForge** | PLAYTEST | Both | Competing firearm finalist with deep Create/Aero integration | Must prune ExoSuit, progression/worldgen, excess catalogue if it wins |
| **Create: Gunsmithing + NTGL** | RESERVE | Both | Compact Create-native firearm fallback | Revisit only if finalists fail |
| **Trigger Mobs** | RESERVE | Both | Gun-aware mob AI fallback/companion | Depends on Gunsmithing/NTGL path |
| **Create: Caliber** | RESERVE | Both | Future Create-native firearm option | Watch until maturity |
| **Vic's Point Blank** | OMIT | Both | Polished firearm suite | Insufficient Skyforge-specific integration advantage |

**Weapon acceptance gate:** terrain fire; firing from moving Sable craft; hits on moving Sable hulls; explosives vs mass; rotation/translation; civilian/hostile NPC use; ammo-production economics; damage vs major creatures; content suppression; performance.

---

# 4. Ecology / Ordinary Fauna

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Naturalist** | CORE | Both | Broad terrestrial/aquatic ordinary-fauna library | Native spawning subordinated to Skyforge habitat/population authority |
| **Fowl Play** | CORE | Both | Preferred ordinary bird implementation | Shared Skyforge atmosphere contract for flight/soaring |
| **Critters & Companions** | CONFIGURED | Both | Small fauna / arthropod / companion niches | Species-level duplicate audit |
| **Sky Whales** | PROVISIONAL | Both | Exceptional aerial megafauna | Native spawning suppressed; lift role playtested |
| **Alex's Mobs** | RESERVE | Both | Species library for selected real/fantastical niches | Admit species-by-species only |
| **Birds/Boids Reforged** | RESERVE | Both | Bird behavior alternative | Only if Fowl Play leaves a real gap |
| **Hybrid Birds** | RESERVE | Both | Bird content alternative | Same rule |

Skyforge remains authoritative for habitat, niche, carrying capacity, climate suitability, migration, spawn admission, population budgets, and exceptional-fauna geography.

---

# 5. Threats / Bosses / Mythic Ecology

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Vanilla hostile mechanics** | CORE | Both | Ordinary baseline danger | Govern through Skyforge context where necessary |
| **Friends & Foes** | PROVISIONAL | Both | Vanilla-adjacent mob vocabulary | Avoid density inflation |
| **Mowzie's Mobs** | PROVISIONAL | Both | Exceptional encounters / mythic threats | Curate placement/rewards |
| **Ice & Fire CE** | PROVISIONAL | Both | Dragons and selected mythic/deep-sky threats | Skyforge owns geography/spawn; audit taming/Dragonsteel/progression |
| **Bosses of Mass Destruction** | PROVISIONAL | Both | Focused legendary destinations | Sparse exceptional placement |
| **In Control!** | PROVISIONAL | Server/Both | Static spawn/integration safety layer | Safety net, not ecology authority |
| **Creeper Overhaul** | RESERVE | Both | Hostile variation | Only if regional niche remains |
| **Rotten Creatures** | RESERVE | Both | Hostile variation | Same |
| **Cataclysm** | RESERVE | Both | Boss/content candidate | Avoid boss-density/progression overload |
| **Born in Chaos** | OMIT | Both | Broad hostile-content pack | Threat-density philosophy conflicts with Skyforge |

---

# 6. Civilization / Settlements / NPCs

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Vanilla Villagers** | CORE | Both | Base civilian population/professions/trade | Skyforge owns settlement/world role |
| **More Villagers** | PROVISIONAL | Both | Additional civilian professions/trades | Curate trade economy |
| **Guard Villagers** | CORE | Both | Settlement defense / armed civilians | Weapon-stack integration |
| **Easy NPC** | PROVISIONAL | Both | Named/authored/special-purpose actors | Not general population simulator |
| **CTOV** | PROVISIONAL | Both | Civilian settlement vocabulary | Native generation subordinated |
| **Towns & Towers** | PROVISIONAL | Both | Civilian village/settlement vocabulary | Can coexist with CTOV if role split survives integration |
| **Vanilla Illagers** | CORE | Both | Hostile-civilization baseline | Skyforge faction geography |
| **Illager Invasion** | PROVISIONAL | Both | Expanded hostile roster/combat | Density + weapon integration |
| **It Takes a Pillage Continuation** | PROVISIONAL | Both | Hostile roles/sites | Skyforge placement |
| **Illager Structures** | CORE | Both | Hostile civilization architecture library | Native placement subordinated |

**Civilization simulation rule:** abstract off-screen; materialize real Create/Sable aircraft when interaction matters. NPC aircraft AI is Skyforge-owned and external; captured craft do not grant hidden NPC automation.

---

# 7. Structure / Site Vocabulary

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Create: Structures Arise** | PROVISIONAL | Both | Industrial/technical/history structure library | Asset-level curation; no unrestricted native generation |
| **Create Aeronautics Structures (MIT project)** | PROVISIONAL | Both | Aviation ruins / boats / airships / balloons | Exact-volume integration |
| **Create Aeronautics Discovery** | PROVISIONAL | Both | Physical aircraft prefab/flyover/patrol realization | Skyforge retains mission/civilization authority |
| **Aeronautical Explorations** | PLAYTEST | Both | Abandoned airstrip / petrol-station vocabulary | Governance + license/runtime acceptance |
| **Radio Towers Lite** | PROVISIONAL | Both | Tower shells for radar/weather/navigation roles | Functional overlay supplied by retained systems |
| **Explorify** | PLAYTEST | Both | Frontier caches/guideposts/farmsteads/camps | A/B or role split with Structory |
| **Structory** | PLAYTEST | Both | Historical ruins/firetowers/cottages/graveyards | A/B or role split with Explorify |
| **YUNG's Better Mineshafts** | PROVISIONAL | Both | Historical extraction/subsurface history | Placement governed by Skyforge |
| **YUNG's Better Dungeons** | PROVISIONAL | Both | Expanded dungeon vocabulary | Placement governed |
| **Selected YUNG structure families** | RESERVE | Both | Temple/hut/monument roles | Only for explicit gaps |
| **When Dungeons Arise** | CONFIGURED | Both | Exceptional asset quarry only | No unrestricted megastructure generation |
| **Moog's Voyager Structures** | RESERVE | Both | Individual asset quarry | No unrestricted catalogue generation |
| **Repurposed Structures** | RESERVE | Both | Selective asset source | Only for real gaps |
| **Abandoned Structures: Aviation** | RESERVE | Both | Aviation ruins | Currently redundant |
| **Create: Sky Village** | RESERVE | Both | Detached settlement content | Currently overlaps retained stack |
| **Create Aeronautics Structures (ARR project)** | RESERVE | Both | Prefab/repairable craft source | Avoid duplicate aviation structure layers |

Skyforge owns site role, site plan, density, history/maintenance state, ownership/faction, resource/route relation, provenance, terrain accommodation, cliff/underside anchors, and airfield geometry.

---

# 8. Nether / End Content — Not Yet Final Baseline

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Eternal Nether** | PLAYTEST | Both | Narrow Nether structures/threat substrate | Strong data-driven candidate |
| **BetterNether: New Dawn** | PLAYTEST | Both | Broad Nether content candidate | High worldgen/dependency surface |
| **Jaden's Nether Expansion** | PLAYTEST | Both | Alternative broad Nether substrate | External dependency only; restrictive license |
| **Unusual End** | PLAYTEST | Both | Leading broad End content prototype | Native density/placement governed |
| **MES / Moog's End Structures** | RESERVE | Both | Narrow End structure vocabulary | Only if broad End substrate leaves gap |
| **Enderman Overhaul** | PLAYTEST | Both | End creature/reward candidate | Mobility/transport side effects |
| **End's Delight** | OPTIONAL | Both | End sustenance/local-life layer | Only if End needs local-life depth |

Policy: prefer at most one broad overhaul substrate per dimension in the first integrated prototype; narrow additions only for non-overlapping roles.

---

# 9. Ordinary Life / Agriculture / Food

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Farmer's Delight** | CORE | Both | Ordinary food/agriculture substrate | Skyforge owns crop geography |
| **Create: Central Kitchen** | PROVISIONAL | Both | Preferred Create/FD automation bridge | Verify Dragons Plus/dependency surface |
| **Supplementaries** | CONFIGURED | Both | Human-scale settlement/domestic/workshop utility | Disable/intercept duplicate pulley/cannon/navigation/weather/worldgen authorities |
| **Brewin' & Chewin'** | PROVISIONAL | Both | Distinct fermentation/brewing/preservation/cheese role | Exact 1.21.1 dependency acceptance |
| **Cultural Delights** | PROVISIONAL | Both | Regional crops/cuisine/trade vocabulary | Keep if compatible; suppress native placement and let Skyforge assign crops culturally/ecologically |
| **Farmer's Respite** | RESERVE | Both | Tea / tea-house / regional beverage production | Reconsider if 1.21.1 port is sufficiently stable |
| **Slice & Dice** | RESERVE | Both | Distinct agricultural machinery such as sprinklers | Do not install just to duplicate Central Kitchen |
| **Future WBY-native Delight addon** | RESERVE | Both | Regional cuisine, expedition provisions, Skyforge-native ingredients | Long-term bespoke content after ecology/civilization maturity |

---

# 10. Exploration / Player Tools / Recovery

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Artifacts** | CONFIGURED | Both | Curated exploration-reward layer | Audit mobility/teleport-like artifacts |
| **Lootr** | CONFIGURED | Both | Per-player ordinary exploration loot | Strategic/shared assets blacklisted |
| **Tool Belt (gigaherz)** | CORE | Both | Professional tool storage | Tools, not freight |
| **Graveless** | CORE | Server/Both | Persistent death/recovery, including void-safe recovery | Preserve geography; prevent death-based cargo teleport |
| **Create Aeronautics: Toolgun** | PROVISIONAL | Both | Aircraft blueprint/reconstruction knowledge | Must consume real materials and never clone cargo/fuel/ammo/value state |
| **Create Schematics / Schematicannon** | CORE | Both | Conservative structure/vehicle reconstruction fallback | Material accounting preserved |
| **Better Contraption Diagram / sublevel rescue** | DEV/ADMIN | Admin | Technical recovery from physics/desync failures | Not ordinary gameplay |
| **Void-rescue consumable** | RESERVE | Both | Specialist emergency equipment | No blanket void immunity |
| **OpenFlares** | PROVISIONAL | Both | Visual signaling/rescue/LZ/rendezvous | Performance test |
| **Climbable Ropes for Create Aeronautics** | PROVISIONAL | Both | Aircraft/rope traversal | Strong thematic fit |
| **Create Grappling Hooks** | CONFIGURED | Both | Physical grappling/cable utility | Swing-hook traversal likely disabled unless proven safe |
| **Explorer's Compass** | OMIT | Both | Global structure locator | Conflicts with exploration/information design |
| **Nature's Compass** | OMIT | Both | Global biome locator | Same |

---

# 11. Gliding / Personal Aerial Mobility

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Hang Glider** | PLAYTEST | Both | Leading conventional glider candidate | Handling/glide ratio/aircraft deployment |
| **Create: Ornithopter Glider** | PLAYTEST | Both | Advanced mechanically assisted glider candidate | Must remain net-descending over sustained flat-ground flight |
| **Reliable Gliders** | RESERVE | Both | Conservative fallback/reference | Use if richer candidates fail |
| **Vanilla Elytra** | CORE | Vanilla | Mature high-performance glider | Rocket propulsion disabled |
| **Paragliders** | OMIT | Both | Stamina/updraft glider system | Unneeded parallel RPG/stamina layer |
| **Gliders (tiered material mod)** | OMIT | Both | Material-tier glider progression | Generic tier ladder conflicts with desired engineering progression |

Invariant: personal gliding converts altitude into range. Aircraft remain superior for sustained powered travel, altitude generation, cargo, passengers, weapons, and route independence.

---

# 12. Mapping / Information / Navigation

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Map Atlases** | CORE | Both | Physical learned cartography | Entity radar off; no automatic structure revelation |
| **Spyglass Improvements** | CORE | Client | Legitimate optical information | No supernatural locator behavior |
| **Create: Radars** | PLAYTEST | Both | Physical sensor/navigation technology | See engineering section |
| **Xaero's Minimap / World Map** | OMIT | Client | Digital map/radar layer | Baseline survival omitted |
| **JourneyMap** | OMIT | Client | Rich digital map/radar/cave map | Baseline survival omitted |

---

# 13. QoL / Information UI

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **JEI** | CORE | Client | Recipe/usage discovery | Non-negotiable; no portable-crafting extensions |
| **Jade** | CORE | Both | Contextual world/machine inspection | Information, not omniscience |
| **Jade Addons** | CORE | Both | Create/Lootr/Artifacts integration | Keep |
| **Jade Sable Compat** | CORE | Both | Correct targeting/info on moving sublevels | Treat as part of Jade stack |
| **Controlling** | CORE | Client | Keybind search/conflict management | Essential at pack scale |
| **Mouse Tweaks** | CORE | Client | Inventory input friction reduction | Keep |
| **Crafting Tweaks** | CORE | Both/Client | Craft-grid convenience | No progression bypass |
| **AppleSkin** | CORE | Client | Hunger/saturation legibility | Keep |
| **Polymorph** | CORE | Both | Recipe-collision safety net | Do not use as excuse for bad canonical recipes |
| **Clumps** | CORE | Both | XP entity/performance cleanup | Keep |
| **Shulker Box Tooltip** | PROVISIONAL | Client | Container inspection | Does not increase capacity |
| **Enchantment Descriptions** | PROVISIONAL | Client | Enchantment legibility | Keep unless redundancy |
| **Inventory Profiles Next** | CONFIGURED | Client | Sorting, locked slots, move matching, loadouts | Disable automation that erases resource/equipment state |
| **BetterF3** | OPTIONAL | Client | Diagnostic/readability UI | Optional |
| **Create: Quality of Life** | OMIT | Both | Broad Create content suite | Not pure QoL; overlaps logistics/equipment authority |

---

# 14. Storage

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Vanilla + Create storage/logistics** | CORE | Both | Base and freight storage infrastructure | Default storage substrate |
| **Small field backpack (Reliable Backpacks / equivalent)** | PLAYTEST | Both | 9–18 slot expedition buffer only | No upgrades, nesting, fluids, auto-feeding, remote storage; verify Graveless interaction |
| **Sophisticated Storage** | RESERVE | Both | Stationary storage fallback | Add only if Create/vanilla UX genuinely fails |
| **Sophisticated Backpacks** | RESERVE | Both | Portable storage fallback | Strongly constrained if ever used |
| **AE2 / Refined Storage** | OMIT | Both | Digital storage/network authority | Conflicts with physical logistics baseline |

---

# 15. Performance / Technical

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Sodium** | CORE | Client | Renderer optimization baseline | Preferred over Embeddium on current stack |
| **Lithium** | CORE | Both | General logic optimization | Current Create-compatible direction |
| **FerriteCore** | CORE | Both | Memory optimization | Keep |
| **ImmediatelyFast** | CORE | Client | Rendering/UI performance | Keep |
| **Dynamic FPS** | CORE | Client | Background/inactive client resource reduction | Keep |
| **ModernFix** | PROVISIONAL | Both | Broad performance/bug-fix layer | Regression-tested because of broad mixin scope |
| **Create: Catalyst** | PLAYTEST | Client/Both | Create rendering optimization | Benchmark visually and with Sable/Aero |
| **StellarCreateOptimization** | PLAYTEST | Server/Both | Deep Create server optimization | Correctness + determinism gate |
| **Entity Culling** | PLAYTEST | Client | Render culling | Validate transformed/moving sublevels |
| **spark** | DEV/ADMIN | Server/Client | Profiling/diagnostics | Mandatory development infrastructure |
| **Embeddium** | OMIT | Client | Alternative renderer | Omit while Sodium stack is viable |
| **Radium** | OMIT | Both | Lithium alternative | Explicitly incompatible with current Create direction |

---

# 16. Multiplayer / Server Operations

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Simple Voice Chat** | CORE | Both | Proximity/directional voice | Disable unrestricted global/group voice |
| **Walkie-Talkie Plus** | CORE | Both | Physical long-range radio communication | Limited range, frequencies, batteries/FE |
| **Simple Backups** | CORE | Server | Scheduled bounded-retention backups | Off-machine backup remains deployment responsibility |
| **ServerCore** | CONFIGURED | Server | Conservative server optimization | Behavior-changing optimizations off initially |
| **Chunk-Pregenerator** | DEV/ADMIN | Server | Spawn/hub/benchmark pregeneration | Do not pregenerate the whole world |
| **FTB Chunks / Claims / Flan** | RESERVE | Server/Both | Public-server claims/permissions profile | Not baseline cooperative gameplay |
| **Discord/global-chat bridge** | OMIT | Server | External communications | Not baseline |

---

# 17. Onboarding / Documentation

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **FTB Quests** | CORE | Both | Authored capability progression / milestone journal | ~30–50 meaningful spine milestones; avoid item-checklist quests |
| **Patchouli** | CORE | Both | Skyfarer's Field Manual | Durable reference; avoid spoilers |
| **Create Ponder** | CORE | Both | Spatial/machine instruction | Extend with Skyforge-specific scenes where useful |
| **JEI + Jade** | CORE | — | Factual item/world information | Not substitutes for authored progression |

Quest doctrine: teach, orient, challenge, reveal, or mark real accomplishment. Prefer outcomes/capabilities over prescribed recipes. Material rewards remain restrained.

---

# 18. Pack Integration / Progression Control

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **KubeJS** | CORE | Both | Primary pack scripting authority | Integrates mods; does not become Skyforge simulation engine |
| **KubeJS Create** | CORE | Both | Create-native recipe/process integration | Use sequenced assembly/mixing/pressing/etc. |
| **LootJS** | CORE | Both | Loot curation and suppression | Ordinary/exploration/strategic/boss/civilization/salvage classes |
| **Paxi** | CORE | Both | Global datapack/resource-pack distribution/load order | Pack delivery authority |
| **Almost Unified** | CONFIGURED | Both | Duplicate-material normalization | May normalize implementation duplicates, never define Skyforge material ontology |
| **Item Obliterator** | RESERVE | Both | Hard content suppression | Add if KubeJS/datapacks cannot cleanly prune winning stacks |
| **CraftTweaker** | OMIT | Both | Alternate script authority | Avoid two pack-script authorities |
| **GameStages / broad hard recipe staging** | OMIT | Both | Artificial progression lock | Use only for a demonstrated sequence-breaking exploit |

---

# 19. Presentation / Atmosphere

| Component | Status | Deploy | Authority / role | Key gate / configuration |
|---|---|---:|---|---|
| **Sound Physics: Aeronautics** | CORE | Client | Moving-sublevel-aware acoustics / attenuation / flyby/Doppler | Preferred over generic Sound Physics Remastered |
| **Presence Footsteps** | PROVISIONAL | Client | Material-aware movement audio | Presentation compatibility |
| **True Adaptive Music** | PROVISIONAL | Client | Runtime for Skyforge-authored adaptive soundtrack | Regional/altitude/discovery/combat logic later |
| **Better Clouds** | PLAYTEST | Client | High-priority volumetric cloud-authority candidate | Must support future semantic sky-biome integration |
| **Iris** | OPTIONAL | Client | Enhanced shader profile | Never required for core meaning |
| **Iris Flywheel Compat** | OPTIONAL | Client | Shader/Create/Sable bridge | Required if Iris profile needs it |
| **Iris Veil Compat** | OPTIONAL | Client | Aeronautics Veil effect bridge | Required if Iris profile needs it |
| **Create Shader Fixes** | OPTIONAL | Client | Shader artifact fixes | Add only with shader profile |
| **Shaderpack (TBD)** | PLAYTEST | Client | Optional enhanced visual profile | Must preserve DH, cloud, aircraft, navigation readability |
| **AmbientSounds** | RESERVE | Client | Possible delivery layer for Skyforge-authored ambience | Must not become ecology authority |
| **Particle Rain** | PROVISIONAL | Client | Precipitation presentation candidate | Must consume canonical wind |
| **Weather2 / Expanded Weather2 Dynamics** | RESERVE | Both | Severe-weather R&D only | Not baseline weather authority |
| **Simple Clouds** | RESERVE | Client | Cloud-renderer A/B reference | Superseded by deeper Better Clouds audit direction |
| **Generic dynamic-light addon** | OMIT | Client | Extra renderer hook | No current need |

Rendering invariant: the sky is part of world geometry and navigation. Meaning must survive without shaders.

---

# 20. Explicit Baseline Omissions

These are not current baseline systems:

- general-purpose teleport / Waystones-style travel that erases geography;
- omniscient structure/biome locator compasses;
- unrestricted digital minimaps/world maps;
- AE2 / Refined Storage as default logistics;
- giant personal backpacks / recursive portable freight;
- broad parallel magic/RPG progression;
- multiple competing weather/cloud authorities;
- multiple competing electrical authorities;
- multiple pack scripting authorities;
- unrestricted megastructure worldgen;
- external cave/worldgen systems that compete with Skyforge authorship;
- broad hostile-density packs that turn wilderness into constant combat.

---

# 21. Active Playtest / Acceptance Gates

The mod-discovery phase is effectively complete. Remaining selection work is bounded:

| Gate | Decision still open |
|---|---|
| **Personal firearms** | TaCZ stack vs Scorched Guns |
| **Gliding** | Hang Glider / Ornithopter role; Reliable Gliders fallback |
| **Radar/fire-control** | Create: Radars and connected weapon-electronics acceptance |
| **Atmosphere/rendering** | Better Clouds + optional Iris/shader compatibility architecture |
| **Dimension content** | Broad Nether/End substrate selection |
| **Frontier structures** | Explorify vs Structory role split/A-B |
| **Field storage** | Whether any small backpack is needed at all |
| **Performance** | ModernFix/Catalyst/Stellar/Entity Culling and ServerCore knob acceptance |
| **Material normalization** | Almost Unified scope after canonical material ontology is pinned |
| **Hard content suppression** | Whether Item Obliterator is needed after winning stack curation |

---

# 22. Exact-Version / Dependency Lock Pass

Before a distributable build is called locked, every baseline/provisional component must receive a machine-readable entry with:

- exact mod version;
- artifact filename/hash;
- Minecraft version;
- NeoForge version;
- direct dependencies;
- transitive dependencies;
- client/server technical requirement;
- license / redistribution status;
- source URL/project ID;
- configuration files owned by Skyforge;
- native worldgen enabled/disabled;
- native spawning enabled/disabled;
- authority role;
- incompatibilities;
- acceptance-test references.

Until that pass, this document is the **canonical selection manifest**, not the final binary lockfile.

---

# 23. Closure Rule

> Do not resume broad mod discovery unless a concrete gameplay role is demonstrably unfilled.

From this point forward, default work is:

**assemble → pin → configure → integrate → test → prune**, not **search for more mods**.