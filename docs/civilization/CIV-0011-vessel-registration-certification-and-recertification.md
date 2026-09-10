# CIV-0011: Vessel registration, certification, and recertification

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

Skyfarer's Guild aviation administration should distinguish three concepts:

- **Registration** establishes vessel identity, ownership, and record linkage.
- **Certification** establishes the operations for which the Guild is willing to recognize and stand behind a vessel configuration.
- **License / authorization** establishes what the operator may do within the Guild network.

These concepts should create engineering and institutional gameplay without requiring bureaucratic busywork.

## Registration

Registration should be cheap, routine, and mechanically useful rather than tedious. A registered vessel receives a persistent identity that may link to:

- owner / responsible party;
- vessel class or design reference;
- insurance policy;
- financing or liens;
- flight recorder / incident records;
- recovery state and safe snapshots;
- contract eligibility;
- certification status.

Registration is the identity layer beneath insurance, recovery, finance, and Guild-recognized operations.

## Standard Guild aircraft

Guild-standard aircraft families, including the Bellanca B0-A, should benefit from pre-certified baseline designs.

A stock or recognized approved configuration should therefore require little more than ordinary registration, inspection where appropriate, and insurance setup before Guild-recognized operation.

This is one of the principal practical benefits of Guild standardization.

## Certification applies to configuration

A Guild aircraft is not permanently certified merely because its model name was once approved. Certification applies to the aircraft's **recognized configuration**.

Changes should be classified approximately as follows:

| Change | Guild treatment |
| --- | --- |
| Cosmetic blocks, furnishings, paint, minor storage | No certification effect |
| Replacement with approved equivalent component | No effect or simple record update |
| Guild-approved modification package | Certification remains valid for the approved configuration; installation may require verification |
| Material change to propulsion, lift, controls, mass distribution, structure, cargo restraint, or other safety-critical systems | Recertification required |
| Extensive redesign | Custom / experimental certification pathway |

The exact technical thresholds remain an implementation-stage problem.

## Recertification state

Material modification should not necessarily make a vessel illegal or physically unusable. A useful state model is:

**CERTIFIED → MODIFIED / RECERTIFICATION DUE → CERTIFIED**

While recertification is due, private operation may remain possible, but Guild privileges tied to the former certified configuration may be restricted. Examples include:

- bonded freight;
- sensitive contracts;
- certain insurance coverage;
- favorable financing;
- use of specialized Guild facilities;
- representation of the vessel as Guild-certified for the previous operating class.

## Custom aircraft

Player-built aircraft remain fully valid. Guild certification should assess functional capability rather than demand blueprint identity.

A custom aircraft may be registered as experimental / custom and later demonstrate suitability through inspection and proving activity. Candidate functional concerns include:

- controllability;
- adequate propulsion / lift;
- safe mass and balance behavior;
- control interfaces;
- transponder / registration identification;
- flight recorder requirements for commercial operations;
- cargo restraint for bonded freight;
- appropriate emergency / recovery equipment for higher-risk classes.

Certification should produce gameplay through building, testing, inspection, and proving flights rather than menu paperwork.

## Certification scope

Certification should not be a single binary global status. A vessel may be acceptable for one operating role and unsuitable for another.

Conceptual scopes may include:

- personal / light operation;
- commercial operation;
- freight;
- heavy / special operation.

The exact classes should remain small and implementation-driven.

## Guild jurisdiction

The Guild does not own the sky. An uncertified or modified aircraft should not become magically unflyable.

Guild certification instead controls the institution's willingness to extend trust through:

- insurance;
- financing;
- bonded cargo;
- Guild contracts;
- specialized facilities;
- certification claims and institutional backing.

Local sovereign settlements may impose their own laws independently.

Canonical rule:

> Guild certification constrains access to Guild trust, insurance, finance, contracts, and facilities — not the player's fundamental ability to build and fly whatever they want.

## Operator licenses and authorizations

Ordinary personal operation should require minimal administrative friction. Specialized commercial activities should preferentially use the authorization model established elsewhere rather than proliferating many mandatory license items.

Candidate authorizations include:

- commercial freight;
- bonded freight;
- salvage / recovery;
- inspection;
- heavy / special vessel operation;
- Guild agency operation.

Scrip may pay fees, dues, inspections, bonds, or renewals. Standing controls institutional trust. Credit controls financial terms.

## Renewals

Routine expiration should not create repetitive clerical chores. Ordinary credentials should remain valid unless a meaningful condition changes, such as:

- major vessel modification;
- suspension or serious violation;
- loss of membership;
- change of operating class;
- an intentionally long and infrequent renewal interval.

## High-standing progression

Appropriate high-Standing authorizations may eventually allow the player to inspect or sign off certain modification classes themselves, reducing institutional friction as demonstrated expertise grows.

## Precommitted rules

> Registration establishes vessel identity and ownership. Certification establishes the operations for which the Guild is willing to recognize and stand behind a vessel configuration. Licenses and authorizations establish what the operator may do within the Guild network.

> Standard Guild designs minimize certification friction; material modification can require recertification, and substantial deviation enters the custom certification pathway.

> Certification should create engineering gameplay, not paperwork simulation.