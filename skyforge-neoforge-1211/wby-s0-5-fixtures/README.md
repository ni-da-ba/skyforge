# WBY S0.5A policy fixtures

Acceptance-only fixtures for the pack-authority slice.

They are deliberately inert with respect to progression:

- KubeJS parses a server script and observes the Create recipe bridge without adding/removing recipes.
- LootJS registers an empty modifiers callback without changing loot.
- Paxi exposes a minimal datapack containing only a private Skyforge probe tag.
- Almost Unified is constrained to an explicit material allowlist and has worldgen/loot unification disabled.

These fixtures are not semantic authority. They exist only to prove that the selected pack-authority tools can load under the cumulative S0.5A profile and that Almost Unified cannot silently broaden the material ontology.
