# CIV-0026 — Player Production and Supply-Chain Participation

Status: precommit / design lock

## Decision

Player-operated production may enter the same civilization supply chains used by NPC and settlement production. The player does not receive a separate economy or special quest-only buyer. Registered player commercial facilities publish authoritative supply through explicit economic interfaces, and civilization demand may source from that supply when price, quantity, route cost, reliability, and applicable standards make it competitive.

The system must preserve regional scarcity and logistical geography. A large automated Create factory may become economically important, but it must not flatten the entire world merely because it can produce one commodity cheaply.

## Core principle

> The player may become part of civilization's productive base, but production matters only where logistics, demand, capital, and institutional access can carry it.

Player production should compete on the same effective delivered-value basis as other supply rather than receiving privileged demand.

## Economic boundary

Private factory output remains ordinary Minecraft inventory until deliberately committed to a registered commercial interface.

```text
physical player production
        |
        v
registered commercial/export interface
        |
        v
commercial custody / published supply
        |
        v
civilization market and logistics system
```

The civilization simulation must not continuously scan arbitrary chests, belts, vaults, or factories to infer output.

Once goods enter commercial custody, the economic ledger becomes authoritative until those goods are withdrawn back into physical custody or loaded into a realized shipment.

## Commodity classification

The civilization layer should reason primarily in coarse semantic commodity classes rather than individual mod items. Actual Minecraft items remain the transaction payload at adapter boundaries.

Illustrative classes include:

- FOOD
- GRAIN
- TIMBER
- STONE
- METALS
- FUEL
- TEXTILES
- MACHINERY
- AIRCRAFT_PARTS
- LUXURY_GOODS

The adapter may map multiple compatible items or tags into one semantic class while retaining enough item-level information to materialize withdrawals and validate deliveries.

Do not model every modded item as an independent global market unless identity is economically meaningful.

## Player supply offers

A registered player commercial facility may publish supply approximately as:

```text
facility
commodity / accepted item class
quantity available
ask price or sale policy
minimum lot, if needed
freshness / availability state
```

The offer is backed by stock already committed to commercial custody. The player may not publish stock that remains freely available in unrelated private inventories.

## Demand allocation

When settlement or network demand requires supply, candidate sources may include:

- local settlement production;
- nearby NPC commercial supply;
- regional depots or hubs;
- Guild-owned stock where appropriate;
- registered player facilities.

Selection should account for more than nominal unit price. A coarse effective delivered cost can consider:

```text
effective_cost =
    source_price
  + transport_cost
  + expected_hazard_loss
  + delay / unreliability penalty
  + transshipment cost
  + applicable fees
```

Exact formulas are implementation details. The design requirement is that the cheapest factory in nominal terms is not automatically the best supplier for every settlement in the world.

## Regionality and anti-flattening

World economics should resist global price collapse through ordinary structural constraints rather than arbitrary player caps.

Important constraints include:

1. **Finite demand** — each market can absorb only so much of a commodity over a period.
2. **Transport cost** — distant supply loses competitiveness.
3. **Route capacity** — freight throughput is limited semantically.
4. **Hazard and unreliability** — risky corridors increase effective cost.
5. **Backhaul imbalance** — one-way freight can make nominally profitable routes expensive.
6. **Inventory/storage limits** — destinations do not absorb infinite surplus.
7. **Information delay** — the player may act on stale demand signals.
8. **Supplier diversity/resilience where appropriate** — critical systems may avoid relying on one source when alternatives exist.
9. **Institutional requirements** — bonded, certified, or sensitive goods may require recognized production or custody standards.
10. **Market response** — sustained oversupply lowers local prices and reduces further demand.

Therefore a highly efficient player factory can dominate a region or supply chain if it earns that position, but doing so requires enough transport, market access, and demand to distribute its output.

## No arbitrary anti-success ceiling

Skyforge should not impose a hidden rule such as "player factories may satisfy at most 20% of regional demand" merely to preserve NPC relevance.

If a player builds a superior industrial system with adequate logistics, it is acceptable for civilization to depend heavily on it.

The balancing response should emerge from consequences:

- marginal prices fall as markets saturate;
- farther customers cost more to serve;
- additional freight capacity must be built or purchased;
- input shortages may appear upstream;
- concentration creates disruption exposure;
- competitors and alternate suppliers remain viable in locations the player's network serves poorly;
- growth can increase capital, insurance, maintenance, and coordination demands.

> Success may reshape the economy; it should not delete geography.

## Supply chains rather than isolated sales

Player output may become an intermediate input for downstream settlement or player production.

Example:

```text
player ironworks
    |
    v
METALS supply
    |
    v
regional machinery works
    |
    v
MACHINERY supply
    |
    v
frontier construction / repair demand
```

Likewise, a player factory may depend on external upstream inputs:

```text
mining settlement
    |
    v
player refinery
    |
    v
player aircraft-parts plant
    |
    v
Guild / civilian aircraft market
```

This makes industrial specialization and logistics consequential.

## Production standards and item equivalence

Most ordinary commodity demand should accept semantically compatible goods rather than one exact recipe output.

Higher-value or institutional goods may require additional recognition, for example:

- standardized aircraft components;
- certified replacement parts;
- bonded relief cargo;
- safety-critical machinery.

The Guild may validate functional or specification classes where gameplay value justifies it. Avoid turning all manufacturing into bureaucracy.

## Contracts and free-market demand

Player production may earn Scrip through both:

- open market sales / standing buy demand; and
- explicit procurement or supply contracts.

Contracts can offer stronger terms, guaranteed quantity, advances, deadlines, or priority in return for commitment and risk.

A contract should not be required merely to sell ordinary goods into an existing market.

## Price response

Player supply should affect the same stock and demand state that drives NPC markets.

```text
shortage
  -> high price
  -> player/NPC supply arrives
  -> stock increases
  -> marginal price falls
```

Repeated dumping into the same market should therefore destroy its own margin naturally.

Prices should update on coarse economic reconciliation, not on every inserted item tick, unless immediate local interaction requires a quote refresh.

## Market absorption and contracts

Large demand may be represented through a combination of:

- ordinary market absorption;
- scheduled procurement;
- recurring industrial relationships;
- explicit large-volume contracts.

This permits a player factory to operate at scale without requiring every shipment to be hand-authored while still making very large transactions legible and bounded.

## Logistics requirement

Commercial demand does not teleport goods.

A player sale to a remote buyer creates or consumes a logistics obligation. Depending on the transaction, goods may be:

- collected by NPC/private freight;
- transported by Guild logistics;
- dispatched by player-owned automated freight;
- carried manually by the player.

The selected transport mode determines timing, cost, risk, and physical realization when observed.

## Player industrial influence

If a player becomes a major supplier, downstream civilization may react in coarse state:

- local prices stabilize or fall;
- shortages become less frequent;
- freight volume increases;
- route importance may increase;
- warehouses or service demand may grow;
- contracts may shift toward inputs, maintenance, or distribution rather than finished-goods procurement;
- settlements may become more economically connected to the player's facility.

These effects should be event-driven/coarse rather than continuously recomputed at block scale.

## Concentration risk

A world that begins depending on the player's facility may become vulnerable to its disruption.

If a major player supplier stops operating, loses a route, suffers piracy, or withdraws stock, the resulting shortage is legitimate systemic consequence.

The simulation need not model corporate strategy. It only needs to observe that expected supply disappeared and allow stock, price, route, and contract systems to react.

## Computational constraints

The design explicitly rejects:

- continuous scanning of arbitrary player storage;
- per-item global market simulation;
- continuous factory simulation while unloaded;
- continuous global re-optimization of supply chains;
- mandatory physical freight simulation while unobserved.

Prefer:

- coarse commodity aggregates;
- explicit registered economic interfaces;
- sparse logistics graphs;
- bounded candidate supplier sets;
- coarse or event-driven market clearing;
- cached information views;
- semantic freight while unobserved.

## Invariants

1. A good cannot simultaneously exist as authoritative semantic commercial stock and as freely usable physical inventory.
2. Player supply competes in the same economic system as NPC supply.
3. Remote demand never moves goods without a corresponding logistics operation.
4. Market saturation reduces marginal profitability naturally.
5. Geography, capacity, hazard, and reliability remain economically meaningful at every scale.
6. Industrial success may materially reshape regional civilization.
7. Skyforge must not preserve NPC relevance by arbitrary hidden caps on player economic share.
8. The civilization simulation consumes registered commercial state, not arbitrary factory internals.

## Recommended implementation posture

Begin with a small number of commodity classes, local/regional supplier candidate sets, bounded settlement demand, and coarse price response. Add richer substitution, supplier resilience, intermediate production chains, and programmable dispatch only after profiling demonstrates that the base model is stable and legible.

The intended experience is not a macroeconomic simulator. It is an engineering and logistics sandbox in which a player's factory becomes economically meaningful because the surrounding civilization can actually consume, transport, depend upon, and react to what that factory produces.
