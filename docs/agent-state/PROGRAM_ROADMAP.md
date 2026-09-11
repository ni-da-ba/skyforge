# Skyforge Program Roadmap

**Status:** Canonical program-level convergence roadmap when merged  
**Updated:** 2026-09-10 (America/Chicago)  
**Purpose:** tell Audit/orchestration and fresh producer agents what product-convergent work comes next without turning long-range ideas into premature implementation tasks.

## 1. Authority and role

This document sits between the program charter and the lane-specific execution state.

```text
PROGRAM_CHARTER.md
    defines what Skyforge is and the durable lane/validation rules
            |
            v
PROGRAM_ROADMAP.md
    defines the product-convergence sequence and phase transitions
            |
            v
lane state + cross-lane contracts + human strategy + active issues/PRs
    define the current executable milestone and ownership boundary
            |
            v
source + tests + merged history
    prove what is actually accepted
```

This roadmap does **not** replace:

- `PROGRAM_CHARTER.md`;
- `VALIDATION_POLICY.md`;
- `CROSS_LANE_CONTRACTS.md`;
- `HUMAN_STRATEGY_ROADMAP.md` except where this document explicitly records an owner-approved resolution of the same roadmap decision;
- lane state ledgers;
- active issues / pull requests;
- source, tests, CI, merged history, and required human gates.

If a lane state or issue names a bounded current objective, use that objective. The roadmap becomes the routing authority when a current milestone closes, a lane ledger is silent about what comes next, or Audit/orchestration must decide whether later work is authorized at all.

Repository evidence always wins over stale prose about what has already been implemented.

## 2. Product objective

The first complete Skyforge game realization remains Minecraft 1.21.1 / NeoForge, built on backend-neutral authored world semantics.

The product converges when a player can naturally discover that:

- geography and verticality change survival and travel;
- atmosphere is a shared physical/gameplay system;
- personal mobility is cheap while logistics remain consequential;
- resource geography creates routes, specialization, freight, and infrastructure;
- ecology, structures, civilization, threats, dimensions, and industry follow coherent authored world meaning;
- mature engineering and computing increase the scale of agency rather than merely adding redundant material tiers;
- large negative space remains meaningful;
- the result is recognizably Minecraft while enabling experiences ordinary Minecraft does not.

## 3. Roadmap state vocabulary

Every numbered phase is one of:

- **COMPLETE** — its primary product risk is retired. Do not manufacture new work there unless downstream evidence exposes a concrete defect or accepted consumer gap.
- **PRIMARY_ACTIVE** — the current default product-convergence focus when a lane has no stronger already-authorized objective.
- **NEXT_AUTHORIZED** — the next phase has understood entry criteria and may begin as the active phase closes. Bounded preflight may run earlier only under the rules below.
- **PLANNED** — intended later product work. Presence here is not authorization to start broad implementation now.
- **OPTIONAL_EXPANSION** — compatible with Skyforge but not on the critical path to the first complete Minecraft realization.

A human gate is a **transition condition**, not another phase state.

## 4. Orchestrator routing contract

The roadmap exists principally to prevent two failure modes:

1. an orchestrator inventing speculative work because a lane became idle;
2. an orchestrator getting stranded after a milestone because no single issue happened to spell out the already-decided next integration tranche.

### 4.1 Default selection order

On a useful wake, select work in this order:

```text
1. Finish or repair an already-active accepted milestone / PR.
2. Follow a lane ledger's explicit next bounded milestone.
3. Execute the next bounded tranche inside the PRIMARY_ACTIVE phase.
4. If the active phase exit is satisfied, evaluate transition gates and activate NEXT_AUTHORIZED.
5. Permit a later-phase feasibility spike only if failure could materially invalidate current architecture.
6. Otherwise leave PLANNED and OPTIONAL_EXPANSION work dormant.
```

### 4.2 Existing-work exception

This roadmap does not retroactively cancel already-authorized work merely because its eventual product home is a later phase.

An existing issue/PR may continue when:

- it was already authorized by accepted lane/contracts/product state;
- it remains bounded and information-bearing;
- it does not cross a current human/product gate;
- it does not seize another lane's ownership;
- it does not displace the primary convergence path without a concrete dependency reason.

This is especially relevant to current Bootstrap civilization/runtime work, retained-mod feasibility work, Music, Presentation, and Audit/orchestration.

### 4.3 Missing-issue rule

A missing issue alone must not strand the program.

If all of the following are true:

- the PRIMARY_ACTIVE phase explicitly names the next tranche;
- required upstream contracts/evidence already exist;
- no human/product/permission gate remains;
- no healthy producer is already doing the work;
- the task can be bounded without inventing new product semantics;

then Audit/orchestration may create or dispatch the **smallest technically prudent milestone** needed to advance that tranche, with explicit acceptance/stop conditions and repository handoff.

If choosing the task would require a new product rule rather than technical decomposition, stop at `HUMAN_GATE` instead.

### 4.4 Phase-transition rule

When a phase's exit criteria are credibly satisfied:

1. do not continue polishing the completed subsystem merely because additional tests/specimens are possible;
2. resolve any named human strategy/visual/play gate that is now at trigger;
3. carry forward accepted generic fixes and portable evidence;
4. mark the next phase as the primary convergence focus in the appropriate supervisory/lane state;
5. dispatch the smallest first integration risk of that phase.

### 4.5 Validation rule

The roadmap does not weaken `VALIDATION_POLICY.md`.

Use:

```text
cheap deterministic evidence -> broad/exhaustive where practical
expensive runtime/client evidence -> representative by risk-equivalence class
sampled failure -> widen the affected class
retired isolated risk -> move to the next integration risk
```

Do not turn a phase into an exhaustive Cartesian acceptance matrix unless evidence demonstrates genuinely different failure classes.

## 5. Current program phase table

| Phase | Name | State | Current interpretation |
| --- | --- | --- | --- |
| 0 | Core world-synthesis foundation | **COMPLETE** | Maintenance only unless a downstream consumer exposes a defect/gap |
| 1 | Production geography convergence | **PRIMARY_ACTIVE** | Finish current morphology confidence, bounded hybrid/provider proof, then pivot |
| 2 | Integrated physical world | **NEXT_AUTHORIZED** | Materials/geology -> hydrology -> structures -> ecology on production geography |
| 3 | Bootstrap Province | **PLANNED** | Extensively pre-specified; bounded existing #224/#466/#467 work remains authorized |
| 4 | Atmospheric and visual-world convergence | **PLANNED** | Regional semantic weather, DH, reference shader/cloud stack, Lower cloud deck |
| 5 | Regional systemic sandbox | **PLANNED** | Multiple provinces, specialization, Guild/economy/freight persistence |
| 6 | Mature engineering, industry, and computing | **PLANNED** | Brass/electricity/petroleum/heavy logistics/computing/autonomy |
| 7 | Adventure, ecology, danger, archaeology, and light Nether integration | **PLANNED** | Frontier content without destroying negative space |
| 8 | High sky and End progression | **PLANNED** | Extreme altitude, Levitite, End as upper-atmosphere frontier |
| 9 | Alpha/release convergence | **PLANNED** | Whole-game integration, tuning, packaging, performance, multiplayer sanity |
| X | Lower Sea / Deep / second backend / other large expansions | **OPTIONAL_EXPANSION** | Explicitly off the first-complete-Minecraft critical path |

## 6. Phase 0 — Core world-synthesis foundation

**State:** COMPLETE / maintenance only.

### Objective

Prove that Skyforge can deterministically author and realize finite three-dimensional floating worlds through Minecraft without losing exact ownership, provenance, native-world integration, persistence, or bounded runtime behavior.

### Accepted boundary includes

At a program level this phase established the substrate for:

- backend-neutral authored world meaning and deterministic provenance;
- exact 3-D floating-island ownership and stacked-volume isolation;
- native surface/biome adaptation;
- native + authored cave composition;
- post-cave lakes/local modifications/ores/underground decoration/fluid springs;
- generated-fluid fencing/provenance;
- deterministic/idempotent lifecycle behavior;
- save/reload and actual-client reopen evidence;
- performance convergence sufficient to stop blind micro-optimization;
- legible land ecology carriers;
- exact production morphology carriers for the five built-in families at the accepted initial specimen boundary.

### Orchestrator rule

Do not resume generic foundation building.

Legal Phase-0 work requires a concrete downstream failure such as:

```text
accepted consumer
    -> exposes missing generic capability / correctness defect
    -> bounded foundation repair
    -> return immediately to the consuming phase
```

## 7. Phase 1 — Production geography convergence

**State:** PRIMARY_ACTIVE.

### Objective

Turn technically valid morphology into a controlled, production-usable terrain vocabulary across seeds/scales and representative composition, without confusing generator capacity with ordinary gameplay selection.

### Current sequence

```text
remaining built-in multi-seed / multi-scale Minecraft evidence
    -> #214 / #267 / #283 human morphology judgment
    -> bounded recipe/control correction if actually required
    -> one representative pairwise hybrid proof
    -> one representative external-provider proof
    -> enough regional composition evidence to rule out an integration-level failure
    -> pivot to Phase 2
```

### Owner-approved post-morphology pivot

This roadmap records the program decision that the project should **not** continue into a long isolated morphology-certification era after built-in confidence is adequate.

After the current built-in matrix and required human morphology judgment:

- preserve broad morphology capacity;
- retune only if evidence/human review identifies a real production-selection/control problem;
- prove one representative hybrid and one representative external-provider/backend composition seam;
- use bounded regional evidence rather than exhaustive isolated regional permutations;
- then move into integrated geology/materials, hydrology, structures, and ecology.

This resolves the sequencing question previously tracked as **HS-03 (post-morphology pivot point)**. A stale `OPEN` marker for HS-03 must not reopen this already-approved transition; `HUMAN_STRATEGY_ROADMAP.md` should be reconciled when convenient.

### Remaining human authority

The roadmap does **not** pre-judge the live morphology aesthetic questions tracked by #214/#267/#283 / HS-04.

Human review still owns whether:

- the current Massif surface cadence is acceptable for its intended production role;
- Tableland identity is sufficiently distinct;
- production recipes need different quiet/dramatic terrain balance;
- underside vocabulary needs further visual development.

### Exit criteria

Phase 1 exits when evidence supports all of the following:

- built-in family identity survives representative seed/scale variation;
- Authorship can deliberately request useful terrain character rather than merely generate it by accident;
- Content can select appropriate recipes without pruning difficult terrain from generator capacity;
- Minecraft realizes requested neutral terrain faithfully;
- representative hybrid/provider composition works through the production path;
- further isolated morphology work is lower-value than dressing the world with geology/water/structures/ecology.

## 8. Phase 2 — Integrated physical world

**State:** NEXT_AUTHORIZED.

### Objective

Turn good floating geometry into believable places whose materials, water, caves, structures, soils, and ecology compose from the same authored world meaning.

Primary sequence:

```text
production geography
    -> materials / geology realization
    -> authored hydrology realization
    -> structures on current exact-volume lifecycle
    -> ecology under real production geography
    -> representative dressed region acceptance
```

### 8.1 Materials and geology realization

Consume accepted authored geology/resource opportunity rather than creating a second resource-geography system.

Near-term representative resource priorities remain:

- Iron — bootstrap-critical / starting-cluster concern;
- Copper — post-flight regional engineering;
- Zinc — post-flight regional engineering.

Implement physical deposit identity/geometry/accessibility/placement/persistence through Implementation-owned adapters while preserving Authorship provenance and Content availability/guarantee policy.

Petroleum machinery/source bridge work may continue where already authorized, but petroleum remains mature strategic industry and must not become a Phase-2/Bootstrap dependency merely because its integration seam exists.

### 8.2 Authored hydrology realization

Consume accepted channel, retained-waterbody, cascade/waterfall, edge-discharge, aquifer, and spring semantics through the current exact-volume lifecycle.

Important distinction:

> vanilla/native springs are not a substitute for authored visible hydrology.

Acceptance should prove believable water on representative production terrain, including persistence and boundary behavior, not every possible watershed arrangement.

### 8.3 Structures

Reintegrate structures against the newest production terrain and site-evidence stack.

Representative modes eventually include:

- surface;
- embedded;
- cliff/rim;
- underside;
- detached;
- settlement/infrastructure;
- network/route-related structures where the owning Content contract exists.

Consume AUTH-0096/0097-style site/support/access evidence rather than inventing a competing site graph. Content owns semantic role/selection; Implementation owns exact block-space geometry, orientation, support/clearance, accommodation, mutation, persistence, and lifecycle.

### 8.4 Ecology under production geography

Move from ecology proof specimens to habitat composition that visibly follows:

```text
terrain + substrate + moisture + water + altitude + isolation + authored ecology
    -> realized habitat/content
```

Do not tune final world population density here unless the representative integration actually requires it.

### 8.5 Allowed future-phase preflight

Late in Phase 2, a bounded combined render-stack feasibility spike is authorized because a hard incompatibility could materially affect later atmosphere/visual architecture:

```text
Minecraft 1.21.1 / NeoForge
+ Iris
+ Distant Horizons
+ candidate reference shader foundation
+ candidate volumetric-cloud integration
+ representative Skyforge floating terrain
+ representative Create/Aeronautics rendering
```

This is a **feasibility spike**, not authorization to begin full shader/weather production during Phase 2.

### Exit criteria

At least one representative production region is accepted as a coherent dressed place:

> intentional landform + geology/materials + authored water + caves + ecology + appropriate structures, realized persistently and correctly in Minecraft.

When this exists, the larger risk becomes complete player experience rather than isolated world dressing.

## 9. Phase 3 — Bootstrap Province

**State:** PLANNED, with substantial already-authorized bounded work.

### Objective

Produce the first complete Skyforge game slice from crash through independent Skyfarer status and one genuine post-tutorial systemic freight/economy loop.

Existing #224, civilization precommit work, #466, and #467 remain authoritative bounded consumers and may proceed under the existing-work exception.

### Intended player sequence

```text
crashed Bellanca
-> survival / orientation
-> basic Minecraft + Create competence
-> cheap gliding / vertical traversal
-> shared atmosphere / thermals / soaring ecology
-> gather first-flight closure
-> build crude practical powered aircraft
-> cross a distance where aircraft materially beats personal flight
-> reach eligible civilization / Guild Hall
-> Bellanca claim + recorder evidence
-> liability reversal + restitution choice
-> TUTORIAL COMPLETE
-> first routine real producer/consumer freight opportunity
-> systemic sandbox
```

### 9.1 Opening and survival

The crash establishes circumstance and motivation. The player first secures an ordinary survival foothold rather than immediately receiving a finished operational aircraft.

### 9.2 Early engineering

Teach enough Create/mechanical competence to make the first aircraft feel earned. Hard bootstrap closure may not depend on a lucky loot roll.

### 9.3 Personal mobility

Cheap gliding teaches verticality and atmosphere while preserving the invariant:

> PERSONAL MOBILITY IS CHEAP. LOGISTICS ARE NOT.

### 9.4 First powered flight

The first aircraft must be a practical logistics/travel capability, not merely a faster glider.

Production Bellanca functionality/beautification should be scheduled late enough that it does not displace the crude-first-flight proof, while still being ready for the claim/restitution experience that needs it.

### 9.5 Civilization / Guild closure

The first eligible civilization encounter must make mature skyborne infrastructure legible through layout/services rather than exposition alone.

Bellanca onboarding closes through the accepted claim/evidence/liability/restitution state sequence.

### 9.6 First sandbox economy

Prove one transaction using the same architecture intended for mature systemic play:

```text
real producer stock/need
-> real contract
-> authoritative pickup/custody transition
-> physical cargo
-> physical transport
-> delivery validation
-> destination semantic reconciliation
-> payment/obligation settlement exactly once
```

Do not substitute a quest-only fiction for this loop.

### 9.7 Minimum Bootstrap atmosphere

Bootstrap requires enough real atmosphere to teach the central game:

- wind;
- trusted thermals/updrafts;
- downdrafts where relevant;
- turbulence/shear where justified;
- pressure/altitude behavior;
- bounded weather state sufficient for the player/fauna/aircraft to consume the same atmospheric truth.

It does **not** require the finished cinematic atmosphere renderer from Phase 4.

### Human gates

Before final starting-province guarantees are locked, resolve the then-current Bootstrap first-hours policy (currently tracked under HS-06), including timing/guarantee scope and first-civilization certainty.

Human play/visual judgment remains mandatory for:

- opening/tutorial pacing;
- first powered-flight feel;
- first sight/arrival at civilization;
- Guild Hall usability;
- Bellanca claim/restitution closure;
- first post-tutorial freight/market loop.

### Exit criteria

A new player can start from the crash, learn the key systems through play, obtain practical powered flight, reach civilization, resolve the Bellanca onboarding arc, and complete one systemic logistics/economy loop without developer intervention.

At Phase-3 exit, Skyforge is demonstrably a game rather than only a world-synthesis stack.

## 10. Phase 4 — Atmospheric and visual-world convergence

**State:** PLANNED.

### Objective

Make atmospheric depth, distant geography, weather, lighting, and the world below the player become a defining visual and mechanical identity while keeping semantic weather independent of the rendering backend.

The detailed atmosphere/cosmology strategy currently lives in separate worldbuilding/strategy work and should be consumed only once merged/current.

### 10.1 Semantic weather

Expand the Bootstrap atmosphere into regional/climatological state:

- prevailing winds;
- pressure/temperature/humidity tendencies;
- moving storm/front/convection systems;
- vertical winds;
- precipitation;
- turbulence;
- visibility;
- persistent lower-deck tendency/openings.

Use coarse semantic state, deterministic reconstruction, and bounded updates rather than CFD.

### 10.2 Distant terrain

Treat Distant Horizons (or an equivalent accepted solution) as distant **solid-terrain** infrastructure, not atmosphere authority.

### 10.3 Reference shader stack

Develop/adapt a Skyforge reference Iris-compatible shaderpack only after the combined-stack feasibility result justifies it.

Prioritize Skyforge-specific needs:

- atmospheric scattering/extinction;
- DH compositing/depth integration;
- altitude-dependent sky presentation;
- coherent sunlight/shadow/weather response;
- water;
- performance appropriate for long view distance and volumetric atmosphere.

Reuse a permissively licensed existing shader foundation where practical before writing generic rendering machinery from scratch.

### 10.4 Clouds and weather presentation

Integrate local volumetric clouds, distant storms, rain curtains, lightning, weather-responsive lighting, and atmospheric LOD under Skyforge semantic weather authority.

### 10.5 Persistent lower cloud deck

Render the lower cloud ocean as a major visual boundary, with climatologically meaningful openings and towers rather than a static decorative plane.

### 10.6 Lower Sea implication

Through rare openings, render only enough distant Lower Sea/old-surface information to establish depth and cosmology:

- black/dark water;
- haze;
- Dark Mountains;
- rare immense shelf/old-surface silhouettes.

Do not require a fully playable Lower Sea or planet-sized continuously loaded ocean.

### Exit criteria

Normal flight, distant navigation, a major storm encounter, and at least one lower-cloud opening all communicate the intended scale and atmospheric identity without explanatory text, while weather remains mechanically coherent with aircraft/fauna/player behavior.

## 11. Phase 5 — Regional systemic sandbox

**State:** PLANNED.

### Objective

Scale the Bootstrap architecture from one proving province into a persistent world of multiple regions whose geography, settlement roles, markets, Guild services, contracts, and freight create meaningful strategic choices.

### Main growth

```text
multiple provinces/clusters
-> differentiated resource/service roles
-> sparse civilization
-> local bounded markets
-> Guild network and route knowledge
-> contracts/freight
-> persistent consequences
```

Mature civilization state should continue to follow the accepted simulation principle:

> unobserved history/activity stays semantic; observed or player-consequential state becomes physical/persistent as required.

### Scope discipline

Bring advanced CIV systems online only when a concrete player loop consumes them. Do not activate the entire civilization precommit corpus merely because the architecture exists.

Possible consumers include:

- standing/trust;
- credit;
- insurance/claims;
- route security;
- settlement service degradation/restoration;
- autonomous freight;
- faction ownership/consequence where play evidence justifies it.

### Exit criteria

A mature player can choose among multiple persistent regional logistics/economic strategies without requiring every settlement, shipment, or aircraft to remain physically simulated offscreen.

## 12. Phase 6 — Mature engineering, industry, and computing

**State:** PLANNED.

### Objective

Make engineering increase the player's scale of agency rather than simply adding stronger item tiers.

### Capability growth

Representative mature capabilities include:

- Brass and advanced Create machinery;
- electricity/power infrastructure where retained stack supports it;
- petroleum extraction/refining/engines;
- larger and more specialized aircraft/vessels;
- industrial freight handling;
- remote stations and infrastructure;
- CC:Tweaked instrumentation/computing;
- telemetry/networking;
- later autopilot/fleet/infrastructure orchestration.

Petroleum remains mature strategic-node content. Existing retained Diesel Generators machinery/source-bridge work should be consumed, not reinvented.

CC:Tweaked remains infrastructure progression, not a first-flight prerequisite and not a teleporting replacement for physical logistics.

AAL or another retained logistics layer may execute physical movement if its accepted public contract supports the use case; Skyforge remains authority for route/shipment/economic meaning and canonical identity.

### Exit criteria

Mature engineering lets the player operate reliable infrastructure and logistics at a qualitatively larger scale than an early Skyfarer while preserving meaningful geography, transport, loading, and resource constraints.

## 13. Phase 7 — Adventure, ecology, danger, archaeology, and light Nether integration

**State:** PLANNED.

### Objective

Give mature mobility/infrastructure compelling frontiers and hazards without solving the world with content density.

### World content

Expand and tune as real consumers require:

- sky ecology and migration;
- predators/raptors/dragons;
- Sky Whales and dangerous/resource-bearing sky industries such as whaling where accepted;
- hostile-controlled airspace;
- ruins and archaeology;
- rare exceptional structures/phenomena;
- boss encounters;
- anomalous/legendary encounters.

Final population rhythm remains a human/product tuning concern. Negative space is intentional.

### Nether policy

The Minecraft Nether should receive a **light integration treatment**, not a wholesale Skyforge rewrite, unless future executable evidence shows a concrete need.

Default direction:

- retain useful Nether resources, mobs, structures, Wither chain, and mod dependencies;
- reinterpret rather than remove its chthonic/soul/undeath character;
- constrain trivial arbitrary portal access if needed for geography/logistics;
- selectively improve terrain/structures/ecology/visual fit using existing content first;
- preserve spatial distortion where it supports useful dangerous infrastructure without obsoleting the sky;
- keep the Nether backend-specific rather than indispensable to Skyforge's cross-backend identity.

If the still-open cosmology strategy PR changes these details before Phase 7 begins, consume the merged authority then rather than conversational recollection.

### Exit criteria

Leaving established routes produces real ecological, archaeological, territorial, anomalous, and combat variety while the sky still contains large quiet spaces and the Nether remains useful without becoming the project's dominant development sink.

## 14. Phase 8 — High sky and End progression

**State:** PLANNED.

### Objective

Fulfill Skyforge's central vertical premise as a late-game technological/exploration frontier.

### Progression shape

```text
ordinary inhabited sky
-> high sky / sparse civilization
-> declining pressure and harder flight
-> specialized high-altitude capability
-> extreme upper-atmosphere transition
-> End-region backend
```

The intended cosmological direction is that Minecraft's End is a separate **implementation dimension** but canonically the extreme upper atmosphere / near-exospheric highest island field of the same world, not another metaphysical realm like the Nether.

Strongholds may remain an ancient/anomalous shortcut to that same upper region while modern progression reaches it through ascent technology.

### Likely late-game content

- extreme-altitude aviation constraints;
- rare high-sky stations/resources;
- Levitite enrichment;
- extreme/radiation-exposed geology;
- sparse adapted or possibly extraterrestrial life;
- ancient End structures;
- upper-atmosphere visual transition toward space.

### Exit criteria

Late progression reaches the End through a Skyforge-specific vertical technological arc, with Strongholds functioning as an alternate archaeological route rather than the only conceptual path.

## 15. Phase 9 — Alpha / release convergence

**State:** PLANNED / human-scope dependent.

### Objective

Stop extending architecture and prove the whole selected release scope as a coherent game.

This phase is dominated by integration, tuning, packaging, compatibility, and player validation rather than new systemic invention.

### Convergence classes

- new-player progression and recovery from failure;
- production world quality/distribution;
- flight and atmosphere;
- freight/economy/civilization;
- mature industry included in the selected release scope;
- Nether/End features included in the selected release scope;
- Music/audio integration and final masters required for the build;
- guidance/UI/accessibility as required;
- save longevity and migration policy;
- multiplayer/dedicated-server sanity;
- performance/support tiers;
- dependency/license/redistribution policy;
- modpack/update/version-lock policy;
- release presentation and documentation.

### Human scope gates

The exact first-public-alpha boundary remains a human decision and must be resolved before this phase becomes active. Current strategy topics such as HS-05, HS-10, and HS-11 exist for that purpose.

Do not silently assume every Phase-4-through-8 feature is required for the first public alpha merely because it appears in the complete-game roadmap.

### Exit criteria

A release candidate can be handed to a player who knows nothing about the development history and judged as a coherent, stable, understandable Skyforge game.

## 16. Optional expansion — explicitly off the first-complete-Minecraft critical path

**State:** OPTIONAL_EXPANSION.

Do not wake a producer for these merely because current work becomes quiet:

- directly playable Lower Sea;
- extensive Dark Mountain / drowned-continent expedition gameplay;
- planet-scale continuously instantiated ocean beneath the sky;
- a bespoke Deep / giant-cavern game layer;
- a second production backend;
- a fully native non-Minecraft Skyforge game;
- fully custom volumetric cloud/planet renderer when retained/adapted tools suffice;
- seamless literal orbital/planetary-scale continuous traversal.

These may become separate future roadmap programs when a deliberate owner decision promotes one of them.

## 17. Parallel lanes

Music, Presentation, and Audit/orchestration are not numbered product phases.

### 17.1 Music / Audio

Music may advance composition independently when it has a clear authored objective and no listening/source gate. Runtime/adaptive integration should wait for accepted gameplay state that can provide authoritative cue triggers. Final GAME/OST mastering should follow actual release/showcase need.

Music must not invent gameplay/world meaning merely to create work.

### 17.2 Presentation

Presentation consumes accepted project truth. It may update claims, diagrams, demo scripts, captures, portfolio/release packaging, and audience artifacts when producer progress materially changes what can legitimately be shown.

Presentation must not become the critical path merely because communication work is available.

### 17.3 Audit / orchestration

Audit remains the supervisory/liveness/evidence-economy layer across every phase.

Once orchestration infrastructure is live-accepted and reliable, further controller/infrastructure work requires:

- an observed reliability/safety/efficiency defect;
- or an explicit product-policy change.

Do not let orchestration engineering become a substitute product phase.

## 18. Roadmap transition registry

This section records cross-phase product decisions so Audit/orchestration can distinguish a settled transition from a genuine human gate.

### RM-R01 — post-built-in morphology pivot — RESOLVED

After current built-in morphology confidence and required human #214/#267/#283 judgment:

```text
bounded correction if needed
-> one representative hybrid proof
-> one representative external-provider proof
-> bounded regional smoke
-> Phase 2 integrated world systems
```

Do not require exhaustive hybrid/provider/regional morphology certification before integrating geology/hydrology/structures/ecology.

This is the owner-approved resolution of the earlier HS-03 sequencing question.

### RM-R02 — atmosphere timing — RESOLVED

- Bootstrap requires real mechanical atmosphere sufficient to teach flight, gliding, fauna, pressure, and weather interaction.
- Full Distant-Horizons/shader/cloud/lower-deck visual convergence is a distinct post-Bootstrap primary phase.
- A bounded combined render-stack feasibility spike is allowed in late Phase 2 when it can retire a hard architecture/compatibility risk.

### RM-R03 — Nether scope — RESOLVED DIRECTION / DETAILS MAY EVOLVE

Treat the Nether lightly in the Minecraft backend. Reuse and reinterpret useful existing content before bespoke reauthoring. Do not make Nether reconstruction a prerequisite for Bootstrap or general world convergence.

### RM-R04 — End role — RESOLVED DIRECTION / IMPLEMENTATION LATER

Treat the End as the Minecraft backend for the extreme upper-atmosphere / highest-island frontier of the same physical world, with modern ascent and possible Stronghold shortcut paths.

### RM-R05 — Lower Sea / Deep / second backend — DEFERRED

These remain valid setting/architecture possibilities but are not required for the first complete Minecraft realization unless future player evidence or owner strategy deliberately promotes them.

## 19. What the orchestrator should do today

At the time this roadmap is introduced:

```text
Phase 0 = COMPLETE
Phase 1 = PRIMARY_ACTIVE
Phase 2 = NEXT_AUTHORIZED
```

The orchestrator should therefore:

1. preserve healthy current producer work and human gates;
2. drive the current production-morphology boundary to information-bearing closure rather than restarting older foundation work;
3. stop for the remaining human morphology judgment when ready;
4. after that judgment, execute only bounded corrective morphology work if required;
5. complete the representative hybrid/provider/regional smoke described by RM-R01;
6. then pivot the primary program focus into Phase 2 materials/geology -> hydrology -> structures -> ecology;
7. allow current already-authorized Bootstrap civilization/runtime work to continue without interpreting Phase 3's PLANNED state as cancellation;
8. leave broad atmosphere/shader, mature industry, adventure/Nether, End, Lower Sea, and future-backend work dormant except for the explicitly authorized feasibility/dependency exceptions above.

If repository state has advanced beyond this snapshot, use current accepted evidence to determine which criteria have already been satisfied rather than blindly repeating them.

## 20. Maintenance rule

Update this roadmap only when one of these changes:

- a phase becomes the primary convergence focus;
- a phase's entry/exit criteria materially change;
- the owner resolves a cross-phase strategy decision;
- a major downstream discovery changes the product sequence;
- a planned phase is promoted, split, merged, or deliberately removed from the critical path.

Do **not** edit this document for every lane-local milestone, PR number, CI run, merge SHA, or minor implementation detail. Those belong in lane state/issues/tests/history.

The roadmap should remain a stable route map, not another activity log.
