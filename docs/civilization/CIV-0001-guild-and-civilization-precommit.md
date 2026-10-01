# CIV-0001: Skyfarer's Guild and civilization precommit

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Purpose

This document preserves the current agreed direction for civilization-facing systems before the dedicated civilization stage begins. It is intentionally stronger than a brainstorm and weaker than an implementation ADR: later civilization authors may refine parameters and presentation, but should not silently reverse the invariants below.

The intended architecture remains consistent with Skyforge's existing separation of meaning from realization:

> Skyforge determines semantic state; the Minecraft backend realizes that state.

Civilization, economy, structures, traffic, contracts, and logistics should follow the same rule.

## 1. Civilization model

Settlements are not static decoration. Each settlement is a semantic world entity with persistent coarse state. At minimum that state may include:

- population or settlement scale class;
- production capabilities;
- stock levels;
- desired stock / demand targets;
- services available;
- infrastructure classes;
- active economic modifiers;
- active contracts;
- settlement relationships and institutional affiliations;
- last authoritative update time.

The implementation should prefer a small set of authoritative aggregate variables over pretending to simulate every citizen when nobody is observing them.

### Simulation tiers

1. **Observed / loaded:** detailed Minecraft realization may exist: NPCs, physical inventories, vehicles, machines, combat, loading activity, and visible traffic.
2. **Unobserved / unloaded:** settlements advance through coarse semantic state transitions and analytical production/consumption.
3. **Reconciliation:** on load, visit, relog, or interaction, the Minecraft realization is reconstructed from authoritative semantic state.

Canonical rule:

> Simulate physically when observed; simulate semantically when unobserved.

## 2. The Skyfarer's Guild

The Skyfarer's Guild is the principal institutional interface between the player and inter-settlement civilization.

Its fundamental commodity is **trust**.

The Guild provides or intermediates:

- freight and bonded contracts;
- certified aircraft and aircraft standards;
- vessel registry;
- salvage and recovery;
- insurance and claims;
- account services and escrow;
- professional standing and certification;
- stores and commodity exchange;
- route information and navigation services;
- arbitration of Guild-recognized commercial disputes;
- blueprints and standardized technical information.

The Guild should feel powerful because many settlements voluntarily depend on its network, not because it is secretly a world government.

## 3. Guild origin and jurisdiction

Current preferred origin model:

The Guild was not chartered by one sovereign. It emerged from local pilot associations, freight brokers, shipwright societies, merchants, insurers, and mutual-aid organizations that entered an early **Interport Charter / Compact** during the first sustained era of long-distance sky commerce.

The early compact likely standardized only narrow interport functions:

- vessel registration recognition;
- bonded contracts;
- standardized manifests;
- mutual rescue;
- salvage claims;
- certification of recognized skyfarers;
- settlement account recognition;
- interport commercial arbitration.

Over time the network accumulated additional functions: registry, contract enforcement, arbitration, insurance, banking, technical standards, recovery, and commercial influence.

### Sources of Guild authority

The Guild's authority may arise from three overlapping sources:

1. **Delegated public authority** — member settlements authorize specific Guild functions locally.
2. **Contractual authority** — individuals and firms voluntarily accept Guild rules when using Guild services.
3. **Network power** — access to routes, accounts, insurance, certification, stores, and counterparties makes Guild recognition economically important.

### What the Guild generally does not control

Unless a local polity separately delegates it, the Guild is not responsible for:

- local criminal law;
- general taxation;
- elections;
- ordinary policing;
- ordinary domestic trade;
- sovereign land ownership;
- settlement governance.

This distinction is important because Guild overreach can produce legitimate jurisdictional disputes.

## 4. Settlement relationships to the Guild

Settlements may support multiple relationship classes rather than one universal membership state. Initial conceptual classes:

- **full member** — broad recognition of Guild contracts, registry, accounts, arbitration, recovery, and standards;
- **associate** — partial recognition or limited local services;
- **independent / non-charter** — trades with Guild participants but does not broadly delegate authority;
- **hostile / excluded** — does not recognize Guild claims or actively obstructs them.

These states should matter systemically: service availability, contract enforceability, insurance, salvage rights, route risk, local prices, and legal ambiguity may all vary.

## 5. Organizational structure

Preferred hierarchy:

**Central Charter / General Council**
→ **regional or provincial chapters**
→ **local Guild halls and service offices**

A local hall may contain some subset of:

- Contract Office;
- Vessel Registry;
- Guild Bank / Account Office;
- Insurance and Claims;
- Guild Store / Commodity Counter;
- Salvage and Recovery;
- Navigation / Survey Office;
- warehouse and bonded freight functions;
- hangar, dock, maintenance, or inspection services.

Local chapters should have meaningful discretion. Individual halls may be competent, corrupt, conservative, aggressive, underfunded, or politically entangled without implying that the entire Guild shares the same behavior.

## 6. Player opening: Bellanca crash

Preferred opening remains:

1. The player begins beside a crashed **Bellanca B0-A**.
2. The wreck establishes aircraft, loss, repair, and risk before the player understands the Guild.
3. The player recovers identifying material such as registration or transponder evidence.
4. The player reaches a settlement and encounters a Guild hall.
5. The Guild recognizes the registered / insured aircraft.
6. The first claim creates the player's account relationship and introduces Scrip, insurance, registry, and professional trust.
7. The player receives a viable path back into the sky.

Potential first-claim resolutions may include:

- replacement aircraft;
- Scrip settlement;
- retention of the wreck plus reduced settlement.

The third option is particularly attractive because it preserves engineering agency: a technically inclined player may rebuild the original aircraft.

The wreck should remain a meaningful world object until salvaged, surrendered, or otherwise resolved.

## 7. Certified aircraft

The Guild should maintain a limited set of **certified standard aircraft families** rather than an enormous bespoke catalog.

Certification provides an institutional reason for standardized parts, performance envelopes, blueprints, inspection, insurance, and training.

The Bellanca B0-A is the current light-aircraft anchor: ubiquitous trainer, courier, and utility aircraft.

Player-designed aircraft remain valid. Certification and insurance for custom craft should simply be less automatic, more expensive, or require evidence/testing.

## 8. Contracts

Routine contracts should be downstream of actual settlement state, not random MMO quest rolls.

Routine families include:

- procurement;
- freight;
- escort;
- repair;
- construction;
- survey;
- salvage;
- emergency supply;
- scheduled supply.

Canonical principle:

> Routine contracts sustain the economy. Exceptional contracts reveal the world.

### Contract provenance

Not all contracts should share one moral or jurisdictional status. Candidate provenance classes:

- Guild Contract;
- Chapter Contract;
- Private Commission;
- Discretionary Contract;
- Illicit Contract.

Rare contracts may expose political disputes or Guild overreach: e.g. a local chapter asks the player to disable a mayor's aircraft while claiming bonded-property authority, while the mayor asserts that the Guild has exceeded its charter.

### Blueprint contracts

Repair and construction contracts should specify a desired state rather than a single prescribed construction sequence.

Candidate validation classes:

- `EXACT_BLOCK`
- `BLOCK_TAG`
- `MATERIAL_CLASS`
- `OPTIONAL`
- `FUNCTIONAL_INTERFACE`

This allows player creativity while retaining contract-verifiable requirements.

## 9. Guild Scrip, standing, and accounts

Emeralds should not be the foundation of the serious Guild economy because ordinary Minecraft trading systems can trivialize their supply.

Preferred distinction:

- **Guild Scrip** — money / transferable economic value;
- **Guild Standing** — professional trust and institutional reputation;
- **Financial Credit** — the bank's assessment of the player's financial reliability.

These values should remain distinct.

### Guild Bank MVP

Initial banking mechanics should remain deliberately small:

- account balance;
- escrow;
- insurance billing / claims;
- financial record required by other Guild systems.

Loans, securities, macroeconomic banking behavior, bank runs, and elaborate monetary simulation are deferred unless later gameplay proves they are useful.

## 10. Guild stores and merchant careers

Guild stores should make trading a viable profession independent of manufacturing.

Canonical principle:

> Manufacturers create margin through production efficiency. Traders create margin through spatial and informational differences.

Regional stores should maintain bounded inventory and local prices derived from settlement state.

Players may therefore:

- buy goods where supply is high;
- transport them to higher-demand locations;
- fulfill contracts using purchased goods rather than self-produced goods;
- exploit information, route knowledge, timing, and risk rather than factory ownership.

Progression can support multiple economic identities:

**BUY → TRADE → ASSEMBLE → MANUFACTURE → AUTOMATE**

A contract should generally care that the required good arrives in acceptable condition, not how the player obtained it.

## 11. Standard components and freight

The Guild should standardize a small family of commercially important interfaces and components. Candidate examples:

- engines;
- propellers;
- control surfaces;
- pumps;
- bearings and gearboxes;
- structural members;
- cargo containers;
- navigation equipment;
- certified aircraft assemblies.

A promising cargo primitive is the provisional **Guild Freight Unit (GFU-3)**, inspired by existing Aeronautics cargo-build dimensions:

- external envelope approximately 3 × 3 × 11 blocks;
- standard handling clearances;
- standard lifting / docking hardpoints;
- standard mass class / manifest metadata.

This is not yet a frozen implementation dimension, but the civilization stage should preserve the idea of a standardized freight interface because it can unify warehouses, cranes, contracts, airships, depots, and autonomous logistics.

## 12. Autonomous logistics

Autonomous freight routes should continue to exist when the player is away, but should not require physically ticking every vehicle.

A route may persist semantic data such as:

- route ID;
- origin;
- destination;
- vessel / service class;
- cargo plan;
- departure time;
- expected arrival;
- risk;
- reliability;
- current semantic state.

When a leg becomes due, the system may resolve it semantically. Physical aircraft materialize only when observation or gameplay requires them.

Potential external compatibility worth auditing during civilization implementation: **Create Aeronautics: Automated Logistics**, which appears to overlap directly with stations, transponders, recorded routes, docking, cargo transfer, unloaded operation, and materialization.

## 13. Economy architecture

The economy should use the same semantic layering as world generation:

**World / settlement semantics**
→ **economic state**
→ **Minecraft realization**

Examples:

- Skyforge decides that a settlement has a grain shortage.
- Guild boards expose food contracts.
- Guild stores show elevated prices and reduced stock.
- warehouses may look depleted;
- inbound traffic may increase;
- exceptional events may modify demand and risk.

The backend should display the consequence; the semantic layer remains authoritative.

Design rule:

> Deep fiction, simple primitives, emergent consequences.

Initial primitives should remain close to:

1. settlement demand;
2. settlement supply / stock;
3. contracts;
4. Guild Scrip;
5. Guild standing/account state;
6. service expenditures;
7. route and event modifiers.

## 14. Exceptional events

Rare events should perturb real state rather than exist only as quest text. Examples:

- illager attack on a Guild distress site;
- dragon attack on a settlement or route;
- aircraft disaster;
- freight theft;
- infrastructure failure;
- political blockade;
- salvage dispute.

Consequences may alter:

- stock;
- demand;
- prices;
- service availability;
- insurance claims;
- local construction;
- traffic;
- security;
- contract availability.

## 15. Relationship to lost-asset recovery

The Guild should be the civilization-facing owner of many recovery interactions, while the recovery system itself remains a robust lower-level asset lifecycle.

Existing preferred lifecycle:

**ACTIVE → LOST → RECOVERY_LOCKED → ACTIVE**

The Sky Dredger and related recovery systems should feel like institutionalized salvage rather than magical item restoration.

Vessels should preferentially recover from a known safe snapshot / registered state instead of requiring expensive instant-of-failure serialization.

## 16. Civilization-stage invariants

The dedicated civilization stage should treat the following as precommitted unless explicitly superseded:

1. Settlements have authoritative semantic state independent of loaded chunks.
2. Physical simulation is observation-dependent; coarse simulation remains authoritative when unloaded.
3. The Guild is powerful but non-sovereign.
4. Guild authority derives from delegated settlement powers, contracts, and network effects.
5. Scrip, professional Standing, and financial Credit are separate concepts.
6. Contracts derive primarily from world / settlement state.
7. Routine economic work and rare narrative events are separate layers.
8. Trading is a first-class career, not merely a precursor to manufacturing.
9. Standardized aircraft, freight, and service interfaces are desirable because they create both gameplay and asset-production leverage.
10. The Minecraft backend realizes civilization state but does not become its semantic authority.
11. No economic or civilization feature should require a bespoke physical asset for every instance.
12. Guild content should expose systemic consequences visibly in settlements whenever practical.

## 17. Deliberately deferred questions

The following should remain open until the civilization stage can evaluate them against implementation constraints and gameplay tests:

- exact settlement simulation equations and time steps;
- exact Scrip issuance / destruction mechanisms;
- exact price-response curves;
- exact starting account balance and first-claim payout;
- exact standing thresholds and rank names;
- precise Interport Charter wording and historical dates;
- exact Guild central governance model;
- exact number and names of certified aircraft families;
- GFU-3 final dimensions and mass metadata;
- route-risk model and random-event probabilities;
- detailed NPC representation;
- loans and advanced finance;
- exact relationship to third-party autonomous logistics mods.

These are parameters or implementation-sensitive systems, not reasons to reopen the core conceptual architecture.