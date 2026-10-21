# Skyforge Wave C1 datapack fixture

This standalone Minecraft 1.21.1 datapack is the authoritative runtime fixture
for the first integrated engineering-stack prototype.

Install it at higher priority than mod-provided datapacks for the disposable
Wave C1 test world.

It currently:

- makes the Create Crafts & Additions Modular Accumulator accept the mod's
  existing Gold-or-Electrum usable-wire tag instead of hard-requiring Electrum;
- disables all audited Create Propulsion: Simulated Platinum biome modifiers
  with NeoForge's supported `neoforge:none` modifier;
- disables Create: Metallurgy Wolframite biome injection with the same no-op.

This fixture is not production Skyforge content. Results from the integrated
runtime A/B determine which entries graduate into the eventual pack-level
datapack.
