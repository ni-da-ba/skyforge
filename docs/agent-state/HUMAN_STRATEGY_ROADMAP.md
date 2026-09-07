# Skyforge Human Strategy Roadmap

**Status:** Canonical human-decision backlog for cross-lane strategy  
**Updated:** 2026-09-07 (America/Chicago)

## Purpose

This file records strategy questions that should be decided by the project owner rather than inferred
independently by producer agents.

It is not a producer todo list. A topic belongs here when the answer can materially change multiple
lanes, acceptance philosophy, scope, or the intended player experience.

Audit should watch the trigger for each open topic and surface it when the decision becomes timely.
Do not interrupt the project owner merely because an item exists.

## Escalation rule

For each open strategy topic:

```text
NOT YET AT TRIGGER
    -> remain silent

TRIGGER APPROACHING / DECISION NOW BLOCKS MULTIPLE LANES
    -> notify Nicholas and summarize the concrete choice

DECISION MADE
    -> persist the decision in the owning design/contract/state document
    -> mark the topic RESOLVED here
    -> producer agents proceed from repository state
```

A producer may gather evidence before a human decision, but must not silently choose a major product
direction merely to keep a branch moving.

---

## RESOLVED / ESTABLISHED STRATEGY

These should not be reopened without contrary evidence.

### HS-R01 — hierarchical world model

**Resolved direction:** island-local semantic identity composes into cluster/region/province systems.
An island may own morphology, ecology, hydrology, geology, structures, and local history while higher
levels own relationships, traffic, specialization, civilization, exceptional phenomena, and other
cross-island structure.

### HS-R02 — reuse-first content sourcing

**Resolved direction:** vanilla -> retained mods/libraries -> config/datapack/integration -> thin
Skyforge adapter -> bespoke content only for demonstrated gaps.

Skyforge owns meaning; it does not need to author every asset.

### HS-R03 — exceptional regional phenomena

**Resolved direction:** rare Regional / Unusual / Exceptional phenomena may violate ordinary
geographic expectations while remaining semantically coherent and shader-independent. Cross-island
phenomena belong at an appropriate higher-order authored layer rather than being forced into
island-local semantics.

### HS-R04 — personal mobility versus logistics

**Resolved direction:** personal mobility is cheap; logistics are not. Gliding may be broadly useful,
while aircraft, airships, infrastructure, and automation retain advantages in bulk, fluids, entities,
contraptions, repeatability, and throughput.

### HS-R05 — generated civilization remains Minecraft-like

**Resolved direction:** players may ordinarily dismantle and loot civilization. Progression-sensitive
assets are controlled primarily by content budgeting and world state rather than broad invulnerability
or chunk protection.

### HS-R06 — inhabited airspace / aircraft fidelity

**Resolved direction:**

- attractive reusable schematics may receive separate automation/control packages;
- NPC aircraft need not simulate player-equivalent fuel, ammunition, maintenance, or service
  inventories;
- regional infrastructure may represent fuel, munitions, maintenance, radar, and sortie capability;
- most transient/patrol aircraft do not need autonomous landing;
- far-field traffic is coarse state and near-player aircraft are materialized when relevant;
- intact aircraft capture must not make the P2 reliable-flight engineering transition optional;
- later capture/repair may become deliberate reward content.

### HS-R07 — computing is infrastructure, not first-flight prerequisite

**Resolved direction:** CC:Tweaked / retained avionics and networking are optional capability
progression layered onto mechanical competence rather than mandatory bootstrap knowledge.

---

# OPEN HUMAN STRATEGY TOPICS

## HS-01 — Implementation certification depth and "good enough" boundary

**Status:** **DISCUSS NOW**

**Why now:** SF-IMP-0083 / issue #284 currently requires all 20 remaining built-in AUTH-0083
seed/scale specimens to receive exact Minecraft evidence including admission, persistence, digest, and
actual-client reopen. The tranche has already exposed serious warmup/catch-up performance pathologies
and accumulated substantial branch/integration churn.

The technical work has been useful, but the program now needs an explicit answer to:

> How exhaustive must Minecraft carrier certification be before we accept that the generic carrier
> works and move on to the integrated world?

### Decision

Choose the long-term acceptance philosophy for large deterministic corpora:

**A — exhaustive full-lifecycle certification**
- every exact corpus member receives full Minecraft lifecycle + reopen evidence;
- strongest individual evidence;
- highest CI/runtime/integration cost.

**B — layered certification**
- all corpus members receive cheap deterministic identity/support/topology/digest evidence;
- a representative matrix receives full Minecraft lifecycle/persistence/reopen;
- targeted expansion occurs only when a family/scale/provider exercises a genuinely different backend
  path or a sampled failure appears.

**C — split milestone**
- close the generic backend-carrier milestone on bounded representative evidence;
- move exhaustive atlas generation into a manual visual/regression corpus that does not block the
  next production-world milestone.

### Audit recommendation

Prefer **B, with C-style separation of heavy visual atlas work from ordinary acceptance**.

The accepted SF-IMP-0081/0082 work already indicates one generic exact-carrier architecture serves
multiple families. Repeating actual-client persistence for every parameter specimen has diminishing
architectural information unless the specimen exercises a new backend path.

Do not silently relax issue #284; this is a human acceptance-policy decision.

**Trigger:** already met.

---

## HS-02 — CI, characterization, and performance budget

**Status:** **DISCUSS NOW / establish before the next large matrix**

The project has a technical principle against endless micro-optimization, but it lacks a durable
human-owned budget for how expensive normal acceptance may become.

Decide:

- expected upper bound for routine PR CI;
- which workloads may be manual-only;
- acceptable duration for a deliberate heavy characterization;
- reference machine/server class for worldgen performance;
- acceptable near-player generation/tick hitch behavior;
- whether full actual-client reopen is required per specimen, per backend path, or only at milestone
  boundaries;
- how much GitHub Actions cost/runtime the program is willing to spend for redundant confidence.

### Audit recommendation

Use three tiers:

```text
ROUTINE PR GATES
    cheap, deterministic, automatically recurring

MILESTONE CHARACTERIZATION
    deliberately triggered, bounded, representative

HUMAN / SOAK / LARGE-MATRIX EVIDENCE
    manual or scheduled only when the milestone truly needs it
```

The program should treat a 50-minute repeated matrix as a design smell unless that runtime is itself
the thing being measured.

**Trigger:** already met by SF-IMP-0083.

---

## HS-03 — Post-morphology pivot point

**Status:** OPEN — discuss when SF-IMP-0083 reaches a clean acceptance boundary.

Issue #214 eventually asks for hybrids, external-provider axes, regional contexts, and dressed
material/ecology/hydrology/geology contexts. The program must decide how much of that atlas should
precede the next substantive world-system integration.

### Decision

After built-in family confidence is adequate, should the program:

- continue deeply through hybrid/provider/regional morphology certification first; or
- prove a small hybrid/provider smoke set, then pivot into geology/materials -> hydrology ->
  structures/ecology and evaluate morphology under real world dressing?

### Audit recommendation

Do **one representative hybrid and one external-provider/backend smoke**, then pivot to integrated
world systems.

The highest remaining product risk is increasingly interaction among morphology, geology, hydrology,
structures, ecology, civilization, and lifecycle—not whether another isolated morphology specimen can
persist.

**Trigger:** synchronized SF-IMP-0083 acceptance candidate exists.

---

## HS-04 — Production morphology aesthetic doctrine

**Status:** OPEN — human visual decision after representative multi-seed/scale evidence.

Issues #267 and #283 deliberately defer tuning until broader evidence exists.

Human decisions needed:

- how much ordinary traversable land should be low/medium relief;
- how dramatic Massif may be before walking quality suffers;
- how broad/quiet Tableland interiors should become;
- desired ratio of broad benches/terraces/valleys to local hills;
- how much family identity should be visible at horizon distance;
- whether undersides need a richer explicit secondary vocabulary;
- how much procedural signature/repetition is tolerable across a cluster.

### Current recommended north star

> Most traversable land should not be mountainous; dramatic relief should emerge from quieter
> geography rather than becoming constant background texture.

Preserve strong macro verticality, legible families, substantial negative space, deliberate
undersides, and coherent terrain suitable for structures and hydrology.

**Trigger:** #214 multi-seed/scale comparison is ready for human review.

---

## HS-05 — Wild Blue Yonder first-public-alpha scope

**Status:** OPEN — should be decided before Bootstrap Province grows into a release candidate.

The Bootstrap Province defines a vertical slice, but the repository does not yet state exactly what
must ship in the first public/playable alpha.

Decide whether alpha requires:

- only a production Overworld province or also Nether/End authored terrain;
- one complete civilization archetype versus a broad civilization system;
- hostile/civilian air traffic;
- Bellanca and/or only the crude first powered aircraft;
- adaptive music versus static soundtrack integration;
- quests/onboarding depth;
- multiplayer/server certification;
- Distant Horizons as hard dependency;
- exceptional phenomena;
- final or provisional mod roster.

### Audit recommendation

Define an intentionally narrow alpha around:

```text
production Overworld province
+ survival foothold
+ glider
+ first powered aircraft
+ post-flight Copper/Zinc specialization
+ one meaningful freight loop
+ one sparse civilization/infrastructure encounter
+ legible ecology/atmosphere
+ save/reload/multiplayer sanity
+ soundtrack/presentation sufficient to establish identity
```

Do not require complete Nether/End reauthoring, mature fleet automation, or every planned exceptional
feature for first alpha.

**Trigger:** before final Bootstrap Province implementation begins.

---

## HS-06 — Bootstrap Province first-hours progression and guarantee philosophy

**Status:** OPEN — discuss before geology/resources/structures are locked into the starting recipe.

The repository establishes the sequence but not the exact experiential contract.

Human choices include:

- intended time-to-glider and time-to-first-powered-flight;
- how much experimentation failure/retry should the province support;
- whether Iron/Andesite/wool/adhesive/workshop closure is guaranteed on the starting island, group,
  cluster, or province;
- whether first Copper/Zinc discovery is guaranteed directly through geology or may be satisfied by a
  guaranteed trade/salvage route;
- whether the first civilization encounter is optional, probable, or guaranteed after flight;
- how much quest text is acceptable before observation should teach the system.

### Existing constraint

No lucky loot roll may be required for bootstrap closure. Trade/salvage can satisfy a hard guarantee
only when the world recipe guarantees access to that path.

**Trigger:** before Content turns C20/base-metal opportunity into concrete province selection and before
production resource realization is locked.

---

## HS-07 — Population tone: ecology, civilization, threats, and sky traffic

**Status:** OPEN — discuss before population systems are realized at production density.

The project already prefers sparse negative space, but a human tone decision is still needed for how
inhabited and dangerous the actual game should feel.

Decide relative density/rhythm of:

- ordinary wildlife;
- soaring birds / Sky Whales / aerial ecology;
- civilian aircraft and balloons;
- faction patrols;
- terrestrial ambient monsters;
- ecological predators;
- hostile civilization;
- anomalous/legendary encounters.

Questions:

- Should ordinary wilderness usually feel safe, lonely, dangerous, or variable by province?
- How often should a player see a vehicle on the horizon in settled airspace?
- How strongly should entering hostile-controlled airspace announce itself before combat?
- How much empty sky must remain even in developed regions?

**Trigger:** before final ecology/spawn/traffic budgets are tuned.

---

## HS-08 — Civilization interaction, ownership, and social consequence

**Status:** PARTIALLY RESOLVED — discuss only when active civilization is executable.

Already resolved:
- ordinary structures remain dismantleable;
- progression-sensitive assets are budgeted;
- pre-P2 intact aircraft theft must not trivialize flight progression.

Still open:

- whether active settlements react socially to theft/dismantling;
- whether civilian aircraft may ever be hijacked;
- whether faction hostility/reputation persists after attacks;
- whether customs/restricted airspace exists as gameplay rather than scenery;
- whether settlement services recover/rebuild after player destruction;
- whether consequences remain purely physical or eventually include lightweight faction state.

### Audit recommendation

Do not add an RPG crime/reputation system preemptively. Start with physical causality, faction combat,
and deliberate capture eligibility; add social state only if playtesting proves the world feels
implausibly indifferent.

**Trigger:** first active civilization specimen with meaningful player interaction.

---

## HS-09 — Permanent dimension and cosmology strategy

**Status:** OPEN / NOT YET BLOCKING

Current implementation keeps Nether and End largely under vanilla terrain authority while C10/C15
prove 1:1 Nether coordinate behavior as an aviation-preserving prototype. The documents explicitly
leave future authored Nether/End open.

Human decisions eventually needed:

- whether the 1:1 Nether scale is permanent WBY canon or provisional balance;
- whether the End becomes the first fully authored second dimension;
- whether Nether is eventually a Skyforge solid/cavern grammar;
- how portals interact with aircraft, cargo, and infrastructure;
- whether dimensions are required for first public alpha or later expansion.

**Trigger:** before dimension terrain authorship or assembled-vehicle portal transfer becomes active
roadmap work.

---

## HS-10 — Dependency freeze, licensing, and redistribution policy

**Status:** OPEN / later alpha gate

The reuse-first strategy deliberately increases reliance on retained mods and possibly community
schematics/assets.

Before packaging a public alpha, decide:

- core dependency budget;
- acceptable number of optional visual/content mods;
- policy for bundling third-party schematics;
- explicit-license versus permission-required assets;
- whether Skyforge ships adapted assets or only uses them as engineering references;
- version-lock/update policy for fast-moving Aeronautics/Sable ecosystem dependencies.

**Trigger:** before public pack assembly / redistribution.

---

## HS-11 — Product performance and supported play environment

**Status:** OPEN / alpha gate

Separate from CI cost, define what the released game must support:

- target RAM;
- target CPU/GPU class;
- Distant Horizons expectations;
- single-player versus dedicated server;
- expected multiplayer player count;
- view/simulation-distance assumptions;
- acceptable first-generation and exploration latency;
- whether pre-generation is recommended, optional, or forbidden as a crutch.

**Trigger:** once Bootstrap Province can be played end-to-end and before external testers.

---

## HS-12 — Audio presentation scope

**Status:** OPEN / later

Human listening is inherently required for:

- Track-00 repair A/B;
- Track-06 source recovery/disposition;
- final cue identity and mix/master acceptance;
- how much adaptive music is needed for first alpha;
- ambience/soundscape priority relative to new composition.

Do not build a large adaptive runtime before real gameplay states exist to justify transitions.

**Trigger:** playable Bootstrap Province loop and synchronized source integrity.

---

## HS-13 — Independent Skyforge / second-backend ambition

**Status:** OPEN / explicitly non-blocking for WBY alpha

A second serious backend would materially strengthen the claim that Skyforge is a general
backend-neutral world-authoring system rather than a Minecraft-specific architecture.

Do not let that proof delay the first complete Minecraft game realization.

**Trigger:** after the first credible Wild Blue Yonder alpha unless a second backend becomes unusually
cheap to prove earlier.

---

## Automated roadmap policy

Audit should treat the ordering approximately as:

```text
NOW
    HS-01 certification depth
    HS-02 CI/performance budget

AFTER SF-IMP-0083 CLEAN BOUNDARY
    HS-03 post-morphology pivot
    HS-04 morphology tuning review

BEFORE / DURING BOOTSTRAP PROVINCE
    HS-05 first-alpha scope
    HS-06 first-hours/guarantees
    HS-07 population tone
    HS-08 civilization interaction when executable

LATER ALPHA / PRODUCTIZATION
    HS-09 dimensions
    HS-10 dependencies/licensing
    HS-11 supported performance environment
    HS-12 audio presentation

POST-ALPHA
    HS-13 second backend
```

The watchdog should notify Nicholas when a trigger is met and the decision is not yet recorded. It
should not repeatedly notify for the same topic without new evidence.
