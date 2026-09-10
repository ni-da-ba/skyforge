# CIV-0022 — Guild information UI and computer interface

## Status
Locked civilization precommit.

## Decision
The Guild membership/account interface is the player's primary human-facing window into Guild civilization state. The same curated information layer should also expose a machine-readable interface suitable for CC:Tweaked-style computers, allowing players to build their own accounting, logistics, market, fleet, and operational dashboards.

The machine interface must consume the same authoritative, permission-filtered, freshness-aware Guild information model as the player UI. It must not expose raw civilization internals, hidden future state, or omniscient global data.

## Human-facing information surface
The Guild interface may expose, at minimum:

- Account
  - Scrip balance
  - Credit state
  - debts / financing
  - membership status
- Standing
  - institutional standing
  - authorizations
- Contracts
- Markets
  - known Hall catalogues
  - current known quoted prices
  - shortages / surpluses where the Guild has information
- Network
  - known settlements
  - recognized routes
  - route advisories
  - navigation infrastructure
- Fleet
  - registered vessels
  - certification state
  - insurance state
  - recovery / incident records where relevant

## Information provenance and freshness
Guild information is not globally omniscient. Every datum that can become stale should carry enough provenance for the client to understand its quality.

Conceptual freshness states:

- LIVE
- RECENT
- STALE
- UNKNOWN

Exact freshness thresholds are implementation details and should depend on communications infrastructure, route connectivity, Guild presence, and the source of the information.

A well-connected Guild node may expose live or near-live regional information. A remote Hall may expose delayed market and route data. Undiscovered or non-networked settlements may expose nothing.

Information logistics should therefore create gameplay value. Players may act on fresher information, build communications infrastructure, or exploit information asymmetries in poorly connected regions.

## Machine-readable interface
The Guild should expose a stable, curated machine interface to compatible in-world computers. CC:Tweaked is the primary candidate environment, but Skyforge should define its own semantic API boundary so the integration remains replaceable.

Possible conceptual domains include:

- `account`
- `standing`
- `contracts`
- `markets`
- `network`
- `fleet`
- `incidents`

Illustrative calls only:

```text
guild.account.getBalance()
guild.account.getCreditSummary()
guild.markets.getQuote(nodeId, commodity)
guild.markets.listKnownCatalog(nodeId)
guild.network.listKnownRoutes()
guild.network.getRouteStatus(routeId)
guild.fleet.listRegisteredVessels()
guild.fleet.getCertification(vesselId)
guild.contracts.listAvailable()
```

Exact names and calling conventions are deferred.

## Security and scope
The computer interface inherits the same identity, membership, authorization, and information-availability rules as the ordinary Guild UI.

A computer does not gain broader access simply because it is programmable.

Machine access should require some credible Guild identity/authentication relationship, such as:

- a linked membership credential;
- an account-authorized Guild terminal/peripheral;
- a registered Guild agency installation;
- another explicit authentication mechanism chosen later.

The credential authenticates access to the account/network; it does not physically contain the player's Scrip balance.

## No raw simulation leakage
The machine interface must expose civilization products, not internal solver state.

Appropriate:

- current known price quote;
- quote timestamp/freshness;
- route status;
- known route hazard advisory;
- available contracts;
- registered fleet status.

Inappropriate:

- hidden exact settlement demand curves;
- undiscovered node coordinates;
- future shipment RNG outcomes;
- raw civilization field tensors;
- unrevealed incident causes;
- exact internal scoring weights.

Canonical rule:

> Computers may automate access to information the player is legitimately entitled to know; they do not create omniscience.

## Automation value
The interface should deliberately support player-created systems such as:

- market price dashboards;
- route profitability calculations;
- inventory/accounting ledgers;
- debt and cash-flow tracking;
- fleet-status boards;
- contract filtering and prioritization;
- logistics planning;
- custom alerts for price, route, or fleet changes;
- integration with player-built warehouses and Create machinery.

This is desirable because it lets technically inclined players build superior operational tooling without requiring Skyforge to author every possible dashboard.

## Computational constraint
The computer interface should be query/event driven and backed by cached Guild/civilization adapter products. It must not cause expensive global recomputation whenever a computer polls a value.

Recommended approach:

```text
civilization state
    ↓ coarse/event-driven update
Guild information cache / adapter products
    ↓
┌──────────────┬──────────────────┐
│ player UI    │ machine API      │
└──────────────┴──────────────────┘
```

Polling should read existing authoritative data or bounded cached summaries. Expensive derived results should refresh according to the underlying simulation cadence, not the computer's polling frequency.

Rate limiting or event subscriptions may be introduced if necessary for server performance.

## Design principles

> The Guild UI and machine interface are two clients of the same information layer.

> Information has provenance, latency, and value.

> Player automation should deepen logistics gameplay, not bypass discovery, trust, or communications constraints.

> Expose semantic business information; hide implementation internals.

## Deferred questions

- exact membership-card / Guild-terminal UX;
- CC:Tweaked peripheral or API implementation;
- whether computer access requires a physical Guild modem/peripheral;
- event subscriptions versus polling;
- remote communications range and relay mechanics;
- data-access permissions for player-operated Guild agencies;
- whether some high-value market data is fee-gated or Standing-gated;
- detailed information-freshness propagation through the Guild network.
