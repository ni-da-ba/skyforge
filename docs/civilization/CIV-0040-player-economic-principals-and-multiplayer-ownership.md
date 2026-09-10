# CIV-0040 — Player Economic Principals and Multiplayer Ownership

Status: PRECOMMIT / LOCKED FOR CIVILIZATION DESIGN

## Core decision

> Economically meaningful assets belong to an explicit economic principal. The ordinary default principal is an individual player; optional player organizations provide shared ownership and authorization for multiplayer logistics without being required for progression.

The ownership model exists to support Guild accounts, commercial facilities, vessels, contracts, loans, shipments, programmable access, and multiplayer logistics. It must not become a general block-ownership or corporate-governance simulator.

## Economic principals

Candidate principal classes:

```text
PLAYER
PLAYER_ORGANIZATION
NPC_OR_CIVILIAN_ENTITY where individual identity is gameplay-relevant
GUILD
FACTION_OR_POLITY where ownership/obligation actually matters
```

Most ordinary player-facing commerce only requires `PLAYER` and optional `PLAYER_ORGANIZATION`.

A player may operate a large commercial network under their personal principal. Creating an organization is never a progression requirement.

Canonical rule:

> A company is an organizational convenience, not a progression tier.

## Assets that may carry economic ownership

Ownership metadata should be limited to institutional assets such as:

```text
Guild / commercial account
registered vessel
registered commercial facility
commercial stock
shipment / manifest
contract / obligation
loan / financed asset
published offer or reservation
registered automation/logistics asset where needed
```

Ordinary terrain, decorative blocks, walls, roads, fences, and arbitrary containers do not need persistent economic ownership metadata merely because they are physically near a registered facility.

## Explicit commercial boundary

A base remains ordinary Minecraft until the player explicitly registers economically meaningful interfaces.

Economic ownership begins at explicit transactions and registered boundaries, not by scanning arbitrary storage or inferring ownership from block proximity.

Example:

```text
physical player goods
    ↓ explicit deposit
REGISTERED COMMERCIAL INTERFACE
principal = Blackbird Aero
    ↓
semantic commercial stock
owner = Blackbird Aero
```

Where goods move between personal and organization custody, the transfer must be explicit enough to determine which principal owns the resulting commercial stock.

## Optional player organizations

A multiplayer organization may own or control:

- Scrip accounts;
- commercial stock;
- registered facilities;
- vessels;
- loans/liabilities;
- contracts;
- routes/dispatch policies where appropriate.

Do not require deep office hierarchies.

Prefer bounded action permissions such as:

```text
MANAGE_FACILITY
MANAGE_FLEET
DISPATCH_FREIGHT
TRADE
ACCEPT_CONTRACT
MANAGE_FUNDS
MANAGE_MEMBERS
```

Presentation may later expose convenient named roles, but authorization semantics should remain capability-based.

The same authorization state should govern Guild UI, registered commercial interfaces, and CC/programmatic access where applicable rather than inventing parallel permission systems.

## Personal professional standing

Guild Standing and professional/operator authorizations remain personal.

Organization membership must not allow an otherwise unauthorized player to borrow another member's pilot, bonded-freight, recovery, inspection, or other professional authorization.

Example:

```text
organization owns certified heavy freighter

operator A: heavy-freight authorization = YES
operator B: heavy-freight authorization = NO
```

Operator B may perform actions their organization permissions and personal qualifications allow, but cannot satisfy a Guild-regulated personal authorization merely through shared ownership.

## Scrip and Credit

Scrip accounts may belong to either an individual or an organization.

Financial Credit belongs to the actual borrowing principal.

Examples:

```text
personal aircraft loan
→ personal credit history

organization freight-aircraft financing
→ organization credit history
```

This preserves the existing separation:

```text
Scrip    = spendable purchasing power
Standing = professional/institutional trust
Credit   = debtor financial reliability
```

## Contracts and liable principals

Contracts should distinguish the liable economic principal from the individual operators who perform regulated work.

Example:

```text
bonded freight contract
liable principal: Blackbird Aero
pilot/operator: Player A
```

The principal receives payment and bears the commercial obligation. Personal misconduct or professional failure may still affect the relevant operator's Standing where justified.

Do not attempt to model every real-world corporate-liability edge case.

## Facilities and shared operation

A registered facility may identify an owning principal and a bounded authorization set for users.

Example:

```text
facility owner: Blackbird Aero
Player A: MANAGE_FACILITY, TRADE, DISPATCH_FREIGHT
Player B: DISPATCH_FREIGHT
Player C: MANAGE_FUNDS
```

This supports shared factories, warehouses, agencies, terminals, and fleet dispatch without requiring a general player-claim system.

## Ownership is not physical protection

Economic ownership must not silently become universal Minecraft invulnerability.

A vessel, warehouse, or shipment may have a legitimate owner while another actor can physically damage, enter, seize, steal, or dismantle it if ordinary gameplay permits.

Physical possession/control and institutional ownership are distinct.

Example:

```text
registered owner: Civilian Merchant
current controller: Player
provenance: CIVILIAN
legal status: STOLEN_OR_DISPUTED
```

The same distinction supports piracy, salvage, capture, disputed title, and recovery gameplay.

Server owners may layer independent claim/protection systems on top; Skyforge's economic model should not require them.

## Transfers and title

Legitimate ownership transfer of a registered vessel, facility, or other titled asset should be explicit.

Physical possession alone does not automatically transfer Guild-recognized title or registration.

A sale/gift may therefore perform:

```text
seller principal
    ↓ explicit transfer
buyer principal
    ↓ registry/account update where applicable
```

A stolen/captured asset may remain legally associated with its prior owner until sale, salvage adjudication, restitution, forfeiture, or another recognized process resolves title.

## Allegiance remains separate from ownership

CIV-0039 distinguishes allegiance, provenance, jurisdiction, Guild relationship, access, and legal status.

This decision does not collapse those dimensions into ownership.

Example:

```text
owner: Blackbird Aero
allegiance: PLAYER_INDEPENDENT
Guild status: REGISTERED
jurisdiction: Aster Republic
```

A player organization holding a delegated Guild agency charter does not thereby become the Guild for all political or legal purposes.

## Out of scope

Do not build baseline systems for:

- incorporation paperwork;
- shares/equity;
- salaries/payroll;
- employment-contract simulation;
- corporate taxation;
- shareholder governance;
- organizational politics;
- deep management trees.

The system requires shared economic agency, not a corporate administration game.

## Invariants

> A company is an organizational convenience, not a progression tier.

> Economic ownership begins at explicit registered interfaces and transactions, not arbitrary block proximity.

> Ownership, physical possession, allegiance, registration, and legal status remain distinct.

> Guild Standing and regulated operator qualifications remain personal.

> Organizations may own funds, facilities, vessels, commercial stock, contracts, and liabilities without granting every member every action.

> Economic ownership does not imply universal physical protection.
