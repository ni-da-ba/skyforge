# WBY S2 integrated computing profile

This profile layers the already-selected CC:Tweaked + Create: Avionics baseline onto the accepted S1 stack. It uses the isolated `skyforge-neoforge-1211/run-wby-s2-integrated` directory.

The S2 profile does not include Create:Aero Automated Logistics, Create Aeronautics Discovery, or any new Skyforge computer/peripheral. The accepted WBY-INT-0005 / #1418 C14 evidence already proves CraftOS peripheral discovery, a live sensor read, and bounded throttle control. S2 Actions repeats that capability smoke and the C7 shared-lift smoke inside the cumulative profile, then performs a dedicated-server join and same-world reopen.

## Launch after S2 Actions passes

Use **Git Bash** in the existing checkout at `C:/Users/nicho/Documents/skyforge`. Do not run these Bash commands in PowerShell.

First time on this branch:

    cd /c/Users/nicho/Documents/skyforge
    git status --short --branch
    git fetch origin
    git switch --track origin/wby/s2-computing-control
    git pull --ff-only origin wby/s2-computing-control
    bash scripts/wby-s2-diagnostic-launch.sh client

If the local branch already exists, use this instead of the `git switch --track` line:

    git switch wby/s2-computing-control
    git pull --ff-only origin wby/s2-computing-control

The launcher builds the pinned Aerodynamics4MC 0.2.2 source, stages the two selected gliders, Simple Clouds, Distant Horizons, CC:Tweaked, and Create: Avionics, then opens the client. The first launch may take several minutes while Gradle builds and resolves the pinned dependencies.

Create a **new Skyforge preset world** in this profile. Do not reuse the earlier S1 diagnostic save. Clouds and Distant Horizons are already selected and were verified in S1; no cloud comparison is part of this check.

## What to review

- Does the cumulative profile reach the title screen and open a new Skyforge world without a mod-loading error?
- Do CC:Tweaked and Create: Avionics appear in the loaded mod list and expose their expected computer/aircraft-instrumentation items?
- Does any visible startup, keybind, HUD, or world-open problem appear only when the computing mods are combined with S1?

The C14 and C7 capability checks are automated in Actions, so this gate does not ask for another detailed Lua/peripheral-control reproduction. Do not tune recipes, progression, autopilot, route automation, or balance during this review. Send back the diagnostics folder printed by the launcher if startup fails, or describe any concrete integration issue.

## Separate logistics decision

D-06 remains unresolved. The S2 baseline excludes Automated Logistics until the separate #441/#1423 exact-artifact/runtime work is accepted. Any later route/cargo playtest will have its own focused commands and review questions.
