# WBY S8 Incendium full Nether stack human gate

This is the local gameplay gate for the cumulative Incendium profile. Run it after the exact PR head passes the six core S8 profiles (control, control-mnd, jne, jne-mnd, incendium, and incendium-full) in GitHub Actions and the owner decides the client is ready to review. The isolated `incendium-betternether` candidate is non-blocking and is not part of this gate; a failed candidate result must remain recorded and must not be described as compatible.

The profile layers official Incendium 5.4.4 over the accepted S0-S7 stack, including TaCZ and Eternal Nether, then adds Nether Depths Upgrade 3.3, My Nether's Delight 1.10.4.1, Create: Nether Industry 0.2.3, ThinAir: ReLived 1.0.3 with Curios 9.5.1, and YUNG's Better Nether Fortresses 3.1.5. JNE and BetterNether are not in this gameplay profile. BetterNether remains a separate composition qualification. Better Bastions and Wither: Reincarnated are isolated local A/B profiles, not part of the core gate.

## Before the launch

Use Git Bash. Work in a separate review checkout so files in the main Skyforge checkout are untouched.

The full profile launcher copies a tested S8 control client profile, stages the pinned S8 additions from their official release sources into `.skyforge-diagnostics`, and then launches the copied profile. It does not run Gradle. The profile uses the same client runtime settings as the accepted stack: Simple Clouds and Distant Horizons enabled, cloud style Off, and shaders disabled to preserve the severe-artifact fix.

Place the already-selected Sky Whale Retexture ZIP at:

```
.skyforge-diagnostics/s8-nether-downloads/Sky-Whale-Retexture.zip
```

The full-stack launcher installs it into the client resource-pack folder if present. Enable it in **Options > Resource Packs** before reviewing whales. The texture is required for any use of the whales and must remain a local review dependency.

The launcher downloads S8 dependencies into the diagnostics directory. Nether Depths Upgrade, Curios, and My Nether's Delight are All Rights Reserved/review-only as applicable; do not bundle or redistribute those files. Better Bastions has conflicting license metadata and is also local-only. Incendium is staged as the original whole data-pack ZIP.

## Launch the integrated profile

From the review checkout root, launch:

```bash
bash scripts/wby-s8-diagnostic-launch.sh incendium-full
```

When the client opens, create a **new normal/default world** (not the Skyforge preset) with seed `817304`. During world creation, open **Data Packs > Open Pack Folder**, copy the Incendium ZIP path printed by the launcher into that folder, return to the world-creation screen, move Incendium to Selected, and create the world. Verify `/datapack list enabled` includes Incendium. Do not reuse or copy Nether chunks from any prior world.

Keep the same game mode, difficulty, and graphics settings throughout the review. Keep shaders off, cloud style Off, Distant Horizons enabled, and Simple Clouds enabled. Let initial chunk generation settle before judging performance or world appearance.

## What to review

1. **World generation and routes:** Incendium biome scale/connectivity, lava and safe routes, landmarks, navigation and flight; confirm Nether structures are neither missing nor visibly duplicated.
2. **Fortresses and progression:** locate a fortress and verify Blaze access. Check that YUNG's replacement structures generate on fresh chunks and that fortress or bastion placement does not block key progression.
3. **Ecology and resources:** observe Nether Depths Upgrade creatures/resources, My Nether's Delight features such as Powdery Cannon, and any overlap with Eternal Nether and the existing S7/BOMD threat layer. Report missing, duplicated, hostile, or confusing encounters.
4. **ThinAir:** test Nether breathability and hazard readability, safe pockets (including soul-fire behavior), death/respawn and rejoin state, and the Create backtank interaction. Report whether equipment meaningfully helps without bypassing the intended hazard.
5. **Create: Nether Industry:** inspect representative inputs and processing paths. Report any route that creates Blaze Rods or other progression-critical materials without the intended Nether progression; do not alter recipes yet.
6. **Rendering and stability:** inspect Simple Clouds/Distant Horizons from several Nether heights and angles with the accepted shader workaround. Report crashes, disconnects, severe frame/tick stalls, or broken saves.
7. **Portal and Create ingress/egress:** verify Nether entry/exit and a simple contraption trip do not strand the player or lose the assembly.

Record coordinates, screenshots, the item/recipe involved, and any suspected bypass. Save and quit normally, then send the diagnostics directory if a technical issue occurred.

## Separate A/B candidates

Do not add BetterNether to the integrated profile or infer compatibility from launch success. It has its own `incendium-betternether` composition arm; a separate fresh-world review must establish biome/feature/structure ownership before it can be considered.

The automated `incendium-betternether` client comparison remains unqualified: its server started and Nether locate probes completed, but the client stayed on a Wunderlib `LayoutScreen` and did not join within 900 seconds. Keep BetterNether out of this gameplay profile; a separate review must establish client startup and worldgen coexistence before reconsidering it.

If the owner elects to review Better Bastions, compare the core `incendium-full` profile against `incendium-full-betterbastions` in separate fresh worlds with the same seed, keeping all other settings fixed. Its binary remains local-only pending license reconciliation.

If the owner elects to review Wither: Reincarnated, compare the ordinary fight with `incendium-full-wither` only in its own disposable review world.

The human gate reports observed behavior and product judgment. Recipe, loot, drop, spawn-rate, and balance changes remain deferred until the complete mod set is assembled and the integrated profile is accepted.
