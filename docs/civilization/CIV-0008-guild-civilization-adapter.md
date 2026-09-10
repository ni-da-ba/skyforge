# CIV-0008: Guild civilization adapter

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

Guild-specific settlement behavior should be implemented as part of the civilization realization / adapter layer, consuming authoritative Skyforge civilization fields rather than creating a parallel Guild simulation for population, settlement importance, or economic geography.

Canonical layering:

**Skyforge civilization fields / semantic settlement state**
→ **civilization adapter**
→ **Guild interpretation and capability derivation**
→ **Minecraft realization**

The Guild layer should not independently decide population or duplicate settlement semantics already established by Skyforge.

## Inputs from civilization semantics

The adapter may consume existing or future civilization-facing fields such as:

- settlement population / scale;
- settlement type or functional role;
- local production and resource context;
- trade importance;
- route connectivity;
- remoteness / hazard exposure;
- political / institutional relationship to the Guild;
- recent events or damage states;
- terrain and siting opportunities relevant to landing and freight handling.

The exact field set remains dependent on the final civilization architecture.

## Guild-derived outputs

From those inputs, the adapter may derive or select:

- whether a Guild presence exists;
- Hall / campus footprint class;
- Hall siting preference;
- available Guild service capabilities;
- contract families and weighting;
- local Guild trade / commodity behavior;
- navigation, recovery, freight, repair, and claims availability;
- visual activity cues and operational intensity;
- appropriate regional Hall grammar and material realization.

These are interpretations of civilization state, not an independent source of truth about the settlement.

## Population rule

Population is authoritative on the Skyforge backend. Guild logic may use population as an input but should not estimate, recalculate, or maintain its own population model.

Population should also not be the sole driver of Guild importance. A small settlement on a critical route may justify a large Guild facility, while a larger but isolated settlement may support only a modest Hall.

## Economy rule

The Guild economy should consume settlement supply, demand, production, logistics, and event semantics where those already exist. It should avoid duplicating those systems merely to generate Guild prices or contracts.

The adapter should translate semantic imbalances into player-facing Guild behavior, for example:

- shortage → procurement / freight demand;
- surplus → outbound trade opportunity;
- infrastructure damage → repair / construction work;
- route hazard → escort / rescue / navigation work;
- lost registered asset → salvage / recovery work.

## Implementation intent

Keep the Guild integration thin and data-driven. Prefer derived capability sets and service registries over bespoke logic per settlement.

A conceptual shape is:

```text
CivilizationSettlementState
    population
    industry
    production / demand
    route importance
    risk
    institutional relations
    environment
        |
        v
GuildSettlementAdapter
    derivePresence()
    deriveCapabilities()
    deriveContracts()
    deriveTradeContext()
    deriveSitingRequirements()
        |
        v
Minecraft Hall / NPC / UI / structure realization
```

Names are illustrative only; no implementation API is frozen by this document.

## Precommitted rule

> **Guild settlement behavior is an interpretation of authoritative Skyforge civilization state. It belongs in the civilization adapter / realization path, not in a parallel population or settlement simulation.**

Exact adapter APIs, field names, contract weighting, price equations, Hall class thresholds, and Minecraft realization details remain implementation-stage decisions.
