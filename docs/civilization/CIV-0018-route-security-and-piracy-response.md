# CIV-0018 — Route Security and Piracy Response

Status: precommit / design lock

## Decision

Freight security is modeled primarily as route-level economic and operational state. Physical escorts, convoys, attacks, and responses are realized only when player observation or gameplay requires them.

Every realized freighter must correspond to an authoritative semantic shipment. A physical freight encounter may modify that shipment, but may not manufacture cargo disconnected from the economy.

**Invariant:** one physical freighter = one semantic shipment; one shipment settles only once.

## Route-level security state

Routes may carry coarse state such as:

- traffic intensity
- cargo value / strategic importance
- hazard level
- piracy pressure
- security presence
- reliability
- travel time / throughput

These values influence both semantic freight outcomes and the form of any physical realization.

## Guild response to piracy

The Guild is not a sovereign police force. Its first response to piracy should use the commercial and institutional powers it actually controls:

- reroute or delay traffic
- consolidate shipments into convoys
- increase or contract escort coverage
- issue warnings / route advisories
- restrict bonded freight on unsafe corridors
- increase security requirements for certain shipments
- alter insurance / underwriting decisions where player-relevant
- request support from settlement authorities
- issue recovery, escort, investigation, or protection contracts

Physical patrol and escort entities are created only where player observation or direct gameplay makes them relevant.

## Player piracy

Player piracy is an optional systemic behavior rather than a separate mode. Attacking a realized freighter interrupts its corresponding semantic shipment and can produce real cargo loss, route disruption, and economic effects.

Attacks against Guild-owned or Guild-bonded freight are attacks against Guild interests and may cause severe Standing consequences if attributable. Attacks against private civilian freight may also affect Guild relationships where the Guild has registry, insurance, freight, or contractual interests, but criminal-law consequences remain with relevant sovereign authorities.

Avoid a universal omniscient wanted level. Attribution should depend on available evidence such as registered/transponder identity, distress records, witnesses, recovered wreckage, docking records, or identifiable bonded cargo.

The future Guild transponder / black-box system should support evidence and accountability without requiring omniscience.

## Adaptive economic response

Persistent predation should change the economy being preyed upon. Repeated losses may reduce traffic, increase protection, reroute valuable shipments, worsen shortages, or temporarily suspend service.

This provides a systemic self-balancing mechanism: piracy cannot remain an infinitely renewable stream of identical victims on a static route.

## Guild security vs sovereign security

The Guild may plausibly maintain limited protective capabilities, including:

- guards for Guild facilities
- bonded-cargo escorts
- rescue / security craft
- investigators
- contracted skyfarers
- emergency-response personnel

These protect Guild property, contracts, and network operations. Governments and settlement authorities remain responsible for general criminal law and public policing.

## Authorized seizure

High-Standing players may later receive scoped authorizations to intercept or recover specific assets, such as stolen Guild property, defaulted financed vessels, pirate vessels, or disputed commercial property. Mechanically this can resemble piracy while remaining institutionally distinct.

Delegated authority remains narrow and explicitly scoped.

## Computational constraint

The model must remain semantic by default. Distant piracy/security activity must never require continuous Aeronautics physics, persistent patrol simulation, or detailed NPC combat. Physical realization is reserved for nearby, observed, or directly relevant encounters.
