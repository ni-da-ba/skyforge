# CIV-0016: Autonomous freight, physical realization, and piracy

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

NPC freight is a real part of Skyforge civilization. Inter-settlement commerce continues independently of the player, but most freight movement should be simulated at a **coarse semantic level** unless the route, vessel, cargo, or incident becomes directly relevant to observed gameplay.

The existing candidate integration **Create Aeronautics: Automated Logistics / Aeronautics Automated** remains the preferred system to investigate for the physical execution layer when autonomous aircraft must actually materialize, dock, load, unload, or traverse observed space. Skyforge civilization state remains authoritative; third-party automation should realize that state rather than become its semantic owner.

Canonical rule:

> Simulate freight semantically by default; materialize and physically operate it when observation or gameplay relevance justifies the cost.

## 1. Freight state

A semantic freight movement may carry data such as:

- route ID;
- operator class;
- vessel or service class;
- origin and destination;
- departure and expected arrival time;
- cargo manifest or semantic commodity quantities;
- value / bonded status;
- route risk and reliability;
- current route state;
- insurance / recovery relationships where relevant.

The implementation does not need to tick an aircraft block-by-block across unloaded space.

Candidate coarse lifecycle:

**SCHEDULED → LOADING → IN_TRANSIT → ARRIVED**

with exceptional branches such as:

**DELAYED / DIVERTED / DISTRESS / ATTACKED / LOST / RECOVERY_PENDING**.

## 2. Physical realization

When a freight movement becomes observable or otherwise gameplay-relevant, the Minecraft adapter may realize the semantic operation physically using an Aeronautics-compatible autonomous logistics layer.

Examples include:

- departure or arrival at a loaded Guild facility;
- docking and cargo transfer near the player;
- a vessel crossing an observed route segment;
- a distress event;
- an escort contract;
- a salvage / recovery operation;
- an attack or piracy event.

The physical vessel is a realization of an existing semantic route operation. It must not create duplicate cargo, duplicate authoritative vessel identity, or independent economic state.

## 3. Operators

The Guild facilitates and regulates a commercial network but does not own every vessel within it. Freight traffic may be operated by:

- the Guild itself;
- private merchants and carriers;
- settlement-owned operators;
- independent skyfarers;
- player-owned logistics enterprises.

This distinction should be preserved because attacking a Guild vessel and attacking an unrelated civilian carrier can carry different legal, institutional, and Standing consequences.

## 4. Player piracy

Skyforge may support **player piracy / freight raiding** as an optional sandbox behavior.

This should not be implemented as consequence-free loot pinatas. A freight vessel is an economic asset with an operator, cargo ownership, route context, registry state, and possibly Guild protection or insurance.

A piracy interaction may therefore propagate into:

- stolen physical cargo;
- settlement stock changes;
- route losses or delays;
- insurance claims;
- salvage / recovery opportunities;
- altered route security;
- escort or recovery contracts;
- criminal or hostile relationships where the relevant polity recognizes the offense;
- Guild Standing consequences when Guild-recognized rights or property are violated;
- suspension or loss of particular Guild authorizations;
- increased scrutiny or refusal of sensitive contracts.

Exact law-enforcement and hostility systems remain civilization-stage design work and should not be invented solely to support piracy.

## 5. Guild versus civilian targets

The system should distinguish at least conceptually between:

### Guild freight

Cargo or vessels directly owned, operated, bonded, or entrusted by the Guild. Raiding these assets is a direct breach of the Guild network and should normally have serious Standing and authorization consequences if attribution is established.

### Civilian / private freight

Privately owned or settlement-owned traffic operating within or outside the Guild network. Consequences depend on ownership, local law, Guild recognition, contracts, insurance, and whether the cargo is bonded or protected by Guild agreements.

The Guild should not automatically become the police force for every act of piracy in the world. It may refuse services, enforce contractual consequences, honor claims, post bounties or recovery work, or cooperate with local authorities within its chartered scope.

## 6. Attribution and evidence

The future Guild black box / transponder system is a natural evidence source for freight incidents. Where appropriate it may support:

- vessel identity;
- route and transponder history;
- cargo / bonded status references;
- distress transmission;
- incident timing;
- attacker identification where technically plausible;
- loss / recovery state;
- evidence used by claims, arbitration, or Standing systems.

The implementation should not assume perfect omniscience. A successful pirate operation may have different consequences if attribution is absent, ambiguous, or proven.

This creates useful gameplay space between lawful commerce and overt hostility without requiring arbitrary invisible reputation penalties.

## 7. Economy interaction

NPC freight should gradually respond to and correct settlement supply / demand imbalances. Player piracy can therefore have real economic consequences:

**freight attacked → cargo fails to arrive → destination stock remains low → prices / contracts / route risk may change**.

Likewise, persistent attacks on a corridor may justify semantic changes such as:

- reduced reliability;
- higher insurance exposure;
- increased escort demand;
- rerouting;
- lower throughput;
- stronger security;
- temporary suspension of service.

These effects should remain coarse unless observed.

## 8. Production constraint

Do not require every semantic freight route to own a continuously simulated physical aircraft. Physical realization is an expensive presentation / gameplay mode selected when needed.

Canonical architecture:

**Skyforge civilization state → semantic logistics operation → observation/relevance decision → Aeronautics physical realization when warranted → reconciliation back into semantic state**

## Precommitted rules

1. NPC freight exists independently of the player.
2. Freight is simulated coarsely unless directly relevant or observed.
3. Aeronautics Automated / Automated Logistics remains the preferred candidate to investigate for physical autonomous-aircraft realization.
4. Skyforge remains authoritative over route, cargo, economic, and civilization semantics.
5. Guild, private, settlement, and player-owned freight should all be representable.
6. Player piracy may be supported as legitimate sandbox behavior.
7. Piracy must propagate into real economic, contractual, insurance, Standing, and route consequences rather than produce isolated loot encounters.
8. Guild consequences depend on actual relationship to Guild rights or assets; the Guild is not automatically the sovereign police authority for all civilian commerce.
9. Incident attribution should use available evidence rather than assume perfect institutional omniscience.
10. Exact combat, boarding, law-enforcement, bounty, and hostility mechanics remain deferred until the relevant systems are designed.