# WBY S1 manual comparison launch

These are interactive review profiles for the staged S1 issue. They use the cumulative S0.5 stack, Aerodynamics4MC 0.2.2 core + Create Aeronautics compatibility built from the immutable upstream source commit in the S1 pin file, and No More Elytra Boosting. Optional candidates are separate overlays so the D-02 and D-03 comparisons stay isolated. The source build replaces the server-crashing 0.2.1 compatibility artifact from the accepted C3 probe without changing C3's historical evidence.

## Launch from Git Bash

Open Git Bash and run these commands from the isolated S1 checkout:

    cd /c/Users/nicho/Documents/skyforge-cloud-diagnostic-20261003
    git status --short --branch
    git pull --ff-only origin wby/s1-atmosphere-mobility
    bash scripts/wby-s1-diagnostic-launch.sh client none none false

The first launch clones and builds the pinned upstream Aerodynamics4MC source with its Gradle wrapper, then stages the 0.2.2 core and Create Aeronautics compatibility jars. Later launches reuse the clone and rebuild the same commit. Wait for the source build before the game starts. The launcher opens the client to the title screen and stages the baseline profile. The Better Clouds overlay also pins and stages its required YetAnotherConfigLib (YACL) dependency on the client only. Create a fresh Skyforge preset world using an untouched seed. Keep the same world/seed when switching overlays. The S1 launcher does not stage or activate the S0.5 synthetic visibility/structure fixture, and it does not tune recipes or progression.

After closing Minecraft, launch each comparison profile:

    bash scripts/wby-s1-diagnostic-launch.sh client ornithopter simple-clouds false without-dh
    bash scripts/wby-s1-diagnostic-launch.sh client ornithopter simple-clouds false with-dh
    bash scripts/wby-s1-diagnostic-launch.sh client none better-clouds false
    bash scripts/wby-s1-diagnostic-launch.sh client none better-clouds false without-dh
    bash scripts/wby-s1-diagnostic-launch.sh client hang-glider none false
    bash scripts/wby-s1-diagnostic-launch.sh client ornithopter none false

ThinAir is a distinct optional overlay for respiration/breathability inspection. It is not part of either D-02 or D-03 comparison:

    bash scripts/wby-s1-diagnostic-launch.sh client none none true

To start the dedicated server profile instead, use:

    bash scripts/wby-s1-diagnostic-launch.sh server none none false

## What to review

For the Simple Clouds comparison, the two commands above launch the same S1 client stack with Ornithopter Glider and differ only in whether Distant Horizons is present. The first run uses a new isolated `run-wby-s1-simple-clouds` game directory; create a fresh world and note its seed. Set one fixed non-zero Cloud Seed in Simple Clouds' client config and leave it unchanged, because client-only mode randomizes cloud positions on each login otherwise. Use the same world, location, time, weather, video settings, and cloud seed in both runs. Confirm Minecraft video settings have Clouds enabled, and do not change Simple Clouds' cloud mode between runs. The test overlays Simple Clouds on the client only; localized server weather, synchronization, and Skyforge semantic-weather mapping are not being evaluated.

If the no-DH run shows clouds but the DH run does not, capture a screenshot and retain the `config/simpleclouds-client.toml` file in that run's diagnostics bundle. Simple Clouds has an upstream open issue for clouds disappearing with Distant Horizons when its mode is set to Ambient; record the selected cloud mode before trying any setting change. [Upstream issue #212](https://github.com/nonamecrackers2/simple-clouds/issues/212).

For the Better Clouds comparison, compare baseline against Better Clouds in the same seed and daytime/weather conditions. Its without-dh profile excludes only Distant Horizons from the client runtime classpath and staged-mod list.

- whether Simple Clouds renders with DH disabled and again after DH is added, with the rest of the profile unchanged;
- whether the same cloud layout stays visible through DH at both near and horizon distances;
- whether cumulus/stratus/storm formations provide useful height, windward/leeward exposure, and route-direction cues around the sky islands;
- whether cloud layers obscure distant islands, the horizon, or Sable craft at navigation distances;
- whether frame pacing/performance remains acceptable and the sky colors/transparency stay correct;
- whether Better Clouds appears in the no-DH run, where it is absent, or remains absent in both;
- whether cloud layers help judge height, windward/leeward exposure, and route direction;
- whether the cloud field obscures distant islands or Sable craft at the distances used for navigation;
- whether vanilla clouds remain the clearer default;
- whether rendering remains readable with the current Distant Horizons / Sodium / SSRD stack in the ordinary profile. The no-DH run is a diagnostic comparison only.

For the glider comparison, use the same starting position, launch height, and route for each finalist:

- handling predictability and how quickly a new player can understand steering;
- whether lift or powered assistance obscures the intended cheap, early mobility role;
- whether sustained flat-ground travel has net descent (the glider must not become self-powered level flight);
- whether its progression feels appropriate beside the accepted aircraft stack.

Do not tune recipes, cooldowns, advancement hooks, quests, or progression during this comparison. Record the profile and seed, then note the choice or the specific unresolved concern. Capture the generated .skyforge-diagnostics/wby-s1-* directory if a launch fails.

## Current technical exclusions

- Particle Rain is not in the cumulative profile while its own wind field has not been shown to consume the accepted Aerodynamics4MC sample. Its current upstream integration path is WindLink, which would add a second wind authority unless a reviewed adapter maps it to Aerodynamics4MC.
- Create: FlyHigher is deferred pending proof that it does not introduce a competing pressure/atmosphere authority.
- Create: Deep Seas remains isolated R&D and does not block S1.
- Iris and shader profiles are outside S1; the D-02 cloud decision happens first.
- Aerodynamics4MC 0.2.2 is pinned by source commit because the upstream tag has no published release asset. The Skyforge workflow and manual launcher both build that same source and stage only its core and Create Aeronautics compatibility artifacts.
