# Skyforge AUDIT Agent State

**Lane:** AUDIT  
**Status:** Canonical live lane handoff  
**Updated:** 2026-09-07 (America/Chicago)  
**Main snapshot before AUDIT-0007 reconciliation:** c739fe9a521e2c75f640bbfcf79eee6abd0298cf

Read first:

- [Program charter](PROGRAM_CHARTER.md)
- [Cross-lane contracts](CROSS_LANE_CONTRACTS.md)
- current lane state files and Git/PR/issue/test evidence

Repository `main`, merged history, source/tests, and exact-head evidence override stale summaries.

## MERGED / ACCEPTED AUDIT BOUNDARY

### AUDIT-0001 — authoritative state reconstruction

PR #246 / merge `cab1be65f370f3f81a6d8019ed3db23bcff6f49f`.

Established repository-first reconstruction, `docs/agent-state/` as the single live state namespace, explicit cross-lane/manual gates, and duplicate-state cleanup discipline.

### AUDIT-0002 — runtime-documentation reconciliation

PR #249 / merge `d0e4329ed95088a38a5a0f8f2880736a34986616`.

Reconciled reviewer-facing runtime documentation through accepted SF-IMP-0079. Accepted head `ed02e6e033f5b2001fba7ff4ab6ae9f4ba86a967`; exact-head CI `34079407149` passed.

### AUDIT-0003 — active convergence-gate audit

PR #255 / merge `e7689121aee85c71573b971393d82823f3d86028`.

Recorded the SF-IMP-0080 compile/substrate failure progression, AUTH-0087/C14 acceptance state, and remaining C11/#240/Bellanca prerequisites without taking over producer-lane work.

### AUDIT-0004 — accepted SF-IMP-0080 ecology boundary

Accepted by the merge that places this update on `main`.

AUDIT-0004 records the completed ecology-showcase convergence; it does not create Implementation behavior.

SF-IMP-0080 / PR #248 merged as `e721b512d7aaf402d671b8d0c72db802f9bd0912`; Implementation recorded the accepted boundary as `dfb439b9b33b24327cd5f110dabee6e51cb8a491`. Issue #194 is closed.

The acceptance path is evidence-backed:

- the failed compact-showcase ecology prototype was abandoned rather than cosmetically patched;
- literal registration corruption was repaired;
- the first real lifecycle run exposed an unsuitable ocean/gravel-backed native-surface substrate;
- land-backed seed `1405130932537311389` produced strong forest/taiga native ecology;
- repository CI, unchanged current-capability showcase, dedicated ecology preparation/reopen, SF-IMP-0070 characterization, and retained compatibility gates passed on the accepted integration line;
- mutation-inert actual-client reopen preserved ecology and biome presentation;
- the explicit #194 human-eye gate passed.

Accepted visible result included land substrate, trees/foliage, non-tree plants, meaningful forest-versus-taiga distinction, plausible vegetation attachment, and correct persistence.

One non-blocking limitation remains: moving a short distance away from terrain may return client-visible biome presentation to BASE_WORLD ambience. This matches the deliberately narrow SF-IMP-0058 immediate-surface envelope and is tracked separately as issue #261; it does not reopen #194.

No producer-lane capability is created by AUDIT-0004.

### AUDIT-0005 — post-ecology convergence audit

Accepted by the merge that places this update on `main`.

AUDIT-0005 records repository-visible convergence after AUDIT-0004 without creating producer-lane behavior:

- AUTH-0088 and AUTH-0089 remain accepted; AUTH-0090 / PR #276 is now also merged/accepted as the exact area-weighted regional ecological-opportunity profile. Accepted head `744e2d76`, CI `34088215137` PASS, runtime merge `1a2f9cc2`, and durable-state repair PR #278 are all complete;
- Content C16 remains accepted; Content C17 / PR #272 is now also merged/accepted, proving stock GPS requires a non-collinear host constellation inside ordinary wireless range while Ender-backed GPS inherits the mature-bypass status. Exact-head C17, repository CI, showcase persistence, and performance gates passed on `9373c307`;
- SF-IMP-0081 / PR #265 is merged/accepted as the first exact AUTH-0083 Massif Minecraft carrier: accepted head `0e3dddae90a8af605dd959132ce865412e92e5c0`, runtime merge `bac972eb7e8d772a250d292602e109428d06514a`, Implementation state repair `0566c7909219a5281609da3445d8a082d0be02c6`, and shared-contract update `001bc7aa6dbd01d94d8a92023f8c5fc7831d3020`; first #214 human Massif review passed while full #214 remains open;
- issue #267 records the non-blocking Massif traversal-cadence/lumpiness observation; do not flatten the accepted carrier from one specimen;
- issue #269 reserves SF-IMP-0082 for Tableland, Spine, Basin, and Lobed using the generalized exact-carrier path;
- SF-IMP-0082 / PR #273 and Content C18 / PR #277 remain reviewed in-progress producer work. AUTH-0090 / PR #276 is merged/accepted and its durable state is repaired by PR #278.

No producer-lane capability is created by AUDIT-0005.

### AUDIT-0006 — Music / Audio lane integration

Accepted by the merge that places this update on `main`.

AUDIT-0006 makes Music / Audio a first-class program lane for all future Audit reconstruction,
contract, drift, persistence, and human-gate checks. It does not accept any musical cue or runtime
audio behavior by itself.

Audited MUS-0001 / PR #159 boundary at branch head `1825d3931125f37e8be48abc46b0b1b13ebf2ed2`:

- MUS-0001 remains **draft / unmerged / non-mergeable** and is 88 commits ahead /
  1069 behind `main@34bd884f` at this audit point;
- no canonical `docs/agent-state/MUSIC_STATE.md` exists yet;
- no current exact-head repository/source-verification CI exists for the Music branch;
- Track 00 V2F.1 remains the canonical frozen source; two range-repair A/B candidates are explicitly
  non-canonical pending human BBCSO audition;
- Track 01 remains frozen and structural/range-clean, with Track-11 HC plugin-state provenance still
  incomplete;
- Track 02 / **The Lord of Empty Miles** has a corrected, checksum-pinned canonical BBCSO percussion
  source and accepted repaired-render identity; its two one-semitone-over trumpet events remain a
  declared non-blocking frozen-source exception;
- Track 03 / **Count the Leagues** is frozen/complete at the 72-bar Second Horizon source;
- Track 06 storm is compositionally frozen at Draft 02.3, but its exact accepted MIDI is missing and
  must not be reconstructed from documentation or conversational memory;
- the principal-theme motif is captured but the composition remains pending;
- the PR description itself still contains obsolete Track-03 experiment state and must be reconciled
  to the current cue slate before merge;
- cue manifests are detailed, but source identity/instrument alignment is not yet protected by a
  repository-executable verifier.

Required MUS-0001 repository-acceptance gates:

1. recompose onto current `main` without losing exact source/manifests/history;
2. add a concise lane-owned `MUSIC_STATE.md` under `docs/agent-state/`;
3. add deterministic source verification for canonical gzip/MIDI hashes, SMF structure, conductor
   tempo/meter, canonical track order, documented BBCSO playable ranges, and cue-specific
   PERC/TP/HC state/exception declarations;
4. keep Track 06 explicitly non-canonical until the exact accepted Draft 02.3 MIDI is recovered;
5. preserve Track 00's repair candidates as non-canonical until the human A/B listening gate;
6. reconcile the PR summary to the actual current cue state;
7. run exact-head repository/source verification after synchronization;
8. keep large WAV masters outside ordinary Git until an explicit versioned artifact strategy exists.

Cross-lane rule: musical authorship/source identity belongs to Music / Audio; world truth remains
Authorship-owned, gameplay/experience meaning remains Content-owned, and Minecraft/adaptive playback
runtime remains Implementation-owned. No runtime adaptive-music contract is accepted yet.

### AUDIT-0007 — post-SF-IMP-0082 convergence reconciliation

Accepted by the merge that places this update on main.

AUDIT-0007 records current repository state after the accepted SF-IMP-0082 built-in morphology tranche and subsequent parallel producer movement. It creates no producer-lane behavior.

Verified boundaries:

- SF-IMP-0082 / PR #273 merged as 3c48828924b0cf4ac7f1184c493d60ef605bf84a from synchronized head 88f0898b9913d14875b35ec1fb6214af6b0e53b5;
- exact-head repository build, SF-IMP-0070 characterization, Showcase Acceptance, Tableland / Spine / Basin / Lobed plus retained Massif, ecology, persistence/reopen, C16 wireless, C17 GPS, portal, runtime, and compile/smoke gates passed with no failing checks;
- project-owner #214 review passed the four-family SF-IMP-0082 tranche; issue #269 is closed and full #214 remains open;
- issue #267 remains the non-blocking Massif traversal-cadence question; issue #283 separately tracks Tableland-vs-Massif plateau identity;
- Implementation now owns active SF-IMP-0083 / issue #284 / draft PR #285 for the remaining 20 exact built-in AUTH-0083 seed/scale specimens;
- AUTH-0091 / PR #279 merged/accepted as c739fe9a521e2c75f640bbfcf79eee6abd0298cf after exact-head candidate fbebcf5d passed its repository/showcase/performance/retained compatibility matrix;
- the owning Authorship ledger still reports AUTH-0090 as highest at this snapshot and requires an Authorship-owned durable-state repair.

Parallel producer state is preserved rather than rewritten. Audit/shared coordination state is reconciled only where repository evidence makes the prior snapshot stale.
### Highest AUDIT milestone: AUDIT-0008 — agent / execution process-health governance

Accepted by the merge that places this update on `main`.

AUDIT-0008 extends Audit from repository-state correctness into explicit **agent/process convergence health**. It changes no producer behavior or acceptance result.

Audit now treats the following as first-class evidence:

- branch divergence relative to current `main`;
- repeated CI failure/rework cycles that do not produce new diagnostic information;
- contradictory, stale, or repeatedly repaired durable handoffs;
- long-lived branches that continue accumulating work while integration history moves far ahead;
- repeated synchronization/merge churn that obscures the actual producer delta;
- large commit sequences without a meaningful verified boundary;
- explicit user reports that an agent/session has expired, become slow, or is approaching context limits.

Audit cannot see another agent's private token/context state. A context-blowup flag must therefore be described as a **process-risk inference**, unless the user explicitly reports the session condition.

Operational classifications are intentionally qualitative rather than hard numeric cutoffs:

- **HEALTHY / ACTIVE** — work is producing new evidence and converging toward a bounded acceptance gate;
- **WATCH** — drift, repeated failures, or state lag require intervention soon but useful convergence continues;
- **STALE / DORMANT** — work is materially behind and should not resume without fresh current-main reconstruction;
- **LOOP RISK** — repeated attempts are reproducing substantially the same failure or rework without information gain; checkpoint and start a fresh execution session rather than extending the loop.

Intentional dormant/reserved work is not considered an unhealthy agent merely because it is old. The health concern begins when stale work is actively extended or represented as current without reconstruction.

## CURRENT AUTHORITATIVE PROGRAM SNAPSHOT

| Lane | Highest merged boundary |
| --- | --- |
| Implementation | **SF-IMP-0082** / PR #273 accepted all five SMALL / seed-skyforge built-in carriers; **SF-IMP-0083 / #284 / draft PR #285** owns the remaining 20 built-in seed/scale specimens |
| Authorship | **AUTH-0091** / PR #279 merged/accepted as freshwater habitat opportunity; the owning lane ledger still requires post-merge repair |
| Content / Experience | **C17** accepted; C18 / draft PR #277 remains machine-green only on its prior tested head and requires current-main resynchronization; C11/C12 remain separately in progress/reserved |
| Showcase | cave/interior and ecology showcases accepted; all five SMALL / seed-skyforge built-in morphology carriers have passed the current machine/human tranche |
| Music / Audio | no MUS milestone merged; MUS-0001 / PR #159 remains draft/unmerged and requires synchronization/source-verification/state repair before repository acceptance |
| AUDIT | **AUDIT-0007** by this reconciliation merge |

Merged Bellanca PR #238 remains accepted **design state**, not accepted aircraft runtime/flight behavior.

## IN PROGRESS / UNMERGED

### SF-IMP-0083 / issue #284 / draft PR #285 — built-in seed/scale matrix

Active Implementation work after accepted SF-IMP-0082.

The tranche owns the remaining 20 exact AUTH-0083 built-in specimens: MEDIUM seed-min / seed-zero / seed-skyforge plus LARGE seed-skyforge for Massif, Tableland, Spine, Basin, and Lobed.

The first PR #285 diagnostic head b5b0d47a compiled the exact corpus and profiled support/build-range/footprint evidence, but its retained CI fan-out failed at the shared compile-and-unit-test stage because the diagnostic test prematurely asserted that every specimen must fit the vanilla build interval. That assertion preempted the issue's explicit packaging investigation rather than proving a producer regression.

Implementation corrected the diagnostic at dc46f54a: build-range fit is now reported descriptively as vanillaBuildIntervalFits plus highestFittingMinimumY, leaving packaging policy to subsequent evidence. Fresh exact-head CI is in progress with no failure recorded yet at this audit point.

Audit requirements remain:

- preserve exact AUTH-0083 IDs, seeds, scales, provider identity, full detail/secondary morphology, and pure-translation proof;
- require objective admission/top/underside/digest/persistence/reopen evidence once runtime packaging begins;
- do not convert descriptive roughness/traversal data into arbitrary aesthetic thresholds;
- use the multi-seed/multi-scale evidence to classify #267 and #283 before morphology retuning;
- retain the explicit #214 human comparison gate.

No SF-IMP-0083 capability is accepted yet.
### Content C18 / PR #277 — stock turtle void-freight envelope

Draft/in progress. The first unloaded-adjacent-chunk fixture hypothesis failed closed because Minecraft kept the intended target loaded. The owner replaced that artificial precondition with a better player-facing experiment: one explicitly forced starting chunk, then a 48-block otherwise-unforced route with durable per-move progress and fuel accounting.

The prior Audit gate-definition blocker is resolved on tested head f99219eb6ba20f0a76be980b12a098cda7ae3f13. That head is now diverged from current main and must resynchronize before Content acceptance. Explicit `STOPPED` is accepted only when the stock turtle error contains `Cannot leave loaded world`; unrelated stop reasons fail. A no-result path may instead become measured `STALLED / NO_TICK_PROGRESS` only after the bounded progress-stall window.

The accepted specimen interpretation remains:

```text
outboundMoves=64
returnMoves=64
fuelStart=160
fuelEnd=32
delivered=960
boundaryOutcome=STALLED
boundaryMoves=17
boundaryFuelStart=80
boundaryFuelEnd=63
boundaryError=NO_TICK_PROGRESS
```

This proves measured route/ticking-envelope dependence, not an exact internal chunk-boundary error and not a production chunk-loader policy. The freight turtle moves 128 blocks with exact fuel delta and delivers 960 items; the otherwise-unforced route stalls after bounded progress with exact fuel accounting.

Current-head gates are all green: C18 `34128010315`, C17 `34128009964`, repository CI `34128010024`, Showcase Acceptance `34128010145`, and SF-IMP-0070 characterization `34128010009` PASS. The branch is zero-behind `main@34bd884f` at this Audit point. AUDIT has no remaining cross-lane, gate-definition, synchronization, or machine-evidence blocker; Content still owns acceptance/merge and later gameplay conclusions.

Aircraft-vs-turtle economics, dedicated chunk-loader progression, mining/farming throughput, Ender-storage combinations, and any nerf remain separate Content decisions.

### AUTH-0091 / PR #279 — merged/accepted; lane-state repair pending

AUTH-0091 merged as c739fe9a521e2c75f640bbfcf79eee6abd0298cf from exact-head candidate fbebcf5defa8a69a304216422d28d1bcb0e0d639.

Accepted semantics remain narrow: deterministic island-scale retained-freshwater planning evidence from the fixed watershed/waterbody stack, including exact provenance, unique inundated planning cells/coarse horizontal area, shoreline-cell and source-kind counts, normalized water-depth potential, and valid dry-island zero profiles. Physical cubic water volume/depth, fauna/carrying capacity, agriculture/resources, Minecraft fluid/biome identity, and backend lifecycle remain outside the contract.

Exact-head run 34139804616 and retained repository/showcase/performance/Content compatibility checks passed with no failures before merge.

Durable-state hazard: current docs/agent-state/AUTHORSHIP_STATE.md still reports AUTH-0090 as highest. Audit records the discrepancy but does not rewrite the producer-owned ledger; Authorship must repair it.
### C11 — live pre-Brass first-flight recipe surface — PR #233

Long-lived/reserved. Re-audit only after current-main synchronization and fresh focused runtime evidence.

### Portable Engine cutoff — PR #240 / issue #237

Stationary retained-stack behavior has evidence. Still unaccepted for assembled-Sable behavior, two-engine aircraft behavior, save/reload, current-main synchronization, and human ergonomics.

### Bellanca B0-A assembly docs — PR #242

Audited proposal head `797e0c54325b1f45d0aa6229124c3bb7e8727bda` remains cross-lane consistent but old-base. C12 live Sable assembly/physics/flight, measured mass/CG, propulsion/governor behavior, and later power-off handling remain authoritative.

## VERIFIED CROSS-LANE CONTRACTS

- Authorship owns semantic world meaning; Implementation owns Minecraft realization/lifecycle; Content owns game integration/experience.
- AUTH-0086 is the accepted visible authored-hydrology intent producer; it is not Minecraft water placement.
- AUTH-0087 is the accepted exact publication↔authored-realization coverage gate before downstream authored material/ecology consumers; concrete Minecraft material/biome mapping remains Implementation-owned.
- AUTH-0088 is the accepted exact published-volume/world-XZ surface-ecology projection; physical Y, quart-cell policy, Minecraft biome identity, placement, persistence, and lifecycle remain Implementation-owned.
- AUTH-0089 is the accepted fixed island-scale ecological-opportunity summary; it is horizontal authored-habitat planning evidence, not species/spawn/carrying-capacity/resource/backend policy.
- AUTH-0090 is the accepted exact regional aggregation of AUTH-0089 through AUTH-0087 provenance with horizontal-area weighting; it still assigns no species/resources/province class/backend behavior.
- AUTH-0091 is the accepted deterministic retained-freshwater habitat-opportunity summary; it exposes coarse planning-cell/shoreline/source/depth evidence, not physical water volume, fauna/resource eligibility, Minecraft fluid identity, or backend lifecycle.
- C14 accepts existing CC:Tweaked/Create: Avionics as a baseline programmable sensor/bounded-control substrate, not mature autopilot or a first-flight prerequisite.
- C15 accepts ordinary-player 1:1 Nether portal linking/placement mechanics only; assembled contraption transfer, passengers/cargo, authored portal-site safety, terminal UX, and permanent cosmology remain separate.
- C16 accepts the measured stock CC:Tweaked wireless envelope only: ordinary wireless remains bounded local/regional infrastructure; Ender Modems are a range/dimension bypass whose progression treatment remains Content-owned.
- C17 accepts stock GPS as infrastructure-dependent under ordinary radio range; Ender-backed GPS inherits the mature-bypass status. No duplicate Skyforge GPS/network authority is justified.
- Bootstrap first powered aircraft remains distinct from the later Brass-era Giuseppe Bellanca GB-1A.
- Machine correctness cannot waive explicit human/play gates.

## MANUAL VERIFICATION

### #194 ecology legibility — PASSED / CLOSED

Passed for accepted SF-IMP-0080 and recorded before merge. Do not reopen for issue #261.

### #214 production morphology — OPEN / built-in SMALL tranche passed

SF-IMP-0081 Massif and SF-IMP-0082 Tableland / Spine / Basin / Lobed have all passed their explicit SMALL / seed-skyforge in-engine carrier/viability reviews. Full #214 remains open for the SF-IMP-0083 multi-seed/multi-scale matrix, hybrids/providers, regional contexts, and later material/ecology/hydrology/geology contexts.

Issue #267 tracks Massif traversal cadence/lumpiness. Issue #283 separately tracks stronger Tableland plateau identity relative to Massif. Neither reopens the accepted carrier tranche; both require broader deterministic evidence before retuning.
### Music / Audio — OPEN listening / persistence gates

- Track 00 BBCSO range repair requires human A/B audition; neither candidate is canonical.
- Track 06 exact accepted Draft 02.3 MIDI must be recovered before canonical persistence.
- HC/PERC/TP plugin-state-sensitive lanes require explicit reproducibility records where MIDI alone
  is insufficient.
- Runtime adaptive scoring remains unaccepted until explicit cross-lane state/transition contracts
  and Implementation playback evidence exist.

### Bellanca / C12 — OPEN

Machine assembly/physics first; human handling, landing, rough-field usefulness, glide feel, controls, and later silhouette/art review remain required.

### Portable Engine cutoff — OPEN

Human play must verify discoverability and non-surprising redstone shutdown behavior after missing assembled/persistence machine gates close.

## AGENT / EXECUTION HEALTH WATCH

Snapshot at AUDIT-0008 commencement from `main@5ababf52`. Re-evaluate from repository evidence rather than carrying these labels forward mechanically.

| Lane / work | Health | Evidence / required action |
| --- | --- | --- |
| Implementation — SF-IMP-0083 / PR #285 | **HEALTHY / ACTIVE** | The first profiling assertion failed once, was corrected with new information, then work progressed through synchronized fixture -> runtime/viewer -> lifecycle -> automated seed/scale matrix. Retained build/showcase/ecology/performance/Content regressions are green; new five-family seed/scale jobs are pending. No loop evidence. |
| Authorship — AUTH-0091 state repair / PR #287 | **WATCH — synchronization conflict** | Producer responded promptly and build is green, but the state-repair branch is now behind AUDIT-0007 and conflicts in shared `CROSS_LANE_CONTRACTS.md`. Recompose onto current main before merge. This is parallel drift, not a reasoning loop. |
| Content — C18 / PR #277 | **WATCH — branch drift** | Tested head remains machine-green but is approximately 8 commits ahead / 50 behind current main. Synchronize and rerun exact-head evidence before acceptance or before adding another large tranche. |
| Music / Audio — MUS-0001 / PR #159 | **STALE / HIGH-RISK HISTORY** | Approximately 88 commits ahead / 1119 behind current main at this snapshot. Do not indefinitely append persistence/fix work to this historical branch; reconstruct from current main and recompose the canonical source/manifests/history before repository acceptance. Existing listening/source-integrity gates remain. |
| Content C11 / Portable Engine / Bellanca branches | **STALE / DORMANT-RESERVED** | Roughly 369 / 323 / 301 commits behind current main respectively. Their dormancy is not itself unhealthy. If work resumes, use a fresh session/current-main reconstruction and recompose the narrow valid delta rather than continuing from old conversational state. |
| Audit | **HEALTHY / ACTIVE** | AUDIT-0007 merged exact-head green; AUDIT-0008 makes this process-health watch durable. |

Process-health actions already taken:

- PR #159 received a high-risk stale-history warning;
- PR #277 received a synchronization warning;
- PR #287 received a current-main synchronization warning after parallel Audit movement.

## KNOWN HAZARDS / TECHNICAL DEBT

1. **Biome presentation flight envelope / issue #261:** immediate-surface biome presentation can fall back to BASE_WORLD ambience away from owned terrain. Non-blocking for SF-IMP-0080; avoid claiming whole native biome columns as the fix.
2. **Long-lived branch / execution drift:** C11, Portable Engine cutoff, Bellanca proposal, and especially MUS-0001 require fresh current-main reconstruction before active continuation or acceptance. Audit must distinguish intentional dormancy from an actively extended stale execution.
3. **Music source reproducibility:** MUS-0001 currently relies on manifests/audit prose without a repository-executable canonical MIDI/library-alignment gate; Track 06 canonical source is missing and Track 00 repair remains human-gated.
4. **Production morphology:** all five built-in SMALL / seed-skyforge carriers passed the current human tranche, but full #214 remains open. SF-IMP-0083 must test seed/scale stability before #267/#283 tuning decisions.
5. **Design/runtime ambiguity:** merged/open design documents are not executable capability without required runtime evidence.
6. **Performance:** do not restart local micro-optimization absent fresh realistic-scale profiling.
7. **Authorship durable-state lag:** AUTH-0091 is merged/accepted but AUTHORSHIP_STATE.md still reports AUTH-0090 as highest; producer-owned repair remains required.

Resolved hazards include the C11/C13 identifier collision, duplicate live-state namespaces, AUTH-0086/AUTH-0087/AUTH-0088 durable-state lag, C16 durable-state lag, SF-IMP-0080's compile/substrate/ecology-legibility blockers, SF-IMP-0081's post-C16 synchronization plus temporary Implementation-ledger lag, SF-IMP-0082's stale post-merge Audit/shared snapshot, and C18's permissive STOPPED gate.

## ORDERED NEXT AUDIT WORK

1. Maintain the agent/execution health watch on every Audit pass. Flag stale-history extension, repeated no-information failure cycles, contradictory handoffs, or user-reported context/session degradation; recommend a fresh execution session/current-main reconstruction when convergence degrades.
2. Audit SF-IMP-0083 / issue #284 / draft PR #285 as it moves from exact support/build-fit profiling into runtime packaging. Preserve exact AUTH-0083 identity and require objective persistence plus explicit #214 human seed/scale comparison before acceptance.
3. Track the AUTH-0091 Authorship-ledger repair until AUTHORSHIP_STATE.md matches merged repository truth; do not reopen the accepted contract.
4. Include Music / Audio in every reconstruction. Track MUS-0001 / PR #159 through current-main recomposition, MUSIC_STATE creation, executable source-integrity verification, Track-00 A/B listening, Track-06 exact-source recovery, PR-state reconciliation, and exact-head CI.
5. Track C18 / PR #277 as Audit-cleared only on its tested head; require current-main synchronization before Content acceptance and reject leaps from the specimen to turtle nerfs, production chunk-loader policy, or exact internal chunk-boundary claims.
6. Keep full #214 open beyond the accepted SMALL built-in carriers; classify #267 and #283 from the multi-seed/multi-scale matrix before tuning.
7. Track issue #261 as the non-blocking biome/ambient flight-envelope refinement; Audio may consume a later accepted envelope but does not own it.
8. Re-audit C11 / PR #233, Portable Engine #240, and Bellanca #242 only after their declared synchronization/runtime prerequisites.
9. Update this ledger at each material merge, contract change, new hazard, or handoff.
