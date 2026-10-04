# WBY S3 industry profile owner review

This review layers the current S3 industry candidates onto the selected and accepted cumulative profile:

- selected S1: Simple Clouds, Distant Horizons, Reliable Gliders, and Create: Ornithopter Glider;
- accepted S2: CC:Tweaked and Create: Avionics;
- S3 candidate: Create: Diesel Generators 1.21.1-1.3.15 (`ZM3tt6p1-UoPH8lO1`);
- S3 CBC overlay: Create: Big Cannons 5.11.7 (`GWp4jCJj-bOiDu0LS`) + Ritchie's Projectile Library 2.1.2 (`B3pb093D-hZ6B2Z0x`).

CBC and RPL reuse the existing immutable Wave C1 pins. CBC 5.11.7 is the repository-tested version and includes Sable 2.0 compatibility fixes. No CBC addon is included.

The disposable profile disables Diesel Generators' native normal/high oil chunk generation and retains its crude oil and machinery assets. It does not yet place Skyforge-authored oil sources in the world. This profile is for launch/registry/compatibility review, not recipe, projectile-balance, combat, or quest decisions.

The candidate branch is stacked on the S3 Diesel branch and does not merge either S3 change automatically. Diesel's prior review remains pending; this cumulative launch can review Diesel and CBC together.

## Run from an isolated worktree

In **PowerShell**, from the existing clone:

```powershell
cd C:\Users\nicho\Documents\skyforge
git fetch origin wby/s3-industry-cbc
git worktree add --track -b codex/review-s3-cbc ..\skyforge-s3-cbc origin/wby/s3-industry-cbc
```

If Git says the local review branch already exists, use a fresh name in the `-b` argument, such as `codex/review-s3-cbc-2`.

Then open **Git Bash** and run:

```bash
cd /c/Users/nicho/Documents/skyforge-s3-cbc
git pull --ff-only
bash scripts/wby-s3-diagnostic-launch.sh client
```

The launcher builds the pinned Aerodynamics4MC compatibility jars, resolves/stages the exact stack, and starts NeoForge. The first run may take several minutes while Gradle downloads/builds dependencies.

For a standalone server diagnostic instead:

```bash
bash scripts/wby-s3-diagnostic-launch.sh server
```

## What to review

1. The client reaches the title screen without a missing-dependency screen or crash.
2. The Mods list shows Create: Big Cannons 5.11.7, Ritchie's Projectile Library 2.1.2, Diesel Generators, CC:Tweaked, and the selected S1/S2 components.
3. In a **new disposable world**, open JEI. Confirm CBC cannon components, projectiles/ammunition, and Diesel's Pumpjack Hole, Distillation Tank, Diesel Engine, and crude oil are registered.
4. If convenient, use creative mode in that disposable world to assemble and fire one basic CBC cannon. Report any assembly/projectile failure. This checks that the machinery works; it is not a balance review.
5. After the world has opened once, inspect `skyforge-neoforge-1211/run-wby-s3-integrated/saves/<world-name>/serverconfig/createdieselgenerators-server.toml`. Confirm both `"Disable normal oil chunks" = true` and `"Disable high oil chunks" = true`.

Report missing assets, launch errors, recipe/material conflicts that are obvious during inspection, or other integration issues. Do not resolve recipe conflicts or tune weapon balance in this review.

When finished, exit Minecraft. The launcher gathers the logs, staged mod list, config, and disposable save under `.skyforge-diagnostics/wby-s3-client-<timestamp>`. Send me that diagnostics folder or a short pass/fail report.

## Refresh after new commits

From Git Bash in the review worktree:

```bash
git pull --ff-only origin wby/s3-industry-cbc
```
