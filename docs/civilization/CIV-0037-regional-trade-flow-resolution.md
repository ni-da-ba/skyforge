# CIV-0037 — Regional Trade-Flow Resolution

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Core decision

> Regional commerce resolves bounded procurement needs against exportable supply over the existing sparse route network. Supplier selection considers generalized delivered cost, reliability, capacity, and established commercial relationships rather than source price alone.

Skyforge must support coherent trade circulation without introducing a world-scale continuous market-clearing or optimal-transport solver.

## Procurement and export pressure

Activated markets derive procurement need and exportable supply from their existing economic state.

Conceptually:

```text
projected stock
    = current stock
    + expected local production
    + committed arrivals
    - expected consumption
```

A projected deficit below desired-stock bands creates procurement pressure.

A projected surplus above useful local/reserve requirements creates exportable supply.

The trade solver consumes these states; it does not invent decorative freight independently of them.

## Commercial relationship inertia

Established supplier relationships receive first consideration when they remain reasonably viable.

Trade relationships should not oscillate because another source is marginally cheaper for one update.

Preferred behavior:

```text
existing supplier relationships
        ↓
can they satisfy demand at acceptable generalized cost/reliability?
        ↓
yes → preserve a meaningful share of established flow
        ↓
remaining unmet demand or materially inferior incumbent
        ↓
search bounded alternatives
```

> Trade changes more readily than infrastructure, but less readily than prices.

This complements route-formation hysteresis and gives commercial history persistence without individual merchant simulation.

## Bounded candidate search

Do not inspect every supplier in the world for every procurement need.

Candidate suppliers should come from a bounded route-network neighborhood such as:

- established suppliers;
- directly connected commercial nodes;
- nearby/regional hubs;
- a small number of route-reachable suppliers within bounded path depth or generalized cost;
- eligible player commercial suppliers publishing committed stock/offers.

The existing route topology owns connectivity and path geometry. The economic layer must not create a duplicate geographic graph.

## Generalized delivered cost

Supplier choice must evaluate delivered economic value rather than source price alone.

Relevant factors may include:

```text
source price
+ transport cost
+ handling / transshipment
+ expected hazard loss
+ delay penalty
+ reliability penalty
+ congestion / capacity pressure
```

The exact implementation formula remains an executable-design decision.

The invariant is that a cheap source on a dangerous, unreliable, or saturated route is not automatically superior to a slightly more expensive dependable source.

## Capacity constraints

Trade allocation is bounded simultaneously by:

```text
supplier exportable stock / productive capacity
buyer procurement need
route/path residual throughput
```

A player or NPC supplier may dominate local production without automatically satisfying unbounded regional demand if its transport interfaces and routes cannot carry the output.

This makes logistics capacity an economically meaningful constraint rather than imposing arbitrary production caps.

## Route capacity is semantic throughput

NPC route capacity is aggregate freight throughput, not a count of continuously simulated aircraft.

A route may resolve substantial economic flow during an interval while only a small number of representative vehicles are ever physically realized.

Concrete shipment or aircraft state is required only where previous CIV contracts demand it, for example:

- player-owned vessels;
- player-interceptable traffic;
- contract-specific freight;
- incidents;
- custody/bonding/evidence;
- nearby physical realization.

## Deterministic incremental allocation

The implementation may use a deterministic bounded allocation procedure rather than global optimization.

Conceptually:

```text
determine procurement need
        ↓
reserve reasonable incumbent supply
        ↓
rank bounded alternatives
        ↓
allocate until:
    demand satisfied
    suppliers exhausted
    route/path capacity exhausted
    or no viable candidate remains
```

Equivalent authoritative state must produce equivalent allocation results.

The exact ranking/tie-break algorithm is deferred to implementation/specification work.

## Unmet demand is valid

The solver must be permitted to leave procurement unresolved.

```text
need: 100
deliverable: 63
unmet: 37
```

Unmet demand feeds existing systems:

```text
stock pressure
→ price pressure
→ procurement/freight opportunities
→ route-capacity value
→ player opportunity
```

Skyforge must not fabricate inventory or suppliers solely to force every local balance to close.

## Multi-hop trade

Multi-hop routes and transshipment are allowed where the shared route grammar supports them.

A supply relationship may be represented economically as:

```text
supplier
→ recognized route/path
→ consumer
```

without creating persistent shipment entities at every intermediate hop for aggregate NPC commerce.

Detailed custody is instantiated only when gameplay, contracts, incidents, player ownership, or physical realization require it.

## Communications and information quality

Commercial decisions may consume Guild information records carrying source, timestamp, provenance, and freshness.

Stale or incomplete information may produce delayed or less efficient sourcing decisions and may increase dependence on established supplier relationships.

However:

> Information quality may affect decisions; it may not override authoritative inventory, capacity, or conservation constraints.

A stale quote cannot cause nonexistent goods to become available.

## Player suppliers

Player commercial facilities participate through the same supplier interface as NPC commercial nodes.

Eligibility may require:

- committed commercial stock;
- published eligible offer/availability;
- recognized logistics interface;
- route connectivity;
- available freight throughput;
- required Guild/institutional permissions where applicable.

Player suppliers receive no special market-clearing rule.

Their competitive position emerges from price, reliability, logistics, capacity, information, and geography.

## Price causality

Trade allocation does not directly solve or dictate local market price.

Preferred causal order:

```text
trade allocation
    ↓
goods depart / arrive
    ↓
local stocks and expectations change
    ↓
local market-price model reconciles
```

This preserves the authority of the local market/catalogue/price system defined in prior CIV precommits.

## Latent-region compatibility

Wholly latent regions may carry broad import/export and flow pressure rather than exact shipment accounting.

When activated economics interacts with latent civilization, consequential external supply or unmet-demand effects may be recorded compactly according to CIV-0033 through CIV-0035 rather than activating every downstream cluster.

## Computational constraints

Do not require:

- all-pairs settlement supplier search;
- world-scale optimal transport;
- continuous market clearing;
- one persistent vehicle per aggregate freight flow;
- per-tick rerouting;
- duplicate route topology.

Prefer:

```text
bounded candidate sets
+ established relationship inertia
+ deterministic incremental allocation
+ aggregate route throughput
+ lazy/coarse reconciliation
```

## Design invariants

> Regional trade follows the existing sparse route network.

> Supplier choice is based on generalized delivered value, not source price alone.

> Inventory, supplier capacity, and route capacity are real constraints.

> Unmet demand is a valid and useful economic state.

> NPC economic throughput is not equivalent to continuously simulated vehicle count.

> Player commercial suppliers compete through the same economic interface as civilization suppliers.

> Flow changes stocks; stocks and expectations drive local prices.
