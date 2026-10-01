# CIV-0042 — Civilization Implementation and Acceptance Contract

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Purpose

This document closes the current civilization/Guild conceptual sweep. It does not define another gameplay feature. It translates the accepted CIV precommit corpus into cross-lane implementation, persistence, correctness, performance, evidence, and human-acceptance obligations.

After this boundary, new work should normally decompose the accepted civilization model into bounded Content / Experience and Implementation milestones rather than extending the precommit with additional conceptual documents unless executable work exposes a genuinely missing product contract.

## Program ownership

Skyforge's existing lane ownership remains authoritative.

### Authorship

Authorship owns backend-neutral world causes and evidence, including:

- province / region / island semantics;
- morphology, geology, hydrology, ecology, and environmental fields;
- exact authored associations, provenance, and deterministic world evidence;
- neutral physical/site evidence already accepted for downstream civilization role selection.

Authorship does not own named civilization roles, Guild policy, market behavior, Minecraft structures, or concrete runtime persistence merely because those systems consume authored evidence.

### Content / Experience

Content / Experience owns civilization and Guild gameplay meaning, including:

- province civilization context and settlement/cluster planning policy;
- island functional-role requirements and candidate selection;
- progression and Bootstrap Province integration;
- Guild institutional behavior and player-facing services;
- economy, markets, contracts, route meaning, freight, piracy, allegiance/access policy, and economic tuning;
- mod capability selection and normalization;
- FTB Quest onboarding presentation;
- executable gameplay acceptance criteria.

Content must consume accepted Authorship evidence rather than creating competing geology, terrain, ecology, or physical-site truth.

### Implementation

Implementation owns concrete Minecraft / NeoForge realization and runtime correctness, including:

- authoritative runtime state containers and serialization;
- lifecycle, activation, dormancy, reconciliation, scheduling, save/reload, and synchronization;
- physical cargo and semantic/physical custody transitions;
- concrete vessel/structure/facility bindings;
- functional-anchor realization and invalidation;
- exact block/entity/contraption interaction;
- Create / Aeronautics / Automated Logistics / CC integration where retained;
- performance and bounded runtime behavior;
- persistence/version migration;
- implementation acceptance fixtures and diagnostics.

Implementation must not invent competing civilization meaning merely to simplify the backend.

## Semantic-to-runtime flow

The intended ownership chain is:

```text
AUTHORSHIP
    terrain / geology / ecology / site evidence
            ↓
CONTENT / CIVILIZATION SEMANTICS
    province civilization context
    cluster plan
    island roles
    specialization
    Guild relationships
    route/economic policy
    demand / markets / contracts
    onboarding meaning
            ↓
IMPLEMENTATION
    persistent runtime state
    reconciliation scheduler
    Minecraft adapters
    physical cargo / vessel binding
    functional-anchor lifecycle
    retained-mod integration
            ↓
MINECRAFT + RETAINED MODS
    blocks / inventories / contraptions
    NPCs / aircraft / cargo
    terminals / physical infrastructure
```

Minecraft ontology must remain out of backend-neutral modules unless a genuinely neutral abstraction is justified through the normal program process.

## Minimum authoritative runtime concepts

This precommit does not freeze Java class names, package structure, database/schema layout, or serialization technology. The eventual runtime must nevertheless be able to represent equivalent authority for the following concepts where the corresponding feature exists.

### Civilization identity and state

```text
ProvinceCivilizationContext
SettlementCluster
    stable identity
    latent plan
    activation/dormancy state
    population/settlement scale
    economic condition
    control/allegiance context
    Guild relationship
```

### Activated economics

```text
EconomicState
    catalogue / commodity state
    current stock
    desired-stock / demand pressure
    production / consumption
    prices / quote state
    supplier relationships
    temporary modifiers
    last authoritative reconciliation time

RouteEconomicState
    institutional recognition
    throughput/capacity
    reliability
    security/disruption
    relevant commercial state
```

### Institutional/economic actors

```text
EconomicPrincipal
    player / player organization / other institutional principal
    ownership and authorization scope

GuildAccount
Standing
Credit
```

Standing, Scrip, and Credit remain separate concepts.

### Registered facilities and vessels

```text
CommercialFacility
    owner/principal
    registered capabilities
    economic interfaces
    commercial custody
    route/comms relationship

VesselRecord
    stable vessel identity
    owner/title
    current controller where relevant
    provenance/allegiance
    Guild registration
    certification/configuration state
```

### Transactions and world consequences

```text
ShipmentRecord
ContractRecord
IncidentRecord
Claim / restitution state where implemented
CapabilityBinding
ConsequentialDelta / compact latent-world effect
```

Exact runtime decomposition may combine or split these concepts when invariants remain explicit and testable.

## Stable identity and provenance

Every persistent semantic object that can outlive one Minecraft realization requires stable authoritative identity.

Examples include:

```text
settlement_id
facility_id
vessel_id
shipment_id
contract_id
incident_id
economic_principal_id
```

Persistent identity must not depend on transient entity IDs, chunk load order, client presence, or nondeterministic discovery order.

Ownership, provenance, allegiance, registration, jurisdiction, possession/control, and legal/access state remain distinct where gameplay depends on the distinction.

## Single-authority conservation rule

Economically consequential state must have one authoritative representation at a time.

### Goods and cargo

For a quantity transferred from abstract settlement/commercial stock into player-observable physical custody:

```text
SEMANTIC STOCK
      XOR
PHYSICAL CARGO
```

A correct pickup transition is conceptually:

```text
reserve/authorize quantity
→ decrement or escrow source semantic authority
→ materialize exact physical cargo
→ establish physical custody
```

A correct delivery transition is conceptually:

```text
validate physical cargo / manifest / destination
→ consume/remove physical cargo authority
→ increment destination semantic stock
→ settle payment/obligation exactly once
```

The same authoritative goods must never simultaneously exist as saleable semantic inventory and recoverable physical cargo.

### Commercial custody

Ordinary player/private storage remains physical and economically invisible until deliberately committed through a registered commercial interface.

Remote sale, reservation, or automation may only act on goods already under authoritative commercial custody. Market access never teleports goods.

### Shipments

One authoritative semantic shipment may have at most one authoritative physical realization at a time.

Realization, unload/reload, incident materialization, or AAL handoff must not duplicate shipment contents or create a second settlement of the same delivery.

### Money and obligations

Scrip payments, refunds, insurance/restitution settlements, contract payouts, loan disbursements, and repayment transitions must be idempotent.

```text
one authoritative obligation
→ one authoritative settlement
```

Save/reload, reconnect, chunk lifecycle, repeated callbacks, or UI retries must not duplicate financial settlement.

## Semantic / physical capability reconciliation

Intended settlement roles and current operational capability remain separate.

A realized service may bind to a small number of explicit functional anchors or registered contributors rather than requiring continuous block-volume scans.

```text
INTENDED ROLE
BEACON_NAVIGATION

CURRENT CAPABILITY
NAVIGATION = OPERATIONAL / DEGRADED / OFFLINE
```

Once physically relevant infrastructure is realized and tracked, player modification must have real consequences. The semantic layer may not silently preserve a service whose required realized anchor has been destroyed.

Ordinary decorative/structural blocks do not need semantic ownership or continuous validation unless they actually participate in a gameplay-relevant capability contract.

## Latent, activated, dormant, and physical state

Player discovery, civilization existence, detailed economic activation, and Minecraft realization are distinct.

Preferred conceptual hierarchy:

```text
PROCEDURAL/AUTHORED WORLD EVIDENCE
        ↓
LATENT CIVILIZATION PLAN
        ↓ as required
DETAILED ECONOMIC STATE
        ↕ hot / dormant
        ↓ as observed/relevant
MINECRAFT PHYSICAL REALIZATION
```

### Latent state

Latent civilization carries structural causality and broad needs/capacities without requiring detailed inventories, prices, shipments, or actors.

### Detailed economic state

Detailed state may be activated because a player, Guild information service, contract, shipment, neighboring active market, incident, or authored event requires concrete accounting.

### Dormancy

Detailed civilization may become computationally cold without becoming historically blank.

Previously consequential stock, ownership, contract, shipment, infrastructure, route, and player-caused state must not be regenerated from a pristine procedural warm start merely because the player left the area.

### Physical realization

Physical Minecraft realization exists only where observation/interactivity or an explicit retained-mod execution path requires it. Unloaded civilization must not require loaded chunks or continuously simulated NPC/aircraft physics.

## Persistence and compaction

Persist state when regeneration would erase consequential history or violate external references.

Likely persistent categories include:

- activated/dormant economic snapshots after consequential interaction;
- player commercial facilities and explicit commercial custody;
- registered vessels and title/certification changes;
- contracts, loans, claims, and unresolved obligations;
- shipment and incident records whose identity/custody matters;
- player-caused market effects not yet reconciled;
- altered route/security/recognition state where history matters;
- damaged/restored civic capabilities;
- ownership/allegiance/legal-state changes;
- Bellanca/tutorial state and restitution outcome.

Pure deterministic latent descriptors may remain reconstructible where implementation/versioning permits.

Persist the current consequence of ordinary economic history rather than every historical tick or transaction. Retain explicit historical records only where the record itself has gameplay/institutional value.

Persistence schemas must be versioned. Accepted saves must not be silently reinterpreted under incompatible future schemas.

## Time and reconciliation

Structural civilization changes are rare/event-driven. Activated economics use coarse periodic or lazy reconciliation. Operational gameplay transactions are immediate/event-driven.

Stable intervals should be integrated analytically or in bounded coarse steps where practical. Material discontinuities divide the interval:

```text
T0 ───────── X ───────── T1
             ↑
        route outage

reconcile T0→X under old conditions
reconcile X→T1 under new conditions
```

Do not replay every implied production cycle or shipment since the last player visit.

## Regional trade and route use

Detailed commerce resolves bounded procurement needs against exportable supply over the existing sparse route network.

The runtime must not require all-pairs settlement search or a continuously optimized world economy.

Supplier selection may consume:

- established relationships;
- bounded route-reachable candidates;
- delivered cost;
- stock/capacity;
- reliability/hazard;
- route throughput/congestion;
- institutional access;
- information freshness;
- eligible player commercial supply.

Unmet demand is a valid outcome.

NPC route capacity is aggregate semantic throughput unless custody/player interaction/incident/contract/physical realization requires a concrete shipment or craft.

## Communications and programmable access

Guild UI and programmable/computer access consume the same permission-filtered information layer.

Queries must not:

- recompute the economy merely because a terminal is polled;
- expose raw hidden world-generation fields;
- expose future RNG or unrevealed incident causes;
- bypass discovery/permission/freshness constraints;
- create stock, money, contracts, or shipment authority without normal transactional checks.

Programmable actions may automate observation and ordinary commitment but may not skip physical execution or institutional constraints.

## Faction, sovereignty, ownership, and allegiance

Territorial control, local sovereignty, broad allegiance, Guild relationship, economic ownership, physical possession/control, and actor access are independent dimensions where needed.

Civilian, Guild-recognized, player, and illager/faction locales and vessels must expose enough allegiance/provenance information for:

- patrol/hostility decisions;
- route and access policy;
- contract/legal interpretation;
- ownership/title disputes;
- piracy/capture/salvage consequences;
- settlement/facility service eligibility.

Do not replace these scoped relationships with one universal reputation or wanted scalar.

## Bellanca / Bootstrap acceptance chain

The canonical onboarding proof should eventually demonstrate an end-to-end chain equivalent to:

```text
Bellanca crash
→ onboarding state survives save/reload
→ FTB Quests guide but do not own authoritative claim/economic state
→ player regains practical mobility through foundational engineering
→ player reaches an eligible Guild/civilization destination
→ Bellanca claim exists authoritatively
→ recorder evidence changes the claim outcome
→ Guild liability/restitution becomes valid
→ chosen restitution settles exactly once
→ tutorial-complete state persists
→ player enters systemic sandbox
```

A first post-tutorial economic proof should then demonstrate:

```text
real settlement demand exists
→ market/contract exposes an opportunity
→ source stock enters commercial/physical custody once
→ player transports real cargo
→ destination validates delivery
→ destination semantic stock changes
→ Scrip/contract settlement occurs exactly once
→ save/reload preserves the settled result
```

The first executable slice need not implement every mature Guild/economy feature to prove these contracts.

## Computational prohibitions

The civilization runtime must not require, as its ordinary architecture:

- per-tick global settlement simulation;
- continuously loaded/generated civilization chunks;
- all-pairs settlement connectivity or supplier search;
- global commodity optimization;
- individual household/citizen economic agents;
- continuously simulated off-screen aircraft fleets;
- detailed off-screen crew life simulation;
- arbitrary storage/container scans to discover player commerce;
- full historical shipment/transaction replay;
- duplicate Authorship geometry/route evidence merely for civilization convenience.

Prefer:

```text
sparse route graph
+ bounded candidate searches
+ coarse/lazy reconciliation
+ compact consequential state
+ explicit registered interfaces
+ cached information products
+ event-driven physical realization
+ small hot working set
```

## Validation contract

Civilization work follows the canonical Skyforge Validation and Evidence Economy Policy.

### Tier 0 — cheap deterministic evidence

Cheap invariants should be broad/exhaustive where practical, including:

- deterministic/stable IDs;
- schema/version validity;
- stock conservation;
- semantic↔physical authority exclusivity;
- transaction idempotence;
- lazy-reconciliation equivalence on representative analytical cases;
- route/supplier candidate bounds;
- ownership/allegiance/access matrices;
- standing/authorization separation;
- permission enforcement;
- deterministic warm-start behavior;
- no discovery-order dependence where not explicitly authored.

### Tier 1 — routine integration evidence

Routine automated integration should cover bounded cases such as:

- save/reload of settlement/economic state;
- activation → dormancy → reactivation;
- physical cargo pickup/delivery;
- duplicate callback/retry safety;
- contract settlement;
- capability-anchor invalidation/restoration;
- player commercial deposit/withdrawal;
- registered vessel ownership/configuration persistence;
- cross-lane regressions affected by the implementation.

### Tier 2 — representative expensive characterization

Use risk-equivalence representatives for expensive real-runtime behavior, including where relevant:

- actual Minecraft aircraft freight;
- Create cargo loading/unloading;
- retained Automated Logistics unload/re-entry behavior;
- physical incident realization;
- actual-client reopen;
- dedicated-server lifecycle;
- representative multiplayer ownership/authorization;
- realistic exploration-scale performance.

Do not construct a Cartesian product merely because many commodities, settlements, routes, or aircraft families exist. If a representative exposes a parameter-dependent failure, widen the affected risk class until the domain is understood.

### Tier 3 — human/play acceptance

Human review remains authoritative for qualitative questions such as:

- whether waking beside the Bellanca establishes the intended premise;
- whether onboarding is directed without feeling mechanically railroaded;
- whether foundational engineering progression is understandable;
- whether reaching civilization feels consequential;
- whether Guild services and the claim are comprehensible;
- whether the liability reversal and restitution choices feel credible/fair;
- whether the first post-tutorial freight/trade opportunity reads as player freedom rather than Tutorial Part II;
- whether civilization feels inhabited, functional, and coherent without destroying Skyforge's negative space;
- whether markets/logistics are legible and interesting in actual play.

Machines should prefilter correctness defects but must not replace these judgments with proxy metrics.

## Implementation diagnostics

Runtime implementation should make failures attributable rather than opaque. Exact diagnostics are an Implementation decision, but debugging should be able to distinguish relevant domains such as:

```text
latent planning
warm start / activation
reconciliation
market stock / demand
route flow
commercial custody
shipment realization
contract/payment settlement
capability binding
ownership/allegiance/access
Guild information freshness
persistence/migration
```

Performance telemetry should make the number of hot/dormant/latent clusters and reconciliation work visible enough to prove that exploration does not create an unbounded active-simulation cost.

## Merge / milestone discipline

After this precommit is accepted, producer work should follow the repository-first disposable-agent process:

1. reconstruct from current `main`, lane state, cross-lane contracts, active issue/PR, and relevant CIV records;
2. select the smallest bounded integration risk;
3. preserve lane ownership rather than implementing across boundaries by convenience;
4. prove cheap invariants broadly;
5. characterize expensive runtime behavior only where it retires a named uncertainty;
6. surface human/play gates explicitly;
7. update lane state/cross-lane contracts when the accepted boundary materially changes them;
8. merge the smallest coherent milestone before expanding scope.

## Design invariants

> Civilization semantics remain backend-neutral and downstream of accepted Authorship evidence; Content owns gameplay meaning and policy, while Implementation owns concrete Minecraft realization, persistence, lifecycle, and performance.

> Every economically consequential good, payment, shipment, vessel, facility, obligation, and realized capability has one authoritative state at a time. Semantic/physical transitions must be deterministic, idempotent, and save/reload safe.

> Unobserved civilization advances through bounded semantic reconciliation and never requires world-scale Minecraft simulation, arbitrary chunk loading, or global optimization.

> Persistent state records consequential history rather than exhaustive history; reconstructible procedural state should remain reconstructible.

> Existing mods remain capability/asset libraries under Skyforge semantic authority. Retained mod automation may execute Skyforge decisions but does not become the source of civilization meaning.

> Acceptance exhaustively proves cheap invariants and uses representative expensive lifecycle evidence, widening only when a failure demonstrates a broader risk class.

> Human review remains authoritative for onboarding, legibility, pacing, civilization feel, and the Bellanca-to-sandbox transition.

## Closeout

With CIV-0042 accepted, the current civilization conceptual sweep is complete.

The default next action is decomposition into bounded Content / Experience and Implementation milestones against the existing Bootstrap Province, retained-mod integration, and runtime roadmaps. Further CIV precommits should be created only when executable work discovers a genuinely unresolved product contract that cannot be answered from the accepted corpus.