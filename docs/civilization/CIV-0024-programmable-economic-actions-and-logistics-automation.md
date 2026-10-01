# CIV-0024 — Programmable Economic Actions and Logistics Automation

Status: Precommit / human-approved design direction
Branch: docs/civilization-precommit

## Decision

Guild information systems may eventually support authenticated programmable economic actions in addition to read-only observation, but automation must create the same real economic obligations as manual interaction and must never bypass physical logistics.

The intended boundary is:

1. Observe — prices, catalogues, stock indications, route state, contracts, account state, fleet state.
2. Commit — reserve stock, place orders, accept suitable routine work, allocate Scrip, create financial obligations.
3. Execute — move cargo, fly vessels, load/unload goods, repair infrastructure, complete physical work.

Automation may advance from observation into commitment as implementation matures, but it must not skip execution.

## Core invariants

- A computer query reads existing Guild information state; it does not trigger global economic recomputation.
- A remote order or reservation never teleports goods.
- Physical goods remain represented by actual Minecraft inventories/cargo when they enter the interactive world.
- A programmable action is subject to the same Scrip, Credit, Standing, authorization, certification, inventory, route-capacity, and risk constraints as the equivalent manual action.
- Routine commercial work may become machine-manageable; sensitive, discretionary, or authored decisions remain explicitly player-facing.
- Guild APIs expose facts and permissions, not an oracle for optimal strategy.

## Quotes, reservations, orders, and shipments

These concepts must remain distinct:

- Quote: informational market observation with provenance and freshness.
- Reservation: temporary commitment of stock and commercial terms.
- Order: financial/legal obligation created between parties.
- Shipment: actual physical or semantic movement of committed goods.

A stale quote is not a guaranteed price. A reservation may guarantee inventory and terms because the seller has actually committed stock. A remote order creates a logistics requirement rather than instant delivery.

## ComputerCraft / machine interface

The normal Guild UI and any CC:Tweaked-compatible Guild terminal should consume the same Guild information layer.

Sophisticated players may build their own accounting and logistics systems using:

- market quotes and freshness;
- warehouse inventory state;
- route risk and travel estimates;
- contract data;
- account/loan state;
- fleet and certification state.

Player software may eventually reserve goods, accept authorized routine commercial work, and dispatch player-owned logistics, provided all resulting obligations and physical freight are represented by authoritative Skyforge state.

## Historical data

The Guild may provide limited recent history, but player computers may record received observations indefinitely. This allows players to construct their own forecasting, arbitrage, route-risk, and fleet-utilization tools without Skyforge providing an optimal-trade oracle.

## Design principle

Skyforge should permit players to automate logistics businesses deeply. The challenge should arise from capital, information quality, inventory, routes, risk, engineering, and institutional permissions—not from forcing repetitive manual UI interaction.

Programmability should deepen the sandbox, not bypass it.
