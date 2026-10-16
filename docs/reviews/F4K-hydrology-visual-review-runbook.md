# F4K bounded connected-water review

## Scope

The previous specimen was accepted only as an early proof of concept. PR #1490 changes the actual
Minecraft input: bed and stage share an exact-association elevation datum, and the owner-approved
water envelope can occupy AIR above original ground. Excavation limits remain unchanged.

The expanded-envelope regression reports 55 level cross sections, two connected rivers,
5,400 water columns, 3,760 above old ground and 3,541 without terrain excavation.
These are machine geometry measurements, not aesthetic acceptance. Use the latest PR check results
and durable evidence comment for full native placement and saved-world preparation status.
Do not request visual acceptance until those checks pass. This bounded gate is not DR-70 or general
confluence/cascade/lake acceptance. The separate latent bend station-switch defect is not fixed here.

## Safe checkout and first launch — PowerShell

Do not pull into or resolve an unfinished merge to launch this review. Fetch and create a separate
detached review worktree inside the repository instead. After PR #1490 is merged and checks pass:

```powershell
Set-Location -LiteralPath 'C:\Users\nicho\Documents\skyforge'
git fetch origin main
git worktree add --detach '.reviews/f4k-connected-water-1490' origin/main
Set-Location -LiteralPath 'C:\Users\nicho\Documents\skyforge\.reviews\f4k-connected-water-1490'
.\gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:launchF4KHydrologyReview
```

If that worktree already exists, do not overwrite it; inspect its revision first. The launch task
regenerates only its disposable F4K review directory, prepares and verifies the saved water, then
quick-plays the client directly into the review world. Do not substitute an older prepared world.

## What to judge

- Across a river, water should have a consistent transverse level, not multiple differently tilted
  patches introduced by the old terrain-relative frame.
- Along a river, block-scale downward stepping is expected. Look for dry breaks, abrupt side joins,
  accidental disconnected pools, or water extending unsupported past intended banks.
- Inspect the shorelines closely: the new bounded inundation can legitimately cover old low ground.
  It must not look like arbitrarily floating water or erase foreign terrain.
- The specimen remains an intentionally bare, isolated fixture. Attractive ecosystem dressing and
  complete natural-world integration are not what this gate proves.

## Reopen the same saved world

Close the client, then run only this client task from the same checkout:

```powershell
.\gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:runF4kHydrologyReviewClient
```

This does not run preparation or regenerate the world. The viewer restores the exact fixture's
read-only Overworld static-water tick mask, with no terrain writer or legacy-planner fallback.
Compare the same shoreline after reopening and remaining in-world for a while. Report whether
the river geometry is coherent and stable enough to continue, or attach close-ups of remaining defects.
