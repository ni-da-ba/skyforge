# F4K hydrology review status

## Current boundary — 2026-10-15

The owner accepted the previous key-77 specimen as an **early proof of concept worth improving**, not completed hydrology or DR-70 acceptance. Older feedback that it looked unchanged is historical, not a reason to repeat the same review.

PR #1490 implements an association-bound shared world-elevation frame. Integrated machine evidence proves 55 level continuous cross sections, two face-connected integer rivers, no missing centerline samples, and 3,928 persisted water voxels.

**No new visual review is requested yet.** The retained adapter permits water only where exact owner solid existed before excavation. It clips 219 materialized columns and suppresses intended water over unexcavated low ground. The pending human decision is whether to authorize a precisely bounded, connected candidate water volume above the old ground, with exact island/foreign-volume isolation and existing excavation safeguards retained. See [world-frame contract](../authorship/HYDROLOGY_ASSOCIATED_WORLD_FRAME.md).

## Runtime changes

- The opt-in F4K fixture uses the exact associated physical terrain for bed/water generation and qualification.
- Preparation verifies every permitted water voxel, not just the bottom water voxel of each column.
- Qualified static-water tick fencing uses exact qualified positions, never a legacy planner fallback.
- The viewer restores a **read-only** static-water tick mask from the exact fixture authorization. It does not install terrain mutation or a native surface adapter.
- The dedicated launch task regenerates the disposable review world before opening it; do not substitute a stale prepared world from another revision.

## When the next gate is ready

Use an accepted clean review checkout. The authoritative prepare-and-quick-play task is:

```bash
./gradlew.bat --no-configuration-cache :skyforge-neoforge-1211:launchF4KHydrologyReview
```

Do not run this as an acceptance request until the pending ownership boundary and machine validation are resolved. Do not modify or abort the owner's existing unfinished merge merely to create a review checkout; use a separate detached review worktree.

The next review must inspect lateral water-level consistency, downstream stepping, shoreline containment and reopen stability. Passing this bounded specimen is not full hydrology or DR-70 acceptance.
