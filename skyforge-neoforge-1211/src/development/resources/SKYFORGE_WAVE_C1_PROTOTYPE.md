# Skyforge Wave C1 development-only integration pack

These resources are intentionally placed in the NeoForge module's `development`
source set. The Gradle build attaches that source set to local development runs
but excludes it from Skyforge's production jar.

Current prototype overrides:

- Create Crafts & Additions Modular Accumulator accepts the mod's existing
  `createaddition:modular_accumulator_usable_wires` item tag, allowing Gold or
  Electrum instead of hard-requiring Electrum/Silver progression.
- Create Propulsion: Simulated Platinum biome modifiers are redirected to the
  empty `#skyforge:integration_disabled` biome tag.
- Create: Metallurgy Wolframite biome injection is redirected to the same empty
  tag for the foundry A/B.

These files are test scaffolding, not final production integration. Runtime
acceptance must prove resource-pack precedence and confirm that no Platinum or
Wolframite placements occur when the relevant optional mods are loaded.
