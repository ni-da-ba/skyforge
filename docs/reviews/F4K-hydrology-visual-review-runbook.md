# F4K hydrology visual review runbook

**Review scope:** narrow F4H/F4D runtime containment and persistence check for the opt-in ordinary
fixture (province=8, cluster=81, key=77). This is not the DR-70 product review and cannot accept
the reset hydrology system or stand in for the required key-287 specimen.

The F4K owner review has already been performed. The owner reports that the Minecraft result looks
identical to the preceding hydrology review, so it demonstrates no visible product improvement. Keep
that feedback as a non-acceptance result; do not ask for another run of this same fixture.

The runtime derives the accepted F4H direct/refined field and installs
SkyIslandHydrologyRuntimeAuthorization; it does not use the retained legacy specimen.

## Reproduction command

For debugging only, from the repository root on the desired accepted main:

```powershell
git fetch origin
git switch main
git pull --ff-only
.\gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runF4kHydrologyReviewClient
```

This command launches the scoped F4K fixture. It is not the next human review request.

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
count, and a first authorized wet anchor. Use that anchor for close inspection only when reproducing
the already-completed scoped check.

## Recorded result

The owner reported the result was visually indistinguishable from the previous hydrology review.
Therefore F4K is not visual/product acceptance. Its only retained value is the limited containment,
ownership, persistence, and lifecycle check; it does not establish a distinct or compelling authored
hydrology feature.

The deterministic F4C/F4D/F4H evidence and runtime authorization checks do not replace human judgment,
and passing this scoped check does not satisfy DR-70. A future human review must use a genuinely
different, fully qualified specimen after the key-287 continuous geometry and transition work passes
its machine gates.