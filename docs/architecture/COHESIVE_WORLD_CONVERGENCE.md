# Cohesive World / Production Stack Convergence

**Status:** OWNER-APPROVED ROADMAP REVISION / DEFERRED SOURCE MERGE  
**Decision:** issue #1063  
**Activation boundary:** after the active DR-70 repair and human re-review are accepted; before Bootstrap Province becomes PRIMARY_ACTIVE  
**Current-work fence:** this document is staged on an isolated roadmap branch. It does not alter PR #1062, current DR-70 acceptance, or live Platform-v2 authority.

## 1. Purpose

Phase 2 proves that Skyforge can produce a coherent dressed environment from shared authored world meaning. Bootstrap is primarily a gameplay-convergence phase: onboarding, progression, recovery, first-flight pacing, civilization arrival, and the first systemic freight/economy loop.

A missing integration boundary exists between those concerns.

Phase 2.5 therefore asks:

> Can the intended **alpha production stack** generate and sustain a recognizably Skyforge world across the spatial scales the game actually depends on, before gameplay balancing is asked to compensate for substrate uncertainty?

The companion pre-alpha scope policy is `docs/architecture/FIRST_PUBLIC_ALPHA_SCOPE.md`. Phase 2.5 must establish a complete production substrate for that alpha without dragging post-alpha bespoke-content breadth onto the critical path.

The phase is intentionally narrower than "finish the game before Bootstrap." It exists to retire production-stack, scale, compatibility, persistence, and performance unknowns that would otherwise contaminate Bootstrap playtesting.

## 2. Governing distinction

```text
Phase 2 — First Environments / Integrated Physical World
    Can the world systems compose into a convincing place?

Phase 2.5 — Cohesive World / Production Stack Convergence
    Can the actual intended game stack sustain a convincing Skyforge world?

Phase 3 — Bootstrap Province
    Is that world a good game for a new player?
```

Do not move gameplay balancing problems backward into Phase 2.5 unless they expose a substrate defect.

## 3. Production-baseline dependency roster

Phase 2.5 establishes an **alpha production-baseline roster**, not a permanently frozen final mod list.

The roster is an executable shipping commitment: every dependency selected as `CORE` for the alpha must be intentionally supported, and every capability marked `INTEGRATED` must have its intended user-facing path exercised in the combined production stack. Reference-only, test-only, `OPTIONAL`, `REJECTED`, and `DEFERRED` dependencies do not create that obligation.

The broader development/reference pack is not automatically the alpha shipping manifest. By Phase-2.5 exit, every dependency actually intended to ship must be classified, and no retained jar may remain in the public alpha merely as an accidental development leftover.

Every retained capability should be classified as one of:

| Classification | Meaning |
| --- | --- |
| CORE | Expected production dependency unless strong evidence overturns it |
| INTEGRATED | Adapter/compatibility work is complete enough for production use |
| CANDIDATE | Worth bounded evaluation against a concrete Skyforge consumer |
| OPTIONAL | Enhancement; the production world does not depend on it |
| REJECTED | Evaluated and deliberately excluded |
| DEFERRED | Potentially useful later; not required for Bootstrap |

Reference packs and contemporary ecosystem evidence may inform candidate discovery and compatibility expectations, but Skyforge adopts individual capabilities deliberately rather than inheriting another pack wholesale.

Representative reconnaissance inputs may include active Create/Aeronautics packs such as Create: Plane and Simple, Create Aeronautics: Skybound, All of Create Aeronautics, Create Aeronautica, and focused performance/compatibility packs. Their manifests/configurations are evidence, not authority.

## 4. Adapter / authority convergence

For every production-baseline dependency, preserve the rule:

> Skyforge owns meaning. Mods provide capabilities. Adapters connect them.

Representative boundaries:

- Create supplies machinery; Skyforge owns world/economic purpose.
- Aeronautics/Sable supplies assembled-vehicle physics; Skyforge owns progression, route, freight, and world semantics.
- Distant Horizons supplies far-terrain rendering; Skyforge owns geography.
- ecology mods supply entity implementations; Skyforge owns habitat/population opportunity and selection.
- CC:Tweaked supplies programmable computation; Skyforge exposes instrumentation/network capabilities without yielding game-state authority.
- camera/schematic/utility addons may supply implementation capabilities without becoming semantic authorities.

Complete the adapters needed by the alpha production baseline and its Bootstrap consumers. Do not carry a half-integrated dependency into alpha merely because the jar is present in the development pack.

## 5. Spatial certification ladder

### 5.1 Island scale

Prove one representative place coherently composes the production-baseline systems relevant at local scale:

```text
terrain
+ geology/resources
+ authored hydrology
+ caves/interiors
+ ecology
+ structures
+ minimum atmosphere
+ selected retained-mod capabilities
+ lifecycle/persistence
```

Phase-2 accepted evidence should be reused rather than repeated unless the production stack materially changes the tested surface.

### 5.2 Cluster scale

Prove several islands behave coherently together.

Representative risks:

- island isolation and stacked/neighbor volume integrity;
- altitude/relief variation;
- gliding and early-flight route geometry;
- atmosphere continuity and lift/weather fields;
- ecology/resource differentiation;
- navigation/readability;
- cross-island loading/streaming;
- Distant Horizons behavior;
- aircraft/sublevel operation across the cluster;
- persistence/reload across multiple active places.

This is not first-flight gameplay balancing. It proves the world can support that later balancing.

### 5.3 Province scale

Prove several clusters can support the minimum regional world semantics Bootstrap will consume:

- differentiated resource/service roles;
- sparse civilization/infrastructure;
- at least one inhabited/service location sufficient to prove civilization, producer/consumer, and freight roles without requiring full Guild content;
- producer/consumer asymmetry;
- freight-capable route geometry and landing/service opportunity;
- regional ecology and atmosphere variation;
- navigation/world clues;
- far/near materialization where relevant;
- persistent regional state.

Do not tune exact first-hours pacing, prices, recipe costs, reward rates, or tutorial difficulty here.

### 5.4 Coarse-world scale

Prove that Skyforge remains coherent beyond one curated province without requiring the infinite world to remain physically instantiated.

Representative risks:

- province/region distribution;
- long-range geography and negative-space cadence;
- climate/atmosphere variation;
- civilization sparsity;
- ecological/resource distribution;
- far-field semantic state and materialization;
- long-distance persistence/reconstruction;
- world-generation/exploration latency;
- server/client memory and performance behavior.

The question is not "is the whole world finished?" It is:

> If the player keeps travelling, does the architecture continue producing Skyforge rather than degrading into disconnected local demonstrations?

## 6. Compatibility and performance baseline

Before Phase 2.5 exits, run bounded combined-stack evidence for the production baseline where a hard incompatibility could invalidate Bootstrap work.

At minimum this includes the relevant combinations of:

- Minecraft 1.21.1 / NeoForge;
- Skyforge production world generation;
- Create;
- Sable / Create Aeronautics;
- Distant Horizons;
- selected ecology/content dependencies;
- selected utility/compatibility addons;
- the intended reference rendering path where sufficiently stable;
- single-player and representative dedicated-server behavior where the Bootstrap substrate depends on it.

Prefer representative risk-equivalence evidence, not a Cartesian product of every mod and world condition.

## 7. Explicit non-goals

Phase 2.5 does not require:

- a permanently final mod list;
- every planned Create/Aeronautics addon;
- mature petroleum industry;
- advanced fleet automation;
- final radar/network infrastructure;
- mature CC:Tweaked progression;
- dragons, whaling, warfare, or every adventure system;
- the full Guild institution, bespoke Guild Hall breadth, Bellanca bureaucracy/restitution narrative, or mature faction content;
- final civilization breadth;
- final weather/cloud/shader presentation;
- final soundtrack/adaptive audio;
- every structure archetype;
- final economy balance;
- final tutorial/quest design;
- final recipe/progression tuning;
- final public-alpha packaging.

A capability without a Phase-2.5 or Bootstrap consumer remains later work.

## 8. Exit criteria

Phase 2.5 exits when all of the following are true:

- an alpha production-baseline dependency roster exists with explicit CORE / INTEGRATED / CANDIDATE / OPTIONAL / REJECTED / DEFERRED dispositions;
- every alpha-critical CANDIDATE has been resolved;
- every CORE dependency is intentionally supported and every INTEGRATED capability has been exercised through its intended user-facing combined-stack path;
- the proposed alpha shipping manifest contains no unclassified dependency or accidental development/reference-only inclusion;
- required semantic ownership/adapters for the alpha baseline are explicit and sufficient;
- representative island-scale composition is accepted;
- representative cluster-scale composition is accepted;
- representative province-scale substrate exists for civilization/resource/freight consumers without requiring gameplay tuning;
- coarse-world distribution/materialization/persistence has representative evidence;
- the selected stack has no known architecture-invalidating compatibility problem;
- performance is bounded enough that Bootstrap playtesting measures gameplay rather than obvious substrate failure;
- unresolved remaining questions are predominantly gameplay/content/presentation questions rather than world-substrate questions.

Canonical exit principle:

> The minimum intended production stack can generate and sustain a representative Skyforge world across the island -> cluster -> province -> coarse-world hierarchy without major unresolved architectural, compatibility, persistence, or performance unknowns.

## 9. Bootstrap handoff

Bootstrap consumes this substrate and becomes the first complete player-experience convergence phase.

Its primary concerns remain:

- onboarding and instructional design;
- survival-to-Create learning;
- glider timing and utility;
- first-aircraft closure and pacing;
- engineering experimentation budget;
- failure/recovery;
- first meaningful powered journey;
- arrival at an inhabited/service location sufficient to demonstrate civilization and regional logistics;
- first real freight/economy loop;
- recipes, rewards, prices, timing, guidance, and playtesting.

If Bootstrap exposes a substrate defect, repair the smallest owning Phase-2/2.5 layer and return immediately to gameplay convergence.

## 10. Transition safety

Issue #1063 records this owner decision durably.

The source-bound roadmap/progression files must be merged only as one reconciled transition because `PROGRAM_PROGRESSION.json` fingerprints `PROGRAM_ROADMAP.md`. Until the active DR-70 boundary is complete, current main and PR #1062 retain authority.

This phase must not be used to reopen already-retired DR-70 machine evidence, broaden current hydrology work, or delay the active DR-70 human gate.
