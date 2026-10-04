# WBY S1 integrated profile — owner review gate

This is the final S1 manual gate for the selected atmosphere and mobility stack. It stages the accepted S0.5 cumulative profile, Aerodynamics4MC 0.2.2 core + Create Aeronautics compatibility built from its immutable upstream source commit, No More Elytra Boosting, Reliable Gliders, Create: Ornithopter Glider, Simple Clouds, and the existing Distant Horizons client layer.

Simple Clouds with Distant Horizons has already passed the owner's visual check after Minecraft's Clouds setting was enabled. The remaining human review is whether Reliable Gliders works as the early personal-mobility role alongside the retained Elytra-gated Ornithopter. The two gliders serve different progression roles, so this is an integrated coexistence review rather than a head-to-head comparison.

## Launch from Git Bash

Run these commands in Git Bash. The first command assumes the isolated checkout used for S1:

    cd /c/Users/nicho/Documents/skyforge-cloud-diagnostic-20261003
    git status --short --branch
    git switch wby/s1-atmosphere-mobility
    git pull --ff-only origin wby/s1-atmosphere-mobility
    bash scripts/wby-s1-diagnostic-launch.sh client combined simple-clouds false with-dh

The launcher checks out and builds the pinned Aerodynamics4MC 0.2.2 source, stages its core and Create Aeronautics compatibility jars, then launches the integrated client. Let Gradle and the game finish starting. The profile uses the isolated `skyforge-neoforge-1211/run-wby-s1-integrated` directory.

Create a **new Skyforge preset world with a fresh seed** in that profile. Do not copy an earlier diagnostic world into it. Keep Minecraft's **Clouds** video setting enabled and leave Distant Horizons enabled. This profile is independent of the previous cloud-only directory.

The timestamped diagnostics folder is printed in the terminal and stored under `.skyforge-diagnostics/wby-s1-client-combined-simple-clouds-thinair-false-with-dh-<timestamp>/`. If launch fails, send that whole folder, or at minimum `console.log`, `latest.log`, and `staged-mods.txt`.

## What to review

Use ordinary survival progression as the reference; do not change recipes, progression, handling, balance, cooldowns, or quest hooks during this gate.

- **Early role:** Can Reliable Gliders be obtained and used without an Elytra or another late-game unlock? Note the visible recipe/material tier, but do not tune it.
- **Practical flight:** Try takeoff, steering, landing, and recovery from representative terrain. Record whether the controls feel predictable and usable for a new player.
- **Travel limits:** Try a sustained flat-ground route. Does it descend overall, preserving a clear distinction from powered level flight and late-game vehicles?
- **Atmosphere fit:** If convenient, compare ordinary-air and A4MC wind/thermal conditions. Record whether the glider remains understandable and responds consistently.
- **Late role and coexistence:** Confirm the Ornithopter remains Elytra-gated, is still a useful later-game option, and both gliders load/work in the same profile without confusing controls or duplicated progression.
- **World presentation:** Confirm Simple Clouds remain visible with Distant Horizons in the fresh Skyforge preset world; note any cloud/island readability issue that materially affects flight.

Please send back the world seed, whether Reliable Gliders fits the early role, and any concrete blocker or friction you observed. Recipe, progression, balance, and quest-hook decisions remain separate producer decisions; we will stop for those if the review raises them.

## Accepted technical evidence reused

WBY-INT-0004 / #1411 and #1412 already prove Reliable Gliders 1.4.1 compatibility with the accepted Aerodynamics4MC stack, including exact resolution/staging, client/server separation, distant-Sable visibility, shared-lift behavior, and no second client-only server leak. S1 CI will recheck the selected combination and run the cumulative dedicated-server boot, actual-client join, and same-world save/reopen with both gliders in the current stack. This gate is for current-profile gameplay/coexistence and human role judgment, not a repetition of the isolated compatibility test.

## S1 boundaries

- Simple Clouds is the selected renderer. Its current rendered appearance is confirmed; mapping Skyforge-authored local weather/climate semantics into its API remains technical follow-on work.
- Particle Rain stays deferred until it consumes the accepted Aerodynamics4MC wind authority.
- Create: FlyHigher stays deferred until pressure-authority compatibility is proved.
- ThinAir remains a separate optional breathability overlay.
- Create: Deep Seas stays isolated R&D and does not block S1.
- Hang Glider is rejected by the owner and is not staged.
- Iris/shaders remain optional and outside S1.
