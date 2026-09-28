# F4K hydrology visual review runbook

This is the human visual gate for the opt-in F4K authorized hydrology fixture. It uses a fresh
disposable ModDevGradle game directory and the fixed ordinary authored identity
`(province=8, cluster=81, key=77)`. The runtime derives the accepted F4H direct/refined field and
installs `SkyIslandHydrologyRuntimeAuthorization`; it does not use the retained legacy specimen.

## Launch

From the repository root on the accepted current `main`:

```powershell
git fetch origin
git switch main
git pull --ff-only
.gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runF4kHydrologyReviewClient
```

In Minecraft:

1. Create a **new disposable world**.
2. Select **Skyforge Development (SF-IMP-0043)**.
3. Select Creative mode and allow cheats.
4. After joining, run:

```text
/time set day
/weather clear
/tp @s 0 320 0
```

The run's console log prints the F4H authorized-column count, exact removed-block count, refined-reach
count, and a first authorized wet anchor. Use that anchor for a close inspection if the origin overview
does not expose the channel immediately.

## Human observations

Record PASS only if all of the following are visually true in a new world:

- the floating island and its channel/pond geometry are present without a generation crash;
- water is confined to authored, terrain-owned hydrology and does not appear on unrelated vanilla
  terrain or in unsupported air;
- channel water follows the terrain rather than floating above it, cutting below it, or leaking
  through the island underside;
- chunk boundaries do not create discontinuities, duplicate water, or missing water;
- the surrounding vanilla world remains ordinary and unaffected;
- save, quit, reopen, and revisit preserve the same island/hydrology result.

This gate is qualitative. The deterministic F4C/F4D/F4H evidence and the runtime authorization checks
do not replace the human judgment about visible continuity, containment, and presentation.
