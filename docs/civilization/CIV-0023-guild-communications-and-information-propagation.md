# CIV-0023 — Guild Communications and Information Propagation

## Status
Locked precommit design direction.

## Purpose
Define how Guild information moves between settlements, Halls, agencies, player interfaces, and programmable computers without simulating a packet network or granting omniscient real-time knowledge.

## Core rule
Guild information is semantic state with provenance, permissions, and freshness. Communications infrastructure determines what a node can know and how recently it can know it.

The simulation should answer:

> What does this Guild node know, how recently did it learn it, and who is authorized to access it?

It should not simulate continuous radio packets, per-message propagation, or network traffic unless a later gameplay feature explicitly requires that detail.

## Information classes
The Guild information layer may expose or propagate information including:

- market catalogues, quotes, shortages, and surpluses;
- recognized routes and route advisories;
- settlement and Guild-node discovery/recognition state;
- navigation infrastructure status;
- contracts and contract availability;
- Guild operational notices;
- player account, Standing, Credit, membership, and authorization state;
- player fleet registration, certification, insurance, and recovery records;
- incident and distress information where the user has permission to receive it.

Not every node receives every class of information.

## Freshness model
Every remote datum should carry an observation/update time and a coarse freshness classification suitable for UI and machine use.

Initial conceptual classes:

- `LIVE` — effectively current for gameplay purposes;
- `RECENT` — recent enough for ordinary operational use;
- `STALE` — usable historical information, but potentially misleading for fast-moving decisions;
- `UNKNOWN` — unavailable or not yet received.

Exact thresholds are implementation/profile dependent and may differ by information type.

Freshness is not cosmetic. A stale market quote may support planning but should not imply that the remote store will still transact at that exact price when the player arrives.

## Communications topology
Communications should reuse the civilization/network graph rather than creating an unrelated global service.

A node may have communication relationships that differ from its freight relationships. For example:

- cargo may continue moving while a relay failure makes remote market information stale;
- communications may remain strong across a route temporarily degraded for physical freight;
- remote frontier nodes may depend on couriered or periodic synchronization;
- regional hubs may aggregate and redistribute Guild information.

Conceptually:

```text
Guild Hall / Agency
      |
communications relation
      |
relay / chapter / hub
      |
other Guild node
```

The implementation only needs coarse connectivity, latency/freshness behavior, and reliability. It does not need packet-level simulation.

## Information propagation
Authoritative civilization and Guild systems periodically publish derived snapshots into a Guild information cache.

Consumers read from the cache rather than forcing the economy, route resolver, or civilization model to recompute on demand.

```text
Skyforge civilization / Guild state
              |
       coarse publication
              v
      Guild information cache
        |                 |
        v                 v
 membership UI      computer peripheral
```

This is a hard performance boundary: repeated UI refreshes or ComputerCraft polling must not drive additional civilization simulation.

## Provenance
Remote information should identify its source where useful. Examples include:

- originating Guild Hall;
- chapter office;
- route beacon or relay;
- vessel transponder/recorder;
- settlement market;
- player-authorized agency.

This allows later systems to distinguish official Guild information from rumor, local reports, independent settlement data, or player-produced analytics without changing the underlying data model.

## Permissions and Standing
Information access is not necessarily universal.

Public/basic membership information may include ordinary routes, local markets, and routine contracts.

Higher Standing or specific authorizations may expose additional information such as:

- bonded-freight notices;
- sensitive route advisories;
- restricted recovery records;
- Guild-property distress signals;
- discretionary commissions;
- agency operational data.

Permissions should be capability-based rather than rank-number based, consistent with CIV-0010.

## Membership UI
The Guild membership interface is the default human-facing client for the information layer. It may provide:

- Account;
- Standing and authorizations;
- Contracts;
- Markets;
- Network;
- Fleet.

Remote entries must display freshness/provenance where those materially affect interpretation.

The membership credential authenticates the player. It does not physically store Scrip or authoritative account state.

## ComputerCraft / programmable interface
The same Guild information layer should be consumable through a machine-readable Guild terminal/peripheral for CC:Tweaked-style automation.

The peripheral is a client of the same permissions, provenance, and freshness rules as the normal UI. It must not expose hidden simulation internals or superior omniscient data.

A computer may be able to read data such as:

- Scrip balance and debt state;
- Standing and authorizations;
- available contracts;
- market catalogues/quotes and freshness;
- recognized routes and advisories;
- registered fleet status;
- incident/recovery notices where authorized.

Exact API method names remain an implementation decision.

## Read-first automation policy
Initial programmable access should be primarily observational.

Powerful read access is desirable because it allows players to build:

- market monitors;
- logistics dashboards;
- fleet status boards;
- arbitrage/accounting tools;
- route-risk monitors;
- warehouse/planning systems integrated with Create and CC:Tweaked.

State-changing operations should be added selectively.

A remote machine action must never teleport physical goods or bypass actual freight. A future remote purchase order, for example, may reserve goods and create a shipment obligation, but delivery remains physical/semantic logistics.

## Information infrastructure as gameplay
Communications quality may be improved or degraded by civilization and player actions.

Possible later effects include:

- damaged relay -> information becomes stale;
- repaired relay -> freshness improves;
- new Guild agency -> new information node;
- player-built relay -> stronger regional coverage if authorized;
- isolated settlement -> periodic rather than live synchronization.

This should remain coarse and event-driven.

## Performance constraints
Do not:

- run packet-level simulations;
- recompute civilization state on API/UI polling;
- create continuously ticking communications entities for every route;
- expose raw internal simulation objects to player scripts;
- treat stale information as guaranteed current transaction state.

Prefer:

- cached derived snapshots;
- coarse/event-driven propagation;
- explicit freshness metadata;
- capability-gated access;
- shared human/machine data contracts.

## Locked principles

> Guild information is semantic state with provenance, permissions, and freshness.

> The information layer is derived from authoritative civilization/Guild state and cached; consumers do not drive simulation recomputation.

> Human UI and programmable computers consume the same information contract and therefore see the same world, subject to the same access and freshness limitations.

> Information logistics may differ from physical freight logistics, allowing damaged relays, isolated settlements, and player-built infrastructure to create meaningful information asymmetry.

> Programmable access should enable player-authored accounting and logistics systems without bypassing physical goods movement or exposing hidden simulation internals.
