# SF-IMP-0043 F4K hydrology visual review

Run from `skyforge-neoforge-1211`:

```powershell
.\gradlew.bat launchF4KHydrologyReview
```

This does not enter the normal Create New World flow. A dedicated server first builds a fresh, bounded `f4k-hydrology-review` save using the exact ordinary `(8, 81, 77)` F4H fixture and stops only after it finds persisted water heads in F4D-authorized carved columns. The client then quick-plays that save and places the player in spectator at the overview.

Review expectations:

- The island is visible below the overview near `(0, 320, 0)`.
- Follow the visible wet channel in spectator flight. Water belongs inside the carved channel bed, rather than spreading across untouched surface or appearing outside the island.
- The water should read as a constrained downhill channel: no broad flat sheet, floating water, or water escaping through uncarved terrain.
- The client is a viewer process: it does not reinstall the F4K terrain-mutation binding. Do not create a new world from this run.

For a preparation-only check, use:

```powershell
.\gradlew.bat f4kHydrologyReviewPrepareVerify
```

A failed preparation leaves no reviewable saved-world claim. Attach the resulting console log and `build/acceptance/f4k-hydrology-review/prepare.properties` to any defect report.
