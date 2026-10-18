# First Public Alpha Scope and Bespoke-Content Policy

**Status:** OWNER-APPROVED ROADMAP POLICY / STAGED WITH #1064  
**Decision record:** issue #1063 comment 5799031828  
**Applies to:** all pre-alpha phases after current DR-70 acceptance  
**Current-work fence:** this policy is staged only on the isolated roadmap branch and does not modify active DR-70 PR #1062.

## 1. Governing principle

The first public alpha is **systems-complete for the intended core game and content-light by design**.

It must not ship with a fundamental Skyforge system simply absent because that system was expensive or bespoke. It also must not delay release merely to accumulate bespoke creatures, structures, factions, narrative, dimensions, encounters, or decorative breadth that can safely consume already-proven systems after alpha.

Canonical rule:

> Prove every fundamental system before alpha. Defer bespoke breadth unless it is the smallest practical content needed to prove one of those systems.

This is not a "prototype with missing pillars" policy. It is a scope-control policy for separating **system completeness** from **content completeness**.

## 2. Pre-alpha reuse-first test

Before authorizing new bespoke content, ask in order:

```text
Can retained Minecraft/mod content express the required role adequately?
        |
       yes -> adapt/reuse it
        |
        no
        v
Is this role necessary to prove a pre-alpha core system?
        |
       no -> defer to a post-alpha content phase
        |
       yes
        v
Build the smallest bespoke or semi-bespoke content that proves the role.
```

"Interesting", "iconic", "eventually desirable", or "fits the setting" is not sufficient pre-alpha authorization by itself.

## 3. Alpha production stack completeness

Phase 2.5 establishes the **alpha production baseline**.

Every retained dependency must receive one disposition:

- `CORE`
- `INTEGRATED`
- `CANDIDATE`
- `OPTIONAL`
- `REJECTED`
- `DEFERRED`

Before first public alpha:

- every `CANDIDATE` that could affect the alpha critical path must be resolved;
- every dependency selected as `CORE` for alpha must be intentionally supported;
- every capability claimed as `INTEGRATED` must have its intended user-facing path exercised in the combined production stack;
- Skyforge/mod authority boundaries must be explicit enough that two systems do not independently own the same semantic state;
- combined compatibility, persistence, performance, and packaging behavior must be demonstrated.

This does **not** require full user-facing integration of reference-only, test-only, `OPTIONAL`, `REJECTED`, or `DEFERRED` dependencies.

The rule is therefore:

> No half-integrated dependency in the alpha production baseline.

The broader development/reference pack is not automatically the shipping alpha stack. Before alpha, every dependency that will actually ship must have an explicit disposition, and no user-facing mod should remain present merely because it happened to be installed during development.

For each shipped dependency, every capability exposed to the player must be one of:

- intentionally integrated and supported;
- intentionally disabled/hidden because Skyforge does not claim that capability in alpha;
- explicitly documented as optional/non-core behavior with no dependency from Skyforge's critical path.

"Fully integrated" does not mean Skyforge must consume every feature offered by every retained mod. It means the alpha contains no accidental, unsupported, or semantically conflicting surface simply because the jar is present.

## 4. Fundamental systems that must exist before alpha

The exact implementation may evolve, but the alpha must contain a coherent implementation of the systems required to demonstrate Skyforge's advertised core premise.

### 4.1 World and island systems

Required:

- production floating-island geography;
- geology/material/resource realization sufficient for progression and regional differentiation;
- authored hydrology;
- caves/interiors;
- structures/site realization substrate;
- ecology/habitat opportunity;
- persistent deterministic lifecycle;
- island -> cluster -> province -> coarse-world composition;
- negative-space and spatial-distribution behavior that remains coherent under continued travel.

### 4.2 Sky / airspace systems

Required:

- a coherent open-sky/airspace model sufficient for flight between islands;
- minimum authored or derived airspace/environment differentiation where the game depends on it;
- authoritative wind/lift/sink/turbulence/pressure behavior through the selected atmosphere stack;
- compatibility between atmospheric truth, gliders, aircraft, and representative soaring fauna;
- navigation/readability sufficient for real travel;
- day/night and weather behavior sufficient to make flying through the production world representative;
- Distant Horizons / equivalent distant-world presentation if retained as a core alpha dependency.

The alpha does not require every eventual sky biome, rare anomaly, storm ecology, or specialized aerial creature family.

### 4.3 Core gameplay systems

Required:

- ordinary survival foothold;
- Create/mechanical entry;
- cheap personal gliding;
- practical first powered aircraft;
- geographic resource differentiation;
- a real reason to transport material between places;
- physical cargo/freight semantics;
- producer/consumer or equivalent supply-demand semantics;
- delivery validation and exactly-once economic consequence;
- deterministic recovery from critical-path failure;
- enough guidance/onboarding for an unfamiliar player to discover the loop.

### 4.4 Ecology and population substrate

Required:

- ecology/spawn authority driven by environmental opportunity rather than generic mob checklists;
- enough retained/adapted fauna to demonstrate terrestrial and aerial ecological behavior;
- at least one shared-atmosphere ecological consumer where required to prove the system;
- persistent/performant population behavior appropriate to the alpha world.

Not required pre-alpha:

- a large bespoke bestiary;
- every planned predator;
- dragons;
- complete Sky Whale migration/industry content;
- storm-dead or other anomalous creature families unless a later decision proves one is necessary to validate a fundamental system.

### 4.5 Civilization / inhabited-world substrate

Required:

- at least one inhabited or service location that proves civilization can exist coherently in the generated world;
- structure/site placement sufficient for settlements/infrastructure;
- service/producer/consumer roles needed by the core loop;
- physical freight endpoints and route opportunity.

Not required pre-alpha:

- the full Guild as a bespoke institution;
- bespoke Guild Hall architectural breadth;
- clerks, bureaucracy, regional Guild variants, or institutional narrative depth;
- Bellanca claim/liability/restitution closure;
- broad faction systems;
- complex reputation/crime policy;
- custom air-war AI.

A generic or lightly adapted inhabited/service location is acceptable if it proves the underlying systems honestly.

### 4.6 Technical product baseline

Required:

- save/reload and migration expectations appropriate to alpha;
- representative single-player and dedicated-server sanity where supported;
- bounded client/server memory and runtime behavior;
- compatibility among the selected production dependencies;
- a shipping manifest in which every included dependency is classified and every claimed user-facing capability has an intentional integration/support disposition;
- packaging, version-lock, and licensing/redistribution decisions sufficient to ship the alpha;
- enough presentation that external testing evaluates Skyforge rather than obvious scaffolding.

## 5. Explicit post-alpha content candidates

Unless promoted by a concrete pre-alpha system dependency, defer:

- full Guild institutional realization and regional variants;
- Bellanca bureaucracy/restitution narrative;
- full Lower Sea realization beyond lore or minimal required presentation;
- bespoke Nether reauthoring;
- bespoke End/high-sky progression;
- custom dimension-reaching sequences beyond retained baseline needs;
- complex hostile/civilian air traffic and custom combat AI;
- faction-war content;
- bespoke dragons;
- large aerial creature catalogs;
- full Sky Whale migration/industry content;
- storm-borne undead and other anomalous ecological families;
- broad archaeology/ruin/boss catalogs;
- exceptional-phenomena catalogs;
- mature civilization/narrative breadth;
- bespoke content whose main justification is novelty rather than a missing alpha capability.

These are not rejected ideas. They become candidate **post-alpha content waves** once player evidence can prioritize them.

## 6. Post-alpha phase rule

A post-alpha phase may:

- broaden content;
- add regional variants;
- deepen progression;
- expand the scale at which an already-present system operates;
- add optional/advanced capabilities;
- introduce bespoke content that consumes proven systems.

A post-alpha phase must **not** reveal that a system fundamental to the advertised first-alpha premise was simply absent from alpha.

If later work discovers such an omission, treat it as an alpha-scope defect rather than normal content expansion.

## 7. First public alpha acceptance question

A successful alpha should allow a player who knows nothing about development history to discover that:

- the world is made of coherent floating geography rather than isolated showcase islands;
- the sky between islands is meaningful travel space;
- atmosphere affects movement;
- personal mobility and logistics are different capability classes;
- engineering enables practical aircraft;
- geography creates resource/service differences;
- transporting physical things between places matters;
- ecology follows the environment;
- inhabited/service locations belong to the world;
- the selected retained mod stack behaves as one coherent game rather than a collection of loosely connected mods.

The alpha may still be narrow in creature count, structure variety, narrative, dimensions, factions, and bespoke spectacle.

## 8. Content-production transition after alpha

After first public alpha, bespoke content becomes an intentional production stream rather than an exception.

Candidate waves may include:

```text
Guild / civilization content
aerial ecology and migration
storms / anomalous airspace
adventure / archaeology / bosses
air traffic / conflict
mature industry and computing
high-sky / End
Nether integration
regional architecture
narrative and world history
full-release presentation
```

Prioritize those waves using alpha player evidence, production cost, and how strongly they deepen already-proven Skyforge systems.
