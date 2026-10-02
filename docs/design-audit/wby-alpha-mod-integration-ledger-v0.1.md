# WBY Alpha Mod Integration Ledger v0.1

**Authority:** #1433  
**Target:** Minecraft 1.21.1 / NeoForge 21.1.249 / Java 21  
**Selection source:** `wby-canonical-mod-manifest-provisional-2026-09-30.md`  
**Artifact source:** `wby-build-lock-provisional-2026-09-30.md`  
**Pack-integration source:** `wby-pack-integration-progression-provisional-2026-09-30.md`

This ledger is the execution view for alpha assembly. It does not replace the canonical manifest or build lock.

## Rules

1. Preserve repository-tested immutable pins exactly.
2. CORE / PROVISIONAL / required CONFIGURED components belong in the alpha baseline once their exact 1.21.1 artifact and required dependencies are closed.
3. PLAYTEST groups use optional comparison profiles; they do not block the common baseline.
4. RESERVE / OMIT / DEV-only components do not enter the alpha baseline.
5. Reuse historical component evidence. New CI should prove cumulative boot, save/reload, side classification, owned configuration, authority suppression, and only targeted interactions exposed by the combined stack.
6. Skyforge retains world, ecology, civilization, mission, progression, and strategic-geography semantics.

## A. Immutable-tested baseline — active convergence layer

| Capability | Components | Integration state |
|---|---|---|
| Engineering substrate | Create 6.0.10, RPL 2.1.2, Create: Big Cannons 5.11.7, Create Crafts & Additions 1.6.0, Create: Metallurgy 1.0.3 | Exact repo pins; assembled in #1433 |
| Flight | Sable 2.0.5, Create Aeronautics 1.3.2 bundled distribution, Create Propulsion: Simulated 1.1.5 | Exact repo pins; assembled |
| Atmosphere | A4MC core 0.2.1 + Aeronautics compat | Exact repo pins; assembled through separate-artifact seam |
| Ecology | Fowl Play 1.2.3 + SmartBrainLib 1.16.11 + YACL 3.6.5 | Exact repo pins; assembled |
| Computing | CC:Tweaked 1.119.0 + Create: Avionics 0.5.2 | Exact repo pins; assembled |
| Vehicle logistics / AI execution substrate | Create Aeronautics Automated Logistics 0.6.2 | Exact artifact + SHA-256 from #441; assembled. Skyforge keeps mission/AI semantics |
| Petroleum | Create Diesel Generators 1.3.15 | Exact repo pin; assembled with native normal/high oil globally suppressed |
| Recipe discovery | JEI 19.50.0.414 | Exact repo pin; assembled |
| Long-range visibility | Sodium 0.8.13, Distant Horizons 3.3.2, SSRD 1.8.7 | Accepted W1 pins; assembled |
| Performance | Lithium 0.15.4, FerriteCore 7.0.3, ImmediatelyFast 1.6.14, Dynamic FPS 3.11.3 | Accepted W1-B pins; side-aware assembly |
| Elytra policy | No More Elytra Boosting 1.0.0 | Exact tested pin; assembled pending release-license disposition |

**Canonical correction:** Reliable Gliders 1.4.1 remains a tested fallback but is **RESERVE**, so it is not in the alpha baseline.

## B. Exact candidate pins — batch integration, not per-mod projects

These have a frozen candidate version in the Sep. 30 build lock. Add them in category batches, resolve required libraries once, then run the cumulative alpha gate.

### B1 — Information / QoL / navigation

- Jade 15.10.6
- Jade Addons 6.1.1
- Jade Sable Compat 1.3.0
- Crafting Tweaks 21.1.11
- Controlling 19.0.5
- Mouse Tweaks 2.26.1
- AppleSkin 3.0.5
- Polymorph 1.1.0+1.21.1
- Clumps 19.0.0.1
- Shulker Box Tooltip 5.1.9+1.21.1
- Enchantment Descriptions 21.1.11
- Map Atlases 7.0.1
- Spyglass Improvements 1.5.7

### B2 — Exploration / recovery / aircraft tools

- Artifacts 13.2.5
- Lootr 1.11.38.127
- Graveless 1.3.1
- Create Aeronautics: Toolgun 0.3.6
- Climbable Ropes for Create Aeronautics 2.1.3
- Create Grappling Hooks 1.2.0

### B3 — Ecology / threats / civilization / ordinary life

- Naturalist 2.0.3
- Critters & Companions 2.7.0
- Mowzie's Mobs 1.8.2
- Bosses of Mass Destruction 1.3.3
- In Control! 10.3.0
- More Villagers 6.0.0
- Guard Villagers 2.4.12
- CTOV 3.6.3
- Towns and Towers 1.13.11
- Illager Structures 0.1.2
- Farmer's Delight 1.3.4
- Create: Central Kitchen 2.6.0
- Supplementaries 3.9.9
- Brewin' & Chewin' 4.5.0
- Cultural Delights 0.18.1

### B4 — Pack integration / onboarding

- FTB Quests 2101.1.36
- Patchouli 93
- KubeJS 2101.7.2-build.377
- KubeJS Create 2101.3.1-build.18
- LootJS 3.7.0
- Paxi 5.1.3
- Almost Unified 1.4.2 under explicit Skyforge allowlist only

### B5 — Multiplayer / operations / presentation

- Simple Voice Chat 2.6.22
- Walkie-Talkie Plus 1.4.0
- Simple Backups 4.0.21
- ServerCore 1.5.19+1.21.1 with conservative behavior-changing knobs disabled
- Chunk-Pregenerator 4.5.4 — DEV/ADMIN profile only
- Sound Physics: Aeronautics 2.0.1
- True Adaptive Music 2.7.0
- ModernFix 5.27.24+mc1.21.1 — provisional, broad-mixin regression required before baseline promotion

## C. Selected baseline components still requiring exact artifact/dependency closure

Resolve and record exact artifact identity before assembly. Do not choose an arbitrary newest release.

| Area | Components |
|---|---|
| Player tools | Tool Belt; OpenFlares |
| Civilization | Easy NPC; Illager Invasion; It Takes a Pillage Continuation |
| Threats | Ice & Fire CE; Friends & Foes; Sky Whales |
| Structures | Create: Structures Arise; preferred Create Aeronautics Structures project; **Create Aeronautics Discovery**; Radio Towers Lite; YUNG's Better Mineshafts; YUNG's Better Dungeons |
| Engineering | Create Aero Radars; Create: Fire Control; Mianbao NewModernWarfare; CBC Neo Warfare; Create Linear Bearing; Aeronautics: No Horizon; Create Aeronautics: Transmission & Linkage; Steam 'n' Rails |
| Presentation | Presence Footsteps; Particle Rain |

### Vehicle-AI boundary

- **Automated Logistics**: route/logistics execution substrate; exact pin already active.
- **Create Aeronautics Discovery**: physical aircraft prefab/flyover/patrol realization; exact artifact still pending.
- **Skyforge**: mission selection, faction/civilization meaning, high-level NPC aircraft AI, materialize/dematerialize authority, capture handoff.
- **Sable/Aeronautics**: physical vehicle truth.

## D. Playtest profiles — intentionally unresolved winners

Do not block baseline loader integration on these decisions.

- Personal firearms: TaCZ family vs Scorched Guns NeoForge.
- Personal gliding: Hang Glider vs Create: Ornithopter Glider.
- Radar / terminal-ballistics acceptance.
- Better Clouds and optional Iris/shader profile.
- Explorify vs Structory frontier-site role split.
- Broad Nether substrate.
- Broad End substrate.
- Small field backpack need/choice.
- Catalyst / StellarCreateOptimization / Entity Culling.
- Almost Unified final allowlist.
- Item Obliterator need after content curation.

## E. Pack-authority configuration required before tester handoff

The distributable alpha must version-control at least:

- Diesel Generators native petroleum suppression.
- native structure placement suppression/redirect where Skyforge owns placement.
- native ecology/spawn suppression or admission where Skyforge owns populations.
- Supplementaries duplicate-authority suppression.
- Map Atlases no-radar/no-automatic-reveal policy.
- Lootr strategic/shared blacklist.
- Artifacts curation.
- Graveless recovery policy.
- voice-chat group/radio range/energy policy.
- ServerCore conservative profile.
- canonical material tags and Almost Unified allowlist.
- crop-geography governance.
- firearm/aircraft-weapon duplicate-content suppression.
- rocket-Elytra suppression.
- KubeJS/KubeJS Create recipe normalization.
- LootJS loot normalization.
- Paxi authored pack delivery.

## Exit criterion

The alpha mod stack is ready for human recipe/balance/playtest work when:

1. every baseline row is pinned with dependency closure;
2. one side-aware cumulative dedicated-server world creates successfully;
3. the same save reopens in an actual client;
4. owned configs/scripts/datapacks load from the pack;
5. prohibited worldgen/spawn/duplicate-authority behavior is suppressed;
6. representative category registries/features are present;
7. no catastrophic performance regression is observed;
8. optional A/B profiles can be swapped without rebuilding the common baseline.
