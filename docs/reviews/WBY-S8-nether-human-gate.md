# WBY S8 Nether substrate human gate

This gate compares three mutually exclusive substrate arms (vanilla control, Jaden's Nether Expansion/Mosaic, and Incendium) on the cumulative S0-S7 profile, plus a matched `control-mnd` / `jne-mnd` pair to probe the reported Powdery Cannon interaction. TaCZ remains in every arm. The MND pair is excluded from substrate ranking.

## One-time setup

1. Clone or update the S8 branch in a separate review checkout and download the `wby-s8-control-client-profile` artifact from successful [S8 Actions run 38493898354](https://github.com/ni-da-ba/skyforge/actions/runs/38493898354). The branch has a later documentation-only commit; the tested runtime code is unchanged.
2. Extract the artifact contents directly into `skyforge-neoforge-1211/run-wby-s8-control/` (the archive paths are rooted at that profile). Confirm `skyforge-neoforge-1211/run-wby-s8-control/mods` exists.
3. Put the mandatory [Sky Whale Retexture](https://modrinth.com/resourcepack/sky-whale-retexture) ZIP in `.skyforge-diagnostics/s8-nether-downloads/Sky-Whale-Retexture.zip`. The launcher copies it into every S8 run directory. The pack is an owner-selected required local review dependency because the cumulative S0-S7 profile includes Sky Whales; do not redistribute it.
4. Put the official candidate downloads in `.skyforge-diagnostics/s8-nether-downloads`:
   - JNE arm: [JNE 2.4.1](https://www.curseforge.com/minecraft/mc-mods/jadens-nether-expansion/files/8707156), [Elysium API 2.0.1](https://www.curseforge.com/minecraft/mc-mods/elysium-api/files/8707151), and [Lodestone 1.8.2](https://www.curseforge.com/minecraft/mc-mods/lodestone/files/7264731), saved as their original downloaded JAR names. For the matched `control-mnd` and `jne-mnd` pair, also download [My Nether's Delight 1.10.4.1](https://www.curseforge.com/minecraft/mc-mods/my-nethers-delight/files/8854319). Farmer's Delight is already in the cumulative profile.
   - Incendium arm: [official Incendium 5.4.4 data-pack ZIP](https://modrinth.com/datapack/incendium/version/pwe1kTJE), saved as `Incendium-5.4.4.zip`. This is the original data-pack release, distinct from the mod-wrapper release. During Create World, open **Data Packs > Open Pack Folder**, copy that ZIP from `.skyforge-diagnostics/s8-nether-downloads` into the opened world datapacks folder, return to the screen, move Incendium to Selected, and confirm before creating the world. After world creation, verify `/datapack list enabled` shows Incendium.
   - JNE's CurseForge metadata says All Rights Reserved, while the upstream 1.21.1 repository declares CC BY-NC-ND 4.0. Keep JNE local to this review until the license discrepancy is resolved; do not republish or bundle it. My Nether's Delight is All Rights Reserved and is also local-review-only.

## Launch

In Git Bash, from the review checkout root, run one command per fresh world:

```bash
bash scripts/wby-s8-diagnostic-launch.sh control
bash scripts/wby-s8-diagnostic-launch.sh control-mnd
bash scripts/wby-s8-diagnostic-launch.sh jne
bash scripts/wby-s8-diagnostic-launch.sh jne-mnd
bash scripts/wby-s8-diagnostic-launch.sh incendium
```

For each run, create a NEW normal/default world (not the Skyforge preset) with seed `817304`. Do not copy worlds or Nether chunks between arms. Use matching difficulty and game mode. Keep shaders disabled and Simple Clouds' cloud style set to Off (the accepted severe-artifact workaround); keep Distant Horizons and clouds enabled in all five runs. In **Options > Resource Packs**, confirm and enable Sky Whale Retexture in every run. `control-mnd` and `jne-mnd` are a matched compatibility pair; compare their Powdery Cannon generation separately from the substrate ranking. Let generation finish, enter the Nether, and compare the same exploration route.

## Review and report

Compare Nether geography and biome scale/connectivity, lava and route safety, flight/navigation, landmarks and resource placement, Simple Clouds/Distant Horizons rendering, fortress/Blaze access, and missing/duplicated/suppressed structures. Compare `control-mnd` against `jne-mnd` on the same seed and equivalent Nether exploration route. Record whether the reported Powdery Cannon generation difference reproduces. The upstream cave-like darkness report #279 concerns JNE paired with No Man's Land, not My Nether's Delight; No Man's Land is not in the current cumulative S0-S7 profile, so do not treat that report as a result of this MND pair. Note any unexpected lighting in the JNE arms separately. Do not use either MND arm to rank substrate geography.

Send the arm name, coordinates, screenshots, performance observations, and any launch/runtime issues. A clear human decision is still required to select a substrate. Recipe, loot, drops, spawn tuning, and progression adjustments remain deferred.
