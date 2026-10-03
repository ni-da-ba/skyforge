# WBY S1 manual comparison launch

These are interactive review profiles for the staged S1 issue. They use the cumulative S0.5 stack, Aerodynamics4MC 0.2.2 core + Create Aeronautics compatibility built from the immutable upstream source commit in the S1 pin file, and No More Elytra Boosting. Optional candidates are separate overlays so the D-02 and D-03 comparisons stay isolated. The source build replaces the server-crashing 0.2.1 compatibility artifact from the accepted C3 probe without changing C3's historical evidence.

## Launch from Git Bash

Open Git Bash and run:

    cd /c/Users/nicho/Documents/skyforge
    bash scripts/wby-s1-diagnostic-launch.sh client none none false

The first launch clones and builds the pinned upstream Aerodynamics4MC source with its Gradle wrapper, then stages the 0.2.2 core and Create Aeronautics compatibility jars. Later launches reuse the clone and rebuild the same commit. Wait for the source build before the game starts. The launcher opens the client to the title screen and stages the baseline profile. The Better Clouds overlay also pins and stages its required YetAnotherConfigLib (YACL) dependency on the client only. Create a fresh Skyforge preset world using an untouched seed. Keep the same world/seed when switching overlays. The S1 launcher does not stage or activate the S0.5 synthetic visibility/structure fixture, and it does not tune recipes or progression.

After closing Minecraft, launch each comparison profile:

    bash scripts/wby-s1-diagnostic-launch.sh client none better-clouds false
    bash scripts/wby-s1-diagnostic-launch.sh client none better-clouds false without-dh
    bash scripts/wby-s1-diagnostic-launch.sh client hang-glider none false
    bash scripts/wby-s1-diagnostic-launch.sh client ornithopter none false

ThinAir is a distinct optional overlay for respiration/breathability inspection. It is not part of either D-02 or D-03 comparison:

    bash scripts/wby-s1-diagnostic-launch.sh client none none true

To start the dedicated server profile instead, use:

    bash scripts/wby-s1-diagnostic-launch.sh server none none false

## What to review

For the cloud comparison, compare baseline against Better Clouds in the same seed and daytime/weather conditions. To isolate Better Clouds from its Distant Horizons compatibility path, run the added without-dh profile after the ordinary Better Clouds profile. It keeps Better Clouds and the rest of the client stack enabled while excluding only Distant Horizons from the runtime classpath and staged-mod list.

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
