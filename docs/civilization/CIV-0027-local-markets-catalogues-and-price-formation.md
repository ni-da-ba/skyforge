# CIV-0027 — Local Markets, Catalogues, and Price Formation

Status: precommit

## Decision

Guild markets are local economic surfaces with three separate concepts:

- catalogue: what a location normally deals in;
- stock: what is currently available or demanded;
- price: the current transaction terms at that market.

Catalogues change slowly from settlement role, population, industry, route connectivity, Guild capabilities, regional specialization, and sustained commercial activity. Stock and prices may change more frequently through coarse economic events.

## Price formation

Prices are locally derived from coarse economic state rather than fixed globally or maintained through a continuous order book. Relevant pressures include current stock, desired stock, local production and consumption, incoming shipments, route cost and hazard, regional availability, recent disruptions, and handling/service margin.

A useful conceptual form is:

`local price = regional reference × scarcity pressure × demand pressure + delivered import cost + handling margin`

This is not a mandated literal formula.

Buy and sell prices differ. The spread represents handling, storage, risk, and commercial margin and prevents trivial buy/sell cycling.

## Market depth

A quoted price is not an infinite bid. Large transactions change the market. If a settlement urgently wants 100 units and receives 5,000, the scarcity premium should not apply to every unit. Markets therefore need bounded demand depth or quantity-sensitive repricing, though no explicit player-facing order book is required.

## Replenishment

Stores replenish through the economic network, not through independent timers. Low stock creates procurement pressure, which may be satisfied by local production, regional suppliers, NPC freight, player freight, or player manufacturing.

Finished goods, commodities, and major assets use the same broad supply-demand-logistics framework at different granularities. Major assets may be locally available, regionally transferable, or special-order rather than represented as large shelf inventories.

## Regional price structure

Well-connected markets with good freight, information, and inventory tend to converge more strongly. Frontier markets can diverge because of transport cost, hazard, delay, and stale information. Delivered transport cost therefore creates geographic price floors and makes route improvements economically meaningful.

## Player industry

Registered player production can increase local or regional supply and reduce scarcity premiums. This should improve the region while eroding the extraordinary margin that attracted the player in the first place, naturally encouraging broader distribution and logistics rather than infinite local dumping.

## Computational constraints

Markets reconcile on coarse economic events such as transactions, shipment arrival/failure, production-consumption updates, major incidents, or scheduled economy updates. UI and ComputerCraft reads consume cached quotes and do not trigger recomputation.

## Locked principles

> Each Guild market maintains a locally derived catalogue, bounded stock, and transaction prices. Catalogues change slowly with settlement function and network development, while stock and prices respond on coarse economic events to production, consumption, trade, logistics, and disruption.

> Prices reflect marginal local scarcity rather than unlimited fixed-rate demand. Large transactions change the market they transact against.

> A store replenishes through the economy; it does not replenish independently of it.
