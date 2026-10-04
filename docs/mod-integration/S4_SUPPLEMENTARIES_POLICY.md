# S4 Supplementaries worldgen policy

S4 retains Supplementaries blocks, items, and recipes while suppressing its autonomous world-generation additions in candidate worlds. This avoids adding third-party structures and ecology on top of Skyforge-authored world composition.

The default config fixture is `skyforge-neoforge-1211/wby-s4-policy/supplementaries-common.toml`. The S4 staging task copies it to the isolated run profile's `config/supplementaries-common.toml`. This is Supplementaries' common config, so it applies to the candidate profile on both client and server and is independent of a world's seed or `serverconfig/` contents.

Disabled settings:

- `building.way_sign.road_signs.enabled`: prevents Supplementaries road signs from spawning. The upstream issue reports structure search can force structure-start chunk work during generation, and notes the configured search radius does not constrain the runtime lookup. This behavior is explicitly disabled in this candidate.
- `building.ash.basalt_ash`: suppresses the worldgen basalt ash feature.
- `functional.urn.cave_urns`: suppresses cave urn generation.
- `functional.flax.wild_flax`: suppresses wild flax generation.
- `functional.cannon.plunderer.galleon`: disables galleon structures.
- `redstone.pulley_block.mineshaft_elevator`: sets the new mineshaft elevator piece chance to zero.

This is an S4 development profile policy, not a global production config. Confirm the options against the pinned Supplementaries 3.8.9 NeoForge config during CI and manual review. If the file format or settings are rejected, fix the policy before proceeding. An S4 world should contain no Supplementaries-generated road signs, galleons, cave urns, wild flax, basalt ash patches, or Supplementaries mineshaft elevators.

## Hearth and Harvest compatibility companion

The pinned [Hearth and Harvest 1.21.1 NeoForge 1.3.4 release](https://www.curseforge.com/minecraft/mc-mods/hearth-and-harvest/files/8837088) is included because Create: Central Kitchen's optional Hearth and Harvest GameTest integration directly links its Cask block-entity class during server test discovery. Farmer's Delight is already a required S4 dependency and satisfies H&H's declared dependency.

Hearth and Harvest 1.3.4 also adds its Lilliput Lane structure to forests. This candidate does not claim the structure is accepted for Skyforge production. During the manual gate, use a fresh disposable test world and inspect any generated Lilliput Lane structures for interaction with authored islands, forced-spawn structures, and the intended empty space. Report whether the structure should remain, be disabled through a verified supported policy, or whether Hearth and Harvest should be removed once the CCK startup issue has another compatible resolution. Do not reuse an existing world for this review.

The shader overlay remains client-only and retains the specifically selected Iris/Simple Clouds/Distant Horizons bridge and Atmospheric Shaders package.


Upstream references:

- [Supplementaries 1.21.1 CommonConfigs.java](https://github.com/MehVahdJukaar/Supplementaries/blob/1.21.1/common/src/main/java/net/mehvahdjukaar/supplementaries/configs/CommonConfigs.java)
- [Supplementaries customization guide](https://github.com/MehVahdJukaar/Supplementaries/wiki/Customization)
- [Way-sign structure-search performance report](https://github.com/MehVahdJukaar/Supplementaries/issues/2155)
