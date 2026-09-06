# Skyforge Wave C1 development-only integration pack

These resources are intentionally placed in the NeoForge module's `development`
source set. The Gradle build attaches that source set to local development runs
but excludes it from Skyforge's production jar.

Current prototype overrides:

- Create Crafts & Additions Modular Accumulator accepts the mod's existing
  `createaddition:modular_accumulator_usable_wires` item tag, allowing Gold or
  Electrum instead of hard-requiring Electrum/Silver progression.
- Create Propulsion: Simulated Platinum biome modifiers are overridden with
  NeoForge's supported `neoforge:none` no-op modifier.
- Create: Metallurgy Wolframite biome injection is overridden with the same
  no-op modifier for the foundry A/B.

These files are test scaffolding, not final production integration. Runtime
acceptance must prove resource-pack precedence and confirm that no Platinum or
Wolframite placements occur when the relevant optional mods are loaded.


## Reproducible runtime profiles

Exact external-mod pins live at:

`skyforge-neoforge-1211/wave-c1-mods.properties`

Launch tasks:

- `runWaveC1BaselineClient`
- `runWaveC1MetallurgyClient`
- `runWaveC1PropulsionClient`
- `runWaveC1IntegratedClient`

Preflight artifact resolution:

- `waveC1ResolvePinnedMods`

These dependencies are attached only to their named ModDevGradle runs. They do not become
production Skyforge dependencies or alter the backend-neutral engine modules.
