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
- [Working mod and to-build ledger](mod-and-build-ledger.md)
