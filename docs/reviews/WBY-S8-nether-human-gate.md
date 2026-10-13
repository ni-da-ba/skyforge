# WBY S8 Nether substrate human gate

This gate compares the vanilla control, Jaden's Nether Expansion/Mosaic, and Incendium as mutually exclusive substrates on the cumulative S0-S7 profile. TaCZ remains in every arm. This is a world and compatibility comparison, not a recipe/balance pass or a substrate selection by the project.

## One-time setup

1. Clone or update the S8 branch in a separate review checkout and download the GitHub Actions artifact `wby-s8-control-client-profile` from the exact-head S8 run.
2. Extract the artifact into the checkout's `skyforge-neoforge-1211` directory. Confirm `skyforge-neoforge-1211/run-wby-s8-control/mods` exists.
3. Put the official candidate downloads in `.skyforge-diagnostics/s8-nether-downloads`:
   - JNE arm: [JNE 2.4.1](https://www.curseforge.com/minecraft/mc-mods/jadens-nether-expansion/files/8707156), [Elysium API 2.0.1](https://www.curseforge.com/minecraft/mc-mods/elysium-api/files/8707151), and [Lodestone 1.8.2](https://www.curseforge.com/minecraft/mc-mods/lodestone/files/7264731), saved as their original downloaded JAR names.
   - Incendium arm: [official Incendium 5.4.4 ZIP](https://modrinth.com/datapack/incendium/version/7mVvV9Th), saved as `Incendium-5.4.4.zip`.
   - JNE's CurseForge metadata says All Rights Reserved, while the upstream 1.21.1 repository declares CC BY-NC-ND 4.0. Keep JNE local to this review until the license discrepancy is resolved; do not republish or bundle it.

## Launch

In Git Bash, from the review checkout root, run one command per fresh world:

```bash
bash scripts/wby-s8-diagnostic-launch.sh control
bash scripts/wby-s8-diagnostic-launch.sh jne
bash scripts/wby-s8-diagnostic-launch.sh incendium
```

For each run, create a NEW normal/default world (not the Skyforge preset) with seed `817304`. Do not copy worlds or Nether chunks between arms. Use matching difficulty and game mode. Keep shaders disabled and Simple Clouds' cloud style set to Off (the accepted severe-artifact workaround); keep Distant Horizons and clouds enabled in all three runs. Let generation finish, enter the Nether, and compare the same exploration route.

## Review and report

Compare Nether geography and biome scale/connectivity, lava and route safety, flight/navigation, landmarks and resource placement, Simple Clouds/Distant Horizons rendering, fortress/Blaze access, and missing/duplicated/suppressed structures. For JNE, revisit the reported Powdery Cannon structure and cave-like lighting behavior. Step A deliberately excludes Nether's Delight and the other later content layer.

Send the arm name, coordinates, screenshots, performance observations, and any launch/runtime issues. A clear human decision is still required to select a substrate. Recipe, loot, drops, spawn tuning, and progression adjustments remain deferred.
