# Skyforge — Wild Blue Yonder Build Lock

**Snapshot:** 2026-09-30  
**Target:** Minecraft 1.21.1 / NeoForge  
**Status:** **Provisional build lock** — hard-locks the already-tested substrate and freezes candidate release versions for integrated acceptance.  
**Selection authority:** `Skyforge_WBY_Canonical_Mod_Manifest_Provisional.md`

## Lock policy

This document distinguishes four materially different states:

- **IMMUTABLE_TESTED** — exact artifact already pinned in the Skyforge repository and used by an isolated acceptance/integration wave. Do **not** silently upgrade it.
- **CANDIDATE_PIN** — exact/current 1.21.1 release selected for assembly, but not yet promoted to immutable until it passes the integrated pack.
- **PIN_PENDING** — the mod is selected, but exact artifact identity or dependency closure is not sufficiently verified yet.
- **DEFER_TO_PLAYTEST** — do not pin a winner yet because the selection itself remains an A/B or experiential decision.

### Platform invariants

- Minecraft **1.21.1**
- NeoForge **21.1.249**
- Runtime Java **21**
- Skyforge build JDK may be newer, but Minecraft-facing output remains Java 21.
- **No silent “latest” upgrades.** Upgrade proposals are separate changes with acceptance evidence.

## 1. Immutable tested substrate

| Category | Component | Version | ArtifactOrCoordinate | Dependencies | NextGate |
|---|---|---|---|---|---|
| Platform | Minecraft | 1.21.1 | minecraft:1.21.1 | — | None |
| Platform | NeoForge | 21.1.249 | net.neoforged:neoforge:21.1.249 | Minecraft 1.21.1 | None |
| Platform | Java runtime | 21 | Java 21 runtime | — | None |
| Skyforge | Skyforge | 0.1.0 | repo build | Minecraft 1.21.1 / NeoForge 21.1.249 | Continue normal project CI |
| Engineering | Create | 6.0.10+mc1.21.1 | maven.modrinth:LNytGWDc:UjX6dr61 | bundles Ponder 1.0.82; Flywheel 1.0.6; Registrate MC1.21-1.3.0+67 | Integrated release assembly / no silent upgrade |
| Engineering | Ritchie's Projectile Library | 2.1.2 | maven.modrinth:B3pb093D:hZ6B2Z0x | required by Create Big Cannons | Integrated release assembly / no silent upgrade |
| Engineering | Create: Big Cannons | 5.11.7 | maven.modrinth:GWp4jCJj:bOiDu0LS | Create 6.0.10; RPL 2.1.2 | Integrated release assembly / no silent upgrade |
| Engineering | Create Crafts & Additions | 1.6.0 | maven.modrinth:kU1G12Nn:qPr8V4G2 | Create >=6.0.7,<6.1.0; JEI optional; CC optional | Integrated release assembly / no silent upgrade |
| Engineering | Create: Metallurgy | 1.0.3-1.21.1 | maven.modrinth:Soft45xC:4RhIMmaJ | Create 6.0.10; JEI/Jade/KubeJS optional integrations | Integrated release assembly / no silent upgrade |
| Flight | Sable | 2.0.5+mc1.21.1 | maven.modrinth:T9PomCSv:U678xqle | bundles Sable Companion 1.6.0; Veil; Rapier runtime | Integrated release assembly / no silent upgrade |
| Flight | Create Aeronautics (bundled) | 1.3.2+mc1.21.1 | maven.modrinth:oWaK0Q19:44pLdPGg | Create; Sable; bundles Simulated/Aeronautics/Offroad modules | Integrated release assembly / no silent upgrade |
| Flight | Create Propulsion: Simulated | 1.1.5 | maven.modrinth:ApkoHNO9:H13U56dc | Create; Sable; Aeronautics; Sable Companion satisfied by Sable JIJ | Integrated release assembly / no silent upgrade |
| QoL | JEI | 19.50.0.414 | maven.modrinth:u6dRKJwZ:zKog3N6a | — | Integrated release assembly / no silent upgrade |
| Power | Create Diesel Generators | 1.21.1-1.3.15 | maven.modrinth:ZM3tt6p1:UoPH8lO1 | Create | Integrated release assembly / no silent upgrade |
| Atmosphere | Aerodynamics4MC core | 0.2.1 | maven.modrinth:UnshaaiE:6Z0Z1pfP | — | Integrated release assembly / no silent upgrade |
| Atmosphere | Aerodynamics4MC Aeronautics compat | 0.2.1-Aeronaustics-Compat | maven.modrinth:UnshaaiE:L1NGyZ63 | A4MC; Create Aeronautics | Integrated release assembly / no silent upgrade |
| Dev | Wind Tunnel | 1.1.8 | maven.modrinth:EnEqwk7y:IdHA8JDW | LDLib >=2.2.6 | Integrated release assembly / no silent upgrade |
| Dev | LDLib | 2.2.6 | maven.modrinth:B1CBVXHX:KX3KmrCS | required by Wind Tunnel | Integrated release assembly / no silent upgrade |
| Ecology | Fowl Play | 1.2.3+1.21.1-neoforge | maven.modrinth:WpXfePbg:kZyPjCmU | SmartBrainLib 1.16.11; YACL 3.6.5 | Integrated release assembly / no silent upgrade |
| Library | SmartBrainLib | 1.16.11 | maven.modrinth:PuyPazRT:O5EpeqI3 | Fowl Play | Integrated release assembly / no silent upgrade |
| Library | YACL | 3.6.5+1.21.1-neoforge | maven.modrinth:1eAoo2KR:XoVxAvc2 | Fowl Play | Integrated release assembly / no silent upgrade |
| Avionics | CC:Tweaked | 1.119.0 | maven.modrinth:gu7yAYhd:puxJkazX | — | Integrated release assembly / no silent upgrade |
| Avionics | Create: Avionics | 0.5.2 | maven.modrinth:h4nsLvjf:sWhAueMC | Create; CC:Tweaked; aircraft stack | Integrated release assembly / no silent upgrade |
| Logistics | Create Aeronautics Automated Logistics | 0.6.2 | maven.modrinth:73ZXeRfx:EgPi4wq9; sha256=b966fe666212da694ef19b292516643081b402c0af18549f94152dd387cc268e | Create/Aeronautics stack | Integrated release assembly / no silent upgrade |
| Gliding | Reliable Gliders | 1.4.1 | maven.modrinth:pVIWsXir:HFLhMfNC | — | Integrated release assembly / no silent upgrade |
| Mobility | No More Elytra Boosting | 1.0.0 | maven.modrinth:uNWWxAv9:G9hL7wPy | — | Integrated release assembly / no silent upgrade |

### Nested dependency closure

The accepted engineering/flight substrate intentionally relies on upstream Jar-in-Jar/bundled packaging:

- Create 6.0.10 supplies matching **Ponder 1.0.82, Flywheel 1.0.6, Registrate MC1.21-1.3.0+67**.
- Sable 2.0.5 supplies **Sable Companion 1.6.0, Veil, and its Rapier runtime**.
- Create Aeronautics 1.3.2 uses the bundled distribution containing **Simulated / Aeronautics / Offroad**.
- Do **not** install loose duplicates of those components unless an actual loader failure proves upstream packaging changed.
- The repository's existing Wave C1 dependency-closure document remains authoritative for this tested substrate.

## 2. Candidate release pins for integrated assembly

These versions are frozen as the **first integration candidate**. They are not yet immutable; promotion requires boot/save/reload/dedicated-server and category-specific acceptance.

| Category | Component | Selection | Version | Environment | License | Dependencies | NextGate |
|---|---|---|---|---|---|---|---|
| Visibility | Distant Horizons | CORE | 3.3.3 - 1.21.1 neo/fabric | Client | LGPL-3.0 | Sodium/Iris compatibility varies | Integrated DH/Sable/sky test |
| Information | Jade | CORE | 15.10.6 | Both | Custom | — | Integrated launch |
| Information | Jade Addons | CORE | 6.1.1 | Both | Upstream | Jade | Integrated launch |
| Information | Jade Sable Compat | CORE | 1.3.0 | Client | MIT | Jade; Sable | Integrated launch |
| QoL | Crafting Tweaks | CORE | 21.1.11 | Client/Both | ARR | — | Integrated launch |
| QoL | Controlling | CORE | 19.0.5 | Client | MIT | — | Integrated launch |
| QoL | Mouse Tweaks | CORE | 2.26.1 | Client | BSD | — | Integrated launch |
| QoL | AppleSkin | CORE | 3.0.5 | Client | Public Domain | — | Integrated launch |
| QoL | Polymorph | CORE | 1.1.0+1.21.1 | Both | LGPL-3.0 | — | Integrated launch |
| QoL | Clumps | CORE | 19.0.0.1 | Server/Both | MIT | — | Integrated launch |
| QoL | Shulker Box Tooltip | PROVISIONAL | 5.1.9+1.21.1 | Client | Upstream | — | Integrated launch |
| QoL | Enchantment Descriptions | PROVISIONAL | 21.1.11 | Client/Both | LGPL-2.1 | — | Integrated launch |
| Performance | Sodium | CORE | 0.8.13 | Client | LGPL-3.0 | — | Sable/Aero/DH regression |
| Performance | Lithium | CORE | 0.15.4 | Both | LGPL-3.0 | — | Server benchmark |
| Performance | FerriteCore | CORE | 7.0.3 | Both | MIT | — | Integrated launch |
| Performance | ImmediatelyFast | CORE | 1.6.14 | Client | LGPL-3.0 | — | Integrated client benchmark |
| Performance | Dynamic FPS | CORE | 3.11.3 | Client | MIT | — | Integrated launch |
| Performance | ModernFix | PROVISIONAL | 5.27.24+mc1.21.1 | Both | LGPL-3.0 | — | Full regression |
| Navigation | Map Atlases | CORE | 7.0.1 | Both | GPL-3.0 | — | Config acceptance |
| Navigation | Spyglass Improvements | CORE | 1.5.7 | Client | Upstream | Architectury-family dependency may apply; scan at assembly | Dependency scan + launch |
| Exploration | Artifacts | CONFIGURED | 13.2.5 | Both | MIT | Dependency scan required | Loot/config audit |
| Exploration | Lootr | CONFIGURED | 1.11.38.127 | Both | MIT | — | LootJS integration |
| Recovery | Graveless | CORE | 1.3.1 | Server/Both | Upstream | — | Graveless/field-pack/aircraft-loss test |
| Recovery | Create Aeronautics: Toolgun | PROVISIONAL | 0.3.6 | Both | CC-BY-NC-4.0 | Java 21; NeoForge >=21.1.228; Create 6.0.10; Sable 1.1.3-2.x; Aeronautics 1.1.3-1.x | Duplication/reconstruction acceptance |
| Traversal | Climbable Ropes for Create Aeronautics | PROVISIONAL | 2.1.3 | Both | MIT | Create Aeronautics/Simulated | Traversal playtest |
| Traversal | Create Grappling Hooks | CONFIGURED | 1.2.0 | Both | MIT | Create Aeronautics | Traversal playtest |
| Ecology | Naturalist | CORE | 2.0.3 | Both | Custom | Dependency scan required | Species/spawn integration |
| Ecology | Critters & Companions | CONFIGURED | 2.7.0 | Both | ARR | Dependency scan required | Species integration |
| Threats | Mowzie's Mobs | PROVISIONAL | 1.8.2 | Both | Custom | Dependency scan required | Encounter/loot acceptance |
| Threats | Bosses of Mass Destruction | PROVISIONAL | 1.3.3 | Both | LGPL-3.0 | Dependency scan required | Encounter/loot acceptance |
| Threats | In Control! | PROVISIONAL | 10.3.0 | Server/Both | Upstream | McJtyLib-family dependency scan required | Decide whether beta is acceptable |
| Civilization | More Villagers | PROVISIONAL | 6.0.0 | Both | Upstream | Dependency scan required | Trade/integration test |
| Civilization | Guard Villagers | CORE | 2.4.12 | Both | Custom | — | NPC combat test |
| Civilization | CTOV | PROVISIONAL | 3.6.3 | Both | Custom | Lithostitched required (version to pin) | Dependency pin + placement test |
| Civilization | Towns and Towers | PROVISIONAL | 1.13.11 | Both | CC 4.0 | Cristel Lib 3.1.x family; exact pin required | Dependency pin + role split |
| Civilization | Illager Structures | CORE | 0.1.2 | Both | GPL-3.0 | — | Placement integration |
| Food | Farmer's Delight | CORE | 1.3.4 | Both | MIT | — | Integrated ordinary-life launch |
| Food | Create: Central Kitchen | PROVISIONAL | 2.6.0 | Both | LGPL-3.0 | Create 6.0.10; Farmer's Delight; dependency scan incl. DragonsPlus surface | Dependency scan + recipe test |
| Ordinary Life | Supplementaries | CONFIGURED | 3.9.9 | Both | Upstream | Moonlight Lib likely required; exact pin required | Dependency pin + config audit |
| Food | Brewin' & Chewin' | PROVISIONAL | 4.5.0 | Both | ARR | Farmer's Delight; dependency scan | Ordinary-life integration |
| Food | Cultural Delights | PROVISIONAL | 0.18.1 | Both | ARR | Farmer's Delight required | Crop geography + recipe audit |
| Information | FTB Quests | CORE | 2101.1.36 | Both | ARR | FTB library stack; FTB XMod Compat likely needed for KubeJS/JEI integration | Dependency closure + quest prototype |
| Information | Patchouli | CORE | 93 | Both | Upstream | — | Integrated launch |
| Integration | KubeJS | CORE | 2101.7.2-build.377 | Both | LGPL-3.0 | Rhino/Architectury-style dependencies per release; exact scan required | Dependency closure |
| Integration | KubeJS Create | CORE | 2101.3.1-build.18 | Both | Upstream | KubeJS; Create | Recipe integration test |
| Integration | LootJS | CORE | 3.7.0 | Both | Upstream | KubeJS | Loot regression |
| Integration | Paxi | CORE | 5.1.3 | Both | LGPL-3.0 | — | Integrated launch |
| Integration | Almost Unified | CONFIGURED | 1.4.2 | Both | ARR | — | Material ontology audit |
| Multiplayer | Simple Voice Chat | CORE | 2.6.22 | Both | ARR | UDP 24454 default | Dedicated-server voice test |
| Multiplayer | Walkie-Talkie Plus | CORE | 1.4.0 | Both | MIT | Simple Voice Chat | MP radio test |
| Operations | Simple Backups | CORE | 4.0.21 | Server | Apache-2.0 | — | Restore drill |
| Operations | ServerCore | CONFIGURED | 1.5.19+1.21.1 | Server | MIT | — | Benchmark + Sable regression |
| Operations | Chunk-Pregenerator | DEV/ADMIN | 4.5.4 | Server/Admin | ARR | — | Admin smoke test |
| Presentation | Sound Physics: Aeronautics | CORE | 2.0.1 | Both/Client-facing | GPL-3.0 | Sable/Aeronautics stack | Acoustic + stability playtest |
| Presentation | True Adaptive Music | PROVISIONAL | 2.7.0 | Client | Upstream | — | Music transition prototype |

## 3. Selected components still awaiting an exact pin

| Category | Component | Selection | Environment | Dependencies | SkyforgeConfig | NextGate |
|---|---|---|---|---|---|---|
| Visibility | Separate Sable Render Distance | CORE | Client | Distant Horizons or Voxy + Sodium | Exact 1.21.1 release/version ID not yet recorded | Resolve exact artifact before assembly |
| QoL | Tool Belt (gigaherz) | CORE | Both | Curios optional | 1.21.1 exact artifact not yet recorded | Resolve exact artifact |
| Player Tools | OpenFlares | PROVISIONAL | Both | dependency scan | Exact version pending | Resolve exact artifact |
| Civilization | Easy NPC | PROVISIONAL | Both | dependency scan | NeoForge 1.21.1 bundle artifact needs exact resolution | Resolve exact artifact |
| Civilization | Illager Invasion | PROVISIONAL | Both | dependency scan | 1.21.1 exact artifact not yet recorded | Resolve exact artifact |
| Civilization | It Takes a Pillage Continuation | PROVISIONAL | Both | dependency scan | Exact artifact pending | Resolve exact artifact |
| Threats | Ice & Fire CE | PROVISIONAL | Both | dependency scan | Exact artifact pending | Resolve exact artifact + progression audit |
| Threats | Friends & Foes | PROVISIONAL | Both | dependency scan | Exact artifact pending | Resolve exact artifact |
| Ecology | Sky Whales | PROVISIONAL | Both | dependency scan | Exact artifact pending | Resolve exact artifact + progression test |
| Structures | Create: Structures Arise | PROVISIONAL | Both | dependency scan | Exact artifact pending | Resolve exact artifact |
| Structures | Create Aeronautics Structures (preferred project) | PROVISIONAL | Both | Aero stack | Exact project/artifact identity needs final disambiguation | Resolve preferred project/version |
| Structures | Create Aeronautics Discovery | PROVISIONAL | Both | Aero/Sable | Exact artifact pending | Resolve exact artifact |
| Structures | Radio Towers Lite | PROVISIONAL | Both | dependency scan | Exact artifact pending | Resolve exact artifact |
| Structures | YUNG's Better Mineshafts | PROVISIONAL | Both | YUNG API likely | Exact artifact/dependency pin pending | Resolve exact artifact |
| Structures | YUNG's Better Dungeons | PROVISIONAL | Both | YUNG API likely | Exact artifact/dependency pin pending | Resolve exact artifact |
| Engineering | Create: Radars | PLAYTEST | Both | Create/Sable/Aero | Final version should be selected with radar playtest | Defer to playtest |
| Engineering | Create Aero Radars | PROVISIONAL | Both | Create: Radars/Aero | Exact artifact pending | Resolve when radar stack selected |
| Engineering | Create: Fire Control | PROVISIONAL | Both | CBC/radar stack | Exact artifact pending | Resolve with combat stack |
| Engineering | Mianbao's NewModernWarfare | PROVISIONAL | Both | dependency scan | Exact artifact pending | Resolve with combat stack |
| Engineering | CBC Neo Warfare | PROVISIONAL | Both | CBC | Exact artifact pending | Resolve with combat stack |
| Engineering | CBC Terminal Ballistics | PLAYTEST | Both | CBC | Final version selected during combat tests | Defer to playtest |
| Engineering | Create Linear Bearing | PROVISIONAL | Both | Create/Aero | Exact artifact pending | Resolve exact artifact |
| Engineering | Aeronautics: No Horizon | PROVISIONAL | Both | Aero/Sable | Exact artifact pending | Resolve exact artifact |
| Engineering | Create Aeronautics: Transmission & Linkage | PROVISIONAL | Both | Aero/Sable | Exact artifact pending | Resolve exact artifact |
| Engineering | Steam 'n' Rails | PROVISIONAL | Both | Create | Exact artifact pending | Resolve exact artifact |
| Presentation | Presence Footsteps | PROVISIONAL | Client | dependency scan | Exact artifact pending | Resolve exact artifact |
| Presentation | Particle Rain | PROVISIONAL | Client | dependency scan | Exact artifact pending | Resolve exact artifact |

A `PIN_PENDING` row is not permission to choose an arbitrary newest file during assembly. Resolve and record the exact artifact first.

## 4. Selection intentionally deferred to playtesting

| Category | Component | Authority | NextGate |
|---|---|---|---|
| Combat | TaCZ stack | Personal firearm finalist | Assigned playtest |
| Combat | Scorched Guns NeoForge | Personal firearm finalist | Assigned playtest |
| Gliding | Hang Glider | Leading conventional glider | Assigned playtest |
| Gliding | Create: Ornithopter Glider | Advanced assisted glider | Assigned playtest |
| Presentation | Better Clouds | Cloud-authority candidate | Assigned playtest |
| Presentation | Iris + compatibility layers + shaderpack | Optional enhanced renderer profile | Assigned playtest |
| Structures | Explorify vs Structory | Frontier/history role split | Assigned playtest |
| Dimensions | Nether broad substrate | Eternal Nether vs BetterNether/New Dawn vs Jaden's | Assigned playtest |
| Dimensions | End broad substrate | Unusual End and narrow supplements | Assigned playtest |
| Storage | Small field backpack | Only if inventory playtest demonstrates need | Assigned playtest |
| Performance | Create: Catalyst / StellarCreateOptimization / Entity Culling | Benchmark/correctness candidates | Assigned playtest |

## 5. Dependency-closure rules

1. **Repository pins win over current-latest releases** until an upgrade is separately accepted.
2. For CurseForge/Modrinth candidate pins, resolve direct + transitive required dependencies before first integrated launch and record exact versions.
3. If a dependency is already nested/Jar-in-Jar in the tested substrate, do not add a loose duplicate.
4. Client-only optimization/presentation mods must never be required for dedicated-server startup unless the upstream mod explicitly requires both sides.
5. Worldgen/spawn-providing content remains subject to Skyforge governance even when its jar is locked.
6. License/distribution status is a release gate independent from runtime compatibility.
7. Any mod whose 1.21.1 build is beta is flagged as such and requires explicit acceptance; do not quietly replace it with a different release line.

## 6. Configuration ownership

Skyforge must ship and version-control the configurations that define gameplay authority, especially:

- Supplementaries feature suppression;
- Map Atlases radar/reveal policy;
- Lootr strategic/shared blacklist;
- Artifacts curation;
- Graveless recovery behavior;
- voice-chat group policy and radio range/energy;
- ServerCore conservative profile;
- Almost Unified canonical-material allowlist;
- Farmer/Cultural crop placement governance;
- firearm and aircraft-weapon content suppression;
- rocket-Elytra suppression;
- native structure/spawn/worldgen disabling where Skyforge owns placement.

A jar lock without config ownership is **not** a pack lock.

## 7. Promotion criteria

A candidate pin becomes `IMMUTABLE_ACCEPTED` only after:

- client boot;
- dedicated-server boot;
- client join/leave;
- world create;
- save + reload;
- representative feature smoke test;
- no duplicate mod IDs / loader conflicts;
- configuration loaded from the pack rather than defaults;
- no unauthorized native worldgen/spawning;
- profiling shows no unexplained catastrophic regression;
- its category-specific acceptance/playtest gate passes.

## 8. Immediate assembly order

Use small integration waves:

1. **Platform + tested Create/Sable/Aeronautics substrate**
2. **Performance + DH/SSRD**
3. **Core engineering/power**
4. **Ecology**
5. **Civilization + structures**
6. **Ordinary life**
7. **Player tools/QoL/navigation**
8. **Multiplayer/operations**
9. **Onboarding/integration scripts**
10. **Combat finalists**
11. **Atmosphere/presentation**

Do not introduce the combat A/B candidates or shader/cloud experiments into the baseline until the preceding wave is stable.

## 9. Next transition: playtest assignments

The build-lock is now structured so each `DEFER_TO_PLAYTEST` group can become a standalone assignment with:

- exact candidate jars;
- fixed comparison world/seed;
- explicit test cases;
- objective telemetry;
- human-eye/feel criteria;
- pass/fail/reopen rule;
- artifact capture requirements.

The first useful assignments are **firearms**, **gliding**, **radar/fire-control**, and **atmosphere/rendering**.

## Closure

This is the authoritative **provisional build lock**, not yet the final distributable lockfile.  
The next technical pass is to resolve the remaining `PIN_PENDING` rows and dependency libraries while the experiential decisions move into playtesting.