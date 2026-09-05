# Skyforge Minecraft Content Integration Audit

**Snapshot:** 2026-09-05  
**Status:** Working design corpus; not an accepted architecture or implementation milestone.

This directory records the Minecraft-backend content, experience, ecosystem, threat, atmosphere, navigation, and structure audit conducted in parallel with the active Skyforge authorship and implementation lanes.

The purpose is to give other agents a stable cross-reference for decisions that would otherwise live only in conversation history.

## Scope

Skyforge remains the semantic/world-authoring authority. Minecraft, NeoForge, vanilla registries, and selected third-party mods are treated as lower-level realization systems and content libraries.

Default content-source order:

1. Vanilla Minecraft.
2. Existing mods/libraries.
3. Datapacks/configuration/integration.
4. Thin Skyforge adapters.
5. Bespoke Skyforge content only where a real design gap remains.

Core principle:

> Meaning belongs to Skyforge; the particular Minecraft content asset does not necessarily have to.

## Confidence vocabulary

- **Core / probable:** current preferred dependency or design direction.
- **Strong prototype:** should be tested before final lock.
- **Reserve / optional:** useful only if a later gap appears.
- **Omit / reject:** currently mismatched or redundant.
- **To-build:** bespoke Skyforge integration requirement.

## Current high-level decisions

- Distant Horizons is treated as a major presentation dependency for the Minecraft experience.
- Create + Create Aeronautics + Sable are the core aviation/physics substrate.
- A cheap early glider is preserved as personal soaring mobility, not as a replacement for powered flight logistics; Reliable Gliders is the leading prototype pending recipe override and gameplay validation.
- Thermals/updrafts are treated as a shared world language for gliders and soaring fauna: natural lift, terrain lift, and limited anthropogenic heat can all participate without turning gliding into freight logistics.
- Vanilla-style Elytra firework boosting is considered a mobility bypass and should be suppressed while preserving ordinary fireworks; optional blast/instability feedback may make the rule diegetic.
- Nether/portal distance compression is now an explicit audit item because dimension identity may be preserved without allowing portal transit to erase province-scale aviation; a 1:1 Nether coordinate-scale datapack is the leading low-bespoke interim prototype.
- Nether and End generation are **current vanilla-authority implementation domains, not permanent exceptions**. Their future terrain must be derived from gameplay requirements first; outer End remains the preferred low-risk cross-dimension pilot, while a solid-dominant Nether cavern province is only the leading stress-test hypothesis if it best serves Nether gameplay.
- Detailed realization-value audit now treats the dimensions asymmetrically: **Overworld full realization is already justified; Nether is a strong conditional yes pending repeatable multi-node operations; End is a promising conditional yes pending enduring post-Elytra/Shulker/Levitite expedition value.**
- Exploration enrichment is now a separate acceptance axis: the Nether must make difficult/tight traversal repeatedly worth the effort, while the End must keep sparse long-range expeditions behaviorally and structurally varied enough that the next horizon remains interesting. Third-party mods should be treated as curated behavior/asset libraries under Skyforge geography and population authority.
- Route/infrastructure planning is now explicitly cross-dimensional: routes are defined by destination value, movement capability, payload class, directionality, reliability, recovery, and infrastructure rather than raw distance. Overworld networks emphasize productive connectivity, Nether networks emphasize constrained corridor operation, and End networks emphasize staging/navigation/recovery across negative space.
- Post-flight resource progression is now evaluated by **capability payoff**, not material tier. Copper enables fluid/electrical infrastructure; zinc persists through Brass/capacitors/Levitite; Brass coordinates logistics/control and mature engines; petroleum creates strategic fluid/fuel networks; electricity enables conversion/distribution/storage/computation; CBC gives Cast Iron/Bronze/Steel/Nethersteel a real heavy-industry role; End Levitite adds a new lift-support architecture.
- The industrial stack is now governed by a **single canonical production graph**: Skyforge owns the material/process vocabulary, mods become producers/consumers of that shared economy, manufactured complexity is preferred over geological clutter, and recipe/tag/config overrides are used only to close capability, normalize shared materials, or repair demonstrated progression problems.
- Sable's built-in Overworld, Nether, and End profiles **all** decrease pressure with altitude, and current Sable lift/propeller code scales aerodynamic forces by local pressure. Dimension identity therefore comes from each curve's relevant operating envelope—not from altitude pressure loss itself. The End is referenced at pressure 1.0 around Y 0 (vs ~Y 63 Overworld), while the Nether reaches zero near its low ceiling; both require gameplay testing before morphology is frozen.
- Create Aeronautics itself gives the End a major technology loop: End Stone -> End Stone Powder -> zinc/water/heated Levitite Blend -> crystallized Levitite. Aeronautics Ponder shows Levitite can keep a contraption afloat but cannot provide climb alone, making it a strong End-derived lift-support technology rather than free propulsion.
- The Nether's leading gameplay role is hostile corridor operation and high-value process/industrial capability. Current leading heavy-industry payoff is CBC Nethersteel: Netherite Scrap + retained cannon metals + superheat produces a materially stronger but heavier and unweldable artillery material. Wolframite/Tungsten/Obdurium are no longer planned progression resources; Create: Metallurgy is now only a foundry-mechanics A/B candidate. Sable pressure decay still makes roof-level aerodynamic bypass naturally unattractive.
- Create Propulsion: Simulated is now a strong advanced-propulsion R&D candidate because its current 1.21.1 branch includes chemical/solid/ion thrusters and optional atmosphere-dependent performance, but that pressure behavior is not yet a locked pack assumption.
- Portable storage is governed separately from freight: vanilla inventory/Shulkers remain provisionally intact, while early warehouse-scale backpacks are disfavored and aircraft should win on bulk throughput, fluids, entities, contraptions, and automation.
- Copper/zinc/Brass now form the leading **first post-flight regional engineering loop**; petroleum remains a later strategic-node resource, so first flight turns regional specialization on rather than depending on it.
- Aerodynamics4MC is the leading authoritative wind/atmosphere prototype; not yet locked.
- Naturalist + Fowl Play + Critters & Companions + Sky Whales form the leading ecology stack.
- Ecology is niche-first: Skyforge derives feasible ecological roles, then maps them to available species.
- Open sky remains sparse. Species richness does not imply high active-entity density.
- Hostile natural spawning must be governed by Skyforge semantics and budgets. Darkness alone is not authorization.
- Player construction is not a blanket spawn-governor exemption; bridges and large open structures must not become monster carpets.
- Technical mob farms should remain possible through an engineered-spawning context rather than a simple player-block rule.
- Illagers are modeled as a hostile civilization, not generic dark-area monsters.
- Friends & Foes, It Takes a Pillage Continuation, Illager Structures, Mowzie's Mobs, and Bosses of Mass Destruction form the leading hostile/structure content set.
- Structure realization is divided into surface-supported, settlement/network, subsurface-embedded, cliff/underside-attached, detached, and structure-seeded terrain modes.
- Structure-seeded terrain is morphology-agnostic by default: structures constrain admissible scale/support/interior/access conditions rather than prescribing visible island shape.
- Progression-critical vanilla structures such as Strongholds must be guaranteed. If no suitable island exists, Skyforge may author terrain around the required structure.
- Ancient Cities should support both buried and rare exposed/structure-seeded realizations.
- Ordinary cluster authoring should preserve **layering without roofing**: vertical composition without routinely placing large islands directly over habitable lower surfaces.

## Relationship to accepted implementation work

The design corpus must not be mistaken for already-landed runtime capability.

The repository has already accepted generic native surface-structure support/admission, bounded fill-only accommodation, piece-aware footprints, underside contradiction detection/rejection, exact-volume biome population, caves, ores, underground decoration, springs, lakes, and authored/native cave composition.

Relevant accepted documents include:

- [SF-IMP-0045 Structure Candidate Admission](../architecture/Skyforge_SF-IMP-0045_Structure_Candidate_Admission.md)
- [SF-IMP-0047 Piece-Aware Structure Footprints](../architecture/Skyforge_SF-IMP-0047_Piece_Aware_Structure_Footprints.md)
- [SF-IMP-0052 Terrain Domain Isolation](../architecture/Skyforge_SF-IMP-0052_Terrain_Domain_Isolation.md)
- [SF-IMP-0054 Exact-Volume Biome Bridge](../architecture/Skyforge_SF-IMP-0054_Exact_Volume_Biome_Bridge.md)
- [SF-IMP-0067 Native/Authored Cave Precedence](../reviews/SF-IMP-0067-native-authored-cave-precedence-acceptance.md)

After terrain-domain isolation, structures have not yet been reintroduced as a production exact-volume island population phase. This audit therefore describes both the next structure-integration requirements and the intended content semantics.

## Documents

- [Ecology and fauna](ecology-and-fauna.md)
- [Atmosphere, aviation, navigation, and horizon](atmosphere-aviation-navigation.md)
- [Threats, hostile spawning, and farm compatibility](threats-and-spawn-governance.md)
- [Structures, dungeons, settlements, and realization modes](structures-and-realization-modes.md)
- [Structure realization contract v0.1](structure-realization-contract-v0.1.md)
- [Representative structure realization matrix v0.1](representative-structure-realization-matrix-v0.1.md)
- [Structure reservation and relocation policy v0.1](structure-reservation-and-relocation-policy-v0.1.md)
- [Structure-to-terrain compatibility contract v0.1](structure-terrain-compatibility-contract-v0.1.md)
- [Structure–authorship interaction policy v0.1](structure-authorship-interaction-policy-v0.1.md)
- [Structure site capability profile v0.1](structure-site-capability-profile-v0.1.md)
- [Civilization and settlement system v0.1](civilization-and-settlement-system-v0.1.md)
- [Civilization archetypes and infrastructure teaching v0.1](civilization-archetypes-and-infrastructure-teaching-v0.1.md)
- [Civilization history and regional composition v0.1](civilization-history-and-regional-composition-v0.1.md)
- [Civilization reuse-first realization strategy v0.1](civilization-reuse-first-realization-strategy-v0.1.md)
- [Civilization player interaction and progression v0.1](civilization-player-interaction-and-progression-v0.1.md)
- [Civilization service and reward matrix v0.1](civilization-service-and-reward-matrix-v0.1.md)
- [Civilization modification, looting, and civic assets v0.1](civilization-modification-looting-and-civic-assets-v0.1.md)
- [Resource and progression geography v0.1](resource-and-progression-geography-v0.1.md)
- [Resource role matrix v0.1](resource-role-matrix-v0.1.md)
- [Engineering and mobility progression ladder v0.1](engineering-and-mobility-progression-ladder-v0.1.md)
- [Early glider mobility contract v0.1](early-glider-mobility-contract-v0.1.md)
- [Vanilla mobility bypass governance v0.1](vanilla-mobility-bypass-governance-v0.1.md)
- [Portable storage and freight integrity v0.1](portable-storage-and-freight-integrity-v0.1.md)
- [Post-flight regional specialization sequence v0.1](post-flight-regional-specialization-sequence-v0.1.md)
- [Dimension realization value audit v0.1](dimension-realization-value-audit-v0.1.md)
- [Dimension exploration enrichment audit v0.1](dimension-exploration-enrichment-audit-v0.1.md)
- [Cross-dimension route and infrastructure grammar v0.1](cross-dimension-route-and-infrastructure-grammar-v0.1.md)
- [Post-flight capability payoff audit v0.1](post-flight-capability-payoff-audit-v0.1.md)
- [Material and process retention audit v0.1](material-and-process-retention-audit-v0.1.md)
- [Unified industrial production graph v0.1](unified-industrial-production-graph-v0.1.md)
- [Recipe and material normalization backlog v0.1](recipe-and-material-normalization-backlog-v0.1.md)
- [Exact recipe/worldgen collision manifest v0.1](exact-recipe-worldgen-collision-manifest-v0.1.md)
- [Create: Big Cannons industrial integration audit v0.1](create-big-cannons-industrial-integration-audit-v0.1.md)
- [Create: Big Cannons material access closure v0.1](create-big-cannons-material-access-closure-v0.1.md)
- [Overworld realization audit v0.1](overworld-realization-audit-v0.1.md)
- [Nether realization audit v0.1](nether-realization-audit-v0.1.md)
- [End realization audit v0.1](end-realization-audit-v0.1.md)
- [Dimension gameplay requirements v0.1](dimension-gameplay-requirements-v0.1.md)
- [End Aeronautics progression contract v0.1](end-aeronautics-progression-contract-v0.1.md)
- [Nether gameplay and aviation contract v0.1](nether-gameplay-and-aviation-contract-v0.1.md)
- [Cross-dimension Skyforge authorship strategy v0.1](cross-dimension-skyforge-authorship-strategy-v0.1.md)
- [Dimension world-grammar matrix v0.1](dimension-world-grammar-matrix-v0.1.md)
- [Bootstrap region recipe v0.1](bootstrap-region-recipe-v0.1.md)
- [Onboarding, guidance, and quest layer v0.1](onboarding-guidance-and-quest-layer-v0.1.md)
- [First-flight recipe closure audit v0.1](first-flight-recipe-closure-audit-v0.1.md)
- [First-flight bootstrap BOM v0.1](first-flight-bootstrap-bom-v0.1.md)
- [Bootstrap experience recipes v0.1](bootstrap-experience-recipes-v0.1.md)
- [Selected-mod resource worldgen authority audit v0.1](selected-mod-resource-worldgen-authority-audit-v0.1.md)
- [Working mod and to-build ledger](mod-and-build-ledger.md)
