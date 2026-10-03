# WBY S1 manual comparison launch

These are interactive review profiles for the staged S1 issue. They use the cumulative S0.5 stack, Aerodynamics4MC 0.2.2 core + Create Aeronautics compatibility built from the immutable upstream source commit in the S1 pin file, and No More Elytra Boosting. Optional candidates are separate overlays. The owner has selected Simple Clouds as the renderer foundation and confirmed it renders with Distant Horizons; the remaining S1 human gate is the early-game glider. The source build replaces the server-crashing 0.2.1 compatibility artifact from the accepted C3 probe without changing C3's historical evidence.

## Launch from Git Bash

Open Git Bash and run these commands from the isolated S1 checkout:

    cd /c/Users/nicho/Documents/skyforge-cloud-diagnostic-20261003
    git status --short --branch
    git pull --ff-only origin wby/s1-atmosphere-mobility
    bash scripts/wby-s1-diagnostic-launch.sh client none none false

The first launch clones and builds the pinned upstream Aerodynamics4MC source with its Gradle wrapper, then stages the 0.2.2 core and Create Aeronautics compatibility jars. Later launches reuse the clone and rebuild the same commit. Wait for the source build before the game starts. The launcher opens the client to the title screen and stages the baseline profile. The Better Clouds comparison overlay also pins and stages its required YetAnotherConfigLib (YACL) dependency on the client only. Create a fresh Skyforge preset world using an untouched seed. Keep the same world/seed when switching overlays. The S1 launcher does not stage or activate the S0.5 synthetic visibility/structure fixture, and it does not tune recipes or progression.

The owner has completed the Simple Clouds + Distant Horizons review. The paired cloud launches below are retained for reproducibility, but do not need to be repeated. The remaining S1 manual review is the early-game Hang Glider candidate. After closing Minecraft, run:

    bash scripts/wby-s1-diagnostic-launch.sh client hang-glider none false

ThinAir is a distinct optional overlay for respiration/breathability inspection. It is not part of either D-02 or D-03 comparison:

    bash scripts/wby-s1-diagnostic-launch.sh client none none true

To start the dedicated server profile instead, use:

    bash scripts/wby-s1-diagnostic-launch.sh server none none false

## Owner review and remaining human gate

### Cloud renderer: accepted

The owner reports that Simple Clouds rendered after enabling Minecraft's Clouds setting, including with Distant Horizons present. The Simple Clouds + Distant Horizons compatibility/rendering gate is closed for this pinned S1 client profile. Simple Clouds is the selected renderer foundation for Skyforge's intended cloud/weather presentation; Better Clouds is no longer an active S1 comparison.

This confirms rendering compatibility only. Skyforge still needs a technical integration that maps authored climate/weather meaning into Simple Clouds' cloud presentation. The client-only diagnostic does not prove localized server weather, synchronization, weather persistence, or semantic mapping.

### Early-game glider: owner review required

The owner likes Create: Ornithopter Glider as a later-game mobility tool, but it requires an Elytra and therefore does not fill the early-game gliding role. Hang Glider is the existing early-game candidate in the S1 profile.

Use the same fresh Skyforge preset world and comparable elevated starting point. The candidate launcher uses `run-wby-s1`, separate from the Simple Clouds run directory, so it will not mutate the cloud comparison world.

Review:

- can the Hang Glider be obtained and used without an Elytra or late-game unlock;
- how predictable its deployment, steering, landing, and recovery feel to a new player;
- whether a sustained flat-ground route has net descent, so it does not become powered level flight;
- whether it provides useful early traversal without making the later Ornithopter feel redundant;
- note the visible recipe and ingredient tiers, but do not change recipes, unlocks, cooldowns, balance, or quest hooks.

Record whether Hang Glider fits the early-game role, or the specific capability gap that would justify testing another candidate. The exact profile and seed are useful in the report. If launch fails, send the generated `.skyforge-diagnostics/wby-s1-*` directory or at least its `console.log` and `latest.log`.

## Current technical exclusions

- Particle Rain is not in the cumulative profile while its own wind field has not been shown to consume the accepted Aerodynamics4MC sample. Its current upstream integration path is WindLink, which would add a second wind authority unless a reviewed adapter maps it to Aerodynamics4MC.
- Create: FlyHigher is deferred pending proof that it does not introduce a competing pressure/atmosphere authority.
- Create: Deep Seas remains isolated R&D and does not block S1.
- Iris and shader profiles are outside S1; the Simple Clouds renderer decision is recorded, while any future shader profile remains optional.
- Aerodynamics4MC 0.2.2 is pinned by source commit because the upstream tag has no published release asset. The Skyforge workflow and manual launcher both build that same source and stage only its core and Create Aeronautics compatibility artifacts.
