# DR-60 canonical dressed-region human review packet

**Status:** machine-complete handoff candidate for the terminal project-owner flight/exploration gate.
**Issue:** #535
**Specimen:** `P2_DRESSED_REGION_A`

This packet packages the accepted DR-50 specimen for inspection. It records machine evidence and a reproducible launch path; it does **not** judge aesthetics, pacing, density, composition, gameplay quality, or whether the region is worth exploring.

## 1. Exact specimen and evidence identity

- Packet source main SHA: `10569fc85452c03f41aa44902bb0bc6686fcc7f8`.
- Accepted DR-50 repair merge on main: `8119c50209a184b5a0f42dc44d2d3e0726860593`.
- Exact accepted repair head: `c9b5692587b3cb80fd7874e9a3a6bd4dc14cd53b`.
- DR-50 acceptance workflow run: `35163438331`; same-head repeat artifact: `10474981110`.
- Commits between the accepted DR-50 merge and this packet source main affect aircraft compiler/runtime files, not the DR-50 world-integration path.
- Specimen ID: `P2_DRESSED_REGION_A`.
- Authored/world root seed: `6001989086914692933` (unsigned).
- Minecraft acceptance level seed: `493030`.
- Authored identity: province `8`, cluster `81`, island `1471`.
- Authorship seed: `389656995481184958`; morphology family: `massif`.
- Authorship provenance: `AUTH-0001`, `AUTH-0030`; ecology authority: `AUTH-0046`, `AUTH-0103`, `AUTH-0096`.
- Implementation provenance: `SF-IMP-0068`, `SF-IMP-0082`; accepted dressing milestones: DR-00 through DR-50.
- Exact canonical volume: `6001989086914692933/sf-imp-0068-production-composed-cave/0/0/680068`.
- Geometry seed: `680068`.
- Conservative production-region bounds: X `-103.93216077880793..103.93216077880793`, Y `110..300`, Z `-103.93216077880793..103.93216077880793`.

Current repaired integrated evidence is identified by `dr50RegionDigest=9c788b382232c405` and `dr50PopulationOutcomeDigest=f69ae93eeda1196e`. Fresh A and B agree on both values.

## 2. Runtime and configuration

- Minecraft `1.21.1`; NeoForge `21.1.249`; Java `21` toolchain.
- World type: `skyforge:development`.
- Production runtime: `SkyforgeNeoForge1211ProductionComposedCaveDevRuntime` plus `SkyforgeDr50IntegratedRegionEvidence`.
- Preparation task: `:skyforge-neoforge-1211:dr50IntegratedRegionAcceptance`.
- Human-viewer run: `:skyforge-neoforge-1211:runDr60HumanReviewClient`.
- Prepared B run directory: `.\skyforge-neoforge-1211\run-dr50-auto-b\`.
- Prepared world: `.\skyforge-neoforge-1211\run-dr50-auto-b\saves\acceptance\`.
- Machine result directory: `.\skyforge-neoforge-1211\build\acceptance\dr-50\`.
- Preparation server mode: creative, peaceful, offline development server, spawn protection `0`, view distance `7`, simulation distance `5`.

`runDr60HumanReviewClient` reopens the qualified B world with DR-50/DR-40/composed-cave reload expectations but intentionally omits the automated acceptance harness. The client therefore remains open for human inspection instead of exiting after the reload proof.

## 3. Exact Windows / PowerShell preparation and launch

From the repository root in PowerShell, prepare a clean accepted B world first:

```powershell
git switch main
git pull --ff-only
git rev-parse HEAD
.\gradlew.bat :skyforge-neoforge-1211:dr50IntegratedRegionAcceptance --stacktrace --no-configuration-cache
```

For exact reproduction of this packet snapshot, `git rev-parse HEAD` should be `10569fc85452c03f41aa44902bb0bc6686fcc7f8`. If main has advanced, review the intervening changes before treating this packet as an exact-current-main handoff; do not silently reuse an older prepared world.

After the aggregate task reports PASS, launch the persistent human viewer:

```powershell
.\gradlew.bat :skyforge-neoforge-1211:runDr60HumanReviewClient --no-configuration-cache
```

Do not launch `runDr50IntegratedRegionAcceptanceReloadClient` for the review session: that task is an automated proof client and exits after recording PASS.

## 4. Initialization and entry

1. Confirm `production-a.properties`, `production-b.properties`, and `reload.properties` all report `status=PASS` under `.\skyforge-neoforge-1211\build\acceptance\dr-50\`.
2. Launch `runDr60HumanReviewClient` as above and wait for the `acceptance` world to finish loading.
3. Remain in creative mode with flight enabled. If needed, run `/gamemode creative` and double-tap jump to fly.
4. Enter above the exact canonical volume center with `/tp @s 0 320 0`.

Entry coordinate `(0, 320, 0)` is a deterministic navigation point twenty blocks above the recorded maximum Y=300. It is not an aesthetic selection.

## 5. Feature-presence-derived inspection route

Use the coordinates below because repaired DR-50 evidence proves the corresponding system is present. They are navigation anchors, not quality rankings.

1. **Macro flight view — `(0, 320, 0)`:** rise enough to see the whole bounded island, orbit the X/Z extent near `±104`, and inspect both upper mass and underside. Do not infer quality from the route choice.
2. **Authored water — `(-56, 241, -60)`:** this is `dr50HydrologyRepresentativePos`. Repaired evidence records 9 authored hydrology positions and digest `27ca179d890f969`. Verify water presence and its physical relation to nearby terrain.
3. **Starting-cluster geology/material — `(-101, 220, -7)`:** this is `dr50MaterialPos`, recorded as `minecraft:iron_ore`. Inspect the local terrain/access relationship without treating the point as a designed mine site.
4. **Cave/interior — `(-6, 137, 6)`, then `(-2, 112, 8)` / `(-2, 111, 8)`:** the first is the accepted base cave evidence position; the latter are mouth/outward anchors near the lower vertical boundary. Repaired evidence records 196 completed interior obligations, 136 nonempty obligations, and 1160 successful interior features.
5. **Structure evidence boundary — no canonical structure coordinate exists in repaired DR-50:** `dr50StructureLifecycleInvoked=true`, but `dr50CanonicalCompletedStructures=0`. Do not substitute the separate DR-30 generic native machine probe and do not claim a visible canonical structure. Record this absence as a review limitation; structure grounding/architecture cannot be judged from this specimen.
6. **Ecology — savanna `(-93, 236, -1)`, plains `(-86, 247, -1)`, swamp `(-78, 236, -43)`:** these are the repaired integrated land-A, land-B, and wet/riparian carriers. Current integrated ecology records 5 native biome carriers, 206 successful native features, and population digest `f69ae93eeda1196e`.
7. **Boundary / vertical relation — X/Z near `±104`, Y `110..300`:** compare surface, edge discharge, lower cave mouth, and underside. This stop exists to inspect exact-volume/vertical composition, not to score emptiness or dramatic effect.

If the preparation files are absent or any required result reports failure, stop the review and treat it as a machine preparation failure rather than improvising a substitute specimen.

## 6. Passed machine invariants

The repaired DR-50 acceptance and retained predecessor gates establish the following objective machine facts:

- fresh generation A equals fresh generation B on authoritative DR-50 state;
- `dr40PopulationOutcomeDigest`, `dr50PopulationOutcomeDigest`, and `dr50RegionDigest` are A/B equal on the accepted repair head;
- server reload and actual-client reload both pass, including DR-40 biome presentation and DR-50 material/hydrology persistence;
- exact-volume isolation and stacked-volume isolation pass;
- authored hydrology survives final composition without material collision;
- starting-cluster Iron persists and replay is idempotent;
- composed cave/native interior lifecycle settles with zero pending obligations and no replay;
- native carver/transform, authored-change, authored-provenance, interior, hydrology, structure-state, population, and region authority digests are stable at the accepted comparison boundary;
- production ecology is presented persistently and fails closed for foreign volume population;
- the DR-30 structure runtime seam, persistence, player-mutation preservation, and stacked isolation are independently accepted and reused as structure proof authority;
- no aesthetic tuning was authorized by DR-50 acceptance.

Key repaired digests: native transform `56fadc5cec66c609`, native carve `a91d1bdbbf589880`, authored change `c7c22aadd95317ea`, authored provenance `92697946b4796125`, interior `a6189762edc9972c`, canonical structure-state `cbf29ce484222325`, population `f69ae93eeda1196e`, region `9c788b382232c405`.

## 7. Known limitations

- **No completed canonical structure is present in the repaired integrated specimen.** DR-50 records zero completed canonical structures. DR-30 proves the generic native placement/persistence seam on its accepted machine fixture; that evidence must not be presented as a structure physically present in this DR-50 world.
- Final structure architecture, settlement layout, route geometry, and aesthetic policy remain outside DR-30/DR-50 machine authority.
- Ecology carriers, feature counts, and the documented stops are proof/inspection evidence, not target density or beauty thresholds.
- The checked-in `docs/agent-state/DR50_INTEGRATED_REGION_EVIDENCE.json` still describes its promotion state as `CANDIDATE`; current acceptance authority is the merged DR-50 repair, closed #496, successful exact-head Actions runs, and repeat artifact identified above. Do not infer that the stale JSON state revokes the accepted runtime evidence.
- The review world is a development/acceptance world prepared with creative/peaceful/offline settings, not a final player-start configuration.
- Once the manual viewer is open, normal game simulation can advance. DR-50 determinism applies to the accepted generation/reload comparison boundary, not to arbitrary elapsed live-simulation time after the owner begins exploring.

## 8. Intentionally unresolved human/product questions

The machine pipeline stops here. The project owner should decide, from direct flight/exploration, whether:

- the region reads as one coherent place rather than layered generators;
- macro landform is interesting from flight;
- water, geology/interiors, and ecology have convincing physical relationships;
- procedural artifacts are visible or distracting;
- exploration produces meaningful variation rather than repetition;
- the region feels empty, cluttered, or appropriately paced;
- ecology appears motivated by geography rather than merely overlaid;
- the landscape invites traversal and investigation;
- the result has a recognizably Skyforge identity;
- the region is worth flying around and developing further.

Structure grounding/architecture is **not currently assessable on this canonical specimen** because no canonical structure completed. That gap should be evaluated explicitly rather than hidden or replaced with the separate DR-30 probe.

## 9. Nonblocking anomalies and evidence notes

- The diagnostic whole-loaded-footprint block scan differs between repaired fresh A and B (`8653170` / `cd1f7d657a88c38f` versus `8653211` / `f837510627205621`). This scan includes unrelated base-world/post-generation simulation and is intentionally diagnostic, not an authoritative DR-50 equality oracle. The authoritative population/region digests match.
- The repaired integrated ecology digest (`f69ae93eeda1196e`) supersedes the earlier standalone DR-40 evidence digest for this DR-60 handoff. The older value must not be used as current integrated evidence.
- CI logs may emit optional-mod recipe/mixin warnings for absent Create-family development classes while continuing to successful acceptance. A warning alone is not a DR-60 failure; the preparation PASS files and workflow conclusion are the gate.
- The structure count of zero is a visible product-review limitation, not a reason for the machine to invent or place a structure during DR-60.

## 10. Terminal gate

After the preparation succeeds and this exact specimen is open in `runDr60HumanReviewClient`, autonomous dressed-region convergence has reached its intended stop. No machine should tune the region in response to the questions above until the project owner supplies the human flight/exploration judgment.
