# Skyforge Content / Experience Agent State

**Lane:** Content / Experience  
**Status:** Canonical live lane handoff  
**Updated:** 2026-09-08 (America/Chicago)  
**Highest accepted Content boundary:** C26 / PR #382

Read first:

- [Program charter](PROGRAM_CHARTER.md)
- [Cross-lane contracts](CROSS_LANE_CONTRACTS.md)
- [Content integration corpus index](../design-audit/README.md)

## MERGED / ACCEPTED

### DR-10 / #534 — C20 starting-cluster evidence — IN PROGRESS

`DR10_MATERIALS_EVIDENCE.json` binds the locked `P2_DRESSED_REGION_A` DR-00 specimen to C20's
Iron `STARTING_CLUSTER` condition. It selects no replacement specimen and fails closed if the
canonical descriptor loses nonzero AUTH-0093 Iron opportunity. Existing Iron/Copper/Zinc/Create-Zinc
realization evidence remains authoritative; this bounded proof does not alter its identities,
quantity, grade, accessibility, placement, persistence, or C21 noncompetition boundary.

The evidence proves only C20's Iron scope-selection condition. It does not claim the unresolved
first-aircraft material budget, route, trade/salvage, recovery, or human-play guarantees from the
broader Bootstrap recipe.

### CIV/Bootstrap #466 — Guild destination, Bellanca closure, and first freight proof — IN PROGRESS

Issue #466 now has a backend-neutral Content specimen in `skyforge-world`:

- deterministic Guild Hall candidates preserve exact AUTH-0096/AUTH-0097 association and anchor order, require only accepted physical-surface plus observed-open-ray evidence, and fail closed to `REPLAN_REQUIRED` unless an explicitly route-planned reachable candidate exists;
- every eligible Hall carries the Bootstrap MVP service core: account access, Bellanca claim/restitution, basic market, basic contracts, and route/settlement information, so another eligible Hall can resolve the network-owned claim;
- the authoritative Bellanca state machine covers crash, unfavorable initial claim, recoverable recorder evidence, liability reversal, exactly one restitution outcome, and tutorial completion;
- the first post-tutorial opportunity is a routine recognized physical-cargo copper delivery from a distinct producer settlement to a Guild consumer. It consumes C20’s accepted post-flight copper policy and remains an economic contract, not a mandatory quest step.
- the freight contract explicitly requires the Content-owned `CARGO_TRANSFER` consumer capability; merged #467 consumes that stable requirement through its one tracked Guild cargo anchor, while anchor realization/lifecycle remains Implementation-owned.

This is deliberately Content semantics only. Minecraft persistence/lifecycle, physical cargo materialization and settlement realization remain Implementation work. Human play, arrival, Hall, and freight-loop judgment remain the CIV-0041 human gates.

### Highest accepted executable Content milestone: C26

**C26 / PR #382** binds accepted AUTH-0098 local petroleum-system evidence to the literal-source
admission boundary required by the C25 Implementation adapter.

Accepted local contract:

```text
AUTH-0098 systemOpportunity == 0
    -> LOCAL_SOURCE_INELIGIBLE

AUTH-0098 systemOpportunity > 0
    -> LOCAL_SOURCE_ADMISSIBLE_FOR_REALIZATION_CONSIDERATION
```

Accepted interpretation:

- no threshold above accepted AUTH-0098 zero/nonzero semantics is introduced;
- eligible cells are the exact nonzero AUTH-0098 cells in unchanged source order/provenance;
- eligible x/z source columns contain only nonzero cells and preserve first canonical eligible-cell
  occurrence order;
- the column view exists only to support a downstream vertical-source bridge; it is not block-space
  well geometry;
- C26 exposes no selected cell/column, no final pumpjack anchor, and no magnitude-derived reserves,
  pressure, saturation, thickness, volume, extraction rate, depletion, rarity, or placement weight;
- island/province eligibility from C22/C23 does not authorize arbitrary literal petroleum placement
  elsewhere on that island;
- **AUTH-0098 is sufficient for local source admission. No new Authorship producer is required.**
- Implementation owns which admissible cells/columns become literal sources, exact geometry/count,
  quantity/pressure/depletion, pumpjack alignment/adapter behavior, persistence, and lifecycle.

Exact code-head verification passed Wave C26 Local Petroleum Source Admission run `34188611055`,
Wave C20 Base Metal Content Policy regression run `34188611005`, and repository CI run
`34188611029`. No Minecraft manual or human visual gate applies.

### C25

**C25 / PR #377** proves that the pinned Create: Diesel Generators petroleum machinery/fluid stack can
remain available while its independent native chunk-noise petroleum geography is disabled.

Accepted runtime split:

```text
Create: Diesel Generators machinery / crude / refining / engines    KEEP
native per-chunk Perlin/biome petroleum authority                   SUPPRESS GLOBALLY
AUTH-0098/AUTH-0100 + C22-C24                                      SKYFORGE MEANING
literal petroleum source / depletion / pumpjack bridge              IMPLEMENTATION
```

Accepted interpretation:

- baseline isolated runtime reads upstream `DISABLE_NORMAL_OIL_CHUNKS=false` and
  `DISABLE_HIGH_OIL_CHUNKS=false`;
- suppressed isolated runtime reads both flags as true;
- representative upstream `OilChunksSavedData.getBaseOilAmount(...)` queries return zero under
  suppression;
- `createdieselgenerators:crude_oil`, Pumpjack Hole, Distillation Tank, and Diesel Engine remain
  registered in both modes;
- KubeJS is deliberately absent from the specimen, so the optional CDG oilAmount listener cannot
  replace the tested native config path;
- the validation TOML exists only inside the disposable C25 suppressed world and is not packaged;
- unlike C21's Create-Zinc authority split, petroleum native authority is globally incompatible with
  C22/C23 strategic-node semantics and should remain suppressed in production;
- upstream chunk noise, biome tags, persistent `cdg_oil_chunks` native generation semantics, and
  default bedrock pumpjack termination are retained-mod mechanics, not Skyforge petroleum meaning;
- C25 does not choose the production adapter technique and does not justify adding KubeJS merely for
  its optional petroleum event;
- **Implementation** now owns the narrow petroleum-source/depletion/pumpjack bridge from accepted
  AUTH-0100 + C22-C24 semantics into retained Diesel Generators machinery, including literal source
  geometry/quantity/pressure/depletion, exact surface/subsurface alignment, persistence, and lifecycle.

Exact code-head verification passed Wave C25 Diesel Petroleum Authority run `34187671861` and
repository CI run `34187671841`. Live markers proved BASELINE
`normalOilDisabled=false highOilDisabled=false` and SUPPRESSED
`normalOilDisabled=true highOilDisabled=true` with retained assets in both modes. No Minecraft
manual or human visual gate applies.

### C24

**C24 / PR #373** consumes accepted C23 + AUTH-0096/AUTH-0097 to nominate coarse petroleum
infrastructure candidates without introducing a new Authorship threshold or selecting a final site.

Accepted role contract:

```text
PETROLEUM_EXTRACTION_INTERFACE
    -> C23 petroleum-eligible association
    + AUTH-0096 physical surface support

REFINERY_PROCESSING
    -> AUTH-0096 physical surface support
    -> may be on another island in the same exact C23/AUTH-0100 region

FREIGHT_TRANSFER_EDGE
    -> AUTH-0096 physical surface support
    + at least one AUTH-0097 observed-open ray
```

Accepted interpretation:

- extraction-interface nomination is restricted to C23 petroleum-eligible islands;
- refinery/processing may be separated from the source island by logistics;
- freight-edge nomination uses only broad accepted open-direction evidence and is not a runway, dock,
  vehicle corridor, or neighboring-obstruction proof;
- candidate order and provenance remain exact AUTH-0096/AUTH-0097 source order;
- C24 returns candidate lists only and exposes no selected site or local ranking score;
- no flatness, area, grade, approach-length, hydrology, morphology, reserves, quantity, deposit
  position, or backend threshold enters C24;
- AUTH-0096/AUTH-0097 are sufficient for the **coarse candidate stage**. C24 demonstrates no new
  Authorship evidence gap;
- the next Content boundary must derive concrete infrastructure requirement envelopes from actual
  retained/bespoke pumpjack/refinery/freight assets and gameplay roles before any further Authorship
  request;
- Implementation owns exact structure geometry/orientation/admission, deposit-interface alignment,
  neighboring obstruction, mutation, placement, persistence, and lifecycle.

Exact candidate verification passed Wave C24 Petroleum Infrastructure Candidates run
`34186747769`, Wave C20 Base Metal Content Policy regression run `34186747718`, and repository CI
run `34186747691`. No Minecraft manual or human visual gate applies to C24 itself.

### C23

**C23 / PR #367** consumes accepted C22 + AUTH-0100 in the first deterministic
petroleum-province feasibility/replan layer.

Accepted intent/outcome contract:

```text
ORDINARY_PROVINCE
    -> NO_PETROLEUM_REQUIREMENT

PETROLEUM_STRATEGIC_NODE
    + AUTH-0100 eligibleIslandCount == 0
        -> REPLAN_REQUIRED
    + AUTH-0100 eligibleIslandCount > 0
        -> CANDIDATES_AVAILABLE
```

Accepted interpretation:

- ordinary provinces never re-plan solely because petroleum is absent;
- an intentionally petroleum-bearing strategic-node province must contain at least one exact
  AUTH-0100 geologically eligible island or Content must re-plan the province;
- C23 exposes AUTH-0100 `eligibleIslands()` and `rankedEligibleIslands()` exactly and rejects
  substituted candidate evidence;
- C23 deliberately exposes no selected island, so the strongest geological candidate cannot silently
  become an oilfield/refinery/route/civilization choice;
- no strategic-node frequency, opportunity threshold, reserves, grade, pressure, volume, extraction
  rate, site role, route geometry, or backend ontology enters the planner;
- AUTH-0100 is sufficient for **province geological feasibility**. C23 demonstrates no new Authorship
  evidence gap;
- the next petroleum Content boundary should define explicit infrastructure-role requirements and
  first attempt to consume accepted AUTH-0096 local surface-site plus AUTH-0097 directional-access
  evidence before requesting new Authorship semantics;
- Implementation still owns literal petroleum deposits, extraction, concrete structure geometry,
  worldgen, persistence, and lifecycle.

Exact candidate verification passed Wave C23 Petroleum Province Feasibility run `34185862890`,
Wave C20 Base Metal Content Policy regression run `34185862839`, and repository CI run
`34185862783`. Intervening main movement was Audit/orchestration-only. No Minecraft manual run or
human visual gate applies.

### C22

**C22 / PR #360** binds accepted AUTH-0098 petroleum-system geological opportunity to the first
executable backend-neutral petroleum gameplay policy:

```text
availability                 STRATEGIC_NODE
progression                   R3_MATURE_INDUSTRY
first-flight critical         false
ordinary-province guarantee   none
alternative access            bounded trade/salvage
mature industrial supply      primary extraction or logistics required
regional node selection       AUTH-0100 regional inventory
```

Accepted interpretation:

- ordinary provinces may legitimately contain no petroleum;
- petroleum remains explicitly outside first-flight/bootstrap closure;
- geological eligibility is exactly AUTH-0098 nonzero `peakSystemOpportunity()`;
- unchanged AUTH-0098 `meanSystemOpportunity()` may rank already-eligible candidates only;
- no opportunity magnitude becomes reserves, grade, pressure, deposit scale, extraction rate,
  gameplay rarity, or placement weight;
- bounded trade/salvage may introduce or bridge petroleum use but does not replace mature industrial
  extraction/logistics;
- an intentionally petroleum-bearing strategic-node province must select/re-plan around eligible
  authored geology rather than inject petroleum into zero-opportunity islands;
- AUTH-0100 / PR #361 supplies the exact canonical regional inventory C22 requested;
- final petroleum-node selection must not be inferred from geological rank alone if route, site,
  civilization, or service requirements require additional accepted evidence;
- Implementation remains owner of concrete petroleum identity, deposit geometry/count/volume,
  extraction, pumpjack/refinery realization, placement, persistence, and lifecycle.

Exact synchronized verification on head `fa3d5daab452fe35689939a7704d05f37ef7cb52` passed Wave C22
Petroleum Content Policy run `34184590388`, Wave C20 Base Metal Content Policy regression run
`34184590386`, and repository CI run `34184590376`. No Minecraft manual run or visual gate applies.

### C21

**C21 / PR #315** proves that the pinned Create 6.0.10 resource assets are separable from its broad
generic Overworld placement authority.

Accepted interpretation:

- baseline Create `create:zinc_ore` and `create:striated_ores_overworld` biome modifiers remain
  active add-feature modifiers;
- an isolated world-local validation datapack can replace exactly those two modifiers with
  `neoforge:none` while `create:zinc_ore`, `create:deepslate_zinc_ore`, and `create:crimsite`
  remain registered;
- that no-op datapack is validation-only and must not ship as a packaged global override;
- ordinary `BASE_WORLD` generation remains under native Create authority;
- inside explicit Skyforge-owned exact-volume population, Implementation owns the narrow
  feature-admission/filtering seam that prevents generic Create Zinc/striated placement from
  competing with authored resource semantics;
- vanilla Iron/Copper and Create Zinc identities/assets remain the preferred retained resource
  identities unless later executable evidence rejects them;
- concrete AUTH-0093/0094 + C20 deposit geometry, count, grade, quantity, accessibility, placement,
  persistence, and lifecycle remain Implementation-owned.

Acceptance is gated by the dedicated C21 A/B workflow, repository CI, and the targeted C17 regression
on the synchronized PR #315 merge candidate. No Minecraft manual run or visual gate is required.

### C20

**C20 / PR #302**, merge `0b76038a0b0a98628b6b3ec0e39b5cb0cd76a8d9`; final synchronized policy head `efc03a4f5233eade9eff24d438315315524b247d`.

C20 binds accepted AUTH-0093 Iron/Copper/Zinc geological opportunity to the first executable backend-neutral Content resource policy:

```text
IRON   -> COMMON_REGIONAL / first-flight critical / STARTING_CLUSTER
COPPER -> COMMON_REGIONAL / post-flight / POST_FLIGHT_PROVINCE
ZINC   -> COMMON_REGIONAL / post-flight / POST_FLIGHT_PROVINCE
```

Accepted interpretation:

- geological eligibility is exactly accepted AUTH-0093 nonzero opportunity; zero-opportunity geology fails closed;
- unchanged AUTH-0093 mean opportunity is an ordinal ranking score among eligible candidates only;
- availability class and guarantee scope are fixed Content policy and do not vary with opportunity magnitude;
- Iron bootstrap reliability must be satisfied by selecting/re-planning eligible starting-cluster geology rather than injecting ore into an ineligible island;
- Copper and Zinc remain post-flight regional engineering rewards and are not reintroduced as first-flight prerequisites;
- trade/salvage are bounded alternatives and count toward a hard guarantee only when that path is itself guaranteed by the world recipe;
- C20 does **not** infer grade, reserves, deposit count/volume, physical accessibility, industrial supply, exact bootstrap quantity, Minecraft resource identity, or placement/worldgen policy.

Exact-head verification passed Wave C20 Base Metal Content Policy run `34158865011` and repository CI run `34158865063`. No Minecraft manual run or visual gate is required for this neutral policy milestone.

### C19

**C19 / PR #292**, merge `97c9e1accf0a243ddc892fbf1fc424825b0c244e`; final synchronized runtime head `4c6bfe67f07e5c80650c23d8a697615588a02a9a`.

C19 measures real stock CC:Tweaked mining-turtle extraction and loaded/ticking-route dependence on the retained C9 computing stack:

```text
digs=96
outboundMoves=96  returnMoves=96
fuelStart=240      fuelEnd=48
delivered=96       elapsedTicks=2377
boundaryOutcome=STALLED  boundaryDigs=17  boundaryMoves=17
boundaryFuelStart=80     boundaryFuelEnd=63
boundaryError=NO_TICK_PROGRESS
```

Accepted interpretation:

- stock mining-turtle base capability is **KEEP**; C19 does not justify a generic Skyforge mining-turtle nerf or replacement;
- a real normal turtle with the stock diamond-pickaxe upgrade can extract a deterministic vanilla ore corridor, physically return, and deliver the recovered material with exact movement/fuel accounting;
- the mining upgrade does not change C18's infrastructure boundary: an otherwise-unforced 48-block mining route stalled after 17 successful dig/move cycles;
- resource geography is not erased merely by stock turtle digging because turtles still require physical deployment to authored deposits plus fuel, ticking/loading, and physical cargo handling;
- farming throughput, quarry/branch-mining patterns, dedicated chunk-loader progression, Ender-storage combinations, ore abundance, mature fleet automation, and final aircraft-vs-turtle economics remain separate Content decisions.

Final exact-head verification passed C10/C13/C14/C15/C16/C17/C18/C19, repository CI, the complete current Showcase Acceptance matrix, and SF-IMP-0070 performance characterization. The exact C19 runtime result reproduced before and after current-main synchronization around AUTH-0092.

### C18

**C18 / PR #277**, merge `1c7e24a0e37c4ade8aa5a8943f65017d2300248b`; final synchronized runtime head `9ce39a797ea337fe2f152bc5168ba4d6715c578a`.

C18 measures real stock CC:Tweaked turtle freight and loaded/ticking-route dependence on the retained C9 computing stack:

```text
outboundMoves=64  returnMoves=64
delivered=960
fuelStart=160      fuelEnd=32
boundaryOutcome=STALLED  boundaryMoves=17
boundaryFuelStart=80     boundaryFuelEnd=63
boundaryError=NO_TICK_PROGRESS
```

Accepted interpretation:

- stock turtle movement/freight is **KEEP**; C18 does not justify a generic Skyforge turtle nerf or replacement;
- a turtle can carry 15 full stacks across unsupported sky when external infrastructure keeps the route loaded/ticking, with one fuel per successful physical move;
- a turtle does not itself provide arbitrary long-range chunk/ticking infrastructure: the otherwise-unforced 48-block specimen stalled after 17 moves with exact fuel accounting;
- route loading therefore remains infrastructure rather than a free turtle capability;
- mining/farming throughput, dedicated chunk-loader progression, Ender-storage combinations, autopilot, and final aircraft-vs-turtle economics remain separate Content decisions.

Final exact-head verification passed C10/C13/C14/C15/C16/C17/C18, repository CI, the complete current Showcase Acceptance matrix, and SF-IMP-0070 performance characterization. C17 required one targeted rerun for its already-known GPS-host startup race; the rerun passed and no repeated unchanged retries were used.

### C17

**C17 / PR #272**, merge `65bd49a4e349f16bab8b796aa5b1a1e6dd72a7cc`; final synchronized runtime head `9373c307ef6bc79a64643ad0d958f5a09ebe1c9c`.

C17 proves the stock CC:Tweaked GPS layer remains infrastructure-dependent under ordinary radio range:

```text
noHostFound=false
normalNearFound=true  normalNear=16,64,16
normalFarFound=false
enderRemoteFound=true enderRemote=512,80,272
```

Accepted interpretation:

- ordinary GPS is KEEP: it requires deployed non-collinear GPS hosts within the bounded Wireless Modem envelope;
- GPS towers/beacons can provide legible aviation/navigation infrastructure without a bespoke Skyforge GPS system;
- Ender-backed GPS inherits C16's mature-bypass status because it removes the ordinary range constraint;
- final tower spacing/placement, Ender progression gating, turtle economics, and autopilot remain separate gameplay decisions;
- Skyforge must not add a duplicate generic GPS/network implementation while the retained CC surface remains sufficient.

Final exact-head verification passed C10/C13/C14/C15/C16/C17, repository CI, all current showcase persistence/reopen jobs including ecology and Massif morphology, and SF-IMP-0070 performance characterization. The branch was synchronized twice around accepted SF-IMP-0081 and AUTH-0089 before merge.

Verification-reliability maintenance after C21: the C17 runtime fixture later reproduced a transient
Ender-locator startup miss while a same-head rerun passed. The fixture now boots the GPS host
constellations for 60 server ticks before powering locators, removing the host/locator startup race
without changing C17's accepted topology, modem semantics, or stock CraftOS API boundary.

### C16

**C16 / PR #264**, merge `d63f7c712355fb421c909f7bbdc74465a88522e6`; exact synchronized runtime head `03418c4e941bc394989ca4269a1fbff3fc40cab7`.

C16 measures the real CC:Tweaked wireless infrastructure envelope with six real CraftOS computers and stock modem peripherals:

```text
normalNearReceived=true  normalNearDistance=48
normalFarReceived=false
enderRemoteReceived=true enderRemoteDistance=512
enderCrossReceived=true  enderCrossDistance=nil
```

Accepted interpretation:

- ordinary Wireless Modems are a bounded local/regional infrastructure layer and need no Skyforge nerf or replacement;
- Ender Modems remove same-dimension range and cross-dimensional separation and are therefore an explicit mature bypass, not an unexamined early Bootstrap Province default;
- no bespoke Skyforge generic networking API is justified;
- final Ender Modem progression/recipe gating, GPS layout, turtle throughput, and autopilot progression remain separate gameplay decisions.

Exact-head verification before merge passed C2/C3/C5/C6/C7/C9/C10/C13/C14/C15/C16, repository CI, both showcase persistence jobs, and SF-IMP-0070 performance characterization. A first C6 attempt on an earlier synchronized head timed out while the runner was ~40 ticks behind; the exact same C6 job was rerun successfully before later synchronization, and C6 passed again on the final C16 head.

### C15 — ordinary-player 1:1 Nether portal linking/placement

**PR #259**, merge `764fbb393644fd2de7aa9076f71f29602fa25732`; accepted head `8e75e84b800587a6d87676d8e18e4ed974f65c29`.

Vanilla portal search and missing-target creation consume the live C10 1:1 scale at non-origin coordinates. Same-coordinate linking beats a deliberate 8:1 distractor; reverse linking is exact; missing targets are created near the 1:1 destination. Assembled contraption/passenger/cargo transfer, authored foothold safety, terminal UX, and permanent cosmology remain open.

### C14 — executable programmable avionics capability

**PR #256**, merge `b4b44c87509ee70b27ddbe20468d2d287cbd79f1`.

Real CraftOS computers discover upstream Create: Avionics altitude/throttle peripherals, read live altitude, and drive the retained physical throttle through upstream 0..15 clamping. Computing remains optional for manual/first flight; no duplicate generic aircraft telemetry/control API is justified.

### C13 — Elytra/firework bypass suppression

**PR #247**, merge `dcfa4d467bfcd8e64443da84f2f970de6b1ccd7b`.

The pinned No More Elytra Boosting 1.0.0 runtime removes Elytra rocket propulsion while preserving fall-flying and ordinary fireworks. Human clarity/optional feedback remains a UX question only.

### C11 — live pre-Brass/pre-petroleum first-flight recipe surface — ACCEPTED LATE

**PR #386** recomposes the valid bounded C11 capability from historical draft PR #233 on current
`main`. PR #233 remains closed as reserved historical work, not rejected.

The exact current C1-pinned Create/Sable/Aeronautics runtime exposes directly bootstrap-safe live
RecipeManager paths for all nine required first-aircraft/workshop outputs:

```text
simulated:physics_assembler
simulated:engine_assembly
simulated:red_portable_engine
aeronautics:andesite_propeller
simulated:steering_wheel
simulated:swivel_bearing
simulated:white_symmetric_sail
create:mechanical_press
create:mechanical_saw
```

The live engine assembly starts from iron sheet and its upstream sequenced assembly consists only of
cutting/pressing the transitional assembly; it does not hide a Brass/petroleum deployer ingredient.

C11 proves only the **direct live recipe surface**. It does not prove transitive raw-material/BOM
closure, exact bootstrap quantities/guarantees, adhesive/fuel availability, aircraft viability,
time-to-flight, or player-facing HS-06 choices. C12 still owns the executable Bellanca mule and the
Portable Engine cutoff path remains a separate dependency for final powered-soaring/restart closure.

Exact recomposed-head verification passed Wave C11 First Flight Recipe Runtime run
`34189656148` and repository CI run `34189656149`. No manual or human-eye gate applies to C11.

C26 remains the highest numbered accepted Content boundary; late acceptance of C11 does not renumber
or supersede C12-C26.

### C10 — live 1:1 Nether scale

**PR #232**, merge `5eaff75638cd3f9033f440e67b82f705c999cd51`.

Final live Overworld and Nether `coordinate_scale` are both 1.0. C15 subsequently closes ordinary-player portal linking/creation for that interim policy.

### C9 — CC:Tweaked avionics substrate

**PR #230**, merge `6db44a0f26d244598aa44317abe6c7219eec44c1`.

Accepted retained stack: CC:Tweaked 1.119.0 + Create: Avionics 0.5.2 + Create 6.0.10 + Sable 2.0.5 + Create Aeronautics 1.3.2.

### C8 and earlier retained experience contracts

- C8 / PR #229 closes Phantom-gated glider acquisition/maintenance with ordinary leather/wool repair.
- C7 / PR #223 proves Reliable Gliders consumes trusted shared A4MC lift after native glider physics.
- C6 proves the retained Fowl Play red-tailed hawk can enter/exit thermal SOAR from the same atmosphere authority.
- C3/C5 prove A4MC and retained fauna stack loader/runtime compatibility.
- C1 industrial scaffolding is merged but still has manual/runtime closure gates listed below.

## IN PROGRESS / RESERVED

### Bootstrap Province — issue #224

Central Content vertical slice:

```text
survival -> Create -> cheap glider -> shared thermals/fauna -> first aircraft
-> specialized destination -> regional freight -> mature infrastructure evidence
```

SF-IMP-0080 / PR #248 is now merged/accepted and the project-owner human ecology gate passed, so legible forest/taiga land ecology is no longer blocked by issue #194. Issue #261 is only a non-blocking short-distance biome ambience/presentation follow-up.

### C12 — executable Bellanca B0 — issue #239

**C12 B0-A1/B0-A2 / PR #401** accepts the first real assembled Sable/Create Aeronautics Bellanca
engineering-mule boundary. Exact head `091a2decaf81bebc9e9161e8b04e291c2831d9b0` passed Wave C12
Bellanca B0 Assembly, retained C11 First Flight Recipe Runtime, Portable Engine cutoff/persistence/
assembled-Sable regressions, and repository CI.

Accepted bounded result:

- Simulated's real Physics Assembler moves exactly one 105-block historical B0-A `MAIN_BODY` into
  one Sable `ServerSubLevel`, preserves the expected block states, and admits no extra translated
  MAIN_BODY-volume block;
- Sable's live merged mass tracker supplies authoritative finite/positive mass and X/Y/Z COM,
  satisfies lateral symmetry, and remains below the explicit 60-kpg review threshold;
- historical PR #242 remains source-constrained topology input only; its paper mass/CG estimates
  are not accepted runtime facts.

AUTH-0101 and subsequent main movement are orthogonal to this C12 runtime surface. The exact-head
expensive assembly/mass evidence is therefore portable under the validation policy; no duplicate
live-assembly run is warranted solely to refresh its SHA.

This is not powered-aircraft acceptance. PROP_CHILD lifecycle, lift, propulsion, controls, landing
gear, taxi/flight, and power-off glide/restart remain explicitly deferred. Portable Engine cutoff
machine seams remain accepted separately; issue #237 still retains only its human ergonomics gate.

### Portable Engine cutoff — issue #237; stationary seam accepted via PR #389 candidate

Historical PR #240 remains closed/unmerged as reserved evidence. PR #389 recomposes the stationary
compatibility seam from current `main` on the narrower accepted C11 flight-only runtime.

Accepted stationary behavior:

- unconfigured Portable Engine ignores neighboring redstone and keeps upstream 32 RPM/burn behavior;
- explicitly opted-in engine + redstone cuts output to zero;
- active burn timer is preserved exactly while cut;
- queued fuel is not consumed while cut;
- signal removal resumes upstream cold/warm ignition behavior without refund/duplication;
- repeated cut/restart preserves the exact running timer.

Exact candidate validation passed Portable Engine Cutoff run `34190573454`, retained C11 regression
`34190573367`, and repository CI `34190573402`.

PR #392 additionally accepts real **three-boot** persistence in both CUT and configured-RUN states,
plus comparator coherence:

- boot A saves configured CUT at burn=600, one queued coal, comparator=12, zero output;
- boot B reopens that exact CUT state, removes the persisted signal, resumes at burn=599 / 32 RPM /
  comparator=11, then saves configured RUN;
- boot C reopens configured RUN with cutoff mode still persisted but inactive, exact burn=599,
  one queued coal, comparator=11, and normal 32 RPM output;
- one further real engine tick decrements burn to 598 while preserving normal output/comparator
  behavior.

Exact strengthened-head verification passed Portable Engine Cutoff Persistence run `34193038291`,
stationary cutoff regression `34193038285`, retained C11 regression `34193038301`, and repository
CI `34193038286`.

PR #397 accepts the real assembled-Sable/two-engine machine boundary:

- Simulated's own `assembleFromSingleBlock` moves the specimen into a real Sable `ServerSubLevel`;
- both configured Portable Engines, one shared Create shaft, and both redstone controls are retained
  inside that exact returned sublevel;
- both engine cutoff modes and exact pre-assembly burn counters survive assembly;
- together CUT freezes both counters and produces zero output;
- together RUN resumes exact counters and normal 32-RPM output;
- engine A and engine B can each be cut/restarted independently while the other continues running;
- repeated together CUT/restart remains stable.

Exact synchronized-head verification passed Portable Engine Cutoff Sable run `34215553782`,
Portable Engine Cutoff Persistence `34215553812`, stationary Portable Engine Cutoff
`34215553718`, retained C11 `34215553709`, and repository CI `34215553710`.

Issue #237 remains **open only for acceptance item #10: the human ergonomics / unexpected
neighbor-redstone shutdown play gate**. Machine acceptance items #1-#9 are now satisfied.

### Authorship / Implementation dependencies

- AUTH-0088 is accepted: exact published-volume/world-XZ surface-ecology projection, with no Minecraft biome/Y policy.
- AUTH-0089 is accepted: deterministic island ecological opportunity aggregation without species/spawn/resource/backend policy.
- AUTH-0092 is accepted: raw deterministic regional nearest-peer center-distance / nominal-radial-gap isolation evidence without gameplay classification.
- AUTH-0093 is accepted: normalized Iron/Copper/Zinc geological opportunity subordinate to exact mineral-bearing structural host support; Content owns availability classes, bootstrap guarantees, trade/salvage, and resource progression policy.
- AUTH-0094 is accepted: exact published-region inventory of every canonical AUTH-0093 island profile, including eligible-island lists and threshold-free mean-opportunity ranking; C20 owns scope selection/replanning and guarantee satisfaction.
- AUTH-0096 and AUTH-0097 are accepted: threshold-free local surface-site and directional-access evidence are available for later Content structure/civilization site policy; neither selects a gameplay role.
- AUTH-0098 is consumed by C22; AUTH-0100 / PR #361 supplies the canonical regional petroleum opportunity inventory C22 requested. Petroleum remains STRATEGIC_NODE / R3 mature industry with no ordinary-province hard guarantee.
- SF-IMP-0080 is accepted: legible forest/taiga ecology showcase.
- SF-IMP-0081 and SF-IMP-0082 are accepted for all five SMALL / seed-skyforge built-in morphology carriers; SF-IMP-0083 / issue #284 owns the remaining multi-seed/multi-scale built-in matrix, while #267 and #283 separately track tuning concerns.

## PROPOSED / OPEN CONTENT QUESTIONS

### Create:Aero Automated Logistics (AAL) — issue #431 Content audit (2026-09-09)

This is a bounded Content investigation only. It does not accept a new dependency, change player
progression, or authorize Implementation work. Supplied AUDIT-0031 evidence (checked 2026-09-09)
identifies AAL 0.6.2 as Minecraft 1.21.1 NeoForge and MIT-licensed, with current-stack compatibility
in principle. Its README/changelog evidence is recorded in issue #431.

#### Independent dispositions

- **Player-facing: HIDDEN / NPC ONLY.** AAL replays a recorded vehicle-specific route and compresses
  schedule playback, docking, cargo orchestration, unloaded progress, and recovery. It does not
  provide pathfinding, obstacle avoidance, rerouting, or general live autopilot. It therefore
  preserves aircraft construction, route design/safety, and reactive control, but its player UI,
  ownership, safety, fuel, throughput, chunk-loading, and cargo semantics are not accepted. The
  retained Create Aeronautics + Create + CC:Tweaked/redstone stack remains the expressive alternative.
  Reconsider only as late P4/P5 optional logistics, never first-flight/P2, after a separate gate.
- **NPC/runtime: LIMITED ROUTE-BOUND USE.** AAL is a credible substrate for routine civilian/faction
  cargo craft, ferries, couriers, merchant traffic, and patrol circuits where Skyforge owns why the
  traffic exists and the route is safety-validated. It is not NPC flight AI: investigation,
  interception, combat, emergency diversion, and other reactive behavior require a Skyforge
  controller. Strong-infrastructure-candidate status is deferred pending the prototype below.

#### Benefits, risks, and bespoke work avoided

Benefits are removal of repetitive route execution, docking queues/reservations, cargo transfer,
unloaded recorded-route progress, materialization/recovery, and runtime administration. If the
adapter succeeds, this likely avoids **medium-to-large** bespoke work across approximately **5-7
subsystem seams** (routine route execution, docking, schedule playback, coarse travel, and much of
persistence/recovery); this is directional, not an implementation commitment.

Content risks include teleportation/geography bypass, unsafe recorded paths, hidden chunk-loading,
fuel/throughput advantages, cargo loss/duplication, station contention, and meaningless routine
traffic. Technical risks include no evidence yet for programmatic generated stations/routes/
schedules or NPC ownership; vehicle-specific routes; Sable storage constraints; and unknown
long-duration, destruction/removal, stale-state, and persistence behavior. Historical lifecycle
fixes show maturation, not reliability proof. MIT licensing makes adaptation plausible, but API and
state-format stability and adapter maintenance remain unknown; prefer upstream contribution over a
local fork unless a demonstrated gap requires otherwise.

Named unknowns: programmatic route/station/schedule construction; NPC ownership; routine-to-reactive
override and route reacquisition; exact pinned-stack unload, rematerialization, restart, cargo,
contention, and long-duration behavior; failure-order recovery; and persistence/API stability.
If route creation requires a human recording pass, that is the principal NPC-integration blocker.

#### Exact next prototype / acceptance gate

Before dependency adoption or implementation expansion, run one bounded proof on the exact pinned
stack:

`generated/Skyforge-owned cargo craft -> two generated stations -> programmatic route assignment ->
physical departure -> chunk unload -> unloaded progression -> rematerialization -> docking -> cargo
transfer -> return trip -> server restart mid-lifecycle -> successful continuation`,

plus one deliberate station/vehicle-removal or unsafe-route-interruption failure/recovery case.
Acceptance requires no duplication/loss, stale runtime, route desynchronization, unsafe bypass, or
unrecoverable state, with observable chunk-loading, fuel, cargo, and contention behavior. If feasible,
a second proof must demonstrate routine-route -> reactive Skyforge-controller override -> route
reacquisition/resume. Failure of automated state construction or lifecycle boundaries stops this
proposal for a new Content decision; it does not authorize bespoke replacement infrastructure.

Required human/manual evidence is a recorded server/client play review of departure, docking, cargo,
unload/rematerialization, restart continuation, and recovery, including visual confirmation that
craft do not teleport or bypass geography. A representative multi-ship contention/soak run is needed
before unattended NPC logistics is accepted. Any later player exposure also needs human judgment that
hidden NPC-only behavior and P4/P5 logistics choices remain legible and meaningful.

### Computing after C19

C9/C14/C16/C17/C18/C19 now establish substrate, real avionics capability, wireless-envelope behavior, GPS infrastructure topology, the base turtle freight/ticking envelope, and base mining extraction behavior. Remaining questions are gameplay/bypass questions only:

- turtle farming throughput, quarry/branch-mining patterns, dedicated chunk-loader progression, Ender-storage combinations, and final aircraft-vs-turtle economics;
- GPS tower spacing/placement and how visibly navigation infrastructure appears in #224;
- Ender Modem progression gating;
- autopilot versus route-planning/navigation gameplay;
- thin Skyforge peripherals only for genuinely Skyforge-owned semantics absent upstream.

Computing is a first-class infrastructure axis, **not** a first-flight prerequisite.

### Base-metal resource geography after C21

AUTH-0093/0094 + C20 + C21 now close the authored-opportunity -> gameplay-policy -> retained-resource-authority
boundary for Iron/Copper/Zinc. The next gap is concrete exact-volume realization, not another authored
opportunity or a packaged global suppression datapack.

- keep vanilla Iron/Copper and Create Zinc block/item/processing identities unless executable evidence disproves them;
- leave ordinary BASE_WORLD Create placement intact;
- suppress/redirect the competing generic Create Zinc/striated placed features only inside explicit Skyforge-owned exact-volume population;
- Implementation owns concrete deposit geometry/count/volume/accessibility/placement/lifecycle;
- C11 now proves the direct first-flight recipe surface is pre-Brass/pre-petroleum, but the 48-64 iron-equivalent first-flight band remains an engineering estimate until transitive C12/Bootstrap closure and HS-06;
- sample access versus industrial-scale supply remains open and must not be inferred from AUTH-0093 magnitude alone.

### Nether after C15

Ordinary 1:1 portal mechanics are closed. Remaining questions:

- assembled Aeronautics/Sable contraption portal transfer;
- passengers/cargo if transfer is possible;
- authored portal foothold/site safety;
- human portal-terminal usability;
- eventual permanent route-scale/cosmology decision.

## MANUAL VERIFICATION REQUIRED

### C1 industrial specimen

Still requires recorded runtime/play evidence for material suppression/recipe closure, identity collisions, Metallurgy A/B value, electrical throughput, and world-side industrial source throughput.

### Mobility / aircraft

- practical cheap-glider envelope;
- blocked Elytra-boost clarity in ordinary play;
- aircraft-vs-personal-flight freight/logistics comparison;
- Bellanca B0 mass/CG, propulsion, ground handling, takeoff/climb/cruise, power-off glide/restart, atmosphere response, and payload;
- human-eye aircraft review only after mechanical credibility.

### Morphology

Issue #214 remains a project-owner visual gate for underside/approach quality. Do not treat machine evidence as a substitute for the required view/flight-route review.

## Architectural invariants

- Reuse priority: vanilla -> existing mods -> config/datapack/integration -> thin adapter -> bespoke.
- Skyforge owns meaning; retained mods provide assets/capabilities unless a demonstrated gap justifies more.
- Personal mobility is cheap; logistics are not.
- Aircraft win through payload, repeatability, fluids/entities/contraptions/automation, not blanket nerfs.
- Resource geography should create routes/infrastructure rather than repetitive chores.
- A4MC remains the single atmosphere authority for accepted aircraft/fauna/player lift behavior.
- Computing extends infrastructure; manual flight and the basic loop must not depend on Lua/computers.
- Ordinary wireless is bounded infrastructure; Ender Modems are a mature bypass and should be progression-conscious.

## Verification shortcuts

- `Wave C2 Mobility Preflight`
- `Wave C3 Atmosphere Preflight`
- `Wave C5 Soaring Fauna Preflight`
- `Wave C6 Hawk Thermal Compat`
- `Wave C7 Glider Shared Lift`
- `Wave C9 Computing Avionics`
- `Wave C10 Nether Scale Runtime`
- `Wave C11 First Flight Recipe Runtime`
- `Wave C13 Elytra Bypass`
- `Wave C14 Avionics Capability`
- `Wave C15 Portal Linking`
- `Wave C16 Wireless Envelope`
- `Wave C17 GPS Infrastructure`
- `Wave C18 Turtle Freight`
- `Wave C19 Turtle Mining`
- `Wave C20 Base Metal Content Policy`
- `Skyforge Showcase Acceptance`
- repository `CI`

## Ordered next work

1. Proceed with C12 / issue #239 executable Bellanca B0. C11 direct recipe closure and #237's
   stationary, CUT/RUN persistence, and real assembled-Sable/two-engine machine seams are now
   available. Recompose historical PR #242 only as source-constrained topology/manifest input and
   replace its paper mass/CG assumptions with live Sable evidence.
2. Surface issue #237 acceptance item #10 as a bounded human play gate: verify the opt-in control is
   understandable/intended and ordinary neighboring aircraft redstone does not cause surprising
   shutdown. Do not accumulate more machine evidence for #237 unless that play gate exposes a defect.
3. Hand C21's exact-volume resource-authority contract to Implementation: filter competing Create Zinc/striated generic feature execution only inside explicit Skyforge-owned population, never by a packaged BASE_WORLD-wide override.
4. Track Implementation issue #387 as the consumer of C25+C26: keep Diesel Generators machinery, suppress native chunk oil globally, and realize the narrow Skyforge source/depletion/pumpjack bridge only inside C23-eligible petroleum provinces and exact C26 nonzero AUTH-0098 local support.
5. Do not add another petroleum Authorship wrapper for retained-machine block geometry; Pumpjack 4-16 arm span and Distillation Tank dimensions remain Implementation geometry unless a concrete backend-neutral cause is demonstrated missing.
6. Resume the remaining #227 turtle questions after the first-aircraft dependency chain is moving: farming throughput, quarry/branch-mining scale, or dedicated chunk-loader/Ender-storage combinations. Do not nerf turtles without measured substitution evidence.
7. Continue Bootstrap Province acceptance with C20's guaranteed Iron and post-flight Copper/Zinc semantics. C11 supports pre-Brass/pre-petroleum direct recipes, but exact quantities and acquisition guarantees remain downstream of C12/Bootstrap closure and HS-06.
8. Keep assembled-aircraft Nether transfer separate from accepted ordinary-player C15 mechanics.
9. Return to C1 industrial runtime evidence when a focused runtime window is practical.

### C27 — AAL adaptation architecture investigation (#439, 2026-09-09)

This bounded second-stage update accepts C26 and #431/PR #438: AAL remains **HIDDEN / NPC ONLY** and
**LIMITED ROUTE-BOUND USE**. It adds no dependency, progression, or runtime adapter. It is a concrete
Implementation handoff; every upstream AAL hook below is unproven until the pinned-stack prototype.

#### Authority/dataflow and current touchpoints

```text
SkyIslandIdentity + published region association
 -> Content civilization/service/freight intent
 -> Content site policy over AUTH-0096/AUTH-0097 evidence
 -> Implementation station/corridor admission and exact Minecraft realization
 -> versioned Skyforge routine-route specification
 -> optional thin AAL adapter
 -> AAL route/dock/cargo execution
 -> Skyforge lifecycle record + derived Minecraft/AAL references
```

The concrete current seams are `skyforge-model` (`SkyIslandIdentity`, `SkyIslandDescriptor`,
`SkyIslandVolumeDescriptor`), `skyforge-reference` published association/evidence corpora,
`skyforge-recipes` deterministic compilation, `skyforge-world` (`SkyIslandWorldCatalog`,
`SkyIslandWorldVolume`, `SkyIslandWorldVolumeId`), and `skyforge-neoforge-1211` exact-volume physical
admission/population/lifecycle code. The repository has no civilization, station, route, or freight
runtime contract today. The smallest new seam is a neutral Content `RoutineTransportIntent` and
versioned `RoutineRouteSpec`, plus an Implementation-side persistence record/adapter; do not build a
general logistics framework.

#### Smallest neutral contract and adapter

The contract carries only: `CivilizationId`/service owner; `TransportServiceId`; `StationId` and
ordered endpoint/leg descriptors; `RouteId` and immutable route version; schedule cadence/window;
cargo/service intent; station role/capability; `VehicleId` binding; lifecycle/authority status and
last acknowledged leg/waypoint. Meaning belongs to Content, physical realization to Implementation,
and all of these remain backend-neutral. AAL object IDs, adapter version, route-version reference,
and recovery token are opaque operational cross-references, not semantic IDs.

Required operations: capability/version discovery; idempotent station create/bind; craft bind; route
create/import; schedule assign/resume/suspend; route/leg/status and docking/queue observation;
idempotent cargo authorization/observation; safe suspension; release to the reactive controller;
reacquire at a declared waypoint/leg; reload recovery; and stale/destroyed object disposal/reconcile.
Programmatic generated station/route/schedule construction, NPC ownership, safe takeover, cargo
transaction guarantees, and reacquisition are **UNKNOWN UPSTREAM HOOKS**. Bulk fleet APIs and player
UI are optional. Pathfinding, obstacle avoidance, tactics, combat, interception, emergency diversion,
route safety, demand, site selection, progression, and cargo meaning stay outside AAL.

#### Ownership matrix

| Concern | Owner / source of truth |
|---|---|
| place/province/island meaning, provenance, deterministic identity | Authorship; Content consumes |
| civilization/service/freight meaning, admission, density | Content |
| site/corridor choice from AUTH-0096/0097 | Content; Implementation validates geometry |
| blocks/entities, physics, chunk tickets, docking realization | Implementation |
| routine playback, queues, schedule execution | AAL operationally through adapter |
| reactive flight and takeover | reactive Skyforge controller |
| canonical route/service/station/vehicle lifecycle and intent | Skyforge record; Content meaning + Implementation persistence |
| live route position/queue and opaque AAL IDs | AAL-derived telemetry, never semantic authority |

#### Lifecycle/state machine

`ROUTINE_AAL -> SUSPENDING_ROUTINE -> REACTIVE_SKYFORGE -> REJOINING_ROUTE -> ROUTINE_AAL`.
Exactly one controller lease is valid. AAL must acknowledge a safe dock/queue/waypoint (or a proven
equivalent); the reactive controller records route version and last safe leg. Rejoin selects a valid
waypoint/leg and never snaps coordinates. Failure enters `RECOVERING`, then `FAILED / ADMIN_REQUIRED`
when unresolved. Missing route/station, destruction, invalid version, failed cargo commit, or failed
takeover preserves intent for replan and never permits double control.

#### Persistence, reconciliation, performance, and isolation

Persist canonical IDs, immutable route version, endpoints/legs, schedule/cargo intent, authority
state, last safe leg/waypoint, adapter capability version, opaque AAL references, and cargo
transaction IDs/status. Skyforge owns intent, identity, versions, authority, and commit outcomes;
AAL may own only live operational state. Startup/reload scans records, validates versions, and
idempotently rebinds or rebuilds derived AAL objects; canonical IDs reject duplicate materialization.
Restart mid-leg resumes from the last acknowledged checkpoint. Unresolved partial cargo is
conservative/admin state. Missing objects become stale and are disposed or replanned; schema/mod
changes require migration or fail-closed disablement. Distant traffic is a semantic record and live
craft materialize only under later Implementation policy. No numeric radius, fleet cap, or tick budget
is selected here: bounded temporary loading may support an accepted live operation, while coarse
unloaded progression is preferred. Implementation must measure tickets/chunks, ticks, memory, cargo
latency, and contention. Capability/API drift disables only derived AAL state and retains Skyforge
intent. Prefer an upstream public hook; a local fork needs an explicit narrow gap and upstream refusal.
Unsafe reflection/mixins, human-only generated routing, state loss/duplication, or non-deterministic
recovery are abandonment criteria.

#### Prototype milestones and stop/go criteria

Implementation order: (1) pinned-stack API/capability probe; (2) generated station binding; (3)
programmatic two-station route; (4) one-craft routine execution; (5) unload/coarse progression and
rematerialization; (6) docking/queue and idempotent cargo; (7) restart mid-leg plus stale/duplicate
repair; (8) multi-craft contention/performance; (9) routine/reactive takeover and waypoint/leg
reacquisition. A thin adapter is a go only after single-craft lifecycle, persistence, no-loss/no-dup,
and exclusive-control gates pass. A missing narrow public hook requests upstream contribution; a
local fork is considered only after refusal and bounded maintenance ownership. Failure of generated
construction, cargo correctness, exclusive control, or restart/recovery stops AAL adoption and does
not authorize bespoke replacement infrastructure.

#### Cross-lane handoff

Next action belongs to **Implementation** once the prototype is authorized. **Audit** owns evidence
economy and a later human server/client/play gate for non-teleporting geography, docking,
unload/reload, and recovery. Content owns the neutral intent and integration policy; Authorship needs
no new evidence because accepted AUTH-0096/0097 and identity contracts suffice. This is the stop
boundary: no AAL addition, progression change, or production adapter.
