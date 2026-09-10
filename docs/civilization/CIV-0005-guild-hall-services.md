# CIV-0005: Guild Hall services

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

Every proper Skyfarer's Guild Hall should provide a small universal service core, while additional services are capability-based and derived from local economic, geographic, and strategic conditions.

The implementation must remain deliberately thin. Distinct Guild services do not require distinct NPCs, rooms, buildings, or bespoke subsystems unless later gameplay testing demonstrates clear value.

> **One Hall service registry, one concentrated player-facing interaction surface, and a small set of capability flags should be preferred over spatial or systemic fragmentation.**

## Universal Hall services

Every proper Hall should provide:

1. **Contracts** — access to local and relevant regional Guild work.
2. **Registry / identity** — vessel ownership, registration, status, basic certification visibility, and loss reporting.
3. **Guild account access** — Scrip balance, escrow, obligations, and related account state.
4. **Basic trade** — a bounded set of common supplies, standard parts, and locally relevant goods.
5. **Route / settlement information** — known routes, nearby settlements, hazards, local market signals, and Guild presence where appropriate.

A very small or frontier Hall may expose several or all of these through one counter, one clerk, one terminal, one notice board, or another compact interaction pattern.

## Capability-based services

Additional services should be represented as capabilities rather than fixed Hall tiers.

Candidate capabilities include:

- insurance claims;
- bonded freight;
- warehouse / bulk storage;
- light repair;
- heavy repair;
- aircraft sales;
- custom-aircraft certification;
- advanced navigation / weather / survey services;
- salvage and recovery;
- heavy docking;
- underwriting / credit;
- arbitration;
- blueprint / technical archives;
- training / academy functions.

Capabilities should follow settlement function and route role rather than population alone.

Examples:

- agricultural hub → commodity trade, bonded freight, procurement, storage;
- mountain route station → navigation, weather, rescue, repair;
- industrial center → heavy repair, parts market, aircraft sales, certification;
- administrative center → underwriting, arbitration, archives, advanced registry.

## Player-facing consolidation

Services may be mechanically distinct while remaining spatially consolidated.

Preferred layout pattern:

**entry / contract board**
→ **main counter or common service surface**
→ **optional specialist rooms or desks**
→ **working-side freight / workshop / dock functions**

The player should not be required to cross a settlement simply because multiple Guild systems exist.

## Implementation constraint

Avoid overbuilding bureaucracy.

The first implementation should prefer:

- one data-driven Hall service registry;
- capability flags or equivalent declarative metadata;
- shared UI / interaction surfaces where practical;
- service availability derived from settlement state and Hall capabilities;
- reused NPC / interaction logic rather than one bespoke actor per service;
- optional physical differentiation only where it improves readability or gameplay.

Do not introduce fixed visible player-facing Hall tiers unless later testing demonstrates that they improve comprehension.

## Minimum usefulness guarantee

A functioning Guild Hall should never be a dead destination.

At minimum, the player should be able to:

- find work;
- inspect or use Guild account state;
- access registry / identity functions;
- conduct basic commerce;
- obtain useful route or settlement information.

## Precommitted rule

> **Every proper Guild Hall provides contracts, registry/identity services, Guild account access, basic trade, and route/settlement information. Additional services are capability-based, locally derived, and should be implemented with the least spatial and systemic complexity necessary to make them legible and useful.**

Exact UI, NPC count, desk count, inventory breadth, capability schema, and service-specific mechanics remain implementation-stage decisions.
