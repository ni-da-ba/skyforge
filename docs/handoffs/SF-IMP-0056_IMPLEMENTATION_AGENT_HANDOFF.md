# Skyforge SF-IMP-0056 Implementation Agent Handoff

**Prepared:** 2026-09-02 (America/Chicago)  
**Repository:** `ni-da-ba/skyforge`  
**Visibility:** public  
**Accepted runtime boundary:** SF-IMP-0055  
**Active milestone:** SF-IMP-0056 — physical volume admission  
**Active PR:** #63 — `SF-IMP-0056: prevent physical occupancy collisions`  
**Active branch:** `agent/sf-imp-0056`

This is the authoritative operational handoff for the next implementation agent. SF-IMP-0056 is implemented but **not accepted**. Do not advance the accepted boundary until the dedicated interactive proof and final exact-head acceptance gates pass.

## 1. Final repository / hardening state

Skyforge is public. Repository hardening and the immediately available validated maintenance are merged to `main` at:

```text
68ea314a66ca5f18c6be4113987a1c07bf081cff
```

PR #75 consolidated the remaining GitHub Actions hardening and passed full PR CI #351 before merge. PR #76 then applied the grouped low-risk build maintenance — Gradle wrapper 9.7.1 and JUnit 5.14.4 — after full CI #353 passed.

Current CI hardening includes:

- `permissions: contents: read`;
- `persist-credentials: false` on checkout;
- no `pull_request_target` workflow;
- standard GitHub-hosted `ubuntu-24.04` runner;
- 30-minute job timeout;
- concurrency cancellation for superseded runs;
- immutable full-SHA action pinning;
- `actions/checkout` 7.0.1;
- `actions/setup-java` 6.0.0;
- `gradle/actions` 6.3.0 for wrapper validation and Gradle setup;
- `actions/upload-artifact` 7.0.1;
- fork-safe artifact publication;
- compact evidence bundle with seven-day retention;
- artifact publication is non-blocking so storage exhaustion cannot misclassify a correct build.

Dependabot is configured to:

- group Gradle minor/patch maintenance;
- group GitHub Actions updates into one maintenance PR;
- limit GitHub Actions maintenance PR churn;
- defer the JUnit 6 semver-major migration for deliberate compatibility review rather than automatic hygiene.

The old individual action-upgrade PRs were closed as superseded by #75. The JUnit 6 PR was explicitly closed as deferred. The earlier standalone Gradle-wrapper PR was replaced by grouped PR #76. No project history was rewritten or pruned.

### Administrative repository settings still owned by the repository owner

At the last API-visible check, `main` had no branch-protection/ruleset policy. The connected GitHub integration can inspect but cannot create these administrative settings, and no installable GitHub administration plugin was available.

Recommended `main` ruleset:

- block force pushes;
- block branch deletion;
- require pull requests before merge;
- require the repository CI check before merge;
- do not impose unnecessary multi-reviewer requirements on the present solo-maintainer workflow.

Also verify in GitHub's code-security settings that secret scanning, push protection, and private vulnerability reporting are enabled where available. These settings are outside the integration's readable/writable sensitive endpoint surface and must not be assumed from this document.

## 2. Feature branch synchronization state

Current `main` was merged into `agent/sf-imp-0056` with normal two-parent history. No force push or history rewrite was used.

Latest maintenance synchronization commit:

```text
ba9d2fe7062fe54310f18c0b2af51c99bd61002f
```

The merge preserves the SF-IMP-0056-only `physicalAdmissionClient` Gradle run while updating the shared wrapper and JUnit 5 maintenance line. This handoff document is a documentation-only commit after that synchronization. Require CI to be green on the exact final branch head before accepting SF-IMP-0056.

The stacked SF-IMP-0057 branch/PR #77 was also synchronized to this hardened SF-IMP-0056 base with normal merge history; its post-processing implementation was not altered by repository cleanup.

## 3. Historical CI #334 — correct classification

Do not diagnose historical run #334 as an SF-IMP-0056 regression.

On pre-publication head:

```text
76b2c8cd72a608ad1c15950dfc998d085008fa88
```

these gates passed:

- Gradle build;
- backend-independence verification;
- unit tests;
- NeoForge/FML test bootstrap;
- fixed-seed evidence generation;
- suspended-volume evidence generation.

The run failed afterward only because the old artifact upload returned:

```text
Failed to CreateArtifact: Artifact storage quota has been hit.
```

That was artifact-storage infrastructure failure, not code failure and not compute-minute exhaustion. Later public feature runs #349 and #350 passed before the final hardening sync; PR #75 CI #351 validated the upgraded workflow; PR #76 CI #353 validated the grouped Gradle/JUnit 5 maintenance.

## 4. Architectural problem owned by SF-IMP-0056

SF-IMP-0052 established generation-domain isolation:

```text
BASE_WORLD generates without Skyforge ownership
        -> native terrain / structures / decoration complete
        -> explicit Skyforge island domain may realize later
```

SF-IMP-0055 then exposed a separate composition problem: completed native content can physically occupy coordinates that a later Skyforge island intends to claim.

The required distinction is:

```text
generation-domain isolation != physical occupancy compatibility
```

SF-IMP-0056 prevents destructive Skyforge realization until a planned exact volume reaches a terminal physical-admission decision based on completed native occupancy evidence.

This is a Minecraft/NeoForge backend lifecycle concern. Do not push Minecraft block-state, chunk-lifecycle, or registry concepts into backend-neutral modules merely to simplify this adapter problem.

## 5. Implementation already present

The active branch already contains the integrated physical-admission path.

### Physical lifecycle

```text
PLANNED -> ADMITTED
PLANNED -> REJECTED
```

Terminal decisions are not reopened.

### Exact native occupancy survey

`SkyforgeNativeChunkOccupancySurvey` inspects only coordinates the compiled exact Skyforge volume actually owns as solid. The first conservative policy treats any pre-existing non-air native block at an owned solid coordinate as a conflict and records native block/block-entity evidence.

### Whole-volume admission ledger

`SkyforgePhysicalVolumeAdmissionLedger` derives the finite Minecraft-chunk footprint of every planned exact volume.

Required semantics:

- one conflict rejects the entire volume immediately;
- clear evidence cannot admit until every required footprint chunk reports;
- duplicate equivalent evidence is idempotent;
- changed or out-of-contract evidence does not silently rewrite identity;
- terminal states cannot reopen.

### Runtime admission stage

`SkyforgePhysicalVolumeAdmissionStage` sits between completed native generation and destructive Skyforge realization.

It provides:

- fail-closed solid-write gating while an exact owner is `PLANNED`;
- write authority only for `ADMITTED` owners;
- no write authority from `REJECTED` owners;
- population gating until physical admission;
- immutable deferred-realization evidence;
- no long-lived retained mutable `WorldGenRegion` or chunk references.

### Non-forcing deferred catch-up

`SkyforgePhysicalVolumeCatchupService` services deferred realization only when target chunks already exist as stable loaded `LevelChunk`s.

It uses:

```text
ServerChunkCache#getChunkNow
```

Missing chunks remain pending until Minecraft loads them independently. SF-IMP-0056 must not create generation tickets merely to complete a planned island footprint.

After terrain catch-up, the accepted exact-volume native surface-population stage is replayed. The existing population coordinator remains authoritative for idempotency.

### Integrated ownership points

Physical admission is wired into the NeoForge chunk adapter, concrete chunk writer, surface stage, native surface population stage, runtime/mod binding, domain mixin support, and dedicated tests.

## 6. Immediate task for the next implementation agent

Do **not** start a new architecture milestone. Continue SF-IMP-0056 by running its existing interactive proof.

Run:

```text
:skyforge-neoforge-1211:runPhysicalAdmissionClient
```

Use a **new disposable Skyforge Development world**.

Required terminal marker:

```text
SF-IMP-0056 PHYSICAL ADMISSION PASS
```

The marker alone is insufficient. Inspect the emitted evidence and the resulting world state.

## 7. Runtime proof contract

The fixture plans two deterministic tablelands across a 5x5 / 25-chunk footprint.

### Lower volume — forced rejection

The lower exact volume deliberately intersects the vanilla Overworld bedrock floor.

It must:

- reach terminal `REJECTED`;
- retain the native conflict as evidence;
- preserve the conflicting native block exactly;
- leave no destructive Skyforge terrain behind;
- leave no pending deferred realization;
- never receive island-owned population.

### Upper volume — delayed whole-volume admission

The upper exact volume occupies clear high air.

It must:

- remain physically absent while `PLANNED`;
- remain unadmitted until all 25 required footprint chunks provide clear evidence;
- transition to `ADMITTED` only after the complete finite footprint reports;
- catch up terrain only through already-loaded stable chunks;
- never force unavailable chunks into generation;
- materially realize its terrain after admission;
- execute native taiga surface population only after admission;
- finish with zero pending catch-up work.

## 8. Non-regression rules

Preserve all of the following while finishing SF-IMP-0056:

- BASE_WORLD remains observationally isolated from Skyforge.
- Generation-domain isolation and physical occupancy admission remain separate concerns.
- Do not restore global highest-surface competition between native terrain and all Skyforge volumes.
- Exact island operations stay scoped to exact `SkyIslandWorldVolumeId` ownership unless a higher-level operation explicitly owns multiple volumes.
- `PLANNED` volumes fail closed and cannot partially realize.
- `REJECTED` volumes leave no destructive terrain or population residue.
- Deferred catch-up does not force arbitrary future chunks to generate.
- Island-owned native population cannot run before `ADMITTED` while the admission stage is installed.
- Population replay remains idempotent.
- Mutable Minecraft generation objects are not retained as deferred work.
- Minecraft/NeoForge APIs remain out of `skyforge-kernel`, `skyforge-model`, `skyforge-recipes`, and `skyforge-world` unless a genuinely backend-neutral abstraction is first demonstrated.
- Preserve deterministic identity, exact three-dimensional ownership, canonical evidence, and accepted SF-IMP-0052/0054/0055 behavior.
- Preserve repository CI least privilege and visible engineering history.

## 9. If the runtime proof fails

Fix the narrowest layer that actually owns the failure.

Do not broaden the architecture merely to make the fixture pass. After any behavioral correction:

1. run repository exact-head CI;
2. rerun the interactive physical-admission proof in a fresh disposable world;
3. inspect proof semantics, not merely the final marker.

## 10. Acceptance procedure / definition of done

SF-IMP-0056 is complete only when all of these are true:

1. Exact final implementation head passes repository CI.
2. `runPhysicalAdmissionClient` emits `SF-IMP-0056 PHYSICAL ADMISSION PASS` on that implementation.
3. Lower rejection preserves native state and leaves no residue.
4. Upper volume proves no partial realization while planned.
5. Admission requires the complete finite footprint.
6. Catch-up uses already-available chunks and does not force generation.
7. Upper terrain materially realizes after admission.
8. Native population begins only after admission and retains idempotency.
9. Pending catch-up reaches zero.
10. An SF-IMP-0056 acceptance record is added under `docs/reviews/` with exact implementation SHA, CI run, runtime command, marker, and observed invariants.
11. Add/update an ADR only if the final implementation establishes a material architectural decision not already captured; verify the next unused ADR number first.
12. README and current-runtime documentation change from “accepted through SF-IMP-0055” to SF-IMP-0056 only after acceptance is real.
13. PR #63 is merged only after the acceptance evidence and exact-head CI are green.

Until then, **SF-IMP-0055 remains the authoritative accepted runtime boundary.**

## 11. Explicitly out of scope for SF-IMP-0056

Do not broaden this milestone into:

- terrain beautification or morphology tuning;
- production world-plan/config bootstrap;
- ores, caves, underground decoration, or hydrology;
- structure population;
- generalized optional-mod compatibility;
- persistent world-plan serialization;
- production caching/spatial-index policy;
- new hierarchy levels;
- player-facing release polish.

Those remain follow-on engineering decisions after physical occupancy is demonstrably safe.
