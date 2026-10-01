# CIV-0015: Scrip ledger, Guild stores, and capital backing

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

Guild Scrip is **ledger money**, not a physical inventory item.

A player's Scrip balance is maintained by the Guild network and exposed through an authenticated player-facing credential or interface, provisionally represented by the player's Guild membership card and/or a dedicated Guild account tab in the UI.

A physical card may therefore function as a token of identity, membership, and account access, but the Scrip itself should not be modeled as stackable Minecraft currency items.

## Institutional backing

Scrip may be described in-world as being backed by the Guild's aggregate capital and capacity rather than by a fixed commodity peg.

That capital can include:

- inventories and strategic reserves;
- warehouses and bonded stores;
- ships and other transport assets;
- docks, yards, halls, and logistics infrastructure;
- workshops and industrial capacity;
- claims on member settlements and counterparties;
- outstanding commercial receivables;
- the Guild's ability to move, store, insure, finance, and procure goods across its network.

The intended implication is not that a holder may redeem one Scrip for a fixed weight of iron, grain, or another commodity. Instead, Scrip has value because a large, asset-rich, commercially important institution accepts it for real goods and services, settles obligations in it, and has substantial productive and logistical capacity behind those promises.

Canonical framing:

> **Scrip is backed by the Guild's balance sheet, commercial network, and capacity to make good on its obligations—not by a literal commodity peg.**

The exact monetary, reserve, or issuance doctrine remains deferred unless gameplay later requires it.

## Player-facing account model

The Guild account should expose relevant values through a lightweight interface rather than require physical currency handling.

Candidate account surfaces may include:

- Scrip balance;
- outstanding loans and payments;
- active escrow;
- insurance status and charges;
- Credit summary;
- Standing / authorizations;
- vessel registrations;
- membership and credential state.

The membership card or equivalent credential may provide an in-world justification for account authentication and access, but account state remains authoritative outside the physical item.

Loss or destruction of the card must therefore not destroy the player's balance. Replacement should be an identity/credential problem, not a currency-loss event.

## Guild stores are local markets

Guild stores, trade counters, and exchanges should not share one global catalogue or universal price list.

Each store may have its own:

- stock;
- buying demand;
- prices;
- catalogue of finished goods;
- local and regional specialties;
- service inventory;
- access restrictions and authorizations;
- temporary shortages or surpluses.

The store's state should be derived from settlement and network semantics through the civilization adapter.

Canonical rule:

> **Each Hall is a node in the Guild network, not a clone of a global vending machine.**

A player may therefore discover meaningful differences in price and availability between Guild locations.

## Store and commodity distinction

Where useful, the implementation may distinguish between:

- **retail / Guild stores** — standardized components, supplies, certified equipment, finished goods, and services;
- **commodity trade** — bulk buying and selling driven more directly by settlement supply, demand, and logistics.

These may share a physical counter or interface in small Halls and split into specialized services only where settlement scale warrants it.

## Trade and arbitrage

Spatial price differences should create a viable merchant career.

The intended logic remains:

> Manufacturers create margin through production efficiency. Traders create margin through spatial and informational differences.

Stores should use bounded stock and responsive demand so that obvious infinite arbitrage loops collapse as inventories normalize.

Autonomous NPC/Guild freight may also move markets toward equilibrium over time when the player does nothing.

## Scrip sources and sinks

Scrip should enter player accounts primarily through useful participation in Guild and settlement economies, including:

- contract payments;
- commodity sales;
- sale of manufactured goods or components;
- salvage and recovery compensation;
- Guild agency or service income;
- insurance settlements and restitution;
- financing and contract advances, which create corresponding obligations.

Major Scrip sinks may include:

- aircraft and component purchases;
- insurance premiums and deductibles;
- repair and recovery services;
- certification, inspection, and licensing fees;
- membership dues where appropriate;
- storage, docking, freight, and logistics services;
- Guild-authorized infrastructure;
- debt repayment.

Scrip should not normally enter the economy as random mob drops or large generic loot rewards.

## Implementation boundary

The civilization layer should remain coarse and semantic. It may reason in commodity classes, aggregate stock, desired stock, route state, and institutional capital rather than tracking every physical item when unobserved.

At the Minecraft realization boundary, actual item stacks and inventories may be transacted against that semantic state.

This preserves the existing Skyforge architecture:

**civilization semantics -> Guild/economic interpretation -> Minecraft realization**

## Precommitted rules

1. Scrip is ledger currency rather than a stackable item.
2. The player's balance survives loss of any physical membership card or credential.
3. A membership card or Guild UI may expose account state and serve as the in-world authentication surface.
4. Guild stores maintain local stock, prices, and catalogues rather than a universal global inventory.
5. Trade opportunities should emerge from geographic, industrial, and logistical differences.
6. Scrip may be fictionally backed by Guild capital, assets, receivables, and logistical capacity without being pegged to one redeemable commodity.
7. Monetary-policy simulation is deferred unless later gameplay requires it.
8. Economic semantics remain authoritative; Minecraft inventories realize transactions at the adapter boundary.
