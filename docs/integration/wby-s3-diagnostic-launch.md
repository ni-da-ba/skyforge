# WBY S3 Diesel Generators owner review

This review layers the exact Diesel Generators pin onto the accepted S2 profile:

- selected S1: Simple Clouds, Distant Horizons, Reliable Gliders, and Ornithopter Glider;
- accepted S2: CC:Tweaked and Create: Avionics;
- S3 candidate: Create: Diesel Generators 1.21.1-1.3.15 (`ZM3tt6p1-UoPH8lO1`).

The diagnostic profile disables Diesel Generators' native normal/high oil chunk generation in its disposable worlds. It preserves the retained crude oil, Pumpjack Hole, Distillation Tank, and Diesel Engine registrations. It does not add a Skyforge petroleum adapter or recipe/progression tuning.

The S3 branch is stacked on the exact owner-reviewed S2 branch. It does not merge S2 or S3 automatically. The unresolved D-06 logistics comparison remains outside this tranche; Implementation owns its route persistence/playback evidence.

## Run from an isolated worktree

In **PowerShell**, from the existing clone:

```powershell
cd C:\Users\nicho\Documents\skyforge
git fetch origin wby/s3-industry-diesel
git worktree add --track -b codex/review-s3-diesel ..\skyforge-s3-diesel origin/wby/s3-industry-diesel
```

If Git reports that the local review branch already exists, use a fresh name in the `-b` argument, such as `codex/review-s3-diesel-2`.

Then open **Git Bash** and run:

```bash
cd /c/Users/nicho/Documents/skyforge-s3-diesel
git pull --ff-only
bash scripts/wby-s3-diagnostic-launch.sh client
```

The launcher fetches and builds the pinned Aerodynamics4MC source, resolves/stages the exact profile, and starts the NeoForge client. First run may take several minutes while Gradle downloads/builds dependencies.

For a standalone server diagnostic instead, use:

```bash
bash scripts/wby-s3-diagnostic-launch.sh server
```

## What to review

1. The client reaches the title screen without a missing-dependency screen or crash.
2. The Mods list shows Create: Diesel Generators, CC:Tweaked, and the already accepted profile components.
3. Create a **new disposable world** in the diagnostic profile and open JEI. Confirm it exposes Diesel Generators' Pumpjack Hole, Distillation Tank, Diesel Engine, and crude oil.
4. After the world has opened once, inspect `skyforge-neoforge-1211/run-wby-s3-integrated/saves/<world-name>/serverconfig/createdieselgenerators-server.toml`. Confirm both `"Disable normal oil chunks" = true` and `"Disable high oil chunks" = true`.
5. Report any missing assets, unexpected world oil generation, launch error, or other visible integration issue.

This gate asks for launch/visibility feedback. Recipe balance, resource-site choices, progression and quest hooks are later product decisions. When done, exit Minecraft; the launcher gathers logs, the staged mod list, config, and disposable saves under `.skyforge-diagnostics/wby-s3-client-<timestamp>`. Send me that diagnostics folder or a short pass/fail report.

## Branch refresh after new commits

From Git Bash in the review worktree, run:

```bash
git pull --ff-only origin wby/s3-industry-diesel
```

If you are returning after the PR is merged, the selected S3 work will be incorporated through the normal cumulative integration path.
