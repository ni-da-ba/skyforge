# CIV-0009: Scrip, Standing, Credit, and Guild trust

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

The Skyfarer's Guild uses three separate player-facing institutional axes:

- **Guild Scrip** — actual purchasing power within the Guild network;
- **Guild Standing** — institutional trust, reputation, and demonstrated professional capability;
- **Guild Credit** — financial reliability and debt-repayment history.

These values are deliberately orthogonal. They answer different questions:

- **Scrip:** can the player pay for this?
- **Standing:** does the Guild trust the player to do or access this?
- **Credit:** will the Guild finance the player, and on what terms?

No single axis should collapse into a generic reputation level.

## 1. Guild Scrip

Scrip is money, not prestige.

It is required for most Guild goods and services, including as appropriate:

- insurance premiums;
- vessel purchases;
- supplies and components;
- certified equipment;
- repair and recovery services;
- docking, storage, and handling charges;
- license issue and renewal;
- Guild membership or dues;
- contract bonds and deposits;
- debt repayment.

Scrip should be accepted throughout the Guild network and earned through real economic activity such as contracts, trade, freight, salvage, and other recognized services.

Canonical rule:

> **Scrip buys access to Guild resources. It does not buy Guild trust.**

Local barter, ordinary Minecraft trade, and non-Guild commerce may coexist with Scrip. The Guild should not become a closed company-store economy.

## 2. Guild Standing

Standing measures how much faith the Guild places in the player as an operator, contractor, professional, and custodian of Guild interests.

It reflects demonstrated capability and conduct, not merely wealth or debt history.

Higher Standing may unlock or improve access to:

- dangerous or high-risk contracts;
- high-value or sensitive cargo;
- authority to protect or recover Guild property;
- delegated discretion during emergencies;
- reduced or waived penalties for credible mistakes or extenuating circumstances;
- restricted technical records or blueprints;
- custom certification pathways;
- advanced inspection, recovery, or salvage work;
- authority to establish Guild-certified infrastructure at the player's own base;
- confidential, discretionary, or politically sensitive assignments;
- off-board or deniable commissions issued by trusted Guild personnel.

Standing should rise primarily through demonstrated competence and responsible behavior, for example:

- completing contracts reliably;
- completing difficult or sensitive contracts successfully;
- delivering bonded cargo intact;
- protecting Guild lives and assets;
- reporting salvage correctly;
- exercising delegated authority responsibly;
- successfully performing certified construction, repair, inspection, or recovery work;
- sustained reliable service.

Standing should fall for conduct such as fraud, theft from Guild custody, repeated negligence, abuse of delegated authority, serious contract violations, or abandoning obligations in bad faith.

Ordinary financial difficulty should not heavily damage Standing by itself. Debt behavior belongs primarily to Credit unless the conduct around the debt also indicates dishonesty or professional misconduct.

Canonical rule:

> **Standing determines what the Guild is willing to entrust to the player.**

## 3. Guild Credit

Credit is narrower than Standing. It measures financial responsibility and the player's history as a debtor and commercial counterparty.

Potential inputs include:

- repayment history;
- late payments;
- defaults;
- current debt burden;
- collateral history;
- account longevity;
- contract-income history;
- prior financed purchases.

Credit may affect:

- maximum loan principal;
- required down payment;
- interest or financing cost;
- collateral requirements;
- access to contract advances;
- financed insurance premiums;
- financed vessel or equipment purchases;
- size and quality of ordinary commercial credit lines.

Canonical rule:

> **Credit determines how much capital the Guild is willing to extend and on what terms.**

## 4. Interaction between Standing and Credit

Standing and Credit should produce meaningfully different institutional treatment.

### Low Standing / high Credit

The player is financially reliable but professionally unproven.

Expected consequences:

- favorable financing on ordinary commercial goods and vessels;
- reliable access to routine financial services;
- limited access to dangerous, sensitive, or delegated-authority work;
- restricted portfolio of specialist Guild services until capability is demonstrated.

### High Standing / low Credit

The player is professionally trusted but financially unreliable.

Expected consequences:

- broad access to advanced missions and institutional responsibilities;
- access to sensitive contracts, recovery work, and Guild-property operations;
- worse borrowing terms;
- larger deposits or stronger collateral requirements;
- possible pay-up-front requirements for services despite high professional trust.

### High Standing / high Credit

The player is both a trusted operator and reliable debtor.

Expected consequences:

- broadest access to Guild services and missions;
- best financing terms;
- authority to handle sensitive Guild assets and operations;
- eligibility for major financed vessels, infrastructure, and commercial expansion;
- possible authorization to establish Guild operations at a player-controlled base.

### New player

A new player should begin as **unproven**, not dishonorable.

They may have:

- little or no Standing history;
- thin or nonexistent Credit history;
- no implication that either value is inherently bad.

The system should distinguish absence of evidence from negative evidence.

## 5. Membership and institutional status

Guild membership should be an institutional status rather than another numeric progression axis.

Membership may require Scrip for initiation, dues, or license renewal, but payment alone should not confer professional trust.

Possible conceptual statuses include:

- **non-member / public user** — may use selected public Guild services and trade;
- **member** — ordinary access to accounts, contracts, registry, insurance eligibility, and member services;
- **authorized operator / agent** — earned through Standing and explicit authorization rather than purchased.

Exact names and thresholds remain implementation-stage decisions.

## 6. Player-operated Guild facilities

High Standing may eventually permit the player to become part of the Guild's operating network rather than merely consume its services.

Possible delegated privileges include authorization to establish at the player's own base:

- a contract board;
- certified landing or docking facilities;
- bonded freight handoff;
- bonded storage;
- route beacon or navigation equipment;
- rescue or recovery capability;
- limited Guild trade or agency services.

The Guild should provide the authorization, standards, and interfaces; the player should normally build and operate the physical facility.

Canonical progression fantasy:

> **The player begins by relying on Guild infrastructure and may eventually be trusted to become part of that infrastructure.**

## 7. Permissions and terms, not generic bonuses

Standing and Credit should primarily change **permissions, responsibilities, and financial terms**, not provide arbitrary numerical reward multipliers.

Prefer:

- higher Standing unlocks bonded freight, Guild-property recovery, sensitive contracts, delegated infrastructure, or discretionary commissions;
- higher Credit improves down payments, loan ceilings, collateral requirements, and financing costs.

Avoid generic effects such as:

- `+15% Scrip rewards` for high Standing;
- broad shop discounts from Credit;
- one combined Guild level that simultaneously controls money, trust, rank, and financing.

## Precommitted rule

> **Scrip represents economic capacity; Standing represents institutional trust and demonstrated professional capability; Credit represents financial reliability. Services may independently require payment, Standing, Credit, or combinations thereof. High Standing expands what the Guild will entrust to the player, while high Credit improves the financial terms under which the Guild will extend capital. Neither substitutes completely for the other.**

Exact numeric scales, thresholds, interest models, membership fees, Standing progression rates, and Credit formulas remain civilization-stage implementation decisions.