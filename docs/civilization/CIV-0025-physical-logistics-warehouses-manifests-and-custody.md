# CIV-0025 — Physical Logistics, Warehouses, Manifests, and Custody

Status: precommit design lock

## Decision

Skyforge separates coarse economic inventory from physical Minecraft custody.

Semantic inventory is authoritative while goods remain abstract. Physical Minecraft items become authoritative when goods enter player-observable or player-controlled custody. Goods reconcile back into semantic stock only through explicit authorized receiving transactions.

No quantity may simultaneously exist as both authoritative semantic stock and authoritative physical cargo.

## Core transition

```text
SETTLEMENT / MARKET STOCK
semantic, coarse
        |
        | withdrawal / purchase / loading / theft / inspection
        v
PHYSICAL CARGO
Minecraft items / inventories / containers
        |
        | verified delivery / sale / authorized warehouse intake
        v
SETTLEMENT / MARKET STOCK
semantic, coarse
```

The transition boundary, not continuous world materialization, is the authoritative accounting point.

## Warehouse realization

Guild and settlement warehouses may visually contain crates, vaults, belts, shelves, machinery, and representative stock, but those blocks and item stacks do not need to equal the settlement's entire economic inventory one-for-one.

Rendered warehouse contents represent economic activity; they do not numerically constitute all settlement stock.

A settlement may own thousands of semantic commodity units while only the currently loaded, reserved, inspected, or player-relevant subset is physically instantiated.

## Withdrawal / pickup

When a player, observed vessel, or authorized logistics system takes custody of goods:

1. validate the reservation, purchase, contract, or shipment operation;
2. atomically decrement or reserve the relevant semantic stock;
3. instantiate the corresponding Minecraft goods at an authorized loading boundary;
4. transfer custody to the physical inventory, container, or vessel;
5. record the associated shipment/manifest state where required.

This must be anti-duplication safe: failed physical realization must not leave both the original semantic quantity and a usable physical copy.

## Delivery / intake

When physical goods are delivered into an authorized economic receiver:

1. identify the applicable market, shipment, contract, or warehouse transaction;
2. validate item identity, quantity, cargo class, and manifest conditions where relevant;
3. consume or transfer the physical goods from authoritative physical custody;
4. increment destination semantic stock only after successful validation;
5. settle payment, contract completion, standing effects, or other institutional consequences.

Ordinary private chests do not automatically become economic receivers.

## Cargo identity proportional to gameplay value

Do not globally serialize forensic identity onto every commodity item.

Use progressively stronger identity only where identity creates gameplay:

- Ordinary goods: item/commodity class + quantity.
- Commercial shipment: shipment manifest + expected contents and custody.
- Bonded/sensitive cargo: persistent shipment or container identity, custody, tamper state, and relevant evidence.

High-value, bonded, recoverable, insured, financed, or institutionally sensitive cargo may justify persistent identity. Bulk ordinary materials usually do not.

## Shipment manifest

Meaningful freight operations use a compact semantic manifest that may include:

```text
ShipmentManifest
    shipment_id
    owner
    carrier
    origin
    destination
    cargo / commodity
    quantity
    declared / insured value where relevant
    bonded_state
    custody
    vessel_id
    contract_id? 
    reservation / order linkage?
```

The manifest is the bridge among markets, contracts, piracy, freight, insurance, recovery, and physical vessels.

It answers the institutional questions that matter without requiring detailed simulation of every item throughout transit.

## Bonded cargo

Bonded or sensitive cargo is tamper-evident, not magically inaccessible.

A player may physically open, break, steal, divert, or destroy bonded cargo if the underlying Minecraft mechanics permit it. Such actions change authoritative state, for example:

```text
SEALED -> TAMPERED
IN_CUSTODY -> MISDELIVERED / STOLEN / LOST
```

Consequences then arise through contracts, standing, ownership, claims, law, and evidence rather than invisible interaction barriers.

Institutional rules should constrain behavior through consequences, not arbitrary physical impossibility.

## Cargo interfaces

Guild-compatible warehouses, docks, vessels, and player facilities communicate through standardized cargo boundaries rather than blueprint conformity.

A certified commercial vessel may expose one or more recognized loading/unloading interfaces. Guild and settlement facilities expose compatible receiving/loading interfaces.

Minecraft realization may use Create logistics, Aeronautics docking systems, inventories, vaults, funnels, pipes, containers, or future compatible mechanisms, but the semantic contract belongs to Skyforge rather than to any one mod block.

## Certification relationship

Commercial certification may require functional capabilities such as:

- recognized vessel identity;
- suitable cargo storage;
- compatible transfer interface;
- required transponder/recorder equipment;
- cargo restraint or secure storage where appropriate.

Bonded freight may add:

- bonded-capable storage;
- seal/tamper evidence;
- manifest association;
- stricter custody requirements.

Certification validates capability, not exact hull or blueprint conformity.

## Containers

Standardized physical cargo containers are permitted and may become especially useful for bonded, high-value, high-volume, crane-handled, intermodal, salvage, or piracy gameplay.

They are not required for every commodity or for the initial economy implementation.

## Player storage and registered commercial storage

Ordinary player storage remains ordinary Minecraft inventory and does not automatically participate in Guild economic accounting.

A player may designate explicit registered commercial storage / warehouse interfaces. Those interfaces may expose economic state such as:

- available stock;
- reserved stock;
- incoming shipments;
- outgoing shipments;
- cargo capacity;
- loading interfaces;
- authorized commodity classes.

Only designated economic boundaries should be queried by the Guild adapter.

## Performance rule

Skyforge must not continuously scan arbitrary player factories, chest networks, or whole settlements to infer economic inventory.

Economic participation occurs through explicit interfaces and transaction boundaries.

Likewise, settlement semantic stock should not be materialized continuously merely to preserve the fiction of warehouses containing goods.

## Automation

Create and ComputerCraft-style automation may operate the physical side of the economic boundary:

```text
incoming shipment
    -> dock
    -> manifest recognized
    -> physical unload
    -> authorized receipt validation
    -> semantic settlement / payment
```

and:

```text
order / contract
    -> goods allocated
    -> physical cargo instantiated at authorized pickup
    -> Create loads vessel
    -> manifest attached
    -> automated vessel departs
```

Automation does not bypass stock ownership, physical goods movement, manifests, cargo capacity, route risk, or settlement accounting.

## Invariants

1. No authoritative good exists simultaneously in semantic and physical form.
2. Materialization/dematerialization occurs only through explicit transaction boundaries.
3. Ordinary world inventories are not silently scanned into the Guild economy.
4. Cargo identity is added only where ownership, bonding, evidence, recovery, or other gameplay justifies it.
5. Bonded cargo remains physically violable; institutions respond to violations after the fact.
6. Mod-specific logistics implementations sit below Skyforge's semantic cargo contract.
7. Rendered warehouse stock is representative, not necessarily numerically complete.

## Canonical principles

> Semantic inventory is authoritative while goods are abstract; physical inventory becomes authoritative while goods are in player-observable custody.

> Simulate and identify cargo only to the level at which that identity creates gameplay.

> Certification validates capability, not blueprint conformity.

> Institutions constrain behavior through consequences, not invisible walls.
