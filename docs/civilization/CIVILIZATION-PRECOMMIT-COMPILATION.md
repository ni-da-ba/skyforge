# Skyforge Civilization / Skyfarer's Guild Precommit Compilation

**Status:** CIVILIZATION CONCEPTUAL SWEEP COMPLETE / HANDOFF-READY  
**Branch at compilation:** `docs/civilization-precommit`  
**Terminal design contract:** `CIV-0042-civilization-implementation-and-acceptance-contract.md`  
**Bootstrap integration contract:** `CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md`

## Purpose

This is the durable synthesis and handoff entry point for the current Skyforge civilization / Skyfarer's Guild precommit corpus.

It exists so fresh Content / Experience, Implementation, Audit, and supporting agents can reconstruct the accepted product direction without replaying the design conversation or reading every CIV record before understanding the whole system.

This compilation is a navigation and synthesis document. The individual `CIV-*.md` records remain the detailed source for their specific decisions. Where an earlier broad statement is refined by a later explicit lock, the later specific lock governs that subject. Current `main`, accepted cross-lane contracts, executable tests, and merged history remain program authority once this branch is merged.

## Fresh-agent read order

Before acting on civilization work, a fresh producer should read:

1. `AGENTS.md`;
2. `docs/agent-state/PROGRAM_CHARTER.md`;
3. `docs/agent-state/VALIDATION_POLICY.md`;
4. its own lane state;
5. `docs/agent-state/CROSS_LANE_CONTRACTS.md`;
6. this compilation;
7. `CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md` for Bootstrap/onboarding work;
8. `CIV-0042-civilization-implementation-and-acceptance-contract.md` for runtime/integration work;
9. only the subsystem CIV records necessary for the bounded task;
10. current relevant source/tests/issues/PRs and merged history since the lane ledger boundary.

Do not treat conversational memory as authoritative.

## Precedence and refinement notes

The early precommit grew iteratively and contains parallel early record numbers (`CIV-0003`, `CIV-0004`, and `CIV-0005`). Filenames, not bare numbers, are canonical. Do not renumber these records merely for cosmetic sequence cleanup.

Important later refinements include:

- `CIV-0030` locks ordinary NPC resource production to persistent productive potential rather than universal deposit exhaustion;
- `CIV-0032`–`CIV-0035` refine the original broad unloaded-simulation concept into latent planning, warm start, lazy reconciliation, dormancy, and consequential persistence;
- `CIV-0036` defines event-driven semantic/physical capability reconciliation through explicit functional anchors rather than continuous structure scans;
- `CIV-0037` defines bounded regional trade-flow resolution rather than global optimization;
- `CIV-0038` keeps demographics structurally slow while allowing coarse economic condition/prosperity to change;
- `CIV-0039` expands Guild jurisdiction into explicit sovereignty/access/allegiance distinctions and introduces the minimum allegiance primitive for vessels, structures, and locales;
- `CIV-0040` distinguishes economic principal/ownership from possession, allegiance, registration, and personal professional Standing;
- `CIV-0041` is the current onboarding authority: the Bellanca crash frames the full tutorial and the resolved Guild transaction ends it;
- `CIV-0042` closes the conceptual sweep and defines cross-lane implementation/acceptance obligations.

## System in one diagram

```text
AUTHORED WORLD EVIDENCE
morphology / geology / ecology / site capability / regional context
        ↓
PROVINCE CIVILIZATION CONTEXT
population intensity / development / control / trade / history
        ↓
CLUSTER LATENT PLAN
settlement identity / island roles / broad needs & capacities / route intent
        ↓ as economic detail becomes necessary
ACTIVATED / DORMANT ECONOMIC STATE
production / consumption / desired stocks / catalogues / prices
supplier relations / Guild state / contracts / route throughput
        ↓ when custody or observation requires physicality
PHYSICAL MINECRAFT REALIZATION
Guild Hall / market / warehouse / cargo / aircraft / NPCs / infrastructure
        ↓ player/world interaction
CONSEQUENTIAL STATE
ownership / stock / route / incident / capability / claim / contract changes
        ↓
PERSIST + LAZY RECONCILE
```

Canonical simulation rule:

> **Simulate physically when observed or directly interactive; simulate semantically when unobserved.**

Companion persistence rule:

> **Detailed civilization can become computationally cold without becoming historically blank.**

## 1. Civilization architecture

Civilization is a regional condition and functional network rather than a structure-placement probability.

The accepted semantic hierarchy reuses the existing repository direction:

```text
Province civilization context
    ↓
Cluster civilization plan
    ↓
Island functional roles
    ↓
Minecraft realization
```

The cluster is the principal settlement-scale functional unit for medium and large settlements. A town may span several islands with residential, agricultural, mining, industrial, storage, airfield, dock, beacon, weather, defensive, or other roles.

Most islands remain wild or lightly used. Sparse negative space remains a core experience requirement.

## 2. The Skyfarer's Guild

The Guild is the principal institutional interface between the player and inter-settlement aviation/commercial civilization.

Its fundamental commodity is **trust**.

Its authority comes from overlapping:

- delegated public authority from participating settlements;
- contractual authority over participants using Guild systems;
- network power created by routes, accounts, insurance, certification, registry, stores, counterparties, information, and recovery services.

The Guild is not automatically sovereign. Ordinary criminal law, taxation, elections, policing, land ownership, domestic governance, and local political authority remain outside its jurisdiction unless specifically delegated.

The preferred origin is an early Interport Charter / Compact among pilot associations, merchants, brokers, shipwright societies, insurers, and mutual-aid organizations responding to sustained long-distance sky commerce.

## 3. Guild architecture and physical presence

Guild architecture follows a reusable **Guild Mercantile Functionalism** rather than one bespoke structure per settlement.

The Open Sky emblem is the invariant institutional mark. Regional materials and treatments may change while the recognizable geometry remains stable.

One settlement normally has one primary Guild destination scaled to local capability. Remote beacons, rescue assets, approaches, and moorings may exist separately where their function justifies them.

Core Hall services are a subset of:

```text
contracts
registry / certification
Guild account
basic trade
route / settlement information
```

Optional locations may add repair, warehousing, claims, aircraft sales, navigation, recovery, advanced certification, credit, academy, arbitration, or other specialized functions.

No systemic feature may assume a unique physical asset for every instance.

Donor/community builds are treated as whole-build, module, systems, or reference inputs subject to rights/provenance review and a Skyforge standardization pass.

## 4. Canonical onboarding: Bellanca crash to independent Skyfarer

Skyforge begins at the crashed **Bellanca B0-A**.

The crash, wreck, and unresolved insurance/registration problem frame the complete tutorial. FTB Quests may guide survival, Create basics, gliding, vertical traversal, first powered mobility, recorder recovery, and the journey toward civilization, but they do not own authoritative vessel, claim, account, evidence, or economic state.

Preferred onboarding arc:

```text
crashed Bellanca
→ survive / orient
→ basic engineering / Create
→ gliding / vertical traversal
→ improvise or build first practical powered mobility
→ reach civilization
→ encounter eligible Guild Hall
→ initial Bellanca claim
→ recorder / black-box evidence
→ Guild infrastructure fault established
→ liability reversal
→ choose restitution
→ TUTORIAL COMPLETE
→ player becomes an independent Skyfarer
```

The preferred restitution set remains:

- standardized Bellanca replacement;
- Scrip payout;
- retain wreck + partial payout.

The player may recover the recorder early without understanding its significance. The claim flow should remain recoverable if the recorder was not initially found.

The claim belongs conceptually to the Guild network, not necessarily one unique scripted NPC, provided an alternate reached Hall has the required capability.

Freight becomes an early post-tutorial professional opportunity rather than a mandatory final tutorial chore.

## 5. Money, trust, and finance

Three concepts remain orthogonal:

```text
SCRIP    = purchasing power / money
STANDING = professional and institutional trust
CREDIT   = debtor / financial reliability
```

Scrip is ledger/account money, not an inventory item. Credentials authenticate access but do not contain the balance.

Guild stores have local catalogues, bounded inventory, local prices, and distinct buy/sell terms. The Guild is not a universal infinite catalogue.

Institutional backing is balance-sheet/capital/logistics based rather than a fixed commodity redemption peg. No central-bank macro simulation is required.

Loans finance identifiable productive assets or mobilization rather than functioning as free money. Standing governs entrusted opportunity; Credit governs financing terms. Default/recovery behavior is staged rather than an instant one-missed-payment deletion.

## 6. Membership, delegated authority, and services

The Guild does not use a simple prestige rank ladder as the core progression model.

Useful distinctions are:

```text
public / non-member
member
authorized operator
scoped Guild agent / factor
```

Membership gives network participation. Standing and explicit authorizations unlock dangerous, sensitive, bonded, recovery, inspection, or agency work.

A sufficiently trusted player may eventually operate a Guild Agency at a qualifying player facility. The player remains an independent economic actor; delegated authority is scoped rather than general police power.

## 7. Vessels, certification, insurance, and evidence

Vessel concepts remain distinct:

```text
registration  = identity / title / ownership recognition
certification = recognized vessel configuration / fitness
operator authorization = what a person may legally/professionally do
```

Stock standardized aircraft can be pre-certified. Custom player aircraft remain valid and may be certified through functional inspection/proving requirements. Material propulsion/lift/control/mass/structure/cargo changes may require recertification; cosmetic/minor changes need not.

Black-box/transponder state may carry registration/configuration identity, route linkage, distress/incident information, selected telemetry, evidence, and recovery identity. It need not record every variable every tick.

Insurance covers real loss without making loss save-ending. Routine claims can be simple; exceptional claims such as the Bellanca opening may expose evidence and liability systems.

## 8. Contracts

Contracts are legal/economic relationships with the world, not isolated quest rolls.

A contract may carry:

```text
issuer / provenance
objective
payment
required authorization
operational hazard
Guild asset exposure
bond / collateral
insurance / liability
advance
completion conditions
```

Risk remains multidimensional rather than a simple Rank I–V ladder.

Useful overlapping classes include Routine, Bonded, Hazardous, and Discretionary/Sensitive.

Provenance includes Guild, Chapter, Private, Discretionary, and Illicit commissions.

Routine contracts should be downstream of real settlement/economic state. Authored/exceptional contracts may expose politics, disaster, danger, or unusual world events.

Construction/repair validation should focus on required state/function rather than one prescribed block-by-block sequence.

## 9. Physical logistics, commercial custody, and player industry

The central custody rule is:

> **Semantic inventory is authoritative while goods are abstract; physical inventory becomes authoritative while goods are in player-observable custody.**

Example transfer:

```text
semantic settlement stock
→ authorized pickup
→ source stock decremented / committed
→ physical cargo realized
→ transport
→ destination validation
→ physical cargo consumed from authority
→ destination semantic stock incremented
→ payment settles once
```

Ordinary player storage is economically invisible until deliberately committed through a registered commercial interface. The economy must never scan arbitrary chests to discover tradeable goods.

Player production enters the same supply chains used by civilization. There is no separate quest-only player market.

A large Create factory can become economically important if it has demand, logistics, capital, reliability, and institutional access. There is no hidden player market-share cap. Success may reshape regional economics without deleting geography because finite demand, route capacity, transport cost, risk, storage, and upstream inputs remain real constraints.

## 10. Markets, demand, production, and specialization

Local Guild markets distinguish:

```text
CATALOGUE = what the location normally handles
STOCK     = what it currently has
PRICE     = current transaction quote
```

Prices respond to local stock/demand, production/consumption, delivered import cost, risk, route conditions, and connected regional reference conditions. Large trades encounter market depth; one quoted scarcity price is not infinite quantity at that price.

Settlement demand derives from authoritative population/settlement state plus productive inputs, infrastructure requirements, prosperity, and temporary events. It is represented primarily through bounded desired-stock/consumption pressure rather than households.

Population strongly affects subsistence goods. Industry, fleet activity, infrastructure, and settlement role independently drive specialized goods.

Durable capital such as aircraft and major machinery is demanded through discrete loss/replacement/expansion/project conditions rather than fractional per-capita consumption.

Production chains are coarse economically meaningful transformations rather than mirrors of every Create recipe step. Settlement specialization emerges from resources, productive capability, infrastructure, connectivity, and history.

## 11. Resource potential and demographic limits

Ordinary NPC resource production does **not** universally deplete hidden finite deposits.

World geology/ecology establishes persistent productive potential. NPC civilization converts that into coarse productive capacity. Player mining still consumes finite physical Minecraft blocks.

Exceptional exhausted seams, abandoned mines, new discoveries, collapses, or other depletion/discovery stories may exist when explicitly authored/procedurally conditioned, but universal decrementing reserves are rejected.

Population remains a slow structural civilization property. Skyforge does not ordinarily simulate births, deaths, household migration, or famine arithmetic.

A coarse economic condition/prosperity state may change slowly in response to sustained supply adequacy, production, connectivity, infrastructure, and disruption. It changes demand composition and commercial behavior without automatically spawning/destroying buildings or changing population class.

## 12. Route topology, navigation, and regional flow

The Guild does not own the sky. It maintains a trusted network through it.

Routes are sparse semantic edges over existing authored geography. They may represent broad passages, beacon chains, landmark sequences, altitude bands, approaches, and other recognized movement relationships.

Route formation responds to trade complementarity, strategic value, network benefit, distance, hazard, infrastructure cost, reliability, capacity, backhaul, and inertia. New-route thresholds should exceed route-retention thresholds so networks do not churn every update.

Regional commerce resolves bounded procurement needs against exportable supply over the sparse route network. Supplier choice considers generalized delivered cost, reliability, capacity, incumbent relationships, route throughput, institutional access, and information freshness.

No global all-pairs commodity optimizer is required. Unmet demand is a legitimate state and creates prices, contracts, rerouting, infrastructure pressure, and player opportunity.

NPC route capacity is semantic throughput rather than exact off-screen aircraft count.

## 13. Communications, Guild UI, and programmable access

The Guild membership/account interface is the primary human-facing window into civilization state. A CC:Tweaked-style machine interface may consume the same curated information layer.

Information has provenance, latency, permissions, and freshness such as:

```text
LIVE
RECENT
STALE
UNKNOWN
```

The Guild information network is distinct from the freight network even when physical geography/infrastructure overlaps.

Computers may automate access to information the player is legitimately entitled to know; they do not create omniscience.

Programmable economic actions may observe and commit ordinary transactions/reservations/orders under the same constraints as manual UI. They may not teleport goods, bypass Scrip/Standing/Credit/certification/permissions, or skip physical execution.

## 14. Freight realization, incidents, piracy, and security

NPC commerce remains systemic while unobserved but does not require continuously simulated physics fleets.

Canonical rule:

> **One realized physical freighter corresponds to one authoritative semantic shipment and settles once.**

Freight incidents begin as semantic records with cause, location, severity, cargo outcome, evidence/survivor state, and deterministic scene seed. Physical wreck/combat/recovery scenes are created only when player observation/investigation makes them relevant. Player-altered realized incident state becomes persistent.

Piracy affects real shipments and therefore real shortages, route risk, traffic, insurance, and security policy.

Attribution is evidence-based rather than omniscient. Transponders, distress signals, witnesses, proximity, manifests, wreck evidence, and other facts may establish responsibility.

Guild response is primarily institutional/commercial: rerouting, convoying, escort contracts, restrictions, warnings, suspension, local-authority cooperation, and limited security where justified. Civil criminal law remains primarily sovereign/local.

## 15. Latent civilization, warm start, cadence, and dormancy

Unobserved civilization has a plausible semantic past rather than a fully replayed past.

A cluster may remain a cheap latent plan until detailed accounting is required. Initial detailed state is deterministically warm-started from stable world/civilization semantics plus current regional conditions.

Warm start may establish plausible catalogue, stock, price, supplier, and route maturity with bounded variation. It must not fabricate detailed historical incidents merely to explain aggregate state.

Once detailed state becomes consequential, it persists. Leaving the area does not regenerate a pristine economy.

Simulation cadences are:

```text
structural civilization: rare / event-driven
activated economics:     coarse periodic + lazy catch-up
operational gameplay:    immediate / event-driven
```

Stable intervals are integrated analytically/coarsely where possible; meaningful discontinuities divide the interval.

Dormant detailed state consumes negligible active simulation while preserving consequential history.

## 16. Economic shocks and adaptation

Short disruptions change operations before they change structure.

Preferred response ladder:

```text
absorb
→ price
→ procure
→ reroute
→ substitute
→ expand capacity
→ restructure sourcing/specialization
```

Persistence and hysteresis matter. One delayed shipment should not rewrite a settlement's economy.

Economic state may adapt without implying physical settlement reconstruction. Automatic town growth/rebuilding remains deferred unless a later executable design specifically requires it.

## 17. Physical capability reconciliation

Generated and player-built infrastructure should share a functional vocabulary where practical.

Realized capabilities use explicit functional anchors / registered contributors rather than continuous settlement block scans.

Ordinary blocks remain ordinary Minecraft. Only gameplay-relevant anchors/contributors affect semantic service state.

A settlement may therefore retain:

```text
INTENDED ROLE = BEACON_NAVIGATION
CURRENT CAPABILITY = OFFLINE
```

after the player destroys the beacon controller. Repair can restore the capability without rewriting the settlement's semantic identity.

## 18. Sovereignty, allegiance, access, and ownership

These are deliberately separate concepts:

```text
territorial / faction control
local sovereignty
broad allegiance
Guild relationship
legal / service access
asset ownership / title
physical possession / control
```

A minimal allegiance primitive exists so civilian, Guild-recognized, player, and illager/faction vessels, structures, and locales can agree on patrol/hostility, access, contract interpretation, salvage, capture, and service eligibility.

Guild authorization does not universally imply local legality, and lack of Guild membership does not remove a settlement from ordinary economic life.

Hostile or contested regions may still possess mines, factories, freight, internal markets, civilian life, and military logistics.

Do not collapse all relationships into one universal reputation/wanted number.

## 19. Player economic principals and multiplayer

Economically meaningful institutional assets belong to an explicit **economic principal**.

Ordinary default: individual player. Optional player organizations provide shared ownership/authorization for multiplayer logistics without being a progression requirement.

Tracked institutional ownership applies to things such as:

- Scrip accounts;
- registered vessels;
- registered commercial facilities;
- commercial stock;
- shipments;
- contracts;
- loans/obligations.

It does not require ownership metadata on arbitrary blocks.

Organizations use bounded action permissions rather than mandatory corporate bureaucracy. Personal professional Standing/authorizations remain personal. Financial Credit belongs to the actual debtor principal.

Physical theft/capture may change possession/control without automatically transferring legitimate title.

## 20. Bootstrap minimum executable civilization proof

The first executable civilization slice should be deliberately narrow even though this design corpus is broad.

Minimum coherent proof:

```text
specialized producer / resource site
→ registered/recognized cargo interface
→ route / airfield / dock
→ civilized consuming settlement
→ basic Guild Hall / market
```

The first Hall needs only enough to prove:

```text
account
Bellanca claim/restitution
basic market
basic contract access
basic route/settlement information
```

A first post-tutorial freight loop should use the real systems:

```text
real settlement demand
→ opportunity / contract
→ source stock leaves semantic authority
→ physical cargo
→ aircraft transport
→ destination validation
→ destination stock changes
→ Scrip settles exactly once
→ save/reload reproduces result
```

Do not make the first alpha ship every mature loan, insurance, piracy, autonomous-fleet, jurisdiction, agency, or multiplayer feature before proving this core.

## 21. Implementation and validation invariants

`CIV-0042` is the terminal contract for implementation decomposition.

Hard requirements include:

- stable deterministic identities for persistent semantic objects;
- one authoritative representation for each economically consequential good/payment/shipment/capability at a time;
- idempotent semantic↔physical transactions;
- save/reload-safe payments and contract settlement;
- no arbitrary chunk loading to advance or query civilization;
- no continuous global settlement simulation;
- no arbitrary player-storage scans;
- bounded route/supplier search;
- compact consequential persistence rather than exhaustive history;
- schema/version migration for durable saves;
- exact separation between Content meaning and Implementation realization.

Validation follows the program evidence economy:

- cheap deterministic invariants broadly/exhaustively;
- ordinary integration in routine CI;
- representative expensive Minecraft/client/server/AAL/multiplayer evidence by risk-equivalence class;
- automatic widening when a representative reveals an unbounded defect class;
- human review for pacing, legibility, fairness, civilization feel, negative space, and the Bellanca-to-sandbox transition.

## Explicit non-goals / deferred depth

The accepted architecture deliberately does not require:

- individual household economy;
- simulated births/deaths as routine economics;
- continuous global company AI;
- a central-bank macroeconomy;
- a universal NPC mine-depletion ledger;
- continuous off-screen aircraft physics;
- global perfect-information commodity optimization;
- automatic physical settlement growth/reconstruction;
- a universal reputation/wanted meter;
- corporate stock/shareholder/governance simulation;
- detailed international diplomacy/war simulation;
- a unique bespoke Guild building for every location.

These are not missing features unless executable gameplay later demonstrates a need.

## Corpus map

### Foundation, Guild identity, architecture, onboarding

- `CIV-0001-guild-and-civilization-precommit.md`
- `CIV-0002-guild-architecture-and-donor-strategy.md`
- `CIV-0003-guild-regionalization-and-structural-grammar.md`
- `CIV-0003-open-sky-regional-variants.md`
- `CIV-0004-guild-recognition-and-regional-contexts.md`
- `CIV-0004-guild-settlement-footprint.md`
- `CIV-0005-guild-hall-services.md`
- `CIV-0005-open-sky-emblem-lock.md`
- `CIV-0006-first-guild-claim-and-black-box.md`
- `CIV-0007-dialogue-and-service-conversation.md`
- `CIV-0008-guild-civilization-adapter.md`

### Institutional economy, membership, vessels, finance, contracts

- `CIV-0009-scrip-standing-credit.md`
- `CIV-0010-membership-and-delegated-authority.md`
- `CIV-0011-vessel-registration-certification-and-recertification.md`
- `CIV-0012-guild-insurance-and-incident-evidence.md`
- `CIV-0013-guild-loans-and-vessel-financing.md`
- `CIV-0014-contract-structure-and-risk.md`
- `CIV-0015-scrip-ledger-stores-and-capital-backing.md`

### Freight, incidents, security, routes, information, automation

- `CIV-0016-autonomous-freight-and-piracy.md`
- `CIV-0017-freight-incident-realization-and-resolution.md`
- `CIV-0018-route-security-and-piracy-response.md`
- `CIV-0019-npc-freight-realization-and-simulation-budget.md`
- `CIV-0020-route-topology-and-navigation-network.md`
- `CIV-0021-route-formation-evolution-and-logistics-inspiration.md`
- `CIV-0022-guild-information-ui-and-computer-interface.md`
- `CIV-0023-guild-communications-and-information-propagation.md`
- `CIV-0024-programmable-economic-actions-and-logistics-automation.md`
- `CIV-0025-physical-logistics-warehouses-manifests-and-custody.md`

### Player production, markets, specialization, demand, and long-run economy

- `CIV-0026-player-production-and-supply-chain-participation.md`
- `CIV-0027-local-markets-catalogues-and-price-formation.md`
- `CIV-0028-economic-shocks-and-settlement-adaptation.md`
- `CIV-0029-production-chains-and-settlement-specialization.md`
- `CIV-0030-resource-potential-and-nondepleting-baseline.md`
- `CIV-0031-population-and-demand-formation.md`

### Latent simulation, persistence, reconciliation, regional flow

- `CIV-0032-economic-initialization-and-discovery-warm-start.md`
- `CIV-0033-latent-civilization-plan-and-economic-activation.md`
- `CIV-0034-economic-simulation-cadence-and-lazy-reconciliation.md`
- `CIV-0035-economic-dormancy-compaction-and-consequential-persistence.md`
- `CIV-0036-semantic-physical-capability-reconciliation.md`
- `CIV-0037-regional-trade-flow-resolution.md`
- `CIV-0038-prosperity-and-demographic-stability.md`

### Sovereignty, allegiance, multiplayer ownership, Bootstrap, terminal contract

- `CIV-0039-faction-sovereignty-economic-access-and-allegiance.md`
- `CIV-0040-player-economic-principals-and-multiplayer-ownership.md`
- `CIV-0041-bellanca-onboarding-and-bootstrap-civilization-integration.md`
- `CIV-0042-civilization-implementation-and-acceptance-contract.md`

Companion human/manual-gate record:

- `docs/handoffs/CIVILIZATION-PRECOMMIT-HUMAN-GATES.md`

## Handoff boundary

The conceptual sweep is complete.

Do not default to `CIV-0043`.

The next work is executable decomposition:

1. Content / Experience defines the smallest Bootstrap civilization/Guild slice against accepted Authorship/site evidence and the current Bootstrap Province roadmap.
2. Implementation builds the smallest authoritative runtime/persistence/custody seam needed by that slice, without seizing Content meaning.
3. Existing AAL/Create/Aeronautics/CC work remains capability/adaptation research under the same semantic-authority boundary.
4. Human gates are invoked only when machine evidence has made the relevant play/visual choice information-bearing.
5. Audit/orchestration should keep the civilization corpus as durable product direction and resist reopening settled design merely because a fresh worker lacks conversational context.

A new conceptual gate is justified only when executable evidence exposes a real unresolved product question not already answered by the CIV corpus.