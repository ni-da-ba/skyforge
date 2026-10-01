# CIV-0012: Guild insurance and incident evidence

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

Guild insurance should be a deliberately small risk-transfer system tied to registered assets, recognized vessel configurations, and actual incident state. It should soften catastrophic loss without removing meaningful consequences from failure.

The system should distinguish ordinary insurance from Guild restitution.

- **Insurance claim:** the Guild previously agreed to assume a defined risk.
- **Restitution:** the Guild caused, materially contributed to, or otherwise bears responsibility for the loss.

This distinction is especially important for the opening Bellanca incident.

## Core inputs

Insurance should derive from existing Guild and vessel state rather than from a separate simulation.

Relevant inputs may include:

- registered vessel identity;
- insured value;
- recognized / certified configuration;
- coverage class;
- deductible;
- intended operation class;
- incident state;
- cargo status;
- relevant Guild Standing;
- relevant financial Credit;
- available incident evidence.

## Roles of Scrip, Standing, Credit, and certification

- **Scrip** pays premiums, deductibles, uncovered loss, and related Guild services.
- **Certification** determines whether the Guild understands and recognizes the vessel configuration well enough to insure it for a given operation.
- **Standing** affects discretion, access to unusual coverage, tolerance for procedural errors, and other institutional trust decisions.
- **Credit** affects financing terms around insurance, such as payment plans, deductible deferral, or financing bundled with vessel purchase. Credit should not arbitrarily determine whether a valid paid claim is honored.

Standing and Credit therefore influence different dimensions of insurance.

## Coverage families

Initial insurance scope should remain small. Candidate coverage families:

1. **Vessel coverage** — damage or loss of a registered aircraft.
2. **Cargo coverage** — loss of insured or bonded cargo.
3. **Guild liability / restitution** — compensation for Guild-caused loss, tracked separately from ordinary insurance coverage.

Detailed personal-injury insurance, competing insurers, extensive rider systems, and broad financial simulation are deferred.

## Vessel coverage

A vessel policy should remain lightweight. A minimal semantic record may contain:

```text
VesselPolicy {
    vessel_id
    insured_value
    deductible
    certified_configuration
    coverage_class
    status
}
```

Premium and eligibility may primarily reflect:

- vessel value;
- recognized configuration;
- certification / operating class;
- intended operations;
- material risk factors;
- relevant loss history where useful.

Standard certified Guild aircraft should be comparatively easy to insure. Certified custom aircraft may remain insurable but carry greater uncertainty. Materially modified aircraft pending recertification may receive restricted or reduced coverage. Uncertified experimental aircraft need not receive ordinary Guild coverage at all.

## Claims should minimize administrative friction

Routine claims should exploit information the system already knows.

If the Guild already has authoritative state showing that a vessel is registered, insured, lost, and eligible for recovery or settlement, the player should not have to perform paperwork theatre.

A routine flow may be as small as:

**Report loss → evaluate incident → calculate claim → choose resolution**

Evidence gathering and extended dialogue should be reserved for genuinely disputed or exceptional cases.

Potential resolutions include:

- repair / recovery;
- equivalent replacement;
- Scrip settlement;
- retention of surviving salvage plus reduced settlement.

The retain-salvage option should be a general claims concept where applicable, not a one-off tutorial exception.

## Cargo insurance and bonded freight

Bonded Guild freight should not expose the player to save-ending financial loss merely because an aircraft is destroyed.

A bonded-cargo incident may separate:

1. the economic disposition of the cargo loss; and
2. the player's professional consequences.

Responsible loss during ordinary operations may be absorbed through insurance / bonding while still affecting contract outcome. Gross negligence, fraud, theft, or intentional destruction should be handled as misconduct rather than ordinary insured loss.

## Standing and discretion

Standing should primarily affect institutional discretion rather than create a flat premium multiplier.

High Standing may support:

- access to specialized or high-risk coverage;
- expedited claims;
- reasonable penalty waivers;
- tolerance for minor procedural deviations;
- provisional coverage arrangements;
- trusted incident reporting or inspection authority.

A highly trusted skyfarer who damages a craft while performing a legitimate emergency rescue may therefore receive different treatment from an unknown operator committing the same procedural violation for convenience.

## Credit and insurance financing

Credit should affect financing around insurance rather than claim validity.

High Credit may support:

- premium financing;
- insurance bundled into vessel financing;
- lower required upfront payment;
- deductible deferral;
- rapid replacement financing after a covered loss.

Poor Credit may require more Scrip upfront while leaving valid purchased coverage intact.

## Incident evidence and future Guild transponder / black box

The future Guild black box / transponder should be treated as a high-value authoritative evidence source for the craft on which it is installed.

It may eventually support or corroborate state such as:

- vessel identity and registration;
- recognized configuration;
- certification state;
- route / beacon interaction;
- flight telemetry;
- control / propulsion state where technically practical;
- incident timeline;
- distress state;
- last known position;
- cargo or bonded-operation metadata;
- loss / recovery lifecycle evidence;
- maintenance or modification records where useful.

The exact telemetry schema is deliberately deferred until the transponder / recorder system is designed against actual Aeronautics and Skyforge technical capabilities.

Important principle:

> The recorder should make disputes evidence-driven without becoming a requirement to simulate or permanently archive every craft variable at full fidelity.

It should capture the smallest set of authoritative facts needed to support registration, certification, claims, recovery, navigation accountability, and selected gameplay events.

The Bellanca opening remains the canonical example: routine evidence initially supports an unfavorable determination, while recorder evidence establishes that Guild navigation infrastructure materially caused the loss and therefore converts the case from an ordinary claim into Guild restitution.

## Failure consequences

Insurance should reduce catastrophic loss but should not make crashes meaningless. A covered incident may still impose:

- a deductible;
- repair / replacement friction;
- contract failure;
- cargo consequences;
- Standing consequences where negligence exists;
- loss of unregistered or uncovered modifications;
- temporary operational disruption.

Canonical principle:

> Failure creates consequences and decisions, not save-ending catastrophe.

## Precommitted rule

> **Guild insurance is a simple risk-transfer system tied to registered assets and recognized configurations. Routine claims resolve with minimal administrative friction. Scrip pays for coverage and losses; certification determines insurability; Standing affects discretion and special privileges; Credit affects financing terms rather than claim legitimacy. Guild restitution for Guild-caused losses remains distinct from ordinary insurance.**

The future Guild black box / transponder is expected to become a major authoritative evidence and telemetry interface for vessel registration, incidents, certification, recovery, and claims, but its exact implementation remains deferred.