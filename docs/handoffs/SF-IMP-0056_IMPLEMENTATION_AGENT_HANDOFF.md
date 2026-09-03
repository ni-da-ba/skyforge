# Skyforge SF-IMP-0056 Implementation Agent Handoff

**Prepared:** 2026-09-02 (America/Chicago)  
**Repository:** `ni-da-ba/skyforge`  
**Repository visibility:** public  
**Accepted milestone:** SF-IMP-0055  
**Active milestone:** SF-IMP-0056 — physical volume admission  
**Active PR:** #63 — `SF-IMP-0056: prevent physical occupancy collisions`  
**Active branch:** `agent/sf-imp-0056`

This is the operational handoff for the next implementation agent. It records the post-publication continuation point. It does **not** promote SF-IMP-0056 to accepted status; the accepted runtime boundary remains SF-IMP-0055 until the interactive physical-admission proof and final acceptance gates pass.

## 1. Repository / publication state

Skyforge is now public and publication hardening is merged to `main`.

Current accepted/public base:

```text
main = b78685f25fa9adf4d9c837bc5d76b4011bdd12a4
```

Public CI run **#348** passed on that `main` head.

Publication hardening already present includes:

- Apache-2.0 `LICENSE`;
- `SECURITY.md`;
- `CONTRIBUTING.md`;
- pull-request template;
- Dependabot configuration for Gradle and GitHub Actions;
- least-privilege CI (`contents: read`);
- immutable SHA pinning for GitHub Actions;
- no `pull_request_target` execution;
- fork-safe artifact publication guard;
- `persist-credentials: false` in checkout;
- compact seven-day evidence artifact retention;
- `continue-on-error: true` for evidence artifact publication so storage trouble does not misclassify a correct build;
- updated README portfolio evidence and reviewer path;
- updated current-runtime architecture handoff.

A public-code smoke scan found no matches for representative high-risk patterns including `BEGIN PRIVATE KEY`, `ghp_`, `AKIA`, `password`, or `C:\\Users`.

### Administrative hardening still outside this connector

GitHub currently reports:

```text
main protected = false
repository rulesets = []
```

The connected GitHub integration can read those settings but cannot create branch protection or repository rulesets. This is not an SF-IMP-0056 blocker, but the repository owner should add a simple `main` ruleset when convenient:

- block force pushes;
- block branch deletion;
- require pull requests before merge;
- require the repository CI check before merge;
- do not impose unnecessary multi-reviewer requirements on the current solo-maintainer workflow.

Do not change implementation behavior to compensate for the absence of this administrative rule.

## 2. Feature branch integration state

The publication-hardened `main` was merged into `agent/sf-imp-0056` with a normal two-parent merge commit. No history was rewritten and no feature work was force-rebased.

Integrated implementation head before this handoff document:

```text
194e8ee5d36b9b56f63b7d76d1ecbe6c9cd6df16
```

Merge-base status after integration:

```text
agent/sf-imp-0056: ahead of main, behind by 0 commits
```

The `main` publication changes and SF-IMP-0056 implementation changes were disjoint. The feature diff against current `main` contains only SF-IMP-0056 runtime/test/build changes plus this handoff document.

Public CI run **#349** passed on `194e8ee5d36b9b56f63b7d76d1ecbe6c9cd6df16` before this documentation commit. Require CI to remain green on the exact final head used for acceptance.

## 3. Correct interpretation of historical red CI run #334

Do not diagnose run #334 as an SF-IMP-0056 regression.

On pre-publication head:

```text
76b2c8cd72a608ad1c15950dfc998d085008fa88
```

run #334 completed the implementation gates successfully:

- Gradle build: PASS;
- backend-independence gate: PASS;
- unit tests: PASS;
- NeoForge/FML test bootstrap: PASS;
- fixed-seed evidence generation: PASS;
- suspended-volume evidence generation: PASS.

The job failed afterward at `actions/upload-artifact@v4` with:

```text
Failed to CreateArtifact: Artifact storage quota has been hit.
```

That was specifically an **artifact storage quota** failure. It was not a compile/test/evidence failure, and it should not be described as exhausted compute minutes.

Current `main`/feature CI uses the newer compact, guarded, non-blocking evidence upload and has already passed publicly.

## 4. Architectural problem SF-IMP-0056 owns

SF-IMP-0052 established **generation-domain isolation**:

```text
BASE_WORLD generates without Skyforge ownership
        -> native terrain / structures / decoration complete
        -> explicit Skyforge island domain may realize later
```

SF-IMP-0055 then revealed a separate world-composition problem: completed native content can physically occupy coordinates a later Skyforge island intends to claim.

The required distinction is:

```text
generation-domain isolation != physical occupancy compatibility
```

SF-IMP-0056 must therefore prevent destructive Skyforge realization until an entire planned exact volume has a terminal physical-admission decision based on completed native occupancy evidence.

This milestone is Minecraft/NeoForge backend integration. Do not push Minecraft-specific lifecycle or block-state concepts upstream into backend-neutral modules unless a genuinely backend-neutral abstraction is first demonstrated.

## 5. Implementation already present

The active branch already implements the integrated physical-admission path.

### Physical lifecycle

```text
PLANNED -> ADMITTED
PLANNED -> REJECTED
```

Terminal decisions are not reopened.

### Occupancy survey

`SkyforgeNativeChunkOccupancySurvey` inspects only coordinates that the compiled exact Skyforge volume actually owns as solid. The first conservative policy treats any pre-existing non-air native block at an owned solid coordinate as a conflict and records block/block-entity evidence.

### Whole-volume admission ledger

`SkyforgePhysicalVolumeAdmissionLedger` derives the finite Minecraft-chunk footprint of each planned exact volume.

Required behavior:

- one observed conflict rejects the entire volume immediately;
- clear evidence cannot admit a volume until every required footprint chunk has reported;
- duplicate equivalent evidence is idempotent;
- changed or out-of-contract evidence fails rather than silently changing identity;
- terminal states cannot be reopened.

### Runtime admission stage

`SkyforgePhysicalVolumeAdmissionStage` sits between completed native generation and destructive Skyforge writes.

It provides:

- fail-closed position write gating while an exact owner is still PLANNED;
- write authorization only for ADMITTED owners;
- no write authority from REJECTED owners;
- population gating so an exact volume cannot run island-owned population before ADMITTED;
- immutable deferred-realization evidence for chunks encountered before whole-volume admission;
- no retained mutable `WorldGenRegion` or chunk references.

### Deferred catch-up

`SkyforgePhysicalVolumeCatchupService` services deferred work only after target chunks exist as stable loaded `LevelChunk`s.

It uses:

```text
ServerChunkCache#getChunkNow
```

This call is intentionally non-forcing: missing chunks remain pending until Minecraft loads them for an independent reason. SF-IMP-0056 must not create generation tickets merely to complete an island footprint.

After exact terrain catch-up succeeds, the accepted native surface-population stage is invoked. Its existing coordinator/ledger remains authoritative for population idempotency.

### Write and population integration

The active branch also integrates physical admission into:

- the NeoForge chunk adapter;
- the concrete chunk writer;
- the current surface stage;
- native surface population;
- the mod/runtime binding;
- domain mixin support;
- unit tests.

## 6. Dedicated runtime proof already implemented

Gradle run:

```text
:skyforge-neoforge-1211:runPhysicalAdmissionClient
```

System property:

```text
skyforge.dev.physicalAdmission=true
```

Use a **new disposable Skyforge Development world**.

The proof constructs two broad tablelands over a deterministic 5x5 / 25-chunk footprint.

### Lower proof volume — forced rejection

The lower exact volume deliberately intersects the vanilla Overworld bedrock floor.

Required outcome:

- physical state becomes `REJECTED`;
- the first native conflict is retained as evidence;
- the native conflicting block remains exactly unchanged;
- no rejected Skyforge terrain is left behind;
- no deferred realization remains for the rejected volume;
- no population executes for it.

### Upper proof volume — delayed whole-volume admission

The upper exact volume is placed in clear high air.

Required outcome:

- remains physically absent while state is `PLANNED`;
- cannot admit before all 25 required footprint chunks have clear evidence;
- transitions to `ADMITTED` only after the complete finite footprint reports;
- deferred terrain catches up only through stable already-loaded chunks;
- no arbitrary unavailable chunk is forced into generation;
- upper terrain materially exists after catch-up;
- native taiga surface population executes only after admission;
- pending catch-up work reaches zero.

Required terminal marker:

```text
SF-IMP-0056 PHYSICAL ADMISSION PASS
```

Do not accept the milestone merely because that string appears. Inspect the values embedded in the emitted evidence and the world state they claim.

## 7. Exact continuation procedure

The next implementation agent should continue PR #63 directly.

1. Pull/check out current `agent/sf-imp-0056` and confirm it contains current public `main` history.
2. Confirm exact feature-branch CI is green before interactive work. If this handoff documentation commit has triggered a newer CI run, use that newer exact-head result rather than #349.
3. Review the physical-admission implementation before changing it. Do not reimplement already-present ledger, write-gate, population-gate, catch-up, or proof-fixture work unless a concrete failing invariant requires correction.
4. Launch `:skyforge-neoforge-1211:runPhysicalAdmissionClient` in a fresh disposable world.
5. Capture the exact terminal runtime marker and its lower/upper volume evidence.
6. Visually/operationally confirm the rejected lower volume did not damage native terrain and the admitted upper volume did not partially appear before whole-volume admission.
7. If the proof fails, identify the narrowest owning layer and fix only that layer. Preserve the architecture rules below.
8. Re-run repository CI/evidence on the exact corrected head.
9. Re-run the interactive proof after any behavioral change.
10. Only after both automated and runtime proof pass, add `docs/reviews/SF-IMP-0056-...-acceptance.md` containing:
    - exact implementation SHA;
    - exact CI run number/result;
    - exact runtime command;
    - terminal marker;
    - lower rejection evidence;
    - upper admission/catch-up/population evidence;
    - any failed attempts that materially changed the proof oracle or architecture.
11. Create or update an ADR only if the accepted implementation establishes a material architectural decision not already captured. Confirm the next unused ADR number first.
12. Update `README.md` and `docs/architecture/Skyforge_Current_Runtime_Architecture.md` from accepted-through SF-IMP-0055 to SF-IMP-0056 only after acceptance is real.
13. Update PR #63 with final evidence and merge only when exact-head CI and acceptance documentation are green.

## 8. Non-regression rules

Do not compromise these contracts while completing SF-IMP-0056:

- BASE_WORLD generation remains observationally isolated from Skyforge.
- Physical admission remains distinct from generation-domain isolation.
- Do not restore global highest-surface competition between Minecraft terrain and all Skyforge volumes.
- Exact island operations remain scoped to one `SkyIslandWorldVolumeId` unless an explicit composition operation owns multiple volumes.
- PLANNED volumes fail closed and may not partially realize.
- REJECTED volumes leave no destructive terrain or population work behind.
- Deferred catch-up must not force arbitrary future chunks to generate.
- Native population must not execute before physical admission when the stage is installed.
- Population replay remains idempotent.
- Do not retain mutable generation-region/chunk objects as long-lived deferred work.
- Preserve deterministic identity and exact 3-D volume ownership.
- Preserve existing fixed-seed and suspended-volume evidence unless a deliberately accepted semantic change requires new canonical identity.
- Keep Minecraft/NeoForge APIs out of `skyforge-kernel`, `skyforge-model`, `skyforge-recipes`, and `skyforge-world` absent a demonstrated backend-neutral need.
- Preserve publication hardening, least-privilege CI, and visible engineering history.

## 9. Out of scope for SF-IMP-0056

Do not broaden this PR merely because these remain desirable:

- final island morphology / beautification;
- production world-plan/config bootstrap;
- ores, caves, hydrology, underground decoration, structures, or `TOP_LAYER_MODIFICATION` population;
- generalized optional-mod compatibility;
- persistent world-plan serialization;
- production spatial indexing/caching policy;
- runtime-wide generation strategy selection;
- additional hierarchy levels;
- final player-facing release polish;
- broad README screenshots/video portfolio work.

These should follow after physical occupancy is safe and accepted.

## 10. Definition of done

SF-IMP-0056 is complete only when all of the following are true on one exact final head:

- repository CI/evidence gate is green;
- lower collision volume is terminal REJECTED;
- rejected native conflict remains unchanged;
- rejected volume has no deferred write/population residue;
- upper clear volume remains absent while PLANNED;
- upper volume admits only after its full finite footprint is observed;
- catch-up does not force missing chunks;
- upper terrain actually materializes after admission;
- upper native population executes only after admission;
- pending upper catch-up reaches zero;
- `SF-IMP-0056 PHYSICAL ADMISSION PASS` is emitted with evidence consistent with the observed world;
- final acceptance record is committed;
- accepted-runtime documentation is updated;
- PR #63 is merged only after those gates are satisfied.

Until then, **SF-IMP-0055 remains the authoritative accepted runtime boundary.**
