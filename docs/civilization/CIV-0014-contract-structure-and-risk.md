# CIV-0014: Guild contract structure and risk

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

Guild contracts should be treated as legal and economic relationships with the world, not isolated quest instances or simple difficulty tiers.

A contract should be representable with a compact set of properties such as:

- issuer / provenance;
- objective;
- payment;
- required authorization;
- operational hazard;
- Guild or client asset exposure;
- bond / collateral where relevant;
- insurance / liability terms;
- advance where relevant;
- completion conditions.

The player-facing UI may summarize these properties without exposing all underlying simulation state.

## Contract classes

Avoid a single Rank I-V ladder. Use orthogonal qualitative classes that may overlap:

- **Routine** — ordinary commercial or service work available to normal members;
- **Bonded** — Guild or client property is entrusted to the player;
- **Hazardous** — meaningful operational danger, difficult environment, rescue, combat, or equivalent risk;
- **Discretionary / Sensitive** — restricted work involving unusual authority, confidentiality, institutional exposure, or political ambiguity.

A contract may be both Bonded and Hazardous, or otherwise combine traits.

## Routine contracts

Routine work forms the economic backbone and may include:

- freight;
- procurement;
- deliveries;
- survey;
- repair;
- construction;
- ordinary salvage.

Routine contracts should continue to derive from actual settlement or route conditions where practical.

## Bonded contracts

Bonded work means the Guild or a client places valuable property in the player's custody.

Examples include:

- expensive machinery;
- sealed cargo;
- relief supplies;
- financed or entrusted aircraft;
- critical components;
- other Guild-owned or client-owned assets.

Eligibility may combine:

- appropriate vessel certification;
- relevant operational authorization;
- sufficient Standing;
- Credit-dependent bond or collateral requirements.

Canonical distinction:

> Standing answers whether the Guild trusts the player with the asset. Credit answers how much financial security the Guild requires around that trust.

## Hazardous contracts

Hazard should reflect actual world conditions rather than an arbitrary quest difficulty tag.

Candidate causes include:

- severe weather;
- dragon territory;
- hostile factions such as illagers;
- unstable islands or structures;
- difficult navigation;
- emergency rescue;
- hazardous salvage;
- damaged infrastructure.

Higher Standing may unlock more consequential hazardous work because the Guild has evidence that the player can operate reliably under those conditions.

## Discretionary and sensitive work

High-Standing players may receive work that is not posted publicly.

Examples may include:

- investigation of suspected theft, fraud, or misconduct;
- protection of Guild assets without public escalation;
- recovery of politically disputed property;
- confidential inspection of another chapter;
- negotiation with an independent settlement;
- movement of sensitive cargo;
- intervention under narrowly delegated authority;
- other chapter-level work that the Guild does not wish to expose on a public board.

These assignments should not imply that the Guild is secretly evil. They should expose gray areas created by institutional self-interest, delegated authority, local politics, and differences between chapters.

## Contract provenance

Preserve provenance as a first-class concept:

- **Guild Contract** — institutionally backed ordinary work;
- **Chapter Contract** — work issued under local chapter discretion;
- **Private Commission** — private party using Guild contract / escrow infrastructure;
- **Discretionary Commission** — restricted Guild work offered directly;
- **Illicit Contract** — work outside legitimate Guild authority.

The same physical objective can have very different legal and moral meaning depending on provenance.

## Failure and fault

Do not map every failed contract directly to a Standing loss.

Distinguish at least conceptually between:

- **good-faith failure** — no reward and perhaps lost expenses, but little or no Standing consequence;
- **negligence** — possible Standing loss, insurance effects, or financial liability;
- **breach** — significant institutional and financial consequences;
- **fraud / theft** — severe response and possible loss of authorization.

Future Guild black-box / transponder evidence may help determine whether losses resulted from unavoidable events, negligence, misconduct, or Guild infrastructure faults.

## Advances and bonds

Large or consequential contracts may use financial instruments such as:

- mobilization advances;
- performance bonds;
- escrow;
- collateral;
- staged payment.

These should be used selectively. Ordinary low-value contracts should remain frictionless.

Canonical rule:

> Depth where it creates decisions; not paperwork everywhere.

## Relationship to FTB Quests

FTB Quests is appropriate for onboarding, authored tutorial flow, major milestones, and explanatory content.

Recurring Guild contracts should not depend on FTB Quests as their semantic authority. They should ultimately derive from the civilization / Guild adapter so they can respond to real settlement, route, inventory, hazard, and institutional state.

## Precommitted principles

> Contracts are legal and economic relationships with the world, not isolated quests.

> Contract risk is multidimensional: operational hazard, custody exposure, institutional sensitivity, and financial exposure should remain separable.

> Standing should primarily expand the kinds of work and responsibility the Guild will entrust to the player, while Credit governs financial terms around that work.

Exact thresholds, payment formulas, hazard weights, authorization names, and contract-generation algorithms remain implementation-stage decisions.
