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

## Audit evidence-saturation doctrine

The project owner explicitly assigns Audit responsibility for catching situations where Skyforge is
testing too much and proceeding too little.

For any expensive or repeated validation step, Audit should ask:

> **What materially distinct failure mode or acceptance uncertainty does this next test retire?**

If the answer is not concrete, the burden shifts toward moving forward rather than accumulating more
evidence.

### Preferred validation hierarchy

```text
EXHAUSTIVE CHEAP CONTRACT EVIDENCE
    identity / deterministic compilation / support / invariants / unit properties

REPRESENTATIVE FULL-RUNTIME EVIDENCE
    expensive Minecraft lifecycle / persistence / reopen / integration paths

TARGETED EXPANSION
    only when a family, scale, provider, subsystem, or observed failure exercises a distinct risk

HUMAN SAMPLE REVIEW
    visual / traversal / listening / gameplay judgments on a deliberately representative corpus
```

Do not automatically multiply an expensive lifecycle test by every deterministic parameter point when
those points share the same backend path.

### Evidence saturation signals

Audit should intervene when one or more are true:

- repeated specimens prove the same invariant through the same codepath without new failures;
- CI/runtime cost is increasing faster than information gained;
- an acceptance matrix grows because the corpus is large rather than because the backend risk is
  heterogeneous;
- a producer keeps extending test coverage after the milestone's original risk has been retired;
- full-client/reopen or soak evidence is repeated where cheaper deterministic evidence already proves
  parameter variation and representative runtime evidence proves the lifecycle;
- branch drift, merge churn, or CI reruns begin consuming more work than the feature itself;
- further polishing of an isolated subsystem delays first integration with the next major world/game
  system.

The response is not to lower correctness standards. Prefer:

1. name the remaining uncertainty;
2. choose the cheapest evidence that can falsify it;
3. sample representative full-runtime cases;
4. reserve exhaustive heavy matrices for demonstrated heterogeneity or release/soak purposes;
5. close the milestone once its stated risk is retired;
6. move to the next highest product/integration risk.

This doctrine is a program-health rule. Producer lanes should not need to request permission each time
Audit identifies clear evidence saturation; Audit may recommend narrowing/splitting the gate, while
the human owner retains final authority over major acceptance-policy changes.

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

### HS-R08 — layered validation / evidence economy

**Resolved direction:** the project owner approved risk-driven layered certification to reduce
evidence saturation while preserving robustness.

Canonical policy: [VALIDATION_POLICY.md](VALIDATION_POLICY.md).

Key rules:

- cheap deterministic contract evidence remains exhaustive where practical;
- expensive Minecraft lifecycle/persistence/reopen evidence is representative by risk-equivalence
  class rather than multiplied across every parameter point;
- a sampled failure automatically widens the affected expensive-test class;
- expensive evidence may remain portable across orthogonal main movement when the tested dependency
  surface is unchanged and synchronized cheap CI is green;
- once a standalone risk is retired, prefer integration with the next major system.

For SF-IMP-0083 specifically, retain cheap deterministic coverage for all 20 remaining built-in
specimens but target roughly seven new full-runtime representatives selected from support/profile risk
evidence rather than requiring 20 repeated lifecycle/reopen proofs.

### HS-R09 — validation runtime tiers

**Resolved direction:** use operational budgets as escalation triggers, not arbitrary correctness
cutoffs:

- routine recurring CI should normally target about 15 minutes per job;
- recurring jobs above about 20 minutes require justification, optimization, splitting, or promotion
  to deliberate characterization;
- deliberate milestone characterization should normally target about 30 minutes or less where
  technically practical;
- longer soak/large-matrix work is manual/scheduled and must name the uncertainty it retires.

Heavy acceptance work is not rerun solely because unrelated documentation/state moved on main.


### HS-R10 — morphology capacity, control, selection, and realization are separate

**Resolved direction:** production tuning must not shrink Skyforge's neutral terrain capacity merely because some valid terrain is poor for ordinary traversal or for a particular gameplay role.

The project separates four questions:

1. **Capacity:** can the backend-neutral morphology system express the terrain character at all?
2. **Control:** does Authorship know how to request that character deliberately through stable semantic controls / recipes?
3. **Selection:** does Content choose that recipe for an appropriate world/gameplay role and production distribution?
4. **Realization:** does Implementation reproduce the requested neutral terrain faithfully in Minecraft and expose its concrete block-space consequences?

Ownership:

- **Authorship** owns expressive morphology capacity, semantic control axes, recipe/control-response evidence, deterministic examples, and the Morphology Control Atlas. Authorship does not classify terrain as good/bad gameplay or encode Minecraft traversal rules.
- **Content / Experience** owns terrain-role requirements, suitability, production recipe selection/distribution, and progression/world-role policy.
- **Implementation** owns backend fidelity, discretization, block-space traversal consequences, lifecycle, persistence, and backend-introduced artifacts.
- **Human review** owns aesthetic/gameplay-quality judgment when technical ownership is not itself defective.

Fault triage:

```text
neutral shape/control response wrong or not deliberately reproducible -> Authorship
neutral shape correct, but selected for the wrong gameplay/world role  -> Content
neutral shape correct, backend realization distorts it                 -> Implementation
technical stack correct, desired look/feel/distribution still disputed -> Human gate
```

A lumpy, awkward, or hostile specimen may remain valid generator capacity even when it is rejected as a starting-area or ordinary-traversal recipe. Prefer changing production recipe selection or a specific semantic recipe over deleting expressive capability.

Canonical coordination contract:
`docs/agent-state/CROSS_LANE_CONTRACTS.md#Morphology-capacity-control-selection-and-realization`

Authorship control artifact:
`docs/authorship/MORPHOLOGY_CONTROL_ATLAS.md`

---

# OPEN HUMAN STRATEGY TOPICS

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

## Workflow improvements to apply program-wide

### 1. Predeclare the risk retired by each milestone

Every milestone should state a small set of concrete uncertainties it exists to retire. Acceptance
work added later must either map to one of those uncertainties or identify a newly discovered blocker.

### 2. Separate routine CI from heavy characterization

Routine pull-request CI should remain fast enough to encourage frequent integration. Large Minecraft
atlases, soak tests, visual corpora, and multi-client matrices should be deliberate milestone or manual
workflows rather than automatic consequences of every branch update.

### 3. Prefer representative runtime coverage over Cartesian-product testing

If many specimens share one backend path, prove parameter-space breadth cheaply and lifecycle
correctness on representative boundary/typical cases. Expand only after a discrepant specimen or a
genuinely different path appears.

### 4. Heavy tests require a current integration candidate

Do not intentionally launch expensive characterization from a materially stale/non-mergeable branch
unless the purpose of the run is specifically diagnostic and the result is explicitly non-acceptance
evidence.

### 5. Establish acceptance boundaries before branch accumulation becomes the work

When a producer has accumulated substantial useful work but main has moved materially, checkpoint,
recompose, and close a bounded milestone rather than allowing synchronization churn to dominate the
session.

### 6. Optimize for integration risk, not subsystem completeness

After a subsystem has credible correctness, prefer integrating it with the next major world/game
system. Bugs discovered at morphology × geology × hydrology × structures × ecology boundaries are
more valuable to find than polishing isolated morphology indefinitely.

### 7. Maintain a small WIP surface

Each lane should normally have one active acceptance milestone plus explicitly dormant/reserved work.
Old PRs should be merged, closed, or clearly superseded instead of remaining ambiguous parallel
authorities.

### 8. Human reviews should answer product questions, not certify machine invariants

Human time should be spent on silhouette, traversal, gameplay feel, progression, music, readability,
and design choices. Deterministic identity/persistence invariants belong to automation.

## Automated roadmap policy

Audit should treat the ordering approximately as:

```text
NOW
    apply HS-R08 / HS-R09 to SF-IMP-0083 and other active lanes
    finish the smallest clean morphology-carrier acceptance boundary

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
