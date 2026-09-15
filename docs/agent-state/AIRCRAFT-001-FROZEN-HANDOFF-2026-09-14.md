# AIRCRAFT-001 frozen compiler/runtime handoff — 2026-09-14

Status: **FROZEN RESEARCH / PROTOTYPE SNAPSHOT — DO NOT CONTINUE DEVELOPMENT ON THIS BRANCH**

This is the shutdown handoff for issue #545 and draft PR #546. It separates compiler authority, target lowering, exact-stack integration discoveries, reusable runtime seams, harness behavior, and unresolved aircraft functionality. The historical branch is evidence, not the production development shape.

## 1. Freeze identity and authority

- Repository: `ni-da-ba/skyforge`
- Historical branch: `aircraft-compiler-proof`
- Prototype/runtime freeze point before this handoff-only commit: `cc020c872b9c53198cebbb0ae9094097f23c9c45`
- `main` observed at shutdown: `3b6a16c4f300738cbbcffd5829fd71a65006a390`
- Merge base: `9bea844450d9bc1c2c2969f28754b96bc5f3f1d9`
- Divergence: current `main` has 28 commits not on the branch; the branch has 212 commits not on current `main`.
- The final PR comment records the final handoff commit SHA; implementation/runtime code immediately beneath it is exactly `cc020c...`.

Authority order for successors: current `main` and production contracts first; this frozen handoff/branch second as extraction evidence; PR discussion and Actions artifacts as historical runtime evidence. Do not wholesale merge, squash, or rebase this branch into `main`.

Preserve the #545 boundary:

`mission/design condition -> analytical constrained design -> backend-neutral AircraftDesignIR -> blockspace/assembly semantics -> target capability lowering -> runtime qualification`

The aircraft compiler remains a sibling of the structure compiler. Analytical aerodynamics must not be rewritten to mimic observed Create/Aeronautics behavior.

## 2. Reconstruction after the stale v0.18 PR body

After final v0.18 verifier commit `65d6cd9417d73648c58bb55657b65a32f8a1a52b`, the branch accumulated **82 AIRCRAFT-001 commits** through `cc020c...`; Appendix A preserves the exact list.
- **v0.19 — pilot interaction / assembled cockpit presence.** Added pilot interaction IR/profile/CLI/static proof, consumed v0.18 wheel placement, and extended exact-stack headless runtime to verify seat and Steering Wheel at compiled transformed coordinates. This does not prove player interaction.
- **v0.20 — genuine actual-client interaction.** Added actual-client binding IR/profile/CLI, integrated-server observer, disposable client/world preparation, bounded diagnostics, and genuine `MultiPlayerGameMode.useItemOn` paths. Accepted run `34891516947`, job `104135111551`, head `d45b9bfa37e15a202928644911b6d004c7018ecb`; same boundary re-passed at `cc020c...` in run `34917443445`, job `104218037148`.
- **v0.21 — natural Sable tracking / inherited parent translation experiment.** Requires ordinary post-dismount collision/movement-packet acquisition, persistent parent UUID identity, no tracking setter, and no player mutation during measurement. **Not accepted.** Preserve as partial/falsifying evidence and reduce to Platform fixtures before any aircraft retry.

## 3. Shutdown check state at `cc020c...`

| Workflow | Run | Result |
|---|---:|---|
| Aircraft compiler proof | `34917443464` | PASS; 123 compiler tests |
| Aircraft compiler cockpit route proof | `34917443448` | PASS |
| Aircraft compiler pilot interaction proof | `34917443467` | PASS |
| Aircraft compiler yaw control runtime | `34917443397` | PASS |
| Aircraft compiler exact-stack runtime | `34917443430` | PASS |
| Aircraft compiler cockpit route runtime | `34917443587` | PASS |
| Aircraft compiler pilot client runtime | `34917443445`, job `104218037148` | PASS |
| Aircraft compiler pilot tracking runtime | `34917443552`, job `104218037502` | **FAIL; evidence retained** |
| CI | `34917443474` | PASS |
| Wave C11 First Flight Recipe Runtime | `34917443504` | PASS |
| Wave C12 Bellanca B0 Assembly | `34917443538` | PASS |
| Portable Engine Cutoff Sable | `34917443563` | PASS |
| Portable Engine Cutoff Persistence | `34917443462` | PASS; unrelated to aircraft-control persistence |

Proof run `34917443464` ran the aircraft compiler suite: **123 tests, OK**, and regenerated the deterministic staged chain. This shutdown documentation is not a feature change.

A documentation-only checkpoint commit `cbebccb41888e7858bcdb17018efcbebf9643b32` then retriggered Actions against the unchanged implementation. Crucially, v0.21 tracking run `34919202186`, job `104223297486`, **PASSED**, while standalone v0.20 pilot-client run `34919202205`, job `104223297430`, **FAILED** at the known flaky seat-mount precondition. This establishes the v0.21 capability once with exact evidence while simultaneously proving the current actual-client harness is not reproducible enough to remain an aircraft-owned integration test.

## 4. Capability ledger

`PROVEN` means exact cited evidence for the named scope; code existence or a readiness flag is not downstream proof.
| Capability | Classification | Exact evidence / scope |
|---|---|---|
| Analytical aircraft design | `COMPILER_PROVEN` | Compiler proof `34917443464`, 123 tests; v0.1 digest `ac04df8a723f83a8ee0d19e01c0a375c34acccc2f33a5875b8efce47c985881c`. Constrained analytical pre-design only. |
| Blockspace transcription | `COMPILER_PROVEN` | `34917443464`; v0.2 digest `09d6e395c251a7afc801395d749add871deb098f45ed65c15a6f2898e8eacca6`. |
| Assembly-plan semantics | `COMPILER_PROVEN` | `34917443464`; v0.3 digest `9bce3a2bc3145cfcbd9815c40a88ac7099ad580c40e7b29196d9cbd3c926b978`. |
| Target placement manifest | `TARGET_LOWERING_PROVEN` | `34917443464`; v0.4/v0.9 digests `1c1a5f82...9cac6` / `06665b61...51b1c`; exact-stack placement exercised by v0.18 run `34775503848`. |
| Create sail/state lowering | `TARGET_LOWERING_PROVEN` | `34917443464`; v0.6 digest `36f7634422c1464042c38387948e51fda17332b96a199fb3936af3dac52525ab`. |
| Sable primary assembly | `FULL_SPECIMEN_PROVEN` | v0.18 exact-stack `34775503848`, job `103772797027`: complete routed specimen captured with primary assembly PASS and 147 total transferred cells across parent/children. |
| Super Glue domain generation | `FULL_SPECIMEN_PROVEN` | `34775503848`: eight compiled glue domains registered on routed parent; v0.11 digest `32ff60593075a46c8c7311a86ce4da2379383834e9f0b84ec7f467956837d8b2`. |
| Nested Propeller Bearing capture | `FULL_SPECIMEN_PROVEN` | `34775503848`: 9-cell propeller payload captured as nested child while parent/rudder partition stayed intact. |
| Propulsion topology | `FULL_SPECIMEN_PROVEN` | v0.12 acceptance `34761555582`, job `103735217638`; cumulatively re-passed by v0.18. |
| Governor/RPM/stress boundary | `FULL_SPECIMEN_PROVEN` | `34761555582` / `103735217638`: `[128,160,192,224,256]` RPM, positive margins through 224 and exact 0-SU boundary at 256 without overload. |
| Thrust direction | `FULL_SPECIMEN_PROVEN` | `34761555582` / `103735217638`: WEST bearing plus Aeronautics convention resolved vehicle force toward declared forward local `-X`; `thrustDirectionQualified=true`. |
| Swivel control-surface child | `FULL_SPECIMEN_PROVEN` | v0.15 `34770160804`, job `103758175268`, re-passed four-cell Swivel child capture; v0.18 retained one rudder dependency. |
| Create kinetic actuation | `FULL_SPECIMEN_PROVEN` | v0.15 `34770160804` cumulatively re-passed v0.14 real Create kinetic actuation after physical settle. |
| Physical rudder pose response | `FULL_SPECIMEN_PROVEN` | `34770160804` / `103758175268`: target `-62.400002°`, measured child yaw `-62.399996°`. |
| Measured yaw authority | `FULL_SPECIMEN_PROVEN` | `34770160804` / `103758175268`: 10 m/s controlled flow, lateral drag impulse `-0.3928057`, yaw impulse `+0.4611273`; required sign relation passed. |
| Commanded neutral return | `FULL_SPECIMEN_PROVEN` | v0.16 `34770883878`, job `103760144056`: inverse real kinetic command returned target to `0°`, physical yaw to `-8.78e-6°`, hidden cog to `0 RPM`. Not passive self-centering. |
| Steering Wheel source semantics | `FULL_SPECIMEN_PROVEN` | v0.17 exact-stack `34773904723`, job `103768360905`: real Simulated source/release semantics and bounded 16-RPM command path. |
| Deterministic cockpit-to-rudder routing | `FULL_SPECIMEN_PROVEN` | v0.18 runtime `34775503848` / `103772797027`; static proof `34775503870`. Compiled wheel/cog route deflected and explicitly returned rudder. |
| Actual-client Steering Wheel interaction | `ACTUAL_CLIENT_PROVEN` | Accepted `34891516947` / `104135111551`; re-pass `34917443445` / `104218037148`: genuine `useItemOn`, handler acquisition, active/release packet round trips. |
| Create seat interaction | `ACTUAL_CLIENT_PROVEN` | Same v0.20 runs: genuine seat use returned `SUCCESS` and mounted real `SeatEntity`. |
| Passenger/pilot mounting | `ACTUAL_CLIENT_PROVEN` | Same v0.20 runs: LocalPlayer and integrated ServerPlayer mount; ordinary crouch dismount on both. Excludes moving-body tracking. |
| Player/Sable tracking | `ACTUAL_CLIENT_PROVEN` | Final checkpoint run `34919202186`, job `104223297486`: natural collision/movement-packet acquisition, persistent parent UUID identity, post-dismount measurement, no tracking setter, no player mutation, and `playerSableTrackingQualified=true`. Earlier failures remain relevant to harness reliability. |
| Inherited parent translation | `ACTUAL_CLIENT_PROVEN` | `34919202186` / `104223297486`: parent delta `-0.2037849426`, player delta `-0.2214241174`, error `0.0176391748` <= `0.08`; `parentTranslationApplied=true`, `inheritedParentTranslationQualified=true`. Earlier `34912529231` failed at error `0.0811317128`, so reproducibility is a Platform concern. |
| Actual-client seat/tracking harness reproducibility | `PARTIAL` | On identical implementation, v0.21 `34919202186` passed the full mount/dismount/tracking chain while standalone v0.20 `34919202205` failed seat mount after `SUCCESS`. The capability has a passing proof, but the monolithic harness is timing-sensitive. |
| Control persistence / save-reload | `UNTESTED` | v0.20 explicitly reports `completedControlsPersistenceQualified=false`. Portable Engine Cutoff persistence is a different feature. |
| Pitch | `UNTESTED` | No pitch compiler/runtime qualification exists. |
| Roll | `UNTESTED` | No roll compiler/runtime qualification exists. |
| Atmosphere integration | `PARTIAL` | v0.12/v0.18 propulsion observes Aeronautics/Sable pressure/scaling in thrust measurements, but no aircraft atmosphere envelope/validation contract is qualified. |
| Closed-loop stability / handling | `UNTESTED` | No closed-loop handling/stability acceptance exists. |
| Stable flight | `UNTESTED` | Relevant markers remain fail-closed: `flightQualified=false` / `stableFlightVerified=false`. |
| Human flight / feel gate | `UNTESTED` | No human flight/feel pass occurred; remains a future manual gate after machine qualification. |

### Retained falsification

Superseded v0.13 is intentionally retained: exact-stack run `34764106343` left relative cell `[16,4,0]` behind. v0.13.1 corrected the topology instead of deleting the falsification. Preserve this pattern.

## 5. Latest successful runtime boundary

The latest successful aircraft runtime boundary is **v0.21 natural Sable player tracking plus inherited parent translation** on the unchanged frozen implementation.

Final checkpoint proof: run `34919202186`, job `104223297486`, documentation-only head `cbebccb41888e7858bcdb17018efcbebf9643b32` over implementation parent `cc020c...`.

Exact PASS evidence:

- `naturalTrackingAcquired=true`
- `acquisitionPath=client_sublevel_collision_then_movement_packet`
- `trackingParentIdentity=true`
- `liveTrackedParentUsed=true`
- `postDismountMeasurement=true`
- `parentTranslationApplied=true`
- `parentDeltaX=-0.20378494262695312`
- `playerDeltaX=-0.22142411742160562`
- `deltaError=0.01763917479465249 <= tolerance=0.08`
- `harnessTrackingSetterInvoked=false`
- `harnessPlayerMutationDuringMeasurement=false`
- `playerSableTrackingQualified=true`
- `inheritedParentTranslationQualified=true`
- `completedControlsPersistenceQualified=false`
- `pitchRollQualified=false`
- `flightQualified=false`

v0.20 remains the canonical previously accepted baseline (`34891516947` / `104135111551`) for actual Steering Wheel + seat interaction. The final checkpoint proves v0.21 can extend that boundary, but not that the full actual-client harness is deterministic.

## 6. v0.21 evidence history and reproducibility caveat — stop debugging on the aircraft

The final documentation-only rerun **passed** v0.21, so natural Sable tracking and inherited translation now have exact actual-client proof. Earlier failures remain architecturally important:

- `34912529231`: natural tracking/parent identity acquired, then translation error `0.08113171283958565` exceeded `0.08` by `0.00113171283958565`.
- `34912103460`: physics preparation failed with `RuntimeException: Body has been removed`, exposing sublevel/body lifecycle as its own seam.
- `34917443552`: combined v0.21 flow failed the seat-mount precondition.
- final-head standalone v0.20 `34919202205`, job `104223297430`, again failed seat mount even though final-head v0.21 `34919202186` passed the same mount/dismount precondition and complete tracking chain.

Therefore **capability evidence and harness reliability must be separated**. The passing v0.21 run is valid proof of the scoped capability; the alternating seat/physics/tolerance failures show the complete-aircraft actual-client harness is too timing-sensitive to serve as the future platform contract.

> If a complete-specimen integration seam fails more than once, stop debugging it on the complete aircraft. Reduce it to a minimal exact-stack integration fixture, establish the third-party/runtime contract there, then return to the aircraft.

Do not add another retry, tolerance relaxation, physics lookup workaround, teleport/re-anchor workaround, or seat timing workaround to this frozen aircraft branch.

## 7. Reusable seams for the Integration Platform agent

### Sable primary assembly
Current knowledge: exact Physics Assembler/Sable capture, transformed placement, transfer counts, mass/COM and glue registration work on the full specimen. Existing material is full-aircraft oriented, not a clean platform fixture.

Smallest future fixture: tiny rigid 3-D body + one Physics Assembler with deterministic transfer set/count and pose.

Diagnostics: placed cells, assembler BE readiness, trigger, sublevel UUID, transfer set/count, body validity, mass, pose. Outcomes: `FAIL_PLACEMENT`, `FAIL_BLOCK_ENTITY_INIT`, `FAIL_ASSEMBLY`, `FAIL_PHYSICS`, `TIMEOUT_ASSEMBLED_BODY`.

### Super Glue domain generation
Current knowledge: compiler emits bounded glue domains and full-aircraft exact stack demonstrates registration; no isolated runtime fixture.

Smallest future fixture: two small clusters requiring exactly one intentional glue domain, plus unglued negative control.

Diagnostics: domain bounds, Create glue registration, components before/after assembly, transfer count; `FAIL_PLACEMENT`, `FAIL_ASSEMBLY`, `TIMEOUT_GLUE_REGISTRATION`.
### Nested moving-body / Propeller Bearing capture
Current knowledge: full aircraft proves a 9-cell Propeller Bearing child survives primary Sable capture and assembles as its own moving child. No isolated fixture exists.

Smallest future fixture: Sable parent plate + one Propeller Bearing + minimum released-stack sail payload; parent assembly first, child second.

Diagnostics: parent retained cells, child transferred cells, bearing BE state, child identity, dependency relation, RPM, alive/removed state; `FAIL_CHILD_ASSEMBLY`, `FAIL_BLOCK_ENTITY_INIT`, `TIMEOUT_CHILD_CAPTURE`.

### Create kinetic network on a Sable body
Current knowledge: v0.14–v0.18 prove Create network reconstruction/sign propagation on the full aircraft. No small platform fixture exists.

Smallest future fixture: assembled Sable plate with source -> shaft/cog -> one observable consumer/actuator.

Diagnostics: block/BE classes, source RPM, network ID, theoretical/actual node speeds, actor initialization/rebuild state; `FAIL_KINETIC_REBUILD`, `FAIL_NETWORK`, `FAIL_BLOCK_ENTITY_INIT`, `TIMEOUT_NETWORK_READY`.

### Simulated Steering Wheel client/server behavior
Current knowledge: v0.17 proves source/release semantics; v0.20 proves genuine actual-client `useItemOn`, active packet and release packet. Rudder/propulsion/whole aircraft are unnecessary for this contract.

Smallest future fixture: minimal test platform + one Simulated Steering Wheel + server observer, with actual client interaction.

Diagnostics: gameplay-ready state, hit target, use result, handler acquisition, active/release packet count/value, held/released state, client/server positions; `FAIL_CLIENT_INTERACTION`, `FAIL_NETWORK`, `TIMEOUT_ACTIVE_PACKET`, `TIMEOUT_RELEASE_PACKET`.

### Create seat / passenger behavior
Current knowledge: v0.20 proves actual seat use, mount and crouch dismount; v0.21 combined flow can fail this precondition while standalone v0.20 passes on the same SHA. Immediate Platform reduction target.

Smallest future fixture: tiny Sable platform + one Create seat, no wheel/rudder/propulsion/aircraft logic; actual client mount/dismount.

Diagnostics: `lastSeatUseResult`, client/server positions, client/server vehicle class/entity ID, seat state, retry count, teleport-handshake state, mount/dismount events; `FAIL_CLIENT_INTERACTION`, `FAIL_PASSENGER_TRACKING`, `TIMEOUT_SEAT_MOUNT`, `TIMEOUT_SEAT_DISMOUNT`.
### Player tracking on a moving Sable body
Current knowledge: Sable source inspection plus `34912529231` demonstrate natural collision -> LocalPlayer tracking -> movement-packet server adoption and persistent parent UUID identity. Inherited-motion qualification failed; earlier body lifecycle also failed.

Smallest future fixture: rigid Sable floor + seat/landing surface, ordinary dismount/step/jump acquisition, then one bounded known parent translation. No aircraft controls.

Diagnostics: client/server tracking UUID, expected parent UUID, local/global positions, parent pre/post pose, packet coordinate mode, body/handle validity, player/parent deltas, error/tolerance, setter/mutation guards; `FAIL_PASSENGER_TRACKING`, `FAIL_PHYSICS`, `TIMEOUT_TRACKING`, `TIMEOUT_PARENT_TRANSLATION`.

### Generated asset save/reload
Current aircraft knowledge: **no completed aircraft-control persistence proof exists**. Unrelated Portable Engine Cutoff persistence workflows confer no aircraft proof.

Smallest future fixture: generated placement with one stateful Create BE and one Sable/assembly identity; save, unload/reopen, then verify serialization and rebuild.

Diagnostics: manifest digest, save/load events, BE/NBT/state before/after, Sable UUID, init state, network ID/speed; `FAIL_PERSISTENCE`, `FAIL_BLOCK_ENTITY_INIT`, `FAIL_NETWORK`, `TIMEOUT_RELOAD_READY`.

### Create mechanism initialization after generated placement
Current knowledge: v0.20 needed gameplay-screen, physical-settle, Swivel readiness and body-wake diagnostics before interaction. These are platform behavior, not aircraft design authority.

Smallest future fixture: generated source + shafts/cogs + one actuator/consumer, without an aircraft.

Diagnostics: expected/actual block state, BE class/init state, network membership/speed, actor state, last observed timeout state; `FAIL_PLACEMENT`, `FAIL_BLOCK_ENTITY_INIT`, `FAIL_KINETIC_REBUILD`, `TIMEOUT_MECHANISM_READY`.

## 8. Stall / hang audit

Monolithic debugging became inefficient in v0.20/v0.21 because independent third-party seams were repeatedly exercised through the complete specimen.

- pilot-client workflow: 30-minute job cap; world prep and client each have 300-second process timeout.
- pilot-tracking workflow: 30-minute job cap; world prep and client each have 300-second process timeout.
- gameplay readiness/acquisition is internally bounded but failure classification remains prose.
- Steering Wheel active/release waits are bounded but need typed packet/network timeout output.
- seat mount/dismount waits are bounded but current combined failure does not isolate client/server cause.
- v0.21 natural tracking acquisition: 40 ticks; translation settle: 40 ticks.
- older exact-stack workflows use 25-minute job caps and polling/kill loops; intermediate state is not sufficiently structured.
Future fixtures should terminate with `PASS` or classified outcomes such as `FAIL_PLACEMENT`, `FAIL_BLOCK_ENTITY_INIT`, `FAIL_ASSEMBLY`, `FAIL_CHILD_ASSEMBLY`, `FAIL_KINETIC_REBUILD`, `FAIL_NETWORK`, `FAIL_CLIENT_INTERACTION`, `FAIL_PASSENGER_TRACKING`, `FAIL_PHYSICS`, `FAIL_PERSISTENCE`, `TIMEOUT_<EXPECTED_STATE>`, and serialize the last observed state.

Actual-client logs also contain non-causal headless noise (session lookup timeout, missing flite/OpenAL, optional recipe registry warnings). Platform reporting must separate such noise from the acceptance cause.

## 9. Harness behavior that must not become product semantics

- v0.20 exposed a harness-only vanilla teleport handshake: a second `ServerPlayer.teleportTo` populated `awaitingPositionFromClient`, so 1.21.1 `handleUseItemOn` dropped seat input before Create saw it. Accepted v0.20 used final setup `setPos` re-anchor rather than synthetic seat interaction.
- v0.20/v0.21 setup invulnerability, position projection/re-anchor, physics wake/unpause, readiness waits and disposable-world preparation are qualification techniques, not aircraft compiler semantics.
- v0.21 forbids a Sable tracking setter and player mutation during inherited-translation measurement. Preserve that guard in reduced fixtures.
- v0.21 UUID handling uses canonical persistent identity across classloaders rather than Java object identity. This is integration knowledge, not AircraftDesignIR.
- Sable sublevel Java-object identity and physics-body validity can diverge despite UUID continuity. Resolve this in a minimal Platform contract, not with aircraft-specific reflection workarounds.

## 10. Successor ownership split

### Aircraft Compiler agent
Own #545's compiler boundary: analytical design, backend-neutral `AircraftDesignIR`, blockspace/assembly semantics, target capability requests/lowering, and extraction of accepted capabilities into small production PRs from fresh `main`.

Aircraft-specific work remaining after Platform contracts: define pitch/roll semantics from first principles; specify aircraft persistence requirements; integrate platform-qualified moving-player/persistence capabilities; build multi-axis controls only as compiler output; qualify atmosphere envelope if required; then closed-loop stability/handling, stable flight, and human flight/feel.

Do not port the 212-commit branch wholesale.

### Integration Platform agent
Own the seams in §7 and typed diagnostics in §8. Seat/passenger, moving-body tracking, Sable physics-body lifecycle, generated placement initialization and save/reload must be isolated before Aircraft depends on them.

### Candidate shared functional-asset layer — identification only
Aircraft and structures may later share a **small** vocabulary for assembly domains, kinetic routes, control sources, actuators/control surfaces, propulsion units, interaction anchors, and persistence/init policies. This is not authorization to design a universal mechanism framework now.

## 11. Frozen source map

- Compiler: `tools/aircraft_compiler/`
- Staged specimens: `tools/aircraft_compiler/specimens/`, including v0.1/v0.2 and target v0.4 through v0.21.
- Failed topology retained: `create_aeronautics_1.3.2_yaw_control_v0.13.json`; corrected topology: v0.13.1.
- Runtime harness: `skyforge-neoforge-1211/src/main/java/io/github/nidaba/skyforge/neoforge1211/SkyforgeAircraftCompiler*`.
Relevant workflows:

- `.github/workflows/aircraft-compiler-proof.yml`
- `.github/workflows/aircraft-compiler-runtime.yml`
- `.github/workflows/aircraft-compiler-yaw-authority-runtime.yml`
- `.github/workflows/aircraft-compiler-cockpit-route-proof.yml`
- `.github/workflows/aircraft-compiler-cockpit-route-runtime.yml`
- `.github/workflows/aircraft-compiler-pilot-interaction-proof.yml`
- `.github/workflows/aircraft-compiler-pilot-client-runtime.yml`
- `.github/workflows/aircraft-compiler-pilot-tracking-runtime.yml`

Historical design audits: `docs/design-audit/aircraft-compiler-first-principles-v0.1.md` and `docs/design-audit/aircraft-compiler-blockspace-v0.2.md`.

## 12. Local / unpushed disposition

Before this handoff, `git status --short` was clean and `origin/aircraft-compiler-proof` matched `cc020c...`. Downloaded Actions artifacts/logs under `/tmp` are disposable copies of GitHub-hosted evidence and contain no unique source changes. Python `__pycache__` is generated scratch, not authority.

No feature code is added during shutdown. The only branch modification after `cc020c...` is checkpoint/handoff documentation. All valid feature code, fixtures, tests, and useful failed experiments already exist in pushed branch history or cited Actions evidence.

## Appendix A — all AIRCRAFT-001 commits after final v0.18 verifier

Generated from `65d6cd9417d73648c58bb55657b65a32f8a1a52b..cc020c872b9c53198cebbb0ae9094097f23c9c45` over AIRCRAFT compiler/runtime/workflow paths. Parallel non-AIRCRAFT commits are omitted from this appendix but remain in Git history.

```text
5810096d 2026-09-13T13:50:23-05:00 AIRCRAFT-001 v0.19: define pilot interaction qualification contract
9d58a309 2026-09-13T13:50:34-05:00 AIRCRAFT-001 v0.19: add pilot interaction profile
9c669a19 2026-09-13T13:50:45-05:00 AIRCRAFT-001 v0.19: add pilot interaction CLI
c5a077f9 2026-09-13T13:51:01-05:00 AIRCRAFT-001 v0.19: test pilot interaction contract
353e83eb 2026-09-13T13:51:41-05:00 AIRCRAFT-001 v0.19: add pilot interaction static proof
d54995f2 2026-09-13T13:53:35-05:00 AIRCRAFT-001 v0.19: consume wheel resource from v0.18 placement schema
ea730f44 2026-09-13T13:53:56-05:00 AIRCRAFT-001 v0.19: mirror v0.18 wheel placement schema in tests
c82705b1 2026-09-13T13:54:32-05:00 AIRCRAFT-001 v0.19: verify assembled cockpit presence headlessly
f8ee233b 2026-09-13T13:54:48-05:00 AIRCRAFT-001 v0.19: patch headless assembled cockpit presence probe
1a2828fd 2026-09-13T13:57:13-05:00 AIRCRAFT-001 v0.19: test assembled cockpit presence runtime patch
e871f170 2026-09-13T13:57:45-05:00 AIRCRAFT-001 v0.19: extend exact-stack runtime through cockpit presence
daeac5fe 2026-09-13T14:01:21-05:00 AIRCRAFT-001 v0.19: fix coordinate-chain runtime assertion
070a0803 2026-09-13T14:04:53-05:00 AIRCRAFT-001 v0.19: use typed-safe coordinate chain assertion
dd1d97d9 2026-09-13T14:05:08-05:00 AIRCRAFT-001 v0.19: guard typed-safe coordinate assertion
84ed0493 2026-09-13T17:44:46-05:00 AIRCRAFT-001 v0.20: define real-client binding contract
86dd907b 2026-09-13T17:44:56-05:00 AIRCRAFT-001 v0.20: add real-client binding profile
586284e3 2026-09-13T17:45:03-05:00 AIRCRAFT-001 v0.20: add real-client binding CLI
5522f2b4 2026-09-13T17:45:23-05:00 AIRCRAFT-001 v0.20: test real-client binding contract
1557b388 2026-09-13T17:46:30-05:00 AIRCRAFT-001 v0.20: bridge assembled cockpit to actual client
fbf9b9e9 2026-09-13T17:46:57-05:00 AIRCRAFT-001 v0.20: observe actual client effects on server
b05b815c 2026-09-13T17:47:39-05:00 AIRCRAFT-001 v0.20: exercise actual client cockpit interaction
da415601 2026-09-13T17:47:54-05:00 AIRCRAFT-001 v0.20: install actual-client server observer
3bee9d2a 2026-09-13T17:48:11-05:00 AIRCRAFT-001 v0.20: patch runtime for actual-client handoff
08c19081 2026-09-13T17:48:20-05:00 AIRCRAFT-001 v0.20: test actual-client runtime patch
f3dec3d8 2026-09-13T17:50:14-05:00 AIRCRAFT-001 v0.20: add blank-world client preparation gate
18e5bc43 2026-09-13T17:50:29-05:00 AIRCRAFT-001 v0.20: install client world preparation gate
6cf8c684 2026-09-13T17:50:43-05:00 AIRCRAFT-001 v0.20: add disposable actual-client run patch
894a903b 2026-09-13T17:50:51-05:00 AIRCRAFT-001 v0.20: test disposable client run patch
74d051d6 2026-09-13T18:00:05-05:00 AIRCRAFT-001 v0.20: qualify actual client interaction
302c5f82 2026-09-13T18:02:25-05:00 AIRCRAFT-001 v0.20: isolate client qualification wiring
ace913df 2026-09-13T18:04:08-05:00 AIRCRAFT-001 v0.20: target Kotlin Gradle script
74ee91ed 2026-09-13T18:07:47-05:00 AIRCRAFT-001 v0.20: isolate Gradle qualification failure
98f9a969 2026-09-13T18:10:41-05:00 AIRCRAFT-001 v0.20: inject client runs inside ModDev runs block
e71354fc 2026-09-13T18:10:48-05:00 AIRCRAFT-001 v0.20: guard run-block patch placement
c72ca169 2026-09-13T20:00:38-05:00 AIRCRAFT-001: expose v0.20 client runtime diagnostics
e351fd13 2026-09-13T20:10:37-05:00 AIRCRAFT-001: bound v0.20 client diagnostic run
17c04cd8 2026-09-13T20:35:51-05:00 AIRCRAFT-001: retain v0.20 client failure artifacts
14858c98 2026-09-13T20:36:31-05:00 AIRCRAFT-001: restore v0.20 compiler input
e3e183f8 2026-09-13T20:38:13-05:00 AIRCRAFT-001: fix v0.20 blockspace specimen path
375775d5 2026-09-13T22:21:12-05:00 AIRCRAFT-001: report v0.20 client readiness on timeout
7288df39 2026-09-13T22:37:41-05:00 AIRCRAFT-001: seed headless client options for v0.20
1d6a7ba9 2026-09-13T22:52:27-05:00 AIRCRAFT-001: keep v0.20 player in Sable global space
d0e6485d 2026-09-13T22:53:38-05:00 AIRCRAFT-001: aim v0.20 interactions in Sable global space
18ce591e 2026-09-14T00:15:30-05:00 AIRCRAFT-001: expose v0.20 cockpit settle diagnostics
d60e67d1 2026-09-14T00:30:39-05:00 AIRCRAFT-001: project v0.20 player through bound parent pose
a2188c5d 2026-09-14T00:42:52-05:00 AIRCRAFT-001: await client Sable tracking before wheel use
65ee0b06 2026-09-14T00:57:56-05:00 AIRCRAFT-001: expose v0.20 Simulated wheel predicates
a8debb5a 2026-09-14T01:04:59-05:00 AIRCRAFT-001: bound v0.20 physical settle observation
328497d6 2026-09-14T01:13:06-05:00 AIRCRAFT-001: keep v0.20 pilot aligned for bounded interaction
4e10f964 2026-09-14T01:20:53-05:00 AIRCRAFT-001: unpause v0.20 physics proof deterministically
94c7aa2b 2026-09-14T08:10:57-05:00 AIRCRAFT-001: bound v0.20 pilot fall-damage setup
a69361bd 2026-09-14T08:18:17-05:00 AIRCRAFT-001: aim v0.20 outside wheel value box
64e37c18 2026-09-14T08:30:39-05:00 AIRCRAFT-001: stabilize v0.20 swivel actor readiness
fece2193 2026-09-14T08:31:32-05:00 AIRCRAFT-001: fix v0.20 readiness flow scoping
cc364509 2026-09-14T08:52:28-05:00 AIRCRAFT-001: wake v0.20 Sable bodies before physical proof
f0fe7a77 2026-09-14T08:53:13-05:00 AIRCRAFT-001: assert v0.20 deterministic body wake setup
14a5ab61 2026-09-14T09:03:47-05:00 AIRCRAFT-001: expose v0.20 Steering Wheel active-tick state
ee0e61bf 2026-09-14T09:16:27-05:00 AIRCRAFT-001: expose v0.20 Swivel servo impulse state
3bf09173 2026-09-14T09:24:20-05:00 AIRCRAFT-001: pin v0.20 Sable bodies during physical proof
e384b43b 2026-09-14T09:33:00-05:00 AIRCRAFT-001: await gameplay screen before v0.20 wheel hold
8c5fc625 2026-09-14T09:56:32-05:00 AIRCRAFT-001: expose v0.20 seat boundary state
d2b2f96b 2026-09-14T10:03:49-05:00 AIRCRAFT-001: inspect v0.20 seat plot cell directly
508c71d3 2026-09-14T15:06:46-05:00 AIRCRAFT-001 v0.20 make seat setup re-anchor one-shot
d45b9bfa 2026-09-14T15:12:31-05:00 AIRCRAFT-001 v0.20 avoid seat reanchor teleport handshake
1fba3533 2026-09-14T16:09:37-05:00 AIRCRAFT-001 v0.21 add natural pilot tracking proof
a7c32500 2026-09-14T16:33:51-05:00 AIRCRAFT-001 harden real seat interaction against CI race
6f80aa88 2026-09-14T18:23:18-05:00 AIRCRAFT-001 v0.21 compare Sable parent by persistent identity
b326183a 2026-09-14T18:30:59-05:00 AIRCRAFT-001 v0.21 order real wheel release after active observation
a8a47a2a 2026-09-14T18:45:53-05:00 AIRCRAFT-001 v0.21 bound disposable world preparation
72ec3e33 2026-09-14T18:46:02-05:00 AIRCRAFT-001 v0.21 specify persistent Sable parent identity
6a203679 2026-09-14T18:46:19-05:00 AIRCRAFT-001 v0.21 enforce persistent Sable parent identity
a1f59156 2026-09-14T18:46:33-05:00 AIRCRAFT-001 v0.21 regress persistent parent identity contract
cc1a9ef7 2026-09-14T18:47:56-05:00 AIRCRAFT-001 use shared Java Gradle setup in tracking runtime
ef948bf0 2026-09-14T18:48:23-05:00 AIRCRAFT-001 use shared Java Gradle setup in cockpit runtime
07460dfc 2026-09-14T18:48:55-05:00 AIRCRAFT-001 modernize and bound pilot client runtime setup
487914d9 2026-09-14T18:49:27-05:00 AIRCRAFT-001 use shared Java Gradle setup in exact-stack runtime
0afb6c17 2026-09-14T18:49:53-05:00 AIRCRAFT-001 use shared Java Gradle setup in yaw runtime
ea6fa541 2026-09-15T00:03:16+00:00 AIRCRAFT-001 v0.21 expose reflection failure cause
0a2b4d2e 2026-09-15T00:05:02+00:00 AIRCRAFT-001 v0.21 acquire tracking through natural collision path
3bb55269 2026-09-15T00:10:51+00:00 AIRCRAFT-001 v0.21 parse Sable UUID across classloader
fd4dfec1 2026-09-15T00:16:50+00:00 AIRCRAFT-001 v0.21 use live tracked Sable parent
cc020c87 2026-09-15T01:29:02+00:00 AIRCRAFT-001 v0.21 resolve canonical live physics parent

```

## 13. Shutdown instruction to successors

Treat PR #546 as a frozen research notebook. Start production extraction from current `main`, cite this handoff and exact historical runs, and keep every unqualified capability fail-closed until a smaller contract-specific proof exists.
