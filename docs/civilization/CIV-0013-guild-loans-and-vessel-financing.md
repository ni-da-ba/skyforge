# CIV-0013: Guild loans and vessel financing

**Status:** Precommitted design direction
**Stage:** Pre-civilization; implementation deferred
**Date:** 2026-09-10

## Decision

Guild lending should initially focus on **productive, identifiable assets and contracted economic activity**, especially registered aircraft, equipment, and Guild-recognized commercial work.

Credit governs borrowing capacity and financial terms. Standing governs institutional trust and may permit discretionary exceptions without erasing the underlying financial risk. Scrip remains the settlement instrument used for down payments, repayments, fees, and other monetary obligations.

Canonical rule:

> **Debt should create leverage and responsibility, not administrative chores.**

## Vessel financing as the primary case

Vessel financing should be the central early lending mechanic.

A financing offer may depend on:

- vessel registration;
- recognized / certified configuration;
- insurability;
- purchase price and collateral value;
- player Credit;
- relevant Standing or authorization where the vessel class or intended activity is sensitive.

Conceptually:

**registered vessel + recognized configuration + insurance + borrower Credit → financing terms**

Loans should normally be secured against the financed vessel or other identifiable productive collateral.

## Credit

Credit should influence financial terms rather than broad Guild legitimacy.

Candidate effects include:

- maximum financed principal;
- required down payment;
- financing cost;
- collateral requirements;
- repayment flexibility;
- simultaneous loan capacity;
- eligibility for contract advances or working-capital support.

A player with weak Credit may still trade, earn Scrip, own assets bought outright, take eligible contracts, and participate in Guild life.

## Standing and lending discretion

Standing should not merely act as a second interest-rate stat.

Instead, high Standing may allow the Guild to approve financing that it would otherwise decline because the player has demonstrated exceptional professional value or reliability in a relevant domain.

Examples:

- financing a rescue craft for a highly trusted recovery operator with poor Credit;
- allowing a trusted factor to restructure a temporary delinquency;
- approving restricted-purpose financing for a Guild-relevant operation.

Such discretion may still carry higher down payments, stronger collateral, restricted use of proceeds, or automatic servicing from contract revenue.

Conversely, high Credit without sufficient Standing may justify financing an ordinary commercial vessel while still failing to unlock sensitive recovery, enforcement, or Guild-property activity.

Canonical distinction:

> **Standing determines the opportunity. Credit determines the financing around the opportunity.**

## Repayment

Routine repayment should be largely automatic.

Guild income and account balances may service scheduled obligations when due. The player should be able to inspect principal, next payment, collateral, and payment status, and optionally make extra payments.

The system should avoid repetitive manual installment interactions.

## Delinquency and default

Enforcement should be graduated rather than abrupt:

**CURRENT → LATE → DELINQUENT → DEFAULT → REPOSSESSION ELIGIBLE**

Standing may justify grace, restructuring, or procedural lenience, but should not erase debt.

At serious default, available resolutions may include:

- payment of arrears;
- restructuring;
- voluntary sale;
- surrender of collateral;
- contract arrangements that service the debt;
- eventual repossession where justified.

A single missed payment should not instantly remove the player's aircraft.

## Contract advances

Large Guild contracts may provide advances where mobilization costs would otherwise be prohibitive.

Standing determines whether the player is trusted with the contract. Credit determines whether and on what terms the Guild advances funds against it.

This preserves the separation between professional trust and financial reliability.

## Scope control

The MVP should avoid broad consumer-credit simulation, securities, macroeconomic banking, or unrestricted unsecured lending.

Initial lending primitives should remain close to:

- aircraft-secured loans;
- equipment / productive-asset financing;
- contract advances;
- later, high-trust operating credit where gameplay justifies it.

Implementation parameters such as interest formulas, payment intervals, grace periods, collateral ratios, and credit-scoring equations remain deferred.
